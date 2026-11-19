/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feature put in front of real threads that play the failures a script
 * can have: blocked on a lock, dead without reporting, spinning on the same
 * frame; and the pauses that are not failures: sleeping, waiting. The tray
 * observes them the way it observes a script, from outside.
 */
class TrayStateThreadTest {

  private static final long THRESHOLD_MS = 200;

  private final TrayState state = new TrayState(System::currentTimeMillis, THRESHOLD_MS);

  @Test
  void aScriptBlockedOnALockHeldBySomeoneElseTurnsStuck() throws Exception {
    Object lock = new Object();
    CountDownLatch started = new CountDownLatch(1);
    Thread script = new Thread(() -> {
      started.countDown();
      synchronized (lock) {
        // never reached while the test holds the lock
      }
    }, "mock-script-blocked");
    synchronized (lock) {
      script.start();
      started.await(2, TimeUnit.SECONDS);
      state.runStarted("blocked.sikuli");
      assertEquals(TrayState.State.STUCK, observeUntil(script, TrayState.State.STUCK, 3_000));
      assertTrue(state.where().startsWith(TrayStateThreadTest.class.getName()), state.where());
    }
    script.join(2_000);
  }

  @Test
  void aScriptThatDiedWithoutReportingItsEndTurnsStuckAtOnce() throws Exception {
    Thread script = new Thread(() -> { }, "mock-script-dead");
    state.runStarted("dead.sikuli");
    script.start();
    script.join(2_000);
    assertEquals(TrayState.State.STUCK, state.observe(script, false));
  }

  @Test
  void aScriptSpinningOnTheSameFrameTurnsStuck() throws Exception {
    Thread script = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.onSpinWait();
      }
    }, "mock-script-spinning");
    script.start();
    state.runStarted("spinning.sikuli");
    try {
      assertEquals(TrayState.State.STUCK, observeUntil(script, TrayState.State.STUCK, 3_000));
    } finally {
      script.interrupt();
      script.join(2_000);
    }
  }

  @Test
  void aScriptThatSleepsIsAlive() throws Exception {
    Thread script = new Thread(() -> {
      try {
        Thread.sleep(2_000);
      } catch (InterruptedException ignored) {
      }
    }, "mock-script-sleeping");
    script.start();
    state.runStarted("sleeping.sikuli");
    try {
      Thread.sleep(THRESHOLD_MS * 3);
      assertEquals(TrayState.State.RUNNING, state.observe(script, false));
      assertEquals(TrayState.State.SEARCHING, state.observe(script, true));
    } finally {
      script.interrupt();
      script.join(2_000);
    }
  }

  @Test
  void aScriptWaitingForAnAnswerIsAlive() throws Exception {
    CountDownLatch answer = new CountDownLatch(1);
    Thread script = new Thread(() -> {
      try {
        answer.await();
      } catch (InterruptedException ignored) {
      }
    }, "mock-script-waiting");
    script.start();
    state.runStarted("waiting.sikuli");
    try {
      Thread.sleep(THRESHOLD_MS * 3);
      assertEquals(TrayState.State.RUNNING, state.observe(script, false));
    } finally {
      answer.countDown();
      script.join(2_000);
    }
  }

  @Test
  void aScriptThatKeepsMovingIsAlive() throws Exception {
    Thread script = new Thread(() -> {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          Thread.sleep(20);
          Thread.onSpinWait();
        }
      } catch (InterruptedException ignored) {
      }
    }, "mock-script-moving");
    script.start();
    state.runStarted("moving.sikuli");
    try {
      long end = System.currentTimeMillis() + THRESHOLD_MS * 3;
      while (System.currentTimeMillis() < end) {
        assertEquals(TrayState.State.RUNNING, state.observe(script, false));
        Thread.sleep(10);
      }
    } finally {
      script.interrupt();
      script.join(2_000);
    }
  }

  /** Observes the thread every 20 ms until it reports the wanted state or the delay runs out. */
  private TrayState.State observeUntil(Thread thread, TrayState.State wanted, long delayMs) throws InterruptedException {
    long end = System.currentTimeMillis() + delayMs;
    TrayState.State seen = state.observe(thread, false);
    while (seen != wanted && System.currentTimeMillis() < end) {
      Thread.sleep(20);
      seen = state.observe(thread, false);
    }
    return seen;
  }
}
