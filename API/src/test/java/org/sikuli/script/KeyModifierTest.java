/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the modifier-mask parsing bug where a hotkey modifier
 * value stored/passed in the extended InputEvent.*_DOWN_MASK convention
 * (e.g. SHIFT_DOWN_MASK + META_DOWN_MASK = 320) resolved to an empty
 * modifier string, causing HotkeyController to register a bare, unmodified
 * key (e.g. plain 'C') as a global hotkey instead of "shift meta C".
 */
class KeyModifierTest {

  @Test
  void legacyShiftMetaResolves() {
    int legacy = KeyModifier.SHIFT + KeyModifier.META; // 1 + 4 = 5
    assertEquals("shift meta", KeyModifier.getModifierNames(legacy));
  }

  @Test
  void extendedShiftMetaResolves() {
    // SHIFT_DOWN_MASK(64) + META_DOWN_MASK(256) — the convention this was
    // actually stored as in org.sikuli.script.plist's STOP_HOTKEY_MODIFIERS.
    int extended = 320;
    assertEquals("shift meta", KeyModifier.getModifierNames(extended));
  }

  @Test
  void legacyCtrlResolves() {
    assertEquals("ctrl", KeyModifier.getModifierNames(KeyModifier.CTRL));
  }

  @Test
  void noModifiersResolvesEmpty() {
    assertEquals("", KeyModifier.getModifierNames(0));
  }
}
