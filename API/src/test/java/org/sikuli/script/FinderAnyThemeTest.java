package org.sikuli.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.sikuli.basics.Settings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class FinderAnyThemeTest {
  private static final Color NAVY = new Color(0x1d2b53);
  private static final Color LIGHT = new Color(0xe8e8e8);
  private static final Color GREEN = new Color(0x3cb043);
  private static final int SIZE = 96;

  private TestReporter reporter;
  private boolean anyColor;
  private boolean anyTheme;

  @BeforeEach void keepSettings(TestReporter reporter) {
    this.reporter = reporter;
    anyColor = Settings.AnyColor;
    anyTheme = Settings.AnyTheme;
  }

  @AfterEach void restoreSettings() {
    Settings.AnyColor = anyColor;
    Settings.AnyTheme = anyTheme;
  }

  /** A star with a round hole and a diamond in it, drawn in {@code shape} on {@code background}. */
  private static BufferedImage star(Color shape, Color background) {
    BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(background);
      g.fillRect(0, 0, SIZE, SIZE);
      Polygon star = new Polygon();
      for (int i = 0; i < 20; i++) {
        double angle = Math.PI * i / 10 - Math.PI / 2;
        double radius = i % 2 == 0 ? 44 : 26;
        star.addPoint((int) Math.round(48 + radius * Math.cos(angle)),
            (int) Math.round(48 + radius * Math.sin(angle)));
      }
      g.setColor(shape);
      g.fillPolygon(star);
      g.setColor(background);
      g.fillOval(33, 33, 30, 30);
      g.fillOval(44, 12, 8, 8);
      g.setColor(shape);
      g.fillPolygon(new int[] {48, 58, 48, 38}, new int[] {38, 48, 58, 48}, 4);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static BufferedImage screen(Color background, Object... patchesAndPositions) {
    BufferedImage image = new BufferedImage(420, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(background);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      for (int i = 0; i < patchesAndPositions.length; i += 3) {
        g.drawImage((BufferedImage) patchesAndPositions[i],
            (Integer) patchesAndPositions[i + 1], (Integer) patchesAndPositions[i + 2], null);
      }
    } finally {
      g.dispose();
    }
    return image;
  }

  private static Image logical(BufferedImage pixels) {
    Image image = new Image(pixels);
    image.setCaptureDpi(ScreenImage.LOGICAL_DPI);
    return image;
  }

  @Test void negativeIsNotFoundWithoutAnyTheme() {
    Settings.AnyColor = true;
    Settings.AnyTheme = false;
    BufferedImage view = screen(NAVY, star(LIGHT, NAVY), 31, 27);
    Finder finder = new Finder(logical(view));
    finder.find(new Pattern(logical(star(NAVY, LIGHT))).similar(0.9));
    assertFalse(finder.hasNext());
  }

  @Test void negativeIsFoundWithAnyTheme() {
    Settings.AnyColor = false;
    Settings.AnyTheme = true;
    BufferedImage reference = star(NAVY, LIGHT);
    BufferedImage view = screen(NAVY, star(LIGHT, NAVY), 31, 27);
    Finder finder = new Finder(logical(view));
    finder.find(new Pattern(logical(reference)).similar(0.9));
    assertTrue(finder.hasNext());
    Match match = finder.next();
    ReportImages.publish(reporter, "searched", reference);
    ReportImages.publish(reporter, "found", ReportImages.framed(view, match.getRect()));
    assertEquals(new Rectangle(31, 27, SIZE, SIZE), match.getRect());
    assertTrue(match.getScore() > 0.95, "score " + match.getScore());
  }

  @Test void positiveWinsOverNegative() {
    Settings.AnyTheme = true;
    BufferedImage reference = star(NAVY, LIGHT);
    BufferedImage view = screen(NAVY, star(LIGHT, NAVY), 31, 27, star(NAVY, GREEN), 250, 60);
    Finder finder = new Finder(logical(view));
    finder.find(new Pattern(logical(reference)).similar(0.9));
    assertTrue(finder.hasNext());
    Match match = finder.next();
    ReportImages.publish(reporter, "searched", reference);
    ReportImages.publish(reporter, "found", ReportImages.framed(view, match.getRect()));
    assertEquals(new Rectangle(250, 60, SIZE, SIZE), match.getRect());
  }
}
