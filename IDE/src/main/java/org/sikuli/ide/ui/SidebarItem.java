/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A sidebar button with icon and optional label text.
 * Supports collapsed mode (icon only) and expanded mode (icon + label).
 */
public class SidebarItem extends JButton {

  private final String labelText;
  private boolean collapsed = false;

  public SidebarItem(String text, Icon icon, ActionListener action) {
    this.labelText = text;
    setIcon(icon);
    setText(text);
    setToolTipText(text);
    setHorizontalAlignment(SwingConstants.LEFT);
    setIconTextGap(10);
    setFocusPainted(false);
    setBorderPainted(false);
    setContentAreaFilled(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setFont(UIManager.getFont("defaultFont").deriveFont(14.0f));
    putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);

    if (action != null) {
      addActionListener(action);
    }

    addMouseAdapter();
  }

  public SidebarItem(String text, Icon icon) {
    this(text, icon, null);
  }

  private void addMouseAdapter() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (isEnabled()) {
          setContentAreaFilled(true);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setContentAreaFilled(false);
      }
    });
  }

  public void setCollapsed(boolean collapsed) {
    this.collapsed = collapsed;
    if (collapsed) {
      setText(null);
      setHorizontalAlignment(SwingConstants.CENTER);
    } else {
      setText(labelText);
      setHorizontalAlignment(SwingConstants.LEFT);
    }
    revalidate();
  }

  public boolean isCollapsed() {
    return collapsed;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    if (collapsed) {
      d.width = 50;
    }
    d.height = Math.max(d.height, 36);
    return d;
  }

  @Override
  public Dimension getMaximumSize() {
    Dimension d = super.getMaximumSize();
    d.height = getPreferredSize().height;
    return d;
  }
}
