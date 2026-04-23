/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import java.util.Set;

/**
 * Deployment policy for the HTTP transport.
 *
 * <p>The server refuses to bind plain HTTP on any non-loopback interface,
 * because tokens and screen-automation payloads in clear over the wire
 * are never acceptable for the threat model this server targets (regulated
 * agent-driven automation on a shared network).
 *
 * <p>The escape hatch is the env var {@code OCULIX_MCP_TRUST_TLS_TERMINATION=1},
 * which operators must set to explicitly acknowledge that <b>they have
 * terminated TLS upstream</b> (reverse proxy, service mesh, WAF). It is an
 * operator attestation; the server cannot verify it. Without it, binding
 * to e.g. {@code 0.0.0.0} will fail fast at startup.
 *
 * <p>For a future in-process TLS listener (Undertow {@code addHttpsListener})
 * this class is where the policy check will gain a {@code tlsConfigured}
 * clause.
 */
public final class TlsPolicy {

  public static final String TRUST_ENV = "OCULIX_MCP_TRUST_TLS_TERMINATION";

  private static final Set<String> LOOPBACK_HOSTS = Set.of(
      "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost");

  private TlsPolicy() {}

  /** True if the host is a loopback address accepted for plain-HTTP binding. */
  public static boolean isLoopback(String host) {
    return host != null && LOOPBACK_HOSTS.contains(host);
  }

  /** True if the operator has opted into trusting upstream TLS termination. */
  public static boolean trustTlsTermination() {
    return trustTlsTermination(System.getenv(TRUST_ENV));
  }

  /** Testable variant. */
  public static boolean trustTlsTermination(String envValue) {
    if (envValue == null) return false;
    String v = envValue.trim().toLowerCase();
    return v.equals("1") || v.equals("true") || v.equals("yes");
  }

  /**
   * Throw if the host/TLS combination is unacceptable. Intended to be
   * called at startup just before the server binds.
   */
  public static void assertSafe(String host) {
    if (isLoopback(host)) return;
    if (trustTlsTermination()) return;
    throw new IllegalStateException(
        "Refusing to bind " + host + " over plain HTTP.\n"
            + "Either keep --host on loopback (127.0.0.1, ::1, localhost) or,\n"
            + "if you have TLS terminated upstream (nginx / Caddy / service mesh),\n"
            + "set " + TRUST_ENV + "=1 to acknowledge this and restart.");
  }
}
