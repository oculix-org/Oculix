package org.sikuli.support.gui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The dialog engine rebuilt from its text templates: layout, icons, variables, line continuation. */
class SXDialogTest {

  @BeforeAll
  static void needsADisplay() {
    assumeFalse(GraphicsEnvironment.isHeadless(), "SXDialog is a JFrame");
  }

  /** A dialog that never shows and counts the actions its lines call. */
  static class Harness extends SXDialog {
    final Map<String, Integer> calls = new HashMap<>();

    Harness(String res, Object... parms) {
      super(res, new Point(100, 100), parms);
    }

    @Override
    public void run() {
    }

    public void rename() {
      hit("rename");
    }

    public void optimize() {
      hit("optimize");
    }

    public void pattern() {
      hit("pattern");
    }

    public void unpattern() {
      hit("unpattern");
    }

    public void code() {
      hit("code");
    }

    private void hit(String name) {
      calls.merge(name, 1, Integer::sum);
    }

    int hits(String name) {
      return calls.getOrDefault(name, 0);
    }

    /** One line per item, boxes flattened, with everything the layout decides. */
    String describeLayout() {
      StringBuilder out = new StringBuilder();
      describe(paneBox, "", out);
      return out.toString();
    }

    private void describe(BoxItem box, String indent, StringBuilder out) {
      for (BasicItem item : box.items) {
        out.append(indent).append(item.getClass().getSimpleName()).append(' ').append(item.title())
            .append(' ').append(item.rect()).append(" active=").append(item.active())
            .append(" comp=").append(item.comp() != null).append('\n');
        if (item instanceof BoxItem) {
          describe((BoxItem) item, indent + "  ", out);
        }
      }
    }

    IconItem icon(String command) {
      for (BasicItem item : dialogLines) {
        if (item instanceof IconItem && ((IconItem) item).command.equals(command)) {
          return (IconItem) item;
        }
      }
      return null;
    }

    List<IconItem> icons() {
      List<IconItem> found = new ArrayList<>();
      for (BasicItem item : dialogLines) {
        if (item instanceof IconItem) {
          found.add((IconItem) item);
        }
      }
      return found;
    }

    HtmlItem html() {
      for (BasicItem item : dialogLines) {
        if (item instanceof HtmlItem) {
          return (HtmlItem) item;
        }
      }
      return null;
    }

    List<String> textTitles() {
      List<String> titles = new ArrayList<>();
      for (BasicItem item : dialogLines) {
        if (item.getClass() == TextItem.class && !item.isBoxEnd()) {
          titles.add(item.title().strip());
        }
      }
      return titles;
    }

    boolean anyBoxHolds(BasicItem wanted) {
      return holds(paneBox, wanted);
    }

    private boolean holds(BoxItem box, BasicItem wanted) {
      for (BasicItem item : box.items) {
        if (item == wanted || (item instanceof BoxItem && holds((BoxItem) item, wanted))) {
          return true;
        }
      }
      return false;
    }
  }

  static void click(Component comp) {
    comp.dispatchEvent(new MouseEvent(comp, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
        0, 3, 3, 1, false, MouseEvent.BUTTON1));
  }

  static MouseEvent clickOn(Component comp) {
    return new MouseEvent(comp, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
        0, 3, 3, 1, false, MouseEvent.BUTTON1);
  }

  @Nested
  class WhenRebuilt {
    @Test
    void threeRebuildsKeepTheLayout() {
      Harness dialog = new Harness("sxtesticons");
      String first = dialog.describeLayout();
      dialog.reRun();
      dialog.reRun();
      dialog.reRun();
      assertEquals(first, dialog.describeLayout());
    }

    @Test
    void htmlIsWrappedOnce() {
      Harness dialog = new Harness("sxtesticons");
      dialog.reRun();
      dialog.reRun();
      assertEquals("<html>first<br>second", ((JLabel) dialog.html().comp()).getText());
    }

    @Test
    void aDroppedIconLeavesEveryBox() {
      Harness dialog = new Harness("sxtesticons");
      SXDialog.IconItem gone = dialog.icon("pattern");
      assertNotNull(gone);
      dialog.drop("pattern");
      dialog.reRun();
      assertNull(dialog.icon("pattern"));
      assertFalse(dialog.anyBoxHolds(gone));
      assertNotNull(dialog.icon("unpattern").comp());
    }
  }

  @Nested
  class WhenAnIconIsDisabled {
    @Test
    void aClickOnItDoesNothing() {
      Harness dialog = new Harness("sxtesticons");
      dialog.enable("optimize", false);
      dialog.reRun();
      click(dialog.icon("optimize").comp());
      assertEquals(0, dialog.hits("optimize"));
      click(dialog.icon("rename").comp());
      assertEquals(1, dialog.hits("rename"));
    }

