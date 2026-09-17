/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.basics.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class OCRSystemLanguageTest {

  @TempDir
  Path tmp;

  private String savedLanguage;
  private String savedDataPath;
  private String savedUrl;
  private String savedDefaultDataPath;

  @BeforeEach
  void saveGlobals() {
    savedLanguage = Settings.OcrLanguage;
    savedDataPath = Settings.OcrDataPath;
    savedUrl = Settings.OcrTessdataUrl;
    savedDefaultDataPath = OCR.Options.defaultDataPath;
    OCR.Options.defaultDataPath = null;
  }

  @AfterEach
  void restoreGlobals() {
    Settings.OcrLanguage = savedLanguage;
    Settings.OcrDataPath = savedDataPath;
    Settings.OcrTessdataUrl = savedUrl;
    OCR.Options.defaultDataPath = savedDefaultDataPath;
    OCR.globalOptions();
  }

  @Test
  void theDefaultLanguageIsTheSystemOne() {
    assertEquals(OCR.AUTO, Settings.OcrLanguageDefault);
    Settings.OcrLanguage = Settings.OcrLanguageDefault;
    assertEquals(OCR.AUTO, new OCR.Options().language());
  }

  @Test
  void localesMapToTesseractCodes() {
    assertEquals("fra", OCR.systemLanguage(Locale.forLanguageTag("fr-FR")));
    assertEquals("eng", OCR.systemLanguage(Locale.forLanguageTag("en-US")));
    assertEquals("ben", OCR.systemLanguage(Locale.forLanguageTag("bn-BD")));
    assertEquals("ara", OCR.systemLanguage(Locale.forLanguageTag("ar-DZ")));
    assertEquals("deu", OCR.systemLanguage(Locale.forLanguageTag("de-CH")));
    assertEquals("chi_sim", OCR.systemLanguage(Locale.forLanguageTag("zh-CN")));
    assertEquals("chi_sim", OCR.systemLanguage(Locale.forLanguageTag("zh-Hans-SG")));
    assertEquals("chi_tra", OCR.systemLanguage(Locale.forLanguageTag("zh-TW")));
    assertEquals("chi_tra", OCR.systemLanguage(Locale.forLanguageTag("zh-Hant-HK")));
    assertEquals("nor", OCR.systemLanguage(Locale.forLanguageTag("nb-NO")));
    assertEquals("tgl", OCR.systemLanguage(Locale.forLanguageTag("fil-PH")));
    assertEquals("srp_latn", OCR.systemLanguage(Locale.forLanguageTag("sr-Latn-RS")));
    assertEquals("srp", OCR.systemLanguage(Locale.forLanguageTag("sr-Cyrl-RS")));
    assertEquals("uzb_cyrl", OCR.systemLanguage(Locale.forLanguageTag("uz-Cyrl-UZ")));
    assertEquals(Settings.OcrLanguageFallback, OCR.systemLanguage(Locale.forLanguageTag("xx")));
  }

  @Test
  void aMissingModelIsFetchedFromTheConfiguredUrlOnce() throws IOException {
    Path mirror = Files.createDirectories(tmp.resolve("mirror"));
    byte[] model = "not really a model".getBytes();
    Files.write(mirror.resolve("ben.traineddata"), model);
    Settings.OcrTessdataUrl = mirror.toUri().toString();
    Settings.OcrDataPath = tmp.resolve("data").toString();

    assertEquals("ben", OCR.ensureLanguage("ben"));
    File fetched = new File(OCR.globalOptions().dataPath(), "ben.traineddata");
    assertArrayEquals(model, Files.readAllBytes(fetched.toPath()));

    Files.delete(mirror.resolve("ben.traineddata"));
    assertEquals("ben", OCR.ensureLanguage("ben"), "already there, the mirror is not needed any more");
  }

  @Test
  void anUnfetchableLanguageFallsBackToEnglish() {
    Settings.OcrTessdataUrl = tmp.resolve("nowhere").toUri().toString();
    Settings.OcrDataPath = tmp.resolve("data").toString();

    assertEquals(Settings.OcrLanguageFallback, OCR.ensureLanguage("xyz"));
    assertEquals(Settings.OcrLanguageFallback, OCR.ensureLanguage("ben+xyz"));
    assertFalse(new File(OCR.globalOptions().dataPath(), "xyz.traineddata").exists());
  }
}
