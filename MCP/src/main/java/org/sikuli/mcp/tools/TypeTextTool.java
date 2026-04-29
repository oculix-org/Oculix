/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Screen;

/**
 * Type a literal text string at the current keyboard focus.
 *
 * <p>Does not handle modifiers — use {@link KeyComboTool} for Ctrl+C, Cmd+Tab, etc.
 */
public final class TypeTextTool implements Tool {

  @Override public String name()        { return "oculix_type_text"; }
  @Override public String description() {
    return "Type literal text at the current keyboard focus. For shortcuts and modifiers, use oculix_key_combo instead.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("text", new JSONObject()
                .put("type", "string")
                .put("description", "Text to type")))
        .put("required", new JSONArray().put("text"));
  }

  @Override public JSONObject call(JSONObject args) {
    String text = args.getString("text");
    int rc = new Screen().type(text);
    return Tool.textResult(new JSONObject()
        .put("typed", rc == 1)
        .put("length", text.length()).toString());
  }
}
