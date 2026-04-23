/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.sikuli.support.Commons;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression test for the UnsatisfiedLinkError reported on Apple Silicon (Mac M1)
 * when a {@code new Pattern(...)} was created before any {@code new Finder(...)}.
 *
 * <p>Root cause before the fix: {@code Pattern.patternMask = Commons.getNewMat();}
 * is an instance-field initializer that calls {@code new Mat()} directly. OpenCV
 * was only loaded lazily as a side-effect of the Finder -> FindInput2 -> Finder2
 * static-init chain, so any code path that instantiated a Pattern first crashed.
 *
 * <p>Fix: {@code Commons} static initializer now calls {@code loadOpenCV()},
 * guaranteeing the native lib is ready for any caller.
 *
 * <p>This test exercises the two entry points that used to crash on a fresh JVM:
 * {@code Commons.getNewMat()} and Pattern instance-field init. It must be kept
 * in its own test class so Surefire runs it in a clean classloader state
 * (no previous Finder reference).
 */
class OpenCVLoadOnFreshJvmTest {

  @Test
  void commonsGetNewMatWorksWithoutAnyFinderReference() {
    // This line is what used to throw UnsatisfiedLinkError on Mac M1 before the fix.
    // Commons's static initializer is triggered here (first reference to the class),
    // and it must have loaded OpenCV before new Mat() is ever called.
    // If the fix regresses, JUnit reports the UnsatisfiedLinkError as a test error.
    Mat m = Commons.getNewMat();
    assertNotNull(m);
    assertNotNull(m.size());
  }

  @Test
  void patternInstantiationDoesNotTriggerUnsatLinkError() {
    // Pattern.patternMask instance field runs `Commons.getNewMat()` — the exact
    // field that reproduced Raimund's crash. We don't care whether the image
    // resolves (it won't — file does not exist); we only care that the OpenCV
    // native path is reached without UnsatisfiedLinkError.
    try {
      new Pattern("__nonexistent_regression_test__.png");
    } catch (UnsatisfiedLinkError e) {
      fail("new Pattern(...) raised UnsatisfiedLinkError: " + e.getMessage()
          + " -- Commons.loadOpenCV() was not called before Pattern.patternMask field init");
    } catch (Throwable ignored) {
      // Any other failure (SikuliXception, missing file, etc.) is acceptable here.
    }
  }
}
