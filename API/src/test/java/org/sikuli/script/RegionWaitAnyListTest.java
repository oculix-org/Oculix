/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #485 and its root: waitAnyList runs one thread per target on the same Region, and every
 * thread used to write the shared lastMatch field, then read it back. Contract checked here:
 * <ul>
 *   <li>when waitAnyList returns, no search thread is still running on the region</li>
 *   <li>no search thread dies with an uncaught exception while the call is repeated</li>
 *   <li>the found matches are on the region: getLastMatchList(), and getLastMatch() is the first</li>
 * </ul>
 * The present target is a patch of the primary screen captured in memory, the absent ones are
 * random noise; nothing is drawn or written. Skipped on headless environments.
 */
class RegionWaitAnyListTest {

  private Region region;
  private List<Object> targets;

  @BeforeEach
  void onScreen() {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
    Screen screen = Screen.getPrimaryScreen();
    region = new Region(screen.x, screen.y, screen.w, screen.h);
    BufferedImage patch = screen.capture(screen.x, screen.y, 48, 48).getImage();
    Random random = new Random(485);
    List<Object> list = new ArrayList<>();
    list.add(new Image(patch, "waitany-present"));
    for (int i = 1; i <= 3; i++) {
      BufferedImage noise = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
      for (int y = 0; y < 48; y++) {
        for (int x = 0; x < 48; x++) {
          noise.setRGB(x, y, random.nextInt(0xFFFFFF));
        }
      }
      list.add(new Image(noise, "waitany-absent-" + i));
    }
    targets = list;
  }

  private static List<Thread> searchThreadsAlive() {
    List<Thread> alive = new ArrayList<>();
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      for (StackTraceElement frame : entry.getValue()) {
        if (frame.getClassName().endsWith("Region$SubFindRun")) {
          alive.add(entry.getKey());
          break;
        }
      }
    }
    return alive;
  }

  @Test
  void otherSearchThreadsStopAtTheirNextModeNotAfterTheWholeCascade() throws InterruptedException {
    for (int round = 0; round < 3; round++) {
      List<Match> found = region.waitAnyList(5, targets);
      assertFalse(found.isEmpty(), "round " + round + ": the on-screen patch was not found");
      Thread.sleep(1000);
      List<Thread> alive = searchThreadsAlive();
      assertTrue(alive.isEmpty(), "round " + round + ": " + alive.size()
          + " search thread(s) still running one second after waitAnyList returned");
    }
  }

  @Test
  void repeatedCallsLeaveNoDeadThreadBehind() {
    List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
    try {
      for (int round = 0; round < 10; round++) {
        region.waitAnyList(3, targets);
      }
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
    assertTrue(uncaught.isEmpty(), "a search thread died: " + uncaught);
  }

  @Test
  void foundMatchesAreOnTheRegion() {
    List<Match> found = region.waitAnyList(3, targets);
    assertFalse(found.isEmpty(), "the on-screen patch was not found");
    assertEquals(0, found.get(0).getIndex(), "the on-screen patch is target 0");
    assertEquals(region.x, found.get(0).x, "the patch was captured at the screen corner");
    assertEquals(region.y, found.get(0).y, "the patch was captured at the screen corner");
    assertEquals(found, region.getLastMatchList(), "getLastMatchList() is what waitAnyList returned");
    assertSame(found.get(0), region.getLastMatch(), "getLastMatch() is the first of the list");
  }
}
