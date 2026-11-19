/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the modifier-mask parsing bug (#449): a hotkey modifier
 * value stored/passed in the extended InputEvent.*_DOWN_MASK convention
 * (e.g. SHIFT_DOWN_MASK + META_DOWN_MASK = 320) resolved to an empty
 * modifier string, causing HotkeyController to register a bare, unmodified
 * key (e.g. plain 'C') as a global hotkey instead of "shift meta C".
 *
 * Covers every modifier getModifierNames() recognizes, in both the legacy
 * *_MASK and extended *_DOWN_MASK conventions, plus a value mixing both
 * conventions in the same int — the shape any stale/migrated preference
 * value could plausibly take.
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
    // actually stored as in org.sikuli.script.plist's STOP_HOTKEY_MODIFIERS,
    // inherited from a SikuliX 2.0.x install sharing the same Preferences node.
    int extended = InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK; // 320
    assertEquals("shift meta", KeyModifier.getModifierNames(extended));
  }

  @Test
  void extendedShiftAltResolves() {
    // A Windows-side equivalent of the same migration hazard: a 2.0.x
    // Stop hotkey of Shift+Alt stores SHIFT_DOWN_MASK + ALT_DOWN_MASK = 576.
    int extended = InputEvent.SHIFT_DOWN_MASK + InputEvent.ALT_DOWN_MASK; // 576
    assertEquals("shift alt", KeyModifier.getModifierNames(extended));
  }

  @Test
  void extendedCtrlResolves() {
    assertEquals("ctrl", KeyModifier.getModifierNames(InputEvent.CTRL_DOWN_MASK));
  }

  @Test
  void extendedAltGraphResolves() {
    assertEquals("altGraph", KeyModifier.getModifierNames(InputEvent.ALT_GRAPH_DOWN_MASK));
  }

  @Test
  void legacyCtrlResolves() {
    assertEquals("ctrl", KeyModifier.getModifierNames(KeyModifier.CTRL));
  }

  @Test
  void mixedLegacyAndExtendedBitsBothResolve() {
    // A value combining a legacy bit for one modifier with an extended bit
    // for another — not observed in the wild, but the parser checks each
    // modifier's legacy and extended bit independently, so this must still
    // resolve both rather than only the one that happens to match.
    int mixed = KeyModifier.SHIFT + InputEvent.META_DOWN_MASK; // 1 + 256 = 257
    assertEquals("shift meta", KeyModifier.getModifierNames(mixed));
  }

  @Test
  void noModifiersResolvesEmpty() {
    assertEquals("", KeyModifier.getModifierNames(0));
  }

  @Test
  void unrecognizedBitsResolveEmpty() {
    // A bit pattern that is neither a legacy nor an extended modifier mask
    // (e.g. InputEvent.BUTTON1_DOWN_MASK = 1024) must still resolve to no
    // modifiers — getModifierNames() only recognizes keyboard modifiers.
    assertEquals("", KeyModifier.getModifierNames(InputEvent.BUTTON1_DOWN_MASK));
  }
}
