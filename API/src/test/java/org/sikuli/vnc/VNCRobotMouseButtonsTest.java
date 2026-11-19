package org.sikuli.vnc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sikuli.script.Button;
import org.sikuli.script.Mouse;
import org.sikuli.support.devices.IRobot;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #493: the VNC robot must honour the documented mouse contract that Region and the desktop robot
 * already follow. {@code Button.*} constants press and release (they are the ones Mouse.up's
 * javadoc names), and {@code mouseUp(0)} releases every held button.
 * <p>
 * The calls go straight to the robot with exactly what {@code Region.mouseDown(b)} and
 * {@code Region.mouseUp()} hand it ({@code Mouse.down(b)} and {@code Mouse.up(0)} pass the value
 * through unchanged). Going through Region would run MouseDevice, which moves the LOCAL pointer.
 * RFB button mask: 1 = left, 2 = middle, 4 = right.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class VNCRobotMouseButtonsTest {

  private FakeRfbServer server;
  private VNCScreen screen;
  private IRobot robot;

  @BeforeEach
  void connect() throws Exception {
    server = new FakeRfbServer();
    screen = server.connect();
    robot = screen.getRobot();
  }

  @AfterEach
  void disconnect() throws Exception {
    try {
      if (screen != null) screen.stop(); // null if connect() threw: keep its error visible
    } finally {
      if (server != null) server.close();
    }
  }

  /** The button masks of the pointer events one action sends. */
  private List<Integer> masksOf(Runnable action, int expectedEvents) throws InterruptedException {
    int before = server.pointerMasks().size(); // counted BEFORE the action
    action.run();
    List<Integer> all = server.awaitPointerMasks(before + expectedEvents, 10_000);
    assertNull(server.failure(), "fake server failed");
    return all.subList(before, all.size());
  }

  @Test
  void control_mouseConstants_pressAndRelease() throws Exception {
    assertEquals(Arrays.asList(1), masksOf(() -> robot.mouseDown(Mouse.LEFT), 1));
    assertEquals(Arrays.asList(0), masksOf(() -> robot.mouseUp(Mouse.LEFT), 1));
  }

  @Test
  void buttonConstants_pressAndRelease() throws Exception {
    assertEquals(Arrays.asList(1), masksOf(() -> robot.mouseDown(Button.LEFT), 1), "Button.LEFT presses");
    assertEquals(Arrays.asList(0), masksOf(() -> robot.mouseUp(Button.LEFT), 1), "Button.LEFT releases");
    assertEquals(Arrays.asList(6), masksOf(() -> robot.mouseDown(Button.MIDDLE | Button.RIGHT), 1),
        "Button.MIDDLE|RIGHT press both");
    assertEquals(Arrays.asList(0), masksOf(() -> robot.mouseUp(Button.MIDDLE | Button.RIGHT), 1));
  }

  @Test
  void mouseUpZero_releasesEveryHeldButton() throws Exception {
    masksOf(() -> robot.mouseDown(Mouse.LEFT), 1);
    masksOf(() -> robot.mouseDown(Button.RIGHT), 1);
    int[] remaining = new int[1];
    assertEquals(Arrays.asList(0), masksOf(() -> remaining[0] = robot.mouseUp(0), 1), "mouseUp(0) = release all");
    assertEquals(0, remaining[0], "nothing is held afterwards");
  }

  @Test
  void mouseUp_reportsWhatIsStillHeld() throws Exception {
    masksOf(() -> robot.mouseDown(Mouse.LEFT | Mouse.RIGHT), 1);
    int[] remaining = new int[1];
    assertEquals(Arrays.asList(4), masksOf(() -> remaining[0] = robot.mouseUp(Button.LEFT), 1));
    assertEquals(Mouse.RIGHT, remaining[0], "right is still held, reported as Mouse.RIGHT");
  }
}
