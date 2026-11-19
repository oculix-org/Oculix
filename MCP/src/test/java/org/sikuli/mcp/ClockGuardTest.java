/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.audit.ClockGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
class ClockGuardTest {

  /** Wall clock advances in lockstep with monotonic → no regression. */
  @Test
  void steadyClockDoesNotRegress() {
    AtomicLong nano = new AtomicLong(0L);
    MutableClock wall = new MutableClock(Instant.parse("2026-07-06T00:00:00Z"));
    ClockGuard g = new ClockGuard(wall, nano::get);

    // Advance both by 1 second.
    nano.set(1_000_000_000L);
    wall.set(Instant.parse("2026-07-06T00:00:01Z"));

    assertFalse(g.hasRegressed());
  }

  /** Wall clock jumps backward while monotonic advances → regression. */
  @Test
  void wallClockRegressionIsDetected() {
    AtomicLong nano = new AtomicLong(0L);
    MutableClock wall = new MutableClock(Instant.parse("2026-07-06T00:00:00Z"));
    ClockGuard g = new ClockGuard(wall, nano::get);

    // Monotonic advances by 2 seconds, wall clock goes BACK 1 second.
    nano.set(2_000_000_000L);
    wall.set(Instant.parse("2026-07-05T23:59:59Z"));

    assertTrue(g.hasRegressed());
  }

  /** Wall clock stalls while monotonic advances significantly → regression. */
  @Test
  void wallClockStallIsDetected() {
    AtomicLong nano = new AtomicLong(0L);
    MutableClock wall = new MutableClock(Instant.parse("2026-07-06T00:00:00Z"));
    ClockGuard g = new ClockGuard(wall, nano::get);

    // Monotonic advances 5 seconds. Wall clock does not move at all.
    nano.set(5_000_000_000L);

    assertTrue(g.hasRegressed());
  }

  /** Small jitter under the 50ms tolerance stays quiet. */
  @Test
  void smallJitterUnderToleranceDoesNotFireFalseAlarm() {
    AtomicLong nano = new AtomicLong(0L);
    MutableClock wall = new MutableClock(Instant.parse("2026-07-06T00:00:00Z"));
    ClockGuard g = new ClockGuard(wall, nano::get);

    // Monotonic advances 100ms. Wall clock advances 60ms — a 40ms
    // deficit, within the 50ms tolerance.
    nano.set(100_000_000L);
    wall.set(Instant.parse("2026-07-06T00:00:00.060Z"));

    assertFalse(g.hasRegressed(),
        "40ms jitter should not fire a regression alarm");
  }

  /** acknowledge resets the reference so subsequent checks compare from now. */
  @Test
  void acknowledgeResetsReference() {
    AtomicLong nano = new AtomicLong(0L);
    MutableClock wall = new MutableClock(Instant.parse("2026-07-06T00:00:00Z"));
    ClockGuard g = new ClockGuard(wall, nano::get);

    // Trigger a regression.
    nano.set(2_000_000_000L);
    wall.set(Instant.parse("2026-07-05T23:59:58Z"));
    assertTrue(g.hasRegressed());

    // Acknowledge freezes both refs at "now".
    g.acknowledge();
    assertFalse(g.hasRegressed(), "post-acknowledge should be quiet");
  }

  /** nowIso uses the injected wall clock, not System.currentTimeMillis. */
  @Test
  void nowIsoUsesInjectedClock() {
    MutableClock wall = new MutableClock(Instant.parse("2026-07-06T12:34:56.789Z"));
    ClockGuard g = new ClockGuard(wall, () -> 0L);
    String iso = g.nowIso();
    assertTrue(iso.startsWith("2026-07-06T12:34:56"),
        () -> "unexpected iso stamp: " + iso);
    assertTrue(iso.endsWith("Z"));
  }

  /** Default constructor still works (production callers). */
  @Test
  void defaultConstructorWorksAndReadsSystemClock() {
    ClockGuard g = new ClockGuard();
    String iso = g.nowIso();
    assertNotNull(iso);
    assertTrue(iso.length() >= 20, () -> "ISO too short: " + iso);
    assertFalse(g.hasRegressed(),
        "fresh guard on a healthy machine should not report regression");
  }

  /**
   * Minimal mutable {@link Clock} — {@link Clock#fixed} is immutable and
   * would force a rebuild per test step. We keep an instant field and
   * mutate it directly.
   */
  private static final class MutableClock extends Clock {
    private Instant current;
    MutableClock(Instant initial) { this.current = initial; }
    void set(Instant next) { this.current = next; }
    @Override public Instant instant() { return current; }
    @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(java.time.ZoneId zone) { return this; }
  }
}
