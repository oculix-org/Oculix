/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.support.ide.IButton;
import org.sikuli.script.Location;
import org.sikuli.script.Pattern;
import org.sikuli.support.Commons;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.support.gui.SXDialogPaneImage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class EditorImageButton extends JButton implements ActionListener, Serializable, MouseListener {

  Map<String, Object> options;

  public Map<String, Object> getOptions() {
    return options;
  }

  public String getFilename() {
    return ((File) options.get(IButton.FILE)).getAbsolutePath();
  }

  int MAXHEIGHT = getThumbHeight();

  private static int getThumbHeight() {
    try {
      return PreferencesUser.get().getDefaultThumbHeight();
    } catch (Exception e) {
      return 50; // fallback
    }
  }

  BufferedImage thumbnail;

  public BufferedImage getThumbnail() {
    return thumbnail;
  }

  public EditorImageButton() {
  }

  public EditorImageButton(Map<String, Object> options) {
    this.options = options;
    thumbnail = createThumbnailImage((File) this.options.get(IButton.FILE), MAXHEIGHT);

    init();
  }

  public EditorImageButton(File imgFile) {
    thumbnail = createThumbnailImage(imgFile, MAXHEIGHT);
    options = new HashMap<>();
    options.put(IButton.FILE, imgFile);
    options.put(IButton.TEXT, "\"" + codeName(imgFile.getAbsolutePath()) + "\"");

    init();
  }

  public EditorImageButton(Pattern pattern) {
    thumbnail = createThumbnailImage(pattern, MAXHEIGHT);
    options = new HashMap<>();
    File imgFile = pattern.getImage().file();
    options.put(IButton.FILE, imgFile);
    options.put(IButton.TEXT, "\"" + codeName(imgFile.getAbsolutePath()) + "\"");
    options.put(IButton.PATT, pattern);

    init();
  }

  private void init() {
    setIcon(new ImageIcon(thumbnail));
    setButtonText();

    setMargin(new Insets(0, 0, 0, 0));
    setBorderPainted(false);
    setContentAreaFilled(false);
    setOpaque(false);
    setFocusPainted(false);
    setCursor(new Cursor(Cursor.HAND_CURSOR));
    addActionListener(this);
    addMouseListener(this);
  }

  // When the LaF changes (theme toggle), the new ButtonUI resets contentAreaFilled
  // and opaque back to defaults, which makes FlatLightLaf paint an opaque white
  // background over the icon. Keep the button fully transparent so only the cached
  // thumbnail + custom paint() overlay are rendered (issue #165).
  @Override
  public void updateUI() {
    super.updateUI();
    if (thumbnail != null) {
      setIcon(new ImageIcon(thumbnail));
    }
    setMargin(new Insets(0, 0, 0, 0));
    setBorderPainted(false);
    setContentAreaFilled(false);
    setOpaque(false);
    setFocusPainted(false);
  }

  // Build a fresh clone of this button carrying the same visible state but
  // with a fresh ButtonUI installed under the active LaF. Called by
  // EditorPane.afterThemeChange() to replace every embedded image button
  // after a theme toggle so the Flat ButtonUI defaults don't leave the
  // thumbnails invisible or un-clickable. The clone takes the state the
  // button has now (pattern or image, similarity, exact, offset), which the
  // menu and Optimize change after the button was built.
  public EditorImageButton cloneForRefresh(EditorPane pane) {
    if (options == null) return null;
    Object file = options.get(IButton.FILE);
    if (!(file instanceof File)) return null;
    EditorImageButton fresh = new EditorImageButton((File) file);
    fresh._isPattern = _isPattern;
    fresh._similar = _similar;
    fresh._exact = _exact;
    fresh._offset = _offset == null ? null : new Location(_offset.x, _offset.y);
    fresh.rebuildText();
    fresh.setButtonText();
    return fresh;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final EditorImageButton source = (EditorImageButton) e.getSource();
    handlePopup(null);
  }

  private boolean closeIfVisible(SXDialog popup) {
    if (popup != null && popup.isVisible()) {
      popup.closeCancel();
      return true;
    }
    return false;
  }

  private void handlePopup(MouseEvent me) {
    if (closeIfVisible(popwin)) {
      return;
    }
    handlePreview();
  }

  private SXDialogPaneImage popwin = null;

  private void handlePreview() {
    Point where = getLocationOnScreen();
    popwin = new SXDialogPaneImage(where, new String[]{"image", "imgBtn"}, options.get(IButton.FILE), this);
    popwin.run();
  }

  @Override
  public Point getLocationOnScreen() {
    return super.getLocationOnScreen();
  }

  @Override
  public void mouseEntered(MouseEvent me) {
    // Step 1 (RaiMan #209): hover surfaces filename only via the Swing
    // tooltip set in setOptionsAndConfigure(). No custom Pattern() preview
    // popup — keeps the inline button visually quiet.
  }

  @Override
  public void mouseExited(MouseEvent me) {
  }

  @Override
  public void mousePressed(MouseEvent me) {
    if (me.isPopupTrigger()) {
      handlePopup(me);
    }
  }

  @Override
  public void mouseReleased(MouseEvent me) {
    if (me.isPopupTrigger()) {
      handlePopup(me);
    }
  }

  @Override
  public void mouseClicked(MouseEvent me) {
  }

  private BufferedImage createThumbnailImage(Pattern pattern, int maxHeight) {
    //TODO Pattern thumbnail
    return createThumbnailImage(pattern.getImage().file(), maxHeight);
  }

  private BufferedImage createThumbnailImage(File imgFile, int maxHeight) {
    BufferedImage img = null;
    try {
      img = ImageIO.read(imgFile);
    } catch (IOException e) {
    }
    if (img == null) {
      try {
        img = ImageIO.read(SikulixIDE.class.getResource("/icons/sxcapture.png"));
      } catch (Exception e) {
        Commons.terminate(999, "EditorImageButton: createThumbnailImage: possible? %s", e.getMessage());
      }
    }
    int w = img.getWidth();
    int h = img.getHeight();
    if (maxHeight == 0 || maxHeight >= h) {
      return img;
    }
    float _scale = (float) maxHeight / h;
    w *= _scale;
    h *= _scale;
    h = (int) h;
    // Use ARGB so the thumbnail has an alpha channel and survives LaF changes
    // (opaque TYPE_INT_RGB caused icons to vanish visually after a theme toggle - issue #165).
    BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = thumb.createGraphics();
    g2d.drawImage(img, 0, 0, w, h, null);
    g2d.dispose();
    return thumb;
  }

  /**
   * SikuliX historical default similarity. "As Pattern" pins the button to
   * this value so the resulting code reads as the canonical
   * {@code Pattern("name.png")} (no explicit {@code .similar(...)} suffix
   * since the value matches the API default). The badge always paints once
   * a button has been promoted, regardless of the similar value, so the
   * user always sees feedback that the promotion took effect — the prior
   * choice of 0.85-as-default existed only to force the badge to render
   * under a stricter "differ from default" rule that has been removed.
   */
  private static final double AS_PATTERN_DEFAULT_SIMILAR = 0.7;
  private static final double DEFAULT_SIMILAR = 0.7;

  /**
   * Track whether the user has explicitly promoted this image to a Pattern
   * via the "As Pattern" menu action. When true, {@link #toString()} writes
   * the Pattern(...).similar(...) form instead of the bare quoted filename,
   * and {@link #paint(Graphics)} paints the green similarity badge.
   */
  private boolean _isPattern = false;
  private double _similar = DEFAULT_SIMILAR;
  private boolean _exact = false;
  private Location _offset = null;

  /**
   * True once the user has explicitly promoted this image to a Pattern via
   * the right-click menu or via Optimize Apply. Read by
   * {@link org.sikuli.support.gui.SXDialogPaneImageMenu} to hide the
   * "As Pattern" entry from the menu when the promotion already happened —
   * a stale entry would be confusing dead UI.
   */
  public boolean isPromotedToPattern() {
    return _isPattern;
  }

  /**
   * Promote this plain image button to a Pattern at the default similarity
   * {@link #AS_PATTERN_DEFAULT_SIMILAR} (0.7). Visible feedback: green
   * badge ("70") drawn at the bottom-right corner. Code-level effect: next
   * File ▸ Save serializes the surrounding call as
   * {@code Pattern("foo.png")} instead of {@code "foo.png"}, with no
   * {@code .similar(...)} since the value is the default.
   *
   * <p>Idempotent — re-clicking "As Pattern" on an already-promoted button
   * is a no-op.
   */
  public void promoteToPattern() {
    if (_isPattern) return;
    _isPattern = true;
    _similar = AS_PATTERN_DEFAULT_SIMILAR;
    rebuildText();
    setButtonText();
    repaint();
  }

  /**
   * Recompute the IButton.TEXT serialisation from the current pattern
   * state. Mirrors EditorPatternButton.toString() so both button types
   * round-trip identically through File ▸ Save.
   */
  private void rebuildText() {
    if (options == null) options = new HashMap<>();
    if (options.get(IButton.FILE) == null) return;
    final String name = codeName(((File) options.get(IButton.FILE)).getAbsolutePath());
    if (!_isPattern && (_offset == null || (_offset.x == 0 && _offset.y == 0))) {
      options.put(IButton.TEXT, "\"" + name + "\"");
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Pattern(\"").append(name).append("\")");
    if (_exact) {
      sb.append(".exact()");
    } else if (_similar > 0 && _similar != DEFAULT_SIMILAR) {
      sb.append(String.format(java.util.Locale.ENGLISH, ".similar(%.2f)", _similar));
    }
    if (_offset != null && (_offset.x != 0 || _offset.y != 0)) {
      sb.append(".targetOffset(").append(_offset.x).append(",").append(_offset.y).append(")");
    }
    options.put(IButton.TEXT, sb.toString());
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setColor(new Color(0, 128, 128, 128));
    g2d.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 5, 5);
    // The badge always renders once the button has been promoted to a
    // Pattern, regardless of whether the similar value matches the
    // historical default 0.70. It serves as the visual marker
    // "this is a Pattern, not a plain image" — same role as the green
    // dot in legacy SikuliX. Without this, an As Pattern click that
    // landed on default 0.70 produced no visible change → users
    // thought the action did nothing.
    if (_isPattern) {
      drawSimilarBadge(g2d);
    }
  }

  /**
   * Bottom-right green badge with the similarity percentage (e.g. "85").
   * Same visual language as {@link EditorPatternButton#drawDecoration} so
   * users see a consistent indicator regardless of whether the button was
   * captured by the recorder (EditorImageButton) or by the legacy capture
   * path (EditorPatternButton).
   */
  private void drawSimilarBadge(Graphics2D g2d) {
    final String label = String.format("%d", (int) (_similar * 100));
    g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    final FontMetrics fm = g2d.getFontMetrics();
    final int textW = fm.stringWidth(label);
    final int textH = fm.getAscent();
    final int padding = 3;
    final int badgeW = textW + padding * 2;
    final int badgeH = textH + padding * 2;
    final int x = getWidth() - badgeW - 1;
    final int y = getHeight() - badgeH - 1;
    g2d.setColor(new Color(0, 128, 0, 180));
    g2d.fillRoundRect(x, y, badgeW, badgeH, 4, 4);
    g2d.setColor(Color.WHITE);
    g2d.drawString(label, x + padding, y + padding + textH - 1);
  }

  @Override
  public String toString() {
    if (options == null || options.get(IButton.TEXT) == null) {
      return "";
    }
    return (String) options.get(IButton.TEXT);
  }

  /**
   * The code of this image with every value written out, similarity
   * included even at its default, so that a person can edit it in place.
   */
  public String toEditableCode() {
    if (options == null || options.get(IButton.FILE) == null) {
      return toString();
    }
    return editableCode(codeName(((File) options.get(IButton.FILE)).getAbsolutePath()), _exact, _similar, _offset);
  }

  static String editableCode(String imgName, boolean exact, double similarity, Location offset) {
    StringBuilder sb = new StringBuilder("Pattern(\"").append(imgName).append("\")");
    if (exact) {
      sb.append(".exact()");
    } else {
      sb.append(String.format(java.util.Locale.ENGLISH, ".similar(%.2f)", similarity));
    }
    if (offset != null && (offset.x != 0 || offset.y != 0)) {
      sb.append(".targetOffset(").append(offset.x).append(",").append(offset.y).append(")");
    }
    return sb.toString();
  }

  public String info() {
    if (options == null || options.get(IButton.FILE) == null) {
      return "";
    }
    final String name = FilenameUtils.getBaseName(((File) options.get(IButton.FILE)).getAbsolutePath());
    return String.format("%s", name);
  }

  void setButtonText() {
    if (options != null && options.get(IButton.FILE) != null) {
      setToolTipText(((File) options.get(IButton.FILE)).getName());
    } else {
      setToolTipText(info());
    }
  }

  public static void renameImage(String name, Map<String, Object> options) {
    Commons.startLog(3, "renameImage: called name=%s keys=%s", name, options.keySet());
    if (name == null || name.trim().isEmpty()) {
      Commons.startLog(3, "renameImage: empty name, skip");
      return;
    }
    // The dialog (SXDialogPaneImage) builds its own options map using the
    // string "image" as key (see SXDialog.setOptions + the call site at
    // EditorImageButton:171 which passes new String[]{"image"} as the key
    // array). EditorImageButton internally uses IButton.FILE = "FILE".
    // Try both so we work regardless of which caller invoked us.
    File oldFile = (File) options.get("image");
    if (oldFile == null) {
      oldFile = (File) options.get(IButton.FILE);
    }
    if (oldFile == null) {
      Commons.error("renameImage: no source file in options (keys=%s)",
          options.keySet());
      return;
    }
    Commons.startLog(3, "renameImage: oldFile=%s exists=%s", oldFile, oldFile.exists());
    if (!oldFile.exists()) {
      Commons.error("renameImage: source file does not exist on disk: %s", oldFile);
      return;
    }
    String newBaseName = name.trim();
    if (!newBaseName.toLowerCase().endsWith(".png")
        && !newBaseName.toLowerCase().endsWith(".jpg")
        && !newBaseName.toLowerCase().endsWith(".jpeg")) {
      newBaseName += "." + FilenameUtils.getExtension(oldFile.getName()).toLowerCase();
    }
    File newFile = new File(oldFile.getParentFile(), newBaseName);
    Commons.startLog(3, "renameImage: %s -> %s", oldFile.getName(), newFile.getName());
    if (newFile.equals(oldFile)) {
      Commons.startLog(3, "renameImage: same name, skip");
      return;
    }
    boolean overwritten = newFile.exists();
    try {
      java.nio.file.Files.move(oldFile.toPath(), newFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Commons.startLog(3, "renameImage: move OK, newFile exists=%s", newFile.exists());
    } catch (IOException e) {
      Commons.error("renameImage: cannot move %s -> %s: %s",
          oldFile, newFile, e.getMessage());
      return;
    }
    // Update the dialog's own map (caller may inspect it after this returns).
    options.put(IButton.FILE, newFile);
    options.put("image", newFile);
    // The dialog stashed the originating EditorImageButton instance in its
    // options under "imgBtn" — see the construction at EditorImageButton:161
    // and :171, where the keys array {"image", "imgBtn"} pairs file (parm0)
    // with this button (parm1) via SXDialog.setOptions naming.
    // The button's own internal `options` map is a SEPARATE Map from the
    // dialog's — without mirroring the rename onto it, the next save would
    // still serialize the old filename and the tooltip would still show
    // the old name.
    Object btnObj = options.get("imgBtn");
    if (btnObj instanceof EditorImageButton) {
      EditorImageButton btn = (EditorImageButton) btnObj;
      btn.options.put(IButton.FILE, newFile);
      // IButton.TEXT carries the textual form ("filename.png" with quotes)
      // that the button serializes back to the script when the document is
      // saved. Without this update the next File ▸ Save writes the OLD name
      // even though the .png on disk is now the NEW name.
      btn.options.put(IButton.TEXT, "\"" + newFile.getName() + "\"");
      btn.setButtonText();
      btn.repaint();
      Commons.startLog(3, "renameImage: button instance updated (tooltip + serialization)");
    } else {
      Commons.startLog(3, "renameImage: no button instance in options.parm1 (kept dialog-only update)");
    }
    SikulixIDE.get().reparseOnRenameImage(oldFile.getAbsolutePath(),
        newFile.getAbsolutePath(), overwritten);
    Commons.startLog(3, "renameImage: reparseOnRenameImage called");
  }

  //imgBtn.setImage(filename);
  public void setImage(String fname) {

  }

  /**
   * Apply matching parameters from the Optimize / PatternWindow Apply button.
   * Returns true if any value actually changed (PatternWindow uses this for
   * its dirty marker). Called by PatternWindow.actionPerformed at line 304.
   *
   * <p>Side effects: pins the button as a Pattern (so the badge paints + the
   * code serialises as Pattern("foo.png").similar(...)), refreshes the
   * IButton.TEXT serialisation, repaints. {@code numM} is currently ignored —
   * EditorImageButton has no Pattern instance to attach numMatches to, but
   * keeping the signature lets PatternWindow stay agnostic of which button
   * type it edits.
   */
  public boolean setParameters(boolean exact, double sim, int numM) {
    boolean dirty = false;
    if (_exact != exact) {
      _exact = exact;
      dirty = true;
    }
    if (Math.abs(_similar - sim) > 1e-6) {
      _similar = sim;
      dirty = true;
    }
    if (dirty || !_isPattern) {
      _isPattern = true;
      rebuildText();
      setButtonText();
      repaint();
      dirty = true;
    }
    return dirty;
  }

  /**
   * Apply target offset from PatternWindow's TargetOffset tab. Like
   * setParameters, this also pins the button as a Pattern so the resulting
   * code is Pattern("foo.png").targetOffset(x,y).
   */
  public boolean setTargetOffset(Location offset) {
    boolean dirty;
    if (offset == null) {
      dirty = (_offset != null);
      _offset = null;
    } else if (_offset == null) {
      _offset = new Location(offset.x, offset.y);
      dirty = true;
    } else {
      dirty = (_offset.x != offset.x || _offset.y != offset.y);
      _offset = new Location(offset.x, offset.y);
    }
    if (dirty) {
      _isPattern = true;
      rebuildText();
      repaint();
    }
    return dirty;
  }

  public PatternWindow getWindow() {
    return null;
  }

  /** The opposite of {@link #promoteToPattern()}: the image goes back to its bare file name in the code. */
  public void asImage() {
    resetParameters();
  }

  /**
   * The image as the code must name it: relative to the folder of the script that carries
   * it, so an image kept in a subfolder is still found after the code is rewritten.
   */
  protected String codeName(String imageFile) {
    return EditorPane.nameForCode(scriptFolder(), imageFile);
  }

  /** The folder of the script being edited, or null when it cannot be told. */
  static File scriptFolder() {
    SikulixIDE ide = SikulixIDE.get();
    SikulixIDE.PaneContext context = ide == null ? null : ide.getActiveContext();
    return context == null ? null : context.getFolder();
  }

  /**
   * Reset to a plain image (no Pattern wrap). Used by PatternWindow's
   * cancel/reset path so the user can back out of an unwanted promotion.
   */
  public void resetParameters() {
    _isPattern = false;
    _exact = false;
    _similar = DEFAULT_SIMILAR;
    _offset = null;
    rebuildText();
    setButtonText();
    repaint();
  }
}
