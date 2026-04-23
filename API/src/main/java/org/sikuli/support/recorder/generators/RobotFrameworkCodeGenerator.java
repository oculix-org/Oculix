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
    String dir = direction > 0 ? "down" : "up";
    String code = "    Wheel    " + dir + "    " + steps;
    if (pattern != null) {
      code = "    Wheel    " + pattern(pattern) + "    " + dir + "    " + steps;
    }
    return code;
  }

  @Override
  public String typeText(String text, String[] modifiers) {
    return "    Input Text    " + text;
  }

  @Override
  public String typeKey(String key, String[] modifiers) {
    if (modifiers.length > 0) {
      return "    Press Special Key    " + key + "    " + String.join("    ", modifiers);
    }
    return "    Press Special Key    " + key;
  }

  @Override
  public String wait(Pattern pattern, Integer seconds, IRecordedAction matchAction) {
    String code = "    Wait Until Screen Contain    " + pattern(pattern);
    if (seconds != null) {
      code += "    " + seconds;
    }
    return code;
  }

  @Override
  public String mouseDown(Pattern pattern, String[] buttons) {
    return "    Mouse Down    " + (pattern != null ? pattern(pattern) : "");
  }

  @Override
  public String mouseUp(Pattern pattern, String[] buttons) {
    return "    Mouse Up    " + (pattern != null ? pattern(pattern) : "");
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
