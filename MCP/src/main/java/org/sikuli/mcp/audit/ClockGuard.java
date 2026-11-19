/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;

/**
 * Combines wall-clock UTC timestamps with a monotonic reference to
 * detect clock regressions (NTP sync going backward, VM pause,
 * operator tampering).
 *
 * <p>Both clocks are injectable so tests can drive them independently:
 * the wall clock via {@link Clock}, the monotonic reference via a
 * {@link LongSupplier} of nanoseconds. Production callers pass
 * {@link Clock#systemUTC()} and {@link System#nanoTime}; tests pass
 * a {@code Clock.fixed}/{@code Clock.offset} and a synthetic
 * {@code AtomicLong::get}.
 *
 * <p>On regression, the caller is expected to emit a
 * {@code clock_regression} audit entry before the next real tool call
 * entry. This {@code ClockGuard} flags the event but never throws — the
 * audit trail must keep running.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class ClockGuard {

  private static final DateTimeFormatter ISO_MICRO =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

  private final Clock wallClock;
  private final LongSupplier monotonicNano;

  private long refNano;
  private Instant refWall;

  /** Production default: system UTC wall clock plus {@link System#nanoTime}. */
  public ClockGuard() {
    this(Clock.systemUTC(), System::nanoTime);
  }

  /**
   * @param wallClock       source of wall-clock instants (injectable for tests)
   * @param monotonicNano   source of monotonic nanoseconds (injectable for tests)
   */
  public ClockGuard(Clock wallClock, LongSupplier monotonicNano) {
    this.wallClock = wallClock;
    this.monotonicNano = monotonicNano;
    this.refNano = monotonicNano.getAsLong();
    this.refWall = wallClock.instant();
  }

  /**
   * Compute the current UTC wall-clock timestamp with microsecond precision.
   */
  public String nowIso() {
    return ISO_MICRO.format(wallClock.instant());
  }

  /**
   * Check whether wall clock has regressed relative to the monotonic reference.
   * A regression is any delta where the wall clock advanced less than the
   * monotonic clock, plus a generous tolerance margin (50 ms) to absorb
   * normal jitter.
   */
  public boolean hasRegressed() {
    long nowNano = monotonicNano.getAsLong();
    Instant nowWall = wallClock.instant();

    long nanoDeltaMillis = (nowNano - refNano) / 1_000_000L;
    long wallDeltaMillis = nowWall.toEpochMilli() - refWall.toEpochMilli();

    // Wall clock should advance at least (monotonic - tolerance)
    return wallDeltaMillis + 50L < nanoDeltaMillis;
  }

  /**
   * Reset the reference point after a regression has been logged.
   */
  public void acknowledge() {
    this.refNano = monotonicNano.getAsLong();
    this.refWall = wallClock.instant();
  }

  public Instant refWall() { return refWall; }
}
