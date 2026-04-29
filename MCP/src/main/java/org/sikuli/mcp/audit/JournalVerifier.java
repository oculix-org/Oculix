/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import org.json.JSONObject;
import org.sikuli.mcp.crypto.CanonicalJson;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.mcp.crypto.KeyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone verifier for JSONL audit journals.
 *
 * <p>Walks every line of a journal file, re-computes {@code entry_hash},
 * checks the Ed25519 signature against the provided public key, and ensures
 * that {@code prev_hash} chains correctly from one entry to the next.
 *
 * <p>Also verifies invariants on {@code seq}:
 * <ul>
 *   <li>starts at 0 for the first entry of the file</li>
 *   <li>strictly increments by 1 per entry</li>
 *   <li>{@code (ts_utc, seq)} is strictly increasing</li>
 * </ul>
 */
public final class JournalVerifier {

  public static final class Result {
    public final boolean ok;
    public final int entriesChecked;
    public final List<String> issues;
    public Result(boolean ok, int entriesChecked, List<String> issues) {
      this.ok = ok;
      this.entriesChecked = entriesChecked;
      this.issues = issues;
    }
  }

  public static Result verify(Path file, PublicKey publicKey) throws IOException {
    List<String> issues = new ArrayList<>();
    int lineNo = 0;
    long expectedSeq = 0;
    String expectedPrev = null;
    String lastTs = null;

    try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = in.readLine()) != null) {
        lineNo++;
        if (line.isBlank()) continue;
        JSONObject o;
        try {
          o = new JSONObject(line);
        } catch (Exception e) {
          issues.add("line " + lineNo + ": invalid JSON — " + e.getMessage());
          continue;
        }

        long seq = o.getLong("seq");
        String ts = o.getString("ts_utc");
        String prev = o.getString("prev_hash");
        String entryHash = o.getString("entry_hash");
        String sigHex = o.getString("signature");

        // Canonical re-serialisation: strip entry_hash + signature, re-hash
        JSONObject canonical = new JSONObject(line);
        canonical.remove("entry_hash");
        canonical.remove("signature");
        String recomputed = Hashing.sha256Hex(CanonicalJson.serialize(canonical));
        if (!recomputed.equals(entryHash)) {
          issues.add("line " + lineNo + ": entry_hash mismatch (recomputed " + recomputed + ")");
        }

        byte[] sig = Hashing.fromHex(sigHex);
        boolean sigOk = KeyManager.verify(
            entryHash.getBytes(StandardCharsets.UTF_8), sig, publicKey);
        if (!sigOk) {
          issues.add("line " + lineNo + ": signature verification failed");
        }

        if (seq != expectedSeq) {
          issues.add("line " + lineNo + ": seq=" + seq + " expected=" + expectedSeq);
        }
        expectedSeq = seq + 1;

        if (expectedPrev != null && !prev.equals(expectedPrev)) {
          issues.add("line " + lineNo + ": prev_hash chain broken (got " + prev
              + " expected " + expectedPrev + ")");
        }
        expectedPrev = entryHash;

        if (lastTs != null && ts.compareTo(lastTs) < 0) {
          issues.add("line " + lineNo + ": ts_utc regressed (got " + ts
              + " after " + lastTs + ")");
        }
        lastTs = ts;
      }
    }

    return new Result(issues.isEmpty(), lineNo, issues);
  }

  public static PublicKey loadPublicKey(Path pubPath) throws Exception {
    byte[] pubBytes = Files.readAllBytes(pubPath);
    KeyFactory kf = KeyFactory.getInstance("Ed25519");
    return kf.generatePublic(new X509EncodedKeySpec(pubBytes));
  }
}
