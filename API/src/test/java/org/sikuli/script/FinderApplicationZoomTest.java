package org.sikuli.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.sikuli.support.Commons;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FinderApplicationZoomTest {
  private TestReporter reporter;

  @BeforeEach void keepReporter(TestReporter reporter) {
    this.reporter = reporter;
  }

  private static BufferedImage template() {
    BufferedImage image = new BufferedImage(48, 40, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(487);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    return image;
  }

  private static BufferedImage zoom(BufferedImage image, double scale) {
    Commons.loadOpenCV();
    Mat source = Commons.makeMat(image);
    Mat result = new Mat();
    try {
      Imgproc.resize(source, result, new Size(), scale, scale, Imgproc.INTER_LINEAR);
      return Commons.getBufferedImage(result);
    } finally {
      source.release();
      result.release();
    }
  }

  private static Image logical(BufferedImage pixels) {
    Image image = new Image(pixels);
    image.setCaptureDpi(ScreenImage.LOGICAL_DPI);
    return image;
  }

  private static BufferedImage scene(BufferedImage patch) {
    BufferedImage image = new BufferedImage(420, 240, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.drawImage(patch, 17, 23, null);
    } finally {
      g.dispose();
    }
    return image;
  }

  @ParameterizedTest
  @ValueSource(doubles = {2.5, 3.0, 4.0})
  void findsApplicationZoomWithUnchangedDpi(double scale) {
    BufferedImage reference = template();
    BufferedImage enlarged = zoom(reference, scale);
    BufferedImage screen = scene(enlarged);
    Finder finder = new Finder(logical(screen));
    // Equal density disables Mode 2; only Mode 5 changes the template's size.
    finder.find(new Pattern(logical(reference)).similar(0.999));
    assertTrue(finder.hasNext());
    Rectangle found = finder.next().getRect();
    ReportImages.publish(reporter, "searched", reference);
    ReportImages.publish(reporter, "found", ReportImages.framed(screen, found));
    assertEquals(new Rectangle(17, 23, enlarged.getWidth(), enlarged.getHeight()), found);
  }

  @Test void existingScaleKeepsPriorityWhenALargerZoomAlsoMatches() {
    BufferedImage reference = template();
    BufferedImage image = scene(zoom(reference, 1.25));
    Graphics2D g = image.createGraphics();
    try {
      g.drawImage(zoom(reference, 4.0), 180, 50, null);
    } finally {
      g.dispose();
    }
    Finder finder = new Finder(logical(image));
    finder.find(new Pattern(logical(reference)).similar(0.999));
    assertTrue(finder.hasNext());
    Rectangle found = finder.next().getRect();
    ReportImages.publish(reporter, "searched", reference);
    ReportImages.publish(reporter, "found", ReportImages.framed(image, found));
    assertEquals(new Rectangle(17, 23, 60, 50), found);
  }

  @Test void absentTargetWithOversizedZoomsReturnsNoMatch() {
    Finder finder = new Finder(logical(new BufferedImage(100, 90, BufferedImage.TYPE_INT_RGB)));
    finder.find(new Pattern(logical(template())).similar(0.999));
    assertFalse(finder.hasNext());
  }

  @Test void cancellationStillStopsBeforeZoomSearch() {
    BufferedImage reference = template();
    Finder finder = new Finder(logical(scene(zoom(reference, 4.0)))).setStopFlag(() -> true);
    finder.find(new Pattern(logical(reference)).similar(0.999));
    assertFalse(finder.hasNext());
  }
}
