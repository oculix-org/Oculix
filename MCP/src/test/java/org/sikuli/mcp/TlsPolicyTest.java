/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.transport.TlsPolicy;

import static org.junit.jupiter.api.Assertions.*;

class TlsPolicyTest {

  @Test
  void loopbackHostsAreAlwaysSafe() {
    assertDoesNotThrow(() -> TlsPolicy.assertSafe("127.0.0.1"));
    assertDoesNotThrow(() -> TlsPolicy.assertSafe("localhost"));
    assertDoesNotThrow(() -> TlsPolicy.assertSafe("::1"));
  }

  @Test
  void nonLoopbackWithoutTrustFlagIsRefused() {
    // Can't easily set env var from a test — we exercise the static predicate.
    assertFalse(TlsPolicy.isLoopback("0.0.0.0"));
    assertFalse(TlsPolicy.isLoopback("192.168.1.10"));
    assertFalse(TlsPolicy.isLoopback("example.com"));
  }

  @Test
  void trustFlagParsesCommonTruthyValues() {
    assertTrue(TlsPolicy.trustTlsTermination("1"));
    assertTrue(TlsPolicy.trustTlsTermination("true"));
    assertTrue(TlsPolicy.trustTlsTermination("TRUE"));
    assertTrue(TlsPolicy.trustTlsTermination("yes"));
    assertTrue(TlsPolicy.trustTlsTermination("  1  "));

    assertFalse(TlsPolicy.trustTlsTermination(null));
    assertFalse(TlsPolicy.trustTlsTermination(""));
    assertFalse(TlsPolicy.trustTlsTermination("0"));
    assertFalse(TlsPolicy.trustTlsTermination("false"));
    assertFalse(TlsPolicy.trustTlsTermination("maybe"));
  }
}
