/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Combines wall-clock UTC timestamps with a monotonic reference from
 * {@link System#nanoTime()} to detect clock regressions (NTP sync going
 * backward, VM pause, operator tampering).
 *
 * <p>On regression, the caller is expected to emit a {@code clock_regression}
 * audit entry before the next real tool call entry. This {@code ClockGuard}
 * flags the event but never throws — the audit trail must keep running.
 */
public final class ClockGuard {

  private static final DateTimeFormatter ISO_MICRO =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

  private long refNano;
  private Instant refWall;

  public ClockGuard() {
    this.refNano = System.nanoTime();
    this.refWall = Instant.now();
  }

  /**
   * Compute the current UTC wall-clock timestamp with microsecond precision.
   */
  public String nowIso() {
    return ISO_MICRO.format(Instant.now());
  }

  /**
   * Check whether wall clock has regressed relative to the monotonic reference.
   * A regression is any delta where the wall clock advanced less than the
   * monotonic clock, plus a generous tolerance margin (50 ms) to absorb
   * normal jitter.
   */
  public boolean hasRegressed() {
    long nowNano = System.nanoTime();
    Instant nowWall = Instant.now();

    long nanoDeltaMillis = (nowNano - refNano) / 1_000_000L;
    long wallDeltaMillis = nowWall.toEpochMilli() - refWall.toEpochMilli();

    // Wall clock should advance at least (monotonic - tolerance)
    return wallDeltaMillis + 50L < nanoDeltaMillis;
  }

  /**
   * Reset the reference point after a regression has been logged.
   */
  public void acknowledge() {
    this.refNano = System.nanoTime();
    this.refWall = Instant.now();
  }

  public Instant refWall() { return refWall; }
}
