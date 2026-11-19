/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Whether the running script is inside a search loop right now. The API
 * marks the loops, the IDE reads the flag to colour its tray icon.
 */
public final class RunPulse {

  private static final AtomicInteger searching = new AtomicInteger();

  private RunPulse() {
  }

  /** Marks the start of a search loop. */
  public static void searchStarted() {
    searching.incrementAndGet();
  }

  /** Marks the end of a search loop. */
  public static void searchEnded() {
    searching.updateAndGet(n -> Math.max(0, n - 1));
  }

  /** True while at least one search loop is running. */
  public static boolean isSearching() {
    return searching.get() > 0;
  }
}
