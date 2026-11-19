/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * What the tray icon should show, from what the script thread is doing: is a
 * run in progress, where the thread stands and since when, is it inside a
 * search loop. A thread that keeps the same top frame while runnable or
 * blocked, longer than the threshold, counts as stuck; one that sleeps or
 * waits does not, it is pausing on purpose; one that is gone while the run
 * never reported its end has crashed and counts as stuck at once. No AWT
 * here, so the rules can be tested with a clock of their own.
 */
public final class TrayState {

  public enum State { IDLE, RUNNING, SEARCHING, STUCK }

  private final LongSupplier clock;
  private final long stuckMillis;
  private volatile boolean running;
  private volatile String script = "";
  private String frame;
  private Thread.State threadState;
  private long since;

  /**
   * @param clock       the current time in milliseconds
   * @param stuckMillis how long a runnable or blocked thread may keep the same top frame before it counts as stuck
   */
  public TrayState(LongSupplier clock, long stuckMillis) {
    this.clock = clock;
    this.stuckMillis = stuckMillis;
  }

  public void runStarted(String script) {
    this.script = script == null ? "" : script;
    this.frame = null;
    this.threadState = null;
    this.since = clock.getAsLong();
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
   * Looks at the script thread from outside, which works even when it is
   * frozen in native code, and returns the state that look implies.
   *
   * @param thread    the script thread, null when there is none
   * @param searching whether the API reports a search loop in progress
   */
  public State observe(Thread thread, boolean searching) {
    Thread.State threadState = thread == null ? null : thread.getState();
    String frame = null;
    if (thread != null) {
      StackTraceElement[] stack = thread.getStackTrace();
      if (stack.length > 0) {
        frame = stack[0].toString();
      }
    }
    return state(threadState, frame, searching);
  }

  /**
   * The state for one sample of the script thread.
   *
   * @param threadState the thread's state, null when there is no thread to look at
   * @param frame       the thread's top stack frame as text, null when it has none
   * @param searching   whether the API reports a search loop in progress
   */
  public State state(Thread.State threadState, String frame, boolean searching) {
    if (!running) {
      return State.IDLE;
    }
    if (threadState == null || threadState == Thread.State.TERMINATED) {
      return State.STUCK;
    }
    long now = clock.getAsLong();
    if (!Objects.equals(frame, this.frame) || threadState != this.threadState) {
      this.frame = frame;
      this.threadState = threadState;
      this.since = now;
    }
    boolean canBeStuck = threadState == Thread.State.RUNNABLE || threadState == Thread.State.BLOCKED;
    if (canBeStuck && frame != null && now - since > stuckMillis) {
      return State.STUCK;
    }
    return searching ? State.SEARCHING : State.RUNNING;
  }

  /** Seconds the thread has spent on its current top frame. */
  public long frozenSeconds() {
    return Math.max(0, (clock.getAsLong() - since) / 1000L);
  }

  /** The top frame of the last sample, as text; empty when unknown. */
  public String where() {
    return frame == null ? "" : frame;
  }
}
