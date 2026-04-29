/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A popup menu used for sidebar sub-items (File, Edit, Tools, Help).
 * Displayed when clicking a sidebar navigation item.
 */
public class SidebarSubmenu extends JPopupMenu {

  public SidebarSubmenu() {
    setLightWeightPopupEnabled(true);
  }

  public JMenuItem addItem(String text, KeyStroke accelerator, ActionListener action) {
    JMenuItem item = new JMenuItem(text);
    if (accelerator != null) {
      item.setAccelerator(accelerator);
    }
    if (action != null) {
      item.addActionListener(action);
    }
    add(item);
    return item;
  }

  public JMenuItem addItem(String text, ActionListener action) {
    return addItem(text, null, action);
  }

  public void showBelow(Component invoker) {
    show(invoker, invoker.getWidth(), 0);
  }
}
