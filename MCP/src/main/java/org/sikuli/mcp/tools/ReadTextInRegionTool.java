/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import com.sikulix.ocr.OCREngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Region;

import java.io.File;
import java.util.List;

/**
 * OCR: extract the text contained in a screen region.
 *
 * <p>Uses PaddleOCR if available (external Python microservice),
 * otherwise falls back to the bundled Tesseract engine.
 */
public final class ReadTextInRegionTool implements Tool {

  @Override public String name()        { return "oculix_read_text_in_region"; }
  @Override public String description() {
    return "Extract the text from a screen region via OCR. Uses PaddleOCR if reachable, Tesseract as fallback.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema()))
        .put("required", new JSONArray().put("region"));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    Region region = RegionSpec.fromJson(args.getJSONObject("region"));
    File tmp = OcrHelper.captureToTempFile(region);
    try {
      OCREngine engine = OcrHelper.engine();
      String json = engine.recognize(tmp.getAbsolutePath());
      List<String> texts = engine.parseTexts(json);
      String joined = String.join("\n", texts);
      JSONObject result = new JSONObject()
          .put("engine", engine.getName())
          .put("text", joined)
          .put("lines", new JSONArray(texts));
      return Tool.textResult(result.toString());
    } finally {
      tmp.delete();
    }
  }
}
