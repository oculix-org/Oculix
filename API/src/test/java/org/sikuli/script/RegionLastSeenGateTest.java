/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sikuli.basics.Settings;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class RegionLastSeenGateTest {

  private boolean checkLastSeen;
  private double minSimilarity;

  @BeforeEach
  void saveSettings() {
    checkLastSeen = Settings.CheckLastSeen;
    minSimilarity = Settings.MinSimilarity;
    Settings.CheckLastSeen = true;
    Settings.MinSimilarity = 0.7;
  }

  @AfterEach
  void restoreSettings() {
    Settings.CheckLastSeen = checkLastSeen;
    Settings.MinSimilarity = minSimilarity;
  }

  private static Image seenWithScore(double score) {
    Image img = new Image(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "seen");
    img.setLastSeen(new Rectangle(100, 200, 20, 10), score);
    return img;
  }

  @Test
  void plainImageTakesTheLastSeenPathLikeAPattern() {
    Image img = seenWithScore(0.95);
    Double plain = Region.lastSeenScoreFor(img, null);
    Double pattern = Region.lastSeenScoreFor(img, new Pattern(img));
    assertNotNull(plain);
    assertEquals(0.94, plain, 1e-9);
    assertEquals(plain, pattern);
  }

  @Test
  void neverSeenImageRunsTheFullSearch() {
    Image img = new Image(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "never");
    assertNull(Region.lastSeenScoreFor(img, null));
    assertNull(Region.lastSeenScoreFor(img, new Pattern(img)));
  }

  @Test
  void aStricterRequestThanTheLastScoreRunsTheFullSearch() {
    Image img = seenWithScore(0.705);
    assertNull(Region.lastSeenScoreFor(img, null), "0.695 is below MinSimilarity 0.7");
    assertNull(Region.lastSeenScoreFor(img, new Pattern(img).similar(0.9)));
    assertNotNull(Region.lastSeenScoreFor(img, new Pattern(img).similar(0.6)));
  }

  @Test
  void settingOffDisablesBothTargets() {
    Settings.CheckLastSeen = false;
    Image img = seenWithScore(0.95);
    assertNull(Region.lastSeenScoreFor(img, null));
    assertNull(Region.lastSeenScoreFor(img, new Pattern(img)));
  }
}
