/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import java.awt.*;
import javax.swing.*;

import net.miginfocom.swing.MigLayout;
import org.sikuli.ide.theme.OculixColors;
import org.sikuli.support.Commons;
import org.sikuli.support.ide.SikuliIDEI18N;

import java.util.Date;

/**
 * The bottom row of the IDE window. It holds a short message on the left, the
 * three engine indicators in the centre, and the caret position on the right,
 * so the sidebar can drop the "État" section without losing any information.
 */
public class SikuliIDEStatusBar extends JPanel {

  private static final Color GREEN = new Color(0x3D, 0xDB, 0xA4);
  private static final Color RED = new Color(0xFF, 0x6B, 0x6B);
  private static final Color CYAN = new Color(0x63, 0xB8, 0xFF);

  private final JLabel _lblMsg = smallLabel();
  private final EngineIndicator paddle = new EngineIndicator("PaddleOCR");
  private final EngineIndicator tesseract = new EngineIndicator("Tesseract");
  private final EngineIndicator java = new EngineIndicator("Java");
  private final JLabel _lblCaretPos = smallLabel();

  private String currentContentType = "???";
  private int currentRow;
  private int currentCol;
  private long starting;

  public SikuliIDEStatusBar() {
    setLayout(new MigLayout("insets 2 10 2 10, fillx", "[]push[][]12[][]12[][]12[]12[]", "[]"));
    setPreferredSize(new Dimension(10, 24));

    add(_lblMsg);
    add(paddle.dot);
    add(paddle.label);
    add(tesseract.dot);
    add(tesseract.label);
    add(java.dot);
    add(java.label);
    add(new JSeparator(SwingConstants.VERTICAL), "growy");
    add(_lblCaretPos);

    setCaretPosition(1, 1);
    resetMessage();
    refreshEngineStatus();
  }

  public void setType(String contentType) {
    if (contentType == null) return;
    currentContentType = contentType.replaceFirst(".*?\\/", "");
    if (currentContentType.equals("null")) currentContentType = "text";
    setCaretPosition(-1, 0);
  }

  public void setCaretPosition(int row, int col) {
    if (row > -1) {
      currentRow = row;
      currentCol = col;
    }
    _lblCaretPos.setText(String.format("[%s]  R: %d  C: %d", currentContentType, currentRow, currentCol));
    if (starting > 0 && new Date().getTime() - starting > 3000) resetMessage();
  }

  public void setMessage(String text) {
    _lblMsg.setText(text);
    repaint();
    starting = new Date().getTime();
  }

  public void resetMessage() {
    setMessage("OculiX " + Commons.getSXVersionShort() + "  |  Java " + Commons.getJavaVersion());
    starting = 0;
  }

  /** Reads the OCR engine every call because Paddle can go up and down at runtime. */
  public void refreshEngineStatus() {
    boolean paddleOk = false;
    try {
      Class<?> engineClass = Class.forName("com.sikulix.ocr.PaddleOCREngine");
      Object engine = engineClass.getDeclaredConstructor().newInstance();
      paddleOk = (boolean) engineClass.getMethod("isAvailable").invoke(engine);
    } catch (Exception ignored) {}
    paddle.set(paddleOk ? GREEN : RED,
        paddleOk ? _I("sidebarStatusOk") : _I("sidebarStatusOffline"),
        paddleOk ? _I("statusPaddleTipOn") : _I("statusPaddleTipOff"));
    tesseract.set(GREEN, _I("sidebarStatusBuiltIn"), _I("statusTesseractTip"));
    java.set(CYAN, System.getProperty("java.version"),
        _I("statusJavaTip") + " " + System.getProperty("java.runtime.version")
            + " · " + System.getProperty("java.vendor"));

    String caretTip = _I("statusCaretTip");
    _lblCaretPos.setToolTipText(caretTip);
    _lblMsg.setToolTipText(_I("statusMessageTip"));
  }

  /** Backwards-compatible alias for callers that still say "OCR". */
  public void refreshOcrStatus() {
    refreshEngineStatus();
  }

  private static JLabel smallLabel() {
    JLabel l = new JLabel();
    l.setFont(UIManager.getFont("Label.font").deriveFont(11.0f));
    return l;
  }

  private static String _I(String key) {
    try { return SikuliIDEI18N._I(key); } catch (Exception e) { return key; }
  }

  /** A coloured 8 px round dot and its label, drawn together so wrapping stays sane. */
  private static final class EngineIndicator {
    final Dot dot = new Dot();
    final JLabel label = smallLabel();

    EngineIndicator(String name) {
      label.setText(name);
    }

    void set(Color colour, String value, String tip) {
      dot.color = colour;
      dot.repaint();
      String name = label.getText().split(" ")[0];
      label.setText(name + "  " + value);
      label.setToolTipText(tip);
      dot.setToolTipText(tip);
    }
  }

  private static final class Dot extends JComponent {
    Color color = OculixColors.OX_INK_500;

    Dot() {
      setPreferredSize(new Dimension(10, 10));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(color);
      g2.fillOval(1, (getHeight() - 8) / 2, 8, 8);
      g2.dispose();
    }
  }
}
