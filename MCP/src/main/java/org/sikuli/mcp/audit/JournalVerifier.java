/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import org.json.JSONObject;
import org.sikuli.mcp.crypto.CanonicalJson;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.mcp.crypto.KeyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standalone verifier for JSONL audit journals.
 *
 * <p>Three levels of scrutiny are exposed:
 * <ul>
 *   <li>{@link #verify(Path, PublicKey)} — legacy per-file entry check
 *       (unchanged behaviour). Every entry's {@code entry_hash} is
 *       recomputed, its Ed25519 signature verified, {@code seq}
 *       monotonicity and {@code prev_hash} chain enforced, and
 *       {@code ts_utc} monotonicity checked.</li>
 *   <li>{@link #verifyFile(Path, PublicKey)} — same per-file check but
 *       also captures the first and last entry meta, needed to resolve
 *       cross-file links.</li>
 *   <li>{@link #verifyChain(Path, PublicKey, Path)} — full sweep across
 *       the journal directory: per-file verification, cross-file link
 *       resolution via {@code rotation_end}/{@code rotation_begin} hash
 *       lookup (not filename order — filename collisions on the
 *       millisecond stamp suffix as {@code -1}/{@code -2} would lie
 *       about creation order), genesis-anchored file-head validation,
 *       and confrontation with the {@link HighWaterMark} anchor to
 *       detect tail truncation.</li>
 * </ul>
 *
 * <p>The chain result reports at three levels: {@code OK} if everything
 * lined up, {@code WARN} if a soft deviation was detected (anchor lags
 * queue after a write-then-crash, empty journal dir), {@code FAIL} for
 * anything that breaks the tamper-detection claim.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class JournalVerifier {

  private static final String GENESIS = "0".repeat(64);

  public static final class Result {
    public final boolean ok;
    public final int entriesChecked;
    public final List<String> issues;
    public Result(boolean ok, int entriesChecked, List<String> issues) {
      this.ok = ok;
      this.entriesChecked = entriesChecked;
      this.issues = issues;
    }
  }

  /**
   * Structural summary of a single entry, enough to resolve cross-file
   * links without re-parsing the file.
   */
  public static final class EntryMeta {
    public final String type;       // lower-case (tool_call, rotation_begin, ...)
    public final long seq;
    public final String tsUtc;
    public final String prevHash;
    public final String entryHash;
    public final JSONObject extra;  // may be null
    public EntryMeta(String type, long seq, String tsUtc,
                     String prevHash, String entryHash, JSONObject extra) {
      this.type = type;
      this.seq = seq;
      this.tsUtc = tsUtc;
      this.prevHash = prevHash;
      this.entryHash = entryHash;
      this.extra = extra;
    }
  }

  /** Per-file verification result plus first and last entry meta. */
  public static final class FileVerification {
    public final Path file;
    public final Result result;
    public final EntryMeta first;  // null if the file has no entries
    public final EntryMeta last;   // null if the file has no entries
    public FileVerification(Path file, Result result, EntryMeta first, EntryMeta last) {
      this.file = file;
      this.result = result;
      this.first = first;
      this.last = last;
    }
  }

  /**
   * Aggregate result of a chain verification.
   *
   * <p>{@code level}:
   * <ul>
   *   <li>{@code OK} — all files clean, all cross-file links resolved,
   *       anchor agrees with the last file.</li>
   *   <li>{@code WARN} — a soft deviation (e.g. anchor lags the queue
   *       after a write succeeded but the anchor update failed) that
   *       does not compromise the tamper claim. Exit code 2.</li>
   *   <li>{@code FAIL} — per-file error, broken cross-file link, orphan
   *       file, missing anchor with existing queue, or truncation
   *       detected. Exit code 1.</li>
   * </ul>
   */
  public static final class ChainResult {
    public enum Level { OK, WARN, FAIL }

    public final Level level;
    public final int filesChecked;
    public final int entriesChecked;
    public final List<FileVerification> perFile;
    public final List<String> issues;
    public final List<String> warnings;

    public ChainResult(Level level, int filesChecked, int entriesChecked,
                       List<FileVerification> perFile,
                       List<String> issues, List<String> warnings) {
      this.level = level;
      this.filesChecked = filesChecked;
      this.entriesChecked = entriesChecked;
      this.perFile = perFile;
      this.issues = issues;
      this.warnings = warnings;
    }

    public boolean ok() { return level == Level.OK; }
  }

  /**
   * Legacy per-file verifier. Preserved so existing callers and tests
   * that only want a single-file check keep the exact same API.
   */
  public static Result verify(Path file, PublicKey publicKey) throws IOException {
    return verifyFile(file, publicKey).result;
  }

  /**
   * Verify one file and return both the pass/fail plus the first and
   * last entry meta so cross-file resolution can be done without
   * re-parsing.
   */
  public static FileVerification verifyFile(Path file, PublicKey publicKey) throws IOException {
    List<String> issues = new ArrayList<>();
    int lineNo = 0;
    long expectedSeq = 0;
    String expectedPrev = null;
    String lastTs = null;
    EntryMeta first = null;
    EntryMeta last = null;

    try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = in.readLine()) != null) {
        lineNo++;
        if (line.isBlank()) continue;
        JSONObject o;
        try {
          o = new JSONObject(line);
        } catch (Exception e) {
          issues.add("line " + lineNo + ": invalid JSON — " + e.getMessage());
          continue;
        }

        String type = o.getString("type");
        long seq = o.getLong("seq");
        String ts = o.getString("ts_utc");
        String prev = o.getString("prev_hash");
        String entryHash = o.getString("entry_hash");
        String sigHex = o.getString("signature");
        JSONObject extra = (o.has("extra") && !o.isNull("extra"))
            ? o.getJSONObject("extra") : null;

        // Canonical re-serialisation: strip entry_hash + signature, re-hash
        JSONObject canonical = new JSONObject(line);
        canonical.remove("entry_hash");
        canonical.remove("signature");
        String recomputed = Hashing.sha256Hex(CanonicalJson.serialize(canonical));
        if (!recomputed.equals(entryHash)) {
          issues.add("line " + lineNo + ": entry_hash mismatch (recomputed " + recomputed + ")");
        }

        byte[] sig = Hashing.fromHex(sigHex);
        boolean sigOk = KeyManager.verify(
            entryHash.getBytes(StandardCharsets.UTF_8), sig, publicKey);
        if (!sigOk) {
          issues.add("line " + lineNo + ": signature verification failed");
        }

        if (seq != expectedSeq) {
          issues.add("line " + lineNo + ": seq=" + seq + " expected=" + expectedSeq);
        }
        expectedSeq = seq + 1;

        if (expectedPrev != null && !prev.equals(expectedPrev)) {
          issues.add("line " + lineNo + ": prev_hash chain broken (got " + prev
              + " expected " + expectedPrev + ")");
        }
        expectedPrev = entryHash;

        if (lastTs != null && ts.compareTo(lastTs) < 0) {
          issues.add("line " + lineNo + ": ts_utc regressed (got " + ts
              + " after " + lastTs + ")");
        }
        lastTs = ts;

        EntryMeta meta = new EntryMeta(type, seq, ts, prev, entryHash, extra);
        if (first == null) first = meta;
        last = meta;
      }
    }

    Result result = new Result(issues.isEmpty(), lineNo, issues);
    return new FileVerification(file, result, first, last);
  }

  /**
   * Chain verification across every {@code audit-*.jsonl} file in
   * {@code journalDir}, cross-checked against the {@link HighWaterMark}
   * anchor at {@code anchorPath}.
   *
   * <p>The cross-file link check does <strong>not</strong> rely on
   * filename order. Filename collisions on the millisecond stamp are
   * suffixed with {@code -1}, {@code -2}, and lexicographic order
   * places {@code audit-...-000-1.jsonl} before {@code audit-...-000.jsonl}
   * even though the suffixed file was created after. The hash chain
   * does not lie — {@code rotation_end.entry_hash} of file N is
   * carried into {@code rotation_begin.previous_marker_hash} of file
   * N+1, so we index all {@code rotation_end} hashes and resolve every
   * {@code rotation_begin} against that index. A missing predecessor
   * surfaces as an unresolvable reference.
   *
   * @param journalDir directory scanned for {@code audit-*.jsonl} files
   * @param publicKey  key used to check both entry signatures and the
   *                   HWM signature
   * @param anchorPath location of the {@link HighWaterMark} anchor,
   *                   or {@code null} to skip HWM confrontation
   */
  public static ChainResult verifyChain(Path journalDir, PublicKey publicKey,
                                        Path anchorPath) throws IOException {
    List<String> issues = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<FileVerification> perFile = new ArrayList<>();
    int totalEntries = 0;

    if (!Files.isDirectory(journalDir)) {
      issues.add("journal directory does not exist: " + journalDir);
      return new ChainResult(ChainResult.Level.FAIL, 0, 0, perFile, issues, warnings);
    }

    List<Path> files;
    try (Stream<Path> s = Files.list(journalDir)) {
      files = s.filter(p -> {
        String name = p.getFileName().toString();
        return name.startsWith("audit-") && name.endsWith(".jsonl");
      }).sorted(Comparator.naturalOrder()).collect(Collectors.toList());
    }

    // Pass 1 — per-file verification + meta capture
    for (Path f : files) {
      FileVerification fv = verifyFile(f, publicKey);
      perFile.add(fv);
      totalEntries += fv.result.entriesChecked;
      if (!fv.result.ok) {
        for (String iss : fv.result.issues) {
          issues.add(f.getFileName() + ": " + iss);
        }
      }
    }

    // Pass 2 — index rotation_end by entry_hash. This is the truth
    // source for chain order: hash resolution, not filename order.
    Map<String, Path> endsByHash = new HashMap<>();
    for (FileVerification fv : perFile) {
      if (fv.last != null && "rotation_end".equals(fv.last.type)) {
        endsByHash.put(fv.last.entryHash, fv.file);
      }
    }

    // Pass 3 — resolve rotation_begin.previous_marker_hash. Missing
    // files between rotations surface here as unresolvable references.
    for (FileVerification fv : perFile) {
      if (fv.first != null && "rotation_begin".equals(fv.first.type)) {
        JSONObject ex = fv.first.extra;
        if (ex == null || !ex.has("previous_marker_hash")) {
          issues.add(fv.file.getFileName()
              + ": rotation_begin missing previous_marker_hash");
          continue;
        }
        String prevMarker = ex.getString("previous_marker_hash");
        if (!endsByHash.containsKey(prevMarker)) {
          issues.add(fv.file.getFileName()
              + ": rotation_begin references unknown previous_marker_hash="
              + prevMarker + " — missing file between rotations");
        }
      }
    }

    // Pass 4 — genesis-anchored head check. A legitimate chain start
    // has first.prev_hash == GENESIS. That covers: the very first
    // file (initial install), a fresh RECOVERY_GAP file (chain reset),
    // and any legitimate migration. Anything else must be a
    // rotation_begin whose predecessor resolved above. Otherwise the
    // file is orphaned — injected by an attacker, or its predecessor
    // was removed.
    for (FileVerification fv : perFile) {
      if (fv.first == null) continue; // empty file, covered by per-file layer
      boolean genesisAnchored = GENESIS.equals(fv.first.prevHash);
      boolean rotationBegin = "rotation_begin".equals(fv.first.type);
      if (!genesisAnchored && !rotationBegin) {
        issues.add(fv.file.getFileName()
            + ": file does not begin at genesis (prev_hash=" + fv.first.prevHash
            + ") and does not start with rotation_begin — orphan or predecessor missing");
      }
    }

    // HWM confrontation
    if (anchorPath != null) {
      HwmVerdict v = checkAnchor(anchorPath, publicKey, perFile);
      if (v.fail != null) issues.add(v.fail);
      if (v.warn != null) warnings.add(v.warn);
    }

    ChainResult.Level level;
    if (!issues.isEmpty())        level = ChainResult.Level.FAIL;
    else if (!warnings.isEmpty()) level = ChainResult.Level.WARN;
    else                          level = ChainResult.Level.OK;

    return new ChainResult(level, files.size(), totalEntries, perFile, issues, warnings);
  }

  private static final class HwmVerdict {
    final String fail;
    final String warn;
    HwmVerdict(String fail, String warn) { this.fail = fail; this.warn = warn; }
  }

  /**
   * Compare the on-disk anchor against the observed journal state.
   * Reads and parses the anchor inline (no {@link KeyManager} needed
   * here — {@link HighWaterMark#verify} is a static crypto check).
   */
  private static HwmVerdict checkAnchor(Path anchorPath, PublicKey publicKey,
                                        List<FileVerification> perFile) {
    if (!Files.exists(anchorPath)) {
      // Absent anchor plus present queue: possible tail wipe (attacker
      // took the anchor along with the last files). Absent anchor plus
      // empty queue: legitimate fresh install. We warn on the first
      // case, stay silent on the second.
      if (perFile.isEmpty()) {
        return new HwmVerdict(null, null);
      }
      return new HwmVerdict(null,
          "HWM anchor missing at " + anchorPath + " while "
          + perFile.size() + " journal file(s) exist — "
          + "verify the anchor was not wiped along with a truncated tail");
    }

    HighWaterMark.Snapshot snap;
    try {
      String content = Files.readString(anchorPath, StandardCharsets.UTF_8);
      JSONObject o = new JSONObject(content);
      snap = new HighWaterMark.Snapshot(
          o.getString("last_entry_hash"),
          o.getLong("last_seq"),
          o.getString("last_file"),
          o.getString("last_ts_utc"),
          o.getString("signature"));
    } catch (Exception e) {
      return new HwmVerdict("HWM anchor at " + anchorPath
          + " is unparseable: " + e.getMessage(), null);
    }

    if (!HighWaterMark.verify(snap, publicKey)) {
      return new HwmVerdict(
          "HWM signature invalid — anchor tampered, signed by a foreign key, "
          + "or archived-key file signed by an older Ed25519 pair (Phase 2)",
          null);
    }

    Optional<FileVerification> referenced = perFile.stream()
        .filter(fv -> fv.file.getFileName().toString().equals(snap.lastFile))
        .findFirst();
    if (referenced.isEmpty()) {
      return new HwmVerdict("HWM references last_file=" + snap.lastFile
          + " which is missing from the journal directory — tail truncation detected",
          null);
    }
    FileVerification refFv = referenced.get();

    if (refFv.last == null) {
      return new HwmVerdict("HWM references " + snap.lastFile
          + " but that file has no entries", null);
    }

    if (refFv.last.seq < snap.lastSeq) {
      return new HwmVerdict("HWM says last_seq=" + snap.lastSeq
          + " in " + snap.lastFile + " but the file only reaches seq="
          + refFv.last.seq + " — partial truncation of the last file",
          null);
    }
    if (refFv.last.seq == snap.lastSeq
        && !refFv.last.entryHash.equals(snap.lastEntryHash)) {
      return new HwmVerdict("HWM says last_entry_hash=" + snap.lastEntryHash
          + " at seq=" + snap.lastSeq + " in " + snap.lastFile
          + " but the actual hash is " + refFv.last.entryHash
          + " — the last entry was rewritten",
          null);
    }
    if (refFv.last.seq > snap.lastSeq) {
      // Legitimate lag: writeLine succeeded but the follow-up HWM
      // update failed (disk full, permissions), or the queue advanced
      // between the anchor write and this verify. Chain itself is
      // intact, warn only.
      return new HwmVerdict(null,
          "HWM lags actual queue in " + snap.lastFile
          + ": anchor at seq=" + snap.lastSeq
          + ", file reaches seq=" + refFv.last.seq);
    }

    return new HwmVerdict(null, null);
  }

  public static PublicKey loadPublicKey(Path pubPath) throws Exception {
    byte[] pubBytes = Files.readAllBytes(pubPath);
    KeyFactory kf = KeyFactory.getInstance("Ed25519");
    return kf.generatePublic(new X509EncodedKeySpec(pubBytes));
  }
}
