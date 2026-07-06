/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.gate.ActionGate;
import org.sikuli.mcp.gate.AutoApproveGate;
import org.sikuli.mcp.gate.GateDecision;
import org.sikuli.mcp.server.JsonRpc;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.SessionHandle;
import org.sikuli.mcp.tools.Tool;
import org.sikuli.mcp.tools.ToolRegistry;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */

class McpDispatcherTest {

  /** Minimal fake tool — pure in-memory, no screen dependency. */
  private static final class EchoTool implements Tool {
    final AtomicInteger calls = new AtomicInteger();
    @Override public String name() { return "echo"; }
    @Override public String description() { return "echo"; }
    @Override public JSONObject inputSchema() {
      return new JSONObject().put("type", "object");
    }
    @Override public JSONObject call(JSONObject args) {
      calls.incrementAndGet();
      return Tool.textResult(args.toString());
    }
  }

  private static McpDispatcher mkDispatcher(Path dir, ToolRegistry r, ActionGate gate)
      throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    JournalWriter journal = new JournalWriter(
        dir.resolve("journal"), keys, 100, 60_000);
    return new McpDispatcher(r, gate, journal);
  }

  @Test
  void initializeReturnsServerInfo(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());

    JSONObject req = new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize")
        .put("params", new JSONObject()
            .put("clientInfo", new JSONObject()
                .put("name", "inspector").put("version", "1.0")));

    JSONObject resp = d.dispatch(req, new SessionHandle());
    assertNotNull(resp);
    JSONObject result = resp.getJSONObject("result");
    assertEquals(McpDispatcher.PROTOCOL_VERSION, result.getString("protocolVersion"));
    assertEquals(McpDispatcher.SERVER_NAME,
        result.getJSONObject("serverInfo").getString("name"));
  }

  @Test
  void toolsListReturnsRegisteredTools(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();

    // MCP protocol: initialize must precede tools/list.
    d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize"), h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2).put("method", "tools/list"), h);

    JSONArray tools = resp.getJSONObject("result").getJSONArray("tools");
    assertEquals(1, tools.length());
    assertEquals("echo", tools.getJSONObject(0).getString("name"));
  }

  @Test
  void toolsCallBeforeInitializeIsRejected(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());

    JSONObject req = new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
        .put("params", new JSONObject().put("name", "echo"));
    JSONObject resp = d.dispatch(req, new SessionHandle());

    assertTrue(resp.has("error"), () -> "Expected error, got: " + resp);
    assertEquals(JsonRpc.INVALID_REQUEST,
        resp.getJSONObject("error").getInt("code"));
  }

  @Test
  void toolsListBeforeInitializeIsRejected(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());

    JSONObject req = new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", "tools/list");
    JSONObject resp = d.dispatch(req, new SessionHandle());

    assertTrue(resp.has("error"));
    assertEquals(JsonRpc.INVALID_REQUEST,
        resp.getJSONObject("error").getInt("code"));
  }

  @Test
  void pingBeforeInitializeIsAllowed(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    JSONObject req = new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", "ping");
    JSONObject resp = d.dispatch(req, new SessionHandle());
    assertTrue(resp.has("result"), () -> "Ping should be allowed pre-initialize: " + resp);
  }

  @Test
  void protocolVersionNegotiation_echoesSupported() {
    assertEquals(McpDispatcher.PROTOCOL_VERSION,
        McpDispatcher.negotiateProtocolVersion(McpDispatcher.PROTOCOL_VERSION));
  }

  @Test
  void protocolVersionNegotiation_fallsBackOnUnknown() {
    // Unknown proposal → we respond with our canonical version and log a warn.
    assertEquals(McpDispatcher.PROTOCOL_VERSION,
        McpDispatcher.negotiateProtocolVersion("9999-12-31"));
  }

  @Test
  void protocolVersionNegotiation_handlesMissingProposal() {
    assertEquals(McpDispatcher.PROTOCOL_VERSION,
        McpDispatcher.negotiateProtocolVersion(null));
    assertEquals(McpDispatcher.PROTOCOL_VERSION,
        McpDispatcher.negotiateProtocolVersion(""));
  }

  @Test
  void serverVersionSourcedDynamically() {
    // In test mode (no jar), we hit the dev sentinel. In a packaged jar,
    // pom.properties provides the real Maven version. Both are non-blank
    // and never the hard-coded 3.0.1 that used to lie.
    assertNotNull(McpDispatcher.SERVER_VERSION);
    assertFalse(McpDispatcher.SERVER_VERSION.isBlank());
    assertNotEquals("3.0.1", McpDispatcher.SERVER_VERSION,
        "SERVER_VERSION must not be the hard-coded 3.0.1 sentinel any more");
  }

  @Test
  void toolsCallExecutesAndAudits(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    EchoTool echo = new EchoTool();
    r.register(echo);
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();

    d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
        .put("params", new JSONObject()
            .put("clientInfo", new JSONObject().put("name", "t"))), h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 3)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject().put("x", 42))), h);

    assertFalse(resp.getJSONObject("result").getBoolean("isError"));
    assertEquals(1, echo.calls.get());
  }

  @Test
  void deniedByGateReturnsErrorResultAndStillAudits(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    EchoTool echo = new EchoTool();
    r.register(echo);
    ActionGate denying = (name, args) -> GateDecision.denied("policy");
    McpDispatcher d = mkDispatcher(dir, r, denying);
    SessionHandle h = new SessionHandle();

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize")
        .put("params", new JSONObject()), h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject())), h);

    assertTrue(resp.getJSONObject("result").getBoolean("isError"));
    assertEquals(0, echo.calls.get(), "Tool must not execute when denied");
  }

  @Test
  void unknownMethodYieldsMethodNotFound(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 7).put("method", "bogus"),
        new SessionHandle());
    assertEquals(JsonRpc.METHOD_NOT_FOUND,
        resp.getJSONObject("error").getInt("code"));
  }

  @Test
  void notificationsReturnNullResponse(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0")
        .put("method", "notifications/initialized"), new SessionHandle());
    assertNull(resp);
  }

  @Test
  void perRequestLlmDoesNotRegenerateSessionId(@TempDir Path dir) throws Exception {
    // End-to-end guard for the SessionContext bug fix: firing two tools/call
    // each with _meta.llm must keep the same session_id.
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize")
        .put("params", new JSONObject()
            .put("clientInfo", new JSONObject().put("name", "t"))), h);
    String sid1 = h.get().sessionId;

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 2)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject())
            .put("_meta", new JSONObject()
                .put("llm", new JSONObject().put("backend", "claude-opus")))),
        h);
    String sid2 = h.get().sessionId;

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 3)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject())
            .put("_meta", new JSONObject()
                .put("llm", new JSONObject().put("backend", "gpt-5")))),
        h);
    String sid3 = h.get().sessionId;

    assertEquals(sid1, sid2);
    assertEquals(sid2, sid3);
  }

  // ── SubstitutionVault: vault/wrap + transparent resolve ──

  @Test
  void vaultWrapReturnsToken(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    SessionHandle h = new SessionHandle();
    initialize(d, h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "alice@example.com")), h);

    assertEquals("info1", resp.getJSONObject("result").getString("token"));
  }

  @Test
  void vaultWrapBeforeInitializeIsRejected(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "alice@example.com")),
        new SessionHandle());
    assertEquals(JsonRpc.INVALID_REQUEST,
        resp.getJSONObject("error").getInt("code"));
  }

  @Test
  void vaultWrapDedupesIdenticalValues(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    SessionHandle h = new SessionHandle();
    initialize(d, h);

    String first = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "same-value")), h)
        .getJSONObject("result").getString("token");
    String second = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 3)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "same-value")), h)
        .getJSONObject("result").getString("token");

    assertEquals(first, second);
  }

  @Test
  void toolsCallResolvesTokenIntoRealValueBeforeToolSeesIt(@TempDir Path dir)
      throws Exception {
    // The whole point: the tool receives the real value, but the LLM
    // only ever saw the token. EchoTool echoes its args, so if the
    // result contains the real value, the resolve did fire.
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();
    initialize(d, h);

    String token = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "IBAN-FR7612345")), h)
        .getJSONObject("result").getString("token");

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 3)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject().put("text", token))), h);
    String echoedResult = resp.getJSONObject("result").toString();
    assertTrue(echoedResult.contains("IBAN-FR7612345"),
        () -> "tool must have seen the real value: " + echoedResult);
    assertFalse(echoedResult.contains(token),
        () -> "tool must not see the token: " + echoedResult);
  }

  @Test
  void toolsCallResolvesTokensNestedInObjectsAndArrays(@TempDir Path dir)
      throws Exception {
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();
    initialize(d, h);

    String t1 = d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "REAL-EMAIL")), h)
        .getJSONObject("result").getString("token");
    String t2 = d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 3)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "REAL-IBAN")), h)
        .getJSONObject("result").getString("token");

    JSONObject nestedArgs = new JSONObject()
        .put("user", new JSONObject().put("email", t1))
        .put("accounts", new JSONArray().put(t2));

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 4)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", nestedArgs)), h);
    String out = resp.getJSONObject("result").toString();
    assertTrue(out.contains("REAL-EMAIL"), () -> "nested object resolved: " + out);
    assertTrue(out.contains("REAL-IBAN"), () -> "array element resolved: " + out);
    assertFalse(out.contains(t1));
    assertFalse(out.contains(t2));
  }

  @Test
  void toolsCallLeavesUnknownStringsUntouched(@TempDir Path dir) throws Exception {
    // A tool typing "Suivant" or a raw filename must not be affected
    // by the resolver, even after some tokens have been registered.
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();
    initialize(d, h);
    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "secret")), h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 3)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject()
                .put("text", "Suivant")
                .put("fake_token", "info99"))), h);
    String out = resp.getJSONObject("result").toString();
    assertTrue(out.contains("Suivant"));
    assertTrue(out.contains("info99"),
        "unregistered token-shaped string must pass through");
  }

  @Test
  void vaultIsSessionScopedNotGlobal(@TempDir Path dir) throws Exception {
    // Two independent SessionHandles must not share wrapped values.
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    SessionHandle a = new SessionHandle();
    SessionHandle b = new SessionHandle();
    initialize(d, a);
    initialize(d, b);

    String ta = d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "session-A-secret")), a)
        .getJSONObject("result").getString("token");
    String tb = d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 2)
        .put("method", "vault/wrap")
        .put("params", new JSONObject().put("value", "session-B-secret")), b)
        .getJSONObject("result").getString("token");

    // Both got info1 in their own vault — proof they are separate.
    assertEquals("info1", ta);
    assertEquals("info1", tb);
    // Resolving ta in a's vault returns A's secret; b's vault returns
    // B's secret. The tokens do not collide because they never live
    // in the same map.
    assertNotEquals(a.vault().resolve(ta), b.vault().resolve(tb));
  }

  private static void initialize(McpDispatcher d, SessionHandle h) {
    d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize"), h);
  }
}
