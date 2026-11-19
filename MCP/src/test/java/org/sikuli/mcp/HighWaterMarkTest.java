/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.HighWaterMark;
import org.sikuli.mcp.audit.HighWaterMark.Snapshot;
import org.sikuli.mcp.crypto.KeyManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HighWaterMark}.
 *
 * <p>The critical property proven here is that all four business fields
 * ({@code last_entry_hash}, {@code last_seq}, {@code last_file},
 * {@code last_ts_utc}) sit under the Ed25519 signature. Tampering any of
 * them — including a {@code last_file} rename — invalidates the anchor.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
class HighWaterMarkTest {

  private static final String FILE_A = "audit-20260706-000000-000.jsonl";
  private static final String FILE_B = "audit-20260706-010000-000.jsonl";
  private static final String TS_A   = "2026-07-06T00:00:00.000000Z";
  private static final String TS_B   = "2026-07-06T01:00:00.000000Z";
  private static final String HASH_A = "abc123def456";
  private static final String HASH_B = "999888777666";

  @Test
  void updateThenLoad_roundtripsAllFourFields(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 42L, FILE_A, TS_A);

    Snapshot loaded = hwm.load().orElseThrow();
    assertEquals(HASH_A, loaded.lastEntryHash);
    assertEquals(42L, loaded.lastSeq);
    assertEquals(FILE_A, loaded.lastFile);
    assertEquals(TS_A, loaded.lastTsUtc);
    assertNotNull(loaded.signature);
    assertFalse(loaded.signature.isBlank());
  }

  @Test
  void verifyValidSignature_returnsTrue(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);

    Snapshot loaded = hwm.load().orElseThrow();
    assertTrue(HighWaterMark.verify(loaded, keys.publicKey()));
  }

  @Test
  void verifyTamperedSignature_returnsFalse(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);
    Snapshot original = hwm.load().orElseThrow();

    // Flip the first two hex chars of the signature.
    String flippedSig = (original.signature.charAt(0) == '0' ? "1" : "0")
        + original.signature.substring(1);
    Snapshot tampered = new Snapshot(
        original.lastEntryHash, original.lastSeq,
        original.lastFile, original.lastTsUtc, flippedSig);

    assertFalse(HighWaterMark.verify(tampered, keys.publicKey()));
  }

  @Test
  void verifyTamperedLastFile_returnsFalse(@TempDir Path dir) throws Exception {
    // The property this test locks in: last_file is under the signature.
    // A rename cannot silently redirect the verifier onto a wrong file.
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);
    Snapshot original = hwm.load().orElseThrow();

    Snapshot renamed = new Snapshot(
        original.lastEntryHash, original.lastSeq,
        "attacker-supplied.jsonl",
        original.lastTsUtc, original.signature);

    assertFalse(HighWaterMark.verify(renamed, keys.publicKey()),
        "renaming last_file must invalidate the signature");
  }

  @Test
  void verifyTamperedLastEntryHash_returnsFalse(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);
    Snapshot original = hwm.load().orElseThrow();

    Snapshot tampered = new Snapshot(
        HASH_B,
        original.lastSeq, original.lastFile,
        original.lastTsUtc, original.signature);

    assertFalse(HighWaterMark.verify(tampered, keys.publicKey()));
  }

  @Test
  void verifyTamperedLastSeq_returnsFalse(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 42L, FILE_A, TS_A);
    Snapshot original = hwm.load().orElseThrow();

    Snapshot tampered = new Snapshot(
        original.lastEntryHash, 99L,
        original.lastFile, original.lastTsUtc, original.signature);

    assertFalse(HighWaterMark.verify(tampered, keys.publicKey()));
  }

  @Test
  void verifyTamperedLastTsUtc_returnsFalse(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);
    Snapshot original = hwm.load().orElseThrow();

    Snapshot tampered = new Snapshot(
        original.lastEntryHash, original.lastSeq,
        original.lastFile,
        "1970-01-01T00:00:00.000000Z",
        original.signature);

    assertFalse(HighWaterMark.verify(tampered, keys.publicKey()));
  }

  @Test
  void verifyWithForeignPublicKey_returnsFalse(@TempDir Path dir,
                                               @TempDir Path otherDir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    KeyManager foreign = KeyManager.loadOrInit(otherDir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);

    Snapshot loaded = hwm.load().orElseThrow();
    assertFalse(HighWaterMark.verify(loaded, foreign.publicKey()),
        "an unrelated key must not verify a foreign anchor");
  }

  @Test
  void loadFromMissingAnchor_returnsEmpty(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("does-not-exist.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    Optional<Snapshot> loaded = hwm.load();
    assertTrue(loaded.isEmpty());
  }

  @Test
  void loadFromInvalidJson_throwsIOException(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    Files.writeString(anchor, "not valid json {{{ ");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    assertThrows(IOException.class, hwm::load);
  }

  @Test
  void loadFromJsonMissingFields_throwsIOException(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    // Valid JSON, but missing last_seq, last_file, last_ts_utc, signature.
    Files.writeString(anchor, "{\"last_entry_hash\":\"abc\"}");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    assertThrows(IOException.class, hwm::load);
  }

  @Test
  void resetOverwritesExistingAnchor(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwm = new HighWaterMark(anchor, keys);
    hwm.update(HASH_A, 1L, FILE_A, TS_A);
    hwm.reset(HASH_B, 42L, FILE_B, TS_B);

    Snapshot loaded = hwm.load().orElseThrow();
    assertEquals(HASH_B, loaded.lastEntryHash);
    assertEquals(42L, loaded.lastSeq);
    assertEquals(FILE_B, loaded.lastFile);
    assertEquals(TS_B, loaded.lastTsUtc);
  }

  @Test
  void resetWithNewKey_reSignsAnchorSoOldKeyNoLongerVerifies(@TempDir Path dir,
                                                             @TempDir Path otherDir) throws Exception {
    // Simulates the rotate-key flow: the old writer signs the anchor,
    // then the operator promotes a new key and re-signs. Old signature
    // is dead, new one holds. This is exactly the invariant cmdRotateKey
    // relies on so verify does not report a bogus tamper post-rotation.
    KeyManager oldKeys = KeyManager.loadOrInit(dir);
    Path anchor = dir.resolve("journal.hwm");
    HighWaterMark hwmOld = new HighWaterMark(anchor, oldKeys);
    hwmOld.update(HASH_A, 1L, FILE_A, TS_A);

    KeyManager newKeys = KeyManager.loadOrInit(otherDir);
    HighWaterMark hwmNew = new HighWaterMark(anchor, newKeys);
    hwmNew.reset(HASH_A, 1L, FILE_A, TS_A);

    Snapshot loaded = hwmNew.load().orElseThrow();
    assertFalse(HighWaterMark.verify(loaded, oldKeys.publicKey()),
        "old key must no longer verify after re-sign");
    assertTrue(HighWaterMark.verify(loaded, newKeys.publicKey()),
        "new key must verify the re-signed anchor");
  }

  @Test
  void verify_rejectsSnapshotWithMalformedHexSignature(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    Snapshot malformed = new Snapshot(HASH_A, 1L, FILE_A, TS_A, "not-hex-at-all");
    assertFalse(HighWaterMark.verify(malformed, keys.publicKey()));
  }

  @Test
  void verify_rejectsNullSnapshotAndNullSignature(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    assertFalse(HighWaterMark.verify(null, keys.publicKey()));
    Snapshot noSig = new Snapshot(HASH_A, 1L, FILE_A, TS_A, null);
    assertFalse(HighWaterMark.verify(noSig, keys.publicKey()));
  }
}
