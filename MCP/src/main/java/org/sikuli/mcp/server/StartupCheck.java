/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;
import org.sikuli.mcp.audit.JournalVerifier;
import org.sikuli.mcp.crypto.KeyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enforces fail-fast key / journal integrity at server startup.
 *
 * <p>Implements the 5 startup states documented in the V1 spec:
 * <ol>
 *   <li>Key absent, no journal history → init (generate fresh pair, start)</li>
 *   <li>Key absent, journal history exists → REFUSE (operator must run
 *       {@code recover} if the chain is genuinely broken)</li>
 *   <li>Key present but unreadable / corrupted → REFUSE</li>
 *   <li>Key present but inconsistent with last journal entry → REFUSE
 *       ({@code rotate-key} is the only legitimate way to change keys)</li>
 *   <li>Key present and coherent → start normally</li>
 * </ol>
 *
 * <p>Every REFUSE path throws a {@link StartupException} with a message
 * that names the exact recovery command the operator should run.
 */
public final class StartupCheck {

  public static final class StartupException extends RuntimeException {
    public StartupException(String msg) { super(msg); }
  }

  public static KeyManager validateAndLoad(Path oculixDir, Path journalDir) {
    Path privKey = oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME);
    Path pubKey  = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);

    boolean privExists = Files.exists(privKey);
    boolean pubExists  = Files.exists(pubKey);
    boolean historyExists = journalHistoryExists(journalDir);

    // State 1: no keys, no history → init
    if (!privExists && !pubExists && !historyExists) {
      return init(oculixDir);
    }

    // State 2: no keys but history exists → REFUSE
    if (!privExists && historyExists) {
      throw new StartupException(
          "Private key is missing at " + privKey + " but audit history exists in "
              + journalDir + ".\n"
              + "Refusing to start.\n"
              + "If this is intentional (disk loss, deliberate reset), run:\n"
              + "  oculix-mcp recover\n"
              + "This will record an unsigned gap marker and start a fresh chain.");
    }

    // Partial key state (only one of priv/pub present) → REFUSE
    if (privExists ^ pubExists) {
      throw new StartupException(
          "Inconsistent key state in " + oculixDir + ": only one of "
              + KeyManager.PRIVATE_KEY_FILENAME + " / " + KeyManager.PUBLIC_KEY_FILENAME
              + " is present. Refusing to start.\n"
              + "Run `oculix-mcp recover` to repair or reinitialise.");
    }

    // State 3: keys present but unreadable
    KeyManager keys;
    try {
      keys = KeyManager.loadExisting(privKey, pubKey);
    } catch (Exception e) {
      throw new StartupException(
          "Failed to load Ed25519 key pair from " + oculixDir + ": " + e.getMessage()
              + "\nRefusing to start. Never fall back to unsigned mode.\n"
              + "Restore the key pair from backup, or run `oculix-mcp recover`.");
    }

    // State 4: key doesn't match last entry of most recent journal
    if (historyExists) {
      Path latest = latestJournalFile(journalDir);
      if (latest != null) {
        verifyKeyAgainstLastEntry(latest, keys.publicKey());
      }
    }

    // State 5: all good
    return keys;
  }

  // ── Helpers ──

  private static KeyManager init(Path oculixDir) {
    try {
      return KeyManager.generateAndStore(oculixDir);
    } catch (Exception e) {
      throw new StartupException("Failed to initialise key pair: " + e.getMessage());
    }
  }

  private static boolean journalHistoryExists(Path journalDir) {
    if (!Files.isDirectory(journalDir)) return false;
    try (Stream<Path> s = Files.list(journalDir)) {
      return s.anyMatch(p -> p.getFileName().toString().startsWith("audit-")
                          && p.getFileName().toString().endsWith(".jsonl"));
    } catch (IOException e) {
      return false;
    }
  }

  private static Path latestJournalFile(Path journalDir) {
    try (Stream<Path> s = Files.list(journalDir)) {
      List<Path> files = s
          .filter(p -> p.getFileName().toString().startsWith("audit-")
                    && p.getFileName().toString().endsWith(".jsonl"))
          .sorted(Comparator.reverseOrder())
          .collect(Collectors.toList());
      return files.isEmpty() ? null : files.get(0);
    } catch (IOException e) {
      return null;
    }
  }

  private static void verifyKeyAgainstLastEntry(Path latest, PublicKey currentPub) {
    try (BufferedReader in = Files.newBufferedReader(latest, StandardCharsets.UTF_8)) {
      String lastLine = null;
      String line;
      while ((line = in.readLine()) != null) {
        if (!line.isBlank()) lastLine = line;
      }
      if (lastLine == null) return; // empty file, nothing to verify

      JSONObject o = new JSONObject(lastLine);
      String entryHash = o.getString("entry_hash");
      String sigHex    = o.getString("signature");
      byte[] sig = org.sikuli.mcp.crypto.Hashing.fromHex(sigHex);
      boolean ok = KeyManager.verify(entryHash.getBytes(StandardCharsets.UTF_8), sig, currentPub);
      if (!ok) {
        throw new StartupException(
            "Current public key does not verify the last audit entry in " + latest + ".\n"
                + "The key appears to have been changed outside of `oculix-mcp rotate-key`.\n"
                + "Refusing to start.\n"
                + "Run `oculix-mcp recover` if the old key is genuinely lost.");
      }
    } catch (StartupException e) {
      throw e;
    } catch (Exception e) {
      throw new StartupException(
          "Could not verify last audit entry against current key: " + e.getMessage());
    }
  }

  /**
   * Path used by default for the OculiX MCP config directory.
   */
  public static Path defaultOculixDir() {
    return Paths.get(System.getProperty("user.home"), ".oculix-mcp");
  }

  public static Path defaultJournalDir() {
    return defaultOculixDir().resolve("journal");
  }
}
