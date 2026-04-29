/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.transport.BearerAuth;

import static org.junit.jupiter.api.Assertions.*;

class BearerAuthTest {

  @Test
  void staticTokenAcceptsMatchingHeader() {
    BearerAuth.StaticToken t = new BearerAuth.StaticToken("s3cr3t");
    assertTrue(t.accepts("Bearer s3cr3t"));
  }

  @Test
  void staticTokenRejectsWrongScheme() {
    BearerAuth.StaticToken t = new BearerAuth.StaticToken("s3cr3t");
    assertFalse(t.accepts("Basic s3cr3t"));
    assertFalse(t.accepts("s3cr3t"));
    assertFalse(t.accepts(null));
    assertFalse(t.accepts(""));
  }

  @Test
  void staticTokenRejectsEmptyCredential() {
    BearerAuth.StaticToken t = new BearerAuth.StaticToken("s3cr3t");
    assertFalse(t.accepts("Bearer "));
    assertFalse(t.accepts("Bearer    "));
  }

  @Test
  void staticTokenIsCaseSensitive() {
    BearerAuth.StaticToken t = new BearerAuth.StaticToken("s3cr3t");
    assertFalse(t.accepts("Bearer s3cr3T"));
  }

  @Test
  void staticTokenConstructorRejectsBlank() {
    assertThrows(IllegalArgumentException.class, () -> new BearerAuth.StaticToken(""));
    assertThrows(IllegalArgumentException.class, () -> new BearerAuth.StaticToken(null));
    assertThrows(IllegalArgumentException.class, () -> new BearerAuth.StaticToken("   "));
  }

  @Test
  void sessionMatchesIsConstantTime() {
    // Functional check — not a timing test. Verifies that matches() returns
    // true only for the exact bearer.
    assertTrue(BearerAuth.matches("Bearer abc", "abc"));
    assertFalse(BearerAuth.matches("Bearer abc", "abd"));
    assertFalse(BearerAuth.matches("Bearer abc", null));
    assertFalse(BearerAuth.matches(null, "abc"));
    assertFalse(BearerAuth.matches("Basic abc", "abc"));
    // Length mismatch must not short-circuit in an observable way.
    assertFalse(BearerAuth.matches("Bearer a", "abc"));
    assertFalse(BearerAuth.matches("Bearer abcd", "abc"));
  }

  @Test
  void extractBearerHandlesCornerCases() {
    assertEquals("abc", BearerAuth.extractBearer("Bearer abc"));
    assertEquals("abc", BearerAuth.extractBearer("Bearer abc  "));
    assertNull(BearerAuth.extractBearer(null));
    assertNull(BearerAuth.extractBearer(""));
    assertNull(BearerAuth.extractBearer("Bearer"));
    assertNull(BearerAuth.extractBearer("Bearer "));
    assertNull(BearerAuth.extractBearer("bearer abc"));   // case-sensitive scheme
    assertNull(BearerAuth.extractBearer("Basic abc"));
  }

  @Test
  void generatedTokensAreDistinctAndUrlSafe() {
    String a = BearerAuth.generateToken();
    String b = BearerAuth.generateToken();
    assertNotEquals(a, b);
    assertTrue(a.length() >= 40, "Token should carry ≥256 bits of entropy: " + a);
    assertTrue(a.matches("[A-Za-z0-9_-]+"), "Token must be URL-safe: " + a);
  }
}
