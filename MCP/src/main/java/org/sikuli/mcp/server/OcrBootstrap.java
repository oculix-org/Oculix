/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.sikuli.script.OCR;

import java.util.Locale;
import java.util.Map;

/**
 * OCR locale bootstrap: at server startup, pick a Tesseract language pack
 * based on the OS locale (or an explicit override) and configure
 * {@link OCR#globalOptions()} once for the whole session.
 *
 * <p>Without this, SikuliX defaults to {@code eng} regardless of the
 * machine locale. On a French Windows session that means
 * {@code findText("Marque et modèle")} silently mismatches because the
 * Tesseract pipeline reads {@code è} with the English-only model.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code OCULIX_OCR_LANG} environment variable (explicit override —
 *       use the Tesseract code directly, e.g. {@code fra}, {@code eng+fra})</li>
 *   <li>{@link Locale#getDefault()}{@code .getLanguage()} mapped to one of
 *       the language packs bundled in the fat jar
 *       ({@code fra, eng, spa, hin, chi_sim}).</li>
 *   <li>Fallback: {@code eng}.</li>
 * </ol>
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.5
 */
public final class OcrBootstrap {

  /** Tesseract codes for the .traineddata files shipped in the fat jar. */
  private static final Map<String, String> ISO_TO_TESS = Map.of(
      "fr", "fra",
      "en", "eng",
      "es", "spa",
      "hi", "hin",
      "zh", "chi_sim"
  );

  private OcrBootstrap() {}

  /** Configure the OCR language once. Idempotent and safe to call multiple times. */
  public static void applyDefaultLanguage() {
    String resolved = resolveLanguage();
    OCR.globalOptions().language(resolved);
    System.err.println("[oculix-mcp] OCR language=" + resolved
        + " (locale=" + Locale.getDefault().getLanguage() + ")");
  }

  /** Resolve using the live environment + default locale. */
  static String resolveLanguage() {
    return resolveLanguage(System.getenv("OCULIX_OCR_LANG"),
                           Locale.getDefault().getLanguage());
  }

  /** Pure function for unit tests — no env / locale lookup. */
  static String resolveLanguage(String envOverride, String localeIso) {
    if (envOverride != null && !envOverride.isBlank()) {
      return envOverride.trim();
    }
    return ISO_TO_TESS.getOrDefault(localeIso, "eng");
  }
}
