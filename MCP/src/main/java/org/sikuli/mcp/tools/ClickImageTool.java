/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.*;

/**
 * Find a reference image on screen and left-click its centre.
 */
public final class ClickImageTool implements Tool {

  @Override public String name()        { return "oculix_click_image"; }
  @Override public String description() {
    return "Find a reference image and left-click its center. Returns found=false if the image is not visible.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("reference_path", new JSONObject().put("type", "string"))
            .put("similarity", new JSONObject()
                .put("type", "number").put("minimum", 0.0).put("maximum", 1.0))
            .put("timeout", new JSONObject()
                .put("type", "number")
                .put("description", "Seconds to wait for the image, default 3")
                .put("minimum", 0.0)))
        .put("required", new JSONArray().put("reference_path"));
  }

  @Override public JSONObject call(JSONObject args) {
    String path = args.getString("reference_path");
    double similarity = args.optDouble("similarity", 0.7);
    double timeout = args.optDouble("timeout", 3.0);

    Pattern pattern = new Pattern(path).similar((float) similarity);
    Screen screen = new Screen();
    Match m = screen.exists(pattern, timeout);
    if (m == null) {
      return Tool.textResult(new JSONObject().put("clicked", false).put("found", false).toString());
    }
    int rc = m.click();
    return Tool.textResult(new JSONObject()
        .put("clicked", rc == 1)
        .put("found", true)
        .put("x", m.x).put("y", m.y).put("width", m.w).put("height", m.h)
        .put("score", m.getScore())
        .toString());
  }
}
