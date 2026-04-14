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
 */
public final class JournalWriter implements AutoCloseable {

  private static final DateTimeFormatter FILE_TS =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

  private final Path journalDir;
  private final KeyManager keys;
  private final long maxEntries;
  private final long maxAgeMillis;

  private final ClockGuard clock = new ClockGuard();
  private final AtomicLong seq = new AtomicLong(0);

  private Path currentFile;
  private BufferedWriter out;
  private String prevHash = "0".repeat(64); // genesis
  private long fileStartMillis;

  public JournalWriter(Path journalDir, KeyManager keys,
                       long maxEntries, long maxAgeMillis) throws IOException {
    this.journalDir = journalDir;
    this.keys = keys;
    this.maxEntries = maxEntries;
    this.maxAgeMillis = maxAgeMillis;
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
    maybeEmitClockRegression();
    maybeRotate();

    AuditEntry entry = buildEntry(
        AuditEntry.Type.TOOL_CALL, sessionId, clientInfo, llmInfo,
        toolName, args, resultSha256, null);
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
