/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Contract for every MCP tool exposed by OculiX.
 *
 * <p>A tool has:
 * <ul>
 *   <li>A stable snake_case {@code name} used by the MCP client</li>
 *   <li>A short human-readable {@code description}</li>
 *   <li>A JSON-Schema {@code inputSchema} describing its arguments</li>
 *   <li>An {@link #call} implementation that performs the action</li>
 * </ul>
 *
 * <p>Return value of {@link #call} must be a JSON object conforming to the
 * MCP {@code tools/call} result shape: {@code { content: [...], isError: bool }}.
 */
public interface Tool {

  String name();
  String description();
  JSONObject inputSchema();

  JSONObject call(JSONObject args) throws Exception;

  // ── Result helpers ──

  static JSONObject textResult(String text) {
    return new JSONObject()
        .put("content", new JSONArray().put(
            new JSONObject().put("type", "text").put("text", text)))
        .put("isError", false);
  }

  static JSONObject imageResult(String base64Png, String accompanyingText) {
    JSONArray content = new JSONArray();
    if (accompanyingText != null) {
      content.put(new JSONObject().put("type", "text").put("text", accompanyingText));
    }
    content.put(new JSONObject()
        .put("type", "image")
        .put("data", base64Png)
        .put("mimeType", "image/png"));
    return new JSONObject()
        .put("content", content)
        .put("isError", false);
  }

  static JSONObject errorResult(String message) {
    return new JSONObject()
        .put("content", new JSONArray().put(
            new JSONObject().put("type", "text").put("text", message)))
        .put("isError", true);
  }
}
