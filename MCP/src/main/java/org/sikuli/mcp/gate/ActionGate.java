/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.gate;

import org.json.JSONObject;

/**
 * Indirection for per-tool-call authorization.
 *
 * <p>In V1, every tool call passes through an {@link ActionGate} but the
 * default implementation {@link AutoApproveGate} returns {@code APPROVED}
 * for everything. This keeps the runtime behavior unchanged while reserving
 * the integration surface for V2.
 *
 * <p>In V2, a different implementation can queue destructive actions
 * (click, type, key_combo) for out-of-band human approval, block until a
 * verdict arrives (or timeout), and return {@code DENIED} when the
 * operator rejects the action.
 *
 * <p>Implementations must be thread-safe — a single server instance can
 * dispatch concurrent tool calls.
 */
public interface ActionGate {

  GateDecision decide(String toolName, JSONObject args);
}
