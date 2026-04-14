/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.crypto.KeyManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class KeyManagerTest {

  @Test
  void generatesAndPersistsKeyPair(@TempDir Path dir) throws Exception {
    KeyManager k = KeyManager.loadOrInit(dir);
    assertNotNull(k.privateKey());
    assertNotNull(k.publicKey());
    assertTrue(Files.exists(dir.resolve(KeyManager.PRIVATE_KEY_FILENAME)));
    assertTrue(Files.exists(dir.resolve(KeyManager.PUBLIC_KEY_FILENAME)));
  }

  @Test
  void reloadsExistingKeyPair(@TempDir Path dir) throws Exception {
    KeyManager first = KeyManager.loadOrInit(dir);
    KeyManager second = KeyManager.loadOrInit(dir);
    assertArrayEquals(first.publicKeyBytes(), second.publicKeyBytes());
  }

  @Test
  void signAndVerifyRoundtrip(@TempDir Path dir) throws Exception {
    KeyManager k = KeyManager.loadOrInit(dir);
    byte[] msg = "hello world".getBytes(StandardCharsets.UTF_8);
    byte[] sig = k.sign(msg);
    assertTrue(KeyManager.verify(msg, sig, k.publicKey()));
  }

  @Test
  void tamperedMessageFailsVerification(@TempDir Path dir) throws Exception {
    KeyManager k = KeyManager.loadOrInit(dir);
    byte[] msg = "original".getBytes(StandardCharsets.UTF_8);
    byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
    byte[] sig = k.sign(msg);
    assertFalse(KeyManager.verify(tampered, sig, k.publicKey()));
  }

  @Test
  void partialKeyStateIsRejected(@TempDir Path dir) throws Exception {
    KeyManager.loadOrInit(dir);
    Files.delete(dir.resolve(KeyManager.PUBLIC_KEY_FILENAME));
    assertThrows(IllegalStateException.class, () -> KeyManager.loadOrInit(dir));
  }
}
