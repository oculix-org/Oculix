/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.transport.KeyRing;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeyRingTest {

  @Test
  void inMemoryRingStartsWithOneCurrentKey() {
    KeyRing r = KeyRing.inMemory();
    assertEquals(1, r.size());
    assertNotNull(r.currentKid());
    assertNotNull(r.secret(r.currentKid()));
    assertEquals(KeyRing.SECRET_BYTES, r.secret(r.currentKid()).length);
  }

  @Test
  void generateKeepsOldKidsForVerification() {
    KeyRing r = KeyRing.inMemory();
    String k1 = r.currentKid();
    String k2 = r.generate();

    assertNotEquals(k1, k2);
    assertEquals(k2, r.currentKid());
    assertEquals(2, r.size());
    // Old secret still available for verifying previously-issued tokens.
    assertNotNull(r.secret(k1));
    assertNotNull(r.secret(k2));
  }

  @Test
  void retireRemovesKidButNeverTheCurrent() {
    KeyRing r = KeyRing.inMemory();
    String k1 = r.currentKid();
    String k2 = r.generate();
    // Can retire the old one.
    r.retire(k1);
    assertNull(r.secret(k1));
    assertEquals(1, r.size());
    // Cannot retire the current one.
    assertThrows(IllegalStateException.class, () -> r.retire(k2));
  }

  @Test
  void persistenceRoundTrip(@TempDir Path dir) throws Exception {
    Path p = dir.resolve("ring.json");
    KeyRing r1 = KeyRing.loadOrInit(p);
    String k1 = r1.currentKid();
    String k2 = r1.generate();
    r1.save(p);

    KeyRing r2 = KeyRing.load(p);
    assertEquals(r1.currentKid(), r2.currentKid());
    assertEquals(r1.size(), r2.size());
    assertArrayEquals(r1.secret(k1), r2.secret(k1));
    assertArrayEquals(r1.secret(k2), r2.secret(k2));
  }

  @Test
  void loadOrInitGeneratesOnMissingFile(@TempDir Path dir) throws Exception {
    Path p = dir.resolve("ring.json");
    assertFalse(java.nio.file.Files.exists(p));
    KeyRing r = KeyRing.loadOrInit(p);
    assertEquals(1, r.size());
    assertTrue(java.nio.file.Files.exists(p));
  }

  @Test
  void secretsAreDefensivelyCopied() {
    KeyRing r = KeyRing.inMemory();
    String kid = r.currentKid();
    byte[] copy = r.secret(kid);
    java.util.Arrays.fill(copy, (byte) 0);
    byte[] again = r.secret(kid);
    // The zero-filled copy must not have affected the ring's stored secret.
    assertFalse(java.util.Arrays.equals(copy, again));
  }

  @Test
  void kidsReturnsKnownSet() {
    KeyRing r = KeyRing.inMemory();
    String k1 = r.currentKid();
    String k2 = r.generate();
    Set<String> kids = r.kids();
    assertTrue(kids.contains(k1));
    assertTrue(kids.contains(k2));
    assertEquals(2, kids.size());
  }
}
