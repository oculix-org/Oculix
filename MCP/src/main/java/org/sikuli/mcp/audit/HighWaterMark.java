/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.audit;

import org.json.JSONObject;
import org.sikuli.mcp.crypto.CanonicalJson;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.mcp.crypto.KeyManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.PublicKey;
import java.util.Optional;

/**
 * Signed anchor pointing at the last entry of the journal chain.
 *
 * <p>Purpose: close the tail-truncation hole. The intra-file {@code prev_hash}
 * chain and the cross-file {@code rotation_end}/{@code rotation_begin} pair
 * detect modification and mid-chain deletion. Neither detects an attacker
 * lopping off the last N files: nothing after them points at them, so their
 * absence leaves no trace. The high-water-mark is that trace, kept outside
 * the journal directory and signed with the current Ed25519 key.
 *
 * <p>Anchor payload (four business fields, all under the signature):
 * <ul>
 *   <li>{@code last_entry_hash} — {@code entry_hash} of the most recent write</li>
 *   <li>{@code last_seq} — its per-file sequence number</li>
 *   <li>{@code last_file} — the journal file it landed in</li>
 *   <li>{@code last_ts_utc} — its ISO-8601 UTC timestamp</li>
 * </ul>
 *
 * <p>{@code last_file} is included in the signed payload deliberately: a
 * renamed file must invalidate the anchor, otherwise a resolver mapping
 * {@code last_file} to an actual on-disk path could be misled without any
 * signature failure to flag it.
 *
 * <p>The anchor path is injected at construction so a future phase can move
 * it to a genuinely external location (other volume, syslog forward,
 * notary). In phase 1 it lives next to {@code KeyManager}'s key files in
 * {@code oculixDir}, which survives a cosmetic wipe of {@code journalDir}
 * but not a wipe of the whole {@code oculixDir}. That limit is documented,
 * not hidden.
 *
 * <p>Threading: {@link #update} and {@link #reset} are {@code synchronized};
 * {@link #load} is too so callers can read a consistent snapshot even under
 * concurrent writes. On-disk atomicity is provided by a tmp-and-move
 * sequence with {@link StandardCopyOption#ATOMIC_MOVE} where the filesystem
 * supports it.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 4.0.1
 */
public final class HighWaterMark {

  /**
   * Immutable snapshot of the four business fields plus their signature.
   * The canonical form used for signing excludes {@code signature} itself
   * so the four fields hash and sign identically on both write and verify.
   */
  public static final class Snapshot {
    public final String lastEntryHash;
    public final long lastSeq;
    public final String lastFile;
    public final String lastTsUtc;
    public final String signature;

    public Snapshot(String lastEntryHash, long lastSeq,
                    String lastFile, String lastTsUtc, String signature) {
      this.lastEntryHash = lastEntryHash;
      this.lastSeq = lastSeq;
      this.lastFile = lastFile;
      this.lastTsUtc = lastTsUtc;
      this.signature = signature;
    }

    /**
     * Canonical form under the signature: all four business fields,
     * signature deliberately absent. Serialised via
     * {@link CanonicalJson#serialize} for deterministic bytes.
     */
    public JSONObject toCanonicalJson() {
      JSONObject o = new JSONObject();
      o.put("last_entry_hash", lastEntryHash);
      o.put("last_seq", lastSeq);
      o.put("last_file", lastFile);
      o.put("last_ts_utc", lastTsUtc);
      return o;
    }

    /**
     * Full on-disk form: canonical fields plus the hex signature.
     */
    public JSONObject toJson() {
      JSONObject o = toCanonicalJson();
      o.put("signature", signature);
      return o;
    }
  }

  private static final String ANCHOR_TMP_SUFFIX = ".tmp";

  private final Path anchorPath;
  private final KeyManager keys;

  /**
   * @param anchorPath absolute path of the anchor file; the parent
   *                   directory is created on write if missing.
   * @param keys       key manager whose private key signs updates; expected
   *                   to hold the current key after any rotation or recovery.
   */
  public HighWaterMark(Path anchorPath, KeyManager keys) {
    this.anchorPath = anchorPath;
    this.keys = keys;
  }

  public Path anchorPath() {
    return anchorPath;
  }

