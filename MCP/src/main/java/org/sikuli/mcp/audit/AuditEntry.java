/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import org.json.JSONObject;

/**
 * Immutable representation of a single audit-trail entry.
 *
 * <p>An entry captures exactly one MCP tool call (or a special event such as
 * {@code rotation_end} / {@code rotation_begin} / {@code clock_regression}).
 *
 * <p>Serialisation format is canonical JSON: sorted keys, no whitespace,
 * UTF-8. The canonical form is what gets hashed to produce
 * {@code entry_hash} and what is signed to produce {@code signature}.
 */
public final class AuditEntry {

  public enum Type {
    TOOL_CALL,
    ROTATION_END,
    ROTATION_BEGIN,
    CLOCK_REGRESSION,
    RECOVERY_GAP
  }

  public final Type type;
  public final String tsUtc;         // ISO-8601 microsecond precision, UTC
  public final long seq;             // per-journal-file counter, strictly monotonic
  public final String sessionId;
  public final JSONObject client;    // { name, version } or null
  public final JSONObject llm;       // { backend, user_id } with nulls allowed
  public final String tool;          // null for non-TOOL_CALL entries
  public final JSONObject args;      // null for non-TOOL_CALL entries
  public final String resultSha256;  // null for non-TOOL_CALL entries
  public final JSONObject extra;     // for rotation markers, clock_regression, recovery_gap
  public final String prevHash;      // hex
  public final String entryHash;     // hex, computed after core fields
  public final String signature;     // hex, Ed25519 over entryHash bytes

  public AuditEntry(Builder b) {
    this.type = b.type;
    this.tsUtc = b.tsUtc;
    this.seq = b.seq;
    this.sessionId = b.sessionId;
    this.client = b.client;
    this.llm = b.llm;
    this.tool = b.tool;
    this.args = b.args;
    this.resultSha256 = b.resultSha256;
    this.extra = b.extra;
    this.prevHash = b.prevHash;
    this.entryHash = b.entryHash;
    this.signature = b.signature;
  }

  /**
   * Produce the canonical form that gets hashed + signed.
   * Everything except entry_hash and signature is included.
   */
  public JSONObject toCanonicalJson() {
    JSONObject o = new JSONObject();
    o.put("type", type.name().toLowerCase());
    o.put("ts_utc", tsUtc);
    o.put("seq", seq);
    o.put("session_id", sessionId == null ? JSONObject.NULL : sessionId);
    o.put("client", client == null ? JSONObject.NULL : client);
    o.put("llm", llm == null ? JSONObject.NULL : llm);
    o.put("tool", tool == null ? JSONObject.NULL : tool);
    o.put("args", args == null ? JSONObject.NULL : args);
    o.put("result_sha256", resultSha256 == null ? JSONObject.NULL : resultSha256);
    o.put("extra", extra == null ? JSONObject.NULL : extra);
    o.put("prev_hash", prevHash == null ? JSONObject.NULL : prevHash);
    return o;
  }

  /**
   * Full JSONL-ready form including entry_hash and signature.
   */
  public JSONObject toJson() {
    JSONObject o = toCanonicalJson();
    o.put("entry_hash", entryHash);
    o.put("signature", signature);
    return o;
  }

  public String toJsonLine() {
    return toJson().toString();
  }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    Type type = Type.TOOL_CALL;
    String tsUtc;
    long seq;
    String sessionId;
    JSONObject client;
    JSONObject llm;
    String tool;
    JSONObject args;
    String resultSha256;
    JSONObject extra;
    String prevHash;
    String entryHash;
    String signature;

    public Builder type(Type t)                  { this.type = t; return this; }
    public Builder tsUtc(String ts)              { this.tsUtc = ts; return this; }
    public Builder seq(long s)                   { this.seq = s; return this; }
    public Builder sessionId(String s)           { this.sessionId = s; return this; }
    public Builder client(JSONObject c)          { this.client = c; return this; }
    public Builder llm(JSONObject l)             { this.llm = l; return this; }
    public Builder tool(String t)                { this.tool = t; return this; }
    public Builder args(JSONObject a)            { this.args = a; return this; }
    public Builder resultSha256(String h)        { this.resultSha256 = h; return this; }
    public Builder extra(JSONObject e)           { this.extra = e; return this; }
    public Builder prevHash(String h)            { this.prevHash = h; return this; }
    public Builder entryHash(String h)           { this.entryHash = h; return this; }
    public Builder signature(String s)           { this.signature = s; return this; }

    public AuditEntry build() { return new AuditEntry(this); }
  }
}
