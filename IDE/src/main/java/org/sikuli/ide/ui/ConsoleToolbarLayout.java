/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/** One row with trailing actions when space permits; wraps controls on small windows. */
public final class ConsoleToolbarLayout implements LayoutManager {
  @Override public void addLayoutComponent(String name, Component c) { }
  @Override public void removeLayoutComponent(Component c) { }

  @Override public Dimension minimumLayoutSize(Container parent) {
    return new Dimension(0, UIScale.scale(36));
  }

  @Override public Dimension preferredLayoutSize(Container parent) {
    return arrange(parent, false);
  }

  @Override public void layoutContainer(Container parent) {
    arrange(parent, true);
  }

  private Dimension arrange(Container parent, boolean layout) {
    Insets insets = parent.getInsets();
    int gap = UIScale.scale(8);
    int width = parent.getWidth();
    if (width <= 0 && parent.getParent() != null) width = parent.getParent().getWidth();
    int available = width > 0 ? Math.max(1, width - insets.left - insets.right) : Integer.MAX_VALUE / 4;
    java.util.List<Component> controls = new java.util.ArrayList<>();
    int natural = 0, maxHeight = 0;
    for (Component c : parent.getComponents()) {
      if (!c.isVisible()) continue;
      controls.add(c);
      Dimension d = c.getPreferredSize();
      natural += d.width + (controls.size() > 1 ? gap : 0);
      maxHeight = Math.max(maxHeight, d.height);
    }
    boolean singleRow = natural <= available;
    int x = 0, y = 0, rowHeight = 0;
    for (int i = 0; i < controls.size(); i++) {
      Component c = controls.get(i);
      Dimension d = c.getPreferredSize();
      int w = Math.min(d.width, available);
      if (!singleRow && x > 0 && x + w > available) {
        x = 0; y += rowHeight + gap; rowHeight = 0;
      }
      // Filter and auto-scroll lead; all remaining controls align to the trailing edge.
      if (singleRow && i == 2 && width > 0) x += available - natural;
      int h = singleRow ? maxHeight : d.height;
      if (layout) {
        int physicalX = parent.getComponentOrientation().isLeftToRight()
            ? insets.left + x : parent.getWidth() - insets.right - x - w;
        c.setBounds(physicalX, insets.top + y, w, h);
      }
      x += w + gap;
      rowHeight = Math.max(rowHeight, h);
    }
    return new Dimension(natural + insets.left + insets.right,
        y + rowHeight + insets.top + insets.bottom);
  }
}
