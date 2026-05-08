/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sikuli.basics.PreferencesUser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the IDE-wide "last folder used" memory contract:
 * {@link SikulixFileChooser#resolveDefaultDir()} read side and
 * {@link SikulixFileChooser#persistLastDir(File)} write side.
 *
 * <p>The contract these tests pin down:
 * <ul>
 *   <li>resolveDefaultDir returns the stored pref when it exists and is a
 *       valid directory</li>
 *   <li>resolveDefaultDir falls back to {@code user.dir} (the JVM working
 *       directory, i.e. the JAR launch location) when the pref is empty
 *       or points to a non-existent path</li>
 *   <li>resolveDefaultDir falls back to {@code user.home} as a last resort
 *       when {@code user.dir} is also unusable</li>
 *   <li>persistLastDir writes the absolute path of the parent directory
 *       (or the file itself if it is already a directory) to the pref</li>
 *   <li>persistLastDir is a no-op for null, files with no parent, or paths
 *       that do not resolve to an existing directory</li>
 * </ul>
 *
 * <p>Saves and restores the user's actual {@code LAST_OPEN_DIR} pref around
 * each test so the suite is non-destructive on developer machines.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class SikulixFileChooserDefaultDirTest {

  private static final String PREF_KEY = "LAST_OPEN_DIR";
  private String savedPref;

  @BeforeEach
  void saveExistingPref() {
    savedPref = PreferencesUser.get().get(PREF_KEY, "");
  }

  @AfterEach
  void restoreExistingPref() {
    PreferencesUser.get().put(PREF_KEY, savedPref);
  }

  // ---------------------------------------------------------------- read side

  @Test
  void resolveDefaultDir_returnsStoredPref_whenItIsAnExistingDirectory() throws Exception {
    Path tmp = Files.createTempDirectory("oculix-pref-read-");
    try {
      PreferencesUser.get().put(PREF_KEY, tmp.toAbsolutePath().toString());

      File resolved = SikulixFileChooser.resolveDefaultDir();

      assertEquals(tmp.toFile().getCanonicalFile(),
          resolved.getCanonicalFile(),
          "stored pref pointing at an existing dir should win");
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  @Test
  void resolveDefaultDir_fallsBackToUserDir_whenPrefIsEmpty() {
    PreferencesUser.get().put(PREF_KEY, "");

    File resolved = SikulixFileChooser.resolveDefaultDir();

    assertEquals(new File(System.getProperty("user.dir")).getAbsoluteFile(),
        resolved.getAbsoluteFile(),
        "empty pref should fall back to JVM user.dir (= JAR launch dir)");
  }

  @Test
  void resolveDefaultDir_fallsBackToUserDir_whenPrefIsNonExistentPath() {
    String nonExistent = new File(System.getProperty("java.io.tmpdir"),
        "oculix-pref-does-not-exist-" + System.nanoTime()).getAbsolutePath();
    assertFalse(new File(nonExistent).exists(),
        "test setup: path must not exist");
    PreferencesUser.get().put(PREF_KEY, nonExistent);

    File resolved = SikulixFileChooser.resolveDefaultDir();

    assertEquals(new File(System.getProperty("user.dir")).getAbsoluteFile(),
        resolved.getAbsoluteFile(),
        "stale pref pointing at a deleted folder should fall through, not be returned");
  }

  @Test
  void resolveDefaultDir_returnsValidDirectory_inAllConfigurations() {
    PreferencesUser.get().put(PREF_KEY, "");
    File resolved = SikulixFileChooser.resolveDefaultDir();
    // Must always return SOMETHING the file chooser can pass to
    // setCurrentDirectory without throwing — never null, never an empty
    // path, never a bare file.
    assertNotNull(resolved, "must never return null");
    assertNotEquals("", resolved.getPath(), "must never return an empty path");
  }

  // --------------------------------------------------------------- write side

  @Test
  void persistLastDir_writesParentOfFileChosen() throws Exception {
    Path tmpDir = Files.createTempDirectory("oculix-pref-write-");
    File child = new File(tmpDir.toFile(), "script.py");
    try {
      assertTrue(child.createNewFile(), "test setup: child file must be creatable");

      SikulixFileChooser.persistLastDir(child);

      String stored = PreferencesUser.get().get(PREF_KEY, "");
      assertEquals(tmpDir.toFile().getAbsolutePath(), stored,
          "picking a file should persist its parent directory");
    } finally {
      child.delete();
      Files.deleteIfExists(tmpDir);
    }
  }

  @Test
  void persistLastDir_writesSelfWhenChosenIsAlreadyADirectory() throws Exception {
    Path tmpDir = Files.createTempDirectory("oculix-pref-self-");
    try {
      SikulixFileChooser.persistLastDir(tmpDir.toFile());

      String stored = PreferencesUser.get().get(PREF_KEY, "");
      assertEquals(tmpDir.toFile().getAbsolutePath(), stored,
          "picking a directory should persist that directory itself");
    } finally {
      Files.deleteIfExists(tmpDir);
    }
  }

  @Test
  void persistLastDir_noOpOnNull() {
    String before = PreferencesUser.get().get(PREF_KEY, "marker");
    SikulixFileChooser.persistLastDir(null);
    String after = PreferencesUser.get().get(PREF_KEY, "marker");
    assertEquals(before, after, "null input must not touch the pref");
  }

  @Test
  void persistLastDir_noOpWhenChosenHasNoParent() {
    // A bare filename with no path components has getParentFile() == null.
    File rootless = new File("just-a-name.png");
    assertNull(rootless.getParentFile(), "test setup: no parent file");
    String before = PreferencesUser.get().get(PREF_KEY, "marker");

    SikulixFileChooser.persistLastDir(rootless);

    String after = PreferencesUser.get().get(PREF_KEY, "marker");
    assertEquals(before, after,
        "rootless file with no parent must be ignored — never write a junk path");
  }

  @Test
  void persistLastDir_noOpWhenParentDoesNotExist() {
    // Synthetic File whose parent does not exist on disk. We must not write
    // such a path into LAST_OPEN_DIR — it would resurface on next read and
    // leak through resolveDefaultDir as a false positive (mitigated by the
    // existence check there, but write side should be defensive too).
    File ghost = new File(
        new File(System.getProperty("java.io.tmpdir"),
            "oculix-no-such-parent-" + System.nanoTime()),
        "leaf.txt");
    assertFalse(ghost.getParentFile().exists(),
        "test setup: parent must not exist");
    String before = PreferencesUser.get().get(PREF_KEY, "marker");

    SikulixFileChooser.persistLastDir(ghost);

    String after = PreferencesUser.get().get(PREF_KEY, "marker");
    assertEquals(before, after,
        "non-existent parent must not be persisted");
  }

  // ----------------------------------------------- regression: bundle case

  @Test
  void persistLastDir_bundleDirectoryCase_storesSelf_notParent() throws Exception {
    // Regression for the "After opening a .sikuli bundle, the next File ▸
    // Open lands one level too high" bug. Reported scenario:
    //   user picks  C:\Users\DELL\testsikuli.sikuli  (a directory)
    //   bug stored  C:\Users\DELL                    (its parent)
    //   correct     C:\Users\DELL\testsikuli.sikuli  (itself)
    // The pre-fix processDialog used fileChosen.getParent() unconditionally,
    // which is one level too high when fileChosen is already a directory.
    // The fix routes through persistLastDir which checks isDirectory().
    Path bundleParent = Files.createTempDirectory("oculix-bundle-parent-");
    Path bundle = Files.createDirectory(bundleParent.resolve("test.sikuli"));
    try {
      SikulixFileChooser.persistLastDir(bundle.toFile());

      String stored = PreferencesUser.get().get(PREF_KEY, "");
      assertEquals(bundle.toFile().getAbsolutePath(), stored,
          "directory pick must persist the directory itself, not its parent — "
              + "otherwise picking a .sikuli bundle lands the next dialog one level too high");
      assertNotEquals(bundleParent.toFile().getAbsolutePath(), stored,
          "must not store the parent — that was the bug");
    } finally {
      Files.deleteIfExists(bundle);
      Files.deleteIfExists(bundleParent);
    }
  }

  // ----------------------------------------------------------- round-trip

  @Test
  void persistThenResolve_roundTrips_throughTheSamePref() throws Exception {
    Path tmpDir = Files.createTempDirectory("oculix-pref-roundtrip-");
    File child = new File(tmpDir.toFile(), "doc.txt");
    try {
      assertTrue(child.createNewFile());

      SikulixFileChooser.persistLastDir(child);
      File resolved = SikulixFileChooser.resolveDefaultDir();

      assertEquals(tmpDir.toFile().getCanonicalFile(),
          resolved.getCanonicalFile(),
          "the contract: writing a file's parent and reading back must yield the same dir");
    } finally {
      child.delete();
      Files.deleteIfExists(tmpDir);
    }
  }
}
