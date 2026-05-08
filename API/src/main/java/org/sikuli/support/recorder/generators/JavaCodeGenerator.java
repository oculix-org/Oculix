/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */

package org.sikuli.support.recorder.generators;

import org.sikuli.script.Image;
import org.sikuli.script.Location;
import org.sikuli.script.Mouse;
import org.sikuli.script.Pattern;
import org.sikuli.support.recorder.actions.IRecordedAction;

import java.io.File;
import java.util.Locale;

/**
 * Generates executable Java code snippets for OculiX API.
 */
public class JavaCodeGenerator implements ICodeGenerator {

  @Override
  public String pattern(Pattern pattern) {
    String imageFile = pattern.getFilename();
    Image image = pattern.getImage();
    float resizeFactor = pattern.getResize();
    double similarity = pattern.getSimilar();
    Location offset = pattern.getTargetOffset();

    String imgName = new File(imageFile).getName();
    if (image != null) {
      imgName = new File(image.getName()).getName();
    }

    String pat = "new Pattern(\"" + imgName + "\")";
    String patternString = "";

    if (resizeFactor > 0 && resizeFactor != 1) {
      patternString += String.format(Locale.ENGLISH, ".resize(%.2f)", resizeFactor);
    }
    if (similarity > 0) {
      if (similarity >= 0.99) {
        patternString += ".exact()";
      } else if (similarity != 0.7) {
        patternString += String.format(Locale.ENGLISH, ".similar(%.2f)", similarity);
      }
    }
    if (offset != null && (offset.x != 0 || offset.y != 0)) {
      patternString += ".targetOffset(" + offset.x + ", " + offset.y + ")";
    }

    if (!patternString.isEmpty()) {
      return pat + patternString;
    }
    return "\"" + imgName + "\"";
  }

  @Override
  public String click(Pattern pattern, String[] modifiers) {
    return mouse("click", pattern, modifiers);
  }

  @Override
  public String doubleClick(Pattern pattern, String[] modifiers) {
    return mouse("doubleClick", pattern, modifiers);
  }

  @Override
  public String rightClick(Pattern pattern, String[] modifiers) {
    return mouse("rightClick", pattern, modifiers);
  }

  @Override
  public String wheel(Pattern pattern, int direction, int steps, String[] modifiers, long stepDelay) {
    String code = "screen.wheel(";

    if (pattern != null) {
      code += pattern(pattern) + ", ";
    }

    code += direction > 0 ? "Button.WHEEL_DOWN" : "Button.WHEEL_UP";
    code += ", " + steps;

    if (modifiers.length > 0) {
      code += ", " + String.join(" + ", modifiers);
    }

    if (stepDelay != Mouse.WHEEL_STEP_DELAY) {
      if (modifiers.length == 0) {
        code += ", 0";
      }
      code += ", " + stepDelay;
    }

    code += ");";
    return code;
  }

  @Override
  public String typeText(String text, String[] modifiers) {
    if (modifiers.length > 0) {
      return "screen.type(\"" + escapeJava(text) + "\", " + String.join(" + ", modifiers) + ");";
    }
    return "screen.type(\"" + escapeJava(text) + "\");";
  }

  @Override
  public String typeKey(String key, String[] modifiers) {
    if (modifiers.length > 0) {
      return "screen.type(" + key + ", " + String.join(" + ", modifiers) + ");";
    }
    return "screen.type(" + key + ");";
  }

  @Override
  public String wait(Pattern pattern, Integer seconds, IRecordedAction matchAction) {
    String code = "screen.wait(";

    if (pattern != null) {
      code += pattern(pattern);
    }

    if (seconds != null) {
      if (pattern != null) {
        code += ", ";
      }
      code += seconds;
    }

    code += ")";

    if (matchAction != null) {
      code += "." + matchAction.generate(this);
    }

    code += ";";
    return code;
  }

  @Override
  public String mouseDown(Pattern pattern, String[] buttons) {
    return mouse("mouseDown", pattern, buttons);
  }

  @Override
  public String mouseUp(Pattern pattern, String[] buttons) {
    return mouse("mouseUp", pattern, buttons);
  }

  @Override
  public String mouseMove(Pattern pattern) {
    return mouse("mouseMove", pattern, new String[0]);
  }

  @Override
  public String dragDrop(Pattern sourcePattern, Pattern targetPattern) {
    String code = "screen.dragDrop(";
    if (sourcePattern != null) {
      code += pattern(sourcePattern) + ", ";
    }
    code += pattern(targetPattern) + ");";
    return code;
  }

  private String mouse(String type, Pattern pattern, String[] modifiersOrButtons) {
    String code = "screen." + type + "(";

    if (pattern != null) {
      code += pattern(pattern);
    }

    if (modifiersOrButtons.length > 0) {
      if (pattern != null) {
        code += ", ";
      }
      code += String.join(" + ", modifiersOrButtons);
    }

    code += ");";
    return code;
  }

  private String escapeJava(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }
}
