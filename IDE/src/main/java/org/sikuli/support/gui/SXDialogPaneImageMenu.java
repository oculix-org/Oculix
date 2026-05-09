package org.sikuli.support.gui;

import org.sikuli.ide.EditorImageButton;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

/**
 * Right-click popup menu for an embedded image button — shows
 * Rename / Optimize / As Pattern. The text template lives in
 * {@code sxidepaneimagemenu.txt}.
 *
 * <p>Filters "As Pattern" out of the menu when the underlying
 * {@link EditorImageButton} has already been promoted to a Pattern
 * (so re-clicking would be a no-op anyway). User feedback was that
 * the entry should disappear once it has done its job, not stay
 * around as a dead option.
 */
public class SXDialogPaneImageMenu extends SXDialogPaneImage {
  public SXDialogPaneImageMenu(Point where, Object... parms) {
    super("sxidepaneimagemenu", where, parms);
    hideAsPatternIfAlreadyPromoted();
  }

  /**
   * If the originating button is already a Pattern, drop the "As Pattern"
   * action from the rendered menu. Done by:
   *   1. reflecting into the inherited {@code dialogLines} list (package-
   *      private in SXDialog so a subclass in a different package needs
   *      reflection — small price for keeping the parent encapsulated),
   *   2. removing the matching {@code ActionItem} entry,
   *   3. calling {@link SXDialog#reRun()} which clears the pane and re-
   *      packs from the trimmed list.
   *
   * <p>If anything goes wrong (reflection blocked, list shape changed),
   * the method silently leaves the original menu intact — re-clicking
   * "As Pattern" is already idempotent at the action-handler level
   * ({@link EditorImageButton#promoteToPattern()} returns early when
   * already promoted), so a stale menu entry is cosmetic, not broken.
   */
  private void hideAsPatternIfAlreadyPromoted() {
    Object btn = getOptions().get("imgBtn");
    if (!(btn instanceof EditorImageButton)) return;
    if (!((EditorImageButton) btn).isPromotedToPattern()) return;
    try {
      Field f = SXDialog.class.getDeclaredField("dialogLines");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<Object> lines = (List<Object>) f.get(this);
      boolean removed = false;
      for (Iterator<Object> it = lines.iterator(); it.hasNext(); ) {
        Object item = it.next();
        // ActionItem stores the command string under the "command" field —
        // "pattern" maps to the "As Pattern" line in the .txt template.
        try {
          Field cmdField = item.getClass().getDeclaredField("command");
          cmdField.setAccessible(true);
          Object cmd = cmdField.get(item);
          if ("pattern".equals(cmd)) {
            it.remove();
            removed = true;
            break;
          }
        } catch (NoSuchFieldException ignored) {
          // Not an ActionItem — skip.
        }
      }
      if (removed) {
        reRun();
      }
    } catch (Throwable ignored) {
      // Reflection or layout issue — leave menu untouched. Idempotent
      // promote() already prevents double-promotion at the action level.
    }
  }
}
