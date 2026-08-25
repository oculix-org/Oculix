/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sikuli.natives.OSUtil.OsProcess;
import org.sikuli.natives.OSUtil.OsWindow;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #444 invariants for the whole-window vs ROI Region duality:
 * <ul>
 *   <li>{@code Region.forWindow()} is the only path that sets tracks=true</li>
 *   <li>the copy paths propagate sourceWindow AND tracks verbatim</li>
 *   <li>every other path (setSourceWindow, derivations, Match funnels) leaves
 *       or forces tracks=false</li>
 *   <li>the derivation funnel is contains-guarded: no inheritance when the
 *       rect leaves the window</li>
 *   <li>{@code captureSelf} routes by tracks, not by coordinates</li>
 *   <li>{@code highlight} routes to highlightRegionNative when tracks=false</li>
 *   <li>the native capture cache is invalidated on any sourceWindow change</li>
 * </ul>
 *
 * <p>Tests use a deterministic {@link StubWindow} to avoid pulling in a real
 * OS window handle. Skipped on headless environments — {@code Region.forWindow}
 * calls {@code initScreen(null)} which enumerates the AWT screen devices.
 */
class RegionSourceWindowTest {

  /** Deterministic OsWindow stub: bounds + capture image are set by the test. */
  static class StubWindow implements OsWindow {
    Rectangle bounds;
    BufferedImage nextCapture;
    int highlightNativeCalls = 0;
    int highlightRegionCalls = 0;
    Rectangle lastRoi = null;

    StubWindow(Rectangle b) { this.bounds = b; }

    @Override public OsProcess getProcess() { return null; }
    @Override public String getTitle() { return "stub"; }
    @Override public Rectangle getBounds() { return bounds; }
    @Override public boolean focus() { return true; }
    @Override public boolean minimize() { return false; }
    @Override public boolean maximize() { return false; }
    @Override public boolean restore() { return false; }
    @Override public BufferedImage captureNative(Rectangle b) { return nextCapture; }
    @Override public boolean highlightNative(int argb, double secs) {
      highlightNativeCalls++;
      return true;
    }
    @Override public boolean highlightRegionNative(Rectangle roi, int argb, double secs) {
      highlightRegionCalls++;
      lastRoi = roi;
      return true;
    }
  }

