/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.junit.jupiter.api.Test;
import org.sikuli.script.Location;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for steps 2 (glyph indicators) and 3 (hover code variant) of
 * RaiMan's roadmap on issue #209.
 *
 * <p>The glyph drawing itself runs against a real Graphics2D and is hard
 * to assert without a display, so these tests pin down the surrounding
 * contract that drives it:
 * <ul>
 *   <li>{@link EditorPatternButton#toString()} produces the canonical
 *       {@code Pattern("name").similar(X).targetOffset(X,Y)} chain that
 *       both the document serialiser and the hover tooltip consume</li>
 *   <li>{@link EditorPatternButton#getToolTipText()} returns the same
 *       chain — Step 3 contract</li>
 *   <li>The drawDecoration helper called from paint() (Step 2) is reached
 *       via the public paint() override — verified by the @Override
 *       declaration through reflection</li>
 * </ul>
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class EditorPatternButtonGlyphAndTooltipTest {

  // ----------------------------------------------------- Step 3 — tooltip

  @Test
  void getToolTipText_returnsBareQuotedFilename_whenNoModifiers() {
    EditorPatternButton btn = new EditorPatternButton();
    setPrivate(btn, "_imgFilename", "/some/path/foo.png");
    setPrivate(btn, "_similarity", 0.7);   // DEFAULT_SIMILARITY
    setPrivate(btn, "_offset", new Location(0, 0));
    setPrivate(btn, "_exact", false);
    setPrivate(btn, "_resizeFactor", 0f);

    assertEquals("\"foo.png\"", btn.getToolTipText(),
        "no modifiers → bare quoted filename, same shortcut as JythonCodeGenerator");
  }

  @Test
  void getToolTipText_includesSimilar_whenNonDefault() {
    EditorPatternButton btn = new EditorPatternButton();
    setPrivate(btn, "_imgFilename", "/p/foo.png");
    setPrivate(btn, "_similarity", 0.85);
    setPrivate(btn, "_offset", new Location(0, 0));
    setPrivate(btn, "_exact", false);

    String tip = btn.getToolTipText();
    assertTrue(tip.startsWith("Pattern(\"foo.png\")"));
    assertTrue(tip.contains(".similar(0.85)"));
  }

  @Test
  void getToolTipText_includesTargetOffset_whenNonZero() {
    EditorPatternButton btn = new EditorPatternButton();
    setPrivate(btn, "_imgFilename", "/p/foo.png");
    setPrivate(btn, "_similarity", 0.7);
    setPrivate(btn, "_offset", new Location(10, -20));
    setPrivate(btn, "_exact", false);

    String tip = btn.getToolTipText();
    assertTrue(tip.contains(".targetOffset(10,-20)"),
        "targetOffset should appear with raw int coords");
  }

  @Test
  void getToolTipText_chains_similar_then_targetOffset_inThatOrder() {
    EditorPatternButton btn = new EditorPatternButton();
    setPrivate(btn, "_imgFilename", "/p/foo.png");
    setPrivate(btn, "_similarity", 0.92);
    setPrivate(btn, "_offset", new Location(5, 5));
    setPrivate(btn, "_exact", false);

    String tip = btn.getToolTipText();
    int simIdx = tip.indexOf(".similar");
    int offIdx = tip.indexOf(".targetOffset");
    assertTrue(simIdx > 0 && offIdx > simIdx,
        ".similar must come before .targetOffset for syntactic stability "
            + "(JythonCodeGenerator.pattern emits in this order)");
  }

  @Test
  void getToolTipText_emitsExact_overSimilar_whenExactIsTrue() {
    EditorPatternButton btn = new EditorPatternButton();
    setPrivate(btn, "_imgFilename", "/p/foo.png");
    setPrivate(btn, "_exact", true);
    setPrivate(btn, "_similarity", 0.85);   // ignored when exact
    setPrivate(btn, "_offset", new Location(0, 0));

    String tip = btn.getToolTipText();
    assertTrue(tip.contains(".exact()"),
        "exact wins over similar in the canonical chain");
    assertFalse(tip.contains(".similar("),
        "exact must NOT also emit .similar()");
  }

  @Test
  void getToolTipText_recomputesLive_onSubsequentCall() {
    // Step 3 contract: the tooltip is recomputed on every hover, not
    // cached at button creation. Otherwise the SXDialog Optimize flow
    // would never surface its updates in the tooltip.
    EditorPatternButton btn = new EditorPatternButton();
    setPrivate(btn, "_imgFilename", "/p/foo.png");
    setPrivate(btn, "_similarity", 0.7);
    setPrivate(btn, "_offset", new Location(0, 0));
    setPrivate(btn, "_exact", false);

    String first = btn.getToolTipText();
    assertEquals("\"foo.png\"", first);

    // Mutate state — simulate the user adjusting the slider in PatternWindow
    setPrivate(btn, "_similarity", 0.95);

    String second = btn.getToolTipText();
    assertTrue(second.contains(".similar(0.95)"),
        "tooltip recomputes from live fields on every call");
  }

  // ---------------------------------------------------- Step 2 — paint hook

  @Test
  void paint_override_is_present_so_drawDecoration_runs() throws NoSuchMethodException {
    // Step 2 wire-up: paint(Graphics) must be overridden in
    // EditorPatternButton so drawDecoration() (which paints the green
    // similar badge + red targetOffset cross) is actually reached.
    // Before the rc5 cycle, this paint() was commented out and the
    // glyph helpers never ran.
    java.lang.reflect.Method paint =
        EditorPatternButton.class.getDeclaredMethod("paint", java.awt.Graphics.class);
    assertEquals(EditorPatternButton.class, paint.getDeclaringClass(),
        "paint(Graphics) must be declared on EditorPatternButton itself, "
            + "not inherited from the parent");
  }

  @Test
  void getToolTipText_override_is_declared_on_subclass() throws NoSuchMethodException {
    // Step 3 wire-up: getToolTipText must be declared on
    // EditorPatternButton (not just inherited) so it returns the chain
    // form, not the bare filename of the parent.
    java.lang.reflect.Method m =
        EditorPatternButton.class.getDeclaredMethod("getToolTipText");
    assertEquals(EditorPatternButton.class, m.getDeclaringClass());
  }

  // -------------------------------------------------------- helpers

  private static void setPrivate(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new AssertionError(
          "setPrivate failed for " + fieldName + ": " + e.getMessage(), e);
    }
  }
}
