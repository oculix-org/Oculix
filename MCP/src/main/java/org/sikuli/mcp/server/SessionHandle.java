/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.sikuli.mcp.tools.SubstitutionVault;

/**
 * Thin mutable holder around a {@link SessionContext}.
 *
 * <p>The context is replaced on {@code initialize} and updated on every
 * {@code tools/call} that carries fresh {@code _meta.llm}. Keeping a handle
 * rather than passing the context by value lets the dispatcher mutate
 * state transparently for both stdio (single session) and HTTP (many
 * concurrent sessions owned by a {@link SessionStore}).
 *
 * <p>Also owns the per-session {@link SubstitutionVault} used to
 * detokenise PII/secret arguments the LLM operates on as opaque
 * tokens. The vault is created fresh with the handle and dies with
 * it — no cross-session leakage.
 *
 * <p>Access is synchronized because an HTTP transport may expose the same
 * handle to overlapping request threads for the same session id.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class SessionHandle {

  private SessionContext ctx;
  private final SubstitutionVault vault = new SubstitutionVault();

  public SessionHandle() {
    this.ctx = SessionContext.empty();
  }

  public SessionHandle(SessionContext initial) {
    this.ctx = initial;
  }

  public synchronized SessionContext get() {
    return ctx;
  }

  public synchronized void set(SessionContext next) {
    this.ctx = next;
  }

  /** Session-scoped PII/secret detokeniser. Never null. */
  public SubstitutionVault vault() {
    return vault;
  }
}
