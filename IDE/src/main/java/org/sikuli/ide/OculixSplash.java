/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import org.sikuli.ide.theme.OculixFonts;
import org.sikuli.support.Commons;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.Window;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * The start-up window: the OculiX logo, and OculiX doing on it what it does on any screen. Three
 * animations follow one another, drawn in code over one image, so they stay sharp at any scale:
 * a scan line reads the logo and a detection box locks on the eye; the box hops letter by letter
 * on the title, then takes the whole word; the logo zooms to x1.25 and x1.50 and the box finds
 * the eye again at each scale, as the Finder cascade does. Under the image, the version line and
 * the last line startLog wrote. Shown by {@link #show()} during start-up, closed by
 * {@link #close()} when the IDE is ready. It never hurts the start: without the image the window
 * is plain, and a failure is logged, not thrown.
 */
public final class OculixSplash {

  private static final String HERO = "/icons/gecko_cyclope_hero.png";
  private static final String ICON = "/icons/gecko_cyclope_icon.png";
  /** Drawing units: the image is D x D, the footer under it FOOTER high, all scaled at paint time. */
  private static final int D = 420;
  private static final int FOOTER = 80;
  private static final int SRC = 1254;
  private static final double MAX_ZOOM = 1.5;
  private static final int FADE_MILLIS = 300;
  private static final int[] DURATION = {3200, 3300, 3400};

  private static final Color BG = new Color(0x08, 0x0F, 0x20);
  private static final Color EDGE = new Color(0x25, 0x34, 0x54);
  private static final Color CYAN = new Color(0x3D, 0xE0, 0xFF);
  private static final Color TEXT = new Color(0xCF, 0xD9, 0xEE);
  private static final Color STATUS = new Color(0x7C, 0xCB, 0xFF);

  /** Boxes on the 1254 x 1254 hero image: the gecko's eye, each letter of the title, the word. */
  private static final double[] EYE = {525, 340, 830, 640};
  private static final double[][] LETTERS = {
      {209, 103, 439, 331}, {446, 150, 585, 335}, {588, 155, 695, 335},
      {697, 93, 792, 335}, {805, 98, 855, 335}, {859, 150, 1045, 335}};
  private static final double[] WORD = {200, 88, 1052, 340};

  private final float scale = (float) (
      1.0 / GraphicsEnvironment
          .getLocalGraphicsEnvironment()
          .getDefaultScreenDevice()
          .getDefaultConfiguration()
          .getDefaultTransform()
          .getScaleX()
  );
  private BufferedImage base;

  private Frame owner;
  private JWindow window;
  private JPanel panel;
  private Timer frames;
  private Timer fade;
  private float alpha = 0f;
  private int animation;
  private long animationStart;
  private volatile String status = "";

  /** Reads the logo from the jar and reduces it once to the size it will be shown at. */
  public OculixSplash() {
    try (InputStream in = OculixSplash.class.getResourceAsStream(HERO)) {
      BufferedImage hero = in == null ? null : ImageIO.read(in);
      if (hero == null) {
        Commons.startLog(3, "splash: image missing: %s", HERO);
      } else {
        double device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
            .getDefaultConfiguration().getDefaultTransform().getScaleX();
        base = downscale(hero, (int) Math.round(D * MAX_ZOOM * scale * device));
      }
    } catch (IOException | RuntimeException e) {
      Commons.startLog(3, "splash: image not loaded: %s", e.getMessage());
      base = null;
    }
  }

  /** Opens the window centred on the screen, with a short fade-in, and starts the animation. */
  public void show() {
    SwingUtilities.invokeLater(this::build);
  }

  /** Fades the window out and disposes it; safe to call from any thread, and more than once. */
  public void close() {
    SwingUtilities.invokeLater(() -> {
      if (window == null) {
        return;
      }
      if (fade != null) {
        fade.stop();
      }
      long begin = System.currentTimeMillis();
      float from = alpha;
      fade = new Timer(16, e -> {
        long t = System.currentTimeMillis() - begin;
        alpha = Math.max(0f, from * (1f - (float) t / FADE_MILLIS));
        if (t >= FADE_MILLIS) {
          ((Timer) e.getSource()).stop();
          if (frames != null) {
            frames.stop();
          }
          window.dispose();
          window = null;
          owner.dispose();
          owner = null;
        }
      });
      fade.start();
    });
  }

  /** The line under the version: the last message of the start-up log. */
  public void status(String line) {
    status = line == null ? "" : line.strip();
  }

  /**
   * Opens the animated splash centred on the default screen's full bounds.
   * Uses an opaque window and paints the background at full opacity.
   * When the native splash is still visible, starts at full content opacity;
   * otherwise fades the content in.
   * An invisible utility frame owns the window and carries the application icon.
   */
  private void build() {
    Rectangle nativeBounds = nativeSplashBounds();
    boolean nativeShown = nativeBounds != null;
    owner = new Frame();
    owner.setType(Window.Type.UTILITY);
    Image icon = icon();
    if (icon != null) {
      owner.setIconImage(icon);
    }
    window = new JWindow(owner);
    window.setType(Window.Type.POPUP);
    window.setFocusableWindowState(false);
    window.setAutoRequestFocus(false);
    window.setBackground(BG);
    panel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        paintSplash((Graphics2D) g.create());
      }
    };
    panel.setOpaque(true);
    panel.setPreferredSize(new Dimension(Math.round(D * scale), Math.round((D + FOOTER) * scale)));
    window.setContentPane(panel);
    window.pack();
    if (window.getGraphicsConfiguration().getDevice()
        .isWindowTranslucencySupported(
            java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {

      window.setShape(new RoundRectangle2D.Double(
          0, 0,
          window.getWidth(), window.getHeight(),
          28 * scale, 28 * scale
      ));
    }
    Rectangle screen = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .getDefaultScreenDevice()
        .getDefaultConfiguration()
        .getBounds();

    window.setLocation(
        screen.x + (screen.width - window.getWidth()) / 2,
        screen.y + (screen.height - window.getHeight()) / 2
    );

    window.setAlwaysOnTop(true);
    long begin = System.currentTimeMillis();
    animationStart = begin;
    alpha = nativeShown ? 1f : 0f;
    if (nativeShown) {
      warmUp();
    }
    window.setVisible(true);
    if (nativeShown) {
      panel.paintImmediately(0, 0, panel.getWidth(), panel.getHeight());
    }
    closeNativeSplash();

    if (!nativeShown) {
      fade = new Timer(16, e -> {
        alpha = Math.min(1f, (float) (System.currentTimeMillis() - begin) / FADE_MILLIS);
        if (alpha >= 1f) {
          ((Timer) e.getSource()).stop();
        }
      });
      fade.start();
    }
    frames = new Timer(16, e -> panel.repaint());
    frames.start();
  }

  /**
   * Draws the first frame once into an image nobody sees. Java closes the launcher's splash the
   * moment this window is shown, and a first paint is the slowest one (fonts, the scaled logo,
   * cold code): paid here, it no longer leaves a hole between the still image and the animation.
   */
  private void warmUp() {
    BufferedImage scratch = new BufferedImage(Math.max(1, panel.getWidth()),
        Math.max(1, panel.getHeight()), BufferedImage.TYPE_INT_ARGB);
    paintSplash(scratch.createGraphics());
  }

  /**
   * Where the launcher shows the still splash of the manifest, in screen coordinates.
   *
   * @return its bounds, null when there is none, when it is already closed, or when headless
   */
  private static Rectangle nativeSplashBounds() {
    try {
      SplashScreen splash = SplashScreen.getSplashScreen();
      return splash != null && splash.isVisible() ? splash.getBounds() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Closes the launcher's still splash; Java closes it anyway when a window shows, this is sure. */
  private static void closeNativeSplash() {
    try {
      SplashScreen splash = SplashScreen.getSplashScreen();
      if (splash != null && splash.isVisible()) {
        splash.close();
      }
    } catch (RuntimeException e) {
      Commons.startLog(3, "splash: launcher splash not closed: %s", e.getMessage());
    }
  }

  /** The IDE's own window icon, for the owner frame; null when it cannot be read. */
  private static Image icon() {
    try (InputStream in = OculixSplash.class.getResourceAsStream(ICON)) {
      return in == null ? null : ImageIO.read(in);
    } catch (IOException e) {
      return null;
    }
  }

  private void paintSplash(Graphics2D g) {
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.scale(scale, scale);
      g.setComposite(AlphaComposite.SrcOver);
      g.setColor(BG);
      g.fillRect(0, 0, D, D + FOOTER);
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

      long now = System.currentTimeMillis();
      if (now - animationStart > DURATION[animation]) {
        animation = (animation + 1) % DURATION.length;
        animationStart = now;
      }
      long t = now - animationStart;

      int arc = 28;
      int h = D + FOOTER;
      g.setColor(BG);
      g.fillRoundRect(0, 0, D, h, arc, arc);
      if (base != null) {
        Graphics2D img = (Graphics2D) g.create();
        img.setClip(new RoundRectangle2D.Float(0, 0, D, h, arc, arc));
        img.clipRect(0, 0, D, D);
        double size = D * (animation == 2 ? cascadeZoom(t) : 1.0);
        double origin = (D - size) / 2;
        img.drawImage(base, (int) Math.round(origin), (int) Math.round(origin),
            (int) Math.round(size), (int) Math.round(size), null);
        switch (animation) {
          case 0 -> scan(img, t, origin, size);
          case 1 -> read(img, t, origin, size);
          default -> cascade(img, t, origin, size);
        }
        img.dispose();
      }
      g.setColor(EDGE);
      g.drawRoundRect(0, 0, D - 1, h - 1, arc, arc);
      footer(g);
    } finally {
      g.dispose();
    }
  }

  /** The version line, then the last start-up line, shrunk and cut to fit the width. */
  private void footer(Graphics2D g) {
    g.setFont(OculixFonts.mono(16));
    g.setColor(TEXT);
    String version = "OculiX " + Commons.getSXVersionShort() + "  ·  Java " + Commons.getJavaVersion();
    g.drawString(version, (D - g.getFontMetrics().stringWidth(version)) / 2, D + 30);

    g.setColor(STATUS);
    String line = status;
    int room = D - 24;
    int size = 13;
    g.setFont(OculixFonts.mono(size));
    while (g.getFontMetrics().stringWidth(line) > room && size > 10) {
      g.setFont(OculixFonts.mono(--size));
    }
    while (g.getFontMetrics().stringWidth(line) > room && line.length() > 4) {
      line = line.substring(0, line.length() - 2) + "…";
    }
    g.drawString(line, (D - g.getFontMetrics().stringWidth(line)) / 2, D + 58);
  }

  // ---- the three animations -------------------------------------------------------------------

  /** A line reads the logo top to bottom; a box closes in on the eye and locks with its score. */
  private void scan(Graphics2D g, long t, double origin, double size) {
    if (t < 1300) {
      double y = origin + size * ease(t / 1300.0);
      g.setPaint(new GradientPaint(0, (float) (y - 46), new Color(61, 224, 255, 0), 0, (float) y,
          new Color(61, 224, 255, 70)));
      g.fill(new Rectangle2D.Double(0, y - 46, D, 46));
      g.setColor(new Color(61, 224, 255, 90));
      g.setStroke(new BasicStroke(6f));
      g.draw(new Line2D.Double(0, y, D, y));
      g.setColor(CYAN);
      g.setStroke(new BasicStroke(1.6f));
      g.draw(new Line2D.Double(0, y, D, y));
    }
    double[] eye = map(EYE, origin, size);
    double[] wide = {14, 14, D - 14, D - 14};
    if (t >= 1100 && t < 1900) {
      box(g, lerp(wide, eye, ease((t - 1100) / 800.0)), (float) Math.min(1, (t - 1100) / 300.0));
    } else if (t >= 1900) {
      float a = t < 2700 ? 1f : (float) Math.max(0, 1 - (t - 2700) / 400.0);
      box(g, eye, a);
      flash(g, eye, t - 1900);
      tag(g, eye, "0.99", a);
    }
  }

  /** The box hops along the letters of the title, then widens to the whole word. */
  private void read(Graphics2D g, long t, double origin, double size) {
    double[][] letters = new double[LETTERS.length][];
    for (int i = 0; i < LETTERS.length; i++) {
      letters[i] = map(LETTERS[i], origin, size);
    }
    double[] word = map(WORD, origin, size);
    if (t < 2000) {
      int hop = (int) Math.min(LETTERS.length - 1, t / 330);
      double local = (t - hop * 330) / 330.0;
      double[] at = letters[hop];
      if (hop > 0 && local < 0.55) {
        at = lerp(letters[hop - 1], letters[hop], ease(local / 0.55));
      }
      box(g, at, (float) Math.min(1, t / 250.0));
      if (hop == 0 || local >= 0.55) {
        flash(g, letters[hop], (long) ((local - (hop == 0 ? 0 : 0.55)) * 330));
      }
    } else if (t < 2400) {
      box(g, lerp(letters[LETTERS.length - 1], word, ease((t - 2000) / 400.0)), 1f);
    } else {
      float a = t < 2900 ? 1f : (float) Math.max(0, 1 - (t - 2900) / 400.0);
      box(g, word, a);
      flash(g, word, t - 2400);
      tag(g, word, "Match 1.00", a);
    }
  }

  /** The zoom of the cascade animation: 1, then x1.25, then x1.50, then back to 1. */
  private static double cascadeZoom(long t) {
    if (t < 400) return 1.0;
    if (t < 900) return 1.0 + 0.25 * ease((t - 400) / 500.0);
    if (t < 1500) return 1.25;
    if (t < 2000) return 1.25 + 0.25 * ease((t - 1500) / 500.0);
    if (t < 2600) return MAX_ZOOM;
    if (t < 3000) return MAX_ZOOM - 0.5 * ease((t - 2600) / 400.0);
    return 1.0;
  }

  /** At each scale the box snaps onto the eye again, with the scale and the score. */
  private void cascade(Graphics2D g, long t, double origin, double size) {
    double[] eye = map(EYE, origin, size);
    long[] from = {900, 2000, 3000};
    long[] to = {1500, 2600, 3400};
    String[] label = {"x1.25  0.98", "x1.50  0.97", "x1.00  1.00"};
    for (int i = 0; i < from.length; i++) {
      if (t >= from[i] && t < to[i]) {
        long in = t - from[i];
        double grow = 1 + 0.18 * (1 - ease(Math.min(1, in / 160.0)));
        double cx = (eye[0] + eye[2]) / 2;
        double cy = (eye[1] + eye[3]) / 2;
        double hw = (eye[2] - eye[0]) / 2 * grow;
        double hh = (eye[3] - eye[1]) / 2 * grow;
        float a = i == from.length - 1 && t > 3200
            ? (float) Math.max(0, 1 - (t - 3200) / 200.0)
            : (float) Math.min(1, in / 120.0);
        box(g, new double[]{cx - hw, cy - hh, cx + hw, cy + hh}, a);
        tag(g, eye, label[i], a);
      }
    }
  }

  // ---- drawing helpers ------------------------------------------------------------------------

  /** A detection box: a thin frame and thick corner brackets. */
  private void box(Graphics2D g, double[] r, float a) {
    if (a <= 0) return;
    Graphics2D b = faded(g, a);
    double x1 = r[0], y1 = r[1], x2 = r[2], y2 = r[3];
    b.setColor(new Color(61, 224, 255, 110));
    b.setStroke(new BasicStroke(1.2f));
    b.draw(new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1));
    double c = Math.min(14, Math.min(x2 - x1, y2 - y1) / 3);
    b.setColor(CYAN);
    b.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    double[][] corners = {{x1, y1, 1, 1}, {x2, y1, -1, 1}, {x1, y2, 1, -1}, {x2, y2, -1, -1}};
    for (double[] k : corners) {
      b.draw(new Line2D.Double(k[0], k[1], k[0] + c * k[2], k[1]));
      b.draw(new Line2D.Double(k[0], k[1], k[0], k[1] + c * k[3]));
    }
    b.dispose();
  }

  /** A ring that leaves the box when it locks. */
  private void flash(Graphics2D g, double[] r, long since) {
    if (since < 0 || since > 260) return;
    double k = since / 260.0;
    double pad = 3 + 10 * k;
    Graphics2D b = faded(g, (float) (0.8 * (1 - k)));
    b.setColor(CYAN);
    b.setStroke(new BasicStroke(1.5f));
    b.draw(new Rectangle2D.Double(r[0] - pad, r[1] - pad, r[2] - r[0] + 2 * pad, r[3] - r[1] + 2 * pad));
    b.dispose();
  }

  /** The score in a small dark tag above the box, or below it when there is no room above. */
  private void tag(Graphics2D g, double[] r, String text, float a) {
    if (a <= 0) return;
    Graphics2D b = faded(g, a);
    b.setFont(OculixFonts.mono(12).deriveFont(Font.BOLD));
    int tw = b.getFontMetrics().stringWidth(text);
    double x = Math.max(6, Math.min(D - tw - 16, r[0]));
    double y = r[1] - 24 >= 4 ? r[1] - 24 : r[3] + 6;
    RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, tw + 12, 19, 8, 8);
    b.setColor(new Color(8, 15, 32, 225));
    b.fill(shape);
    b.setColor(CYAN);
    b.setStroke(new BasicStroke(1f));
    b.draw(shape);
    b.drawString(text, (float) (x + 6), (float) (y + 14));
    b.dispose();
  }

  /** A copy of g whose opacity is the window's opacity times a. */
  private Graphics2D faded(Graphics2D g, float a) {
    Graphics2D b = (Graphics2D) g.create();
    b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * alpha));
    return b;
  }

  // ---- maths ----------------------------------------------------------------------------------

  /** A box of the hero image, in drawing units, for the image drawn at origin with this size. */
  private static double[] map(double[] r, double origin, double size) {
    double f = size / SRC;
    return new double[]{origin + r[0] * f, origin + r[1] * f, origin + r[2] * f, origin + r[3] * f};
  }

  private static double[] lerp(double[] a, double[] b, double k) {
    double[] r = new double[4];
    for (int i = 0; i < 4; i++) {
      r[i] = a[i] + (b[i] - a[i]) * k;
    }
    return r;
  }

  /** Cubic ease in and out, 0..1 to 0..1. */
  private static double ease(double x) {
    x = Math.max(0, Math.min(1, x));
    return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
  }

  /** Halves the image until one bicubic step is left, which keeps a large source sharp. */
  private static BufferedImage downscale(BufferedImage src, int target) {
    BufferedImage img = src;
    int size = src.getWidth();
    while (size / 2 >= target) {
      size /= 2;
      img = resize(img, size);
    }
    return size == target ? img : resize(img, target);
  }

  private static BufferedImage resize(BufferedImage src, int size) {
    BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(src, 0, 0, size, size, null);
    g.dispose();
    return out;
  }
}
