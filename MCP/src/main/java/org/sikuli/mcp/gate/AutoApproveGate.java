/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.gate;

import org.json.JSONObject;

/**
 * V1 default gate: approves every tool call without human intervention.
 *
 * <p>This is the expected behavior while no human-in-the-loop workflow
 * is configured. The audit trail remains the sole after-the-fact check
 * on agent behavior.
 */
public final class AutoApproveGate implements ActionGate {

  @Override
  public GateDecision decide(String toolName, JSONObject args) {
    return GateDecision.approved();
  }
}
