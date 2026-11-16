package org.sikuli.vnc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #494: on a VNCScreen, text with non-ASCII characters must be typed as keysyms, not handed to
 * {@code paste()}. Before this fix, {@code type("café")} wrote the local clipboard, sent the local
 * paste shortcut (Control_L + v) to the target, and returned 1: the target pasted its own clipboard,
 * or nothing. {@code paste()} itself had the same defect on a remote screen.
 */
class VNCRobotTypeUnicodeTest {

  private static final int XK_CONTROL_L = 0xffe3;

  private static List<FakeRfbServer.Event> record(int events, Consumer<VNCScreen> action) throws Exception {
    try (FakeRfbServer server = new FakeRfbServer()) {
      VNCScreen screen = server.connect();
      try {
        action.accept(screen);
        List<FakeRfbServer.Event> keys = server.awaitKeyEvents(events, 10_000);
        assertNull(server.failure(), "fake server failed");
        return keys;
      } finally {
        screen.stop();
      }
    }
  }

  /** The keysyms of the press events, in order, as hex strings so a failure reads well. */
  private static List<String> pressed(List<FakeRfbServer.Event> keys) {
    List<String> out = new ArrayList<>();
    for (FakeRfbServer.Event e : keys) {
      if (e.down) {
        out.add(String.format("0x%04x", e.keysym));
      }
    }
    return out;
  }

  private static List<String> hex(int... keysyms) {
    List<String> out = new ArrayList<>();
    for (int k : keysyms) {
      out.add(String.format("0x%04x", k));
    }
    return out;
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void typeLatin1Text_typesEveryCharacter_noPasteShortcut() throws Exception {
    List<FakeRfbServer.Event> keys = record(8, s -> s.type("café"));
    assertEquals(hex('c', 'a', 'f', 0x00e9), pressed(keys), "type(\"café\") key presses: " + keys);
    assertEquals(false, pressed(keys).contains(String.format("0x%04x", XK_CONTROL_L)), "no Control_L: " + keys);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void typeBeyondLatin1_sendsTheUnicodeKeysym() throws Exception {
    // U+4F60 (你): X11's Unicode keysym is 0x01000000 | code point.
    List<FakeRfbServer.Event> keys = record(2, s -> s.type("\u4f60"));
    assertEquals(hex(0x01004f60), pressed(keys), "type(\"\\u4f60\") key presses: " + keys);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void paste_onARemoteScreen_typesTheText() throws Exception {
    List<FakeRfbServer.Event> keys = record(2, s -> s.paste("\u00e9"));
    assertEquals(hex(0x00e9), pressed(keys), "paste(\"\\u00e9\") key presses: " + keys);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void asciiText_unchanged() throws Exception {
    // Control: ASCII never went through paste, on any screen.
    List<FakeRfbServer.Event> keys = record(6, s -> s.type("abc"));
    assertEquals(hex('a', 'b', 'c'), pressed(keys), "type(\"abc\") key presses: " + keys);
  }
}
