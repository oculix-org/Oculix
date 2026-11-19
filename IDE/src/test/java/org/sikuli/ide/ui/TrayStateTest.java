/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tray colour follows the script thread. The failures come first: a
 * thread frozen in native code, blocked on a lock, or dead without the run
 * reporting its end, each must turn the icon red. Then the paths that work:
 * a moving thread, a sleeping or waiting one, a run that ends, a new run.
 */
class TrayStateTest {

  private static final String FIND = "org.sikuli.script.Finder.find(Finder.java:120)";
  private static final String TYPE = "org.sikuli.script.Region.type(Region.java:4010)";
  private static final String NATIVE = "org.opencv.imgproc.Imgproc.matchTemplate_0(Native Method)";
  private static final String SLEEP = "java.lang.Thread.sleep(Native Method)";

  private long now = 1_000_000L;
  private final TrayState state = new TrayState(() -> now, 30_000L);

  @Nested
  class WhenTheScriptBreaks {

    @Test
    void aThreadFrozenInNativeCodeTurnsStuckOnceTheThresholdIsPassed() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.RUNNABLE, NATIVE, false);
      now += 31_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.RUNNABLE, NATIVE, false));
      assertEquals(31L, state.frozenSeconds());
      assertEquals(NATIVE, state.where());
    }

    @Test
    void aThreadFrozenWhileSearchingTurnsStuckToo() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.RUNNABLE, NATIVE, true);
      now += 31_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.RUNNABLE, NATIVE, true));
    }

    @Test
    void aThreadBlockedOnALockTurnsStuck() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.BLOCKED, TYPE, false);
      now += 31_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.BLOCKED, TYPE, false));
    }

    @Test
    void aThreadThatDiedWithoutReportingItsEndIsStuckAtOnce() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.RUNNABLE, TYPE, false);
      now += 1_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.TERMINATED, null, false));
      assertEquals(TrayState.State.STUCK, state.state(null, null, false));
    }

    @Test
    void aThreadStillMovingJustBeforeTheThresholdIsNotYetStuck() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.RUNNABLE, NATIVE, false);
      now += 29_000L;
      assertEquals(TrayState.State.RUNNING, state.state(Thread.State.RUNNABLE, NATIVE, false));
    }
  }

  @Nested
  class WhenTheScriptWorks {

    @Test
    void idleWhileNoScriptRuns() {
      assertEquals(TrayState.State.IDLE, state.state(Thread.State.RUNNABLE, NATIVE, false));
      now += 60_000L;
      assertEquals(TrayState.State.IDLE, state.state(Thread.State.RUNNABLE, NATIVE, true));
    }

    @Test
    void aMovingThreadIsRunningOrSearching() {
      state.runStarted("demo.sikuli");
      assertEquals(TrayState.State.RUNNING, state.state(Thread.State.RUNNABLE, TYPE, false));
      now += 5_000L;
      assertEquals(TrayState.State.SEARCHING, state.state(Thread.State.RUNNABLE, FIND, true));
    }

    @Test
    void sleepingOrWaitingIsAPauseNotAFreeze() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.TIMED_WAITING, SLEEP, false);
      now += 120_000L;
      assertEquals(TrayState.State.RUNNING, state.state(Thread.State.TIMED_WAITING, SLEEP, false));
      state.state(Thread.State.WAITING, SLEEP, false);
      now += 120_000L;
      assertEquals(TrayState.State.RUNNING, state.state(Thread.State.WAITING, SLEEP, false));
    }

    @Test
    void aChangeOfFrameBringsAStuckThreadBack() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.RUNNABLE, NATIVE, false);
      now += 45_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.RUNNABLE, NATIVE, false));
      assertEquals(TrayState.State.RUNNING, state.state(Thread.State.RUNNABLE, TYPE, false));
      assertEquals(0L, state.frozenSeconds());
    }

    @Test
    void theEndOfTheRunIsIdleEvenWhenStuck() {
      state.runStarted("demo.sikuli");
      state.state(Thread.State.RUNNABLE, NATIVE, false);
      now += 60_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.RUNNABLE, NATIVE, false));
      state.runEnded();
      assertEquals(TrayState.State.IDLE, state.state(Thread.State.RUNNABLE, NATIVE, false));
    }

    @Test
    void aNewRunStartsWithACleanSlate() {
      state.runStarted("first.sikuli");
      state.state(Thread.State.RUNNABLE, NATIVE, false);
      now += 45_000L;
      assertEquals(TrayState.State.STUCK, state.state(Thread.State.RUNNABLE, NATIVE, false));
      state.runEnded();
      state.runStarted("second.sikuli");
      assertEquals(TrayState.State.RUNNING, state.state(Thread.State.RUNNABLE, NATIVE, false));
      assertEquals("second.sikuli", state.script());
    }
  }
}
