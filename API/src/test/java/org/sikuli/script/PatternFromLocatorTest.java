/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;
import org.sikuli.basics.Settings;

import static org.junit.jupiter.api.Assertions.*;

class PatternFromLocatorTest {

  @Test
  void imageWithDefaultSimilarity() {
    Pattern p = Pattern.fromLocator("button.png");
    assertNotNull(p);
    assertEquals(Settings.MinSimilarity, p.getSimilar(), 0.001);
  }

  @Test
  void imageWithExplicitSimilarity() {
    Pattern p = Pattern.fromLocator("button.png=0.85");
    assertNotNull(p);
    assertEquals(0.85, p.getSimilar(), 0.001);
  }

  @Test
  void imageWithSpacesAroundEquals() {
    Pattern p = Pattern.fromLocator("button.png = 0.9");
    assertNotNull(p);
    assertEquals(0.9, p.getSimilar(), 0.001);
  }

  @Test
  void jpgImage() {
    Pattern p = Pattern.fromLocator("photo.jpg");
    assertNotNull(p);
    assertEquals(Settings.MinSimilarity, p.getSimilar(), 0.001);
  }

  @Test
  void jpgImageWithSimilarity() {
    Pattern p = Pattern.fromLocator("photo.jpg=0.95");
    assertNotNull(p);
    assertEquals(0.95, p.getSimilar(), 0.001);
  }

  @Test
  void jpegImage() {
    Pattern p = Pattern.fromLocator("photo.jpeg=0.8");
    assertNotNull(p);
    assertEquals(0.8, p.getSimilar(), 0.001);
  }

  @Test
  void textPattern() {
    Pattern p = Pattern.fromLocator("Click here");
    assertNotNull(p);
  }

  @Test
  void similarityTooHigh() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.fromLocator("button.png=1.5"));
  }

  @Test
  void similarityTooLow() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.fromLocator("button.png=-0.1"));
  }

  @Test
  void similarityNotANumber() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.fromLocator("button.png=abc"));
  }

  @Test
  void nullLocator() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.fromLocator(null));
  }

  @Test
  void emptyLocator() {
    assertThrows(IllegalArgumentException.class, () ->
        Pattern.fromLocator(""));
  }

  @Test
  void exactBoundarySimilarityZero() {
    Pattern p = Pattern.fromLocator("button.png=0.0");
    assertEquals(0.0, p.getSimilar(), 0.001);
  }

  @Test
  void exactBoundarySimilarityOne() {
    Pattern p = Pattern.fromLocator("button.png=1.0");
    assertEquals(1.0, p.getSimilar(), 0.001);
  }
}
