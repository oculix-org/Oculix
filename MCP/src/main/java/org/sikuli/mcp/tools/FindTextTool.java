/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import com.sikulix.ocr.OCREngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Region;
import org.sikuli.script.Screen;

import java.io.File;

/**
 * Locate a text string on screen via OCR and return its bounding box.
 *
 * <p>Complements {@link ReadTextInRegionTool} which extracts text;
 * this tool searches for a specific text and returns where it is.
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
    File tmp = OcrHelper.captureToTempFile(region);
    try {
      OCREngine engine = OcrHelper.engine();
      String json = engine.recognize(tmp.getAbsolutePath());
      int[] coords = engine.findTextCoordinates(json, text);
      if (coords == null) {
        return Tool.textResult(new JSONObject()
            .put("found", false).put("engine", engine.getName()).toString());
      }
      return Tool.textResult(new JSONObject()
          .put("found", true)
          .put("engine", engine.getName())
          .put("x", region.x + coords[0])
          .put("y", region.y + coords[1])
          .put("width", coords[2])
          .put("height", coords[3])
          .toString());
    } finally {
      tmp.delete();
    }
  }
}
