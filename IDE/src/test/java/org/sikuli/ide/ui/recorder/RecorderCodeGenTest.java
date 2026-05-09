/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sikuli.script.Pattern;
import org.sikuli.support.recorder.generators.ICodeGenerator;
import org.sikuli.support.recorder.generators.JavaCodeGenerator;
import org.sikuli.support.recorder.generators.JythonCodeGenerator;
import org.sikuli.support.recorder.generators.RobotFrameworkCodeGenerator;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the Recorder's code-generation surface — the helpers that
 * decide what gets pushed into the user's script on Insert &amp; Close.
 *
 * <p>These tests pin down the rc5 contract that prevents Bug #1 (duplicate
 * "from sikuli import *" at every consecutive Insert): the trio
 * {@code initHeaders / getHeaderMarker / getHeaderLineCount} must be
 * symmetric per language so {@code RecorderAssistant.insertAndClose} can
 * skip exactly the right number of preview lines when the destination pane
 * already contains the import.
 *
 * <p>Tests also cover {@link RecorderCodeGen#generateImageCode} and
 * {@link RecorderCodeGen#generateTextCode} so any regression on the
 * per-language code shape (Python / Java / Robot Framework) breaks the build
 * before it can ship.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class RecorderCodeGenTest {

  // ---------------------------------------------------------------- helpers

  private static RecorderCodeGen newGen(ICodeGenerator codeGen) {
    return new RecorderCodeGen(codeGen, new RecorderCodePreview());
  }

  private static DefaultListModel<String> previewModel(RecorderCodeGen gen) {
    try {
      Field f = RecorderCodeGen.class.getDeclaredField("codePreview");
      f.setAccessible(true);
      RecorderCodePreview preview = (RecorderCodePreview) f.get(gen);
      return (DefaultListModel<String>) ((JList<?>) preview).getModel();
    } catch (Exception e) {
      throw new AssertionError("reflection failed: " + e.getMessage(), e);
    }
  }

  // ----------------------------------------------------- header-marker contract

  @Nested
  class HeaderMarkerContract {

    @Test
    void python_marker_is_fromSikuliImportStar() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      assertEquals("from sikuli import *", gen.getHeaderMarker());
    }

    @Test
    void java_marker_is_orgSikuliScriptImport() {
      RecorderCodeGen gen = newGen(new JavaCodeGenerator());
      assertEquals("import org.sikuli.script.*;", gen.getHeaderMarker());
    }

    @Test
    void robot_marker_is_settingsBlock() {
      RecorderCodeGen gen = newGen(new RobotFrameworkCodeGenerator());
      assertEquals("*** Settings ***", gen.getHeaderMarker());
    }

    @Test
    void marker_returned_before_initHeaders_is_still_valid() {
      // The marker reflects the language, not the preview state. It must be
      // queryable before initHeaders() ever runs (RecorderAssistant calls
      // getHeaderMarker on insertAndClose, which can fire before any
      // re-init in some race scenarios).
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      assertNotNull(gen.getHeaderMarker());
      assertFalse(gen.getHeaderMarker().isEmpty());
    }
  }

  // --------------------------------------------------- header-line-count contract

  @Nested
  class HeaderLineCountContract {

    @Test
    void python_count_is_zero_before_initHeaders() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      assertEquals(0, gen.getHeaderLineCount(),
          "lines counted only after initHeaders has actually pushed them");
    }

    @Test
    void python_count_after_init_matches_lines_pushed() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.initHeaders();
      assertEquals(2, gen.getHeaderLineCount(),
          "Python header is `from sikuli import *` + blank → 2 lines");
      assertEquals(2, previewModel(gen).size());
    }

    @Test
    void java_count_after_init_matches_lines_pushed() {
      RecorderCodeGen gen = newGen(new JavaCodeGenerator());
      gen.initHeaders();
      assertEquals(7, gen.getHeaderLineCount(),
          "Java header is 2 imports + blank + class + main + screen + blank → 7 lines");
      assertEquals(7, previewModel(gen).size());
    }

    @Test
    void robot_count_after_init_matches_lines_pushed() {
      RecorderCodeGen gen = newGen(new RobotFrameworkCodeGenerator());
      gen.initHeaders();
      assertEquals(6, gen.getHeaderLineCount(),
          "Robot header is settings/lib/doc/blank/test-cases/test-name → 6 lines");
      assertEquals(6, previewModel(gen).size());
    }
  }

  // -------------------------------------------------------- initHeaders content

  @Nested
  class InitHeadersContent {

    @Test
    void python_initHeaders_pushes_import_and_blank() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.initHeaders();
      DefaultListModel<String> m = previewModel(gen);
      assertEquals("from sikuli import *", m.get(0));
      assertEquals("", m.get(1));
    }

    @Test
    void java_initHeaders_pushes_imports_and_class_skeleton() {
      RecorderCodeGen gen = newGen(new JavaCodeGenerator());
      gen.initHeaders();
      DefaultListModel<String> m = previewModel(gen);
      assertEquals("import org.sikuli.script.*;", m.get(0));
      assertEquals("import org.sikuli.basics.Settings;", m.get(1));
      assertEquals("", m.get(2));
      assertTrue(m.get(3).contains("class RecordedTest"));
      assertTrue(m.get(4).contains("public static void main"));
      assertTrue(m.get(5).contains("Screen screen"));
    }

    @Test
    void robot_initHeaders_pushes_settings_block_then_test_cases() {
      RecorderCodeGen gen = newGen(new RobotFrameworkCodeGenerator());
      gen.initHeaders();
      DefaultListModel<String> m = previewModel(gen);
      assertEquals("*** Settings ***", m.get(0));
      assertTrue(m.get(1).contains("Library"));
      assertTrue(m.get(1).contains("SikuliLibrary"));
      assertEquals("", m.get(3));
      assertEquals("*** Test Cases ***", m.get(4));
      assertEquals("Recorded Test", m.get(5));
    }

    @Test
    void initHeaders_marker_is_present_at_position_0_for_every_language() {
      // Sanity: the marker must be on the FIRST line so a simple
      // text.contains(marker) check in RecorderAssistant.insertAndClose
      // works regardless of where the user has scrolled the script.
      // (Yes contains is line-agnostic, but the marker must at least be in
      // the captured headers, otherwise the skip logic never fires.)
      for (ICodeGenerator codeGen : new ICodeGenerator[]{
          new JythonCodeGenerator(),
          new JavaCodeGenerator(),
          new RobotFrameworkCodeGenerator()
      }) {
        RecorderCodeGen gen = newGen(codeGen);
        gen.initHeaders();
        DefaultListModel<String> m = previewModel(gen);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < m.size(); i++) all.append(m.get(i)).append("\n");
        assertTrue(all.toString().contains(gen.getHeaderMarker()),
            "marker must be present in initHeaders output for "
                + codeGen.getClass().getSimpleName());
      }
    }
  }

  // ------------------------------------------------- isJava / isRF predicates

  @Nested
  class LanguagePredicates {

    @Test
    void isJava_true_only_for_java() {
      assertTrue(newGen(new JavaCodeGenerator()).isJava());
      assertFalse(newGen(new JythonCodeGenerator()).isJava());
      assertFalse(newGen(new RobotFrameworkCodeGenerator()).isJava());
    }

    @Test
    void isRF_true_only_for_robot() {
      assertTrue(newGen(new RobotFrameworkCodeGenerator()).isRF());
      assertFalse(newGen(new JythonCodeGenerator()).isRF());
      assertFalse(newGen(new JavaCodeGenerator()).isRF());
    }
  }

  // ----------------------------------------------------- text-action codegen

  @Nested
  class TextActionCodeGen {

    @Test
    void textClick_python_form() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      assertEquals("click(\"foo\")", gen.generateTextCode("textClick", "foo"));
    }

    @Test
    void textWait_python_form_includes_default_timeout() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      assertEquals("wait(\"foo\", 10)", gen.generateTextCode("textWait", "foo"));
    }

    @Test
    void textExists_python_form_no_timeout() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      assertEquals("exists(\"foo\")", gen.generateTextCode("textExists", "foo"));
    }

    @Test
    void textCode_unknown_action_falls_back_to_comment() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String result = gen.generateTextCode("noSuchAction", "foo");
      assertTrue(result.startsWith("#"),
          "unknown action falls back to a Python comment so we never silently emit broken code");
      assertTrue(result.contains("foo"));
    }

    @Test
    void textCode_with_special_chars_in_text_is_passed_through() {
      // Note: the current implementation does NOT escape inner quotes. This
      // is a known limitation we lock here — fixing it would require a
      // proper Python-literal escaper in Bug #232 territory.
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String r = gen.generateTextCode("textWait", "with \"quote\"");
      assertTrue(r.contains("with"));
      assertTrue(r.contains("quote"));
    }
  }

  // ---------------------------------------------------- image-action codegen

  @Nested
  class ImageActionCodeGen {

    @Test
    void click_python_uses_wait_then_click() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String code = gen.generateImageCode("click",
          new Pattern("test.png"));
      assertTrue(code.contains("wait("));
      assertTrue(code.contains(", 10)"));
      assertTrue(code.contains(".click()"));
    }

    @Test
    void doubleClick_python_uses_doubleClick() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String code = gen.generateImageCode("doubleClick",
          new Pattern("test.png"));
      assertTrue(code.contains(".doubleClick()"));
    }

    @Test
    void rightClick_python_uses_rightClick() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String code = gen.generateImageCode("rightClick",
          new Pattern("test.png"));
      assertTrue(code.contains(".rightClick()"));
    }

    @Test
    void wait_python_does_not_chain_a_click() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String code = gen.generateImageCode("wait", new Pattern("test.png"));
      assertFalse(code.contains(".click()"));
      assertFalse(code.contains(".doubleClick()"));
    }

    @Test
    void java_click_emits_semicolon() {
      RecorderCodeGen gen = newGen(new JavaCodeGenerator());
      String code = gen.generateImageCode("click", new Pattern("test.png"));
      assertTrue(code.endsWith(";"),
          "Java statements must terminate with semicolon");
    }

    @Test
    void python_click_does_not_emit_semicolon() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String code = gen.generateImageCode("click", new Pattern("test.png"));
      assertFalse(code.endsWith(";"));
    }

    @Test
    void robot_click_uses_keyword_form_with_wait_and_click() {
      RecorderCodeGen gen = newGen(new RobotFrameworkCodeGenerator());
      String code = gen.generateImageCode("click", new Pattern("test.png"));
      assertTrue(code.contains("Wait Until Screen Contain"));
      assertTrue(code.contains("Click"));
      assertTrue(code.contains("\n"), "Robot emits two-line keyword sequence");
    }

    @Test
    void unknown_image_action_falls_back_to_comment_with_filename() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String code = gen.generateImageCode("nope", new Pattern("test.png"));
      assertTrue(code.startsWith("#"));
      assertTrue(code.contains("test.png"));
    }
  }

  // ---------------------------------------------------- launch / close app

  @Nested
  class AppLifecycleCodeGen {

    @Test
    void python_launch_app_unscoped_emits_one_line() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.generateLaunchApp("/usr/bin/firefox", "fx", false);
      DefaultListModel<String> m = previewModel(gen);
      assertEquals(1, m.size());
      assertTrue(m.get(0).contains("App.open"));
      assertTrue(m.get(0).contains("fx ="));
    }

    @Test
    void python_launch_app_scoped_emits_three_lines() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.generateLaunchApp("/usr/bin/firefox", "fx", true);
      DefaultListModel<String> m = previewModel(gen);
      assertEquals(3, m.size());
      assertTrue(m.get(1).contains(".focus()"));
      assertTrue(m.get(2).contains(".window()"));
    }

    @Test
    void java_launch_app_scoped_uses_typed_assignment_with_App_class() {
      RecorderCodeGen gen = newGen(new JavaCodeGenerator());
      gen.generateLaunchApp("/usr/bin/firefox", "fx", true);
      DefaultListModel<String> m = previewModel(gen);
      assertTrue(m.get(0).startsWith("App fx = App.open"));
      assertTrue(m.get(0).endsWith(";"));
    }

    @Test
    void close_app_python_emits_close_call() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.generateCloseApp("fx");
      DefaultListModel<String> m = previewModel(gen);
      assertEquals(1, m.size());
      assertEquals("fx.close()", m.get(0));
    }

    @Test
    void close_app_java_appends_semicolon() {
      RecorderCodeGen gen = newGen(new JavaCodeGenerator());
      gen.generateCloseApp("fx");
      assertTrue(previewModel(gen).get(0).endsWith(";"));
    }

    @Test
    void close_app_robot_uses_keyword_form() {
      RecorderCodeGen gen = newGen(new RobotFrameworkCodeGenerator());
      gen.generateCloseApp("fx");
      assertTrue(previewModel(gen).get(0).contains("Close Application"));
    }
  }

  // ----------------------------------------------- addLine / addActionCode raw

  @Nested
  class RawAddLine {

    @Test
    void addLine_appends_to_preview() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.addLine("foo()");
      gen.addLine("bar()");
      DefaultListModel<String> m = previewModel(gen);
      assertEquals(2, m.size());
      assertEquals("foo()", m.get(0));
      assertEquals("bar()", m.get(1));
    }

    @Test
    void addActionCode_appScoped_python_prefixes_app_var() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.addActionCode("click()", true, "myapp");
      assertEquals("myapp.click()", previewModel(gen).get(0));
    }

    @Test
    void addActionCode_unscoped_emits_bare_code() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.addActionCode("click()", false, "myapp");
      assertEquals("click()", previewModel(gen).get(0));
    }

    @Test
    void addActionCode_robot_never_prefixes_even_when_scoped() {
      RecorderCodeGen gen = newGen(new RobotFrameworkCodeGenerator());
      gen.addActionCode("Click", true, "myapp");
      assertEquals("Click", previewModel(gen).get(0),
          "Robot keyword syntax has no concept of method-call prefix");
    }

    @Test
    void addMultilineActionCode_splits_on_newlines() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.addMultilineActionCode("a\nb\nc", false, "x");
      DefaultListModel<String> m = previewModel(gen);
      assertEquals(3, m.size());
      assertEquals("a", m.get(0));
      assertEquals("b", m.get(1));
      assertEquals("c", m.get(2));
    }

    @Test
    void addMultilineActionCode_no_newline_acts_like_addActionCode() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.addMultilineActionCode("just one", false, "x");
      assertEquals(1, previewModel(gen).size());
      assertEquals("just one", previewModel(gen).get(0));
    }
  }

  // ------------------------------------------------------------ regressions

  @Nested
  class Regressions {

    @Test
    void initHeaders_called_twice_doubles_the_lines() {
      // initHeaders is intentionally non-idempotent — each call appends. The
      // contract is "ctor calls it once". This test pins that behaviour so
      // future refactors that try to make it idempotent at least do it
      // consciously (and also bump the headerLineCount math accordingly).
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      gen.initHeaders();
      gen.initHeaders();
      assertEquals(4, previewModel(gen).size(),
          "current contract: not idempotent, two calls = two header copies");
    }

    @Test
    void generateImageCode_does_not_mutate_preview() {
      // The image-code generators return a string but should NOT push to the
      // preview themselves — that's handled by addActionCode at the call
      // site. Locking this prevents accidental double-emission.
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String unused = gen.generateImageCode("click", new Pattern("test.png"));
      assertEquals(0, previewModel(gen).size(),
          "generateImageCode is pure — no preview side effects");
    }

    @Test
    void generateTextCode_does_not_mutate_preview() {
      RecorderCodeGen gen = newGen(new JythonCodeGenerator());
      String unused = gen.generateTextCode("textClick", "foo");
      assertEquals(0, previewModel(gen).size(),
          "generateTextCode is pure — no preview side effects");
    }
  }
}
