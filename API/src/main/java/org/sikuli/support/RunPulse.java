/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The pulse of the running script. The API beats it on every search, the
 * IDE reads it to tell a script that is working from one that is wedged.
 */
public final class RunPulse {

  private static volatile long lastBeat = System.currentTimeMillis();
  private static final AtomicInteger searching = new AtomicInteger();

  private RunPulse() {
  }

  /** Records that the script is alive right now. */
  public static void beat() {
    lastBeat = System.currentTimeMillis();
  }

  /** The time of the last beat, in milliseconds since the epoch. */
  public static long lastBeat() {
    return lastBeat;
  }

  /** Marks the start of a search loop; the beat is also recorded. */
  public static void searchStarted() {
    searching.incrementAndGet();
    beat();
  }

  /** Marks the end of a search loop; the beat is also recorded. */
  public static void searchEnded() {
    searching.updateAndGet(n -> Math.max(0, n - 1));
    beat();
  }

  /** True while at least one search loop is running. */
  public static boolean isSearching() {
    return searching.get() > 0;
  }
}
