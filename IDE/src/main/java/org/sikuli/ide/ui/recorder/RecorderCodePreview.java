/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import javax.swing.*;

/**
 * Live preview of generated Jython code lines.
 * Phase 1: empty JList placeholder. Renderer and logic added in Phase 5.
 */
public class RecorderCodePreview extends JList<String> {

  private final DefaultListModel<String> model;

  public RecorderCodePreview() {
    model = new DefaultListModel<>();
    setModel(model);
  }

  public void addLine(String codeLine) {
    model.addElement(codeLine);
    ensureIndexIsVisible(model.size() - 1);
  }

  public void clear() {
    model.clear();
  }
}
