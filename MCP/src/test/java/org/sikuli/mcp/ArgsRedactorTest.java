/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.sikuli.mcp.audit.ArgsRedactor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
class ArgsRedactorTest {

  @Test
  void nullInputReturnsNull() {
    assertNull(ArgsRedactor.redact(null));
  }

  @Test
  void nonSensitiveKeysPassThroughUnchanged() {
    JSONObject in = new JSONObject()
        .put("reference_path", "/tmp/x.png")
        .put("similarity", 0.85)
        .put("timeout_ms", 5000);
    JSONObject out = ArgsRedactor.redact(in);
    assertEquals("/tmp/x.png", out.getString("reference_path"));
    assertEquals(0.85, out.getDouble("similarity"), 1e-9);
    assertEquals(5000, out.getInt("timeout_ms"));
  }

  @Test
  void passwordIsRedactedWithFingerprint() {
    JSONObject in = new JSONObject().put("password", "s3cret-p@ss");
    JSONObject out = ArgsRedactor.redact(in);
    String v = out.getString("password");
    assertTrue(v.startsWith("***REDACTED"), () -> "unexpected: " + v);
    assertTrue(v.contains("sha256="), () -> "no fingerprint: " + v);
    assertFalse(v.contains("s3cret"), "cleartext must not leak");
  }

  @Test
  void identicalSecretsProduceIdenticalFingerprints() {
    JSONObject a = new JSONObject().put("secret", "hunter2");
    JSONObject b = new JSONObject().put("secret", "hunter2");
    assertEquals(
        ArgsRedactor.redact(a).getString("secret"),
        ArgsRedactor.redact(b).getString("secret"),
        "auditors must still be able to correlate identical secrets");
  }

  @Test
  void differentSecretsProduceDifferentFingerprints() {
    JSONObject a = new JSONObject().put("secret", "aaa");
    JSONObject b = new JSONObject().put("secret", "bbb");
    assertNotEquals(
        ArgsRedactor.redact(a).getString("secret"),
        ArgsRedactor.redact(b).getString("secret"));
  }

  @Test
  void allSensitivePatternsAreCovered() {
    String[] sensitiveKeys = {
        "password", "passwd", "secret",
        "api_key", "apiKey", "x-api-key",
        "token", "auth", "authorization",
        "credential", "credentials",
        "private_key", "privateKey",
        "pin", "ssn",
        "iban", "cvv",
        "creditCard", "credit_card",
        "session_id",
    };
    for (String key : sensitiveKeys) {
      JSONObject in = new JSONObject().put(key, "SENSITIVE_VALUE");
      String out = ArgsRedactor.redact(in).getString(key);
      assertTrue(out.startsWith("***REDACTED"),
          () -> "key '" + key + "' was not redacted: " + out);
      assertFalse(out.contains("SENSITIVE_VALUE"),
          () -> "cleartext leaked for key '" + key + "'");
    }
  }

  @Test
  void caseInsensitiveMatch() {
    JSONObject in = new JSONObject()
        .put("Password", "p1")
        .put("PASSWORD", "p2")
        .put("MyApiKey", "k1");
    JSONObject out = ArgsRedactor.redact(in);
    assertTrue(out.getString("Password").startsWith("***REDACTED"));
    assertTrue(out.getString("PASSWORD").startsWith("***REDACTED"));
    assertTrue(out.getString("MyApiKey").startsWith("***REDACTED"));
  }

  @Test
  void nestedObjectsAreRedactedRecursively() {
    JSONObject in = new JSONObject()
        .put("form", new JSONObject()
            .put("username", "alice")
            .put("password", "s3cret")
            .put("nested", new JSONObject().put("secret", "deep")));
    JSONObject out = ArgsRedactor.redact(in);
    JSONObject form = out.getJSONObject("form");
    assertEquals("alice", form.getString("username"));
    assertTrue(form.getString("password").startsWith("***REDACTED"));
    assertTrue(form.getJSONObject("nested").getString("secret")
        .startsWith("***REDACTED"));
  }

  @Test
  void arraysOfObjectsAreRedactedRecursively() {
    JSONArray users = new JSONArray()
        .put(new JSONObject().put("name", "alice").put("token", "t1"))
        .put(new JSONObject().put("name", "bob").put("token", "t2"));
    JSONObject in = new JSONObject().put("users", users);
    JSONObject out = ArgsRedactor.redact(in);
    JSONArray outUsers = out.getJSONArray("users");
    assertEquals("alice", outUsers.getJSONObject(0).getString("name"));
    assertTrue(outUsers.getJSONObject(0).getString("token").startsWith("***REDACTED"));
    assertTrue(outUsers.getJSONObject(1).getString("token").startsWith("***REDACTED"));
  }

  @Test
  void nullValueIsSpelledOut() {
    JSONObject in = new JSONObject().put("password", JSONObject.NULL);
    String out = ArgsRedactor.redact(in).getString("password");
    assertTrue(out.contains("null"), () -> "expected null marker, got: " + out);
  }

  @Test
  void emptyValueIsSpelledOut() {
    JSONObject in = new JSONObject().put("password", "");
    String out = ArgsRedactor.redact(in).getString("password");
    assertTrue(out.contains("empty"), () -> "expected empty marker, got: " + out);
  }

  @Test
  void originalJsonObjectIsNotMutated() {
    JSONObject in = new JSONObject().put("password", "s3cret");
    ArgsRedactor.redact(in);
    assertEquals("s3cret", in.getString("password"),
        "input JSON must remain untouched — redaction returns a fresh tree");
  }
}
