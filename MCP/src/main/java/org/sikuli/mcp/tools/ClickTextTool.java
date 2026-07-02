/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.FindFailed;
import org.sikuli.script.Match;
import org.sikuli.script.Region;
import org.sikuli.script.Screen;

/**
 * OCR-find a text and click its bounding box in a single round-trip.
 *
 * <p>Composes {@link FindTextTool} + {@link ClickAtPointTool}: searches via
 * the SikuliX historical OCR pipeline (upscale + Tesseract), then clicks
 * the center of the match. Halves the MCP round-trips for a UI navigation
 * step and removes the desync risk where the screen changes between the
 * find and the click.
 *
 * <p>Returns the bounding box of what was clicked, so the LLM can verify
 * <em>which</em> match was acted on when the text appears more than once.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.5
 */
public final class ClickTextTool implements Tool {

  @Override public String name()        { return "oculix_click_text"; }
  @Override public String description() {
    return "Find a text on screen via OCR and click the center of its bounding box. "
        + "Pair with a 'region' to disambiguate when the text appears more than once "
        + "(e.g. 'Suivant' in both header and footer). Returns the clicked bbox.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("text", new JSONObject().put("type", "string"))
            .put("button", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("left").put("right").put("double"))
                .put("description", "Click type, default 'left'"))
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region to restrict OCR. Strongly recommended for "
                    + "multi-region UIs to avoid clicking the wrong match. Full screen 0 if omitted.")))
        .put("required", new JSONArray().put("text"));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    String text = args.getString("text");
    String button = args.optString("button", "left").toLowerCase();

    Region region;
    JSONObject regionJson = args.optJSONObject("region");
    if (regionJson != null && regionJson.has("x") && regionJson.has("y")
        && regionJson.has("width") && regionJson.has("height")) {
      region = RegionSpec.fromJson(regionJson);
    } else {
      Screen s = new Screen();
      region = Region.create(s.x, s.y, s.w, s.h, s);
    }

    Match m;
    try {
      m = region.findText(text);
    } catch (FindFailed ff) {
      return Tool.textResult(new JSONObject()
          .put("clicked", false)
          .put("found", false)
          .put("text", text)
          .toString());
    }

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
        .put("found", true)
        .put("text", text)
        .put("x", m.x).put("y", m.y).put("width", m.w).put("height", m.h)
        .put("button", button)
        .toString());
  }
}
