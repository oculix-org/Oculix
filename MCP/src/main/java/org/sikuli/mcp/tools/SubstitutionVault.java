/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Session-scoped ephemeral tokenisation vault for PII / secret values
 * that the LLM must never see in plain form.
 *
 * <p>Threat model. Consider a data-driven scenario where a test harness
 * knows the real value of a form field (an email, IBAN, credit-card
 * number, one-time password). If the harness passes that value into the
 * LLM prompt so the agent can "type it into the login form", the value
 * is logged, cached, and potentially exposed by every part of the LLM
 * pipeline (context stores, tool-use traces, provider-side logging).
 * The value should never enter the LLM in the first place.
 *
 * <p>The vault flips the exposure model: the harness calls {@link #wrap}
 * once per real value, gets back an opaque token ({@code info1},
 * {@code info2}, ...), and only the token is injected into the prompt.
 * The LLM manipulates tokens. When the LLM asks OculiX to type
 * {@code info1}, the dispatcher resolves it back to the real value at
 * the last moment, just before the keystroke.
 *
 * <p>Scope. The vault is <b>session-scoped</b> — one instance per
 * {@link org.sikuli.mcp.server.SessionHandle}. A global vault would
 * leak values across concurrent HTTP sessions in the same process; a
 * per-request vault would defeat the purpose. Nothing is ever
 * persisted to disk: the map lives entirely in the process heap and
 * dies with the session.
 *
 * <p>Journal contract. The journal must log the token, never the
 * resolved value. {@link #wrap} deduplicates so identical values share
 * a token — the auditor can still correlate identical secrets across
 * entries without ever reading them.
 *
 * <p>Where the wrap happens. The wrap must be performed by the
 * component that prepares the LLM context (the data-driven test
 * harness, the scenario runner). The MCP server is downstream of the
 * LLM in the request path — it cannot hide from the model a value the
 * model has already spoken. See the {@code vault/wrap} JSON-RPC method
 * in {@code McpDispatcher} for the endpoint clients call before
 * building their prompt.
 *
 * <p>Thread-safety: all methods are {@code synchronized}. The
 * wrap counter is an {@link AtomicInteger} for read-mostly reporting.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
public final class SubstitutionVault {

  /** Token shape: literal {@code info} followed by one or more digits. */
  public static final Pattern TOKEN_PATTERN = Pattern.compile("info\\d+");

  private final Map<String, String> tokenToValue = new HashMap<>();
  private final Map<String, String> valueToToken = new HashMap<>();
  private final AtomicInteger counter = new AtomicInteger(0);

  /**
   * Register a real value and return the opaque token that the LLM
   * will see instead. Idempotent: calling {@code wrap} on a value that
   * was already wrapped returns the previously-issued token. No
   * re-issue, no collision.
   *
   * @param realValue the value the LLM must never see in plain form
   * @return the token ({@code info1}, {@code info2}, ...)
   * @throws NullPointerException if {@code realValue} is {@code null}
   */
  public synchronized String wrap(String realValue) {
    if (realValue == null) {
      throw new NullPointerException("wrap: realValue must not be null");
    }
    String existing = valueToToken.get(realValue);
    if (existing != null) return existing;
    String token = "info" + counter.incrementAndGet();
    tokenToValue.put(token, realValue);
    valueToToken.put(realValue, token);
    return token;
  }

  /**
   * Resolve a token back to its real value. If the input is not a
   * registered token (an ordinary {@code "Next"} button label, a
   * literal filename, whatever), it is returned unchanged. This
   * matters for the dispatcher's transparent-resolve hook: it walks
   * every {@code type_text}/{@code find_text}/{@code click_text}
   * argument and passes each string through {@code resolve}; only
   * strings that were wrapped are substituted.
   */
  public synchronized String resolve(String tokenOrLiteral) {
    if (tokenOrLiteral == null) return null;
    String v = tokenToValue.get(tokenOrLiteral);
    return v == null ? tokenOrLiteral : v;
  }

  /**
   * True iff {@code s} looks like a token AND is registered. A string
   * that merely matches the token pattern but was never wrapped is
   * treated as literal text.
   */
  public synchronized boolean isKnownToken(String s) {
    return s != null && tokenToValue.containsKey(s);
  }

  /** Number of distinct values currently tokenised. Read-mostly, for reporting. */
  public int size() {
    return counter.get();
  }
}
