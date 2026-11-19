/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** A tab holding a script in memory over a real file, the way the IDE holds it. */
final class EditorTab implements OpenScript {
  final File file;
  String text;
  boolean unsaved;
  int saves;

  EditorTab(File file, String text, boolean unsaved) {
    this.file = file;
    this.text = text;
    this.unsaved = unsaved;
  }

  @Override
  public File file() {
    return file;
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public boolean hasUnsavedChanges() {
    return unsaved;
  }

  @Override
  public void replaceContent(String text) {
    this.text = text;
    this.unsaved = false;
  }

  @Override
  public boolean save() {
    try {
      Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    unsaved = false;
    saves++;
    return true;
  }

  @Override
  public void markUnsaved() {
    unsaved = true;
  }
}
