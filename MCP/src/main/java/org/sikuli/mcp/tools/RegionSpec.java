/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONObject;
import org.sikuli.script.Region;
import org.sikuli.script.Screen;

/**
 * Helper for parsing a region parameter from MCP tool arguments.
 *
 * <p>Accepted JSON shape:
 * <pre>
 *   { "screen": 0, "x": 100, "y": 200, "width": 400, "height": 300 }
 * </pre>
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code screen} — optional, defaults to screen 0</li>
 *   <li>{@code x}, {@code y}, {@code width}, {@code height} — required</li>
 * </ul>
 */
public final class RegionSpec {

  private RegionSpec() {}

  public static Region fromJson(JSONObject o) {
    int screenIdx = o.optInt("screen", 0);
    int x = o.getInt("x");
    int y = o.getInt("y");
    int w = o.getInt("width");
    int h = o.getInt("height");
    Screen s = new Screen(screenIdx);
    return Region.create(x, y, w, h, s);
  }

  public static JSONObject jsonSchema() {
    JSONObject schema = new JSONObject();
    schema.put("type", "object");
    JSONObject props = new JSONObject();
    props.put("screen", new JSONObject()
        .put("type", "integer")
        .put("description", "Screen index, defaults to 0")
        .put("minimum", 0));
    props.put("x", new JSONObject().put("type", "integer"));
    props.put("y", new JSONObject().put("type", "integer"));
    props.put("width", new JSONObject().put("type", "integer").put("minimum", 1));
    props.put("height", new JSONObject().put("type", "integer").put("minimum", 1));
    schema.put("properties", props);
    schema.put("required", new org.json.JSONArray()
        .put("x").put("y").put("width").put("height"));
    return schema;
  }
}
