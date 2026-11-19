package org.sikuli.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * #492: a {@code TYPE_CUSTOM} image has no fixed raster layout, so {@code makeMat} must not read
 * its raster as 4 bytes per pixel. Three real layouts: int-backed (what a VNC frame is; the old
 * code threw ClassCastException), byte-backed with 3 bands (the old code returned scrambled
 * colours without any error) and ushort-backed 16 bpp. Every pixel gets a different colour, and
 * the test checks the exact BGR(A) values, so a conversion that shifts or reorders channels fails
 * as surely as one that throws.
 */
class CommonsMakeMatCustomTypeTest {

  /** One distinct colour per pixel, chosen to be exact in 5-6-5 as well as 8-8-8. */
  private static final int[] RGB = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x000000, 0xFF00FF, 0x00FFFF, 0xFFFF00};
  private static final int W = 4, H = 2;

  @BeforeAll
  static void openCv() {
    Commons.loadOpenCV();
  }

  private static BufferedImage fill(BufferedImage img) {
    for (int i = 0; i < W * H; i++) {
      img.setRGB(i % W, i / W, 0xFF000000 | RGB[i]); // opaque: setRGB takes ARGB
    }
    return img;
  }

  /** The VNC frame shape: DirectColorModel with non-standard masks over a DataBufferInt. */
  private static BufferedImage intBacked() {
    DirectColorModel cm = new DirectColorModel(32, 0xF800, 0x07E0, 0x001F);
    return fill(new BufferedImage(cm, cm.createCompatibleWritableRaster(W, H), false, null));
  }

  /** Byte-backed, 3 interleaved bands in R,G,B order. */
  private static BufferedImage byteBacked3Band() {
    ComponentColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
        false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    WritableRaster r = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, W, H, W * 3, 3, new int[]{0, 1, 2}, null);
    return fill(new BufferedImage(cm, r, false, null));
  }

  /** 16 bpp over a DataBufferUShort, bgr565 masks (rgb565 would be the standard TYPE_USHORT_565_RGB). */
  private static BufferedImage ushortBacked565() {
    DirectColorModel cm = new DirectColorModel(16, 0x001F, 0x07E0, 0xF800);
    return fill(new BufferedImage(cm, cm.createCompatibleWritableRaster(W, H), false, null));
  }

  private static void assertBgr(Mat m, int channels, boolean alpha) {
    assertEquals(H, m.rows());
    assertEquals(W, m.cols());
    assertEquals(channels, m.channels());
    for (int i = 0; i < W * H; i++) {
      int rgb = RGB[i];
      double[] want = alpha
          ? new double[]{rgb & 0xFF, (rgb >> 8) & 0xFF, (rgb >> 16) & 0xFF, 255}
          : new double[]{rgb & 0xFF, (rgb >> 8) & 0xFF, (rgb >> 16) & 0xFF};
      assertArrayEquals(want, m.get(i / W, i % W), 0.0, "pixel " + i + " (x=" + (i % W) + ", y=" + (i / W) + ")");
    }
  }

  @Test
  void intBackedCustomImage_isConvertedNotCast() {
    BufferedImage img = intBacked();
    assertEquals(BufferedImage.TYPE_CUSTOM, img.getType(), "precondition: TYPE_CUSTOM");
    assertInstanceOf(DataBufferInt.class, img.getRaster().getDataBuffer(), "precondition: int-backed");
    assertBgr(Commons.makeMat(img), 3, false);
  }

  @Test
  void byteBacked3BandCustomImage_keepsItsColours() {
    BufferedImage img = byteBacked3Band();
    assertEquals(BufferedImage.TYPE_CUSTOM, img.getType(), "precondition: TYPE_CUSTOM");
    assertInstanceOf(DataBufferByte.class, img.getRaster().getDataBuffer(), "precondition: byte-backed");
    assertBgr(Commons.makeMat(img), 3, false);
  }

  @Test
  void ushortBacked565CustomImage_isConvertedNotCast() {
    BufferedImage img = ushortBacked565();
    assertEquals(BufferedImage.TYPE_CUSTOM, img.getType(), "precondition: TYPE_CUSTOM");
    assertInstanceOf(DataBufferUShort.class, img.getRaster().getDataBuffer(), "precondition: ushort-backed");
    assertBgr(Commons.makeMat(img), 3, false);
  }

  /**
   * The same mechanism, one step wider: standard types that have no fast path in makeMat used to
   * hit "type not supported" and come back as an empty Mat. They are drawn into a known type too.
   */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
      BufferedImage.TYPE_INT_BGR, BufferedImage.TYPE_USHORT_565_RGB, BufferedImage.TYPE_USHORT_555_RGB})
  void standardTypeWithoutFastPath_isConverted(int type) {
    BufferedImage img = fill(new BufferedImage(W, H, type));
    assertBgr(Commons.makeMat(img), 3, false);
  }

  /** ARGB values with alpha 255, 128 and 0 (colour kept in the transparent pixels too). */
  private static final int[] ARGB = {0xFFFF0000, 0x8000FF00, 0x000000FF, 0xFF123456,
      0x80FFFFFF, 0x00ABCDEF, 0xFF00FFFF, 0x80000000};

  private static BufferedImage fillArgb(BufferedImage img) {
    for (int i = 0; i < W * H; i++) {
      img.setRGB(i % W, i / W, ARGB[i]);
    }
    return img;
  }

  private static BufferedImage customWithAlpha() {
    // non-standard component order with alpha -> TYPE_CUSTOM, int-backed
    DirectColorModel cm = new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), 32,
        0x0000FF00, 0x00FF0000, 0xFF000000, 0x000000FF, false, DataBuffer.TYPE_INT);
    return fillArgb(new BufferedImage(cm, cm.createCompatibleWritableRaster(W, H), false, null));
  }

  /**
   * Images that carry alpha: with asBGR=false (what Pattern and the Finder masks pass) the
   * result is BGRA and equals, pixel for pixel and alpha included, the same picture stored as
   * TYPE_4BYTE_ABGR. With asBGR=true the colour channels come out as stored and alpha is dropped.
   */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"INT_ARGB", "CUSTOM_WITH_ALPHA"})
  void alphaImages_matchTheAbgrPicture(String kind) {
    BufferedImage img = kind.equals("INT_ARGB")
        ? fillArgb(new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)) : customWithAlpha();
    if (kind.equals("CUSTOM_WITH_ALPHA")) {
      assertEquals(BufferedImage.TYPE_CUSTOM, img.getType(), "precondition: TYPE_CUSTOM");
      assertEquals(true, img.getColorModel().hasAlpha(), "precondition: has alpha");
    }
    BufferedImage abgr = fillArgb(new BufferedImage(W, H, BufferedImage.TYPE_4BYTE_ABGR));
    Mat want = Commons.makeMat(abgr, false);
    Mat got = Commons.makeMat(img, false);
    assertEquals(4, got.channels(), "BGRA when alpha is requested");
    Mat bgr = Commons.makeMat(img);
    assertEquals(3, bgr.channels(), "BGR by default");
    for (int i = 0; i < W * H; i++) {
      int y = i / W, x = i % W, argb = ARGB[i];
      double[] exact = {argb & 0xFF, (argb >> 8) & 0xFF, (argb >> 16) & 0xFF, (argb >>> 24)};
      assertArrayEquals(exact, want.get(y, x), 0.0, "the ABGR reference itself, pixel " + i);
      assertArrayEquals(want.get(y, x), got.get(y, x), 0.0, kind + " vs ABGR, pixel " + i);
      assertArrayEquals(new double[]{exact[0], exact[1], exact[2]}, bgr.get(y, x), 0.0,
          kind + " BGR keeps the stored colour, pixel " + i);
    }
  }

  @Test
  void customImage_withAlphaRequested_givesOpaqueBgra() {
    assertBgr(Commons.makeMat(intBacked(), false), 4, true);
  }

  @Test
  void getMatList_onCustomImage_returnsAbgrPlanes() {
    // getMatList is public; its documented shape is the 4 planes A, B, G, R of TYPE_4BYTE_ABGR.
    java.util.List<Mat> planes = Commons.getMatList(intBacked());
    assertEquals(4, planes.size());
    int rgb = RGB[0]; // pixel 0 is pure red
    assertEquals(255, planes.get(0).get(0, 0)[0], 0.0, "A");
    assertEquals(rgb & 0xFF, planes.get(1).get(0, 0)[0], 0.0, "B");
    assertEquals((rgb >> 8) & 0xFF, planes.get(2).get(0, 0)[0], 0.0, "G");
    assertEquals((rgb >> 16) & 0xFF, planes.get(3).get(0, 0)[0], 0.0, "R");
  }
}
