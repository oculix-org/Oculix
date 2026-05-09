package org.sikuli.support.gui;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.Debug;
import org.sikuli.ide.EditorImageButton;
import org.sikuli.ide.PatternWindow;
import org.sikuli.ide.SikulixIDE;
import org.sikuli.script.Region;
import org.sikuli.script.SX;
import org.sikuli.support.RunTime;
import org.sikuli.support.devices.ScreenDevice;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SXDialogPaneImage extends SXDialogIDE {
  public SXDialogPaneImage(Point where, Object... parms) {
    super("sxidepaneimage", where, parms);
  }

  public SXDialogPaneImage(String res, Point where, Object... parms) {
    super(res, where, parms);
  }

  File image = (File) getOptions().get("image");
  BufferedImage actualImage = null;
  protected BufferedImage scrImage = null;
  Rectangle ideWindow = null;

  private void prepare() {
    ideWindow = getIdeWindow();
    // Resolve the screen hosting the IDE window with progressive fallback:
    //  1. center point — robust if the window has been dragged partially off-screen
    //  2. top-left corner — original behaviour, kept for compat
    //  3. primary screen — last resort instead of a fatal terminate
    final Point center = new Point(
        ideWindow.x + ideWindow.width / 2,
        ideWindow.y + ideWindow.height / 2);
    // Diag log so we can investigate the "should be on a valid screen"
    // FATAL when the IDE window is reportedly NOT moved (HiDPI / multi-monitor /
    // Java 25 bounds quirks). Visible at default Debug.log level.
    final ScreenDevice[] allScreens = ScreenDevice.get();
    final StringBuilder bounds = new StringBuilder();
    for (int i = 0; i < allScreens.length; i++) {
      if (i > 0) bounds.append(", ");
      bounds.append("[").append(i).append("]=").append(allScreens[i].asRectangle());
    }
    Debug.log(2, "SXDialogPaneImage: prepare(): ideWindow=%s, topLeft=%s, center=%s, screens=%s",
        ideWindow, ideWindow.getLocation(), center, bounds);

    ScreenDevice scr = ScreenDevice.getScreenDeviceForPoint(center);
    if (scr == null) {
      Debug.log(2, "SXDialogPaneImage: prepare(): center NOT on any screen, trying top-left");
      scr = ScreenDevice.getScreenDeviceForPoint(ideWindow.getLocation());
    }
    if (scr == null) {
      Debug.log(2, "SXDialogPaneImage: prepare(): top-left NOT on any screen, falling back to primary");
      scr = ScreenDevice.primary();
    }
    if (scr == null) {
      RunTime.terminate(999, "SXDialogPaneImage: prepare(): no screen available for IDE window");
    }
    SikulixIDE.doHide();
    scrImage = scr.capture();
    globalStore.put("screenshot", scrImage);
    actualImage = adjustTo(ideWindow, scrImage);
  }

  public void rename() {
    closeCancel();
    final String image = FilenameUtils.getBaseName(((File) getOptions().get("image")).getAbsolutePath());
    final Region showAt = new Region(getLocation().x, getLocation().y, 1, 1);
    final String name = SX.input("New name for image " + image, "ImageButton :: rename", showAt);
    EditorImageButton.renameImage(name, getOptions());
  }

  public void optimize() {
    closeCancel();
    // Hand off to the legacy SikuliX1 PatternWindow restored for OculiX:
    // multi-tab dialog (Matching preview + TargetOffset) operating on the
    // EditorImageButton stored in the SXDialog options under the "imgBtn" key.
    // The screen capture / adjustTo machinery in prepare() is unused here —
    // PatternWindow takes its own screenshot via takeScreenshot().
    final Object btn = getOptions().get("imgBtn");
    if (!(btn instanceof EditorImageButton)) {
      Debug.error("SXDialogPaneImage.optimize: no EditorImageButton in options (key 'imgBtn')");
      return;
    }
    new PatternWindow(btn);
  }

  public void pattern() {
    closeCancel();
    // "As Pattern" is an in-place promotion — no new window. The image
    // stays the same image; the surrounding code is what becomes a
    // Pattern call. Effect:
    //   - green "85" badge is drawn at the bottom-right of the button
    //     (visible feedback that the promotion happened)
    //   - on next save, the button serializes as
    //     Pattern("foo.png").similar(0.85) instead of "foo.png"
    //   - re-clicking "As Pattern" is idempotent
    // To tune the similarity / offset, the user clicks "Optimize" which
    // is the dedicated multi-tab editor.
    final Object btn = getOptions().get("imgBtn");
    if (!(btn instanceof EditorImageButton)) {
      Debug.error("SXDialogPaneImage.pattern: no EditorImageButton in options (key 'imgBtn')");
      return;
    }
    ((EditorImageButton) btn).promoteToPattern();
  }
}
