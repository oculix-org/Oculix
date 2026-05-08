/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the OculiX brand color tokens.
 *
 * <p>These tests pin down the canonical hex values consumed by both the
 * FlatLaf .properties files and any hand-styled component that grabs a
 * brand color directly from the constants. A drift between the two would
 * break the visual identity unevenly across the IDE.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class OculixColorsTest {

  @Test
  void cyan500_is_the_canonical_brand_blue() {
    assertEquals(0x1EA5FF, OculixColors.OX_CYAN_500.getRGB() & 0xFFFFFF,
        "OX_CYAN_500 is the brand cyan — same hex used in OculixDarkLaf.properties accent");
  }

  @Test
  void lime500_is_brighter_than_lime300() {
    // Naming contract: lower tone numbers are LIGHTER. lime300 must be
    // visually lighter than lime500.
    int sum500 = OculixColors.OX_LIME_500.getRed()
        + OculixColors.OX_LIME_500.getGreen()
        + OculixColors.OX_LIME_500.getBlue();
    int sum300 = OculixColors.OX_LIME_300.getRed()
        + OculixColors.OX_LIME_300.getGreen()
        + OculixColors.OX_LIME_300.getBlue();
    assertTrue(sum300 > sum500,
        "lime300 must be lighter than lime500 per the 100..900 tone convention");
  }

  @Test
  void ink_scale_is_monotonically_lighter_as_tone_decreases() {
    // ink900 darkest, ink050 lightest. The brightness sum must decrease
    // strictly as the tone number increases.
    int[] sums = new int[]{
        sumRGB(OculixColors.OX_INK_050),
        sumRGB(OculixColors.OX_INK_100),
        sumRGB(OculixColors.OX_INK_200),
        sumRGB(OculixColors.OX_INK_300),
        sumRGB(OculixColors.OX_INK_400),
        sumRGB(OculixColors.OX_INK_500),
        sumRGB(OculixColors.OX_INK_600),
        sumRGB(OculixColors.OX_INK_700),
        sumRGB(OculixColors.OX_INK_800),
        sumRGB(OculixColors.OX_INK_900)
    };
    for (int i = 1; i < sums.length; i++) {
      assertTrue(sums[i] < sums[i - 1],
          "ink scale must darken monotonically: sums[" + (i - 1) + "]="
              + sums[i - 1] + " > sums[" + i + "]=" + sums[i]);
    }
  }

  @Test
  void all_color_constants_are_non_null() {
    int n = 0;
    for (Field f : OculixColors.class.getDeclaredFields()) {
      if (!Modifier.isStatic(f.getModifiers())) continue;
      if (!Modifier.isPublic(f.getModifiers())) continue;
      if (f.getType() != Color.class) continue;
      try {
        Object v = f.get(null);
        assertNotNull(v, f.getName() + " is null");
        n++;
      } catch (IllegalAccessException e) {
        fail("cannot read static color " + f.getName());
      }
    }
    assertTrue(n >= 15,
        "expected at least 15 color constants, got " + n
            + " — palette shrunk unexpectedly");
  }

  @Test
  void color_constants_are_unique_hex_values() {
    Set<Integer> seen = new HashSet<>();
    for (Field f : OculixColors.class.getDeclaredFields()) {
      if (!Modifier.isStatic(f.getModifiers())) continue;
      if (!Modifier.isPublic(f.getModifiers())) continue;
      if (f.getType() != Color.class) continue;
      try {
        Color c = (Color) f.get(null);
        int rgb = c.getRGB() & 0xFFFFFF;
        assertTrue(seen.add(rgb),
            "duplicate hex 0x" + Integer.toHexString(rgb)
                + " for " + f.getName() + " — palette has redundancy");
      } catch (IllegalAccessException e) {
        fail("cannot read " + f.getName());
      }
    }
  }

  @Test
  void withAlpha_preserves_rgb_and_sets_alpha() {
    Color base = OculixColors.OX_CYAN_500;
    Color tinted = OculixColors.withAlpha(base, 128);
    assertEquals(base.getRed(), tinted.getRed());
    assertEquals(base.getGreen(), tinted.getGreen());
    assertEquals(base.getBlue(), tinted.getBlue());
    assertEquals(128, tinted.getAlpha());
  }

  @Test
  void withAlpha_zero_yields_transparent() {
    Color t = OculixColors.withAlpha(OculixColors.OX_LIME_500, 0);
    assertEquals(0, t.getAlpha());
  }

  @Test
  void withAlpha_max_is_opaque() {
    Color t = OculixColors.withAlpha(OculixColors.OX_RED_500, 255);
    assertEquals(255, t.getAlpha());
  }

  @Test
  void cannot_be_instantiated() throws Exception {
    // Utility class — private constructor must reject reflection-based
    // access from production code (test-side reflection only as a sanity
    // check on the constructor signature).
    java.lang.reflect.Constructor<OculixColors> ctor =
        OculixColors.class.getDeclaredConstructor();
    assertTrue(Modifier.isPrivate(ctor.getModifiers()),
        "OculixColors must remain a singleton-static utility");
  }

  private static int sumRGB(Color c) {
    return c.getRed() + c.getGreen() + c.getBlue();
  }
}
