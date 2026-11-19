/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving an image in the explorer must leave every script that names it still working,
 * and must never touch a script that names a different file of the same name.
 */
class ImageReferencesTest {

  private static final Path BUNDLE = Paths.get("C:", "work", "demo.sikuli");

  private static String rebase(String code, Path source, Path target) {
    return ImageReferences.rebase(code, BUNDLE, source, target);
  }

  @Nested
  @DisplayName("a file that moved")
  class MovedFile {

    @Test
    @DisplayName("is followed from the bundle root into a subfolder")
    void intoSubfolder() {
      String code = rebase("click(\"toto.png\")",
          BUNDLE.resolve("toto.png"), BUNDLE.resolve("images").resolve("toto.png"));
      assertEquals("click(\"images/toto.png\")", code);
    }

    @Test
    @DisplayName("is followed back out of a subfolder")
    void outOfSubfolder() {
      String code = rebase("click(\"images/toto.png\")",
          BUNDLE.resolve("images").resolve("toto.png"), BUNDLE.resolve("toto.png"));
      assertEquals("click(\"toto.png\")", code);
    }

    @Test
    @DisplayName("is followed in single quotes as well")
    void singleQuotes() {
      String code = rebase("click('toto.png')",
          BUNDLE.resolve("toto.png"), BUNDLE.resolve("img").resolve("toto.png"));
      assertEquals("click('img/toto.png')", code);
    }

    @Test
    @DisplayName("is followed every time it is named")
    void everyOccurrence() {
      String code = rebase("click(\"toto.png\")\nwait(\"toto.png\", 5)",
          BUNDLE.resolve("toto.png"), BUNDLE.resolve("a").resolve("toto.png"));
      assertEquals("click(\"a/toto.png\")\nwait(\"a/toto.png\", 5)", code);
    }

    @Test
    @DisplayName("keeps the Pattern arguments around it untouched")
    void patternArguments() {
      String code = rebase("Pattern(\"toto.png\").similar(0.85).targetOffset(3,4)",
          BUNDLE.resolve("toto.png"), BUNDLE.resolve("a").resolve("toto.png"));
      assertEquals("Pattern(\"a/toto.png\").similar(0.85).targetOffset(3,4)", code);
    }

    @Test
    @DisplayName("is written absolute when it leaves the bundle")
    void outsideTheBundle() {
      Path outside = Paths.get("C:", "work", "shared", "toto.png");
      String code = rebase("click(\"toto.png\")", BUNDLE.resolve("toto.png"), outside);
      assertEquals("click(\"C:/work/shared/toto.png\")", code);
    }
  }

  @Nested
  @DisplayName("a script that names something else")
  class Untouched {

    @Test
    @DisplayName("keeps its own image of the same name")
    void sameNameOtherFolder() {
      String code = "click(\"b/toto.png\")";
      assertSame(code, rebase(code,
          BUNDLE.resolve("toto.png"), BUNDLE.resolve("images").resolve("toto.png")));
    }

    @Test
    @DisplayName("keeps a name that is not an image")
    void notAnImage() {
      String code = "open(\"toto.txt\")";
      assertSame(code, rebase(code,
          BUNDLE.resolve("toto.txt"), BUNDLE.resolve("images").resolve("toto.txt")));
    }

    @Test
    @DisplayName("is left alone when the entry did not really move")
    void sameSourceAndTarget() {
      String code = "click(\"toto.png\")";
      assertSame(code, rebase(code, BUNDLE.resolve("toto.png"), BUNDLE.resolve("toto.png")));
    }
  }

  @Nested
  @DisplayName("a folder that moved")
  class MovedFolder {

    @Test
    @DisplayName("takes every image under it along")
    void carriesItsContent() {
      String code = rebase("click(\"img/a.png\")\nclick(\"img/deep/b.png\")",
          BUNDLE.resolve("img"), BUNDLE.resolve("assets").resolve("img"));
      assertEquals("click(\"assets/img/a.png\")\nclick(\"assets/img/deep/b.png\")", code);
    }

    @Test
    @DisplayName("leaves a sibling folder with a similar name alone")
    void siblingPrefix() {
      String code = "click(\"images2/a.png\")";
      assertSame(code, rebase(code, BUNDLE.resolve("images"), BUNDLE.resolve("a").resolve("images")));
    }
  }

  @Nested
  @DisplayName("a script open in a tab")
  class OpenTab {

    @TempDir
    Path bundle;

    private EditorTab open(String saved, String shown, boolean unsaved) throws IOException {
      Path file = bundle.resolve("dossier.py");
      Files.write(file, saved.getBytes(StandardCharsets.UTF_8));
      return new EditorTab(file.toFile(), shown, unsaved);
    }

    private String disk(EditorTab tab) throws IOException {
      return new String(Files.readAllBytes(tab.file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("keeps the new name on disk, so it is still there after a restart")
    void cleanTabIsSaved() throws IOException {
      EditorTab tab = open("Pattern(\"Images/x.png\")", "Pattern(\"Images/x.png\")", false);

      ImageReferences.follow(tab, bundle.resolve("Images").resolve("x.png"), bundle.resolve("x.png"));

      assertEquals("Pattern(\"x.png\")", disk(tab));
      assertEquals("Pattern(\"x.png\")", tab.text);
      assertFalse(tab.unsaved);
    }

    @Test
    @DisplayName("with unsaved work, is rewritten and flagged unsaved, the file left for the user to save")
    void unsavedTabIsFlagged() throws IOException {
      EditorTab tab = open("Pattern(\"Images/x.png\")", "Pattern(\"Images/x.png\")\nwait(1)", true);

      ImageReferences.follow(tab, bundle.resolve("Images").resolve("x.png"), bundle.resolve("x.png"));

      assertEquals("Pattern(\"x.png\")\nwait(1)", tab.text);
      assertTrue(tab.unsaved);
      assertEquals("Pattern(\"Images/x.png\")", disk(tab));
      assertEquals(0, tab.saves);
    }

    @Test
    @DisplayName("that does not name the moved image is neither rewritten nor saved")
    void unrelatedTabIsLeftAlone() throws IOException {
      EditorTab tab = open("click(\"other.png\")", "click(\"other.png\")", false);

      boolean rewritten = ImageReferences.follow(tab,
          bundle.resolve("Images").resolve("x.png"), bundle.resolve("x.png"));

      assertFalse(rewritten);
      assertEquals(0, tab.saves);
    }
  }
}
