/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import javax.swing.Icon;
import javax.swing.UIDefaults;

/**
 * The OculiX window buttons: the gecko tail to minimise, the square to
 * maximise or restore, the red cross to close, and the closed eye to hide
 * the window into the notification area. Each one is a FlatLaf window icon,
 * so the buttons keep their native size, spacing and hover.
 */
public final class TitleBarIcons {

  private TitleBarIcons() {
  }

  /**
   * Puts the OculiX tiles on the FlatLaf title bar buttons. The icons are
   * built on first use, once the theme is installed: their FlatLaf parent
   * reads the button size from the installed defaults.
   */
  static void install(UIDefaults defaults) {
    defaults.put("TitlePane.iconifyIcon", lazy("iconify"));
    defaults.put("TitlePane.maximizeIcon", lazy("maximize"));
    defaults.put("TitlePane.restoreIcon", lazy("maximize"));
    defaults.put("TitlePane.closeIcon", lazy("close"));
  }

  private static UIDefaults.LazyValue lazy(String name) {
    return table -> new OculixWindowIcon(name);
  }

  /** The closed eye, for the button that hides the window into the tray. */
  public static Icon hide() {
    return new OculixWindowIcon("hide");
  }
}
