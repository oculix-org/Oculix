/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 */
class OcrBootstrapTest {

  @Test
  void localeFrMapsToFra() {
    assertEquals("fra", OcrBootstrap.resolveLanguage(null, "fr"));
  }

  @Test
  void localeEnMapsToEng() {
    assertEquals("eng", OcrBootstrap.resolveLanguage(null, "en"));
  }

  @Test
  void localeEsMapsToSpa() {
    assertEquals("spa", OcrBootstrap.resolveLanguage(null, "es"));
  }

  @Test
  void localeHiMapsToHin() {
    assertEquals("hin", OcrBootstrap.resolveLanguage(null, "hi"));
  }

  @Test
  void localeZhMapsToChiSim() {
    assertEquals("chi_sim", OcrBootstrap.resolveLanguage(null, "zh"));
  }

  @Test
  void unknownLocaleFallsBackToEng() {
    assertEquals("eng", OcrBootstrap.resolveLanguage(null, "de"));
    assertEquals("eng", OcrBootstrap.resolveLanguage(null, "it"));
    assertEquals("eng", OcrBootstrap.resolveLanguage(null, ""));
  }

  @Test
  void envOverrideTakesPrecedence() {
    assertEquals("fra", OcrBootstrap.resolveLanguage("fra", "en"));
    assertEquals("eng+fra", OcrBootstrap.resolveLanguage("eng+fra", "es"));
  }

  @Test
  void blankEnvOverrideIsIgnored() {
    assertEquals("eng", OcrBootstrap.resolveLanguage("", "en"));
    assertEquals("eng", OcrBootstrap.resolveLanguage("   ", "en"));
  }

  @Test
  void envOverrideIsTrimmed() {
    assertEquals("fra", OcrBootstrap.resolveLanguage("  fra  ", "en"));
  }
}
