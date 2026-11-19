/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.recorder.generators;

import org.junit.jupiter.api.Test;
import org.sikuli.script.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every line the Robot Framework generator writes must be a keyword call that
 * robotframework-SikuliLibrary accepts as is: the keyword exists, and it gets
 * exactly the arguments its signature declares (ScreenKeywords.java,
 * rainmanwy/robotframework-SikuliLibrary). A line that reads fine but names a
 * missing keyword, or passes one argument too few, only fails when the
 * recorded suite is run, which is too late.
 *
 * <p>The recorder names keys, modifiers and buttons as Jython expressions
 * ({@code Key.ENTER}, {@code Key.CTRL}, {@code Button.LEFT}); SikuliLibrary
 * wants the bare constant names.
 */
class RobotFrameworkCodeGeneratorTest {

  private final RobotFrameworkCodeGenerator gen = new RobotFrameworkCodeGenerator();

  @Test
  void wheelUsesWheelDownOrWheelUpWithStepsBeforeTheImage() {
    assertEquals("    Wheel Down    3", gen.wheel(null, 1, 3, new String[0], 0));
    assertEquals("    Wheel Up    2", gen.wheel(null, -1, 2, new String[0], 0));
    assertEquals("    Wheel Down    3    list.png", gen.wheel(new Pattern("list.png"), 1, 3, new String[0], 0));
  }

  @Test
  void typedTextTargetsTheFocusWithAnEmptyImage() {
    assertEquals("    Input Text    ${EMPTY}    hello", gen.typeText("hello", new String[0]));
  }

  @Test
  void typedTextWithModifiersUsesTypeWithModifiers() {
    assertEquals("    Type With Modifiers    c    CTRL", gen.typeText("c", new String[] {"Key.CTRL"}));
  }

  @Test
  void specialKeyIsPressedByItsBareConstantName() {
    assertEquals("    Press Special Key    ENTER", gen.typeKey("Key.ENTER", new String[0]));
  }

  @Test
  void specialKeyWithModifiersHoldsThemAroundThePress() {
    assertEquals(
        "    Key Down    CTRL\n    Key Down    SHIFT\n    Press Special Key    TAB\n    Key Up    SHIFT\n    Key Up    CTRL",
        gen.typeKey("Key.TAB", new String[] {"Key.CTRL", "Key.SHIFT"}));
  }

  @Test
  void waitAlwaysPassesATimeout() {
    assertEquals("    Wait Until Screen Contain    button.png    10", gen.wait(new Pattern("button.png"), 10, null));
    assertEquals("    Wait Until Screen Contain    button.png    3", gen.wait(new Pattern("button.png"), null, null));
  }

  @Test
  void mouseDownAndUpTakeButtonsAndMoveToTheImageFirst() {
    assertEquals("    Mouse Down    LEFT", gen.mouseDown(null, new String[0]));
    assertEquals("    Mouse Move    item.png\n    Mouse Down    LEFT",
        gen.mouseDown(new Pattern("item.png"), new String[] {"Button.LEFT"}));
    assertEquals("    Mouse Up    RIGHT", gen.mouseUp(null, new String[] {"Button.RIGHT"}));
    assertEquals("    Mouse Move    item.png\n    Mouse Up    LEFT",
        gen.mouseUp(new Pattern("item.png"), new String[0]));
  }
}
