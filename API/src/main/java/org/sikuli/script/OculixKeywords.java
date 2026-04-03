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
  private double timeout = 3.0;

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
   */
  public Region getRegion() {
    return region;
  }

  /**
   * Set a new working region.
   */
  public void setRegion(Region region) {
    if (region == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    this.region = region;
  }

  /**
   * Get the wait timeout in seconds.
   */
  public double getTimeout() {
    return timeout;
  }

  /**
   * Set the wait timeout in seconds.
   */
  public void setTimeout(double timeout) {
    this.timeout = timeout;
  }

  // ── Metrics ────────────────────────────────────────────────────────────

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

  // ── Click In (area + target) ───────────────────────────────────────────

  /**
   * Click on a target image INSIDE an area image.
   *
   * @param areaLocator   locator for the containing area
   * @param targetLocator locator for the click target within the area
   * @return [x, y, w, h] of the matched target
   */
  public int[] clickIn(String areaLocator, String targetLocator) {
    return performClickIn(areaLocator, targetLocator, ClickType.SINGLE);
  }

  /**
   * Double-click on a target image INSIDE an area image.
   */
  public int[] doubleClickIn(String areaLocator, String targetLocator) {
    return performClickIn(areaLocator, targetLocator, ClickType.DOUBLE);
  }

  /**
   * Right-click on a target image INSIDE an area image.
   */
  public int[] rightClickIn(String areaLocator, String targetLocator) {
    return performClickIn(areaLocator, targetLocator, ClickType.RIGHT);
  }

  // ── Click Nth ──────────────────────────────────────────────────────────

  /**
   * Click on the Nth occurrence of an image, sorted by column (left→right).
   *
   * @param locator image locator
   * @param index   0-based index
   * @return [x, y, w, h] of the clicked match
   */
  public int[] clickNth(String locator, int index) {
    return clickNth(locator, index, true);
  }

  /**
   * Click on the Nth occurrence of an image.
   *
   * @param locator      image locator
   * @param index        0-based index
   * @param sortByColumn true = sort left→right (column), false = sort top→bottom (row)
   * @return [x, y, w, h] of the clicked match
   */
  public int[] clickNth(String locator, int index, boolean sortByColumn) {
    Pattern pattern = Pattern.fromLocator(locator);
    List<Match> matches = sortByColumn
        ? region.findAllByColumn(pattern)
        : region.findAllByRow(pattern);
    if (matches == null || matches.isEmpty()) {
      throw new ScreenOperationException(
          "No occurrences of '" + locator + "' found on screen");
    }
    if (index < 0 || index >= matches.size()) {
      throw new ScreenOperationException(
          "Index " + index + " out of bounds, " + matches.size() + " occurrences found for '" + locator + "'");
    }
    Match match = matches.get(index);
    try {
      match.click();
    } catch (FindFailed e) {
      throw new ScreenOperationException("Click on occurrence " + index + " of '" + locator + "' failed", e);
    }
    return MatchUtils.regionFromMatch(match);
  }

  // ── Click Text (OCR) ──────────────────────────────────────────────────

  /**
   * Click on text found via OCR on the full screen.
   *
   * @param text the text to find and click
   * @return [x, y, w, h] of the matched text
   */
  public int[] clickText(String text) {
    return performClickText(text, region);
  }

  /**
   * Click on text found via OCR, limited to the current working region (ROI).
   *
   * @param text the text to find and click
   * @return [x, y, w, h] of the matched text
   */
  public int[] regionClickText(String text) {
    return performClickText(text, region);
  }

  // ── Click Region (by coordinates) ─────────────────────────────────────

  /**
   * Click on a region defined by coordinates.
   *
   * @param x region x
   * @param y region y
   * @param w region width
   * @param h region height
   */
  public void clickRegion(int x, int y, int w, int h) {
    clickRegion(x, y, w, h, 0, 0);
  }

  /**
   * Click on a region with optional change detection.
   *
   * @param x           region x
   * @param y           region y
   * @param w           region width
   * @param h           region height
   * @param waitChange  seconds to wait for region to change after click (0 = skip)
   */
  public void clickRegion(int x, int y, int w, int h, double waitChange) {
    clickRegion(x, y, w, h, waitChange, 0);
  }

  /**
   * Click on a region with optional change detection and highlight.
   *
   * @param x                region x
   * @param y                region y
   * @param w                region width
   * @param h                region height
   * @param waitChange       seconds to wait for region to change after click (0 = skip)
   * @param highlightTimeout seconds to highlight the region (0 = skip)
   */
  public void clickRegion(int x, int y, int w, int h, double waitChange, int highlightTimeout) {
    Region target = new Region(x, y, w, h);
    if (waitChange > 0) {
      // Capture before click for change detection
      Image imgBefore = target.getImage();
      target.click();
      target.waitVanish(imgBefore, waitChange);
    } else {
      target.click();
    }
    if (highlightTimeout > 0) {
      target.highlight(highlightTimeout);
    }
  }

  // ── Click On Match / Region (direct) ──────────────────────────────────

  /**
   * Click directly on a Match object.
   */
  public void clickOnMatch(Match match) {
    if (match == null) {
      throw new IllegalArgumentException("Match must not be null");
    }
    match.click();
  }

  /**
   * Click directly on a Region object.
   */
  public void clickOnRegion(Region target) {
    if (target == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    target.click();
  }

  /**
   * Double-click directly on a Match object.
   */
  public void doubleClickOnMatch(Match match) {
    if (match == null) {
      throw new IllegalArgumentException("Match must not be null");
    }
    try {
      match.doubleClick(match.getTarget());
    } catch (FindFailed e) {
      throw new ScreenOperationException("Double click on match failed", e);
    }
  }

  /**
   * Double-click directly on a Region object.
   */
  public void doubleClickOnRegion(Region target) {
    if (target == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    try {
      target.doubleClick(target.getCenter());
    } catch (FindFailed e) {
      throw new ScreenOperationException("Double click on region failed", e);
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────

  private enum ClickType { SINGLE, DOUBLE, RIGHT }

  private int[] performClickIn(String areaLocator, String targetLocator, ClickType clickType) {
    Pattern areaPattern = Pattern.fromLocator(areaLocator);
    Pattern targetPattern = Pattern.fromLocator(targetLocator);
    Match areaMatch;
    try {
      areaMatch = region.wait(areaPattern, timeout);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "Area '" + areaLocator + "' not found on screen", e);
    }
    Match targetMatch;
    try {
      targetMatch = areaMatch.find(targetPattern);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "Target '" + targetLocator + "' not found inside area '" + areaLocator + "'", e);
    }
    try {
      switch (clickType) {
        case DOUBLE:
          areaMatch.doubleClick(targetPattern);
          break;
        case RIGHT:
          areaMatch.rightClick(targetPattern);
          break;
        default:
          areaMatch.click(targetPattern);
          break;
      }
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          clickType + " click on '" + targetLocator + "' in area '" + areaLocator + "' failed", e);
    }
    return MatchUtils.regionFromMatch(targetMatch);
  }

  private int[] performClickText(String text, Region searchRegion) {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("Text must not be null or empty");
    }
    Match match = searchRegion.existsText(text, timeout);
    if (match == null) {
      throw new ScreenOperationException("Text '" + text + "' not found on screen");
    }
    match.click();
    return MatchUtils.regionFromMatch(match);
  }
}
