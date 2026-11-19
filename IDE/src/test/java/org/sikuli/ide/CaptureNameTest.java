/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A capture takes its name from the code line ({@code images = ...} gives "images") or from the
 * naming preference, and never the name of an image already in the bundle: a capture must not
 * overwrite an image the script may already use.
 */
class CaptureNameTest {

  @TempDir
  Path bundle;

  private void image(String name) throws IOException {
    Files.write(bundle.resolve(name + ".png"), new byte[] {1});
  }

  private String captureName(String lineName, String preferred, AtomicInteger asked) {
    return ButtonCapture.captureName(bundle.toFile(), lineName, () -> {
      asked.incrementAndGet();
      return preferred;
    });
  }

  @Test
  void aFreeLineNameIsUsedAndThePreferenceIsNotAsked() {
    AtomicInteger asked = new AtomicInteger();
    assertEquals("plus", captureName("plus", "PLUS", asked));
    assertEquals(0, asked.get(), "the naming preference (OCR, or a prompt) must not run");
  }

  @Test
  void aTakenLineNameFallsBackToThePreference() throws IOException {
    image("images");
    AtomicInteger asked = new AtomicInteger();
    assertEquals("RESET", captureName("images", "RESET", asked));
    assertEquals(1, asked.get());
  }

  @Test
  void threeCapturesOnOneLineGiveThreeFiles() throws IOException {
    // images = [("+1", PLUS), ("RESET", RESET), ("LAMP", LAMP)], each word captured in turn
    AtomicInteger asked = new AtomicInteger();
    String[] read = {"PLUS", "RESET", "LAMP"};
    String[] names = new String[3];
    for (int i = 0; i < 3; i++) {
      names[i] = captureName("images", read[i], asked);
      image(names[i]);
    }
    assertEquals("images", names[0]);
    assertEquals("RESET", names[1]);
    assertEquals("LAMP", names[2]);
    assertEquals(3, bundle.toFile().list().length, "three captures, three files");
  }

  @Test
  void aTakenPreferredNameGetsANumber() throws IOException {
    image("btn");
    image("OK");
    image("OK-1");
    AtomicInteger asked = new AtomicInteger();
    assertEquals("OK-2", captureName("btn", "OK", asked));
  }

  @Test
  void noLineNameUsesThePreference() {
    AtomicInteger asked = new AtomicInteger();
    assertEquals("20261119-030000", captureName("", "20261119-030000", asked));
    assertEquals(1, asked.get());
  }

  @Test
  void freeImageNameCountsUpFromOne() throws IOException {
    File folder = bundle.toFile();
    assertEquals("x", ButtonCapture.freeImageName(folder, "x"));
    image("x");
    assertEquals("x-1", ButtonCapture.freeImageName(folder, "x"));
    image("x-1");
    assertEquals("x-2", ButtonCapture.freeImageName(folder, "x"));
  }
}
