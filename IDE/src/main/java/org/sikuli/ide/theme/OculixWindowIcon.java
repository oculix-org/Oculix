/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import com.formdev.flatlaf.icons.FlatWindowAbstractIcon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;

/**
 * A FlatLaf window button icon that keeps the native button size, hover and
 * pressed backgrounds, and draws one of the OculiX tiles in the middle
 * instead of the stroked symbol.
 */
public class OculixWindowIcon extends FlatWindowAbstractIcon {

  /** The side of the tile inside the button, at 100% scale. */
  private static final int TILE = 18;

  private final BufferedImage tile;
  private final BufferedImage tile2x;

  /**
   * @param name the tile file under {@code /icons/titlebar/}: hide, iconify, maximize or close
   */
  public OculixWindowIcon(String name) {
    super(null);
    this.tile = read("/icons/titlebar/" + name + ".png");
    this.tile2x = read("/icons/titlebar/" + name + "@2x.png");
  }

  @Override
  protected void paintIconAt1x(Graphics2D g, int x, int y, int width, int height, double scaleFactor) {
    BufferedImage image = scaleFactor > 1 && tile2x != null ? tile2x : tile;
    if (image == null) {
      return;
    }
    int side = (int) Math.round(TILE * scaleFactor);
    int ix = x + (width - side) / 2;
    int iy = y + (height - side) / 2;
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image, ix, iy, side, side, null);
  }

  private static BufferedImage read(String path) {
    try {
      URL url = OculixWindowIcon.class.getResource(path);
      return url == null ? null : ImageIO.read(url);
    } catch (Exception e) {
      return null;
    }
  }
}
