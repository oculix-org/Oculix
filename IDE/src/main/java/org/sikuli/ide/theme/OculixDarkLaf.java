/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

/**
 * OculiX Dark — a {@link FlatDarkLaf} subclass that layers the OculiX brand
 * tokens (cyan accents, ink-blue surfaces, lime Run verb) on top of the base
 * FlatLaf dark colors.
 *
 * <p>Concretely, all the {@code Component.borderColor},
 * {@code Button.background}, {@code TabbedPane.underlineColor}, etc. keys
 * are overridden via the companion file
 * {@code resources/themes/OculixDark.properties}.
 *
 * <p>Usage in IDE startup:
 * <pre>
 *   OculixFonts.setup();      // register bundled TTFs first
 *   OculixDarkLaf.setup();    // then install the LaF
 * </pre>
 *
 * <p>Equivalent of {@link FlatDarkLaf#setup()} but with the OculiX skin layered
 * on. If a hostile classpath / resource issue prevents loading the .properties,
 * we fall back to a vanilla {@code FlatDarkLaf} so the IDE still launches.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class OculixDarkLaf extends FlatDarkLaf {

  public static final String NAME = "OculiX Dark";

  /** Path passed to {@link FlatLaf#registerCustomDefaultsSource(String)}. */
  private static final String CUSTOM_DEFAULTS_PACKAGE = "themes";

  static {
    // Tell FlatLaf to also scan resources/themes/<LafName>.properties for
    // additional UI defaults on top of FlatDarkLaf's. Files there are
    // resolved by simple LaF-name match (OculixDarkLaf → OculixDarkLaf.properties).
    FlatLaf.registerCustomDefaultsSource(CUSTOM_DEFAULTS_PACKAGE);
  }

  public static boolean setup() {
    return setup(new OculixDarkLaf());
  }

  @Override
  public String getName() {
    return NAME;
  }
}
