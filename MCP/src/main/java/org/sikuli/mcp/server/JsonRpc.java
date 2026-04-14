/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;

/**
 * Minimal JSON-RPC 2.0 helpers for the MCP stdio transport.
 *
 * <p>MCP uses JSON-RPC 2.0 with one JSON object per line on stdio.
 * This class provides builders for the three shapes:
 * <ul>
 *   <li>result response</li>
 *   <li>error response</li>
 *   <li>notification (no id)</li>
 * </ul>
 */
public final class JsonRpc {

  public static final int PARSE_ERROR      = -32700;
  public static final int INVALID_REQUEST  = -32600;
  public static final int METHOD_NOT_FOUND = -32601;
  public static final int INVALID_PARAMS   = -32602;
  public static final int INTERNAL_ERROR   = -32603;

  private JsonRpc() {}

  public static JSONObject result(Object id, JSONObject result) {
    JSONObject msg = new JSONObject();
    msg.put("jsonrpc", "2.0");
    if (id != null) msg.put("id", id);
    msg.put("result", result);
    return msg;
  }

  public static JSONObject error(Object id, int code, String message) {
    JSONObject err = new JSONObject()
        .put("code", code)
        .put("message", message);
    JSONObject msg = new JSONObject();
    msg.put("jsonrpc", "2.0");
    if (id != null) msg.put("id", id);
    msg.put("error", err);
    return msg;
  }

  public static JSONObject notification(String method, JSONObject params) {
    JSONObject msg = new JSONObject();
    msg.put("jsonrpc", "2.0");
    msg.put("method", method);
    if (params != null) msg.put("params", params);
    return msg;
  }
}