    @Test
    void reenablingBringsBackTheClickAndTheLayout() {
      Harness dialog = new Harness("sxtesticons");
      String first = dialog.describeLayout();
      dialog.enable("optimize", false);
      dialog.reRun();
      dialog.enable("optimize", true);
      dialog.reRun();
      assertEquals(first, dialog.describeLayout());
      click(dialog.icon("optimize").comp());
      assertEquals(1, dialog.hits("optimize"));
    }
  }

  @Test
  void everyIconCallsTheMethodOfItsCommand() {
    Harness dialog = new Harness("sxtesticons");
    for (SXDialog.IconItem icon : dialog.icons()) {
      icon.mouseClick(clickOn(icon.comp()));
      assertEquals(1, dialog.hits(icon.command), icon.command);
    }
  }

  @Nested
  class WhenTheTemplateIsRead {
    @Test
    void continuedLinesMakeOneLine() {
      Harness dialog = new Harness("sxtestcontinue");
      assertEquals(List.of("helloworldagain", "next"), dialog.textTitles());
    }

    @Test
    void anOptionLineIsReadable() {
      assertDoesNotThrow(() -> new Harness("sxtestoption"));
    }

    @Test
    void aFragmentOfAFeatureNameIsPlainText() {
      Harness dialog = new Harness("sxtesticons");
      assertEquals(SXDialog.FEATURE_PLAIN, dialog.getFeature("#co; x"));
    }
  }

  @Nested
  class WhenVariablesExpand {
    final Harness dialog = new Harness("sxtesticons", new String[]{"a", "b", "c"}, "{a}", "{c}", "done");

    @Test
    void aCascadeIsFollowed() {
      assertEquals("done", dialog.replaceVariables("{b}"));
    }

    @Test
    void aCycleStopsAndKeepsTheLine() {
      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals("{a}", dialog.replaceVariables("{a}")));
    }

    @Test
    void anOrphanBraceIsLeftAlone() {
      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
        assertEquals("{ open done", dialog.replaceVariables("{ open {c}"));
        assertEquals("done open {", dialog.replaceVariables("{c} open {"));
      });
    }

    @Test
    void manyVariablesOnOneLine() {
      String[] keys = new String[150];
      Object[] parms = new Object[151];
      StringBuilder line = new StringBuilder();
      for (int i = 0; i < 150; i++) {
        keys[i] = "v" + i;
        parms[i + 1] = "x";
        line.append("{v").append(i).append('}');
      }
      parms[0] = keys;
      Harness many = new Harness("sxtesticons", parms);
      assertEquals("x".repeat(150), many.replaceVariables(line.toString()));
    }
  }

  @Test
  void theAutoCloseDelayIsDroppedWhenClosed() {
    Harness dialog = new Harness("sxtesticons");
    dialog.setAutoClose(5);
    dialog.startAutoClose();
    assertTrue(dialog.autoCloseArmed());
    dialog.startAutoClose();
    assertTrue(dialog.autoCloseArmed());
    dialog.close();
    assertFalse(dialog.autoCloseArmed());
  }

  @Test
  void placingTheDialogLeavesItsAnchorAlone() {
    Harness dialog = new Harness("sxtesticons");
    dialog.run(new Point(300, 300));
    Point first = dialog.runAt();
    Point second = dialog.runAt();
    assertEquals(first, second);
    assertTrue(first.x < 300 && first.y < 300, first.toString());
  }

  @Test
  void adjustToKeepsTheSizeOfTheTwoStepResize() {
    Harness dialog = new Harness("sxtesticons");
    Rectangle window = new Rectangle(0, 0, 700, 500);
    int[][] sizes = {{1920, 1080}, {2560, 1440}, {1366, 768}, {1080, 1920}};
    for (int[] size : sizes) {
      BufferedImage img = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
      Dimension legacy = legacyAdjustTo(dialog, window, img);
      BufferedImage now = dialog.adjustTo(window, img);
      assertTrue(Math.abs(now.getWidth() - legacy.width) <= 1, size[0] + "x" + size[1] + " width " + now.getWidth() + " vs " + legacy.width);
      assertTrue(Math.abs(now.getHeight() - legacy.height) <= 1, size[0] + "x" + size[1] + " height " + now.getHeight() + " vs " + legacy.height);
    }
  }

  /** The resize as it was done before: to the width first, then again to the height if too tall. */
  static Dimension legacyAdjustTo(SXDialog dialog, Rectangle where, BufferedImage img) {
    BufferedImage actual = dialog.new ImageItem(img).resize((int) where.getWidth()).get();
    final double height = actual.getHeight();
    final double whereH = where.getHeight() - 50;
    if (height > whereH) {
      actual = dialog.new ImageItem(actual).resize(whereH / height).get();
    }
    return new Dimension(actual.getWidth(), actual.getHeight());
  }
}
