/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Image file names written in a script, and how they follow the file they name.
 * <p>
 * A reference is read the way the engine reads it: relative to the folder of the
 * script that carries it. Only a reference that resolves onto the moved entry is
 * rewritten, so a script that names its own image of the same name is left alone.
 */
public final class ImageReferences {

  private static final Pattern LITERAL = Pattern.compile(
      "([\"'])([^\"'\\n\\r]*?\\.(?:png|jpg|jpeg))\\1", Pattern.CASE_INSENSITIVE);

  private ImageReferences() {
  }

  /**
   * Rewrite the image references of one script after an entry moved on disk.
   *
   * @param code         the script text
   * @param scriptFolder the folder the script lives in, which its references are relative to
   * @param source       the entry as it was before the move, a file or a folder
   * @param target       the same entry at its new place
   * @return the script text, unchanged when no reference pointed at the entry
   */
  public static String rebase(String code, Path scriptFolder, Path source, Path target) {
    if (code == null || code.isEmpty() || scriptFolder == null || source == null || target == null) {
      return code;
    }
    Path folder = scriptFolder.toAbsolutePath().normalize();
    Path from = source.toAbsolutePath().normalize();
    Path to = target.toAbsolutePath().normalize();
    if (from.equals(to)) {
      return code;
    }
    Matcher matcher = LITERAL.matcher(code);
    StringBuilder out = new StringBuilder();
    boolean changed = false;
    while (matcher.find()) {
      String quote = matcher.group(1);
      String reference = matcher.group(2);
      String rewritten = rebaseOne(reference, folder, from, to);
      if (rewritten == null) {
        matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
        continue;
      }
      changed = true;
      matcher.appendReplacement(out, Matcher.quoteReplacement(quote + rewritten + quote));
    }
    if (!changed) {
      return code;
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /**
   * Rewrite the image references of a script open in a tab after an entry moved on disk.
   * <p>
   * A tab without unsaved work is saved, as the scripts without a tab are. A tab with unsaved
   * work is only flagged: saving it would also save work the user has not chosen to keep.
   *
   * @return true when the script named the entry and was rewritten
   */
  static boolean follow(OpenScript script, Path source, Path target) throws IOException {
    String code = script.text();
    String rebased = rebase(code, script.file().toPath().getParent(), source, target);
    if (rebased.equals(code)) {
      return false;
    }
    boolean unsaved = script.hasUnsavedChanges();
    script.replaceContent(rebased);
    if (unsaved) {
      script.markUnsaved();
    } else {
      script.save();
    }
    return true;
  }

  /** The new spelling of one reference, or null when it does not name the moved entry. */
  private static String rebaseOne(String reference, Path folder, Path from, Path to) {
    if (reference.isEmpty()) {
      return null;
    }
    Path resolved;
    try {
      resolved = folder.resolve(reference).normalize();
    } catch (RuntimeException e) {
      return null;
    }
    Path moved;
    if (resolved.equals(from)) {
      moved = to;
    } else if (resolved.startsWith(from)) {
      moved = to.resolve(from.relativize(resolved));
    } else {
      return null;
    }
    return spell(folder, moved);
  }

  /** Relative to the script folder when the file stays under it, absolute otherwise. */
  private static String spell(Path folder, Path file) {
    Path relative;
    try {
      relative = folder.relativize(file);
    } catch (IllegalArgumentException e) {
      return file.toString().replace('\\', '/');
    }
    String text = relative.toString().replace('\\', '/');
    if (text.isEmpty() || text.startsWith("..")) {
      return file.toString().replace('\\', '/');
    }
    return text;
  }
}
