/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Region;
import org.sikuli.script.ScreenImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Block until a region stops changing — a deterministic alternative to
 * arbitrary {@code sleep()} between UI steps.
 *
 * <p>Polls the region at {@code poll_interval_ms}, computing the pixel
 * delta between consecutive frames. When the delta stays under
 * {@code min_pixel_delta} for {@code settle_window_ms} continuous, the
 * region is declared stable and the tool returns. If the deadline
 * {@code timeout_ms} passes without stability, returns {@code stable=false}.
 *
 * <p>Use this after a navigation click to wait for the next page to settle
 * before sending the next action — no more guesswork like {@code sleep 0.8}.
 *
 * <p>The pixel delta is the number of bytes that differ between two
 * consecutive frame buffers (3 bytes per pixel for RGB). For a 960×1080
 * Chrome half-pane that is ~3M bytes; typical stable noise is &lt; 200,
 * a real page swap is &gt; 100 000. Default threshold of 5 000 is a safe
 * middle.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.5
 */
public final class WaitForStableTool implements Tool {

  @Override public String name()        { return "oculix_wait_for_stable"; }
  @Override public String description() {
    return "Block until a region stops changing for settle_window_ms continuous, polling at "
        + "poll_interval_ms. Returns stable=true if the region settled before timeout_ms, "
        + "otherwise stable=false. Use after a navigation click to deterministically wait "
        + "for the next page to settle — no more arbitrary sleep() calls.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Region to observe for stability."))
            .put("settle_window_ms", new JSONObject()
                .put("type", "integer")
                .put("minimum", 100)
                .put("description", "How long the region must stay stable to be declared settled. Default 800."))
            .put("min_pixel_delta", new JSONObject()
                .put("type", "integer")
                .put("minimum", 0)
                .put("description", "Pixel-byte delta below which a frame is considered identical "
                    + "to the previous one. Default 5000 (matches a typical idle Chrome page)."))
            .put("timeout_ms", new JSONObject()
                .put("type", "integer")
                .put("minimum", 100)
                .put("description", "Maximum total wait. Default 10000 (10s)."))
            .put("poll_interval_ms", new JSONObject()
                .put("type", "integer")
                .put("minimum", 50)
                .put("description", "Polling cadence. Default 150ms.")))
        .put("required", new JSONArray().put("region"));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    JSONObject regionJson = args.getJSONObject("region");
    Region region = RegionSpec.fromJson(regionJson);

    int settleWindowMs = args.optInt("settle_window_ms", 800);
    int minPixelDelta  = args.optInt("min_pixel_delta", 5000);
    int timeoutMs      = args.optInt("timeout_ms", 10000);
    int pollIntervalMs = args.optInt("poll_interval_ms", 150);

    long startNs        = System.nanoTime();
    long deadlineNs     = startNs + (long) timeoutMs * 1_000_000L;
    long settleNeededNs = (long) settleWindowMs * 1_000_000L;

    byte[] previous = captureBytes(region);
    long stableSinceNs = startNs;
    int polls = 1;
    int lastDelta = 0;

    while (System.nanoTime() < deadlineNs) {
      Thread.sleep(pollIntervalMs);
      byte[] current = captureBytes(region);
      polls++;
      lastDelta = pixelByteDelta(previous, current);
      if (lastDelta <= minPixelDelta) {
        if (System.nanoTime() - stableSinceNs >= settleNeededNs) {
          long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
          return Tool.textResult(new JSONObject()
              .put("stable", true)
              .put("polls", polls)
              .put("last_delta", lastDelta)
              .put("elapsed_ms", elapsedMs)
              .toString());
        }
      } else {
        // changed — reset the settle window
        stableSinceNs = System.nanoTime();
      }
      previous = current;
    }

    return Tool.textResult(new JSONObject()
        .put("stable", false)
        .put("timed_out", true)
        .put("polls", polls)
        .put("last_delta", lastDelta)
        .put("timeout_ms", timeoutMs)
        .toString());
  }

  private static byte[] captureBytes(Region region) {
    ScreenImage img = region.getScreen().capture(region);
    BufferedImage bi = img.getImage();
    // Force a 3-byte BGR buffer for a stable delta calculation regardless of
    // the underlying screen type (some platforms return INT_RGB, others 3BYTE_BGR).
    BufferedImage normalized = new BufferedImage(
        bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
    normalized.getGraphics().drawImage(bi, 0, 0, null);
    return ((DataBufferByte) normalized.getRaster().getDataBuffer()).getData();
  }

  private static int pixelByteDelta(byte[] a, byte[] b) {
    int n = Math.min(a.length, b.length);
    int delta = 0;
    for (int i = 0; i < n; i++) {
      if (a[i] != b[i]) delta++;
    }
    return delta;
  }
}
