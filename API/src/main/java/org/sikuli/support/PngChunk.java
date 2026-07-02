package org.sikuli.support;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Streaming read/write of a private ancillary chunk in a PNG file.
 * Zero external dependency. No full-file buffering.
 * <p>
 * Read exits as soon as the target chunk is found. Write streams chunk-by-chunk
 * from source to a temp file, replacing the target chunk in place or inserting
 * before IEND if absent, then atomic-renames.
 */
public final class PngChunk {

  private static final byte[] SIGNATURE = {
      (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
  };
  private static final byte[] IEND_BYTES = {'I', 'E', 'N', 'D'};

  private PngChunk() {}

  /**
   * Read the payload of a chunk with the given 4-letter type.
   * Returns null if the chunk is absent.
   * Reads only what is needed; skips other chunks via {@link java.io.InputStream#skip(long)}.
   */
  public static byte[] read(File png, String type) throws IOException {
    if (type.length() != 4) {
      throw new IllegalArgumentException("Chunk type must be 4 chars: " + type);
    }
    byte[] target = type.getBytes(StandardCharsets.US_ASCII);

    try (DataInputStream in = new DataInputStream(
        new BufferedInputStream(new FileInputStream(png), 64))) {
      validateSignature(in);
      byte[] chunkType = new byte[4];
      while (true) {
        int length = in.readInt();
        in.readFully(chunkType);
        if (Arrays.equals(chunkType, target)) {
          byte[] data = new byte[length];
          in.readFully(data);
          return data;
        }
        if (Arrays.equals(chunkType, IEND_BYTES)) {
          return null;
        }
        skipFully(in, (long) length + 4L);
      }
    } catch (EOFException e) {
      return null;
    }
  }

  /**
   * Write (or replace) a chunk with the given type and payload.
   * If a chunk of that type already exists, it is replaced in place.
   * Otherwise the new chunk is inserted before IEND.
   * <p>
   * Streams through a temp file then atomic-renames. No full-file buffering.
   */
  public static void write(File png, String type, byte[] payload) throws IOException {
    if (type.length() != 4) {
      throw new IllegalArgumentException("Chunk type must be 4 chars: " + type);
    }
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    File tmp = new File(png.getParentFile(), png.getName() + ".tmp");

    try (DataInputStream in = new DataInputStream(
            new BufferedInputStream(new FileInputStream(png)));
         DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(tmp)))) {

      validateSignature(in);
      out.write(SIGNATURE);

      byte[] chunkType = new byte[4];
      byte[] copyBuf = null;
      boolean inserted = false;

      while (true) {
        int length = in.readInt();
        in.readFully(chunkType);

        if (!inserted && Arrays.equals(chunkType, typeBytes)) {
          // Replace: skip old data + CRC, write new chunk
          skipFully(in, (long) length + 4L);
          writeChunkBytes(out, typeBytes, payload);
          inserted = true;
          continue;
        }
        if (Arrays.equals(chunkType, IEND_BYTES)) {
          if (!inserted) {
            writeChunkBytes(out, typeBytes, payload);
          }
          // Copy IEND as-is and stop
          out.writeInt(length);
          out.write(chunkType);
          copyBuf = ensureCapacity(copyBuf, length + 4);
          in.readFully(copyBuf, 0, length + 4);
          out.write(copyBuf, 0, length + 4);
          break;
        }
        // Pass-through chunk
        out.writeInt(length);
        out.write(chunkType);
        copyBuf = ensureCapacity(copyBuf, length + 4);
        in.readFully(copyBuf, 0, length + 4);
        out.write(copyBuf, 0, length + 4);
      }
    }

    if (!png.delete()) {
      throw new IOException("Failed to delete " + png);
    }
    if (!tmp.renameTo(png)) {
      throw new IOException("Failed to rename " + tmp + " to " + png);
    }
  }

  // ── internals ──────────────────────────────────────────────────────

  private static void validateSignature(DataInputStream in) throws IOException {
    byte[] sig = new byte[8];
    in.readFully(sig);
    if (!Arrays.equals(sig, SIGNATURE)) {
      throw new IOException("Not a PNG file");
    }
  }

  private static void writeChunkBytes(DataOutputStream out, byte[] typeBytes, byte[] data)
      throws IOException {
    out.writeInt(data.length);
    out.write(typeBytes);
    out.write(data);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    out.writeInt((int) crc.getValue());
  }

  private static void skipFully(DataInputStream in, long n) throws IOException {
    long remaining = n;
    while (remaining > 0) {
      long skipped = in.skip(remaining);
      if (skipped <= 0) {
        // skip() not guaranteed to make progress; force read fallback
        int r = in.read();
        if (r < 0) throw new EOFException();
        remaining -= 1;
      } else {
        remaining -= skipped;
      }
    }
  }

  private static byte[] ensureCapacity(byte[] buf, int needed) {
    if (buf == null || buf.length < needed) {
      int size = Math.max(needed, 8192);
      return new byte[size];
    }
    return buf;
  }
}
