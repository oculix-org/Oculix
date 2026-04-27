/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.JournalVerifier;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

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

  private static Path findJournalFile(Path journalDir) throws Exception {
    try (var stream = Files.list(journalDir)) {
      return stream.filter(p -> p.getFileName().toString().startsWith("audit-"))
                   .findFirst()
                   .orElseThrow();
    }
  }
}
