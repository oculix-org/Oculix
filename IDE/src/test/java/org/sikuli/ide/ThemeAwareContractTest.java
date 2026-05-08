/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link ThemeAware} interface is a load-bearing contract for the
 * Dark/Light toggle: every open editor pane, the console, the explorer,
 * and any other lifecycle-sensitive component implements it. These tests
 * pin down the shape so a future refactor can't quietly change the
 * before/after pair without flagging every implementer.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class ThemeAwareContractTest {

  @Test
  void interface_declares_beforeThemeChange_and_afterThemeChange() throws Exception {
    Method before = ThemeAware.class.getMethod("beforeThemeChange");
    Method after = ThemeAware.class.getMethod("afterThemeChange");
    assertEquals(void.class, before.getReturnType());
    assertEquals(void.class, after.getReturnType());
    assertEquals(0, before.getParameterCount());
    assertEquals(0, after.getParameterCount());
  }

  @Test
  void interface_has_exactly_two_abstract_methods() {
    int abstractCount = 0;
    for (Method m : ThemeAware.class.getDeclaredMethods()) {
      if (Modifier.isAbstract(m.getModifiers())) abstractCount++;
    }
    assertEquals(2, abstractCount,
        "ThemeAware must keep its surface tight — exactly before + after");
  }

  @Test
  void interface_is_public_and_not_default_package() {
    assertTrue(Modifier.isPublic(ThemeAware.class.getModifiers()));
    assertEquals("org.sikuli.ide", ThemeAware.class.getPackage().getName(),
        "ThemeAware lives in the public IDE package so external Swing extensions can implement it");
  }

  @Test
  void key_implementers_implement_the_interface() throws Exception {
    // Sanity: classes we expect to participate in the lifecycle must
    // declare the interface. If a refactor removes the implements clause,
    // we want to know.
    assertTrue(ThemeAware.class.isAssignableFrom(EditorPane.class),
        "EditorPane must implement ThemeAware (image-button rebuild on toggle)");
    assertTrue(ThemeAware.class.isAssignableFrom(EditorConsolePane.class),
        "EditorConsolePane must implement ThemeAware (re-htmlize scrollback on toggle)");
    assertTrue(ThemeAware.class.isAssignableFrom(
        Class.forName("org.sikuli.ide.ui.ScriptExplorer")),
        "ScriptExplorer must implement ThemeAware (workspace pane bg follows theme)");
  }
}
