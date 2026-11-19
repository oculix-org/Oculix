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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A script changed by another editor while it is open in the IDE: Refresh must show the disk version,
 * and must never throw away unsaved work, or touch the file, without the user's consent.
 */
class ScriptRefreshTest {

  @TempDir
  Path dir;

  private File script(String content) throws IOException {
    Path file = dir.resolve("test.py");
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    return file.toFile();
  }

  private static void editElsewhere(File file, String content) throws IOException {
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
  }

  private static String disk(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("a script changed in another editor shows its new version after Refresh")
  void showsTheVersionFromDisk() throws IOException {
    File file = script("click(\"old.png\")");
    EditorTab tab = new EditorTab(file, "click(\"old.png\")", false);
    editElsewhere(file, "click(\"new.png\")");

    ScriptRefresh.Outcome outcome = ScriptRefresh.refresh(tab, () -> true);

    assertEquals("click(\"new.png\")", tab.text);
    assertEquals(ScriptRefresh.Outcome.RELOADED, outcome);
  }

  @Test
  @DisplayName("unsaved work survives a Refresh the user declines, and the file keeps the other editor's version")
  void declinedRefreshKeepsEverything() throws IOException {
    File file = script("wait(1)");
    EditorTab tab = new EditorTab(file, "wait(1)\nclick(\"mine.png\")", true);
    editElsewhere(file, "wait(2)");

    ScriptRefresh.Outcome outcome = ScriptRefresh.refresh(tab, () -> false);

    assertEquals("wait(1)\nclick(\"mine.png\")", tab.text);
    assertEquals(true, tab.unsaved);
    assertEquals("wait(2)", disk(file));
    assertEquals(ScriptRefresh.Outcome.KEPT, outcome);
  }

  @Test
  @DisplayName("unsaved work is replaced by the disk version once the user agrees")
  void acceptedRefreshTakesTheDiskVersion() throws IOException {
    File file = script("wait(1)");
    EditorTab tab = new EditorTab(file, "wait(1)\nclick(\"mine.png\")", true);
    editElsewhere(file, "wait(2)");

    ScriptRefresh.Outcome outcome = ScriptRefresh.refresh(tab, () -> true);

    assertEquals("wait(2)", tab.text);
    assertEquals(false, tab.unsaved);
    assertEquals(ScriptRefresh.Outcome.RELOADED, outcome);
  }

  @Test
  @DisplayName("a script without unsaved work is refreshed without asking")
  void noQuestionWithoutUnsavedWork() throws IOException {
    File file = script("a = 1");
    EditorTab tab = new EditorTab(file, "a = 1", false);
    editElsewhere(file, "a = 2");
    AtomicInteger asked = new AtomicInteger();

    ScriptRefresh.refresh(tab, () -> {
      asked.incrementAndGet();
      return false;
    });

    assertEquals(0, asked.get());
    assertEquals("a = 2", tab.text);
  }

  @Test
  @DisplayName("a script deleted by another program leaves the editor as it was")
  void deletedFileLeavesTheEditorAlone() throws IOException {
    File file = script("a = 1");
    EditorTab tab = new EditorTab(file, "a = 1\nb = 2", true);
    Files.delete(file.toPath());

    ScriptRefresh.Outcome outcome = ScriptRefresh.refresh(tab, () -> true);

    assertEquals("a = 1\nb = 2", tab.text);
    assertEquals(ScriptRefresh.Outcome.FAILED, outcome);
  }

  @Test
  @DisplayName("accents and non-Latin text written by another editor come back intact")
  void keepsUtf8Text() throws IOException {
    File file = script("a = 1");
    EditorTab tab = new EditorTab(file, "a = 1", false);
    editElsewhere(file, "popup(\"Déjà prêt · 再読み込み\")");

    ScriptRefresh.refresh(tab, () -> true);

    assertEquals("popup(\"Déjà prêt · 再読み込み\")", tab.text);
  }
}
