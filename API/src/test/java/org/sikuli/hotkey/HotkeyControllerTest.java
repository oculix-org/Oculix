/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.hotkey;

import org.junit.jupiter.api.Test;
import org.sikuli.basics.HotkeyEvent;
import org.sikuli.basics.HotkeyListener;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the safety net added in #449/#450: if a modifier value
 * cannot be resolved to any known modifier name, HotkeyController must refuse
 * to register the hotkey rather than silently falling back to registering the
 * bare, unmodified key globally.
 *
 * Uses a fresh {@link HotkeyController} instance (not the {@code get()}
 * singleton) so the refusal path is exercised without touching the native
 * keymaster provider — the constructor leaves {@code hotkeyProvider} null,
 * and a refused call never reaches the code that would dereference it.
 */
class HotkeyControllerTest {

  private static final HotkeyListener NOOP = new HotkeyListener() {
    @Override
    public void hotkeyPressed(HotkeyEvent e) {
    }
  };

  @Test
  void unrecognizedModifierIsRefused() {
    HotkeyController controller = new HotkeyController();
    // BUTTON1_DOWN_MASK is a real InputEvent bit but not one getModifierNames()
    // treats as a keyboard modifier, so it must resolve to "" and be refused —
    // this is exactly the shape of bug that produced a bare global 'C' hotkey.
    String result = controller.addHotkey(NOOP, KeyEvent.VK_C, InputEvent.BUTTON1_DOWN_MASK);
    assertEquals("", result, "an unresolvable non-zero modifier must not register a hotkey");
  }
}
