/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.mcp.gate.ActionGate;
import org.sikuli.mcp.gate.GateDecision;
import org.sikuli.mcp.tools.Tool;
import org.sikuli.mcp.tools.ToolRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * MCP server over stdio.
 *
 * <p>Implements JSON-RPC 2.0 with one message per line on stdin / stdout.
 *
 * <p>Handled methods:
 * <ul>
 *   <li>{@code initialize} — handshake, captures {@code clientInfo}</li>
 *   <li>{@code notifications/initialized} — silent</li>
 *   <li>{@code tools/list} — returns all registered tools</li>
 *   <li>{@code tools/call} — dispatches to the tool through {@link ActionGate}
 *       and writes an {@code AuditEntry} after every call</li>
 *   <li>{@code ping} — returns an empty result</li>
 * </ul>
 *
 * <p>Any other method returns {@code -32601 METHOD_NOT_FOUND}.
 */
public final class McpServer {

  public static final String PROTOCOL_VERSION = "2024-11-05";
  public static final String SERVER_NAME = "oculix-mcp-server";
  public static final String SERVER_VERSION = "3.0.1";

  private final ToolRegistry tools;
  private final ActionGate gate;
  private final JournalWriter journal;
  private final BufferedReader in;
  private final BufferedWriter out;

  private SessionContext session = SessionContext.empty();

  public McpServer(ToolRegistry tools, ActionGate gate, JournalWriter journal,
                   InputStream stdin, OutputStream stdout) {
    this.tools = tools;
    this.gate = gate;
    this.journal = journal;
    this.in = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
    this.out = new BufferedWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8));
  }

  /**
   * Run the server loop. Blocks until stdin is closed or an I/O error occurs.
   */
  public void run() throws IOException {
    String line;
    while ((line = in.readLine()) != null) {
      if (line.isBlank()) continue;
      JSONObject response;
      try {
        JSONObject req = new JSONObject(line);
        response = handle(req);
      } catch (Exception e) {
        response = JsonRpc.error(JSONObject.NULL, JsonRpc.PARSE_ERROR,
            "Failed to parse request: " + e.getMessage());
      }
      if (response != null) {
        out.write(response.toString());
        out.newLine();
        out.flush();
      }
    }
  }

  // ── Dispatch ──

  private JSONObject handle(JSONObject req) {
    Object id = req.has("id") ? req.get("id") : null;
    String method = req.optString("method", null);
    JSONObject params = req.optJSONObject("params");
    if (params == null) params = new JSONObject();

    if (method == null) {
      return JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Missing 'method'");
    }

    switch (method) {
      case "initialize":
        return handleInitialize(id, params);
      case "notifications/initialized":
      case "notifications/cancelled":
        return null; // notifications get no response
      case "ping":
        return JsonRpc.result(id, new JSONObject());
      case "tools/list":
        return JsonRpc.result(id, new JSONObject().put("tools", tools.listAsJson()));
      case "tools/call":
        return handleToolsCall(id, params);
      default:
        if (method.startsWith("notifications/")) return null;
        return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown method: " + method);
    }
  }

  private JSONObject handleInitialize(Object id, JSONObject params) {
    JSONObject clientInfo = params.optJSONObject("clientInfo");
    this.session = new SessionContext(clientInfo, extractLlmInfo(params));

    JSONObject result = new JSONObject()
        .put("protocolVersion", PROTOCOL_VERSION)
        .put("serverInfo", new JSONObject()
            .put("name", SERVER_NAME)
            .put("version", SERVER_VERSION))
        .put("capabilities", new JSONObject()
            .put("tools", new JSONObject()));
    return JsonRpc.result(id, result);
  }

  private JSONObject handleToolsCall(Object id, JSONObject params) {
    String toolName = params.optString("name", null);
    JSONObject args = params.optJSONObject("arguments");
    if (args == null) args = new JSONObject();

    // Capture per-request LLM metadata if the client provides it
    JSONObject perRequestLlm = extractLlmInfo(params);
    if (perRequestLlm != null) {
      this.session = session.withLlm(perRequestLlm);
    }

    if (toolName == null) {
      return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "Missing tool name");
    }
    Tool tool = tools.get(toolName);
    if (tool == null) {
      return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown tool: " + toolName);
    }

    // Gate first
    GateDecision decision = gate.decide(toolName, args);
    if (!decision.isApproved()) {
      JSONObject denied = Tool.errorResult("Denied by ActionGate: " + decision.reason);
      auditSafely(toolName, args, denied);
      return JsonRpc.result(id, denied);
    }

    // Execute
    JSONObject result;
    try {
      result = tool.call(args);
    } catch (Exception e) {
      result = Tool.errorResult(e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    auditSafely(toolName, args, result);
    return JsonRpc.result(id, result);
  }

  private void auditSafely(String toolName, JSONObject args, JSONObject result) {
    try {
      String resultHash = Hashing.sha256Hex(result.toString());
      journal.appendToolCall(
          session.sessionId, session.clientInfo, session.llmInfo,
          toolName, args, resultHash);
    } catch (Exception e) {
      // Audit write failure is critical: the server must stop rather than
      // execute further tool calls without being able to record them.
      System.err.println("[oculix-mcp] FATAL: failed to write audit entry: " + e);
      System.exit(2);
    }
  }

  /**
   * Capture optional LLM metadata from an MCP {@code _meta} field.
   * Expected shape:
   * <pre>
   *   { "_meta": { "llm": { "backend": "claude-4-sonnet", "user_id": "..." } } }
   * </pre>
   */
  private static JSONObject extractLlmInfo(JSONObject params) {
    JSONObject meta = params.optJSONObject("_meta");
    if (meta == null) return null;
    return meta.optJSONObject("llm");
  }
}
