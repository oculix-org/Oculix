/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;
import org.sikuli.basics.Settings;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Pattern.fromLocator() parsing logic.
 * Uses parseImageLocator() and isImageLocator() to avoid OpenCV native dependency.
 */
class PatternFromLocatorTest {

  // ── isImageLocator ────────────────────────────────────────────────────

  @Test
  void pngIsImage() {
    assertTrue(Pattern.isImageLocator("button.png"));
  }

  @Test
  void pngWithSimilarityIsImage() {
    assertTrue(Pattern.isImageLocator("button.png=0.9"));
  }

  @Test
  void jpgIsImage() {
    assertTrue(Pattern.isImageLocator("photo.jpg"));
  }

  @Test
  void jpegIsImage() {
    assertTrue(Pattern.isImageLocator("photo.jpeg"));
  }

  @Test
  void textIsNotImage() {
    assertFalse(Pattern.isImageLocator("Click here"));
  }

  @Test
  void textWithEqualsIsNotImage() {
    assertFalse(Pattern.isImageLocator("x = 5"));
  }

  @Test
  void caseInsensitivePng() {
    assertTrue(Pattern.isImageLocator("Button.PNG"));
  }

  // ── parseImageLocator — image with default similarity ─────────────────

  @Test
  void imageWithDefaultSimilarity() {
    String[] result = Pattern.parseImageLocator("button.png");
    assertNotNull(result);
    assertEquals("button.png", result[0]);
    assertEquals(String.valueOf((float) Settings.MinSimilarity), result[1]);
  }

  // ── parseImageLocator — image with explicit similarity ────────────────

  @Test
  void imageWithExplicitSimilarity() {
    String[] result = Pattern.parseImageLocator("button.png=0.85");
    assertNotNull(result);
    assertEquals("button.png", result[0]);
    assertEquals("0.85", result[1]);
  }

  @Test
  void imageWithSpacesAroundEquals() {
    String[] result = Pattern.parseImageLocator("button.png = 0.9");
    assertNotNull(result);
    assertEquals("button.png", result[0]);
    assertEquals("0.9", result[1]);
  }

  @Test
  void jpgImageWithSimilarity() {
    String[] result = Pattern.parseImageLocator("photo.jpg=0.95");
    assertNotNull(result);
    assertEquals("photo.jpg", result[0]);
    assertEquals("0.95", result[1]);
  }

  @Test
  void jpegImageWithSimilarity() {
    String[] result = Pattern.parseImageLocator("photo.jpeg=0.8");
    assertNotNull(result);
    assertEquals("photo.jpeg", result[0]);
    assertEquals("0.8", result[1]);
  }

  // ── parseImageLocator — text patterns ─────────────────────────────────

  @Test
  void textPatternReturnsNull() {
    assertNull(Pattern.parseImageLocator("Click here"));
  }

  // ── parseImageLocator — boundary values ───────────────────────────────

  @Test
  void exactBoundarySimilarityZero() {
    String[] result = Pattern.parseImageLocator("button.png=0.0");
    assertNotNull(result);
    assertEquals("0.0", result[1]);
  }

  @Test
  void exactBoundarySimilarityOne() {
    String[] result = Pattern.parseImageLocator("button.png=1.0");
    assertNotNull(result);
    assertEquals("1.0", result[1]);
  }

  // ── parseImageLocator — error cases ───────────────────────────────────

  @Test
  void similarityTooHigh() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.parseImageLocator("button.png=1.5"));
  }

  @Test
  void similarityTooLow() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.parseImageLocator("button.png=-0.1"));
  }

  @Test
  void similarityNotANumber() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.parseImageLocator("button.png=abc"));
  }

  @Test
  void nullLocator() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.parseImageLocator(null));
  }

  @Test
  void emptyLocator() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.parseImageLocator(""));
  }
}
