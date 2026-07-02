/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

/**
 * Security posture for the remote (SSH / VNC) launch path.
 *
 * <p>Deliberately mirrors {@code org.sikuli.mcp.tools.ToolRegistry.Mode} —
 * same env-var shape, same parsing, same default-safe stance — so a future
 * unification under a single {@code OCULIX_SECURITY_MODE} is a rename, not a
 * refactor. It is intentionally <em>separate</em> from the MCP mode: the two
 * surfaces have different threat models (MCP = who may command the tool; remote
 * = where credentials live), and a user hardening one should never silently
 * change the other.
 *
 * <dl>
 *   <dt>{@code confidential} (default)</dt>
 *   <dd>SSH password handed to {@code sshpass} via the {@code SSHPASS} env var
 *       ({@code -e}), never on the command line. Unknown SSH host keys are a
 *       hard failure — the operator must add them deliberately.</dd>
 *   <dt>{@code yolo}</dt>
 *   <dd>Opt-in, the name is the consent. Password on the command line
 *       ({@code -p}, visible in process telemetry) and unknown host keys are
 *       auto-trusted. For single-user, dedicated targets on controlled networks
 *       (e.g. a POS automation farm) where the operator accepts the trade-off.
 *       Never use on a shared host or anywhere command lines are collected.</dd>
 * </dl>
 *
 * <p>The chosen mode is logged so a security review can see it was a deliberate,
 * bounded, named choice rather than an accident.
 */
public enum RemoteMode {
  CONFIDENTIAL,
  YOLO;

  /** Resolve from {@code OCULIX_REMOTE_MODE}; defaults to {@link #CONFIDENTIAL}. */
  public static RemoteMode fromEnv() {
    String raw = System.getenv("OCULIX_REMOTE_MODE");
    if (raw == null || raw.isBlank()) {
      return CONFIDENTIAL;
    }
    switch (raw.trim().toLowerCase()) {
      case "yolo":
      case "openbar":
      case "open":
        return YOLO;
      case "confidential":
      case "safe":
        return CONFIDENTIAL;
      default:
        throw new IllegalArgumentException(
            "Unknown OCULIX_REMOTE_MODE=" + raw + " (expected 'confidential' or 'yolo')");
    }
  }

  /**
   * Like {@link #fromEnv()} but never throws — a typo'd value (e.g. {@code ylo})
   * degrades to the SAFE default rather than crashing a GUI/background caller or,
   * worse, silently opening the gate. Use this on every non-CLI path (Recorder
   * preflight, VNC launch). A security knob must fail closed AND fail quietly.
   */
  public static RemoteMode fromEnvOrDefault() {
    try {
      return fromEnv();
    } catch (RuntimeException e) {
      org.sikuli.basics.Debug.error("RemoteMode: %s — defaulting to CONFIDENTIAL", e.getMessage());
      return CONFIDENTIAL;
    }
  }

  public boolean isYolo() {
    return this == YOLO;
  }
}
