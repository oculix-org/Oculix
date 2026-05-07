/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;
import org.sikuli.support.devices.MouseDevice;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the mouse-blocked preflight path introduced by @nishantsir57
 * in PR #234. Validates that {@code Screen.initScreens()} runs the mouse
 * preflight without hanging on Linux / Windows / macOS.
 *
 * Runs in CI matrix via .github/workflows/test-mouse-preflight.yml.
 */
class MousePreflightSmokeTest {

  @Test
  void screen_init_runs_mouse_preflight_without_hanging() {
    long t0 = System.currentTimeMillis();
    new Screen();
    long elapsed = System.currentTimeMillis() - t0;

    System.out.println("[preflight] Screen() init = " + elapsed + " ms");
    System.out.println("[preflight] isUsable()    = " + MouseDevice.isUsable());

    // 30s budget covers a slow macOS runner with cold AWT init.
    // Anything beyond this is the silent hang we are guarding against.
    assertTrue(elapsed < 30_000,
      "Screen() init exceeded 30s — MouseDevice.start() likely hung");
  }
}
