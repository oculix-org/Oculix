/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR: extract the text contained in a screen region.
 *
 * <p>Uses {@link Region#text()} directly, which goes through the historical
 * SikuliX OCR pipeline (capture + upscale via {@code largeImageFactor},
 * PSM/OEM from {@code OCR.globalOptions()}, Legerix-bundled tessdata). This
 * is the path proven to read modern UI fonts that the raw {@code OCR.readWords}
 * on a fixed BufferedImage misses for lack of upscale.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class ReadTextInRegionTool implements Tool {

  @Override public String name()        { return "oculix_read_text_in_region"; }
  @Override public String description() {
    return "Extract the text from a screen region via OCR (SikuliX historical pipeline with upscale).";
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
    String fullText = region.text();
    List<String> lines = new ArrayList<>();
    if (fullText != null && !fullText.isEmpty()) {
      for (String line : fullText.split("\\r?\\n")) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) lines.add(trimmed);
      }
    }
    JSONObject result = new JSONObject()
        .put("engine", "sikulix-region")
        .put("text", fullText == null ? "" : fullText)
        .put("lines", new JSONArray(lines));
    return Tool.textResult(result.toString());
  }
}
