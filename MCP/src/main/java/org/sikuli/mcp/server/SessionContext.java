/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Per-session metadata attached to every audit entry.
 *
 * <p>One MCP server process may serve many consecutive initialize/shutdown
 * cycles, but in the stdio transport there is typically one session per
 * process invocation. The session id is regenerated on every
 * {@code initialize} handshake.
 */
public final class SessionContext {

  public final String sessionId;
  public final JSONObject clientInfo;   // from MCP handshake
  public final JSONObject llmInfo;      // optional, from request _meta

  public SessionContext(JSONObject clientInfo, JSONObject llmInfo) {
    this.sessionId = UUID.randomUUID().toString();
    this.clientInfo = clientInfo;
    this.llmInfo = llmInfo;
  }

  public static SessionContext empty() {
    return new SessionContext(null, null);
  }

  public SessionContext withLlm(JSONObject llm) {
    SessionContext ctx = new SessionContext(this.clientInfo, llm);
    return ctx;
  }
}
