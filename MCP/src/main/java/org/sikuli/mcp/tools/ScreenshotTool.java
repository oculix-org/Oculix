/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONObject;
import org.sikuli.script.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Capture the whole screen or a sub-region, returning a base64 PNG to the
 * MCP client (via an {@code image} content block).
 */
public final class ScreenshotTool implements Tool {

  @Override public String name()        { return "oculix_screenshot"; }
  @Override public String description() {
    return "Capture the screen (or a region) and return it as a PNG image the LLM can see.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region to capture. If omitted, captures the full screen 0.")));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    ScreenImage img;
    JSONObject region = args.optJSONObject("region");
    if (region != null && region.has("x") && region.has("y")
        && region.has("width") && region.has("height")) {
      Region r = RegionSpec.fromJson(region);
      img = r.getScreen().capture(r);
    } else {
      // No region (or empty object sent by some clients) — capture full screen 0
      img = new Screen().capture();
    }
    BufferedImage bi = img.getImage();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, "png", baos);
    String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());

    JSONObject meta = new JSONObject()
        .put("width", bi.getWidth())
        .put("height", bi.getHeight());
    return Tool.imageResult(b64, meta.toString());
  }
}
