/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix.org - MIT license
 */
package org.sikuli.natives;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import org.sikuli.basics.Debug;
import org.sikuli.natives.mac.jna.CoreGraphics;
import org.sikuli.support.Commons;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The two macOS permissions OculiX cannot work without. Without Screen Recording a
 * capture shows the desktop picture and nothing else, so every find fails; without
 * Accessibility the mouse and keyboard events go nowhere.
 * <p>
 * Asked through the system API where it exists: {@code CGPreflightScreenCaptureAccess}
 * from macOS 11, {@code AXIsProcessTrusted}. Before macOS 11 the screen check falls back
 * to the menu bar test of SikuliX 2.0.5.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 5.0.0
 */
public final class MacPermissions {

  /** A permission, with its list in System Settings and what OculiX needs it for. */
  public enum Permission {
    SCREEN_RECORDING("Screen Recording", "see the screen", "Privacy_ScreenCapture"),
    ACCESSIBILITY("Accessibility", "use the mouse and the keyboard", "Privacy_Accessibility");

    private final String settingsName;
    private final String purpose;
    private final String anchor;

    Permission(String settingsName, String purpose, String anchor) {
      this.settingsName = settingsName;
      this.purpose = purpose;
      this.anchor = anchor;
    }

    /** The name of its list under System Settings &gt; Privacy &amp; Security. */
    public String settingsName() {
      return settingsName;
    }

    /** What OculiX cannot do without it. */
    public String purpose() {
      return purpose;
    }

    /** The URL that opens System Settings on its list. */
    public String settingsUrl() {
      return "x-apple.systempreferences:com.apple.preference.security?" + anchor;
    }
  }

  private static final String APPLICATION_SERVICES = "ApplicationServices";
  private static final String CORE_FOUNDATION = "CoreFoundation";

  private MacPermissions() {
  }

  /**
   * The permissions this process does not have, Screen Recording first. Empty on any
   * system other than macOS, and empty when both are granted.
   */
  public static List<Permission> missing() {
    if (!Commons.runningMac()) {
      return Collections.emptyList();
    }
    return missing(screenRecordingAllowed(), accessibilityAllowed());
  }

  /** The permissions missing for the given answers, Screen Recording first. */
  static List<Permission> missing(boolean screenRecording, boolean accessibility) {
    List<Permission> result = new ArrayList<>(2);
    if (!screenRecording) {
      result.add(Permission.SCREEN_RECORDING);
    }
    if (!accessibility) {
      result.add(Permission.ACCESSIBILITY);
    }
    return result;
  }

  /**
   * Puts this process in the System Settings list of each permission, so the user only
   * has a switch to turn on. macOS shows its own prompt the first time it is asked.
   */
  public static void request(List<Permission> permissions) {
    for (Permission permission : permissions) {
      try {
        if (permission == Permission.SCREEN_RECORDING) {
          CoreGraphics.INSTANCE.CGRequestScreenCaptureAccess();
        } else {
          promptAccessibility();
        }
      } catch (UnsatisfiedLinkError e) {
        // Before macOS 11 the capture of the menu bar test has already listed the process.
        Debug.log(3, "MacPermissions: no request call for %s on this macOS", permission);
      } catch (RuntimeException e) {
        Debug.log(3, "MacPermissions: request %s: %s", permission, e.getMessage());
      }
    }
  }

  /**
   * What to tell the user in plain text: each missing permission, the command that opens
   * its list, and that OculiX must start again, since macOS applies a permission only to
   * a program started after it is given.
   */
  public static String message(List<Permission> missing) {
    StringBuilder text = new StringBuilder("macOS does not let OculiX work yet. Missing permission")
        .append(missing.size() > 1 ? "s:" : ":");
    for (Permission permission : missing) {
      text.append("\n  - ").append(permission.settingsName())
          .append(", to ").append(permission.purpose())
          .append("\n      open \"").append(permission.settingsUrl()).append('"');
    }
    return text.append("\nIn System Settings > Privacy & Security, turn on the program that runs OculiX")
        .append(" (Java, or the terminal it was started from), then start OculiX again.")
        .toString();
  }

  /** True when, on each channel, the pixels of {@code rgb} differ by less than 2. */
  static boolean isSingleColour(int[] rgb) {
    int[] min = {255, 255, 255};
    int[] max = {0, 0, 0};
    for (int pixel : rgb) {
      for (int channel = 0; channel < 3; channel++) {
        int value = (pixel >> (16 - 8 * channel)) & 0xFF;
        min[channel] = Math.min(min[channel], value);
        max[channel] = Math.max(max[channel], value);
      }
    }
    for (int channel = 0; channel < 3; channel++) {
      if (max[channel] - min[channel] >= 2) {
        return false;
      }
    }
    return true;
  }

  private static boolean screenRecordingAllowed() {
    try {
      return CoreGraphics.INSTANCE.CGPreflightScreenCaptureAccess() != 0;
    } catch (UnsatisfiedLinkError e) {
      Debug.log(3, "MacPermissions: no CGPreflightScreenCaptureAccess, using the menu bar test");
      return menuBarIsCaptured();
    }
  }

  /**
   * The SikuliX 2.0.5 test, for macOS before 11: without the permission the capture
   * holds the desktop picture only, and the menu bar row comes out in one colour. Reads
   * the row 10 pixels down, from x = 200. When the test cannot run, the answer is yes:
   * OculiX starts as it did before this check.
   */
  private static boolean menuBarIsCaptured() {
    try {
      int width = Toolkit.getDefaultToolkit().getScreenSize().width - 200;
      if (width <= 0) {
        return true;
      }
      BufferedImage row = new Robot().createScreenCapture(new Rectangle(200, 10, width, 1));
      return !isSingleColour(row.getRGB(0, 0, width, 1, null, 0, width));
    } catch (Exception e) {
      Debug.log(3, "MacPermissions: menu bar test not possible: %s", e.getMessage());
      return true;
    }
  }

  /** When the answer cannot be read, yes: the mouse test of MouseDevice still runs. */
  private static boolean accessibilityAllowed() {
    try {
      Function trusted = NativeLibrary.getInstance(APPLICATION_SERVICES).getFunction("AXIsProcessTrusted");
      return (Byte) trusted.invoke(Byte.class, new Object[0]) != 0;
    } catch (UnsatisfiedLinkError | RuntimeException e) {
      Debug.log(3, "MacPermissions: AXIsProcessTrusted not readable: %s", e.getMessage());
      return true;
    }
  }

  /** {@code AXIsProcessTrustedWithOptions} with the prompt option set to true. */
  private static void promptAccessibility() {
    NativeLibrary cf = NativeLibrary.getInstance(CORE_FOUNDATION);
    NativeLibrary ax = NativeLibrary.getInstance(APPLICATION_SERVICES);
    Memory keys = new Memory(Native.POINTER_SIZE);
    keys.setPointer(0, ax.getGlobalVariableAddress("kAXTrustedCheckOptionPrompt").getPointer(0));
    Memory values = new Memory(Native.POINTER_SIZE);
    values.setPointer(0, cf.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0));
    Pointer options = cf.getFunction("CFDictionaryCreate").invokePointer(new Object[]{
        null, keys, values, new NativeLong(1),
        cf.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
        cf.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")});
    if (options == null) {
      return;
    }
    try {
      ax.getFunction("AXIsProcessTrustedWithOptions").invoke(Byte.class, new Object[]{options});
    } finally {
      cf.getFunction("CFRelease").invokeVoid(new Object[]{options});
    }
  }
}
