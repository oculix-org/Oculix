/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.sikuli.support.Commons;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The versions of a script that a save overwrote, kept outside the bundle so nothing reaches git or a shared zip.
 * <p>
 * One folder per script, named after a hash of its path, holding one copy per version named after the moment
 * it was replaced. A failure here never stops a save: losing a history entry is better than losing the script.
 */
final class LocalHistory {

  /** One kept version: when it was replaced, and where its copy lives. */
  static final class Version {
    final Instant time;
    final Path copy;

    Version(Instant time, Path copy) {
      this.time = time;
      this.copy = copy;
    }
  }

  private static final Pattern COPY_NAME = Pattern.compile("(\\d{13})(\\..*)?");
  private static final String SOURCE = "source.txt";

  private static LocalHistory standard;

  private final Path root;
  private final Clock clock;
  private final int maxVersions;
  private final Duration maxAge;

  LocalHistory(Path root, Clock clock, int maxVersions, Duration maxAge) {
    this.root = root;
    this.clock = clock;
    this.maxVersions = maxVersions;
    this.maxAge = maxAge;
  }

  /** The history of this user, 30 days or 100 versions per script, whichever comes first. */
  static synchronized LocalHistory standard() {
    if (standard == null) {
      standard = new LocalHistory(new File(Commons.getAppDataPath(), "LocalHistory").toPath(),
          Clock.systemUTC(), 100, Duration.ofDays(30));
    }
    return standard;
  }

  /**
   * Copy the script as it is on disk, before a save replaces it.
   *
   * @return true when a new version was kept
   */
  boolean keep(File script) {
    try {
      Path source = script.toPath();
      if (!Files.isRegularFile(source)) {
        return false;
      }
      byte[] content = Files.readAllBytes(source);
      List<Version> versions = versions(script);
      if (!versions.isEmpty() && Arrays.equals(content, Files.readAllBytes(versions.get(0).copy))) {
        return false;
      }
      Path folder = Files.createDirectories(folderOf(script));
      Files.write(folder.resolve(SOURCE), key(script).getBytes(StandardCharsets.UTF_8));
      long millis = clock.millis();
      Path copy = folder.resolve(copyName(millis, script));
      while (Files.exists(copy)) {
        copy = folder.resolve(copyName(++millis, script));
      }
      Files.write(copy, content);
      prune(script);
      return true;
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  /** Newest first. */
  List<Version> versions(File script) {
    List<Version> versions = new ArrayList<>();
    Path folder = folderOf(script);
    if (!Files.isDirectory(folder)) {
      return versions;
    }
    try (Stream<Path> files = Files.list(folder)) {
      files.forEach(file -> {
        Matcher name = COPY_NAME.matcher(file.getFileName().toString());
        if (name.matches()) {
          versions.add(new Version(Instant.ofEpochMilli(Long.parseLong(name.group(1))), file));
        }
      });
    } catch (IOException | RuntimeException e) {
      return new ArrayList<>();
    }
    versions.sort(Comparator.comparing((Version v) -> v.time).reversed());
    return versions;
  }

  private void prune(File script) throws IOException {
    Instant oldest = clock.instant().minus(maxAge);
    List<Version> versions = versions(script);
    for (int i = 0; i < versions.size(); i++) {
      Version version = versions.get(i);
      if (i >= maxVersions || version.time.isBefore(oldest)) {
        Files.deleteIfExists(version.copy);
      }
    }
  }

  private Path folderOf(File script) {
    return root.resolve(sha1(key(script)).substring(0, 16));
  }

  /** The script's absolute path, with the case folded where the file system ignores it. */
  private static String key(File script) {
    String path = script.toPath().toAbsolutePath().normalize().toString();
    return File.separatorChar == '\\' ? path.toLowerCase(Locale.ROOT) : path;
  }

  private static String copyName(long millis, File script) {
    String name = script.getName();
    int dot = name.lastIndexOf('.');
    return String.format("%013d", millis) + (dot < 0 ? "" : name.substring(dot));
  }

  private static String sha1(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