  /**
   * Sign the four business fields with the currently-configured key and
   * write the anchor atomically. Called after each journal write.
   */
  public synchronized void update(String lastEntryHash, long lastSeq,
                                  String lastFile, String lastTsUtc) throws IOException {
    Snapshot unsigned = new Snapshot(lastEntryHash, lastSeq, lastFile, lastTsUtc, null);
    String canonical = CanonicalJson.serialize(unsigned.toCanonicalJson());
    byte[] sig = keys.sign(canonical.getBytes(StandardCharsets.UTF_8));
    Snapshot signed = new Snapshot(
        lastEntryHash, lastSeq, lastFile, lastTsUtc, Hashing.toHex(sig));
    writeAtomic(signed);
  }

  /**
   * Semantically identical to {@link #update} but named to make the intent
   * of the caller explicit: a key promotion (rotate-key, recover) has just
   * happened and the anchor must be re-signed by the new key. Without a
   * reset after promotion the next {@code verify} would read an anchor
   * signed by the archived key and report a bogus tamper.
   */
  public synchronized void reset(String lastEntryHash, long lastSeq,
                                 String lastFile, String lastTsUtc) throws IOException {
    update(lastEntryHash, lastSeq, lastFile, lastTsUtc);
  }

  /**
   * Read the anchor from disk. Returns {@link Optional#empty()} if the
   * anchor does not exist (fresh install, no queue to anchor yet).
   * Signature is <em>not</em> verified here so the caller can decide
   * whether a bad signature is a warning or a hard fail. Use
   * {@link #verify(Snapshot, PublicKey)} for the crypto check.
   *
   * @throws IOException if the anchor exists but is unparseable
   */
  public synchronized Optional<Snapshot> load() throws IOException {
    if (!Files.exists(anchorPath)) {
      return Optional.empty();
    }
    String content = Files.readString(anchorPath, StandardCharsets.UTF_8);
    JSONObject o;
    try {
      o = new JSONObject(content);
    } catch (Exception e) {
      throw new IOException(
          "HWM anchor at " + anchorPath + " is not valid JSON: " + e.getMessage(), e);
    }
    try {
      return Optional.of(new Snapshot(
          o.getString("last_entry_hash"),
          o.getLong("last_seq"),
          o.getString("last_file"),
          o.getString("last_ts_utc"),
          o.getString("signature")));
    } catch (Exception e) {
      throw new IOException(
          "HWM anchor at " + anchorPath + " is missing required fields: " + e.getMessage(), e);
    }
  }

  /**
   * Ed25519-verify the given snapshot against a public key. Returns
   * {@code false} on any failure (bad hex, wrong length, invalid signature)
   * rather than throwing so the caller can decide the escalation policy.
   */
  public static boolean verify(Snapshot snapshot, PublicKey publicKey) {
    if (snapshot == null || snapshot.signature == null) {
      return false;
    }
    byte[] sig;
    try {
      sig = Hashing.fromHex(snapshot.signature);
    } catch (Exception e) {
      return false;
    }
    String canonical = CanonicalJson.serialize(snapshot.toCanonicalJson());
    return KeyManager.verify(canonical.getBytes(StandardCharsets.UTF_8), sig, publicKey);
  }

  private void writeAtomic(Snapshot signed) throws IOException {
    Path parent = anchorPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path tmp = anchorPath.resolveSibling(anchorPath.getFileName() + ANCHOR_TMP_SUFFIX);
    Files.writeString(tmp, signed.toJson().toString(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
    try {
      Files.move(tmp, anchorPath,
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      // Cross-filesystem tmp/anchor. Rare when tmp sits next to the anchor,
      // but not impossible on exotic mounts. Fall back to a non-atomic move.
      Files.move(tmp, anchorPath, StandardCopyOption.REPLACE_EXISTING);
    }
    tryLockDownPermissions(anchorPath);
  }

  /**
   * Best-effort {@code 0600} on POSIX. Windows silently skips: NTFS ACLs
   * are richer than POSIX bits and the correct-answer there is inherited
   * from {@code oculixDir}, which {@code KeyManager} already restricts.
   */
  private static void tryLockDownPermissions(Path p) {
    try {
      PosixFileAttributeView view =
          Files.getFileAttributeView(p, PosixFileAttributeView.class);
      if (view != null) {
        view.setPermissions(PosixFilePermissions.fromString("rw-------"));
      }
    } catch (Exception ignore) {
      // POSIX view unavailable — nothing to do here.
    }
  }
}
