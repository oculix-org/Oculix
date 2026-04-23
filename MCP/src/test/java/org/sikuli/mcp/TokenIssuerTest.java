/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.transport.KeyRing;
import org.sikuli.mcp.transport.TokenIssuer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TokenIssuerTest {

  private static TokenIssuer issuerAt(KeyRing ring, Instant t) {
    return new TokenIssuer(ring, Clock.fixed(t, ZoneOffset.UTC));
  }

  @Test
  void mintThenVerifyRoundtrip() {
    KeyRing ring = KeyRing.inMemory();
    Instant now = Instant.parse("2026-04-15T10:00:00Z");
    TokenIssuer issuer = issuerAt(ring, now);

    TokenIssuer.Minted m = issuer.mint("sess-1", 60);

    TokenIssuer.Result r = issuer.verify(m.token);
    assertTrue(r instanceof TokenIssuer.Claims, "Expected Claims, got " + r);
    TokenIssuer.Claims c = (TokenIssuer.Claims) r;
    assertEquals("sess-1", c.sid);
    assertEquals(m.claims.nonce, c.nonce);
    assertEquals(now.getEpochSecond(), c.iat);
    assertEquals(now.getEpochSecond() + 60, c.exp);
    assertEquals(ring.currentKid(), c.kid);
  }

  @Test
  void tokenFormatHasFourPartsAndPrefix() {
    TokenIssuer issuer = new TokenIssuer(KeyRing.inMemory());
    String token = issuer.mint("x", 60).token;
    String[] parts = token.split("\\.");
    assertEquals(4, parts.length, "Token must have 4 dot-separated parts");
    assertEquals("ocx", parts[0]);
    for (int i = 1; i < 4; i++) {
      assertFalse(parts[i].isEmpty(), "Part " + i + " must not be empty");
    }
  }

  @Test
  void verifyRejectsMalformedTokens() {
    TokenIssuer issuer = new TokenIssuer(KeyRing.inMemory());
    assertSame(TokenIssuer.Rejection.MALFORMED, issuer.verify(null));
    assertSame(TokenIssuer.Rejection.MALFORMED, issuer.verify(""));
    assertSame(TokenIssuer.Rejection.MALFORMED, issuer.verify("not.a.token"));
    assertSame(TokenIssuer.Rejection.MALFORMED, issuer.verify("ocx.kid.payload"));      // 3 parts
    assertSame(TokenIssuer.Rejection.MALFORMED, issuer.verify("ocx.kid.p.s.extra"));    // 5 parts
    assertSame(TokenIssuer.Rejection.MALFORMED,
        issuer.verify("jwt.kid.payload.sig"));                                          // wrong prefix
  }

  @Test
  void verifyRejectsTamperedPayload() {
    TokenIssuer issuer = new TokenIssuer(KeyRing.inMemory());
    String token = issuer.mint("sess-1", 60).token;
    // Swap the payload segment.
    String[] parts = token.split("\\.");
    String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"aud\":\"oculix-mcp\",\"sid\":\"HIJACKED\"}".getBytes());
    String tampered = parts[0] + "." + parts[1] + "." + tamperedPayload + "." + parts[3];
    assertSame(TokenIssuer.Rejection.BAD_SIGNATURE, issuer.verify(tampered));
  }

  @Test
  void verifyRejectsUnknownKid() {
    KeyRing ring = KeyRing.inMemory();
    TokenIssuer issuer = new TokenIssuer(ring);
    String token = issuer.mint("sess-1", 60).token;
    // Replace the kid with garbage.
    String[] parts = token.split("\\.");
    String tampered = parts[0] + ".kUNKNOWN." + parts[2] + "." + parts[3];
    assertSame(TokenIssuer.Rejection.UNKNOWN_KID, issuer.verify(tampered));
  }

  @Test
  void verifyRejectsExpiredToken() {
    KeyRing ring = KeyRing.inMemory();
    Instant issuedAt = Instant.parse("2026-04-15T10:00:00Z");
    TokenIssuer mintingIssuer = issuerAt(ring, issuedAt);
    String token = mintingIssuer.mint("sess-1", 60).token;

    // Advance the clock well past exp + skew.
    TokenIssuer futureIssuer = issuerAt(ring, issuedAt.plus(Duration.ofMinutes(5)));
    assertSame(TokenIssuer.Rejection.EXPIRED, futureIssuer.verify(token));
  }

  @Test
  void verifyToleratesShortClockSkew() {
    KeyRing ring = KeyRing.inMemory();
    Instant issuedAt = Instant.parse("2026-04-15T10:00:00Z");
    TokenIssuer mintingIssuer = issuerAt(ring, issuedAt);
    String token = mintingIssuer.mint("sess-1", 60).token;

    // Ten seconds past exp — well within CLOCK_SKEW_SECONDS (30).
    TokenIssuer slightFuture = issuerAt(ring,
        issuedAt.plusSeconds(60 + 10));
    assertInstanceOf(TokenIssuer.Claims.class, slightFuture.verify(token));
  }

  @Test
  void tokensFromRotatedRingStillVerify() {
    // Mint with kid k1, rotate to k2, verify: token signed by k1 must still work.
    KeyRing ring = KeyRing.inMemory();
    TokenIssuer issuer = new TokenIssuer(ring);
    String k1 = ring.currentKid();
    String token = issuer.mint("sess-1", 60).token;

    String k2 = ring.generate();
    assertNotEquals(k1, k2);
    assertEquals(k2, ring.currentKid());

    TokenIssuer.Result r = issuer.verify(token);
    assertInstanceOf(TokenIssuer.Claims.class, r);
    assertEquals(k1, ((TokenIssuer.Claims) r).kid);

    // Freshly minted tokens use k2.
    String newToken = issuer.mint("sess-2", 60).token;
    TokenIssuer.Claims c2 = (TokenIssuer.Claims) issuer.verify(newToken);
    assertEquals(k2, c2.kid);
  }

  @Test
  void retiredKidMakesTokensUnverifiable() {
    KeyRing ring = KeyRing.inMemory();
    TokenIssuer issuer = new TokenIssuer(ring);
    String k1 = ring.currentKid();
    String token = issuer.mint("sess-1", 60).token;

    ring.generate();  // forces k1 non-current
    ring.retire(k1);  // drop k1 from the ring

    assertSame(TokenIssuer.Rejection.UNKNOWN_KID, issuer.verify(token));
  }

  @Test
  void distinctMintsYieldDistinctNoncesAndTokens() {
    TokenIssuer issuer = new TokenIssuer(KeyRing.inMemory());
    TokenIssuer.Minted a = issuer.mint("sess-1", 60);
    TokenIssuer.Minted b = issuer.mint("sess-1", 60);
    assertNotEquals(a.claims.nonce, b.claims.nonce);
    assertNotEquals(a.token, b.token);
  }

  @Test
  void mintRejectsBadArgs() {
    TokenIssuer issuer = new TokenIssuer(KeyRing.inMemory());
    assertThrows(IllegalArgumentException.class, () -> issuer.mint(null, 60));
    assertThrows(IllegalArgumentException.class, () -> issuer.mint("", 60));
    assertThrows(IllegalArgumentException.class, () -> issuer.mint("sid", 0));
    assertThrows(IllegalArgumentException.class, () -> issuer.mint("sid", -1));
  }
}
