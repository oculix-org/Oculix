/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import java.awt.Color;
import javax.swing.UIManager;

/** Semantic colours shared by custom painters. Values live in the two themes. */
public final class OculixUi {
  private OculixUi() { }

  public static Color color(String key, String fallbackKey) {
    Color value = UIManager.getColor("Oculix." + key);
    if (value == null) value = UIManager.getColor(fallbackKey);
    return value != null ? value : Color.GRAY;
  }

  public static String hex(String key, String fallbackKey) {
    return String.format("#%06X", color(key, fallbackKey).getRGB() & 0xffffff);
  }
}
