/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active MCP sessions for the HTTP transport.
 *
 * <p>A session is the triple {@code (sid, nonce, exp)} plus the mutable
 * {@link SessionHandle} that the dispatcher reads from and writes to.
 * The plaintext session bearer is <b>never</b> stored — only the
 * {@code nonce} that the {@code TokenIssuer} embedded in the token
 * payload. Verification compares nonces with {@link MessageDigest#isEqual}.
 *
 * <p>This is the server-side half of the two-part authentication:
 * <ol>
 *   <li>The token proves cryptographic integrity (HMAC-SHA256 by kid).</li>
 *   <li>The server-side record proves the session is still live — on
 *       {@code DELETE /mcp} or server shutdown the entry is dropped and
 *       any captured token referring to it stops being accepted, even
 *       within its validity window.</li>
 * </ol>
 */
public final class SessionStore {

  private final Map<String, IssuedSession> byId = new ConcurrentHashMap<>();

  /**
   * Register a new session.
   *
   * @param sessionId the public {@code Mcp-Session-Id}.
   * @param nonce     the server-side secret bound to this session. Must
   *                  match the {@code nonce} field in any token presented
   *                  for this sid.
   * @param expEpochSec UNIX seconds — matches the token's {@code exp}.
   */
  public IssuedSession issue(String sessionId, String nonce, long expEpochSec,
                             SessionHandle handle) {
    Objects.requireNonNull(sessionId, "session id");
    Objects.requireNonNull(nonce, "nonce");
    Objects.requireNonNull(handle, "handle");
    IssuedSession record = new IssuedSession(sessionId, nonce, expEpochSec, handle);
    byId.put(sessionId, record);
    return record;
  }

  public IssuedSession get(String id) {
    return id == null ? null : byId.get(id);
  }

  /**
   * Constant-time nonce comparison. Returns {@code true} iff the session
   * exists and the presented nonce matches. Does not check expiry — the
   * token layer already does that.
   */
  public boolean nonceMatches(String sessionId, String presentedNonce) {
    IssuedSession s = get(sessionId);
    if (s == null || presentedNonce == null) return false;
    byte[] a = s.nonce.getBytes(StandardCharsets.UTF_8);
    byte[] b = presentedNonce.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(a, b);
  }

  public IssuedSession remove(String id) {
    return id == null ? null : byId.remove(id);
  }

  public int size() {
    return byId.size();
  }

  /** Drop all sessions whose {@code expEpochSec} is before {@code nowEpochSec}. */
  public int purgeExpired(long nowEpochSec) {
    int dropped = 0;
    for (Map.Entry<String, IssuedSession> e : byId.entrySet()) {
      if (e.getValue().expEpochSec < nowEpochSec) {
        if (byId.remove(e.getKey(), e.getValue())) dropped++;
      }
    }
    return dropped;
  }

  /** Immutable session record (the handle stays mutable). */
  public static final class IssuedSession {
    public final String sessionId;
    public final String nonce;
    public final long expEpochSec;
    public final SessionHandle handle;

    IssuedSession(String sessionId, String nonce, long expEpochSec, SessionHandle handle) {
      this.sessionId = sessionId;
      this.nonce = nonce;
      this.expEpochSec = expEpochSec;
      this.handle = handle;
    }
  }
}
