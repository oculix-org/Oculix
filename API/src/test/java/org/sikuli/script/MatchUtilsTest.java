/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchUtilsTest {

  @Test
  void regionFromMatchReturnsCorrectArray() {
    // Match extends Region — we can construct one with known coords
    Match match = new Match();
    match.x = 100;
    match.y = 200;
    match.w = 50;
    match.h = 30;

    int[] result = MatchUtils.regionFromMatch(match);

    assertArrayEquals(new int[]{100, 200, 50, 30}, result);
  }

  @Test
  void regionToArrayReturnsCorrectArray() {
    Region region = new Region(10, 20, 300, 400);

    int[] result = MatchUtils.regionToArray(region);

    assertEquals(10, result[0]);
    assertEquals(20, result[1]);
    assertEquals(300, result[2]);
    assertEquals(400, result[3]);
  }
}
