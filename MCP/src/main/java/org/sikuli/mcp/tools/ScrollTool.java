/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Button;
import org.sikuli.script.Region;
import org.sikuli.script.Screen;

/**
 * Scroll the mouse wheel at the center of a region (or screen).
 *
 * <p>Necessary for navigating custom React/JS dropdowns where arrow keys
 * are ignored — the keyboard event reaches the DOM but the component's
 * own listener does not move the visible selection. A wheel event at the
 * mouse position is what real users send, and the component routes it
 * through its scroll handler.
 *
 * <p>Defaults: 3 steps, screen 0, direction "down".
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.5
 */
public final class ScrollTool implements Tool {

  @Override public String name()        { return "oculix_scroll"; }
  @Override public String description() {
    return "Scroll the mouse wheel (up or down) at the center of a region — or the screen if no region. "
        + "Use this to navigate custom JS/React dropdowns where keyboard arrows are ignored "
        + "(e.g. AXA, Salesforce Lightning, MUI Autocomplete).";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("direction", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("up").put("down"))
                .put("description", "Wheel direction, default 'down'"))
            .put("steps", new JSONObject()
                .put("type", "integer")
                .put("minimum", 1)
                .put("description", "Number of wheel notches, default 3"))
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region. The mouse hovers its center before "
                    + "scrolling, so the wheel is consumed by the right component. "
                    + "If omitted, scrolls at the current mouse position on screen 0.")));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    String dirStr = args.optString("direction", "down").toLowerCase();
    int steps = args.optInt("steps", 3);
    int direction = "up".equals(dirStr) ? Button.WHEEL_UP : Button.WHEEL_DOWN;

    Region target;
    JSONObject regionJson = args.optJSONObject("region");
    if (regionJson != null && regionJson.has("x") && regionJson.has("y")
        && regionJson.has("width") && regionJson.has("height")) {
      target = RegionSpec.fromJson(regionJson);
      // hover at center so the wheel event is consumed by the right component
      target.hover();
    } else {
      Screen s = new Screen();
      target = Region.create(s.x, s.y, s.w, s.h, s);
    }

    int rc = target.wheel(direction, steps);

    return Tool.textResult(new JSONObject()
        .put("scrolled", rc == 1)
        .put("direction", dirStr)
        .put("steps", steps)
        .toString());
  }
}
