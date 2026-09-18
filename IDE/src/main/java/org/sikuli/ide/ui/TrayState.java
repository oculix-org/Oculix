/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import java.util.function.LongSupplier;

/**
 * What the tray icon should show, computed from three facts: is a script
 * running, when did it last beat, is it inside a search loop. No AWT here,
 * so the rules can be tested with a clock of their own.
 */
public final class TrayState {

  public enum State { IDLE, RUNNING, SEARCHING, STUCK }

  private final LongSupplier clock;
  private final long stuckMillis;
  private volatile boolean running;
  private volatile long startedAt;
  private volatile String script = "";

  /**
   * @param clock       the current time in milliseconds
   * @param stuckMillis how long a running script may stay silent before it counts as stuck
   */
  public TrayState(LongSupplier clock, long stuckMillis) {
    this.clock = clock;
    this.stuckMillis = stuckMillis;
  }

  public void runStarted(String script) {
    this.script = script == null ? "" : script;
    this.startedAt = clock.getAsLong();
    this.running = true;
  }

  public void runEnded() {
    this.running = false;
  }

  public boolean isRunning() {
    return running;
  }

  public String script() {
    return script;
  }

  /**
   * The state for the given pulse. A beat older than the start of the run
   * belongs to a previous run and does not count.
   */
  public State state(long lastBeat, boolean searching) {
    if (!running) {
      return State.IDLE;
    }
    if (silentSeconds(lastBeat) * 1000L > stuckMillis) {
      return State.STUCK;
    }
    return searching ? State.SEARCHING : State.RUNNING;
  }

  /** Seconds since the last beat of the current run. */
  public long silentSeconds(long lastBeat) {
    long beat = Math.max(lastBeat, startedAt);
    return Math.max(0, (clock.getAsLong() - beat) / 1000L);
  }
}
