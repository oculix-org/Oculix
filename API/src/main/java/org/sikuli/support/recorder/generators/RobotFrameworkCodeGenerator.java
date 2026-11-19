/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */

package org.sikuli.support.recorder.generators;

import org.sikuli.script.Image;
import org.sikuli.script.Location;
import org.sikuli.script.Pattern;
import org.sikuli.support.recorder.actions.IRecordedAction;

import java.io.File;
import java.util.Locale;

/**
 * Generates Robot Framework keywords compatible with robotframework-SikuliLibrary.
 */
public class RobotFrameworkCodeGenerator implements ICodeGenerator {

  @Override
  public String pattern(Pattern pattern) {
    String imageFile = pattern.getFilename();
    Image image = pattern.getImage();
    String imgName = new File(imageFile).getName();
    if (image != null) {
      imgName = new File(image.getName()).getName();
    }
    return imgName;
  }

  @Override
  public String click(Pattern pattern, String[] modifiers) {
    return "    Click    " + pattern(pattern);
  }

  @Override
  public String doubleClick(Pattern pattern, String[] modifiers) {
    return "    Double Click    " + pattern(pattern);
  }

  @Override
  public String rightClick(Pattern pattern, String[] modifiers) {
    return "    Right Click    " + pattern(pattern);
  }

  @Override
  public String wheel(Pattern pattern, int direction, int steps, String[] modifiers, long stepDelay) {
    String code = (direction > 0 ? "    Wheel Down    " : "    Wheel Up    ") + steps;
    if (pattern != null) {
      code += "    " + pattern(pattern);
    }
    return code;
  }

  @Override
  public String typeText(String text, String[] modifiers) {
    if (modifiers.length > 0) {
      return "    Type With Modifiers    " + text + "    " + String.join("    ", bare(modifiers));
    }
    return "    Input Text    ${EMPTY}    " + text;
  }

  @Override
  public String typeKey(String key, String[] modifiers) {
    String[] held = bare(modifiers);
    StringBuilder code = new StringBuilder();
    for (String modifier : held) {
      code.append("    Key Down    ").append(modifier).append('\n');
    }
    code.append("    Press Special Key    ").append(bare(key));
    for (int i = held.length - 1; i >= 0; i--) {
      code.append("\n    Key Up    ").append(held[i]);
    }
    return code.toString();
  }

  @Override
  public String wait(Pattern pattern, Integer seconds, IRecordedAction matchAction) {
    return "    Wait Until Screen Contain    " + pattern(pattern) + "    "
        + (seconds != null ? seconds : DEFAULT_WAIT_SECONDS);
  }

  @Override
  public String mouseDown(Pattern pattern, String[] buttons) {
    return mouse("Mouse Down", pattern, buttons);
  }

  @Override
  public String mouseUp(Pattern pattern, String[] buttons) {
    return mouse("Mouse Up", pattern, buttons);
  }

  /** SikuliLibrary takes the timeout as a mandatory argument; 3 s is the SikuliX default auto-wait. */
  private static final int DEFAULT_WAIT_SECONDS = 3;

  private String mouse(String keyword, Pattern pattern, String[] buttons) {
    String[] names = buttons.length > 0 ? bare(buttons) : new String[] {"LEFT"};
    String code = "    " + keyword + "    " + String.join("    ", names);
    return pattern != null ? "    Mouse Move    " + pattern(pattern) + "\n" + code : code;
  }

  /** The recorder writes {@code Key.CTRL} or {@code Button.LEFT}; SikuliLibrary wants {@code CTRL}, {@code LEFT}. */
  private static String bare(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(dot + 1) : name;
  }

  private static String[] bare(String[] names) {
    String[] result = new String[names.length];
    for (int i = 0; i < names.length; i++) {
      result[i] = bare(names[i]);
    }
    return result;
  }

  @Override
  public String mouseMove(Pattern pattern) {
    return "    Mouse Move    " + pattern(pattern);
  }

  @Override
  public String dragDrop(Pattern sourcePattern, Pattern targetPattern) {
    return "    Drag And Drop    " + pattern(sourcePattern) + "    " + pattern(targetPattern);
  }
}
