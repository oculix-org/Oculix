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
 * Locate a text string on screen via OCR and return its bounding box.
 *
 * <p>Complements {@link ReadTextInRegionTool} which extracts text;
 * this tool searches for a specific text and returns where it is.
 *
 * <p>Uses {@link Region#findText(String)} directly, which goes through the
 * historical SikuliX OCR pipeline (capture + upscale via {@code largeImageFactor},
 * PSM/OEM from {@code OCR.globalOptions()}, Legerix-bundled tessdata). This
 * is the path proven to read modern UI fonts that the raw {@code OCR.readWords}
 * on a fixed BufferedImage misses for lack of upscale.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class FindTextTool implements Tool {

  @Override public String name()        { return "oculix_find_text"; }
  @Override public String description() {
    return "Search for a text string on screen via OCR. Returns bounding box of the first match, or found=false.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("text", new JSONObject().put("type", "string"))
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region to restrict the search. Full screen 0 if omitted.")))
        .put("required", new JSONArray().put("text"));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    String text = args.getString("text");
    Region region;
    JSONObject regionJson = args.optJSONObject("region");
    if (regionJson != null && regionJson.has("x") && regionJson.has("y")
        && regionJson.has("width") && regionJson.has("height")) {
      region = RegionSpec.fromJson(regionJson);
    } else {
      // No region (or empty object sent by some clients) — search full screen 0
      Screen s = new Screen();
      region = Region.create(s.x, s.y, s.w, s.h, s);
    }
    try {
      Match m = region.findText(text);
      return Tool.textResult(new JSONObject()
          .put("found", true)
          .put("engine", "sikulix-region")
          .put("x", m.x)
          .put("y", m.y)
          .put("width", m.w)
          .put("height", m.h)
          .toString());
    } catch (FindFailed ff) {
      return Tool.textResult(new JSONObject()
          .put("found", false)
          .put("engine", "sikulix-region")
          .toString());
    }
  }
}
