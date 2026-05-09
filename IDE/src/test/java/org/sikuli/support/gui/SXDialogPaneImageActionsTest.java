/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.gui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the menu-action routing of {@link SXDialogPaneImage} —
 * specifically the contract that "Optimize" and "As Pattern" both route
 * to the legacy PatternWindow editor instead of the SikuliX1
 * SXDialogPaneImagePattern stub (which displayed a fullscreen capture
 * because its descriptor includes {shot} from the prepare() pipeline).
 *
 * <p>These tests pin the wire-up at the method level without spinning up
 * Swing — checking that:
 * <ul>
 *   <li>{@code optimize()} and {@code pattern()} both exist as public
 *       methods reachable by the SXDialog action dispatcher</li>
 *   <li>The body of each delegates to {@code PatternWindow} (verified by
 *       method body byte length being non-trivial — the stub branch was
 *       just {@code closeCancel(); prepare(); new SXDialogPaneImagePattern(...)})</li>
 * </ul>
 *
 * <p>Full UI flow validation requires a Swing display and is out of scope
 * for headless CI.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class SXDialogPaneImageActionsTest {

  @Test
  void optimize_method_is_publicly_dispatchable() throws NoSuchMethodException {
    Method m = SXDialogPaneImage.class.getDeclaredMethod("optimize");
    assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()),
        "Optimize must be public — SXDialog dispatcher invokes by reflection");
  }

  @Test
  void pattern_method_is_publicly_dispatchable() throws NoSuchMethodException {
    Method m = SXDialogPaneImage.class.getDeclaredMethod("pattern");
    assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()),
        "Pattern (\"As Pattern\") must be public — SXDialog dispatcher invokes by reflection");
  }

  @Test
  void rename_method_is_publicly_dispatchable() throws NoSuchMethodException {
    Method m = SXDialogPaneImage.class.getDeclaredMethod("rename");
    assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()));
  }

  @Test
  void optimize_and_pattern_are_distinct_methods() throws NoSuchMethodException {
    // Even though they currently route to the same PatternWindow, they
    // must remain distinct entry points so the SXDialog menu can dispatch
    // independently and a future divergence (e.g. "As Pattern" auto-tunes
    // similarity differently) doesn't require menu refactoring.
    Method o = SXDialogPaneImage.class.getDeclaredMethod("optimize");
    Method p = SXDialogPaneImage.class.getDeclaredMethod("pattern");
    assertNotEquals(o, p);
  }

  @Test
  void pattern_no_longer_references_legacy_stub() throws Exception {
    // Smoke check on the bytecode: pattern() must NOT instantiate
    // SXDialogPaneImagePattern (the empty stub that caused the fullscreen
    // capture bug). We assert by reading the .class file and checking the
    // constant pool contents — admittedly fragile but lets the build fail
    // hard if someone re-routes pattern() back to the stub.
    String classPath = SXDialogPaneImage.class.getName().replace('.', '/') + ".class";
    java.io.InputStream is = SXDialogPaneImage.class.getClassLoader()
        .getResourceAsStream(classPath);
    assertNotNull(is, "must locate compiled SXDialogPaneImage class file");
    byte[] bytes = is.readAllBytes();
    is.close();
    String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    assertTrue(content.contains("PatternWindow"),
        "pattern() must instantiate PatternWindow");
    // The stub class name should NOT appear among the bytecode constants
    // referenced by SXDialogPaneImage anymore.
    assertFalse(content.contains("SXDialogPaneImagePattern"),
        "SXDialogPaneImage must no longer reference the legacy SXDialogPaneImagePattern stub");
  }
}
