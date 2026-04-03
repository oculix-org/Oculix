/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

/**
 * Utility methods for working with Match and Region objects.
 */
public final class MatchUtils {

  private MatchUtils() {
    // utility class
  }

  /**
   * Convert a Match to an int array [x, y, w, h].
   *
   * @param match the Match to convert
   * @return int array with [x, y, width, height]
   */
  public static int[] regionFromMatch(Match match) {
    return new int[]{match.getX(), match.getY(), match.getW(), match.getH()};
  }

  /**
   * Convert a Region to an int array [x, y, w, h].
   *
   * @param region the Region to convert
   * @return int array with [x, y, width, height]
   */
  public static int[] regionToArray(Region region) {
    return new int[]{region.getX(), region.getY(), region.getW(), region.getH()};
  }
}
