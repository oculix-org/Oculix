/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OculixKeywordsTest {

  private OculixKeywords kw;
  private Region region;

  @BeforeEach
  void setUp() {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
    region = new Region(0, 0, 1920, 1080);
    kw = new OculixKeywords(region);
  }

  // ── Constructor & Region management ───────────────────────────────────

  @Test
  void constructorRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> new OculixKeywords(null));
  }

  @Test
  void setRegionRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.setRegion(null));
  }

  @Test
  void getRegionReturnsConstructorRegion() {
    assertSame(region, kw.getRegion());
  }

  @Test
  void setRegionUpdatesRegion() {
    Region r2 = new Region(50, 50, 200, 200);
    kw.setRegion(r2);
    assertSame(r2, kw.getRegion());
  }

  // ── Timeout ───────────────────────────────────────────────────────────

  @Test
  void defaultTimeoutIs3() {
    assertEquals(3.0, kw.getTimeout(), 0.001);
  }

  @Test
  void setTimeoutUpdatesValue() {
    kw.setTimeout(10.0);
    assertEquals(10.0, kw.getTimeout(), 0.001);
  }

  // ── Click guards ──────────────────────────────────────────────────────

  @Test
  void clickTextRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.clickText(null));
  }

  @Test
  void clickTextRejectsEmpty() {
    assertThrows(IllegalArgumentException.class, () -> kw.clickText(""));
  }

  @Test
  void regionClickTextRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.regionClickText(null));
  }

  @Test
  void clickOnMatchRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.clickOnMatch(null));
  }

  @Test
  void clickOnRegionRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.clickOnRegion(null));
  }

  @Test
  void doubleClickOnMatchRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.doubleClickOnMatch(null));
  }

  @Test
  void doubleClickOnRegionRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> kw.doubleClickOnRegion(null));
  }

  // ── waitForMultipleImages guards ──────────────────────────────────────

  @Test
  void waitForMultipleImagesRejectsNullExpected() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.waitForMultipleImages(1.0, 0.5, null, null));
  }

  @Test
  void waitForMultipleImagesRejectsEmptyExpected() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.waitForMultipleImages(1.0, 0.5, Collections.emptyList(), null));
  }

  // ── Region coord validation ───────────────────────────────────────────

  @Test
  void returnMatchFromRegionRejectsNullCoords() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.returnMatchFromRegion(null, "img.png"));
  }

  // ── ROI management ────────────────────────────────────────────────────

  @Test
  void setRoiChangesRegion() {
    kw.setRoi(50, 60, 200, 300);
    Region r = kw.getRegion();
    assertEquals(50, r.getX());
    assertEquals(60, r.getY());
    assertEquals(200, r.getW());
    assertEquals(300, r.getH());
  }

  @Test
  void setRoiWithHighlightChangesRegion() {
    kw.setRoi(10, 20, 100, 100, 0); // 0 = no highlight, but region still changes
    Region r = kw.getRegion();
    assertEquals(10, r.getX());
    assertEquals(20, r.getY());
  }

  // ── Highlight map ─────────────────────────────────────────────────────

  @Test
  void highlightCountStartsAtZero() {
    assertEquals(0, kw.getHighlightCount());
  }

  @Test
  void clearHighlightOnNonExistentDoesNotThrow() {
    assertDoesNotThrow(() -> kw.clearHighlight("nonexistent.png"));
  }

  @Test
  void clearAllHighlightsOnEmptyDoesNotThrow() {
    assertDoesNotThrow(() -> kw.clearAllHighlights());
  }
}
