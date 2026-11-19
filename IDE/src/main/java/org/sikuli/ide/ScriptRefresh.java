/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

/**
 * Brings an open script back in line with its file on disk, for scripts edited outside the IDE.
 * <p>
 * Reading only: a refresh never writes the file, and unsaved work is dropped only once the user agrees.
 */
final class ScriptRefresh {

  enum Outcome { RELOADED, KEPT, FAILED }

  private ScriptRefresh() {
  }

  static Outcome refresh(OpenScript script, BooleanSupplier discardConfirmed) {
    String text;
    try {
      text = new String(Files.readAllBytes(script.file().toPath()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return Outcome.FAILED;
    }
    if (script.hasUnsavedChanges() && !discardConfirmed.getAsBoolean()) {
      return Outcome.KEPT;
    }
    script.replaceContent(text);
    return Outcome.RELOADED;
  }
}
