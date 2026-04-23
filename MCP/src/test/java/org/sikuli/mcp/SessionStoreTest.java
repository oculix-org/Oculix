/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.server.SessionHandle;
import org.sikuli.mcp.server.SessionStore;
import org.sikuli.mcp.server.SessionStore.IssuedSession;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

  @Test
  void issueRecordsTripleWithExpiry() {
    SessionStore s = new SessionStore();
    SessionHandle h = new SessionHandle();
    IssuedSession rec = s.issue("sid", "nonce-abc", 1_700_000_000L, h);
    assertEquals("sid", rec.sessionId);
    assertEquals("nonce-abc", rec.nonce);
    assertEquals(1_700_000_000L, rec.expEpochSec);
    assertSame(h, rec.handle);
    assertEquals(1, s.size());
  }

  @Test
  void getReturnsIssuedOrNull() {
    SessionStore s = new SessionStore();
    IssuedSession rec = s.issue("sid", "n", 1L, new SessionHandle());
    assertSame(rec, s.get("sid"));
    assertNull(s.get("other"));
    assertNull(s.get(null));
  }

  @Test
  void nonceMatchesIsConstantTime() {
    SessionStore s = new SessionStore();
    s.issue("sid", "the-right-nonce", 1L, new SessionHandle());
    assertTrue(s.nonceMatches("sid", "the-right-nonce"));
    assertFalse(s.nonceMatches("sid", "the-wrong-nonce"));
    assertFalse(s.nonceMatches("sid", null));
    assertFalse(s.nonceMatches("unknown-sid", "anything"));
    // Length mismatch must still return cleanly, not throw.
    assertFalse(s.nonceMatches("sid", "short"));
    assertFalse(s.nonceMatches("sid", "a-much-longer-nonce-that-cannot-match"));
  }

  @Test
  void removeEjectsSession() {
    SessionStore s = new SessionStore();
    IssuedSession rec = s.issue("sid", "n", 1L, new SessionHandle());
    assertSame(rec, s.remove("sid"));
    assertNull(s.get("sid"));
    assertNull(s.remove("sid"));
  }

  @Test
  void issueRejectsNullArgs() {
    SessionStore s = new SessionStore();
    SessionHandle h = new SessionHandle();
    assertThrows(NullPointerException.class, () -> s.issue(null, "n", 1L, h));
    assertThrows(NullPointerException.class, () -> s.issue("sid", null, 1L, h));
    assertThrows(NullPointerException.class, () -> s.issue("sid", "n", 1L, null));
  }

  @Test
  void purgeExpiredDropsPastExp() {
    SessionStore s = new SessionStore();
    s.issue("past", "n1", 100L, new SessionHandle());
    s.issue("future", "n2", 10_000L, new SessionHandle());
    int dropped = s.purgeExpired(1000L);
    assertEquals(1, dropped);
    assertNull(s.get("past"));
    assertNotNull(s.get("future"));
  }
}
