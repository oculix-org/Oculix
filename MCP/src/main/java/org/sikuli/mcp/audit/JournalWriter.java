/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import org.json.JSONObject;
import org.sikuli.mcp.crypto.CanonicalJson;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.mcp.crypto.KeyManager;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only, cryptographically chained JSONL audit journal.
 *
 * <p>Each call to {@link #appendToolCall} produces one line in the current
 * journal file, signed with the configured {@link KeyManager}.
 *
 * <p>Rotation is triggered automatically every {@code maxEntries} entries
 * or {@code maxAgeMillis} milliseconds, whichever comes first. Rotation
 * closes the current file with a {@code rotation_end} marker signed by the
 * current key, and opens a new file starting with a {@code rotation_begin}
 * marker whose {@code prev_hash} chains to the previous marker.
 *
 * <p>Thread-safety: all public methods are synchronized on {@code this}.
 *
 * <p>An optional {@link HighWaterMark} anchor can be injected: when present,
 * it is updated after every successful line write so an external verifier
 * can detect tail truncation (removal of the last N files) that neither
 * intra-file {@code prev_hash} nor cross-file rotation markers can catch.
 * Passing {@code null} keeps the legacy behaviour and is honoured by the
 * pre-existing four-argument constructor for backward compatibility.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class JournalWriter implements AutoCloseable {

  private static final DateTimeFormatter FILE_TS =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

  private final Path journalDir;
  private final KeyManager keys;
  private final long maxEntries;
  private final long maxAgeMillis;
  private final HighWaterMark hwm;

  private final ClockGuard clock = new ClockGuard();
  private final AtomicLong seq = new AtomicLong(0);

  private Path currentFile;
  private BufferedWriter out;
  private String prevHash = "0".repeat(64); // genesis
  private long fileStartMillis;

  /**
   * Legacy constructor without HWM. Delegates to the five-argument form
   * with {@code hwm = null}; behaviour is bit-identical to the pre-HWM
   * writer so existing tests and callers keep passing unchanged.
   */
  public JournalWriter(Path journalDir, KeyManager keys,
                       long maxEntries, long maxAgeMillis) throws IOException {
    this(journalDir, keys, maxEntries, maxAgeMillis, null);
  }

  /**
   * @param hwm optional anchor updated after each write. May be {@code null};
   *            wire an instance in production so tail truncation becomes
   *            detectable.
   */
  public JournalWriter(Path journalDir, KeyManager keys,
                       long maxEntries, long maxAgeMillis,
                       HighWaterMark hwm) throws IOException {
    this.journalDir = journalDir;
    this.keys = keys;
    this.maxEntries = maxEntries;
    this.maxAgeMillis = maxAgeMillis;
    this.hwm = hwm;
    Files.createDirectories(journalDir);
    openNewFile();
  }

  // ── Public append API ──

  public synchronized AuditEntry appendToolCall(String sessionId,
                                                JSONObject clientInfo,
                                                JSONObject llmInfo,
                                                String toolName,
                                                JSONObject args,
                                                String resultSha256) throws IOException {
    // Order matters: rotate BEFORE emitting a clock regression, not
    // after. If the wall clock has regressed and we emit the marker
    // first, the marker lands in the old file — then rotate opens a
    // new file where the very next tsUtc may still be regressive
    // against nothing (empty new file), but hides the regression
    // relative to the old file's tail. Rotating first anchors the
    // regression marker in the new file, so both files stay internally
    // monotonic and the invariant the guard exists to protect holds.
    maybeRotate();
    maybeEmitClockRegression();

    // Redact sensitive values in args before signing. The journal is
    // append-only and signed for life — a password in a tool_call arg
    // would live forever in plaintext. ArgsRedactor keeps structure
    // and marks redacted values with a short SHA-256 fingerprint so
    // auditors can still correlate identical values across entries.
    JSONObject safeArgs = ArgsRedactor.redact(args);

    AuditEntry entry = buildEntry(
        AuditEntry.Type.TOOL_CALL, sessionId, clientInfo, llmInfo,
        toolName, safeArgs, resultSha256, null);
    writeLine(entry);
    return entry;
  }

  public synchronized AuditEntry appendClockRegression(String sessionId) throws IOException {
    JSONObject extra = new JSONObject()
        .put("reason", "wall_clock_regressed_vs_monotonic");
    AuditEntry entry = buildEntry(
        AuditEntry.Type.CLOCK_REGRESSION, sessionId, null, null,
        null, null, null, extra);
    writeLine(entry);
    clock.acknowledge();
    return entry;
  }

  /**
   * Emit a signed {@code RECOVERY_GAP} entry declaring that the previous
   * chain is unrecoverable and that a fresh chain starts here under the
   * currently-configured key. The entry's {@code prev_hash} is forced back
   * to genesis so a chain verifier can identify a legitimate cold restart
   * without having to trust any file next to the journal.
   *
   * <p>Intended to be called immediately after key promotion by
   * {@code cmdRecover}, as the first line of a new journal file. It skips
   * both {@code maybeEmitClockRegression} and {@code maybeRotate} because
   * neither is meaningful on a chain that has just been declared lost.
   *
   * @param reason               short operator-facing motive (e.g. {@code "key_lost"})
   * @param brokenAt             ISO-8601 timestamp of the last known good
   *                             state, or {@code null} if unknown
   * @param previousChainStatus  one of {@code "lost"}, {@code "corrupted"},
   *                             {@code "abandoned"}; free-form for now
   */
  public synchronized AuditEntry appendRecoveryGap(String reason,
                                                   String brokenAt,
                                                   String previousChainStatus) throws IOException {
    this.prevHash = "0".repeat(64);
    JSONObject extra = new JSONObject()
        .put("reason", reason == null ? JSONObject.NULL : reason)
        .put("broken_at", brokenAt == null ? JSONObject.NULL : brokenAt)
        .put("previous_chain_status",
            previousChainStatus == null ? JSONObject.NULL : previousChainStatus);
    AuditEntry entry = buildEntry(
        AuditEntry.Type.RECOVERY_GAP, null, null, null,
        null, null, null, extra);
    writeLine(entry);
    return entry;
  }

  /**
   * Force rotation. Used by the key-rotation CLI before a new key is adopted.
   * The rotation_end marker embeds the SHA-256 of the next public key so
   * operators can anchor trust across the transition.
   */
  public synchronized void rotate(String reason, String nextPublicKeySha256) throws IOException {
    JSONObject extra = new JSONObject()
        .put("reason", reason)
        .put("final_entry_hash", prevHash)
        .put("next_public_key_sha256",
             nextPublicKeySha256 == null ? JSONObject.NULL : nextPublicKeySha256);
    AuditEntry endMarker = buildEntry(
        AuditEntry.Type.ROTATION_END, null, null, null,
        null, null, null, extra);
    writeLine(endMarker);

    closeCurrent();
    openNewFile();

    JSONObject beginExtra = new JSONObject()
        .put("reason", reason)
        .put("previous_marker_hash", endMarker.entryHash);
    AuditEntry beginMarker = buildEntry(
        AuditEntry.Type.ROTATION_BEGIN, null, null, null,
        null, null, null, beginExtra);
    writeLine(beginMarker);
  }

  @Override
  public synchronized void close() throws IOException {
    closeCurrent();
  }

  public Path currentFile() { return currentFile; }

  // ── Internals ──

  private AuditEntry buildEntry(AuditEntry.Type type, String sessionId,
                                JSONObject clientInfo, JSONObject llmInfo,
                                String toolName, JSONObject args,
                                String resultSha256, JSONObject extra) {
    AuditEntry.Builder b = AuditEntry.builder()
        .type(type)
        .tsUtc(clock.nowIso())
        .seq(seq.getAndIncrement())
        .sessionId(sessionId)
        .client(clientInfo)
        .llm(llmInfo)
        .tool(toolName)
        .args(args)
        .resultSha256(resultSha256)
        .extra(extra)
        .prevHash(prevHash);

    AuditEntry partial = b.build();
    String canonical = CanonicalJson.serialize(partial.toCanonicalJson());
    String entryHash = Hashing.sha256Hex(canonical);
    byte[] sig = keys.sign(entryHash.getBytes(StandardCharsets.UTF_8));
    String sigHex = Hashing.toHex(sig);

    AuditEntry full = b.entryHash(entryHash).signature(sigHex).build();
    this.prevHash = entryHash;
    return full;
  }

  private void writeLine(AuditEntry entry) throws IOException {
    out.write(entry.toJsonLine());
    out.newLine();
    out.flush();
    if (hwm != null) {
      // The anchor tracks the last successfully-written entry. If this call
      // fails (disk full, permissions), the exception propagates and the
      // anchor stays one entry behind — a state a chain verifier can spot
      // as "queue extends past HWM" and surface as a warning, not a fatal
      // tamper. Silent swallow here would defeat the point.
      hwm.update(entry.entryHash, entry.seq,
          currentFile.getFileName().toString(), entry.tsUtc);
    }
  }

  private void maybeEmitClockRegression() throws IOException {
    if (clock.hasRegressed()) {
      JSONObject extra = new JSONObject().put("reason", "wall_clock_regressed_vs_monotonic");
      AuditEntry entry = buildEntry(
          AuditEntry.Type.CLOCK_REGRESSION, null, null, null,
          null, null, null, extra);
      writeLine(entry);
      clock.acknowledge();
    }
  }

  private void maybeRotate() throws IOException {
    boolean entriesExceeded = seq.get() >= maxEntries;
    boolean ageExceeded = (Instant.now().toEpochMilli() - fileStartMillis) >= maxAgeMillis;
    if (entriesExceeded || ageExceeded) {
      rotate("auto_rotation", keys.publicKeySha256Hex());
    }
  }

  private void openNewFile() throws IOException {
    String stamp = FILE_TS.format(Instant.now());
    Path candidate = journalDir.resolve("audit-" + stamp + ".jsonl");
    int suffix = 1;
    while (Files.exists(candidate)) {
      candidate = journalDir.resolve("audit-" + stamp + "-" + suffix + ".jsonl");
      suffix++;
    }
    this.currentFile = candidate;
    this.out = Files.newBufferedWriter(currentFile,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    this.fileStartMillis = Instant.now().toEpochMilli();
    this.seq.set(0);
    // prev_hash is carried across files via the rotation markers
    if (prevHash == null) {
      prevHash = "0".repeat(64);
    }
  }

  private void closeCurrent() throws IOException {
    if (out != null) {
      out.flush();
      out.close();
      out = null;
    }
  }
}
