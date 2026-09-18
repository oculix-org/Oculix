/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tray colour follows the run and its pulse: a script that beats is
 * running, one that goes quiet turns stuck, and the end of the run turns
 * everything back to idle.
 */
class TrayStateTest {

  private long now = 1_000_000L;
  private final TrayState state = new TrayState(() -> now, 30_000L);

  @Test
  void idleWhileNoScriptRuns() {
    assertEquals(TrayState.State.IDLE, state.state(now, false));
    assertEquals(TrayState.State.IDLE, state.state(now - 60_000L, true));
  }

  @Test
  void aBeatingScriptIsRunningOrSearching() {
    state.runStarted("demo.sikuli");
    now += 5_000L;
    assertEquals(TrayState.State.RUNNING, state.state(now, false));
    assertEquals(TrayState.State.SEARCHING, state.state(now, true));
  }

  @Test
  void aSilentScriptTurnsStuckAfterTheThreshold() {
    state.runStarted("demo.sikuli");
    long lastBeat = now;
    now += 29_000L;
    assertEquals(TrayState.State.RUNNING, state.state(lastBeat, false));
    now += 2_000L;
    assertEquals(TrayState.State.STUCK, state.state(lastBeat, false));
    assertEquals(31L, state.silentSeconds(lastBeat));
  }

  @Test
  void aBeatBringsAStuckScriptBack() {
    state.runStarted("demo.sikuli");
    now += 45_000L;
    assertEquals(TrayState.State.STUCK, state.state(now - 45_000L, false));
    assertEquals(TrayState.State.RUNNING, state.state(now, false));
  }

  @Test
  void beatsFromAPreviousRunDoNotCount() {
    state.runStarted("first.sikuli");
    state.runEnded();
    now += 120_000L;
    state.runStarted("second.sikuli");
    assertEquals(TrayState.State.RUNNING, state.state(now - 120_000L, false));
    assertEquals("second.sikuli", state.script());
  }

  @Test
  void theEndOfTheRunIsIdleEvenWhenStuck() {
    state.runStarted("demo.sikuli");
    now += 60_000L;
    assertEquals(TrayState.State.STUCK, state.state(now - 60_000L, false));
    state.runEnded();
    assertEquals(TrayState.State.IDLE, state.state(now - 60_000L, false));
  }
}
