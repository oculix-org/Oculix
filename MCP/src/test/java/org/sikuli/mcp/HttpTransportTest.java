/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.gate.AutoApproveGate;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.SessionStore;
import org.sikuli.mcp.tools.Tool;
import org.sikuli.mcp.tools.ToolRegistry;
import org.sikuli.mcp.transport.BearerAuth;
import org.sikuli.mcp.transport.HttpTransport;
import org.sikuli.mcp.transport.KeyRing;
import org.sikuli.mcp.transport.TokenIssuer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the two-layer HTTP auth plus signed session tokens.
 */
class HttpTransportTest {

  private static final class EchoTool implements Tool {
    @Override public String name() { return "echo"; }
    @Override public String description() { return "echo"; }
    @Override public JSONObject inputSchema() {
      return new JSONObject().put("type", "object");
    }
    @Override public JSONObject call(JSONObject args) {
      return Tool.textResult(args.toString());
    }
  }

  private static final class Server implements AutoCloseable {
    final HttpTransport http;
    final JournalWriter journal;
    final KeyRing ring;
    final String url;

    Server(Path dir, String clientTokenRaw) throws Exception {
      KeyManager keys = KeyManager.loadOrInit(dir);
      this.journal = new JournalWriter(dir.resolve("journal"), keys, 100, 60_000);
      ToolRegistry r = new ToolRegistry();
      r.register(new EchoTool());
      McpDispatcher d = new McpDispatcher(r, new AutoApproveGate(), journal);
      this.ring = KeyRing.inMemory();
      TokenIssuer issuer = new TokenIssuer(ring);
      BearerAuth.StaticToken client = (clientTokenRaw == null)
          ? null : new BearerAuth.StaticToken(clientTokenRaw);
      this.http = new HttpTransport(d, new SessionStore(), issuer, client,
          "127.0.0.1", 0,
          HttpTransport.DEFAULT_MAX_REQUEST_BYTES,
          HttpTransport.DEFAULT_TOKEN_TTL_SECONDS,
          () -> {}); // no System.exit in tests
      http.start();
      this.url = "http://127.0.0.1:" + http.boundPort() + HttpTransport.PATH;
    }

    @Override
    public void close() throws Exception {
      http.close();
      journal.close();
    }
  }

  private static HttpClient client() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  private static HttpRequest.Builder req(String url) {
    return HttpRequest.newBuilder().uri(URI.create(url))
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json");
  }

  // ── initialize: client token branch ──

