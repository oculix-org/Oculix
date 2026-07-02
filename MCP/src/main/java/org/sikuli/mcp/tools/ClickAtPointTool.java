/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Match;
import org.sikuli.script.Region;
import org.sikuli.script.Screen;

/**
 * Left-, right- or double-click at absolute screen coordinates.
 *
 * <p>Designed to be paired with {@code oculix_find_image} or
 * {@code oculix_find_text}, which return a bounding box of an element on
 * screen. Once you have the coordinates, you call this tool to act on
 * them — no need to re-discover the same element via a reference image.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.4
 */
public final class ClickAtPointTool implements Tool {

  @Override public String name()        { return "oculix_click_at_point"; }
  @Override public String description() {
    return "Click at absolute screen coordinates (x, y). Pair with oculix_find_image / oculix_find_text "
        + "which return (x, y, width, height) — feed those coordinates here. "
        + "Defaults: left button, primary screen (0). Use 'button' for right/double click.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("x", new JSONObject()
                .put("type", "integer")
                .put("description", "Absolute X coordinate on the target screen"))
            .put("y", new JSONObject()
                .put("type", "integer")
                .put("description", "Absolute Y coordinate on the target screen"))
            .put("button", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray()
                    .put("left").put("right").put("double"))
                .put("description", "Click type, default 'left'"))
            .put("screen", new JSONObject()
                .put("type", "integer")
                .put("minimum", 0)
                .put("description", "Screen index, default 0 (primary)")))
        .put("required", new JSONArray().put("x").put("y"));
  }

  @Override public JSONObject call(JSONObject args) {
    int x = args.getInt("x");
    int y = args.getInt("y");
    String button = args.optString("button", "left").toLowerCase();
    int screenIdx = args.optInt("screen", 0);

    Screen screen = new Screen(screenIdx);
    // Synthetic 1x1 Match at (x, y) — already located, so Match.click() does
    // not throw FindFailed (unlike Screen.click(Location)).
    Match m = new Match(new Region(x, y, 1, 1, screen), 1.0);

    int rc;
    switch (button) {
      case "right":
        rc = m.rightClick();
        break;
      case "double":
        rc = m.doubleClick();
        break;
      case "left":
      default:
        rc = m.click();
        break;
    }

    return Tool.textResult(new JSONObject()
        .put("clicked", rc == 1)
        .put("x", x)
        .put("y", y)
        .put("button", button)
        .put("screen", screenIdx)
        .toString());
  }
}
