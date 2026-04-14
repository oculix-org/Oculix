/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.*;

/**
 * Non-blocking presence check: does a reference image exist on screen right now?
 *
 * <p>Complement of {@link WaitForImageTool} which blocks until the image
 * appears. Returns immediately with {@code found: true/false}.
 */
public final class ExistsImageTool implements Tool {

  @Override public String name()        { return "oculix_exists_image"; }
  @Override public String description() {
    return "Non-blocking check: returns true if a reference image is currently visible on screen.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("reference_path", new JSONObject()
                .put("type", "string")
                .put("description", "Absolute path to the reference image"))
            .put("similarity", new JSONObject()
                .put("type", "number").put("minimum", 0.0).put("maximum", 1.0)))
        .put("required", new JSONArray().put("reference_path"));
  }

  @Override public JSONObject call(JSONObject args) {
    String path = args.getString("reference_path");
    double similarity = args.optDouble("similarity", 0.7);
    Pattern pattern = new Pattern(path).similar((float) similarity);
    Screen screen = new Screen();
    Match m = screen.exists(pattern, 0);
    boolean found = m != null;
    JSONObject result = new JSONObject().put("found", found);
    if (found) {
      result.put("x", m.x).put("y", m.y).put("width", m.w).put("height", m.h)
            .put("score", m.getScore());
    }
    return Tool.textResult(result.toString());
  }
}
