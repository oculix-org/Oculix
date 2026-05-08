/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import java.awt.Color;

/**
 * OculiX brand color tokens.
 *
 * <p>These are the canonical hex values consumed by:
 * <ul>
 *   <li>{@code resources/themes/OculixDark.properties} (FlatLaf custom theme)</li>
 *   <li>Hand-styled custom Swing components that need a brand color in code
 *       (e.g. capture overlay reticle, run-button glow, status dots)</li>
 * </ul>
 *
 * <p>Naming convention: {@code OX_<HUE>_<TONE>} where the tone follows a
 * Tailwind-like 100..900 scale (lower = lighter, higher = darker).
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class OculixColors {

  private OculixColors() {}

  // ── Brand accents ───────────────────────────────────────────────

  /** Primary cyan. CTAs, focused inputs, selection, links, brand highlight. */
  public static final Color OX_CYAN_500   = new Color(0x1EA5FF);
  /** Cyan hover/lighter. */
  public static final Color OX_CYAN_300   = new Color(0x7CCBFF);

  /** Secondary brand violet. Decorative dividers, code keywords. Use sparingly. */
  public static final Color OX_VIOLET_500 = new Color(0x8B5CF6);
  public static final Color OX_VIOLET_300 = new Color(0xB79DFA);

  /** THE Run verb. Run button, success status, OK indicators only — never decoration. */
  public static final Color OX_LIME_500   = new Color(0x4FC72A);
  /** Success text, active dots. */
  public static final Color OX_LIME_400   = new Color(0x7DE356);
  /** Code strings literal color. */
  public static final Color OX_LIME_300   = new Color(0xA8EE8A);

  /** Errors, capture reticle outline, danger actions. */
  public static final Color OX_RED_500    = new Color(0xE53935);
  public static final Color OX_RED_400    = new Color(0xEF5350);

  /** "Running…" transitional state, warnings, code numerics. */
  public static final Color OX_AMBER_500  = new Color(0xFFC857);

  // ── Ink (neutrals — dark mode is the default) ───────────────────

  /** Deepest background. Main editor area, console. */
  public static final Color OX_INK_900    = new Color(0x05081A);
  /** Default surface. Sidebar, tabs background. */
  public static final Color OX_INK_800    = new Color(0x0A1028);
  /** Raised surfaces. Toolbar, action bar. */
  public static final Color OX_INK_700    = new Color(0x121A3A);
  /** Panels, selected items, popups. */
  public static final Color OX_INK_600    = new Color(0x1C2753);
  /** Hard borders. */
  public static final Color OX_INK_500    = new Color(0x2B3A72);
  /** Soft text on dark, dim icons. */
  public static final Color OX_INK_400    = new Color(0x4456A0);
  /** Subtle text (timestamps, hints). */
  public static final Color OX_INK_300    = new Color(0x7D8BC9);
  /** Muted body text. */
  public static final Color OX_INK_200    = new Color(0xB9C2E8);
  /** Primary body text on dark. */
  public static final Color OX_INK_100    = new Color(0xE6EAFB);
  /** Light surface (rare — explicit light mode). */
  public static final Color OX_INK_050    = new Color(0xF3F5FF);

  // ── Helpers ─────────────────────────────────────────────────────

  /** Returns the given color with an explicit alpha channel (0..255). */
  public static Color withAlpha(Color c, int alpha) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
  }
}
