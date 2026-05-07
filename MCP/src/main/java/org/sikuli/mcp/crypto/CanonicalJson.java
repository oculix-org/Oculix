/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.crypto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic ("canonical") JSON serialisation used for audit-trail hashing.
 *
 * <p>{@code org.json.JSONObject} stores entries in a {@code HashMap}, so its
 * {@link JSONObject#toString()} output depends on insertion order and hash
 * collisions — which means two logically identical objects can serialise to
 * different byte sequences, breaking any hash-based integrity check.
 *
 * <p>This class produces a stable byte-level representation with:
 * <ul>
 *   <li>object keys sorted in strict lexicographic order</li>
 *   <li>no whitespace</li>
 *   <li>numbers serialised via {@link JSONObject#numberToString(Number)}</li>
 *   <li>arrays preserving element order</li>
 * </ul>
 */
public final class CanonicalJson {

  private CanonicalJson() {}

  public static String serialize(JSONObject o) {
    StringBuilder sb = new StringBuilder();
    writeObject(o, sb);
    return sb.toString();
  }

  private static void writeValue(Object v, StringBuilder sb) {
    if (v == null || v == JSONObject.NULL) {
      sb.append("null");
    } else if (v instanceof JSONObject) {
      writeObject((JSONObject) v, sb);
    } else if (v instanceof JSONArray) {
      writeArray((JSONArray) v, sb);
    } else if (v instanceof String) {
      sb.append(JSONObject.quote((String) v));
    } else if (v instanceof Boolean) {
      sb.append(((Boolean) v) ? "true" : "false");
    } else if (v instanceof Number) {
      sb.append(JSONObject.numberToString((Number) v));
    } else {
      // Fallback: coerce to string and quote
      sb.append(JSONObject.quote(v.toString()));
    }
  }

  private static void writeObject(JSONObject o, StringBuilder sb) {
    sb.append('{');
    List<String> keys = new ArrayList<>(o.keySet());
    Collections.sort(keys);
    boolean first = true;
    for (String k : keys) {
      if (!first) sb.append(',');
      first = false;
      sb.append(JSONObject.quote(k)).append(':');
      writeValue(o.opt(k), sb);
    }
    sb.append('}');
  }

  private static void writeArray(JSONArray a, StringBuilder sb) {
    sb.append('[');
    for (int i = 0; i < a.length(); i++) {
      if (i > 0) sb.append(',');
      writeValue(a.opt(i), sb);
    }
    sb.append(']');
  }
}
