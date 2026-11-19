package org.sikuli.script;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedScreenCaptureTest {
  private static BufferedImage solid(int w, int h, int argb) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) { image.setRGB(x, y, argb); }
    }
    return image;
  }

  private static ExtendedScreenCapture.Layer layer(Rectangle r, int color) {
    return new ExtendedScreenCapture.Layer(r, solid(r.width, r.height, color));
  }

  @Test void addsRightHandWindowPartWithoutTouchingAnyMonitorPixel() {
    Rectangle monitor = new Rectangle(0, 0, 192, 108);
    BufferedImage base = solid(192, 108, 0xff123456);
    // A visible occluder already captured by Robot must survive window recomposition.
    base.setRGB(180, 30, 0xffabcdef);
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(monitor, base,
        List.of(layer(new Rectangle(170, 20, 60, 50), 0xffee4422)));
    assertEquals(new Rectangle(0, 0, 230, 108), result.getPhysicalBounds());
    for (int y = 0; y < 108; y++) {
      for (int x = 0; x < 230; x++) {
        int expected = x < 192 ? base.getRGB(x, y)
            : y >= 20 && y < 70 ? 0xffee4422 : 0;
        assertEquals(expected, result.getImage().getRGB(x, y), "pixel " + x + "," + y);
      }
    }
  }

  @Test void preservesPhysicalCoordinatesWithNegativeOriginAndFourSidedOverflow() {
    Rectangle monitor = new Rectangle(-100, -50, 80, 60);
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(monitor, solid(80, 60, 0xff112233),
        List.of(layer(new Rectangle(-110, -60, 100, 80), 0xffcc8844)));
    assertEquals(new Rectangle(-110, -60, 100, 80), result.getPhysicalBounds());
    assertEquals(new Point(-110, -60), result.toPhysicalPoint(0, 0));
    assertEquals(new Point(-20, 10), result.toPhysicalPoint(90, 70));
    for (int y = 0; y < 80; y++) {
      for (int x = 0; x < 100; x++) {
        int expected = x >= 10 && x < 90 && y >= 10 && y < 70 ? 0xff112233 : 0xffcc8844;
        assertEquals(expected, result.getImage().getRGB(x, y));
      }
    }
  }

  @Test void frontWindowWinsOnlyOutsideMonitor() {
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(new Rectangle(0, 0, 10, 10),
        solid(10, 10, 0xff111111), List.of(
            layer(new Rectangle(8, 1, 8, 8), 0xffcc0000),
            layer(new Rectangle(9, 3, 5, 4), 0xff0000cc)));
    assertEquals(0xff0000cc, result.getImage().getRGB(12, 4));
    assertEquals(0xffcc0000, result.getImage().getRGB(15, 4));
    assertEquals(0xff111111, result.getImage().getRGB(9, 4));
    assertEquals(2, result.getWindowCount());
  }

  @Test void ignoresContainedAdjacentAndRecursivelyReachableWindows() {
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(new Rectangle(0, 0, 10, 10),
        solid(10, 10, 0xff123456), List.of(
            layer(new Rectangle(2, 2, 3, 3), 0xffeeeeee),
            layer(new Rectangle(8, 2, 7, 4), 0xffcc0000),
            layer(new Rectangle(14, 2, 10, 4), 0xff0000cc),
            layer(new Rectangle(10, 8, 3, 2), 0xff00cc00)));
    assertEquals(new Rectangle(0, 0, 15, 10), result.getPhysicalBounds());
    assertEquals(1, result.getWindowCount());
    assertEquals(0xffcc0000, result.getImage().getRGB(14, 3));
    assertEquals(0, result.getImage().getRGB(12, 9));
  }

  @Test void noCrossingWindowReturnsExactMonitorAndDefensiveBounds() {
    Rectangle bounds = new Rectangle(40, 80, 5, 3);
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(bounds, solid(5, 3, 0xff557799), List.of());
    bounds.x = 0;
    result.getPhysicalBounds().x = 0;
    assertEquals(new Rectangle(40, 80, 5, 3), result.getPhysicalBounds());
    assertEquals(new Point(44, 82), result.toPhysicalPoint(4, 2));
    assertThrows(IllegalArgumentException.class, () -> result.toPhysicalPoint(5, 0));
    assertEquals(0, result.getWindowCount());
  }

  @Test void refusesWrongPhysicalSizeAndOversizedAllocation() {
    assertThrows(IllegalArgumentException.class, () ->
        new ExtendedScreenCapture.Layer(new Rectangle(0, 0, 10, 10), solid(8, 8, 0)));
    assertThrows(IllegalArgumentException.class, () ->
        ExtendedScreenCapture.compose(new Rectangle(0, 0, 10, 10), solid(10, 10, 0),
            List.of(layer(new Rectangle(9, 0, 20, 10), 0)), 200));
    assertThrows(IllegalArgumentException.class, () -> ExtendedScreenCapture.checkedUnion(
        new Rectangle(Integer.MAX_VALUE - 2, 0, 2, 2),
        new Rectangle(Integer.MAX_VALUE - 1, 0, 4, 2)));
  }

  @Test void physicalPixelCompositionNeedsNoSingleGlobalDpiScale() {
    // Monitor A's native image is 150x100 even if AWT exposed 100x67 at 150%.
    // The window, captured natively on a neighbouring 100% monitor, is 80x40.
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(new Rectangle(200, 0, 150, 100),
        solid(150, 100, 0xff224466),
        List.of(layer(new Rectangle(330, 20, 80, 40), 0xffeecc22)));
    assertEquals(new Rectangle(200, 0, 210, 100), result.getPhysicalBounds());
    assertEquals(0xffeecc22, result.getImage().getRGB(209, 59));
    assertEquals(0xff224466, result.getImage().getRGB(149, 30));
  }

  /** Writes an illustrative simulated desktop; this is NOT a native Windows test. */
  public static void main(String[] args) throws Exception {
    Path directory = Path.of(args[0]);
    Files.createDirectories(directory);
    Rectangle monitor = new Rectangle(0, 0, 640, 360);
    Rectangle window = new Rectangle(480, 80, 300, 180);
    BufferedImage app = solid(300, 180, 0xffedf4fb);
    Graphics2D a = app.createGraphics();
    a.setColor(new Color(0x387bd4)); a.fillRect(0, 0, 300, 32);
    a.setColor(Color.WHITE); a.drawString("Application - complete window", 12, 21);
    a.setColor(Color.DARK_GRAY); a.drawString("Text continuing beyond screen A", 15, 65);
    a.drawRect(12, 90, 270, 65); a.dispose();
    BufferedImage base = solid(640, 360, 0xff20354c);
    Graphics2D b = base.createGraphics();
    b.setColor(Color.WHITE); b.drawString("Screen A - actual visible pixels", 20, 30);
    b.drawImage(app, window.x, window.y, null);
    b.setColor(new Color(0xe59c35)); b.fillRect(440, 190, 200, 100);
    b.setColor(Color.BLACK); b.drawString("Foreground kept intact", 450, 220); b.dispose();
    ExtendedScreenCapture result = ExtendedScreenCapture.compose(monitor, base,
        List.of(new ExtendedScreenCapture.Layer(window, app)));
    ImageIO.write(base, "png", directory.resolve("screen-A.png").toFile());
    ImageIO.write(result.getImage(), "png", directory.resolve("extended.png").toFile());
    System.out.println("SIMULATED: " + monitor + " -> " + result.getPhysicalBounds());
  }
}
