/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.HighWaterMark;
import org.sikuli.mcp.audit.JournalVerifier;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;

import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */

class AuditChainTest {

  @Test
  void emptyFileVerifiesClean(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000)) {
      // write nothing
    }
    // Empty file should pass
    Path file = findJournalFile(journalDir);
    JournalVerifier.Result r = JournalVerifier.verify(file, keys.publicKey());
    assertTrue(r.ok, () -> "Expected OK, got: " + r.issues);
  }

  @Test
  void singleEntryChain(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000)) {
      j.appendToolCall("session-1", new JSONObject().put("name", "test"),
          null, "oculix_find_image",
          new JSONObject().put("reference_path", "/tmp/x.png"),
          "abc123");
    }
    Path file = findJournalFile(journalDir);
    JournalVerifier.Result r = JournalVerifier.verify(file, keys.publicKey());
    assertTrue(r.ok, () -> "Issues: " + r.issues);
    assertEquals(1, r.entriesChecked);
  }

  @Test
  void multiEntryChain(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000)) {
      for (int i = 0; i < 10; i++) {
        j.appendToolCall("sess", new JSONObject().put("name", "c"), null,
            "tool_" + i, new JSONObject().put("i", i), "h" + i);
      }
    }
    Path file = findJournalFile(journalDir);
    JournalVerifier.Result r = JournalVerifier.verify(file, keys.publicKey());
    assertTrue(r.ok, () -> "Issues: " + r.issues);
    assertEquals(10, r.entriesChecked);
  }

  @Test
  void tamperingIsDetected(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000)) {
      j.appendToolCall("s", null, null, "first",
          new JSONObject(), "h1");
      j.appendToolCall("s", null, null, "second",
          new JSONObject(), "h2");
    }
    Path file = findJournalFile(journalDir);

    // Tamper: swap a char in a line
    String content = Files.readString(file);
    String tampered = content.replaceFirst("\"first\"", "\"HACKED\"");
    Files.writeString(file, tampered);

    JournalVerifier.Result r = JournalVerifier.verify(file, keys.publicKey());
    assertFalse(r.ok);
    assertTrue(r.issues.stream().anyMatch(s ->
        s.contains("entry_hash mismatch") || s.contains("signature")));
  }

  @Test
  void rotationProducesTwoFilesWithChain(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000)) {
      j.appendToolCall("s", null, null, "pre_rotation",
          new JSONObject(), "h1");
      j.rotate("test_rotation", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "post_rotation",
          new JSONObject(), "h2");
    }
    long count;
    try (var stream = Files.list(journalDir)) {
      count = stream.filter(p -> p.getFileName().toString().startsWith("audit-"))
                    .count();
    }
    assertEquals(2, count, "Rotation should have produced exactly 2 journal files");
  }

  // ── verifyChain: cross-file + HWM tests ──

  @Test
  void chainVerifyPassesOnCleanChain(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("test_rotation", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
    }

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), anchor);
    assertTrue(r.ok(),
        () -> "Expected OK, got level=" + r.level + " issues=" + r.issues + " warns=" + r.warnings);
    assertEquals(JournalVerifier.ChainResult.Level.OK, r.level);
    assertEquals(2, r.filesChecked);
  }

  @Test
  void chainVerifyDetectsMissingFileBetweenRotations(@TempDir Path dir) throws Exception {
    // Suppression of an audit-*.jsonl between two rotations must
    // surface as a FAIL. The property is proven by hash resolution:
    // the surviving rotation_begin references a rotation_end that
    // no longer exists in the map, regardless of filename order.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("r1", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
      j.rotate("r2", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "c", new JSONObject(), "h3");
    }
    List<Path> files = listAuditFiles(journalDir);
    assertEquals(3, files.size());

    // Delete the middle file.
    Files.delete(files.get(1));

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), null);
    assertEquals(JournalVerifier.ChainResult.Level.FAIL, r.level);
    assertTrue(r.issues.stream().anyMatch(iss ->
        iss.contains("missing file between rotations")
        || iss.contains("unknown previous_marker_hash")),
        () -> "Expected missing-file issue, got: " + r.issues);
  }

  @Test
  void chainVerifyDetectsTamperedPreviousMarkerHash(@TempDir Path dir) throws Exception {
    // Modifying the previous_marker_hash in a rotation_begin breaks
    // the per-file entry_hash first (hash covers extra), but even if
    // an attacker recomputed the hash and re-signed with a stolen key,
    // the hash would no longer resolve into endsByHash. We assert the
    // failure path, not which specific check flags it.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("r1", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
    }
    List<Path> files = listAuditFiles(journalDir);
    Path secondFile = files.get(1);

    String content = Files.readString(secondFile);
    // The rotation_begin is on line 1 of the second file. Rewrite its
    // previous_marker_hash to a bogus value.
    String tampered = content.replaceFirst(
        "\"previous_marker_hash\":\"[0-9a-f]+\"",
        "\"previous_marker_hash\":\"deadbeef\"");
    assertNotEquals(content, tampered, "regex should have matched");
    Files.writeString(secondFile, tampered);

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), null);
    assertEquals(JournalVerifier.ChainResult.Level.FAIL, r.level);
  }

  @Test
  void chainVerifyAcceptsFileStartingWithRecoveryGap(@TempDir Path dir) throws Exception {
    // A file that begins with a signed RECOVERY_GAP is a legitimate
    // fresh chain — prev_hash == genesis by design of appendRecoveryGap.
    // The verifier must accept it as a chain start.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendRecoveryGap("operator_recovery", "2026-07-06T00:00:00Z", "lost");
      j.appendToolCall("s", null, null, "post_recovery",
          new JSONObject(), "h1");
    }
    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), anchor);
    assertTrue(r.ok(),
        () -> "Expected OK on recovery journal, got: " + r.issues + " / " + r.warnings);
  }

  @Test
  void chainVerifyRejectsFakeTxtBypass(@TempDir Path dir) throws Exception {
    // The core property of the fix: a plain .txt marker (as the old
    // cmdRecover used to write) MUST NOT legitimise the deletion of a
    // journal file. The verifier ignores .txt entirely and reports
    // FAIL because the surviving rotation_begin doesn't resolve.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("r1", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
      j.rotate("r2", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "c", new JSONObject(), "h3");
    }
    List<Path> files = listAuditFiles(journalDir);
    Files.delete(files.get(1));

    // Attacker drops a legitimate-looking .txt to try to explain the gap.
    Files.writeString(journalDir.resolve("UNSIGNED_GAP-999.txt"),
        "Audit chain broken at " + java.time.Instant.now() + ".\n");

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), null);
    assertEquals(JournalVerifier.ChainResult.Level.FAIL, r.level,
        "a .txt file must never be treated as a legitimate break");
  }

  @Test
  void chainVerifyToleratesFilenameCollisionSuffixes(@TempDir Path dir) throws Exception {
    // openNewFile suffixes -1, -2 on millisecond collisions, and
    // lexicographic order (audit-...-000-1.jsonl < audit-...-000.jsonl)
    // no longer reflects creation order. Chain resolution runs on the
    // hash chain, not the filename, so the verifier stays clean even
    // when the lexicographic order lies.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("r1", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
    }
    List<Path> files = listAuditFiles(journalDir);
    assertEquals(2, files.size());

    // Rename the FIRST file to simulate a suffixed collision so the
    // lexicographic order places the SECOND file before it. Hash chain
    // must still verify.
    Path first = files.get(0);
    String firstName = first.getFileName().toString();
    // audit-YYYYMMDD-HHMMSS-SSS.jsonl → audit-YYYYMMDD-HHMMSS-SSS-9.jsonl
    String suffixed = firstName.substring(0, firstName.length() - ".jsonl".length())
        + "-9.jsonl";
    // We keep the anchor pointing at the SECOND file so HWM doesn't complain.
    Files.move(first, journalDir.resolve(suffixed));

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), null);
    assertTrue(r.ok(),
        () -> "Chain resolution must not depend on lexicographic order. Got: "
              + r.issues + " / " + r.warnings);
  }

  @Test
  void chainVerifyDetectsTruncatedTail(@TempDir Path dir) throws Exception {
    // The property the whole HWM design exists to hold: if an attacker
    // lops off the last file and does NOT wipe the anchor, verify
    // catches it. This is the tail-truncation case that neither
    // prev_hash nor rotation_end/begin can detect on their own.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("r1", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
    }

    // Delete the last file. Anchor stays behind — points at the file
    // we just deleted.
    List<Path> files = listAuditFiles(journalDir);
    Files.delete(files.get(files.size() - 1));

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), anchor);
    assertEquals(JournalVerifier.ChainResult.Level.FAIL, r.level);
    assertTrue(r.issues.stream().anyMatch(iss ->
        iss.contains("tail truncation") || iss.contains("last_file")),
        () -> "Expected tail-truncation issue, got: " + r.issues);
  }

  @Test
  void chainVerifyDetectsOrphanFileInjection(@TempDir Path dir) throws Exception {
    // Pass 4: a file whose first entry has prev_hash != GENESIS and
    // whose type is not rotation_begin is orphaned. This test
    // simulates an attacker dropping a valid-looking journal file
    // in a place where its predecessor doesn't exist.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.rotate("r1", keys.publicKeySha256Hex());
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
    }

    // Delete the rotation_begin file (second). Then re-parent its
    // remaining part by removing the rotation_begin from an
    // otherwise-valid file → orphan.
    List<Path> files = listAuditFiles(journalDir);
    Path second = files.get(1);
    // Strip line 1 (the rotation_begin) — leave just tool_call lines.
    String content = Files.readString(second);
    int nl = content.indexOf('\n');
    if (nl > 0) {
      Files.writeString(second, content.substring(nl + 1));
    }

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), null);
    assertEquals(JournalVerifier.ChainResult.Level.FAIL, r.level,
        "an orphan file must FAIL");
    assertTrue(r.issues.stream().anyMatch(iss ->
        iss.contains("orphan") || iss.contains("genesis")
        || iss.contains("does not start with rotation_begin")),
        () -> "Expected orphan issue, got: " + r.issues);
  }

  @Test
  void chainVerifyDetectsJournalDirWipeWithHwmIntact(@TempDir Path dir) throws Exception {
    // The anchor lives OUTSIDE journalDir. Wiping journalDir alone
    // leaves the anchor behind and the verifier catches the wipe.
    // This is the wipe-partiel scenario documented as covered.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm"); // outside journalDir
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
    }
    assertTrue(Files.exists(anchor), "anchor must have been written outside journalDir");

    // Wipe the journal directory only. Anchor survives.
    try (var stream = Files.list(journalDir)) {
      List<Path> toDelete = stream.collect(Collectors.toList());
      for (Path p : toDelete) Files.delete(p);
    }

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), anchor);
    // Level is FAIL when the anchor's last_file cannot be resolved.
    // For an empty journalDir + present anchor, the anchor's file is
    // missing → FAIL "tail truncation".
    assertEquals(JournalVerifier.ChainResult.Level.FAIL, r.level,
        "wipe of journalDir with anchor intact must be detected");
  }

  @Test
  void chainVerifyReportsWarnWhenAnchorLagsQueue(@TempDir Path dir) throws Exception {
    // HWM update failure is a possible state at runtime: writeLine
    // reached disk but the follow-up hwm.update crashed (disk full,
    // permission flip, VM pause). We simulate the crash by rewinding
    // the anchor to the previous entry after both writes succeeded.
    // The chain itself is intact; the verifier must WARN, not FAIL.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path journalDir = dir.resolve("journal");
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    try (JournalWriter j = new JournalWriter(journalDir, keys, 100, 60_000, hwm)) {
      j.appendToolCall("s", null, null, "a", new JSONObject(), "h1");
      j.appendToolCall("s", null, null, "b", new JSONObject(), "h2");
    }
    Path file = listAuditFiles(journalDir).get(0);

    // Read entry seq=0 (the first line) so we can rewind the anchor
    // to point at it as if the update for seq=1 had crashed.
    JSONObject first;
    try (var in = Files.newBufferedReader(file)) {
      first = new JSONObject(in.readLine());
    }
    hwm.reset(
        first.getString("entry_hash"),
        first.getLong("seq"),
        file.getFileName().toString(),
        first.getString("ts_utc"));

    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, keys.publicKey(), anchor);
    assertEquals(JournalVerifier.ChainResult.Level.WARN, r.level,
        () -> "Expected WARN for HWM lag, got: " + r.level
              + " issues=" + r.issues + " warns=" + r.warnings);
    assertTrue(r.warnings.stream().anyMatch(w -> w.contains("lags actual queue")),
        () -> "Expected lag warning, got: " + r.warnings);
  }

  private static List<Path> listAuditFiles(Path journalDir) throws Exception {
    try (var stream = Files.list(journalDir)) {
      return stream.filter(p -> p.getFileName().toString().startsWith("audit-")
                             && p.getFileName().toString().endsWith(".jsonl"))
                   .sorted(Comparator.naturalOrder())
                   .collect(Collectors.toList());
    }
  }

  private static Path findJournalFile(Path journalDir) throws Exception {
    try (var stream = Files.list(journalDir)) {
      return stream.filter(p -> p.getFileName().toString().startsWith("audit-"))
                   .findFirst()
                   .orElseThrow();
    }
  }
}
