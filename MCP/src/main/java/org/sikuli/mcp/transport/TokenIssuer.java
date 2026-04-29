/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import org.json.JSONObject;
import org.sikuli.mcp.crypto.CanonicalJson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

/**
 * Mint and verify OculiX MCP session tokens.
 *
 * <h3>Wire format</h3>
 *
 * <pre>
 *   ocx.&lt;kid&gt;.&lt;b64url(payload)&gt;.&lt;b64url(hmac)&gt;
 * </pre>
 *
 * <p>Where:
 * <ul>
 *   <li>{@code prefix = "ocx"} — fixed, lets casual reviewers spot an
 *       OculiX token at a glance; also guards against accidental reuse
 *       of a JWT or another bearer format.</li>
 *   <li>{@code kid} — identifies the HMAC key in {@link KeyRing}. Enables
 *       rotation: new tokens are minted with the current kid, old tokens
 *       verify against their original kid until it is retired.</li>
 *   <li>{@code payload} — canonical JSON:
 *       {@code {"aud":"oculix-mcp","sid":"...","nonce":"...","iat":...,"exp":...}}</li>
 *   <li>{@code hmac} — {@code HMAC-SHA256(secret_kid, "ocx." + kid + "." + b64_payload)}</li>
 * </ul>
 *
 * <h3>Verification checks</h3>
 * <ol>
 *   <li>Structural: 4 dot-separated parts, prefix matches.</li>
 *   <li>Kid known in the keyring.</li>
 *   <li>HMAC is valid under {@code kid}'s secret ({@link MessageDigest#isEqual}).</li>
 *   <li>Payload audience is {@code "oculix-mcp"}.</li>
 *   <li>{@code exp} > now (within {@link #CLOCK_SKEW_SECONDS} tolerance).</li>
 *   <li>Required fields present ({@code sid}, {@code nonce}, {@code iat}, {@code exp}).</li>
 * </ol>
 *
 * <p>This class does <b>not</b> perform server-side revocation checks — it
 * only asserts that the token is cryptographically valid and not expired.
 * Callers (i.e. {@code HttpTransport}) must additionally confirm that the
 * {@code sid} is still active and the {@code nonce} matches the one
 * recorded in {@link org.sikuli.mcp.server.SessionStore}. That combination
 * defeats replay of captured tokens after session teardown.
 */
public final class TokenIssuer {

  public static final String PREFIX = "ocx";
  public static final String AUDIENCE = "oculix-mcp";
  public static final long CLOCK_SKEW_SECONDS = 30L;
  public static final int NONCE_BYTES = 32;

  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64DEC = Base64.getUrlDecoder();

  private final KeyRing keys;
  private final Clock clock;
  private final SecureRandom rng = new SecureRandom();

  public TokenIssuer(KeyRing keys) {
    this(keys, Clock.systemUTC());
  }

  /** Test constructor — lets a deterministic {@link Clock} drive iat/exp. */
  public TokenIssuer(KeyRing keys, Clock clock) {
    this.keys = keys;
    this.clock = clock;
  }

  // ── Mint ──

  /**
   * Mint a fresh token for {@code sid} with a lifetime of {@code ttlSeconds}.
   * Returns both the wire string (for the client) and the server-side
   * record so the caller can store {@code (sid, nonce, exp)} in the
   * session store for revocation.
   */
  public Minted mint(String sid, long ttlSeconds) {
    if (sid == null || sid.isEmpty()) {
      throw new IllegalArgumentException("sid must be non-empty");
    }
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("ttlSeconds must be positive");
    }
    long iat = clock.instant().getEpochSecond();
    long exp = iat + ttlSeconds;
    byte[] nonceBytes = new byte[NONCE_BYTES];
    rng.nextBytes(nonceBytes);
    String nonce = B64.encodeToString(nonceBytes);

    JSONObject payload = new JSONObject()
        .put("aud", AUDIENCE)
        .put("sid", sid)
        .put("nonce", nonce)
        .put("iat", iat)
        .put("exp", exp);
    byte[] payloadBytes = CanonicalJson.serialize(payload)
        .getBytes(StandardCharsets.UTF_8);
    String payloadB64 = B64.encodeToString(payloadBytes);

