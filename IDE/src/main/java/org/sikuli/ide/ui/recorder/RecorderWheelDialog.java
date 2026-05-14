/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Modal dialog for configuring a Wheel action. Shows the captured image
 * with an interactive crosshair for offset selection, direction radio,
 * step count spinner and a live code preview.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class RecorderWheelDialog extends JDialog {

  private final BufferedImage capture;
  private final String imageName;

  private JRadioButton rbUp, rbDown;
  private JSpinner spinnerSteps;
  private ImagePanel imagePanel;
  private JLabel offsetLabel;
  private JTextArea previewArea;
  private JButton okBtn, cancelBtn;

  private int offsetX = 0;
  private int offsetY = 0;
  private String result = null;

  public RecorderWheelDialog(Dialog parent, BufferedImage capture, String imagePath) {
    super(parent, _I("recorderWheelDlgTitle"), true);
    this.capture = capture;
    this.imageName = new File(imagePath).getName();
    setResizable(false);
    buildUI();
    updatePreview();
    pack();
    setLocationRelativeTo(parent);
  }

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout("wrap 1, insets 16, gap 8", "[grow, fill]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    // Image preview with crosshair
    JLabel lblImg = new JLabel(_I("recorderWheelLblRegion"));
    lblImg.setFont(UIManager.getFont("small.font"));
    lblImg.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblImg);

    imagePanel = new ImagePanel();
    // Size the preview to match the captured image, with a minimum of 380x240
    // and a maximum of 1200x800. Small images are scaled up, large images are scaled down.
    int minW = 380;
    int minH = 240;
    int maxW = 1200;
    int maxH = 800;
    int imgW = capture.getWidth();
    int imgH = capture.getHeight();
    double scaleUp = Math.max((double) minW / imgW, (double) minH / imgH);
    double scaleDown = Math.min((double) maxW / imgW, (double) maxH / imgH);
    double scale;
    if (imgW < minW || imgH < minH) {
      scale = scaleUp; // Image too small, scale up
    } else if (imgW > maxW || imgH > maxH) {
      scale = scaleDown; // Image too big, scale down
    } else {
      scale = 1.0; // Image fits naturally
    }
    int previewW = (int) (imgW * scale);
    int previewH = (int) (imgH * scale);
    imagePanel.setPreferredSize(new Dimension(previewW, previewH));
    imagePanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));
    content.add(imagePanel, "align center");

    offsetLabel = new JLabel(_I("recorderWheelLblOffset"));
    offsetLabel.setFont(UIManager.getFont("small.font"));
    offsetLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(offsetLabel);

    content.add(new JSeparator(), "growx, gaptop 4");

    // Direction
    JPanel dirPanel = new JPanel(new MigLayout("insets 0, gap 12", "[][]"));
    dirPanel.setOpaque(false);
    JLabel lblDir = new JLabel(_I("recorderWheelLblDirection"));
    lblDir.setFont(UIManager.getFont("defaultFont"));
    dirPanel.add(lblDir);
    rbDown = new JRadioButton("Down", true);
    rbUp = new JRadioButton("Up");
    ButtonGroup grp = new ButtonGroup();
    grp.add(rbDown);
    grp.add(rbUp);
    rbDown.addActionListener(e -> updatePreview());
    rbUp.addActionListener(e -> updatePreview());
    JPanel radios = new JPanel(new MigLayout("insets 0, gap 8"));
    radios.setOpaque(false);
    radios.add(rbDown);
    radios.add(rbUp);
    dirPanel.add(radios);
    content.add(dirPanel);

    // Steps
    JPanel stepsPanel = new JPanel(new MigLayout("insets 0, gap 12", "[][60]"));
    stepsPanel.setOpaque(false);
    JLabel lblSteps = new JLabel(_I("recorderWheelLblSteps"));
    stepsPanel.add(lblSteps);
    spinnerSteps = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    spinnerSteps.addChangeListener(e -> updatePreview());
    stepsPanel.add(spinnerSteps);
    content.add(stepsPanel);

    content.add(new JSeparator(), "growx, gaptop 4");

    // Code preview
    JLabel lblPrev = new JLabel(_I("recorderWheelLblGeneratedCode"));
    lblPrev.setFont(UIManager.getFont("small.font"));
    lblPrev.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblPrev);

    // Use a JTextArea (not JLabel) so that long generated code wraps
    // inside the dialog instead of pushing the MigLayout `[grow, fill]`
    // column wider, which previously made the dialog grow to the right
    // and shove the OK/Cancel buttons out of view (issue #289).
    previewArea = new JTextArea(" ");
    previewArea.setLineWrap(true);
    previewArea.setWrapStyleWord(false);
    previewArea.setEditable(false);
    previewArea.setFocusable(false);
    previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    previewArea.setForeground(new Color(0x00, 0xA8, 0x9D));
    previewArea.setBackground(UIManager.getColor("Panel.background"));
    previewArea.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    content.add(previewArea, "growx");

    // Buttons
    JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "push[][]", ""));
    buttons.setOpaque(false);
    cancelBtn = new JButton(_I("cancel"));
    cancelBtn.addActionListener(e -> { result = null; dispose(); });
    okBtn = new JButton(_I("ok"));
    okBtn.putClientProperty("JButton.buttonType", "default");
    okBtn.addActionListener(e -> { result = buildCode(); dispose(); });
    buttons.add(cancelBtn);
    buttons.add(okBtn);
    content.add(buttons, "gaptop 8, align right");

    setContentPane(content);
    getRootPane().setDefaultButton(okBtn);
  }

  private String buildCode() {
    int direction = rbDown.isSelected() ? 1 : -1;
    int steps = (Integer) spinnerSteps.getValue();
    String directionStr = direction > 0 ? "WHEEL_DOWN" : "WHEEL_UP";

    String patternExpr;
    if (offsetX == 0 && offsetY == 0) {
      patternExpr = "\"" + imageName + "\"";
    } else {
      patternExpr = "Pattern(\"" + imageName + "\").targetOffset(" + offsetX + ", " + offsetY + ")";
    }
    return "wheel(" + patternExpr + ", " + directionStr + ", " + steps + ")";
  }

  private void updatePreview() {
    previewArea.setText(buildCode());
    offsetLabel.setText("Offset: (" + offsetX + ", " + offsetY + ")"
        + (offsetX == 0 && offsetY == 0 ? " — centered on pattern" : ""));
  }

  public String getResult() {
    return result;
  }

  /**
   * Custom panel that displays the captured image and a movable crosshair.
   */
  private class ImagePanel extends JPanel {

    // Layout state (recomputed every paint). NOT used to position the crosshair
    // across paint calls — the crosshair is derived from the image-relative
    // offsetX/offsetY model state in paintComponent. Storing the crosshair in
    // panel-absolute coordinates previously caused issue #289 : when the panel
    // grew (because the generated code preview got longer), the image shifted
    // but the absolute crosshair did not, breaking the visual alignment.
    private double scale;
    private int drawOffsetX, drawOffsetY;
    private int drawWidth, drawHeight;

    ImagePanel() {
      setBackground(Color.DARK_GRAY);

      MouseAdapter handler = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e)  { updateOffsetFromClick(e.getX(), e.getY()); }
        @Override public void mouseDragged(MouseEvent e)  { updateOffsetFromClick(e.getX(), e.getY()); }
      };
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    /**
     * Translate a click position (panel-absolute pixels) into an image-relative
     * offset stored in the dialog's model state (offsetX / offsetY).
     */
    private void updateOffsetFromClick(int px, int py) {
      if (drawWidth <= 0 || drawHeight <= 0 || capture == null) return;

      // Clamp the click to the visible image area
      px = Math.max(drawOffsetX, Math.min(drawOffsetX + drawWidth, px));
      py = Math.max(drawOffsetY, Math.min(drawOffsetY + drawHeight, py));

      // Convert to image-relative coordinates (origin = image center)
      int imgCx = drawOffsetX + drawWidth / 2;
      int imgCy = drawOffsetY + drawHeight / 2;
      offsetX = (int) Math.round((px - imgCx) / scale);
      offsetY = (int) Math.round((py - imgCy) / scale);

      updatePreview();
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (capture == null) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);

      int panelW = getWidth();
      int panelH = getHeight();
      int imgW = capture.getWidth();
      int imgH = capture.getHeight();

      scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
      drawWidth = (int) (imgW * scale);
      drawHeight = (int) (imgH * scale);
      drawOffsetX = (panelW - drawWidth) / 2;
      drawOffsetY = (panelH - drawHeight) / 2;

      g2.drawImage(capture, drawOffsetX, drawOffsetY, drawWidth, drawHeight, null);

      // Derive the crosshair position from offsetX/offsetY (image-relative
      // model state) so it stays anchored to the image regardless of how
      // the panel has been laid out by Swing.
      int imgCx = drawOffsetX + drawWidth / 2;
      int imgCy = drawOffsetY + drawHeight / 2;
      int chX = imgCx + (int) Math.round(offsetX * scale);
      int chY = imgCy + (int) Math.round(offsetY * scale);

      g2.setColor(new Color(0x00, 0xA8, 0x9D));
      g2.setStroke(new BasicStroke(2));
      g2.drawLine(chX - 10, chY, chX + 10, chY);
      g2.drawLine(chX, chY - 10, chX, chY + 10);
      g2.drawOval(chX - 6, chY - 6, 12, 12);

      g2.dispose();
    }
  }
}
