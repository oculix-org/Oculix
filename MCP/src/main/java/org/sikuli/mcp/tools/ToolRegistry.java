/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory registry of the tools exposed by the MCP server.
 *
 * <p>Registration order is preserved so that {@code tools/list} returns
 * the tools in a stable, readable order.
 */
public final class ToolRegistry {

  private final Map<String, Tool> tools = new LinkedHashMap<>();

  public void register(Tool tool) {
    if (tools.containsKey(tool.name())) {
      throw new IllegalStateException("Duplicate tool name: " + tool.name());
    }
    tools.put(tool.name(), tool);
  }

  public Tool get(String name) {
    return tools.get(name);
  }

  public JSONArray listAsJson() {
    JSONArray arr = new JSONArray();
    for (Tool t : tools.values()) {
      arr.put(new JSONObject()
          .put("name", t.name())
          .put("description", t.description())
          .put("inputSchema", t.inputSchema()));
    }
    return arr;
  }

  public int size() { return tools.size(); }

  /**
   * Build the default V1 registry with the 9 OculiX tools.
   */
  public static ToolRegistry defaultRegistry() {
    ToolRegistry r = new ToolRegistry();
    r.register(new FindImageTool());
    r.register(new ClickImageTool());
    r.register(new ExistsImageTool());
    r.register(new WaitForImageTool());
    r.register(new ScreenshotTool());
    r.register(new TypeTextTool());
    r.register(new KeyComboTool());
    r.register(new FindTextTool());
    r.register(new ReadTextInRegionTool());
    return r;
  }
}
