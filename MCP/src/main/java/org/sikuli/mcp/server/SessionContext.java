/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Per-session metadata attached to every audit entry.
 *
 * <p>In the stdio transport there is typically one session per process
 * invocation. In the HTTP transport, one server process can serve many
 * concurrent sessions, each identified by its own {@code sessionId}
 * (surfaced to clients via the {@code Mcp-Session-Id} header).
 *
 * <p>{@code SessionContext} is immutable. {@link #withLlm(JSONObject)}
 * returns a new instance that preserves the existing {@code sessionId}
 * — it mutates only the optional LLM metadata provided per request.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class SessionContext {

  public final String sessionId;
  public final JSONObject clientInfo;   // from MCP handshake
  public final JSONObject llmInfo;      // optional, from request _meta
  public final boolean initialized;     // true once initialize has run

  private SessionContext(String sessionId, JSONObject clientInfo,
                         JSONObject llmInfo, boolean initialized) {
    this.sessionId = sessionId;
    this.clientInfo = clientInfo;
    this.llmInfo = llmInfo;
    this.initialized = initialized;
  }

  /** Factory that mints a fresh {@code sessionId} — used at initialize time. */
  public static SessionContext newSession(JSONObject clientInfo, JSONObject llmInfo) {
    return new SessionContext(UUID.randomUUID().toString(), clientInfo, llmInfo, true);
  }

  /**
   * Empty context for the pre-initialize window. The dispatcher checks
   * {@link #initialized} rather than trusting a session id: previously
   * {@code empty()} minted a random UUID, which meant a stray
   * {@code tools/call} before {@code initialize} would be audited under
   * a phantom session that never existed at the protocol level.
   * {@code sessionId} is now {@code null} for the empty context so an
   * accidental leak stands out.
   */
  public static SessionContext empty() {
    return new SessionContext(null, null, null, false);
  }

  /**
   * Return a copy with {@code llmInfo} replaced, preserving the original
   * {@code sessionId}, {@code clientInfo} and {@code initialized} flag.
   * Crucially, this never mints a new session id — otherwise every
   * per-request {@code _meta.llm} would shred session continuity in the
   * audit chain.
   */
  public SessionContext withLlm(JSONObject llm) {
    return new SessionContext(this.sessionId, this.clientInfo, llm, this.initialized);
  }
}
