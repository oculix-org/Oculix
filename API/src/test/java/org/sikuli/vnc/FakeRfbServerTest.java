package org.sikuli.vnc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sikuli.script.Mouse;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The test fixture itself: a real VNCScreen connects to {@link FakeRfbServer} through the public
 * API and the server records what the client sends. Uses only inputs that are already correct,
 * so this passes on the current code and keeps passing as VNC input fixes land on top of it.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class FakeRfbServerTest {

  @Test
  void recordsKeysAndPointerButtonsInOrder() throws Exception {
    try (FakeRfbServer server = new FakeRfbServer()) {
      VNCScreen screen = server.connect();
      try {
        screen.type("x");
        List<FakeRfbServer.Event> keys = server.awaitKeyEvents(2, 10_000);
        assertNull(server.failure(), "fake server failed");
        assertEquals(2, keys.size(), "press and release of x: " + keys);
        assertEquals(true, keys.get(0).down);
        assertEquals(0x78, keys.get(0).keysym);
        assertEquals(false, keys.get(1).down);
        assertEquals(0x78, keys.get(1).keysym);

        int before = server.pointerMasks().size();
        screen.getRobot().mouseDown(Mouse.LEFT);
        screen.getRobot().mouseUp(Mouse.LEFT);
        List<Integer> masks = server.awaitPointerMasks(before + 2, 10_000);
        assertNull(server.failure(), "fake server failed");
        assertEquals(Arrays.asList(1, 0), masks.subList(before, masks.size()), "left pressed, then released");
      } finally {
        screen.stop();
      }
    }
  }
}
