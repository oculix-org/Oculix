/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import java.io.File;
import java.io.IOException;

/** A script open in an editor tab, seen from the code that rewrites it from outside the keyboard. */
interface OpenScript {

  File file();

  /** The text as it would be saved, image buttons written back as their file names. */
  String text() throws IOException;

  boolean hasUnsavedChanges();

  /** Show this text as the saved state of the script: no unsaved mark, no undo back to the old text. */
  void replaceContent(String text);

  boolean save();

  /** Flag the tab as holding changes the file does not have yet. */
  void markUnsaved();
}
