/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * OculiX Light — a {@link FlatLightLaf} subclass that layers the OculiX brand
 * tokens on top of the base FlatLaf light colors.
 *
 * <p>Same brand accents as {@link OculixDarkLaf} (cyan / lime / violet / amber /
 * red) but with an inverted neutral ink scale so contrast holds on light
 * surfaces. Overrides live in
 * {@code resources/themes/OculixLightLaf.properties}.
 *
 * <p>Counterpart of {@link OculixDarkLaf}; the IDE picks one or the other
 * based on {@code PreferencesUser.get().getIdeTheme()} at startup, and
 * {@link OculixSidebar} swaps between them on the Dark / Light toggle.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class OculixLightLaf extends FlatLightLaf {

  public static final String NAME = "OculiX Light";

  static {
    // OculixDarkLaf already calls registerCustomDefaultsSource("themes") in
    // its static block. Repeat here defensively in case Light is the first
    // LaF the JVM touches in this session — registering twice is a no-op.
    FlatLaf.registerCustomDefaultsSource("themes");
  }

  public static boolean setup() {
    return setup(new OculixLightLaf());
  }

  @Override
  public String getName() {
    return NAME;
  }
}
