/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.crypto;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Set;

/**
 * Manages the Ed25519 key pair used to sign audit-trail entries.
 *
 * <p>Keys are stored under the OculiX MCP config directory
 * (default {@code ~/.oculix-mcp/}):
 * <ul>
 *   <li>{@code private.key} — PKCS8-encoded Ed25519 private key (perms 600 on POSIX)</li>
 *   <li>{@code public.key} — X509-encoded Ed25519 public key (world-readable)</li>
 * </ul>
 *
 * <p>Ed25519 is natively available in the JDK since Java 15 via
 * {@code KeyPairGenerator.getInstance("Ed25519")}, so no external crypto
 * library is required.
 */
public final class KeyManager {

  public static final String PRIVATE_KEY_FILENAME = "private.key";
  public static final String PUBLIC_KEY_FILENAME = "public.key";

  private final PrivateKey privateKey;
  private final PublicKey publicKey;

  private KeyManager(PrivateKey privateKey, PublicKey publicKey) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  // ── Public API ──

  public PrivateKey privateKey() { return privateKey; }
  public PublicKey publicKey()   { return publicKey; }

  public byte[] publicKeyBytes() { return publicKey.getEncoded(); }
  public String publicKeyHex()   { return Hashing.toHex(publicKey.getEncoded()); }
  public String publicKeySha256Hex() { return Hashing.sha256Hex(publicKey.getEncoded()); }

  public byte[] sign(byte[] message) {
    try {
      Signature sig = Signature.getInstance("Ed25519");
      sig.initSign(privateKey);
      sig.update(message);
      return sig.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Ed25519 signing failed", e);
    }
  }

  public static boolean verify(byte[] message, byte[] signature, PublicKey pub) {
    try {
      Signature sig = Signature.getInstance("Ed25519");
      sig.initVerify(pub);
      sig.update(message);
      return sig.verify(signature);
    } catch (GeneralSecurityException e) {
      return false;
    }
  }

  // ── Lifecycle ──

  /**
   * Load the key pair from {@code oculixDir}, generating a fresh pair if absent.
   *
   * <p>Throws if the private key file exists but is unreadable or corrupted —
   * the server must never silently fall back to "unsigned mode".
   */
  public static KeyManager loadOrInit(Path oculixDir) throws IOException, GeneralSecurityException {
    Files.createDirectories(oculixDir);
    Path priv = oculixDir.resolve(PRIVATE_KEY_FILENAME);
    Path pub  = oculixDir.resolve(PUBLIC_KEY_FILENAME);

    boolean privExists = Files.exists(priv);
    boolean pubExists  = Files.exists(pub);

    if (privExists && pubExists) {
      return loadExisting(priv, pub);
    }
    if (privExists ^ pubExists) {
      throw new IllegalStateException(
          "Inconsistent key state: one of private.key / public.key exists without the other in "
              + oculixDir + ". Refusing to start.");
    }
    return generateAndStore(oculixDir);
  }

  /**
   * Force-load without generating. Throws if keys are absent or corrupted.
   * Used after explicit rotation / recovery to verify the new state.
   */
  public static KeyManager loadExisting(Path privPath, Path pubPath)
      throws IOException, GeneralSecurityException {
    byte[] privBytes = Files.readAllBytes(privPath);
    byte[] pubBytes  = Files.readAllBytes(pubPath);
    KeyFactory kf = KeyFactory.getInstance("Ed25519");
    PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
    PublicKey  pub  = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
    return new KeyManager(priv, pub);
  }

  /**
   * Generate a new Ed25519 key pair and persist it. Returns the loaded manager.
   */
  public static KeyManager generateAndStore(Path oculixDir)
      throws IOException, GeneralSecurityException {
    Files.createDirectories(oculixDir);
    KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
    KeyPair kp = gen.generateKeyPair();

    Path priv = oculixDir.resolve(PRIVATE_KEY_FILENAME);
    Path pub  = oculixDir.resolve(PUBLIC_KEY_FILENAME);

    Files.write(priv, kp.getPrivate().getEncoded(),
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    Files.write(pub, kp.getPublic().getEncoded(),
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

    restrictPrivate(priv);
    return new KeyManager(kp.getPrivate(), kp.getPublic());
  }

  /**
   * Set POSIX permissions to owner-only (600) on the private key file.
   * No-op on non-POSIX filesystems (Windows) — on such systems, the user is
   * responsible for folder-level protection (ACLs).
   */
  public static void restrictPrivate(Path priv) throws IOException {
    try {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
      Files.setPosixFilePermissions(priv, perms);
    } catch (UnsupportedOperationException ignored) {
      // Windows / non-POSIX — skip silently
    }
  }
}
