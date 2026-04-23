/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.gate;

/**
 * Outcome of an {@link ActionGate} evaluation.
 */
public final class GateDecision {

  public enum Verdict { APPROVED, DENIED }

  public final Verdict verdict;
  public final String reason;

  private GateDecision(Verdict verdict, String reason) {
    this.verdict = verdict;
    this.reason = reason;
  }

  public static GateDecision approved() {
    return new GateDecision(Verdict.APPROVED, null);
  }

  public static GateDecision denied(String reason) {
    return new GateDecision(Verdict.DENIED, reason);
  }

  public boolean isApproved() { return verdict == Verdict.APPROVED; }
}
