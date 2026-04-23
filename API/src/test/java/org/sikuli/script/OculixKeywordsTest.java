/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OculixKeywordsTest {

  private OculixKeywords kw;
  private Region region;

  @BeforeEach
  void setUp() {
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

  // ── Extended Region geometry (pure math, no screen needed) ────────────

  @Nested
  class ComputeExtendedRegionTest {

    @Test
    void below() {
      int[] result = OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "below", 2);
      assertArrayEquals(new int[]{100, 230, 50, 60}, result);
    }

    @Test
    void above() {
      int[] result = OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "above", 3);
      assertArrayEquals(new int[]{100, 110, 50, 90}, result);
    }

    @Test
    void left() {
      int[] result = OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "left", 2);
      assertArrayEquals(new int[]{0, 200, 100, 30}, result);
    }

    @Test
    void right() {
      int[] result = OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "right", 2);
      assertArrayEquals(new int[]{150, 200, 100, 30}, result);
    }

    @Test
    void original() {
      int[] result = OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "original", 1);
      assertArrayEquals(new int[]{100, 200, 50, 30}, result);
    }

    @Test
    void caseInsensitive() {
      int[] result = OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "BELOW", 1);
      assertArrayEquals(new int[]{100, 230, 50, 30}, result);
    }

    @Test
    void invalidDirectionThrows() {
      assertThrows(IllegalArgumentException.class, () ->
          OculixKeywords.computeExtendedRegion(100, 200, 50, 30, "diagonal", 1));
    }

    @Test
    void multiplierOne() {
      int[] result = OculixKeywords.computeExtendedRegion(0, 0, 100, 100, "below", 1);
      assertArrayEquals(new int[]{0, 100, 100, 100}, result);
    }
  }

  // ── fromRegionJumpTo (pure math) ──────────────────────────────────────

  @Nested
  class FromRegionJumpToTest {

    @Test
    void jumpBelow() {
      int[] result = kw.fromRegionJumpTo(new int[]{100, 100, 50, 30}, "below", 2, 10);
      // y_new = 100 + (30 + 10) * 2 = 100 + 80 = 180
      assertArrayEquals(new int[]{100, 180, 50, 30}, result);
    }

    @Test
    void jumpAbove() {
      int[] result = kw.fromRegionJumpTo(new int[]{100, 300, 50, 30}, "above", 3, 5);
      // y_new = 300 - (30 + 5) * 3 = 300 - 105 = 195
      assertArrayEquals(new int[]{100, 195, 50, 30}, result);
    }

    @Test
    void jumpRight() {
      int[] result = kw.fromRegionJumpTo(new int[]{100, 100, 50, 30}, "right", 1, 0);
      // x_new = 100 + (50 + 0) * 1 = 150
      assertArrayEquals(new int[]{150, 100, 50, 30}, result);
    }

    @Test
    void jumpLeft() {
      int[] result = kw.fromRegionJumpTo(new int[]{200, 100, 50, 30}, "left", 2, 10);
      // x_new = 200 - (50 + 10) * 2 = 200 - 120 = 80
      assertArrayEquals(new int[]{80, 100, 50, 30}, result);
    }

    @Test
    void zeroJumps() {
      int[] result = kw.fromRegionJumpTo(new int[]{100, 100, 50, 30}, "below", 0, 10);
      assertArrayEquals(new int[]{100, 100, 50, 30}, result);
    }

    @Test
    void invalidDirectionThrows() {
      assertThrows(IllegalArgumentException.class, () ->
          kw.fromRegionJumpTo(new int[]{0, 0, 10, 10}, "up", 1, 0));
    }

    @Test
    void nullCoordsThrows() {
      assertThrows(IllegalArgumentException.class, () ->
          kw.fromRegionJumpTo(null, "below", 1, 0));
    }

    @Test
    void wrongLengthCoordsThrows() {
      assertThrows(IllegalArgumentException.class, () ->
          kw.fromRegionJumpTo(new int[]{1, 2, 3}, "below", 1, 0));
    }
  }

  // ── Region coord validation ───────────────────────────────────────────

  @Test
  void getExtendedRegionFromRegionRejectsNullCoords() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.getExtendedRegionFromRegion(null, "below", 1));
  }

  @Test
  void getExtendedRegionFromRegionRejectsWrongLength() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.getExtendedRegionFromRegion(new int[]{1, 2}, "below", 1));
  }

  @Test
  void getExtendedRegionFromRegionComputes() {
    int[] result = kw.getExtendedRegionFromRegion(
        new int[]{100, 200, 50, 30}, "right", 3);
    assertArrayEquals(new int[]{150, 200, 150, 30}, result);
  }

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
