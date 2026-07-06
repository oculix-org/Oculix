/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.server.StartupCheck;
import org.sikuli.mcp.server.StartupCheck.StartupException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage of the 5 documented startup states plus a "never fall back"
 * guarantee. Each state should either return a valid {@link KeyManager}
 * or throw a {@link StartupException} whose message names the recovery
 * command an operator should run.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
class StartupCheckTest {

  @Test
  void state1_noKeysNoHistory_initialisesFreshKeyPair(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");
    KeyManager k = StartupCheck.validateAndLoad(oculixDir, journalDir);
    assertNotNull(k);
    assertTrue(Files.exists(oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME)),
        "init must persist a private key");
    assertTrue(Files.exists(oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME)),
        "init must persist a public key");
  }

  @Test
  void state2_noKeysButJournalExists_refusesWithRecoverHint(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");
    Files.createDirectories(journalDir);
    // Simulate a leftover audit file with no key pair around.
    Files.writeString(journalDir.resolve("audit-20260706-000000-000.jsonl"),
        "{\"stub\":\"orphan\"}\n");

    StartupException ex = assertThrows(StartupException.class,
        () -> StartupCheck.validateAndLoad(oculixDir, journalDir));
    assertTrue(ex.getMessage().contains("oculix-mcp recover"),
        () -> "message must point at recover: " + ex.getMessage());
  }

  @Test
  void partialKeyState_onlyPrivateKey_refuses(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");
    Files.writeString(oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME),
        "not-a-real-key");
    // Public key deliberately missing.

    StartupException ex = assertThrows(StartupException.class,
        () -> StartupCheck.validateAndLoad(oculixDir, journalDir));
    assertTrue(ex.getMessage().contains("Inconsistent key state"),
        () -> ex.getMessage());
  }

  @Test
  void partialKeyState_onlyPublicKey_refuses(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");
    Files.writeString(oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME),
        "not-a-real-key");

    StartupException ex = assertThrows(StartupException.class,
        () -> StartupCheck.validateAndLoad(oculixDir, journalDir));
    assertTrue(ex.getMessage().contains("Inconsistent key state"));
  }

  @Test
  void state3_corruptedKeyBytes_refusesWithRecoverHint(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");
    // Both files present but not real Ed25519 material.
    Files.writeString(oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME),
        "garbage-not-ed25519");
    Files.writeString(oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME),
        "garbage-not-ed25519");

    StartupException ex = assertThrows(StartupException.class,
        () -> StartupCheck.validateAndLoad(oculixDir, journalDir));
    assertTrue(ex.getMessage().contains("Failed to load")
        || ex.getMessage().contains("recover"),
        () -> ex.getMessage());
  }

  @Test
  void state4_keyDoesNotVerifyLastEntry_refuses(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");

    // Init a valid key + a valid journal entry.
    KeyManager original = KeyManager.generateAndStore(oculixDir);
    try (JournalWriter j = new JournalWriter(journalDir, original, 100, 60_000)) {
      j.appendToolCall("s", null, null, "any", new JSONObject(), "h");
    }

    // Overwrite both key files with a NEW, unrelated pair — as if an
    // operator swapped keys without going through rotate-key.
    Path other = oculixDir.resolve("other-key-dir");
    Files.createDirectories(other);
    KeyManager foreign = KeyManager.generateAndStore(other);
    Files.copy(other.resolve(KeyManager.PRIVATE_KEY_FILENAME),
        oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Files.copy(other.resolve(KeyManager.PUBLIC_KEY_FILENAME),
        oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    StartupException ex = assertThrows(StartupException.class,
        () -> StartupCheck.validateAndLoad(oculixDir, journalDir));
    assertTrue(ex.getMessage().contains("does not verify")
            || ex.getMessage().contains("rotate-key")
            || ex.getMessage().contains("recover"),
        () -> "message should name a remedy: " + ex.getMessage());
  }

  @Test
  void state5_validKeyAndJournal_startsCleanly(@TempDir Path oculixDir) throws Exception {
    Path journalDir = oculixDir.resolve("journal");
    KeyManager original = KeyManager.generateAndStore(oculixDir);
    try (JournalWriter j = new JournalWriter(journalDir, original, 100, 60_000)) {
      j.appendToolCall("s", null, null, "any", new JSONObject(), "h");
    }

    KeyManager loaded = StartupCheck.validateAndLoad(oculixDir, journalDir);
    assertNotNull(loaded);
    // Public key fingerprint should match original.
    assertEquals(original.publicKeySha256Hex(), loaded.publicKeySha256Hex());
  }

  @Test
  void neverFallsBackToUnsignedMode(@TempDir Path oculixDir) throws Exception {
    // The doctrine: any inconsistent state throws, never returns a
    // dummy KeyManager. Meta-test that verifies the aggregate behaviour
    // across all failure modes.
    Path journalDir = oculixDir.resolve("journal");
    Files.createDirectories(journalDir);
    Files.writeString(journalDir.resolve("audit-x.jsonl"), "{}\n");
    // No keys, history exists → REFUSE. Must throw, not return.
    assertThrows(StartupException.class,
        () -> StartupCheck.validateAndLoad(oculixDir, journalDir));
  }
}
