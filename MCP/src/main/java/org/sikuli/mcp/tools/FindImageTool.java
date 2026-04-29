/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.*;

/**
 * Locate a reference image on screen and return its coordinates.
 *
 * <p>Does not click, does not wait. For a waiting variant use
 * {@link WaitForImageTool}.
 */
public final class FindImageTool implements Tool {

  @Override public String name()        { return "oculix_find_image"; }
  @Override public String description() {
    return "Search for a reference image on screen and return its coordinates (x, y, width, height, score). Non-blocking.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("reference_path", new JSONObject()
                .put("type", "string")
                .put("description", "Absolute path to the reference image (PNG/JPG)"))
            .put("similarity", new JSONObject()
                .put("type", "number")
                .put("description", "Match similarity threshold (0.0 to 1.0), default 0.7")
                .put("minimum", 0.0).put("maximum", 1.0)))
        .put("required", new JSONArray().put("reference_path"));
  }

  @Override public JSONObject call(JSONObject args) {
    String path = args.getString("reference_path");
    double similarity = args.optDouble("similarity", 0.7);

    Pattern pattern = new Pattern(path).similar((float) similarity);
    Screen screen = new Screen();
    Match m = screen.exists(pattern);
    if (m == null) {
      return Tool.textResult("{\"found\": false}");
    }
    JSONObject result = new JSONObject()
        .put("found", true)
        .put("x", m.x)
        .put("y", m.y)
        .put("width", m.w)
        .put("height", m.h)
        .put("score", m.getScore());
    return Tool.textResult(result.toString());
  }
}
