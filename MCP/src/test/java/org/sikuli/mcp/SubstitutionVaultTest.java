/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.tools.SubstitutionVault;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
class SubstitutionVaultTest {

  @Test
  void firstWrapReturnsInfo1() {
    SubstitutionVault v = new SubstitutionVault();
    assertEquals("info1", v.wrap("alice@example.com"));
  }

  @Test
  void wrapCounterIsMonotonic() {
    SubstitutionVault v = new SubstitutionVault();
    assertEquals("info1", v.wrap("alice@example.com"));
    assertEquals("info2", v.wrap("bob@example.com"));
    assertEquals("info3", v.wrap("carol@example.com"));
  }

  @Test
  void wrappingSameValueTwiceReturnsSameToken() {
    SubstitutionVault v = new SubstitutionVault();
    String first = v.wrap("alice@example.com");
    String second = v.wrap("alice@example.com");
    assertEquals(first, second, "idempotent wrap");
    assertEquals(1, v.size(), "no duplicate token allocated");
  }

  @Test
  void differentValuesGetDifferentTokens() {
    SubstitutionVault v = new SubstitutionVault();
    assertNotEquals(v.wrap("a"), v.wrap("b"));
  }

  @Test
  void resolveKnownTokenReturnsRealValue() {
    SubstitutionVault v = new SubstitutionVault();
    String t = v.wrap("secret-iban-FR7612345");
    assertEquals("secret-iban-FR7612345", v.resolve(t));
  }

  @Test
  void resolveUnknownStringPassesThroughUnchanged() {
    // A tool that types "Suivant" or a filename "photo.png" must not
    // be tampered with by the resolver. Only registered tokens change.
    SubstitutionVault v = new SubstitutionVault();
    v.wrap("alice@example.com");
    assertEquals("Suivant", v.resolve("Suivant"));
    assertEquals("info99", v.resolve("info99"),
        "unregistered token-shaped string must pass through");
    assertEquals("random text", v.resolve("random text"));
  }

  @Test
  void resolveNullReturnsNull() {
    SubstitutionVault v = new SubstitutionVault();
    assertNull(v.resolve(null));
  }

  @Test
  void isKnownTokenDistinguishesRegisteredFromLookalike() {
    SubstitutionVault v = new SubstitutionVault();
    String t = v.wrap("alice@example.com");
    assertTrue(v.isKnownToken(t));
    assertFalse(v.isKnownToken("info42"),
        "unregistered token-shaped string is not known");
    assertFalse(v.isKnownToken(null));
    assertFalse(v.isKnownToken("Suivant"));
  }

  @Test
  void wrapNullThrowsNpe() {
    SubstitutionVault v = new SubstitutionVault();
    assertThrows(NullPointerException.class, () -> v.wrap(null));
  }

  @Test
  void sizeReflectsWrapCount() {
    SubstitutionVault v = new SubstitutionVault();
    assertEquals(0, v.size());
    v.wrap("a");
    assertEquals(1, v.size());
    v.wrap("b");
    v.wrap("a"); // dedup, no increment
    assertEquals(2, v.size());
  }

  @Test
  void tokenPatternRecognisesEveryWrappedToken() {
    SubstitutionVault v = new SubstitutionVault();
    for (int i = 1; i <= 5; i++) {
      String t = v.wrap("value-" + i);
      assertTrue(SubstitutionVault.TOKEN_PATTERN.matcher(t).matches(),
          () -> "token '" + t + "' should match TOKEN_PATTERN");
    }
  }
}
