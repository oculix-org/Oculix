package org.sikuli.vnc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sikuli.script.Key;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #495: {@code type(Key.DELETE)} on a VNCScreen must send {@code XK_Delete} (0xffff), the Delete
 * key, not {@code XK_KP_Delete} (0xff9f), the keypad key that shares the decimal point. Measured
 * before this fix: QNX 8.0 vncserv typed "." and macOS Screen Sharing delivered nothing.
 */
class VNCRobotDeleteKeyTest {

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

  private static void assertPressRelease(List<FakeRfbServer.Event> keys, int keysym) {
    assertEquals(2, keys.size(), "one press and one release: " + keys);
    assertEquals(true, keys.get(0).down, "first event is the press: " + keys);
    assertEquals(false, keys.get(1).down, "second event is the release: " + keys);
    assertEquals(String.format("0x%04x", keysym), String.format("0x%04x", keys.get(0).keysym), "press keysym");
    assertEquals(String.format("0x%04x", keysym), String.format("0x%04x", keys.get(1).keysym), "release keysym");
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void keyDelete_sendsXK_Delete() throws Exception {
    assertPressRelease(typeAndRecord(Key.DELETE), XKeySym.XK_Delete);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void deleteCharacter_alsoSendsXK_Delete() throws Exception {
    // Control: U+007F already went out as XK_Delete via charToXlib; both spellings now agree.
    assertPressRelease(typeAndRecord("\u007f"), XKeySym.XK_Delete);
  }
}
