/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.cli;

import org.sikuli.mcp.audit.HighWaterMark;
import org.sikuli.mcp.audit.JournalVerifier;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.gate.AutoApproveGate;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.McpServer;
import org.sikuli.mcp.server.OcrBootstrap;
import org.sikuli.mcp.server.SessionStore;
import org.sikuli.mcp.server.StartupCheck;
import org.sikuli.mcp.tools.ToolRegistry;
import org.sikuli.mcp.transport.BearerAuth;
import org.sikuli.mcp.transport.HttpTransport;
import org.sikuli.mcp.transport.KeyRing;
import org.sikuli.mcp.transport.TokenIssuer;

import java.nio.file.*;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entry point for the oculix-mcp executable jar.
 *
 * <p>Usage:
 * <pre>
 *   java -jar oculix-mcp-server.jar run          # (default) start the MCP server over stdio
 *   java -jar oculix-mcp-server.jar rotate-key   # rotate the Ed25519 audit signing key
 *   java -jar oculix-mcp-server.jar recover      # record an unsigned-gap and start fresh
 *   java -jar oculix-mcp-server.jar verify FILE  # verify a journal file
 *   java -jar oculix-mcp-server.jar --help
 * </pre>
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class Main {

  // Rotation thresholds — can be tuned later via env vars
  private static final long MAX_ENTRIES_PER_FILE = 10_000L;
  private static final long MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L; // 24h

  // Signed anchor file. Lives in oculixDir (next to the key files), not in
  // journalDir: a cosmetic wipe of journalDir alone leaves the HWM behind
  // so the missing tail becomes detectable. A wipe of the whole oculixDir
  // takes the HWM with it and cannot be caught in Phase 1 — documented in
  // the README, external anchor is the Phase 2 answer.
  static final String HWM_ANCHOR_FILENAME = "journal.hwm";

  static Path defaultHwmPath(Path oculixDir) {
    return oculixDir.resolve(HWM_ANCHOR_FILENAME);
  }

  public static void main(String[] rawArgs) throws Exception {
    String[] args = rawArgs == null ? new String[0] : rawArgs;
    String cmd = args.length == 0 ? "run" : args[0];
    String[] rest = args.length <= 1 ? new String[0]
                                     : Arrays.copyOfRange(args, 1, args.length);

    switch (cmd) {
      case "run":                  cmdRun(); break;
      case "serve":                cmdServe(rest); break;
      case "rotate-key":           cmdRotateKey(); break;
      case "rotate-session-key":   cmdRotateSessionKey(); break;
      case "recover":              cmdRecover(); break;
      case "verify":               cmdVerify(rest); break;
      case "--help":
      case "-h":
      case "help":        printUsage(); break;
      default:
        System.err.println("Unknown command: " + cmd);
        printUsage();
        System.exit(1);
    }
  }

  // ── run ──

  private static void cmdRun() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();

    KeyManager keys;
    try {
      keys = StartupCheck.validateAndLoad(oculixDir, journalDir);
    } catch (StartupCheck.StartupException e) {
      System.err.println("[oculix-mcp] Startup refused:\n" + e.getMessage());
      System.exit(3);
      return;
    }

    OcrBootstrap.applyDefaultLanguage();

    HighWaterMark hwm = new HighWaterMark(defaultHwmPath(oculixDir), keys);
    try (JournalWriter journal = new JournalWriter(journalDir, keys,
             MAX_ENTRIES_PER_FILE, MAX_AGE_MILLIS, hwm)) {
      McpServer server = new McpServer(
          ToolRegistry.defaultRegistry(),
          new AutoApproveGate(),
          journal,
          System.in, System.out);
      server.run();
    }
  }

  // ── serve (HTTP) ──

  /**
   * Start the MCP server over HTTP (Streamable HTTP transport).
   *
   * <p>Flags:
   * <ul>
   *   <li>{@code --host HOST} (default {@code 127.0.0.1})</li>
   *   <li>{@code --port PORT} (default {@code 7337}, {@code 0} to let the OS pick)</li>
   * </ul>
   *
   * <p>The bearer token is read from {@code OCULIX_MCP_TOKEN}; if absent,
   * one is generated and printed on startup. Never generate tokens silently
   * in production — set the env var explicitly.
   */
  private static void cmdServe(String[] args) throws Exception {
    String host = "127.0.0.1";
    int port = 7337;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if ("--host".equals(a) && i + 1 < args.length) { host = args[++i]; }
      else if ("--port".equals(a) && i + 1 < args.length) {
        port = Integer.parseInt(args[++i]);
      } else if (a.startsWith("--host=")) { host = a.substring("--host=".length()); }
      else if (a.startsWith("--port=")) {
        port = Integer.parseInt(a.substring("--port=".length()));
      } else {
        System.err.println("Unknown serve flag: " + a);
        System.exit(1);
      }
    }

    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();

    KeyManager keys;
    try {
      keys = StartupCheck.validateAndLoad(oculixDir, journalDir);
    } catch (StartupCheck.StartupException e) {
      System.err.println("[oculix-mcp] Startup refused:\n" + e.getMessage());
      System.exit(3);
      return;
    }

    // Client-token layer (OPTIONAL). Gates `initialize` only. When unset,
    // any caller reaching the bound interface can initialize — fine on
    // loopback, insufficient on a remote-reachable host. Session bearers
    // (layer 2) are always enforced on all subsequent calls; they are
    // minted per-initialize and returned to the client in the response.
    String envClientToken = System.getenv("OCULIX_MCP_TOKEN");
    BearerAuth.StaticToken clientToken = null;
    String clientTokenNote;
    if (envClientToken != null && !envClientToken.isBlank()) {
      clientToken = new BearerAuth.StaticToken(envClientToken);
      clientTokenNote = "enforced via OCULIX_MCP_TOKEN env var";
    } else {
      clientTokenNote = "DISABLED — any caller on "
          + ("127.0.0.1".equals(host) ? "loopback" : host + ":" + port)
          + " can call initialize. Set OCULIX_MCP_TOKEN to require a pre-shared client credential.";
    }

    OcrBootstrap.applyDefaultLanguage();

    ToolRegistry.Mode mode = ToolRegistry.Mode.fromEnv();
    ToolRegistry registry = ToolRegistry.defaultRegistry(mode);

    HighWaterMark hwm = new HighWaterMark(defaultHwmPath(oculixDir), keys);
    try (JournalWriter journal = new JournalWriter(journalDir, keys,
             MAX_ENTRIES_PER_FILE, MAX_AGE_MILLIS, hwm)) {
      McpDispatcher dispatcher = new McpDispatcher(
          registry, new AutoApproveGate(), journal);
      SessionStore sessions = new SessionStore();
      // Session-token keyring. HMAC-SHA256 keyed by kid, rotation-capable.
      KeyRing keyring = KeyRing.loadOrInit(
          oculixDir.resolve("session-hmac-keyring.json"));
      TokenIssuer issuer = new TokenIssuer(keyring);

      // Session purge scheduler. Without this, in an HTTP long-running
      // process every `initialize` adds an entry to the SessionStore's
      // ConcurrentHashMap and only DELETE /mcp removes it. Any client
      // that crashes, kill -9s, or drops the socket leaves an entry
      // behind for good — the RAM grows monotonically. We sweep
      // expired sessions every 5 minutes.
      ScheduledExecutorService purgeExec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "oculix-mcp-session-purge");
        t.setDaemon(true);
        return t;
      });
      purgeExec.scheduleAtFixedRate(() -> {
        try {
          int dropped = sessions.purgeExpired(System.currentTimeMillis() / 1000L);
          if (dropped > 0) {
            System.err.println("[oculix-mcp] purged " + dropped + " expired session(s)");
          }
        } catch (Throwable t) {
          // Never let a purge failure kill the scheduler thread.
          System.err.println("[oculix-mcp] session purge error: " + t);
        }
      }, 5, 5, TimeUnit.MINUTES);

      Set<String> allowedOrigins = parseAllowedOrigins(
          System.getenv("OCULIX_MCP_ALLOWED_ORIGINS"));
      HttpTransport http = new HttpTransport(dispatcher, sessions, issuer,
          clientToken, host, port, allowedOrigins);
      http.start();

      int bound = http.boundPort();
      System.err.println("[oculix-mcp] listening on http://" + host + ":" + bound + "/mcp");
      System.err.println("[oculix-mcp] mode=" + mode.name().toLowerCase()
          + " tools=" + registry.size());
      System.err.println("[oculix-mcp] client token: " + clientTokenNote);
      System.err.println("[oculix-mcp] session tokens: HMAC-SHA256, kid=" + keyring.currentKid()
          + " (" + keyring.size() + " kid(s) in ring)");
      System.err.println("[oculix-mcp] token TTL: " + HttpTransport.DEFAULT_TOKEN_TTL_SECONDS + "s");
      System.err.println("[oculix-mcp] Origin allowlist: "
          + (allowedOrigins.isEmpty()
              ? "empty (any non-null Origin will be refused)"
              : allowedOrigins.toString()));
      System.err.println("[oculix-mcp] session purge: every 5 min");

      // Block the main thread; shutdown on Ctrl-C / SIGTERM.
      Thread shutdown = new Thread(() -> {
        try { http.close(); } catch (Exception ignored) {}
        purgeExec.shutdownNow();
      }, "oculix-mcp-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);

      // Park forever.
      synchronized (http) { http.wait(); }
    }
  }

  /**
   * Parse a comma-separated allowlist of Origin values from an env var.
   * Whitespace around entries is trimmed; empty entries dropped. An empty
   * or null env var yields an empty set — the strictest possible policy
   * (any non-null Origin will be refused by the transport).
   */
  static Set<String> parseAllowedOrigins(String envValue) {
    if (envValue == null || envValue.isBlank()) return Set.of();
    return Arrays.stream(envValue.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  // ── rotate-session-key ──

  /**
   * Rotate the HMAC keyring used to sign session tokens.
   *
   * <p>Generates a fresh kid, makes it current, and keeps the previous kid
   * so that already-issued tokens stay verifiable until they expire or
   * {@code retire} is invoked. Tokens minted from this point on are signed
   * with the new kid.
   *
   * <p>Operational note: key rotation here is independent of the audit-chain
   * Ed25519 rotation handled by {@code rotate-key}. The two keys serve
   * different purposes — signing session auth vs signing audit entries —
   * and follow different cadences.
   */
  private static void cmdRotateSessionKey() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Files.createDirectories(oculixDir);
    Path ringPath = oculixDir.resolve("session-hmac-keyring.json");
    KeyRing ring = KeyRing.loadOrInit(ringPath);
    String old = ring.currentKid();
    String fresh = ring.generate();
    ring.save(ringPath);
    System.out.println("Session-token keyring rotated.");
    System.out.println("  previous kid:  " + old);
    System.out.println("  new kid:       " + fresh);
    System.out.println("  ring size:     " + ring.size());
    System.out.println();
    System.out.println("Tokens minted with previous kids remain verifiable until they expire.");
    System.out.println("To force-expire all outstanding tokens, retire the old kids manually");
    System.out.println("or delete " + ringPath + " to wipe the ring entirely.");
  }

  // ── rotate-key ──

  private static void cmdRotateKey() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();
    Path privKey = oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME);
    Path pubKey  = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);

    if (!Files.exists(privKey) || !Files.exists(pubKey)) {
      System.err.println("No existing key pair to rotate in " + oculixDir);
      System.exit(4);
    }

    KeyManager oldKeys = KeyManager.loadExisting(privKey, pubKey);

    // Generate the new pair in a temp dir first
    Path stagingDir = oculixDir.resolve("staging-" + System.currentTimeMillis());
    KeyManager newKeys = KeyManager.generateAndStore(stagingDir);

    // Anchor lives in oculixDir so it survives the archive/promote below.
    // The rotate() call writes rotation_end + rotation_begin — the HWM
    // hook re-signs the anchor after each writeLine with the outgoing key.
    // We resign it with the incoming key at the end of this method so the
    // next verify sees a chain-anchor pair that agrees on the current
    // public key.
    Path anchorPath = defaultHwmPath(oculixDir);
    HighWaterMark hwmOld = new HighWaterMark(anchorPath, oldKeys);

    // Write rotation_end marker to the current journal, signed by the OLD key
    try (JournalWriter journal = new JournalWriter(journalDir, oldKeys,
             Long.MAX_VALUE, Long.MAX_VALUE, hwmOld)) {
      journal.rotate("manual_rotation", newKeys.publicKeySha256Hex());
    }

    // Archive the old key pair
    Path archiveDir = oculixDir.resolve("archive");
    Files.createDirectories(archiveDir);
    String ts = String.valueOf(System.currentTimeMillis());
    Files.move(privKey, archiveDir.resolve("private-" + ts + ".key"));
    Files.move(pubKey, archiveDir.resolve("public-" + ts + ".key"));

    // Promote the new pair
    Files.move(stagingDir.resolve(KeyManager.PRIVATE_KEY_FILENAME), privKey);
    Files.move(stagingDir.resolve(KeyManager.PUBLIC_KEY_FILENAME), pubKey);
    KeyManager.restrictPrivate(privKey);
    Files.deleteIfExists(stagingDir);

    // Re-sign the anchor under the newly-promoted key. Same four fields
    // as the rotate() call wrote — we are not moving the anchor, just
    // switching whose signature vouches for it. Without this step the
    // next verify would read a HWM signed by the archived key and
    // misread the fresh chain as tampered.
    HighWaterMark.Snapshot afterRotate = hwmOld.load().orElseThrow(() ->
        new IllegalStateException("HWM disappeared between rotate write and re-sign"));
    HighWaterMark hwmNew = new HighWaterMark(anchorPath, KeyManager.loadOrInit(oculixDir));
    hwmNew.reset(afterRotate.lastEntryHash, afterRotate.lastSeq,
        afterRotate.lastFile, afterRotate.lastTsUtc);

    System.out.println("Key rotated successfully.");
    System.out.println("Old key archived to " + archiveDir);
    System.out.println("Anchor re-signed under new key: " + anchorPath);
    System.out.println("New public key SHA-256: " + newKeys.publicKeySha256Hex());
  }

  // ── recover ──

  private static void cmdRecover() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();

    Files.createDirectories(journalDir);

    // Human-readable artefact only — the verifier ignores .txt files and
    // anchors solely on the signed RECOVERY_GAP entry written below. Kept
    // for operators skimming the directory with a file browser.
    String ts = String.valueOf(System.currentTimeMillis());
    Path gap = journalDir.resolve("UNSIGNED_GAP-" + ts + ".txt");
    Files.writeString(gap,
        "Audit chain broken at " + java.time.Instant.now() + ".\n"
            + "Reason: operator-initiated recovery (key lost or chain corruption).\n"
            + "Subsequent entries start a fresh chain under a new Ed25519 key pair.\n"
            + "The verifier anchors on the signed RECOVERY_GAP entry in the next\n"
            + "audit-*.jsonl, not on this file.\n",
        StandardOpenOption.CREATE_NEW);

    // Wipe existing keys if any — recovery means we accept the break
    Path privKey = oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME);
    Path pubKey  = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);
    Files.deleteIfExists(privKey);
    Files.deleteIfExists(pubKey);

    // Generate a fresh pair
    KeyManager fresh = KeyManager.generateAndStore(oculixDir);

    // Anchor + journal writer are both bound to the NEW key. The
    // appendRecoveryGap call below writes the signed break declaration
    // AND re-signs the HWM under the new key via the writer's HWM hook,
    // so the next verify reads a fully-consistent chain and anchor.
    Path anchorPath = defaultHwmPath(oculixDir);
    HighWaterMark hwm = new HighWaterMark(anchorPath, fresh);
    Path recoveryJournal;
    try (JournalWriter writer = new JournalWriter(
        journalDir, fresh, MAX_ENTRIES_PER_FILE, MAX_AGE_MILLIS, hwm)) {
      writer.appendRecoveryGap(
          "operator_recovery",
          java.time.Instant.now().toString(),
          "lost");
      recoveryJournal = writer.currentFile();
    }

    System.out.println("Recovery complete.");
    System.out.println("Signed RECOVERY_GAP written to: " + recoveryJournal);
    System.out.println("Informational artefact:         " + gap);
    System.out.println("Anchor re-signed under new key: " + anchorPath);
    System.out.println("Fresh key pair generated.");
    System.out.println("New public key SHA-256: " + fresh.publicKeySha256Hex());
  }

  // ── verify ──

  /**
   * Verify the audit chain.
   *
   * <p>Default mode (no flags): full chain verification via
   * {@link JournalVerifier#verifyChain}. Every {@code audit-*.jsonl} in
   * the journal directory is checked per-file, cross-file rotation
   * links are resolved by hash lookup (not filename order), file heads
   * are checked to start at genesis or at a resolved
   * {@code rotation_begin}, and the {@link HighWaterMark} anchor is
   * confronted to catch tail truncation.
   *
   * <p>{@code --per-file}: legacy single-file check only. Useful when
   * debugging one file in isolation, or in Phase 1 when some files
   * were signed by an archived key that {@code verify} does not yet
   * load — those would fail signature under the current key but are
   * clean under their original key.
   *
   * <p>Explicit paths after the command switch straight to per-file
   * mode on the named files, whether or not {@code --per-file} is
   * passed.
   *
   * <p>Exit codes (default chain mode): {@code 0} clean, {@code 2}
   * soft warnings (anchor lags queue, empty dir with anchor present),
   * {@code 1} hard fail (tamper, truncation, orphan file, unresolved
   * cross-file link). Precedence is deterministic: {@code 1 > 2 > 0}.
   * Legacy per-file mode exits {@code 1} on any failure.
   */
  private static void cmdVerify(String[] args) throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path pubKeyPath = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);

    boolean perFileMode = false;
    List<String> pathArgs = new ArrayList<>();
    for (String a : args) {
      if ("--per-file".equals(a)) {
        perFileMode = true;
      } else if (a.startsWith("--")) {
        System.err.println("Unknown verify flag: " + a);
        System.exit(1);
        return;
      } else {
        pathArgs.add(a);
      }
    }

    Path journalDir = StartupCheck.defaultJournalDir();
    if (!Files.isDirectory(journalDir) && pathArgs.isEmpty()) {
      System.err.println("No journal directory at " + journalDir);
      System.exit(5);
      return;
    }

    PublicKey pub = JournalVerifier.loadPublicKey(pubKeyPath);

    if (perFileMode || !pathArgs.isEmpty()) {
      runPerFileMode(journalDir, pathArgs, pub);
      return;
    }

    // Default: chain verification with HWM confrontation.
    Path anchorPath = defaultHwmPath(oculixDir);
    JournalVerifier.ChainResult r =
        JournalVerifier.verifyChain(journalDir, pub, anchorPath);

    System.out.println("Chain verification of " + journalDir);
    System.out.println("  files checked:   " + r.filesChecked);
    System.out.println("  entries checked: " + r.entriesChecked);
    System.out.println("  anchor:          " + anchorPath);
    System.out.println();
    for (JournalVerifier.FileVerification fv : r.perFile) {
      String tag = fv.result.ok ? "OK  " : "FAIL";
      System.out.println("  " + tag + "  " + fv.file.getFileName()
          + "  (" + fv.result.entriesChecked + " entries)");
    }
    if (!r.warnings.isEmpty()) {
      System.out.println();
      System.out.println("WARNINGS:");
      for (String w : r.warnings) System.out.println("  ! " + w);
    }
    if (!r.issues.isEmpty()) {
      System.out.println();
      System.out.println("ISSUES:");
      for (String iss : r.issues) System.out.println("  X " + iss);
    }

    System.out.println();
    switch (r.level) {
      case OK:
        System.out.println("VERDICT: OK — chain intact, anchor agrees.");
        System.exit(0);
        break;
      case WARN:
        System.out.println("VERDICT: WARN — soft deviation, chain structurally intact.");
        System.exit(2);
        break;
      case FAIL:
        System.out.println("VERDICT: FAIL — tamper, truncation, or orphan file detected.");
        System.out.println("(Rerun `verify --per-file <file>` on individual files for detail.)");
        System.exit(1);
        break;
    }
  }

  private static void runPerFileMode(Path journalDir, List<String> pathArgs,
                                     PublicKey pub) throws Exception {
    List<Path> files;
    if (pathArgs.isEmpty()) {
      try (Stream<Path> s = Files.list(journalDir)) {
        files = s.filter(p -> p.getFileName().toString().startsWith("audit-")
                           && p.getFileName().toString().endsWith(".jsonl"))
                 .sorted(Comparator.naturalOrder())
                 .collect(Collectors.toList());
      }
    } else {
      files = pathArgs.stream().map(Paths::get).collect(Collectors.toList());
    }

    boolean allOk = true;
    for (Path f : files) {
      JournalVerifier.Result r = JournalVerifier.verify(f, pub);
      if (r.ok) {
        System.out.println("OK  " + f + "  (" + r.entriesChecked + " entries)");
      } else {
        allOk = false;
        System.out.println("FAIL " + f + "  (" + r.entriesChecked + " entries)");
        for (String issue : r.issues) {
          System.out.println("     " + issue);
        }
      }
    }
    if (!allOk) System.exit(1);
  }

  private static void printUsage() {
    System.out.println("oculix-mcp — Auditable visual-control MCP server for regulated environments");
    System.out.println();
    System.out.println("Usage:");
    System.out.println("  oculix-mcp run          Start the MCP server over stdio (default)");
    System.out.println("  oculix-mcp serve        Start the MCP server over HTTP (Streamable HTTP)");
    System.out.println("                          Flags: --host HOST (default 127.0.0.1)");
    System.out.println("                                 --port PORT (default 7337, 0 for auto)");
    System.out.println("                          Env:   OCULIX_MCP_TOKEN  pre-shared client token");
    System.out.println("                                                   (optional, gates initialize)");
    System.out.println("                                 OCULIX_MCP_MODE=open|confidential");
    System.out.println("                                 OCULIX_MCP_VAULT=<path> confidential landing dir");
    System.out.println("                                 OCULIX_MCP_TRUST_TLS_TERMINATION=1");
    System.out.println("                                                   acknowledge upstream TLS");
    System.out.println("                                                   for non-loopback binds");
    System.out.println("  oculix-mcp rotate-key            Rotate the Ed25519 audit signing key");
    System.out.println("  oculix-mcp rotate-session-key    Rotate the HMAC keyring for session tokens");
    System.out.println("  oculix-mcp recover      Record an unsigned gap and start a fresh chain");
    System.out.println("  oculix-mcp verify [--per-file] [FILES...]");
    System.out.println("                          Default: full chain verification with anchor check");
    System.out.println("                          --per-file: legacy single-file mode (opt-in)");
    System.out.println("                          Named FILES imply per-file mode");
    System.out.println("                          Exit codes: 0 clean, 2 warn (soft), 1 fail (hard)");
    System.out.println();
    System.out.println("Config dir: " + StartupCheck.defaultOculixDir());
    System.out.println("Journal dir: " + StartupCheck.defaultJournalDir());
  }
}
