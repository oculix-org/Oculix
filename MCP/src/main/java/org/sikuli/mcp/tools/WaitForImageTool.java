/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.*;

/**
 * Block until a reference image becomes visible, up to a timeout.
 */
public final class WaitForImageTool implements Tool {

  @Override public String name()        { return "oculix_wait_for_image"; }
  @Override public String description() {
    return "Block until a reference image appears on screen, up to the given timeout in seconds.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("reference_path", new JSONObject().put("type", "string"))
            .put("timeout", new JSONObject()
                .put("type", "number")
                .put("description", "Maximum wait in seconds, default 10")
                .put("minimum", 0.0))
            .put("similarity", new JSONObject()
                .put("type", "number").put("minimum", 0.0).put("maximum", 1.0)))
        .put("required", new JSONArray().put("reference_path"));
  }

  @Override public JSONObject call(JSONObject args) {
    String path = args.getString("reference_path");
    double timeout = args.optDouble("timeout", 10.0);
    double similarity = args.optDouble("similarity", 0.7);
    Pattern pattern = new Pattern(path).similar((float) similarity);
    Screen screen = new Screen();
    Match m = screen.exists(pattern, timeout);
    if (m == null) {
      return Tool.textResult(new JSONObject()
          .put("found", false).put("timed_out", true).toString());
    }
    JSONObject result = new JSONObject()
        .put("found", true)
        .put("x", m.x).put("y", m.y).put("width", m.w).put("height", m.h)
        .put("score", m.getScore());
    return Tool.textResult(result.toString());
  }
}