    String kid = keys.currentKid();
    byte[] secret = keys.secret(kid);
    if (secret == null) {
      throw new IllegalStateException("Current kid has no secret: " + kid);
    }
    String signingInput = PREFIX + "." + kid + "." + payloadB64;
    byte[] mac = hmac(secret, signingInput.getBytes(StandardCharsets.US_ASCII));
    String macB64 = B64.encodeToString(mac);

    String token = signingInput + "." + macB64;
    return new Minted(token, new Claims(sid, nonce, iat, exp, kid));
  }

  // ── Verify ──

  /**
   * Parse and validate a token. Returns {@link Claims} on success, or a
   * {@link Rejection} describing the failure reason. Never throws on
   * client errors — only on crypto-unavailability.
   */
  public Result verify(String wireToken) {
    if (wireToken == null || wireToken.isEmpty()) {
      return Rejection.MALFORMED;
    }
    int p1 = wireToken.indexOf('.');
    int p2 = p1 < 0 ? -1 : wireToken.indexOf('.', p1 + 1);
    int p3 = p2 < 0 ? -1 : wireToken.indexOf('.', p2 + 1);
    if (p3 < 0 || wireToken.indexOf('.', p3 + 1) >= 0) {
      return Rejection.MALFORMED;
    }
    String prefix = wireToken.substring(0, p1);
    String kid = wireToken.substring(p1 + 1, p2);
    String payloadB64 = wireToken.substring(p2 + 1, p3);
    String macB64 = wireToken.substring(p3 + 1);

    if (!PREFIX.equals(prefix)) return Rejection.MALFORMED;
    if (kid.isEmpty() || payloadB64.isEmpty() || macB64.isEmpty()) {
      return Rejection.MALFORMED;
    }

    byte[] secret = keys.secret(kid);
    if (secret == null) return Rejection.UNKNOWN_KID;

    String signingInput = PREFIX + "." + kid + "." + payloadB64;
    byte[] expected = hmac(secret, signingInput.getBytes(StandardCharsets.US_ASCII));
    byte[] presented;
    try {
      presented = B64DEC.decode(macB64);
    } catch (IllegalArgumentException e) {
      return Rejection.MALFORMED;
    }
    if (!MessageDigest.isEqual(expected, presented)) {
      return Rejection.BAD_SIGNATURE;
    }

    JSONObject payload;
    try {
      byte[] payloadBytes = B64DEC.decode(payloadB64);
      payload = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));
    } catch (Exception e) {
      return Rejection.MALFORMED;
    }

    String aud = payload.optString("aud", null);
    String sid = payload.optString("sid", null);
    String nonce = payload.optString("nonce", null);
    if (aud == null || sid == null || nonce == null
        || !payload.has("iat") || !payload.has("exp")) {
      return Rejection.MALFORMED;
    }
    if (!AUDIENCE.equals(aud)) return Rejection.WRONG_AUDIENCE;

    long iat = payload.getLong("iat");
    long exp = payload.getLong("exp");
    long now = clock.instant().getEpochSecond();
    if (now + CLOCK_SKEW_SECONDS < iat) return Rejection.NOT_YET_VALID;
    if (exp + CLOCK_SKEW_SECONDS < now) return Rejection.EXPIRED;

    return new Claims(sid, nonce, iat, exp, kid);
  }

  // ── Types ──

  /** Sum type returned by {@link #verify} — either {@link Claims} or {@link Rejection}. */
  public interface Result {}

  /**
   * Successful verification — the parsed and authenticated claims.
   * Still subject to server-side revocation: the caller must check that
   * {@code sid} is live and {@code nonce} matches the server's record.
   */
  public static final class Claims implements Result {
    public final String sid;
    public final String nonce;
    public final long iat;
    public final long exp;
    public final String kid;
    public Claims(String sid, String nonce, long iat, long exp, String kid) {
      this.sid = sid; this.nonce = nonce; this.iat = iat; this.exp = exp; this.kid = kid;
    }
  }

  /** Verification failure. */
  public enum Rejection implements Result {
    MALFORMED,
    UNKNOWN_KID,
    BAD_SIGNATURE,
    WRONG_AUDIENCE,
    NOT_YET_VALID,
    EXPIRED
  }

  /** Minted token bundle — the wire string and the server-side claims. */
  public static final class Minted {
    public final String token;
    public final Claims claims;
    public Minted(String token, Claims claims) {
      this.token = token;
      this.claims = claims;
    }
  }

  private static byte[] hmac(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 not available", e);
    }
  }
}
