/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Two-layer auth primitives for the HTTP transport.
 *
 * <p>The MCP HTTP transport distinguishes two kinds of tokens:
 *
 * <dl>
 *   <dt><b>Client token</b> (long-lived, pre-shared)</dt>
 *   <dd>Identifies a trusted client application (qaopslab, Claude Desktop,
 *       MCP Inspector). Distributed out-of-band via
 *       {@code OCULIX_MCP_TOKEN} or similar. Only required to reach the
 *       {@code initialize} endpoint. Optional when the server binds to
 *       loopback on an on-prem host where OS-level controls already
 *       isolate the port. Essentially a PAT-style credential.</dd>
 *
 *   <dt><b>Session bearer</b> (short-lived, server-issued)</dt>
 *   <dd>Minted by the server during {@code initialize} and returned in the
 *       response. Scoped to a single session id; carried as
 *       {@code Authorization: Bearer} on all subsequent requests in that
 *       session. Invalidated on {@code DELETE /mcp} or when the server
 *       shuts down. This is the actual <em>bearer</em> in the OAuth sense —
 *       never persisted, never shared across sessions.</dd>
 * </dl>
 *
 * <p>This class provides:
 * <ul>
 *   <li>{@link StaticToken} — a constant-time verifier for the pre-shared
 *       client token. Used to gate {@code initialize}. Compare in constant
 *       time via {@link StaticToken#accepts(String)}.</li>
 *   <li>{@link #generateToken()} — URL-safe 256-bit random string used
 *       both to mint session bearers and to seed one-off client tokens.</li>
 * </ul>
 *
 * <p>The session bearer itself does not need a dedicated type — it is
 * just a string stored in the session record alongside the session id,
 * with {@link MessageDigest#isEqual} used to compare at the edge.
 */
public final class BearerAuth {

  private static final String PREFIX = "Bearer ";

  private BearerAuth() {}

  /**
   * Verifier for a single pre-shared client token. The plaintext is never
   * held beyond the constructor; only its SHA-256 digest is kept for
   * constant-time comparison against incoming {@code Authorization}
   * header values.
   */
  public static final class StaticToken {
    private final byte[] expectedDigest;

    public StaticToken(String token) {
      if (token == null || token.isBlank()) {
        throw new IllegalArgumentException("Token must be non-empty");
      }
      this.expectedDigest = sha256(token);
    }

    /**
     * Validate an {@code Authorization} header value. Returns {@code true}
     * iff the header is well-formed and matches the configured token.
     */
    public boolean accepts(String authorizationHeader) {
      String presented = extractBearer(authorizationHeader);
      if (presented == null) return false;
      return MessageDigest.isEqual(expectedDigest, sha256(presented));
    }
  }

  /**
   * Constant-time equality check between a supplied Authorization header
   * and an expected plaintext bearer. Used to verify per-session bearers
   * where holding a digest upfront isn't worth the ceremony.
   */
  public static boolean matches(String authorizationHeader, String expectedBearer) {
    String presented = extractBearer(authorizationHeader);
    if (presented == null || expectedBearer == null) return false;
    byte[] a = presented.getBytes(StandardCharsets.UTF_8);
    byte[] b = expectedBearer.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(a, b);
  }

  /**
   * Extract the bearer credential from an {@code Authorization} header.
   * Returns {@code null} if the header is missing, malformed, or empty.
   */
  public static String extractBearer(String authorizationHeader) {
    if (authorizationHeader == null) return null;
    if (!authorizationHeader.startsWith(PREFIX)) return null;
    String presented = authorizationHeader.substring(PREFIX.length()).trim();
    return presented.isEmpty() ? null : presented;
  }

  /**
   * Generate a URL-safe random token, ~256 bits of entropy. Used to mint
   * session bearers at {@code initialize} time.
   */
  public static String generateToken() {
    byte[] buf = new byte[32];
    new SecureRandom().nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  private static byte[] sha256(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
