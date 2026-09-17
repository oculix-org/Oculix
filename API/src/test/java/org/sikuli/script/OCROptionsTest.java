/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.basics.Settings;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OCROptionsTest {

  @TempDir
  Path tmp;

  private String savedLanguage;
  private String savedDataPath;
  private String savedDefaultDataPath;

  @BeforeEach
  void saveGlobals() {
    savedLanguage = Settings.OcrLanguage;
    savedDataPath = Settings.OcrDataPath;
    savedDefaultDataPath = OCR.Options.defaultDataPath;
    OCR.Options.defaultDataPath = null;
  }

  @AfterEach
  void restoreGlobals() {
    Settings.OcrLanguage = savedLanguage;
    Settings.OcrDataPath = savedDataPath;
    OCR.Options.defaultDataPath = savedDefaultDataPath;
    OCR.globalOptions();
  }

  private Path tessdata(String... languages) throws IOException {
    Path dir = tmp.resolve("tessdata");
    Files.createDirectories(dir);
    for (String lang : languages) {
      Files.createFile(dir.resolve(lang + ".traineddata"));
    }
    return dir;
  }

  @Test
  void cloneKeepsLargeImageFactor() {
    OCR.Options options = new OCR.Options().largeImageFactor(0.8f);

    assertEquals(0.8f, options.clone().largeImageFactor());
  }

  @Test
  void resetRestoresLargeImageFactor() {
    OCR.Options options = new OCR.Options().largeImageFactor(0.8f);

    assertEquals(2.0f, options.reset().largeImageFactor());
  }

  @Test
  void psmAcceptsRawLineAndNamesTheWholeRange() {
    OCR.Options options = new OCR.Options();

    assertEquals(13, options.psm(OCR.PSM.RAW_LINE).psm());
    IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> options.psm(14));
    assertTrue(rejected.getMessage().contains("0 .. 13"), rejected.getMessage());
  }

  @Test
  void osdPsmIsAcceptedBeforeTheDataPathIsKnown() {
    OCR.Options options = new OCR.Options();
    assertNull(options.dataPath());

    assertEquals(OCR.PSM.AUTO_OSD.ordinal(), options.psm(OCR.PSM.AUTO_OSD).psm());
  }

  @Test
  void osdPsmNeedsOsdTraineddataOnceTheDataPathIsKnown() throws IOException {
    OCR.Options without = new OCR.Options().dataPath(tessdata("eng").toString());
    assertThrows(IllegalArgumentException.class, () -> without.psm(OCR.PSM.AUTO_OSD));

    Files.createFile(tmp.resolve("tessdata").resolve("osd.traineddata"));
    OCR.Options with = new OCR.Options().dataPath(tmp.toString()).psm(OCR.PSM.AUTO_OSD);
    assertDoesNotThrow(with::validate);
  }

  @Test
  void validateChecksEveryLanguageOfACombination() throws IOException {
    OCR.Options options = new OCR.Options().dataPath(tessdata("eng", "fra").toString());

    assertDoesNotThrow(() -> options.language("fra+eng").validate());
    SikuliXception missing = assertThrows(SikuliXception.class, () -> options.language("fra+deu").validate());
    assertTrue(missing.getMessage().contains("deu.traineddata"), missing.getMessage());
  }

  @Test
  void settingsLanguageIsAppliedInBothDirectionsAndToleratesNull() {
    Settings.OcrLanguage = "fra";
    assertEquals("fra", OCR.globalOptions().language());

    Settings.OcrLanguage = "eng";
    assertEquals("eng", OCR.globalOptions().language());

    Settings.OcrLanguage = null;
    assertEquals("eng", assertDoesNotThrow(() -> OCR.globalOptions()).language());
  }

  @Test
  void settingsDataPathAcceptsTheTessdataFolderOrItsParentAndFollowsChanges() throws IOException {
    Path tessdata = tessdata("eng");

    Settings.OcrDataPath = tessdata.toString();
    TextRecognizer.initDefaultDataPath();
    assertEquals(tessdata.toFile().getAbsolutePath(), OCR.Options.defaultDataPath);

    Settings.OcrDataPath = tmp.toString();
    TextRecognizer.initDefaultDataPath();
    assertEquals(tessdata.toFile().getAbsolutePath(), OCR.Options.defaultDataPath);

    Path other = Files.createDirectories(tmp.resolve("other"));
    Settings.OcrDataPath = other.toString();
    TextRecognizer.initDefaultDataPath();
    assertEquals(new File(other.toFile(), "tessdata").getAbsolutePath(), OCR.Options.defaultDataPath);
  }

  @Test
  void textMatchesKeepOneNormalizedScore() {
    Rectangle box = new Rectangle(10, 20, 30, 40);

    Match fromTesseract = new Match(box, 95.0, "word");
    Match relocated = new Match(box, fromTesseract.getScore(), fromTesseract.getText(), null);

    assertEquals(0.95, fromTesseract.getScore(), 1e-9);
    assertEquals(0.95, relocated.getScore(), 1e-9);
  }

  @Test
  void toStringDoesNotNeedAScreen() {
    assertDoesNotThrow(() -> new OCR.Options().toString());
  }
}
