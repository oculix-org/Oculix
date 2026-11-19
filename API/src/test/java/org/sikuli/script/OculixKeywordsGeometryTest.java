/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The coordinate arithmetic of {@link OculixKeywords}. None of it touches a screen,
 * so it runs headless, unlike {@link OculixKeywordsTest}, whose setup needs one.
 */
class OculixKeywordsGeometryTest {

  // getExtendedRegionFromRegion() and fromRegionJumpTo() only do arithmetic on their
  // arguments. A Match built with no coordinates is a Region with no Screen, so this
  // needs no display.
  private final OculixKeywords kw = new OculixKeywords(new Match());

  // ── getExtendedRegionFromRegion (pure math) ───────────────────────────

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
}
