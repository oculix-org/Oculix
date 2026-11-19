/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import org.sikuli.support.Commons;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads and registers the OculiX bundled fonts on app startup.
 *
 * <p>Bundled families (resources/fonts/):
 * <ul>
 *   <li><b>Inter</b> (regular/medium/semibold/bold) — UI body, menus, buttons</li>
 *   <li><b>JetBrains Mono</b> (regular/semibold) — code editor, console, kickers</li>
 *   <li><b>Fraunces</b> variable italic — display/hero (Welcome tab title)</li>
 * </ul>
 *
 * <p>The class is fail-soft: if a TTF/OTF is missing or fails to register,
 * {@link #setup()} logs a warning and the IDE falls back to system fonts
 * (Segoe UI / SF Pro / Dialog) automatically — no crash, just less brand.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class OculixFonts {

  /** Family name as the JVM exposes it once the TTF is registered. */
  public static final String FAMILY_INTER     = "Inter";
  public static final String FAMILY_MONO      = "JetBrains Mono";
  public static final String FAMILY_FRAUNCES  = "Fraunces";

  private static boolean loaded = false;

  private OculixFonts() {}

  /**
   * Registers all bundled font files with the local {@link GraphicsEnvironment}.
   *
   * <p>Call once, very early in the application lifecycle (before any Swing
   * component is created and before FlatLaf.setup()), so subsequent
   * {@code Font.decode("Inter 14")} or {@code new Font("JetBrains Mono", ...)}
   * calls resolve to the bundled glyphs instead of falling back to a near-miss.
   */
  public static synchronized void setup() {
    if (loaded) return;
    loaded = true;

    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

    String[] resources = {
        "/fonts/Inter-Regular.otf",
        "/fonts/Inter-Medium.otf",
        "/fonts/Inter-SemiBold.otf",
        "/fonts/Inter-Bold.otf",
        "/fonts/JetBrainsMono-Regular.ttf",
        "/fonts/JetBrainsMono-SemiBold.ttf",
        "/fonts/Fraunces-VariableRoman.ttf",
        "/fonts/Fraunces-VariableItalic.ttf",
    };

    int registered = 0;
    for (String path : resources) {
      try (InputStream in = OculixFonts.class.getResourceAsStream(path)) {
        if (in == null) {
          Commons.startLog(3, "[OculixFonts] resource not found: " + path);
          continue;
        }
        Font font = Font.createFont(Font.TRUETYPE_FONT, in);
        if (ge.registerFont(font)) {
          registered++;
        }
      } catch (IOException | java.awt.FontFormatException e) {
        Commons.startLog(3, "[OculixFonts] failed to register " + path + ": " + e.getMessage());
      }
    }
    Commons.startLog(3, "[OculixFonts] registered " + registered + "/" + resources.length + " bundled font files");
  }

  // ── Convenience accessors (rare — most components should let FlatLaf
  //    resolve fonts via the .properties theme. Use these only for hand-painted
  //    components like the Welcome hero title or status-bar kickers that need
  //    explicit fonts not exposed via UIManager keys.) ──────────────────────

  /** Display / hero title (Welcome tab), Fraunces italic. */
  public static Font display(int size) {
    return brandOrFallback(FAMILY_FRAUNCES, Font.ITALIC | Font.BOLD, size);
  }

  /** UI body (default IDE font). */
  public static Font ui(int size) {
    return brandOrFallback(FAMILY_INTER, Font.PLAIN, size);
  }

  /** UI body, semibold weight (Swing maps to BOLD). */
  public static Font uiBold(int size) {
    return brandOrFallback(FAMILY_INTER, Font.BOLD, size);
  }

  /** Monospace (code editor, console, kickers). */
  public static Font mono(int size) {
    // Mono fallback uses Java's logical "Monospaced" family which auto-
    // composites with system mono fonts that have CJK/Arabic/etc.
    return new Font(monoFamily(), Font.PLAIN, size);
  }

  private static final java.util.Map<Character.UnicodeScript, String> FAMILY_FOR_SCRIPT =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** Families tried first when the base font lacks the glyphs of a text. */
  private static final String[] PREFERRED_FALLBACKS = {
      "Nirmala UI", "Segoe UI", "Noto Sans", "Kohinoor Devanagari", "Devanagari Sangam MN",
      "Arial Unicode MS", "DejaVu Sans"
  };

  /**
   * The font to draw {@code text} with: {@code base} when it has every glyph,
   * otherwise an installed family that has them, in the same style and size.
   * The family found for a script is remembered for the next texts of that script.
   */
  public static Font forText(String text, Font base) {
    if (text == null || text.isEmpty() || base.canDisplayUpTo(text) == -1) {
      return base;
    }
    Character.UnicodeScript script = scriptOf(text);
    String family = FAMILY_FOR_SCRIPT.computeIfAbsent(script, s -> familyDisplaying(text));
    return family.isEmpty() ? base : new Font(family, base.getStyle(), base.getSize());
  }

  private static Character.UnicodeScript scriptOf(String text) {
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      Character.UnicodeScript script = Character.UnicodeScript.of(cp);
      if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED
          && script != Character.UnicodeScript.LATIN) {
        return script;
      }
      i += Character.charCount(cp);
    }
    return Character.UnicodeScript.LATIN;
  }

  private static String familyDisplaying(String text) {
    if (new Font(Font.DIALOG, Font.PLAIN, 12).canDisplayUpTo(text) == -1) {
      return Font.DIALOG;
    }
    java.util.List<String> candidates = new java.util.ArrayList<>(java.util.Arrays.asList(PREFERRED_FALLBACKS));
    candidates.addAll(java.util.Arrays.asList(
        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    for (String family : candidates) {
      if (new Font(family, Font.PLAIN, 12).canDisplayUpTo(text) == -1) {
        return family;
      }
    }
    return "";
  }

  // ── Locale-aware font fallback ─────────────────────────────────────
  //
  // The bundled brand fonts (Inter, JetBrains Mono, Fraunces) are
  // Latin-only. Asking them to render Japanese / Chinese / Arabic /
  // Hebrew / Tamil / Devanagari / Cyrillic produces empty boxes
  // because Java's font system does NOT auto-composite physical fonts
  // with system fallbacks the way logical fonts (Dialog, SansSerif,
  // Monospaced) do.
  //
  // Solution: when the active locale belongs to a non-Latin script,
  // return Java's logical "Dialog" family instead. The JVM auto-resolves
  // it through the OS font registry (Segoe UI on Windows, .AppleSystemUI
  // on macOS, DejaVu/Noto on Linux) which all carry full Unicode
  // coverage. The user gets readable glyphs at the cost of losing the
  // brand typography for that locale — an acceptable trade since
  // unreadable boxes are the worst possible outcome.

  /** Languages whose UI text uses a script not covered by Inter / JBM. */
  private static final java.util.Set<String> NON_LATIN_LANGS = java.util.Set.of(
      "ar", "he",                       // Arabic, Hebrew (RTL)
      "ja", "ko", "zh",                 // East Asian
      "hi", "bn", "te", "ta", "mr", "gu", // Indian subcontinent
      "ru", "uk", "bg", "sr"            // Cyrillic
  );

  /**
   * True when the active user locale needs the Unicode fallback chain.
   *
   * <p>Public so the FlatLaf bootstrap in {@code Sikulix.startup} can
   * decide whether to set Inter / JetBrains Mono as the preferred UI
   * font families or switch to Java's logical {@code Dialog} /
   * {@code Monospaced} families (which auto-composite the locale's
   * script via the OS font registry). Without this branch, FlatLaf
   * paints the whole UI with Inter even on CJK / Arabic / Cyrillic
   * locales, producing tofu boxes everywhere.
   */
  public static boolean currentLocaleNeedsFallback() {
    try {
      java.util.Locale loc = org.sikuli.basics.PreferencesUser.get().getLocale();
      return loc != null && NON_LATIN_LANGS.contains(loc.getLanguage());
    } catch (Throwable t) {
      // PreferencesUser may not be initialised yet during very early
      // splash painting; fail safe to brand fonts.
      return false;
    }
  }

  private static Font brandOrFallback(String brandFamily, int style, int size) {
    if (currentLocaleNeedsFallback()) {
      return new Font(uiFamily(), style, size);
    }
    return new Font(brandFamily, style, size);
  }

  /**
   * The UI family for the current IDE language: Inter for Latin scripts,
   * otherwise the first installed family that draws the language's own name.
   */
  public static String uiFamily() {
    if (!currentLocaleNeedsFallback()) {
      return FAMILY_INTER;
    }
    return forText(localeSample(), new Font(Font.DIALOG, Font.PLAIN, 12)).getFamily();
  }

  /**
   * The monospaced family for the current IDE language: JetBrains Mono for
   * Latin scripts, the logical Monospaced family when it draws the language's
   * own name, else the same family as {@link #uiFamily()}.
   */
  public static String monoFamily() {
    if (!currentLocaleNeedsFallback()) {
      return FAMILY_MONO;
    }
    Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    return mono.canDisplayUpTo(localeSample()) == -1 ? Font.MONOSPACED : uiFamily();
  }

  private static String localeSample() {
    java.util.Locale loc = org.sikuli.basics.PreferencesUser.get().getLocale();
    return loc.getDisplayName(loc);
  }
}