  @BeforeEach
  void requireDisplay() {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
        "Region tests require a live GraphicsEnvironment (initScreen needs Screen)");
  }

  // ---------- forWindow(): sole path that asserts tracks=true ----------

  @Test
  void forWindowSetsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(100, 200, 800, 600));
    Region r = Region.forWindow(w);
    assertNotNull(r);
    assertSame(w, r.getSourceWindow());
    assertTrue(r.tracksSourceWindowBounds, "forWindow is the ONE path that sets tracks=true");
    assertEquals(100, r.x);
    assertEquals(200, r.y);
    assertEquals(800, r.w);
    assertEquals(600, r.h);
  }

  @Test
  void forWindowNullReturnsNull() {
    assertNull(Region.forWindow(null));
  }

  @Test
  void forWindowNullBoundsReturnsNull() {
    assertNull(Region.forWindow(new StubWindow(null)));
  }

  // ---------- Copy paths propagate sourceWindow AND tracks ----------

  @Test
  void newRegionCopyPropagatesProvenanceAndTracks() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region copy = new Region(root);
    assertSame(w, copy.getSourceWindow());
    assertTrue(copy.tracksSourceWindowBounds, "copy of whole-window Region stays whole-window");
  }

  @Test
  void regionCreateCopyPropagatesProvenanceAndTracks() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region copy = Region.create(root);
    assertSame(w, copy.getSourceWindow());
    assertTrue(copy.tracksSourceWindowBounds);
    assertEquals(root.x, copy.x);
    assertEquals(root.w, copy.w);
  }

  @Test
  void copyOfRoiStaysRoi() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region roi = root.getInset(new Region(10, 10, 100, 50));
    assertFalse(roi.tracksSourceWindowBounds);
    Region roiCopy = new Region(roi);
    assertSame(w, roiCopy.getSourceWindow());
    assertFalse(roiCopy.tracksSourceWindowBounds, "copy preserves tracks verbatim — a ROI copy stays a ROI");
  }

  // ---------- setSourceWindow: cache invalidation + does NOT touch tracks ----------

  @Test
  void setSourceWindowChangeInvalidatesCache() {
    StubWindow w1 = new StubWindow(new Rectangle(0, 0, 400, 300));
    w1.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region r = Region.forWindow(w1);
    r.getImage();  // populates cache via captureSelf
    assertNotNull(r.cachedNativeImage, "cache populated by first getImage");
    StubWindow w2 = new StubWindow(new Rectangle(0, 0, 500, 400));
    r.setSourceWindow(w2);
    assertNull(r.cachedNativeImage, "cache cleared on window change");
    assertNull(r.cachedNativeBounds);
  }

  @Test
  void setSourceWindowDetachInvalidatesCache() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    w.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region r = Region.forWindow(w);
    r.getImage();
    assertNotNull(r.cachedNativeImage);
    r.setSourceWindow(null);
    assertNull(r.cachedNativeImage);
  }

  @Test
  void setSourceWindowSameInstanceKeepsCache() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    w.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region r = Region.forWindow(w);
    r.getImage();
    BufferedImage cached = r.cachedNativeImage;
    r.setSourceWindow(w);  // same instance
    assertSame(cached, r.cachedNativeImage, "no-op re-attach keeps cache warm");
  }

  @Test
  void setSourceWindowDoesNotChangeTracks() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = new Region(0, 0, 400, 300);
    assertFalse(r.tracksSourceWindowBounds);
    r.setSourceWindow(w);
    assertFalse(r.tracksSourceWindowBounds, "setSourceWindow never asserts tracks=true; only forWindow does");
  }

  // ---------- setX/Y/W/H flip tracks=false on window-backed Region ----------

  @Test
  void setXFlipsTracksOnWholeWindowRegion() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    assertTrue(r.tracksSourceWindowBounds);
    r.setX(50);
    assertFalse(r.tracksSourceWindowBounds, "direct mutation of x turns whole-window Region into ROI");
    assertSame(w, r.getSourceWindow(), "provenance preserved on mutation");
  }

  @Test
  void setYFlipsTracks() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    r.setY(10);
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setWFlipsTracks() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    r.setW(100);
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setHFlipsTracks() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    r.setH(100);
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setterOnNonWindowRegionIsNoOp() {
    Region r = new Region(0, 0, 400, 300);
    assertFalse(r.tracksSourceWindowBounds);
    r.setX(50);
    assertFalse(r.tracksSourceWindowBounds, "no window → tracks stays false");
  }

  // ---------- Derivation funnel: inherited only when contained ----------

  @Test
  void getInsetInsideWindowInheritsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region inset = root.getInset(new Region(10, 10, 100, 50));
    assertSame(w, inset.getSourceWindow(), "sub-rect inside → inherits");
    assertFalse(inset.tracksSourceWindowBounds, "derivation always tracks=false");
    assertEquals(10, inset.x);
    assertEquals(100, inset.w);
    assertEquals(50, inset.h);
  }

  @Test
  void aboveExtendsOutsideAndDropsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region above = root.above(100);  // rect at (0, -100, 400, 100) — outside
    assertNull(above.getSourceWindow(), "above() extends outside → funnel refuses");
  }

  @Test
  void offsetWithinWindowInheritsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region roi = root.getInset(new Region(10, 10, 100, 50));
    Region moved = roi.offset(50, 50);  // (60, 60, 100, 50) — still inside 400x300
    assertSame(w, moved.getSourceWindow(), "offset staying inside → inherits");
    assertFalse(moved.tracksSourceWindowBounds);
  }

  @Test
  void offsetLeavingWindowDropsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region roi = root.getInset(new Region(10, 10, 100, 50));
    Region moved = roi.offset(1000, 1000);  // way outside
    assertNull(moved.getSourceWindow(), "offset leaving window → funnel refuses");
  }

  // ---------- Union/intersection two-parent guard ----------

  @Test
  void unionOfSameWindowInherits() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region a = root.getInset(new Region(10, 10, 50, 50));
    Region b = root.getInset(new Region(70, 10, 50, 50));
    Region u = a.union(b);
    assertSame(w, u.getSourceWindow(), "same-window parents → inherit");
    assertFalse(u.tracksSourceWindowBounds);
  }

  @Test
  void unionOfDifferentWindowsRefusesInheritance() {
    StubWindow w1 = new StubWindow(new Rectangle(0, 0, 400, 300));
    StubWindow w2 = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region a = Region.forWindow(w1).getInset(new Region(10, 10, 50, 50));
    Region b = Region.forWindow(w2).getInset(new Region(10, 10, 50, 50));
    Region u = a.union(b);
    assertNull(u.getSourceWindow(), "different-window parents → refuse to inherit");
  }

  // ---------- captureSelf routing: full window vs ROI crop ----------

  @Test
  void captureSelfOnWholeWindowReturnsFullNativeImage() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    w.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region r = Region.forWindow(w);
    assertTrue(r.tracksSourceWindowBounds);
    ScreenImage si = r.captureSelf(r);
    assertNotNull(si);
    assertSame(w.nextCapture, si.getImage(), "tracks=true returns the full native bitmap unchanged");
  }

  @Test
  void captureSelfOnRoiReturnsCrop() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    w.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region r = Region.forWindow(w);
    Region roi = r.getInset(new Region(10, 10, 100, 50));
    assertFalse(roi.tracksSourceWindowBounds);
    ScreenImage si = roi.captureSelf(roi);
    assertNotNull(si);
    assertEquals(100, si.getImage().getWidth(), "tracks=false returns a crop of the requested size");
    assertEquals(50, si.getImage().getHeight());
  }

  // ---------- highlight routing: whole HWND vs ROI overlay ----------

  @Test
  void highlightOnWholeWindowCallsHighlightNative() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    r.doHighlight(0.5, null);
    assertEquals(1, w.highlightNativeCalls, "tracks=true → highlightNative (whole HWND)");
    assertEquals(0, w.highlightRegionCalls);
  }

  @Test
  void highlightOnRoiCallsHighlightRegionNative() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    Region roi = r.getInset(new Region(10, 10, 100, 50));
    roi.doHighlight(0.5, null);
    assertEquals(0, w.highlightNativeCalls);
    assertEquals(1, w.highlightRegionCalls, "tracks=false → highlightRegionNative (ROI overlay)");
    assertEquals(new Rectangle(10, 10, 100, 50), w.lastRoi,
        "ROI overlay receives the sub-rect in logical coords");
  }
}