  @Test
  void initializeWithoutClientTokenWorksWhenDisabled(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, null)) {
      HttpResponse<String> resp = client().send(
          req(s.url).POST(HttpRequest.BodyPublishers.ofString(initBody())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, resp.statusCode());
    }
  }

  @Test
  void initializeRequiresClientTokenWhenEnabled(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, "PAT-xyz")) {
      assertEquals(401, postInit(s.url, null).statusCode());
      assertEquals(401, postInit(s.url, "Bearer wrong").statusCode());
      assertEquals(200, postInit(s.url, "Bearer PAT-xyz").statusCode());
    }
  }

  // ── Session token ──

  @Test
  void initializeReturnsSignedBearerStartingWithOcx(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, null)) {
      HttpResponse<String> resp = postInit(s.url, null);
      assertEquals(200, resp.statusCode());
      JSONObject meta = new JSONObject(resp.body())
          .getJSONObject("result").getJSONObject("_meta");
      String bearer = meta.getString("bearer");
      assertTrue(bearer.startsWith("ocx."), "Bearer must be in ocx.* format: " + bearer);
      assertEquals(4, bearer.split("\\.").length);
      assertTrue(meta.has("expires_at"));
    }
  }

  @Test
  void subsequentCallNeedsBearerAndSessionHeader(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, null)) {
      HttpResponse<String> init = postInit(s.url, null);
      String sid = init.headers().firstValue(HttpTransport.SESSION_HEADER).orElseThrow();
      String bearer = new JSONObject(init.body())
          .getJSONObject("result").getJSONObject("_meta").getString("bearer");

      // Missing session header → 400
      assertEquals(400, client().send(
          req(s.url).header("Authorization", "Bearer " + bearer)
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());

      // Session header does not match the bearer's embedded sid → 401
      // (mismatch check fires before the "unknown session" check; this is
      // intentional defense-in-depth — a leaked bearer can't be "moved" to
      // a different session id.)
      assertEquals(401, client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, "bogus")
              .header("Authorization", "Bearer " + bearer)
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());

      // No bearer → 401
      assertEquals(401, client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());

      // Wrong bearer (structurally malformed) → 401
      assertEquals(401, client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .header("Authorization", "Bearer not.a.token")
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());

      // Valid bearer → 200
      assertEquals(200, client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .header("Authorization", "Bearer " + bearer)
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());
    }
  }

  @Test
  void tokenFromOneSessionDoesNotAuthorizeAnother(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, null)) {
      // Open two sessions.
      HttpResponse<String> initA = postInit(s.url, null);
      String sidA = initA.headers().firstValue(HttpTransport.SESSION_HEADER).orElseThrow();
      String bearerA = new JSONObject(initA.body())
          .getJSONObject("result").getJSONObject("_meta").getString("bearer");

      HttpResponse<String> initB = postInit(s.url, null);
      String sidB = initB.headers().firstValue(HttpTransport.SESSION_HEADER).orElseThrow();
      assertNotEquals(sidA, sidB);

      // Try to use A's bearer with B's session id.
      HttpResponse<String> crossed = client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sidB)
              .header("Authorization", "Bearer " + bearerA)
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(401, crossed.statusCode(),
          "Cross-session bearer replay must be rejected");
    }
  }

  @Test
  void deleteRevokesSessionServerSide(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, null)) {
      HttpResponse<String> init = postInit(s.url, null);
      String sid = init.headers().firstValue(HttpTransport.SESSION_HEADER).orElseThrow();
      String bearer = new JSONObject(init.body())
          .getJSONObject("result").getJSONObject("_meta").getString("bearer");

      // DELETE with bearer → 204
      assertEquals(204, client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .header("Authorization", "Bearer " + bearer)
              .DELETE().build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());

      // Same (still crypto-valid) bearer after DELETE → 404 (session gone)
      assertEquals(404, client().send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .header("Authorization", "Bearer " + bearer)
              .POST(HttpRequest.BodyPublishers.ofString(pingBody())).build(),
          HttpResponse.BodyHandlers.ofString()).statusCode());
    }
  }

  @Test
  void oversizedPayloadIs413(@TempDir Path dir) throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    try (JournalWriter journal = new JournalWriter(dir.resolve("journal"),
        keys, 100, 60_000)) {
      McpDispatcher d = new McpDispatcher(new ToolRegistry(),
          new AutoApproveGate(), journal);
      TokenIssuer issuer = new TokenIssuer(KeyRing.inMemory());
      HttpTransport http = new HttpTransport(d, new SessionStore(), issuer, null,
          "127.0.0.1", 0, 1024L,
          HttpTransport.DEFAULT_TOKEN_TTL_SECONDS, () -> {});
      http.start();
      try {
        String url = "http://127.0.0.1:" + http.boundPort() + HttpTransport.PATH;
        String huge = "a".repeat(2048);
        HttpResponse<String> resp = client().send(
            req(url).POST(HttpRequest.BodyPublishers.ofString(huge)).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(413, resp.statusCode());
      } finally {
        http.close();
      }
    }
  }

  @Test
  void fullHandshakeListAndCall(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir, "PAT-xyz")) {
      HttpClient c = client();
      HttpResponse<String> init = c.send(
          req(s.url).header("Authorization", "Bearer PAT-xyz")
              .POST(HttpRequest.BodyPublishers.ofString(initBody())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, init.statusCode());
      String sid = init.headers().firstValue(HttpTransport.SESSION_HEADER).orElseThrow();
      String bearer = new JSONObject(init.body())
          .getJSONObject("result").getJSONObject("_meta").getString("bearer");

      HttpResponse<String> list = c.send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .header("Authorization", "Bearer " + bearer)
              .POST(HttpRequest.BodyPublishers.ofString(
                  "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, list.statusCode());

      HttpResponse<String> call = c.send(
          req(s.url).header(HttpTransport.SESSION_HEADER, sid)
              .header("Authorization", "Bearer " + bearer)
              .POST(HttpRequest.BodyPublishers.ofString(
                  "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                      + "\"params\":{\"name\":\"echo\",\"arguments\":{\"x\":1}}}")).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, call.statusCode());
      assertFalse(new JSONObject(call.body())
          .getJSONObject("result").getBoolean("isError"));
    }
  }

  // ── Helpers ──

  private static HttpResponse<String> postInit(String url, String authHeader)
      throws Exception {
    HttpRequest.Builder b = req(url)
        .POST(HttpRequest.BodyPublishers.ofString(initBody()));
    if (authHeader != null) b.header("Authorization", authHeader);
    return client().send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String initBody() {
    return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
        + "\"params\":{\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
  }

  private static String pingBody() {
    return "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}";
  }
}
