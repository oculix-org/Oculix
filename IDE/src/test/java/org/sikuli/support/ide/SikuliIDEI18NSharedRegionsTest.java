/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.ide;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class SikuliIDEI18NSharedRegionsTest {

  private static ResourceBundle bundle(String language, String country) {
    return ResourceBundle.getBundle("i18n/IDE", new Locale(language, country), SikuliIDEI18N.SHARED_REGIONS);
  }

  @Test
  void hongKongAndMacauReadTheTraditionalChineseBundle() {
    assertEquals(Locale.TRADITIONAL_CHINESE, bundle("zh", "HK").getLocale());
    assertEquals(Locale.TRADITIONAL_CHINESE, bundle("zh", "MO").getLocale());
    assertEquals(bundle("zh", "TW").getString("winPreferences"), bundle("zh", "HK").getString("winPreferences"));
  }

  @Test
  void singaporeAndMalaysiaReadTheSimplifiedChineseBundle() {
    assertEquals(Locale.SIMPLIFIED_CHINESE, bundle("zh", "SG").getLocale());
    assertEquals(Locale.SIMPLIFIED_CHINESE, bundle("zh", "MY").getLocale());
    assertEquals(bundle("zh", "CN").getString("winPreferences"), bundle("zh", "SG").getString("winPreferences"));
  }

  @Test
  void regionsWithTheirOwnBundleAreUntouched() {
    assertEquals(Locale.TRADITIONAL_CHINESE, bundle("zh", "TW").getLocale());
    assertEquals(Locale.SIMPLIFIED_CHINESE, bundle("zh", "CN").getLocale());
    assertEquals(new Locale("fr"), bundle("fr", "").getLocale());
    assertEquals(new Locale("pt", "BR"), bundle("pt", "BR").getLocale());
  }

  @Test
  void aRegionWithoutAnyChineseBundleStillFallsBackToEnglish() {
    ResourceBundle plain = ResourceBundle.getBundle("i18n/IDE", new Locale("zh", "HK"));
    assertNotEquals(Locale.TRADITIONAL_CHINESE, plain.getLocale(), "without the shared lookup Hong Kong gets no Chinese");
  }
}
