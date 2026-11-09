package org.sikuli.script;

import org.opencv.core.Core;
import org.sikuli.basics.Settings;
import org.sikuli.support.Commons;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Manual benchmark: real exists(target, 0), with a fixed in-memory capture.
 * Excludes OS capture and does not estimate end-to-end desktop latency.
 * Run unchanged against both Finder versions in separate warmed JVMs.
 */
public class FinderScaleFailureBenchmark {
  private static class SnapshotRegion extends Region {
    private final ScreenImage snapshot;

    SnapshotRegion(BufferedImage pixels) {
      super();
      w = pixels.getWidth();
      h = pixels.getHeight();
      snapshot = new ScreenImage(new Rectangle(0, 0, w, h), pixels, ScreenImage.LOGICAL_DPI);
    }

    @Override ScreenImage captureSelf(int cx, int cy, int cw, int ch) {
      if (cx != 0 || cy != 0 || cw != w || ch != h) {
        throw new AssertionError("benchmark must scan the whole snapshot");
      }
      return snapshot;
    }

    @Override public String toStringShort() {
      return "snapshot " + w + "x" + h;
    }
  }

  private static BufferedImage noise(int w, int h, long seed) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(seed);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    return image;
  }

  public static void main(String[] args) {
    Commons.loadOpenCV();
    Core.setNumThreads(1);
    Settings.CheckLastSeen = false;
    Settings.Highlight = false;
    System.out.printf(Locale.ROOT, "ENV java=%s opencv=%s threads=%d%n",
        System.getProperty("java.version"), Core.VERSION, Core.getNumThreads());
    for (int[] size : new int[][]{{1280, 720, 48, 40}, {1920, 1080, 120, 80}}) {
      SnapshotRegion region = new SnapshotRegion(noise(size[0], size[1], 485));
      Image target = new Image(noise(size[2], size[3], 444));
      target.setCaptureDpi(ScreenImage.LOGICAL_DPI);
      Pattern pattern = new Pattern(target).similar(0.8);
      double[] times = new double[5];
      for (int i = -3; i < times.length; i++) {
        // Clean up prior native Mats outside the timed search.
        System.gc();
        System.runFinalization();
        long start = System.nanoTime();
        Match match = region.exists(pattern, 0);
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        if (match != null) {
          throw new AssertionError("absent target unexpectedly matched");
        }
        if (i >= 0) {
          times[i] = elapsed;
        }
      }
      System.out.printf(Locale.ROOT, "SAMPLES %dx%d template=%dx%d ms=%s%n",
          size[0], size[1], size[2], size[3], Arrays.toString(times));
      Arrays.sort(times);
      System.out.printf(Locale.ROOT, "MEDIAN %dx%d template=%dx%d %.3f ms%n",
          size[0], size[1], size[2], size[3], times[times.length / 2]);
    }
  }
}
