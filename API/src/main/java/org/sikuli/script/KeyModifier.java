/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import java.awt.event.InputEvent;

/**
 * complementing class Key with the constants for the modifier keys<br>
 * only still there for backward compatibility (is already duplicated in Key)
 */
public class KeyModifier {
  public static final int CTRL = InputEvent.CTRL_MASK; //ctrl
  public static final int SHIFT = InputEvent.SHIFT_MASK; //shift
  public static final int ALT = InputEvent.ALT_MASK; //alt
  public static final int ALTGR = InputEvent.ALT_GRAPH_MASK; //altGraph
  public static final int META = InputEvent.META_MASK; //meta
  public static final int CMD = InputEvent.META_MASK;
  public static final int WIN = InputEvent.META_MASK;

  @Deprecated
  public static final int KEY_CTRL = InputEvent.CTRL_MASK;
  @Deprecated
  public static final int KEY_SHIFT = InputEvent.SHIFT_MASK;
  @Deprecated
  public static final int KEY_ALT = InputEvent.ALT_MASK;
  @Deprecated
  public static final int KEY_META = InputEvent.META_MASK;
  @Deprecated
  public static final int KEY_CMD = InputEvent.META_MASK;
  @Deprecated
  public static final int KEY_WIN = InputEvent.META_MASK;

  public static String getModifierNames(int modifier) {
    // Preferences (and some callers) have historically stored modifiers using
    // the extended InputEvent.*_DOWN_MASK convention (e.g. SHIFT_DOWN_MASK=64,
    // META_DOWN_MASK=256) instead of the legacy *_MASK convention documented
    // for this class (SHIFT=1, META=4, ...). Checking both bit patterns keeps
    // a stale/mismatched preference value from silently resolving to "no
    // modifiers", which previously turned e.g. "Shift+Cmd+C" into a bare,
    // globally-captured 'C' hotkey.
    String names = "";
    if (0 != (modifier & CTRL) || 0 != (modifier & InputEvent.CTRL_DOWN_MASK)) {
      names += "ctrl ";
    }
    if (0 != (modifier & SHIFT) || 0 != (modifier & InputEvent.SHIFT_DOWN_MASK)) {
      names += "shift ";
    }
    if (0 != (modifier & ALT) || 0 != (modifier & InputEvent.ALT_DOWN_MASK)) {
      names += "alt ";
    }
    if (0 != (modifier & ALTGR) || 0 != (modifier & InputEvent.ALT_GRAPH_DOWN_MASK)) {
      names += "altGraph ";
    }
    if (0 != (modifier & META) || 0 != (modifier & InputEvent.META_DOWN_MASK)) {
      names += "meta ";
    }
    return names.trim();
  }
}
