package org.sikuli.script;

import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.TestReporter;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Publishes the images a visual test works on, so a report can show what was searched and found. */
final class ReportImages {

  private ReportImages() {
  }

  /** Publishes {@code image} as a PNG attachment of the running test. */
  static void publish(TestReporter reporter, String name, BufferedImage image) {
    reporter.publishFile(name + ".png", MediaType.IMAGE_PNG, path -> ImageIO.write(image, "png", path.toFile()));
  }

  /** A copy of {@code scene} with {@code found} framed in red, or the plain copy when nothing was found. */
  static BufferedImage framed(BufferedImage scene, Rectangle found) {
    BufferedImage copy = new BufferedImage(scene.getWidth(), scene.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = copy.createGraphics();
    try {
      g.drawImage(scene, 0, 0, null);
      if (found != null) {
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(2));
        g.drawRect(found.x - 1, found.y - 1, found.width + 1, found.height + 1);
      }
    } finally {
      g.dispose();
    }
    return copy;
  }
}
