/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.theme;

import com.formdev.flatlaf.util.ScaledImageIcon;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/** The existing gecko assets, shared by tabs and the workspace, with HiDPI variants. */
public final class OculixFileIcons {
  private static final Map<String, Icon> CACHE = new HashMap<>();
  private OculixFileIcons() { }

  public static Icon forName(String name) {
    String value = name == null ? "" : name.replace("*", "").trim().toLowerCase(Locale.ROOT);
    if (value.endsWith(".sikuli")) return get("sikuli");
    if (value.endsWith(".py")) return get("python");
    if (value.matches(".*\\.(png|jpe?g|gif|bmp|webp|svg)$")) return get("image");
    return get("file");
  }

  public static Icon folder(boolean open) {
    return get(open ? "folder-open" : "folder");
  }

  public static synchronized Icon get(String name) {
    Icon cached = CACHE.get(name);
    if (cached != null) return cached;
    URL small = OculixFileIcons.class.getResource("/icons/files/" + name + ".png");
    URL large = OculixFileIcons.class.getResource("/icons/files/" + name + "@32.png");
    try {
      BufferedImage base = small == null ? null : ImageIO.read(small);
      BufferedImage high = large == null ? null : ImageIO.read(large);
      if (base != null) {
        ImageIcon source = high == null ? new ImageIcon(base)
            : new ImageIcon(new BaseMultiResolutionImage(base, high));
        Icon icon = new ScaledImageIcon(source, 20, 20);
        CACHE.put(name, icon);
        return icon;
      }
    } catch (IOException ignored) {
      // Missing optional artwork must not prevent opening a script.
    }
    return UIManager.getIcon(name.startsWith("folder") || "sikuli".equals(name)
        ? "FileView.directoryIcon" : "FileView.fileIcon");
  }
}
