package org.sikuli.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.sikuli.support.Commons;
import org.sikuli.support.FileManager;
import org.sikuli.support.PngChunk;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Raster tests: no desktop, monitor order or current machine DPI is consulted. */
class FinderCaptureDpiTest {
  @TempDir Path dir;
  private TestReporter reporter;

  @BeforeEach void keepReporter(TestReporter reporter) {
    this.reporter = reporter;
  }

  private static BufferedImage noise() {
    BufferedImage image = new BufferedImage(60, 50, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(444);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    return image;
  }

  private static BufferedImage scaled(BufferedImage image, double scale) {
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

  private static BufferedImage scene(BufferedImage patch) {
    BufferedImage result = new BufferedImage(260, 190, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = result.createGraphics();
    try {
      g.drawImage(patch, 37, 29, null);
    } finally {
      g.dispose();
    }
    return result;
  }

  private static ScreenImage shot(BufferedImage image, int dpi) {
    return new ScreenImage(new Rectangle(-700, 100, image.getWidth(), image.getHeight()), image, dpi);
  }

  private static Image target(BufferedImage image, int dpi) {
    Image result = new Image(image);
    result.setCaptureDpi(dpi);
    return result;
  }

  private void assertMatch(Finder finder, Image target, BufferedImage scene, int width, int height) {
    finder.find(new Pattern(target).similar(0.999));
    assertTrue(finder.hasNext(), "expected DPI-aware match");
    Rectangle found = finder.next().getRect();
    ReportImages.publish(reporter, "searched", target.get());
    ReportImages.publish(reporter, "found", ReportImages.framed(scene, found));
    assertEquals(new Rectangle(37, 29, width, height), found);
  }

  @Test void exactMatchKeepsTheFastPathDespiteDifferentDensityMetadata() {
    BufferedImage template = noise();
    BufferedImage screen = scene(template);
    Finder finder = new Finder(shot(screen, 96)).setStopFlag(() -> true);
    assertMatch(finder, target(template, 240), screen, 60, 50);
  }

  @Test void normalizedDesktopFindsPhysicalTemplateAtItsExactRatio() {
    BufferedImage template = noise();
    // 0.4 is deliberately absent from Mode 5: this must be a Mode 2 result.
    BufferedImage screen = scene(scaled(template, 0.4));
    Finder finder = new Finder(shot(screen, ScreenImage.LOGICAL_DPI));
    assertMatch(finder, target(template, 240), screen, 24, 20);
  }

  @Test void sourceImageDensityWorksWithoutAnyLocalScreen() {
    BufferedImage template = noise();
    // 2.4 remains outside Mode 5, so only the DPI ratio can find this patch.
    BufferedImage screen = scene(scaled(template, 2.4));
    Finder finder = new Finder(target(screen, 240));
    assertMatch(finder, target(template, 100), screen, 144, 120);
  }

  @Test void untaggedRemoteCaptureDoesNotBorrowHostDpi() {
    BufferedImage template = noise();
    BufferedImage base = scene(scaled(template, 0.4));
    ScreenImage remote = new ScreenImage(new Rectangle(0, 0, 260, 190), base);
    Finder finder = new Finder(remote);
    finder.find(new Pattern(target(template, 240)).similar(0.999));
    assertFalse(finder.hasNext(), "unknown density must not be silently replaced by the host's DPI");
  }

  @Test void refreshingSnapshotUpdatesDensityAndDoesNotKeepThePreviousOne() {
    BufferedImage template = noise();
    BufferedImage screen = scene(scaled(template, 2.4));
    Finder finder = new Finder(shot(screen, 240));
    Image reference = target(template, 100);
    assertMatch(finder, reference, screen, 144, 120);
    finder.setScreenImage(shot(scene(scaled(template, 2.4)), -1));
    finder.find(new Pattern(reference).similar(0.999));
    assertFalse(finder.hasNext(), "a refreshed unknown source must not inherit the previous source DPI");
  }

  @Test void lastSeenCropAndImageCopyKeepSnapshotDensity() {
    ScreenImage full = shot(scene(noise()), 240);
    ScreenImage crop = full.getSub(new Rectangle(-663, 129, 60, 50));
    assertEquals(240, crop.getCaptureDpi());
    assertEquals(new Rectangle(-663, 129, 60, 50), crop.getRect());
    Image image = new Image(crop);
    assertEquals(240, image.getCaptureDpi());
    assertEquals(240, new Image(crop, "named").getCaptureDpi());
    assertEquals(240, Image.create(image).getCaptureDpi());
  }

  @Test void savedCaptureCarriesItsDensityRatherThanTheSavingDesktop() throws Exception {
    ScreenImage capture = shot(noise(), 240);
    Path png = Path.of(capture.save(dir.toString(), "capture.png"));
    assertDpi(png, 240);
    assertDpi(Path.of(capture.saveInto(dir.toFile())), 240);
    Image image = new Image(capture);
    Path savedImage = dir.resolve("image.png");
    image.save(savedImage.toFile());
    assertDpi(savedImage, 240);
    ScreenImage logical = shot(noise(), ScreenImage.LOGICAL_DPI);
    assertDpi(Path.of(logical.getFile(dir.toString(), "logical.png")), 96);
    assertDpi(Path.of(logical.saveInto(dir.toString())), 96);
    ScreenImage unknown = new ScreenImage(new Rectangle(0, 0, 60, 50), noise());
    Path untagged = Path.of(unknown.save(dir.toString(), "unknown.png"));
    assertNull(PngChunk.read(untagged.toFile(), "pHYs"));
    assertNull(PngChunk.read(Path.of(unknown.saveInto(dir.toFile())).toFile(), "pHYs"));
  }

  @Test void pngDpiAndOplxCoexistAndSearchingDoesNotRewriteEither() throws Exception {
    BufferedImage template = noise();
    Path png = dir.resolve("reference.png");
    FileManager.writePngWithDpi(template, png.toFile(), 240);
    byte[] hint = ByteBuffer.allocate(34).put(new byte[]{'O', 'P', 'L', 0})
        .putShort((short) 1).putInt(-663).putInt(129).putInt(24).putInt(20)
        .putLong(123456789L).putInt(0).array();
    PngChunk.write(png.toFile(), "oPLx", hint);
    byte[] before = Files.readAllBytes(png);
    Image image = Image.create(png.toString());
    assertEquals(new Rectangle(-663, 129, 24, 20), image.getLastSeen());
    BufferedImage screen = scene(scaled(template, 0.4));
    assertMatch(new Finder(shot(screen, 96)), image, screen, 24, 20);
    assertArrayEquals(hint, PngChunk.read(png.toFile(), "oPLx"));
    assertArrayEquals(before, Files.readAllBytes(png));
  }

  @Test void savingAnUntaggedImageDoesNotInventDesktopDensity() throws Exception {
    Path original = dir.resolve("untagged-source.png");
    FileManager.writePngWithDpi(noise(), original.toFile(), -1);
    Image loaded = Image.create(original.toString());
    assertEquals(-1, loaded.getCaptureDpi());
    Path saved = dir.resolve("untagged-copy.png");
    loaded.save(saved.toFile());
    assertNull(PngChunk.read(saved.toFile(), "pHYs"));
    Path named = Path.of(loaded.save("untagged-named.png", dir.toString()));
    assertNull(PngChunk.read(named.toFile(), "pHYs"));
  }

  @Test void savingALoadedImagePreservesFileDensityIncludingInPlace() throws Exception {
    Path original = dir.resolve("tagged-source.png");
    FileManager.writePngWithDpi(noise(), original.toFile(), 240);
    Image loaded = Image.create(original.toString());
    assertEquals(-1, loaded.getCaptureDpi());
    Path named = Path.of(loaded.save("tagged-named.png", dir.toString()));
    assertDpi(named, 240);
    loaded.save(original.toFile());
    assertDpi(original, 240);
  }

  private static void assertDpi(Path png, int expected) throws Exception {
    byte[] phys = PngChunk.read(png.toFile(), "pHYs");
    assertNotNull(phys);
    ByteBuffer data = ByteBuffer.wrap(phys);
    int x = data.getInt();
    assertEquals(expected, Math.round(x / 39.3701));
    assertEquals(x, data.getInt());
    assertEquals(1, data.get());
  }
}
