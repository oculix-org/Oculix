package org.sikuli.script;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An actual monitor capture extended with the off-monitor parts of its windows.
 * All coordinates and images are in physical pixels, not Region coordinates.
 * Uncovered space outside the monitor is transparent. The image is a composite,
 * not an atomic desktop screenshot, and must not be used as a logical ScreenImage.
 */
public final class ExtendedScreenCapture {
  public static final long DEFAULT_MAX_PIXELS = 32_000_000L;

  /** A complete window image with its physical desktop bounds. */
  public static final class Layer {
    private final Rectangle bounds;
    private final BufferedImage image;

    public Layer(Rectangle bounds, BufferedImage image) {
      this.bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
      this.image = Objects.requireNonNull(image, "image");
      checkImage(this.bounds, image);
    }
  }

  private final Rectangle physicalBounds;
  private final Rectangle monitorBounds;
  private final BufferedImage image;
  private final int windowCount;

  private ExtendedScreenCapture(Rectangle bounds, Rectangle monitor,
                                BufferedImage image, int windowCount) {
    this.physicalBounds = new Rectangle(bounds);
    this.monitorBounds = new Rectangle(monitor);
    this.image = image;
    this.windowCount = windowCount;
  }

  public Rectangle getPhysicalBounds() { return new Rectangle(physicalBounds); }
  public Rectangle getMonitorBounds() { return new Rectangle(monitorBounds); }
  public BufferedImage getImage() { return image; }
  public int getWindowCount() { return windowCount; }

  /** Image-relative coordinate to physical desktop coordinate. No DPI conversion. */
  public Point toPhysicalPoint(int imageX, int imageY) {
    if (imageX < 0 || imageY < 0 || imageX >= image.getWidth() || imageY >= image.getHeight()) {
      throw new IllegalArgumentException("Point outside composite");
    }
    return new Point(Math.addExact(physicalBounds.x, imageX),
                     Math.addExact(physicalBounds.y, imageY));
  }

  public static boolean extendsMonitor(Rectangle monitor, Rectangle window) {
    return window.width > 0 && window.height > 0
        && monitor.intersects(window) && !monitor.contains(window);
  }

  /**
   * Layers must be ordered back to front. Only windows crossing the ORIGINAL
   * monitor boundary are selected; enlarging the canvas never selects neighbours.
   * Input images must remain unchanged during this operation.
   */
  public static ExtendedScreenCapture compose(Rectangle monitor, BufferedImage desktop,
                                              List<Layer> backToFront) {
    return compose(monitor, desktop, backToFront, DEFAULT_MAX_PIXELS);
  }

  public static ExtendedScreenCapture compose(Rectangle monitor, BufferedImage desktop,
                                              List<Layer> backToFront, long maxPixels) {
    Rectangle base = new Rectangle(Objects.requireNonNull(monitor, "monitor"));
    checkImage(base, Objects.requireNonNull(desktop, "desktop"));
    Rectangle canvas = new Rectangle(base);
    List<Layer> selected = new ArrayList<>();
    for (Layer layer : Objects.requireNonNull(backToFront, "layers")) {
      if (extendsMonitor(base, layer.bounds)) {
        canvas = checkedUnion(canvas, layer.bounds);
        selected.add(layer);
      }
    }
    checkSize(canvas, maxPixels);
    BufferedImage output = new BufferedImage(canvas.width, canvas.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = output.createGraphics();
    try {
      // Translate the coordinate system once: all clipping stays in desktop pixels.
      g.translate(-(double) canvas.x, -(double) canvas.y);
      g.setComposite(AlphaComposite.Src);
      g.drawImage(desktop, base.x, base.y, null);
      Area outside = new Area(canvas);
      outside.subtract(new Area(base));
      g.setClip(outside);
      g.setComposite(AlphaComposite.SrcOver);
      for (Layer layer : selected) {
        g.drawImage(layer.image, layer.bounds.x, layer.bounds.y, null);
      }
    } finally {
      g.dispose();
    }
    return new ExtendedScreenCapture(canvas, base, output, selected.size());
  }

  public static void checkSize(Rectangle bounds, long maxPixels) {
    if (bounds.width <= 0 || bounds.height <= 0 || maxPixels <= 0
        || (long) bounds.width * bounds.height > maxPixels) {
      throw new IllegalArgumentException("Capture exceeds pixel budget or has invalid size: " + bounds);
    }
  }

  public static Rectangle checkedUnion(Rectangle a, Rectangle b) {
    long x = Math.min(a.x, b.x);
    long y = Math.min(a.y, b.y);
    long right = Math.max((long) a.x + a.width, (long) b.x + b.width);
    long bottom = Math.max((long) a.y + a.height, (long) b.y + b.height);
    if (right > Integer.MAX_VALUE || bottom > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Capture coordinates overflow");
    }
    return new Rectangle((int) x, (int) y, Math.toIntExact(right - x), Math.toIntExact(bottom - y));
  }

  private static void checkImage(Rectangle bounds, BufferedImage image) {
    if (bounds.width != image.getWidth() || bounds.height != image.getHeight()) {
      throw new IllegalArgumentException("Image dimensions must equal physical bounds: " + bounds);
    }
  }
}
