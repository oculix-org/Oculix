/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

import com.sikulix.util.SikuliLogger;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Default OCR engine using Tesseract integrated in SikuliX.
 * Always available, zero config, zero external dependency.
 * Less accurate than PaddleOCR but fully autonomous.
 *
 * Wraps org.sikuli.script.OCR and org.sikuli.script.TextRecognizer
 * to produce output in the unified JSON format used by OCREngine.
 */
public class TesseractEngine implements OCREngine {

    @Override
    public String getName() {
        return "tesseract";
    }

    @Override
    public boolean isAvailable() {
        // Tesseract is always available (bundled with SikuliX)
        return true;
    }

    @Override
    public String recognize(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return createErrorJson("Invalid image path");
        }

        try {
            File imageFile = new File(imagePath.trim());
            if (!imageFile.exists()) {
                return createErrorJson("Image not found: " + imagePath);
            }

            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                return createErrorJson("Cannot read image: " + imagePath);
            }

            // Use reflection to call SikuliX OCR without compile-time dependency
            // This allows the engine to work even if Tesseract isn't fully initialized
            return recognizeWithTesseract(image);

        } catch (Exception e) {
            SikuliLogger.error("[Tesseract] Recognition error: " + e.getMessage());
            return createErrorJson("Tesseract error: " + e.getMessage());
        }
    }

    /**
     * Perform Tesseract OCR via SikuliX's static OCR API.
     *
     * <p><b>Why direct call, not reflection or raw Tess4J:</b> TesseractEngine
     * ships in the same Maven module as {@code org.sikuli.script.OCR}, so the
     * compile-time dependency is fine. The previous implementation fell back
     * to {@code new net.sourceforge.tess4j.Tesseract()} when reflection failed,
     * which silently created an un-configured Tesseract instance — Legerix
     * only configures the static TextRecognizer's Tesseract at startup, NOT
     * arbitrary fresh instances. That fallback produced
     * {@code "Error opening data file ./eng.traineddata"} → 0 texts returned.
     *
     * <p>Going through {@code OCR.readWords} / {@code OCR.readText} hits the
     * same Legerix-configured TextRecognizer used by {@code Region.findText},
     * which is the path proven to work.
     */
    private String recognizeWithTesseract(BufferedImage image) {
        long startTime = System.currentTimeMillis();
        try {
            // Prefer readWords for bounding boxes (parity with Paddle output)
            java.util.List<org.sikuli.script.Match> words =
                org.sikuli.script.OCR.readWords(image);
            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            return buildJsonFromMatches(words, elapsed);
        } catch (Throwable bbErr) {
            // Fallback to readText (no bounding boxes — dummy bbox in output JSON)
            try {
                String text = org.sikuli.script.OCR.readText(image);
                double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                return buildJsonFromText(text, elapsed);
            } catch (Throwable txtErr) {
                SikuliLogger.error("[Tesseract] OCR failed: " + txtErr.getMessage());
                return createErrorJson("Tesseract error: " + txtErr.getMessage());
            }
        }
    }

    /**
     * Build unified JSON from a list of Match objects (SikuliX).
     */
    private String buildJsonFromMatches(List<?> matches, double elapsedSeconds) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"engine\":\"tesseract\",\"recognition_time\":");
        json.append(String.format("%.3f", elapsedSeconds));
        json.append(",\"results\":[");

        boolean first = true;
        for (Object match : matches) {
            try {
                // Use reflection to access Match properties
                Class<?> matchClass = match.getClass();
                String text = "";
                double score = 0.5;
                int mx = 0, my = 0, mw = 0, mh = 0;

                try {
                    java.lang.reflect.Method getTextM = matchClass.getMethod("getText");
                    text = String.valueOf(getTextM.invoke(match));
                } catch (Exception ignored) {}

                try {
                    java.lang.reflect.Method getScoreM = matchClass.getMethod("getScore");
                    score = ((Number) getScoreM.invoke(match)).doubleValue();
                } catch (Exception ignored) {}

                try {
                    mx = ((Number) matchClass.getField("x").get(match)).intValue();
                    my = ((Number) matchClass.getField("y").get(match)).intValue();
                    mw = ((Number) matchClass.getField("w").get(match)).intValue();
                    mh = ((Number) matchClass.getField("h").get(match)).intValue();
                } catch (Exception ignored) {}

                if (text != null && !text.trim().isEmpty()) {
                    if (!first) json.append(",");
                    json.append("{\"text\":\"").append(escapeJson(text.trim()))
                        .append("\",\"confidence\":").append(String.format("%.2f", score))
                        .append(",\"bbox\":[[").append(mx).append(",").append(my)
                        .append("],[").append(mx + mw).append(",").append(my)
                        .append("],[").append(mx + mw).append(",").append(my + mh)
                        .append("],[").append(mx).append(",").append(my + mh)
                        .append("]]}");
                    first = false;
                }
            } catch (Exception ignored) {}
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * Build unified JSON from raw OCR text (no bounding boxes).
     */
    private String buildJsonFromText(String text, double elapsedSeconds) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"engine\":\"tesseract\",\"recognition_time\":");
        json.append(String.format("%.3f", elapsedSeconds));
        json.append(",\"results\":[");

        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.trim().split("\\n");
            boolean first = true;
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    if (!first) json.append(",");
                    json.append("{\"text\":\"").append(escapeJson(trimmed))
                        .append("\",\"confidence\":0.50")
                        .append(",\"bbox\":[[0,0],[100,0],[100,20],[0,20]]}");
                    first = false;
                }
            }
        }

        json.append("]}");
        return json.toString();
    }

    @Override
    public List<String> parseTexts(String json) {
        return PaddleOCRClient.parseTextFromOCRResponse(json);
    }

    @Override
    public int[] findTextCoordinates(String json, String searchText) {
        return PaddleOCRClient.findTextCoordinates(json, searchText);
    }

    @Override
    public List<int[]> findAllTextCoordinates(String json, String searchText) {
        return PaddleOCRClient.findAllTextCoordinates(json, searchText);
    }

    @Override
    public Map<String, Double> parseTextWithConfidence(String json) {
        return PaddleOCRClient.parseTextWithConfidence(json);
    }

    private static String createErrorJson(String message) {
        return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\",\"results\":[]}";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
