/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import java.util.List;

/**
 * Composite keywords for OculiX — higher-level operations built on SikuliX primitives.
 * <p>
 * Each instance is bound to a {@link Region} (local screen, VNC, ADB).
 * No static state — safe for concurrent sessions.
 * </p>
 */
public class OculixKeywords {

  private Region region;

  /**
   * Create keywords bound to a specific region.
   *
   * @param region the region to operate on (Screen, VNCScreen, ADBScreen, or sub-region)
   */
  public OculixKeywords(Region region) {
    if (region == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    this.region = region;
  }

  /**
   * Get the current working region.
   *
   * @return the bound region
   */
  public Region getRegion() {
    return region;
  }

  /**
   * Set a new working region.
   *
   * @param region the new region
   */
  public void setRegion(Region region) {
    if (region == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    this.region = region;
  }

  /**
   * Get the similarity score of an image on screen.
   * Returns 0.0 if the image is not found (no exception thrown).
   *
   * @param locator image locator (e.g. "btn.png", "btn.png=0.9", or text)
   * @return similarity score between 0.0 and 1.0
   */
  public double getMatchScore(String locator) {
    Pattern pattern = Pattern.fromLocator(locator);
    try {
      Match match = region.find(pattern);
      if (match != null) {
        return match.getScore();
      }
      return 0.0;
    } catch (FindFailed e) {
      return 0.0;
    }
  }

  /**
   * Count the number of occurrences of an image on screen.
   * Returns 0 if the image is not found (no exception thrown).
   *
   * @param locator image locator (e.g. "btn.png", "btn.png=0.9", or text)
   * @return number of matches found
   */
  public int imageCount(String locator) {
    Pattern pattern = Pattern.fromLocator(locator);
    List<Match> matches = region.findAllList(pattern);
    return matches.size();
  }
}
