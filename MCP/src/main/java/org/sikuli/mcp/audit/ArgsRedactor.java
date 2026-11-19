/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.mcp.crypto.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Redact sensitive values in a tool-call {@code args} object before it
 * is written to the append-only, forever-signed audit journal.
 *
 * <p>The journal is a Ed25519-signed record of every action. Once an
 * entry is on disk it stays on disk in that exact form — a plaintext
 * password embedded in a {@code type_text} call would live in the
 * journal for the lifetime of the deployment. That is a compliance
 * problem (GDPR, PCI, banking secrecy) and a straightforward
 * information-disclosure risk on backup exfiltration.
 *
 * <p>The redactor walks the {@link JSONObject} recursively, replacing
 * the value of any key whose name matches a sensitive pattern (case-
 * insensitive substring match against {@link #SENSITIVE_KEY_PATTERNS})
 * with a short SHA-256 fingerprint of the original value. The
 * fingerprint preserves auditability: two entries that carried the
 * same secret will show the same fingerprint, so an investigator can
 * still correlate them without ever reading the plaintext.
 *
 * <p>Nested objects and arrays are traversed. Non-sensitive keys pass
 * through unchanged. The original {@link JSONObject} is not mutated —
 * a fresh tree is returned.
 *
 * <p>The predicate is deliberately conservative (loose substring match)
 * so that {@code apiKey}, {@code api_key}, {@code x-api-key},
 * {@code my_secret_thing} all land in the redacted bucket. False
 * positives are cheap (a key called {@code "authorized"} would be
 * redacted); false negatives are what actually leak PII, so we err on
 * the side of over-redacting.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
public final class ArgsRedactor {

  /**
   * Substrings (case-insensitive) that mark a key as sensitive. If a key
   * name contains any of these, its value is replaced with a fingerprint.
   */
  private static final Set<String> SENSITIVE_KEY_PATTERNS = Set.of(
      "password", "passwd",
      "secret",
      "api_key", "apikey", "api-key",
      "token",
      "auth",
      "credential",
      "private_key", "privatekey",
      "pin",
      "ssn",
      "iban",
      "cvv",
      "creditcard", "credit_card", "credit-card",
      "session_id"
  );

  private static final String REDACTED_MARKER = "***REDACTED";

  private ArgsRedactor() {}

  /**
   * Return a redacted deep copy of {@code original}. Sensitive-keyed
   * values (see class Javadoc) become a fingerprint string; every
   * other value is copied unchanged. Nested objects and arrays are
   * traversed.
   *
   * @return a fresh {@link JSONObject}, or {@code null} if the input
   *         was {@code null}
   */
  public static JSONObject redact(JSONObject original) {
    if (original == null) return null;
    JSONObject out = new JSONObject();
    for (String key : original.keySet()) {
      Object val = original.opt(key);
      out.put(key, redactValue(key, val));
    }
    return out;
  }

  private static Object redactValue(String key, Object val) {
    if (isSensitive(key)) {
      return redactedFingerprint(val);
    }
    if (val instanceof JSONObject) {
      return redact((JSONObject) val);
    }
    if (val instanceof JSONArray) {
      return redactArray((JSONArray) val);
    }
    return val;
  }

  private static JSONArray redactArray(JSONArray in) {
    JSONArray out = new JSONArray();
    for (int i = 0; i < in.length(); i++) {
      Object v = in.opt(i);
      if (v instanceof JSONObject) {
        out.put(redact((JSONObject) v));
      } else if (v instanceof JSONArray) {
        out.put(redactArray((JSONArray) v));
      } else {
        out.put(v);
      }
    }
    return out;
  }

  /**
   * Case-insensitive substring match. A key like {@code "x-api-key"}
   * or {@code "MyPassword123"} both trip.
   */
  static boolean isSensitive(String key) {
    if (key == null) return false;
    String lower = key.toLowerCase();
    for (String pat : SENSITIVE_KEY_PATTERNS) {
      if (lower.contains(pat)) return true;
    }
    return false;
  }

  /**
   * Replace a value with a fingerprint string. {@code null} is spelled
   * out so the redacted entry stays informative — an operator scanning
   * the journal can see that the field was present but null.
   */
  private static String redactedFingerprint(Object val) {
    if (val == null || val == JSONObject.NULL) {
      return REDACTED_MARKER + " (null)***";
    }
    String s = val.toString();
    if (s.isEmpty()) {
      return REDACTED_MARKER + " (empty)***";
    }
    String hash = Hashing.sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    return REDACTED_MARKER + " sha256=" + hash.substring(0, 12) + "***";
  }
}
