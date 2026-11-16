package org.sikuli.vnc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sikuli.script.Key;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #497: a VNCScreen must send the same keysym for a {@code Key.*} constant whatever OS the client
 * runs on. On a Windows client {@code Key.toJavaKeyCode} follows the Windows keyboard layout and
 * gives Windows virtual-key codes, which {@code keyToXlib} then read as Java key codes:
 * {@code Key.INSERT} (VK 0x2D) went out as "-", {@code Key.PRINTSCREEN} (0x2C) as ",", and
 * {@code Key.F13}..{@code F15} and {@code Key.ALTGR} threw "Cannot type keycode". On macOS and
 * Linux these tests pass with or without the fix; they only go red on a Windows client.
 * Delete and META alone are covered by their own tests (#495, #496).
 */
class VNCRobotWindowsKeyCodesTest {

  private static List<FakeRfbServer.Event> typeAndRecord(String text) throws Exception {
    try (FakeRfbServer server = new FakeRfbServer()) {
      VNCScreen screen = server.connect();
      try {
        screen.type(text);
        List<FakeRfbServer.Event> keys = server.awaitKeyEvents(2, 10_000);
        assertNull(server.failure(), "fake server failed");
        return keys;
      } finally {
        screen.stop();
      }
    }
  }

  private static void assertPressRelease(String what, List<FakeRfbServer.Event> keys, int keysym) {
    assertEquals(2, keys.size(), what + ": one press and one release: " + keys);
    assertEquals(true, keys.get(0).down, what + ": first event is the press: " + keys);
    assertEquals(false, keys.get(1).down, what + ": second event is the release: " + keys);
    assertEquals(String.format("0x%04x", keysym), String.format("0x%04x", keys.get(0).keysym), what + " press keysym");
    assertEquals(String.format("0x%04x", keysym), String.format("0x%04x", keys.get(1).keysym), what + " release keysym");
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void insert_sendsXK_Insert() throws Exception {
    assertPressRelease("Key.INSERT", typeAndRecord(Key.INSERT), XKeySym.XK_Insert);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void printScreen_sendsXK_Print() throws Exception {
    assertPressRelease("Key.PRINTSCREEN", typeAndRecord(Key.PRINTSCREEN), XKeySym.XK_Print);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void f13toF15_sendXK_F13toF15() throws Exception {
    assertPressRelease("Key.F13", typeAndRecord(Key.F13), XKeySym.XK_F13);
    assertPressRelease("Key.F14", typeAndRecord(Key.F14), XKeySym.XK_F14);
    assertPressRelease("Key.F15", typeAndRecord(Key.F15), XKeySym.XK_F15);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void altGr_sendsISO_Level3_Shift() throws Exception {
    assertPressRelease("Key.ALTGR", typeAndRecord(Key.ALTGR), XKeySym.XK_ISO_Level3_Shift);
  }
}
