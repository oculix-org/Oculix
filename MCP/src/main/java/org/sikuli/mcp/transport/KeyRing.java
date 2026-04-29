/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rotatable keyring of HMAC-SHA256 secrets for session-token signing.
 *
 * <p>Each key is identified by a short {@code kid}. One kid is marked
 * <em>current</em> and is used to sign newly minted tokens. Older kids
 * remain in the ring so that previously issued tokens can still be
 * verified until the operator explicitly retires them — this decouples
 * key rotation from token expiry.
 *
 * <p>Persistence format is a JSON file with {@code 0600} POSIX perms:
 * <pre>
 * {
 *   "current": "k7",
 *   "keys": [
 *     { "kid": "k7", "secret_b64": "..." , "created_at": 1712945000 },
 *     { "kid": "k6", "secret_b64": "..." , "created_at": 1712856000 }
 *   ]
 * }
 * </pre>
 *
 * <p>Thread-safe: all mutations are synchronized. Reads use a defensive
 * copy of the underlying map.
 */
public final class KeyRing {

  /** Length of HMAC secrets in bytes (256 bits). */
  public static final int SECRET_BYTES = 32;

  /** Length of generated kids in bytes before b64url encoding. */
  public static final int KID_BYTES = 6;

  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64DEC = Base64.getUrlDecoder();

  private final Map<String, Entry> keys = new LinkedHashMap<>();
  private String currentKid;

  private KeyRing() {}

  // ── Factory / persistence ──

  /**
   * Create an in-memory keyring with one fresh key. Not persisted.
   * Suitable for tests.
   */
  public static KeyRing inMemory() {
    KeyRing r = new KeyRing();
    r.generate();
    return r;
  }

  /**
   * Load the keyring from {@code path}, generating a fresh ring with one
   * key if the file does not exist. Writes are atomic-ish (temp file +
   * rename) and {@code 0600} on POSIX.
   */
  public static KeyRing loadOrInit(Path path) throws IOException {
    if (Files.exists(path)) {
      return load(path);
    }
    KeyRing fresh = new KeyRing();
    fresh.generate();
    fresh.save(path);
    return fresh;
  }

  public static KeyRing load(Path path) throws IOException {
    JSONObject root = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
    KeyRing r = new KeyRing();
    JSONArray arr = root.getJSONArray("keys");
    for (int i = 0; i < arr.length(); i++) {
      JSONObject k = arr.getJSONObject(i);
      r.keys.put(k.getString("kid"),
          new Entry(B64DEC.decode(k.getString("secret_b64")),
              k.optLong("created_at", 0L)));
    }
    r.currentKid = root.getString("current");
    if (!r.keys.containsKey(r.currentKid)) {
      throw new IOException("Keyring 'current' kid not in 'keys': " + r.currentKid);
    }
    return r;
  }

  public synchronized void save(Path path) throws IOException {
    JSONArray arr = new JSONArray();
    for (Map.Entry<String, Entry> e : keys.entrySet()) {
      arr.put(new JSONObject()
          .put("kid", e.getKey())
          .put("secret_b64", B64.encodeToString(e.getValue().secret))
          .put("created_at", e.getValue().createdAt));
    }
    JSONObject root = new JSONObject()
        .put("current", currentKid)
        .put("keys", arr);

    Files.createDirectories(path.getParent());
    Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
    Files.writeString(tmp, root.toString(2), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
      Files.setPosixFilePermissions(tmp, perms);
    } catch (UnsupportedOperationException ignored) { /* non-POSIX */ }
    Files.move(tmp, path,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
  }

  // ── Mutation ──

  /**
   * Generate a fresh key, install it as current, and return its kid.
   * Previous keys remain in the ring so tokens they signed are still
   * verifiable until {@link #retire(String)} is called.
   */
  public synchronized String generate() {
    byte[] secret = new byte[SECRET_BYTES];
    new SecureRandom().nextBytes(secret);
    String kid = mintKid();
    keys.put(kid, new Entry(secret, System.currentTimeMillis() / 1000L));
    currentKid = kid;
    return kid;
  }

  /** Remove a kid from the ring. Tokens signed with it stop verifying. */
  public synchronized void retire(String kid) {
    if (kid.equals(currentKid)) {
      throw new IllegalStateException("Cannot retire the current kid " + kid
          + ". Call generate() first, then retire the old one.");
    }
    keys.remove(kid);
  }

  // ── Read ──

  public synchronized String currentKid() {
    return currentKid;
  }

  /** Returns the secret for {@code kid}, or {@code null} if unknown. */
  public synchronized byte[] secret(String kid) {
    Entry e = keys.get(kid);
    return e == null ? null : e.secret.clone();
  }

  public synchronized Set<String> kids() {
    return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(keys.keySet()));
  }

  public synchronized int size() {
    return keys.size();
  }

  // ── Helpers ──

  private static String mintKid() {
    byte[] b = new byte[KID_BYTES];
    new SecureRandom().nextBytes(b);
    return "k" + B64.encodeToString(b);
  }

  private static final class Entry {
    final byte[] secret;
    final long createdAt;
    Entry(byte[] secret, long createdAt) {
      this.secret = Objects.requireNonNull(secret);
      this.createdAt = createdAt;
    }
  }
}
