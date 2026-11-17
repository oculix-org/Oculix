package org.sikuli.vnc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sikuli.script.Key;
import org.sikuli.script.KeyModifier;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #496: META (= CMD = WIN as KeyModifier, and Key.META as a key) must go out as XK_Super_L
 * (0xffeb), the key macOS Screen Sharing delivers as Command, not as XK_Meta_L (0xffe7), which
 * macOS delivered as Option and Xtigervnc as Alt. Measured on those servers before this fix.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class VNCRobotMetaKeyTest {

  private interface Action {
    void run(VNCScreen screen) throws Exception;
  }

  /** The keysyms one action sends, as "+0xffeb" (press) / "-0xffeb" (release). */
  private static List<String> keysOf(Action action, int expected) throws Exception {
    try (FakeRfbServer server = new FakeRfbServer()) {
      VNCScreen screen = server.connect();
      try {
        action.run(screen);
        List<FakeRfbServer.Event> keys = server.awaitKeyEvents(expected, 10_000);
        assertNull(server.failure(), "fake server failed");
        List<String> out = new ArrayList<>();
        for (FakeRfbServer.Event e : keys) {
          out.add(String.format("%s0x%04x", e.down ? "+" : "-", e.keysym));
        }
        return out;
      } finally {
        screen.stop();
      }
    }
  }

  @Test
  void metaModifier_holdsSuperL() throws Exception {
    assertEquals(Arrays.asList("+0xffeb", "+0x0064", "-0x0064", "-0xffeb"),
        keysOf(s -> s.type("d", KeyModifier.META), 4));
  }

  @Test
  void cmdModifier_isTheSameKey() throws Exception {
    // KeyModifier.CMD and .WIN are the same bit as META.
    assertEquals(Arrays.asList("+0xffeb", "+0x0063", "-0x0063", "-0xffeb"),
        keysOf(s -> s.type("c", KeyModifier.CMD), 4));
  }

  @Test
  void metaKey_alone_isSuperL() throws Exception {
    assertEquals(Arrays.asList("+0xffeb", "-0xffeb"), keysOf(s -> s.type(Key.META), 2));
  }

  @Test
  void robotKeyDownVkMeta_isSuperL() throws Exception {
    // The path paste() takes on a Mac client: Key.getHotkeyModifier() is VK_META there.
    assertEquals(Arrays.asList("+0xffeb", "-0xffeb"), keysOf(s -> {
      s.getRobot().keyDown(KeyEvent.VK_META);
      s.getRobot().keyUp(KeyEvent.VK_META);
    }, 2));
  }

  @Test
  void control_altModifier_staysAltL() throws Exception {
    assertEquals(Arrays.asList("+0xffe9", "+0x0064", "-0x0064", "-0xffe9"),
        keysOf(s -> s.type("d", KeyModifier.ALT), 4));
  }
}
