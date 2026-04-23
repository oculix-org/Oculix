/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import com.sikulix.ocr.OCREngine;
import com.sikulix.ocr.PaddleOCREngine;
import com.sikulix.ocr.TesseractEngine;
import org.sikuli.script.Region;
import org.sikuli.script.ScreenImage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;

/**
 * Shared OCR helper for the MCP tools that need text recognition.
 *
 * <p>Selects an engine lazily: tries PaddleOCR first (via the local Flask
 * microservice {@code paddleocr-server}), falls back to Tesseract if
 * PaddleOCR is not reachable.
 *
 * <p>The chosen engine is cached for the lifetime of the server.
 */
public final class OcrHelper {

  private static volatile OCREngine cachedEngine;

  private OcrHelper() {}

  public static OCREngine engine() {
    OCREngine e = cachedEngine;
    if (e != null) return e;
    synchronized (OcrHelper.class) {
      if (cachedEngine != null) return cachedEngine;
      try {
        PaddleOCREngine paddle = new PaddleOCREngine();
        if (paddle.isAvailable()) {
          cachedEngine = paddle;
          return cachedEngine;
        }
      } catch (Throwable ignored) {
        // fall through
      }
      cachedEngine = new TesseractEngine();
      return cachedEngine;
    }
  }

  /**
   * Capture the given region to a temp PNG and return its absolute path.
   * The caller is responsible for deleting the file.
   */
  public static File captureToTempFile(Region region) throws Exception {
    ScreenImage si = region.getScreen().capture(region);
    File tmp = Files.createTempFile("oculix-mcp-ocr-", ".png").toFile();
    ImageIO.write(si.getImage(), "png", tmp);
    return tmp;
  }
}
