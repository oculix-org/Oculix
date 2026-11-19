/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A save overwrites the script: the version it replaced must still be there to read back,
 * without filling the disk and without ever stopping the save itself.
 */
class LocalHistoryTest {

  @TempDir
  Path work;

  private Instant now = Instant.parse("2026-10-04T15:00:00Z");

  /** A clock the test moves by hand. */
  private final Clock clock = new Clock() {
    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  };

  private LocalHistory history(int maxVersions, Duration maxAge) {
    return new LocalHistory(work.resolve("history"), clock, maxVersions, maxAge);
  }

  private LocalHistory history() {
    return history(100, Duration.ofDays(30));
  }

  private File script(String folder, String content) throws IOException {
    Path dir = Files.createDirectories(work.resolve(folder));
    Path file = dir.resolve("dossier.py");
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    return file.toFile();
  }

  /** What the IDE does on save: keep the old version, then write the new one. */
  private void save(LocalHistory history, File script, String content) throws IOException {
    history.keep(script);
    Files.write(script.toPath(), content.getBytes(StandardCharsets.UTF_8));
    now = now.plusSeconds(60);
  }

  private static List<String> contents(LocalHistory history, File script) {
    return history.versions(script).stream().map(v -> {
      try {
        return new String(Files.readAllBytes(v.copy), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }).collect(Collectors.toList());
  }

  @Test
  @DisplayName("the version a save overwrote can be read back")
  void overwrittenVersionIsKept() throws IOException {
    LocalHistory history = history();
    File script = script("a.sikuli", "click(\"ok.png\")");

    save(history, script, "oops");

    assertEquals(List.of("click(\"ok.png\")"), contents(history, script));
  }

  @Test
  @DisplayName("versions come newest first")
  void newestFirst() throws IOException {
    LocalHistory history = history();
    File script = script("a.sikuli", "v1");

    save(history, script, "v2");
    save(history, script, "v3");
    save(history, script, "v4");

    assertEquals(List.of("v3", "v2", "v1"), contents(history, script));
  }

  @Test
  @DisplayName("saving the same content again adds no version")
  void noDuplicate() throws IOException {
    LocalHistory history = history();
    File script = script("a.sikuli", "v1");

    save(history, script, "v2");
    save(history, script, "v2");
    save(history, script, "v2");

    assertEquals(List.of("v2", "v1"), contents(history, script));
  }

  @Test
  @DisplayName("keeps no more than the configured number of versions, dropping the oldest")
  void capsTheCount() throws IOException {
    LocalHistory history = history(3, Duration.ofDays(30));
    File script = script("a.sikuli", "v1");

    for (int i = 2; i <= 6; i++) {
      save(history, script, "v" + i);
    }

    assertEquals(List.of("v5", "v4", "v3"), contents(history, script));
  }

  @Test
  @DisplayName("drops versions older than the configured age")
  void capsTheAge() throws IOException {
    LocalHistory history = history(100, Duration.ofDays(30));
    File script = script("a.sikuli", "old");

    save(history, script, "mid");
    now = now.plus(Duration.ofDays(31));
    save(history, script, "new");

    assertEquals(List.of("mid"), contents(history, script));
  }

  @Test
  @DisplayName("two scripts of the same name in different bundles keep separate histories")
  void sameNameDifferentBundles() throws IOException {
    LocalHistory history = history();
    File first = script("a.sikuli", "first");
    File second = script("b.sikuli", "second");

    save(history, first, "first changed");
    save(history, second, "second changed");

    assertEquals(List.of("first"), contents(history, first));
    assertEquals(List.of("second"), contents(history, second));
  }

  @Test
  @DisplayName("a script not yet on disk has nothing to keep")
  void newScript() {
    LocalHistory history = history();
    File script = work.resolve("new.sikuli").resolve("dossier.py").toFile();

    assertFalse(history.keep(script));
    assertTrue(history.versions(script).isEmpty());
  }

  @Test
  @DisplayName("accents and non-Latin text come back byte for byte")
  void keepsBytes() throws IOException {
    LocalHistory history = history();
    File script = script("a.sikuli", "popup(\"Déjà prêt · 再読み込み\")");

    save(history, script, "x");

    assertEquals(List.of("popup(\"Déjà prêt · 再読み込み\")"), contents(history, script));
  }

  @Test
  @DisplayName("a history that cannot be written does not stop the save")
  void brokenHistoryNeverThrows() throws IOException {
    Path blocked = work.resolve("history");
    Files.write(blocked, new byte[]{1});
    LocalHistory history = history();
    File script = script("a.sikuli", "v1");

    assertFalse(history.keep(script));
    assertTrue(history.versions(script).isEmpty());
  }
}
