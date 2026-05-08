/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@link ScriptExplorer.ScriptInfo}, the data record that the
 * Workspace pane uses to render each open script as a card. The
 * {@code fromFolder} factory is the entry point used by SikulixIDE on every
 * tab change, so its image-counting behaviour and saved-flag handling must
 * stay deterministic.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class ScriptExplorerScriptInfoTest {

  @Test
  void fromFolder_with_null_folder_returns_zero_images_and_empty_path() {
    ScriptExplorer.ScriptInfo info =
        ScriptExplorer.ScriptInfo.fromFolder("ghost", null, false);
    assertEquals("ghost", info.name);
    assertEquals(0, info.imageCount);
    assertEquals("", info.path);
  }

  @Test
  void fromFolder_with_non_existent_folder_returns_zero_images() {
    File ghost = new File(System.getProperty("java.io.tmpdir"),
        "oculix-no-folder-" + System.nanoTime());
    assertFalse(ghost.exists());
    ScriptExplorer.ScriptInfo info =
        ScriptExplorer.ScriptInfo.fromFolder("ghost", ghost, false);
    assertEquals(0, info.imageCount);
  }

  @Test
  void fromFolder_counts_only_png_extensions() throws Exception {
    Path tmp = Files.createTempDirectory("oculix-script-info-");
    try {
      Files.createFile(tmp.resolve("a.png"));
      Files.createFile(tmp.resolve("b.png"));
      Files.createFile(tmp.resolve("c.txt"));
      Files.createFile(tmp.resolve("d.jpg"));

      ScriptExplorer.ScriptInfo info =
          ScriptExplorer.ScriptInfo.fromFolder("test", tmp.toFile(), false);
      assertEquals(2, info.imageCount,
          "only .png matches the workspace card counter");
    } finally {
      // best-effort cleanup
      Files.walk(tmp).map(Path::toFile)
          .sorted((a, b) -> b.compareTo(a))
          .forEach(File::delete);
    }
  }

  @Test
  void fromFolder_isTemp_true_means_not_saved() {
    File anywhere = new File(System.getProperty("java.io.tmpdir"));
    ScriptExplorer.ScriptInfo info =
        ScriptExplorer.ScriptInfo.fromFolder("untitled", anywhere, true);
    assertFalse(info.saved, "isTemp=true → saved=false");
  }

  @Test
  void fromFolder_isTemp_false_means_saved() {
    File anywhere = new File(System.getProperty("java.io.tmpdir"));
    ScriptExplorer.ScriptInfo info =
        ScriptExplorer.ScriptInfo.fromFolder("foo", anywhere, false);
    assertTrue(info.saved, "isTemp=false → saved=true (persisted to disk)");
  }

  @Test
  void fromFolder_path_is_absolute() throws Exception {
    Path tmp = Files.createTempDirectory("oculix-path-abs-");
    try {
      ScriptExplorer.ScriptInfo info =
          ScriptExplorer.ScriptInfo.fromFolder("abs", tmp.toFile(), false);
      assertTrue(new File(info.path).isAbsolute(),
          "ScriptInfo.path must be absolute so the workspace card tooltip is unambiguous");
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  @Test
  void direct_construction_preserves_all_fields() {
    ScriptExplorer.ScriptInfo info =
        new ScriptExplorer.ScriptInfo("name", "/some/path", 42, true);
    assertEquals("name", info.name);
    assertEquals("/some/path", info.path);
    assertEquals(42, info.imageCount);
    assertTrue(info.saved);
  }

  @Test
  void fromFolder_name_is_propagated_verbatim() {
    File anywhere = new File(System.getProperty("java.io.tmpdir"));
    ScriptExplorer.ScriptInfo info =
        ScriptExplorer.ScriptInfo.fromFolder("My Script.sikuli", anywhere, false);
    assertEquals("My Script.sikuli", info.name);
  }
}
