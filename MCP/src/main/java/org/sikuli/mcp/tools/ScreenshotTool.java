/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONObject;
import org.sikuli.script.*;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Capture the whole screen or a sub-region, returning a base64 PNG to the
 * MCP client (via an {@code image} content block).
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class ScreenshotTool implements Tool {

  @Override public String name()        { return "oculix_screenshot"; }
  @Override public String description() {
    return "Capture the screen (or a region) and return it as a PNG image the LLM can see.";
  }

  @Override public JSONObject inputSchema() {
    int nScreens = Screen.getNumberScreens();
    String screenDesc = "Screen index to capture (0-based). "
        + "Defaults to 0 (primary). This machine currently exposes " + nScreens
        + " screen" + (nScreens == 1 ? "" : "s") + ". Ignored when 'region' is provided "
        + "(the region already carries its own screen).";
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region to capture. If omitted, captures the full screen at 'screen_index'."))
            .put("screen_index", new JSONObject()
                .put("type", "integer")
                .put("minimum", 0)
                .put("description", screenDesc)));
  }

  /**
   * Magnification applied to region captures. A taskbar icon is roughly 24
   * pixels wide once Windows DPI scaling has had its way, which is at the
   * edge of what a model can tell apart — a red swirl is Paint or the weather,
   * take your pick. Asking for a smaller region does not help: {@code region}
   * crops, it never zooms.
   */
  private static final int REGION_SCALE = 3;

  @Override public JSONObject call(JSONObject args) throws Exception {
    ScreenImage img;
    int scale = 1;
    int originX = 0;
    int originY = 0;
    JSONObject region = args.optJSONObject("region");
    if (region != null && region.has("x") && region.has("y")
        && region.has("width") && region.has("height")) {
      Region r = RegionSpec.fromJson(region);
      img = r.getScreen().capture(r);
      scale = REGION_SCALE;
      originX = r.x;
      originY = r.y;
    } else {
      // No region (or empty object sent by some clients) — capture full screen at screen_index.
      // JSON-RPC callers sometimes serialise integers as strings, so accept both.
      int idx = args.optInt("screen_index", -1);
      if (idx < 0) {
        String s = args.optString("screen_index", "").trim();
        if (!s.isEmpty()) {
          try { idx = Integer.parseInt(s); } catch (NumberFormatException ignore) {}
        }
      }
      if (idx < 0) idx = 0;
      int nScreens = Screen.getNumberScreens();
      if (idx >= nScreens) {
        throw new IllegalArgumentException("screen_index " + idx + " out of range; this machine has "
            + nScreens + " screen" + (nScreens == 1 ? "" : "s") + " (valid indices: 0.." + (nScreens - 1) + ")");
      }
      img = new Screen(idx).capture();
    }
    BufferedImage bi = img.getImage();

    // Full-screen captures are left alone: tripling 1536x864 would ship a
    // 4608x2592 payload to read a window title. Nearest-neighbour on purpose —
    // a magnified icon should keep its pixel edges, not be smoothed into mush.
    if (scale > 1) {
      int w = bi.getWidth() * scale;
      int h = bi.getHeight() * scale;
      BufferedImage up = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = up.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                         RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(bi, 0, 0, w, h, null);
      g.dispose();
      bi = up;
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, "png", baos);
    String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());

    // width/height describe the image as sent. A caller reading a position off
    // a magnified image has to divide by `scale` and add the origin back, so
    // both travel with it — otherwise the next click lands three times too far
    // from the top-left corner.
    JSONObject meta = new JSONObject()
        .put("width", bi.getWidth())
        .put("height", bi.getHeight())
        .put("scale", scale)
        .put("origin_x", originX)
        .put("origin_y", originY);
    return Tool.imageResult(b64, meta.toString());
  }
}
