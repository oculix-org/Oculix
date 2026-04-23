/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.script.Key;
import org.sikuli.script.KeyModifier;
import org.sikuli.script.Screen;

import java.util.Locale;

/**
 * Press a keyboard combination such as {@code Ctrl+C}, {@code Cmd+Tab},
 * {@code Shift+F5}, or {@code Alt+Left}.
 *
 * <p>Input shape:
 * <pre>
 *   {
 *     "modifiers": ["ctrl", "shift"],
 *     "key": "c"  // or a special: "enter", "esc", "tab", "f5", ...
 *   }
 * </pre>
 */
public final class KeyComboTool implements Tool {

  @Override public String name()        { return "oculix_key_combo"; }
  @Override public String description() {
    return "Press a keyboard combination (modifiers + key), for example Ctrl+C or Cmd+Tab.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("modifiers", new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string")
                    .put("enum", new JSONArray().put("ctrl").put("alt")
                        .put("shift").put("meta").put("cmd").put("win")))
                .put("description", "List of modifier keys"))
            .put("key", new JSONObject()
                .put("type", "string")
                .put("description", "Single character or special key name (enter, esc, tab, f1..f12, up, down, left, right, home, end, pageup, pagedown, delete, backspace, space, insert)")))
        .put("required", new JSONArray().put("key"));
  }

  @Override public JSONObject call(JSONObject args) {
    String key = args.getString("key");
    int modMask = 0;
    if (args.has("modifiers")) {
      JSONArray mods = args.getJSONArray("modifiers");
      for (int i = 0; i < mods.length(); i++) {
        modMask |= toModifierMask(mods.getString(i));
      }
    }
    String keyCode = toKeyCode(key);
    int rc = new Screen().type(keyCode, modMask);
    return Tool.textResult(new JSONObject().put("pressed", rc == 1).toString());
  }

  private static int toModifierMask(String mod) {
    switch (mod.toLowerCase(Locale.ROOT)) {
      case "ctrl":  return KeyModifier.CTRL;
      case "alt":   return KeyModifier.ALT;
      case "shift": return KeyModifier.SHIFT;
      case "meta":
      case "cmd":
      case "win":   return KeyModifier.META;
      default:      return 0;
    }
  }

  private static String toKeyCode(String key) {
    switch (key.toLowerCase(Locale.ROOT)) {
      case "enter":     return Key.ENTER;
      case "esc":
      case "escape":    return Key.ESC;
      case "tab":       return Key.TAB;
      case "space":     return Key.SPACE;
      case "backspace": return Key.BACKSPACE;
      case "delete":    return Key.DELETE;
      case "insert":    return Key.INSERT;
      case "up":        return Key.UP;
      case "down":      return Key.DOWN;
      case "left":      return Key.LEFT;
      case "right":     return Key.RIGHT;
      case "home":      return Key.HOME;
      case "end":       return Key.END;
      case "pageup":    return Key.PAGE_UP;
      case "pagedown":  return Key.PAGE_DOWN;
      case "f1":  return Key.F1;   case "f2":  return Key.F2;
      case "f3":  return Key.F3;   case "f4":  return Key.F4;
      case "f5":  return Key.F5;   case "f6":  return Key.F6;
      case "f7":  return Key.F7;   case "f8":  return Key.F8;
      case "f9":  return Key.F9;   case "f10": return Key.F10;
      case "f11": return Key.F11;  case "f12": return Key.F12;
      default:
        // Single character or already a Key constant string
        return key;
    }
  }
}
