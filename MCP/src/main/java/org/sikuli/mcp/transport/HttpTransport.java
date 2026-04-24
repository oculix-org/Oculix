/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.json.JSONObject;
import org.sikuli.mcp.server.JsonRpc;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.SessionHandle;
import org.sikuli.mcp.server.SessionStore;
import org.sikuli.mcp.server.SessionStore.IssuedSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Streamable HTTP transport for MCP, backed by Undertow.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /mcp} — one JSON-RPC in, one JSON response out.
 *       Notifications return {@code 202}.</li>
 *   <li>{@code GET /mcp} — placeholder SSE greeting then close. Gated by
 *       a valid session token.</li>
 *   <li>{@code DELETE /mcp} — session teardown. Revokes the server-side
 *       record so any future request carrying the same token is rejected.</li>
 * </ul>
 *
 * <h3>Auth model (defense in depth)</h3>
 *
 * <p><b>Layer 1 — client credential</b> (optional). A pre-shared
 * {@link BearerAuth.StaticToken} that gates {@code initialize}. Absent →
 * anyone on the bound interface may initialize (fine on loopback).
 *
 * <p><b>Layer 2 — session token</b> (mandatory). Minted on every
 * successful {@code initialize} via {@link TokenIssuer}. Format:
 * {@code ocx.<kid>.<b64url(payload)>.<b64url(hmac)>}. The payload carries
 * {@code sid}, a 256-bit CSPRNG nonce, {@code iat} and a short {@code exp}.
 *
 * <p>Verification on every subsequent request:
 * <ol>
 *   <li>Token structure + prefix match.</li>
 *   <li>HMAC-SHA256 valid for the token's {@code kid}
 *       (constant-time via {@link java.security.MessageDigest#isEqual}).</li>
 *   <li>Payload {@code aud}, {@code exp} ok.</li>
 *   <li>{@code Mcp-Session-Id} header matches token's {@code sid}.</li>
 *   <li>Server-side {@link SessionStore} still has the session and its
 *       nonce matches the token's nonce (constant-time).</li>
 * </ol>
 *
 * <p>This combination defeats replay after session close, key rotation
 * leaves already-issued tokens valid until they expire, and a stolen
 * token cannot be "moved" to a different session id.
 *
 * <h3>TLS</h3>
 *
 * <p>{@link TlsPolicy#assertSafe(String)} is called at bind time to
 * refuse plain HTTP on non-loopback interfaces unless the operator
 * has explicitly opted into "TLS terminated upstream".
 */
public final class HttpTransport implements AutoCloseable {

  public static final String PATH = "/mcp";
  public static final String SESSION_HEADER = "Mcp-Session-Id";
  private static final HttpString SESSION_HTTP_STR = new HttpString(SESSION_HEADER);

  public static final long DEFAULT_MAX_REQUEST_BYTES = 8L * 1024 * 1024;
  /** Default session token TTL, 30 minutes. Refreshed by re-initialize. */
  public static final long DEFAULT_TOKEN_TTL_SECONDS = 30 * 60L;

  private final McpDispatcher dispatcher;
  private final SessionStore sessions;
  private final TokenIssuer issuer;
  private final BearerAuth.StaticToken clientToken; // nullable
  private final String host;
  private final int port;
  private final long maxRequestBytes;
  private final long tokenTtlSeconds;
  private final Runnable onAuditFailure;

  private Undertow server;

  public HttpTransport(McpDispatcher dispatcher, SessionStore sessions,
                       TokenIssuer issuer, BearerAuth.StaticToken clientToken,
                       String host, int port) {
    this(dispatcher, sessions, issuer, clientToken, host, port,
        DEFAULT_MAX_REQUEST_BYTES, DEFAULT_TOKEN_TTL_SECONDS,
        () -> System.exit(2));
  }

  public HttpTransport(McpDispatcher dispatcher, SessionStore sessions,
                       TokenIssuer issuer, BearerAuth.StaticToken clientToken,
                       String host, int port,
                       long maxRequestBytes, long tokenTtlSeconds,
                       Runnable onAuditFailure) {
    this.dispatcher = dispatcher;
    this.sessions = sessions;
    this.issuer = issuer;
    this.clientToken = clientToken;
    this.host = host;
    this.port = port;
    this.maxRequestBytes = maxRequestBytes;
    this.tokenTtlSeconds = tokenTtlSeconds;
    this.onAuditFailure = onAuditFailure;
  }

  public synchronized void start() {
    if (server != null) return;
    TlsPolicy.assertSafe(host);
    HttpHandler handler = this::handle;
    server = Undertow.builder()
        .addHttpListener(port, host)
        .setHandler(handler)
        .build();
    server.start();
  }

  public synchronized int boundPort() {
    if (server == null) return -1;
    return ((java.net.InetSocketAddress)
        server.getListenerInfo().get(0).getAddress()).getPort();
  }

  @Override
  public synchronized void close() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  // ── Handler ──

  private void handle(HttpServerExchange ex) {
    try {
      handleInternal(ex);
    } catch (Throwable t) {
      System.err.println("[oculix-mcp] handler error: " + t);
      t.printStackTrace(System.err);
      if (!ex.isResponseStarted()) {
        ex.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
        ex.getResponseSender().send("{\"error\":\"internal\"}");
      }
    }
  }

  private void handleInternal(HttpServerExchange ex) throws Exception {
    if (!PATH.equals(ex.getRequestPath())) {
      ex.setStatusCode(StatusCodes.NOT_FOUND);
      return;
    }
    if (ex.isInIoThread()) {
      ex.dispatch(this::handle);
      return;
    }

    HttpString method = ex.getRequestMethod();
    if (Methods.POST.equals(method)) {
      handlePost(ex);
    } else if (Methods.GET.equals(method)) {
      handleGet(ex);
    } else if (Methods.DELETE.equals(method)) {
      handleDelete(ex);
    } else {
      ex.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
      ex.getResponseHeaders().put(new HttpString("Allow"), "POST, GET, DELETE");
    }
  }

  private void handlePost(HttpServerExchange ex) throws IOException {
    ex.startBlocking();
    String body;
    try {
      body = readAll(ex.getInputStream(), maxRequestBytes);
    } catch (PayloadTooLargeException e) {
      writeJson(ex, StatusCodes.REQUEST_ENTITY_TOO_LARGE,
          JsonRpc.error(JSONObject.NULL, JsonRpc.INVALID_REQUEST,
              "Request body exceeds " + maxRequestBytes + " bytes"));
      return;
    }

    JSONObject req;
    try {
      req = new JSONObject(body);
    } catch (Exception e) {
      writeJson(ex, StatusCodes.BAD_REQUEST,
          JsonRpc.error(JSONObject.NULL, JsonRpc.PARSE_ERROR,
              "Malformed JSON: " + e.getMessage()));
      return;
    }

    HeaderMap headers = ex.getRequestHeaders();
    String authHeader = firstHeader(headers, "Authorization");
    String rpcMethod = req.optString("method", "");
    String inboundSession = firstHeader(headers, SESSION_HEADER);

    IssuedSession issued;
    String mintedToken = null;

    if ("initialize".equals(rpcMethod)) {
      // Layer 1: client credential required iff configured.
      if (clientToken != null && !clientToken.accepts(authHeader)) {
        denyAuth(ex, "invalid client credential on initialize");
        return;
      }
      // Mint a fresh session + signed session token. No trust is carried
      // over from a prior Mcp-Session-Id; re-initialize to refresh.
      String sessionId = UUID.randomUUID().toString();
      TokenIssuer.Minted m = issuer.mint(sessionId, tokenTtlSeconds);
      mintedToken = m.token;
      issued = sessions.issue(sessionId, m.claims.nonce, m.claims.exp,
          new SessionHandle());
    } else {
      // Layer 2: signed session token required for every non-initialize call.
      if (inboundSession == null) {
        writeJson(ex, StatusCodes.BAD_REQUEST,
            JsonRpc.error(req.opt("id"), JsonRpc.INVALID_REQUEST,
                "Missing " + SESSION_HEADER + " header; call initialize first"));
        return;
      }
      String presented = BearerAuth.extractBearer(authHeader);
      if (presented == null) { denyAuth(ex, "missing bearer token"); return; }

      TokenIssuer.Result v = issuer.verify(presented);
      if (!(v instanceof TokenIssuer.Claims)) {
        denyAuth(ex, "token rejected: " + ((TokenIssuer.Rejection) v).name());
        return;
      }
      TokenIssuer.Claims claims = (TokenIssuer.Claims) v;

      if (!inboundSession.equals(claims.sid)) {
        denyAuth(ex, "token sid does not match " + SESSION_HEADER);
        return;
      }
      issued = sessions.get(claims.sid);
      if (issued == null) {
        writeJson(ex, StatusCodes.NOT_FOUND,
            JsonRpc.error(req.opt("id"), JsonRpc.INVALID_REQUEST,
                "Unknown session id — initialize first"));
        return;
      }
      if (!sessions.nonceMatches(claims.sid, claims.nonce)) {
        denyAuth(ex, "session revoked or token stale");
        return;
      }
    }

    JSONObject resp;
    try {
      resp = dispatcher.dispatch(req, issued.handle);
    } catch (McpDispatcher.AuditFailure audit) {
      System.err.println("[oculix-mcp] FATAL: " + audit.getMessage()
          + ": " + audit.getCause());
      ex.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
      ex.getResponseSender().send("{\"error\":\"audit write failed\"}");
      try { Thread.sleep(50); } catch (InterruptedException ignored) {}
      onAuditFailure.run();
      return;
    }

    ex.getResponseHeaders().put(SESSION_HTTP_STR, issued.sessionId);

    if (resp == null) {
      ex.setStatusCode(StatusCodes.ACCEPTED);
      return;
    }

    if (mintedToken != null) {
      // Attach the freshly-minted session token to the initialize result.
      JSONObject result = resp.optJSONObject("result");
      if (result != null) {
        JSONObject meta = result.optJSONObject("_meta");
        if (meta == null) { meta = new JSONObject(); result.put("_meta", meta); }
        meta.put("bearer", mintedToken);
        meta.put("expires_at", issued.expEpochSec);
      }
    }

    writeJson(ex, StatusCodes.OK, resp);
  }

  private void handleGet(HttpServerExchange ex) {
    // Placeholder SSE greet-then-close, gated by a valid session token.
    if (!verifySessionOrDeny(ex)) return;
    ex.getResponseHeaders().put(new HttpString("Content-Type"), "text/event-stream");
    ex.getResponseHeaders().put(new HttpString("Cache-Control"), "no-cache");
    ex.getResponseHeaders().put(new HttpString("Connection"), "keep-alive");
    ex.getResponseSender().send(": oculix-mcp ready\n\n");
  }

  private void handleDelete(HttpServerExchange ex) {
    String sessionId = firstHeader(ex.getRequestHeaders(), SESSION_HEADER);
    if (sessionId == null) { ex.setStatusCode(StatusCodes.BAD_REQUEST); return; }
    if (!verifySessionOrDeny(ex)) return;
    sessions.remove(sessionId);
    ex.setStatusCode(StatusCodes.NO_CONTENT);
  }

  /** Returns true iff the current request carries a valid session token. */
  private boolean verifySessionOrDeny(HttpServerExchange ex) {
    String sessionId = firstHeader(ex.getRequestHeaders(), SESSION_HEADER);
    String authHeader = firstHeader(ex.getRequestHeaders(), "Authorization");
    if (sessionId == null) {
      ex.setStatusCode(StatusCodes.BAD_REQUEST);
      return false;
    }
    IssuedSession s = sessions.get(sessionId);
    if (s == null) {
      ex.setStatusCode(StatusCodes.NOT_FOUND);
      return false;
    }
    String presented = BearerAuth.extractBearer(authHeader);
    if (presented == null) { denyAuth(ex, "missing bearer token"); return false; }

    TokenIssuer.Result v = issuer.verify(presented);
    if (!(v instanceof TokenIssuer.Claims)) {
      denyAuth(ex, "token rejected: " + ((TokenIssuer.Rejection) v).name());
      return false;
    }
    TokenIssuer.Claims claims = (TokenIssuer.Claims) v;
    if (!sessionId.equals(claims.sid)
        || !sessions.nonceMatches(sessionId, claims.nonce)) {
      denyAuth(ex, "token does not match session state");
      return false;
    }
    return true;
  }

  // ── Helpers ──

  private static void denyAuth(HttpServerExchange ex, String reason) {
    ex.setStatusCode(StatusCodes.UNAUTHORIZED);
    ex.getResponseHeaders().put(new HttpString("WWW-Authenticate"),
        "Bearer realm=\"oculix-mcp\"");
    ex.getResponseHeaders().put(new HttpString("Content-Type"), "application/json");
    ex.getResponseSender().send("{\"error\":\"" + reason + "\"}",
        StandardCharsets.UTF_8);
  }

  private static void writeJson(HttpServerExchange ex, int status, JSONObject body) {
    ex.setStatusCode(status);
    ex.getResponseHeaders().put(new HttpString("Content-Type"), "application/json");
    ex.getResponseSender().send(body.toString(), StandardCharsets.UTF_8);
  }

  private static String firstHeader(HeaderMap headers, String name) {
    var values = headers.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  private static String readAll(InputStream in, long maxBytes)
      throws IOException, PayloadTooLargeException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    long total = 0;
    int n;
    while ((n = in.read(buf)) >= 0) {
      total += n;
      if (total > maxBytes) throw new PayloadTooLargeException();
      out.write(buf, 0, n);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  private static final class PayloadTooLargeException extends Exception {}
}
