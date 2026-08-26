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
    int captureNativeCalls = 0;
    int highlightNativeCalls = 0;
    int highlightRegionCalls = 0;
    Rectangle lastRoi = null;
    /** When set, overrides {@link #nextCapture} and is invoked per call — so a
     *  test can prove captureSelf() photographs freshly rather than caching. */
    java.util.function.Supplier<BufferedImage> captureSupplier;

    StubWindow(Rectangle b) { this.bounds = b; }

    @Override public OsProcess getProcess() { return null; }
    @Override public String getTitle() { return "stub"; }
    @Override public Rectangle getBounds() { return bounds; }
    @Override public boolean focus() { return true; }
    @Override public boolean minimize() { return false; }
    @Override public boolean maximize() { return false; }
    @Override public boolean restore() { return false; }
    @Override public BufferedImage captureNative(Rectangle b) {
      captureNativeCalls++;
      return captureSupplier != null ? captureSupplier.get() : nextCapture;
    }
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

  // ---------- setSourceWindow: no cache anymore (removed in commit removing NATIVE_CAPTURE_CACHE_TTL_MS) ----------

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
    // setX(50) with initial w=400 pushes right edge to 450, past wb.width=400.
    // The rect is no longer contained in the window, so markAsDerivedWindowRegion
    // detaches sourceWindow to preserve the "sourceWindow != null ⇒ contained"
    // invariant (mirrors deriveWithinWindow's refusal on out-of-window rects).
    assertNull(r.getSourceWindow(),
        "mutation leaving the window bounds detaches sourceWindow (invariant guard)");
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

  // ---------- captureSelf is LIVE — no timed cache after Sol's re-review ----------

  @Test
  void captureSelfCallsNativeCaptureFreshEveryCall() {
    // Before the cache removal, captureSelf reused cachedNativeImage for
    // 3 seconds. That silently froze waitForStable / wait / exists on stale
    // pixels. The primitive is now live: every call must reach
    // sourceWindow.captureNative(). This test locks the contract.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    w.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region r = Region.forWindow(w);
    r.captureSelf(r);
    r.captureSelf(r);
    r.captureSelf(r);
    assertEquals(3, w.captureNativeCalls,
        "three captureSelf calls must produce three captureNative calls — no cache");
  }

  @Test
  void waitForStableSemanticsAreLive_captureSelfReflectsChangingPixels() {
    // Simulates what waitForStable/wait/exists need: consecutive captureSelf
    // calls must reflect a CHANGING window, not a snapshot. StubWindow's
    // captureSupplier yields a distinct BufferedImage per call with a
    // different sentinel colour, so any caching layer would show up as a
    // repeated image and fail the sameness check.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    java.util.concurrent.atomic.AtomicInteger frameCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    w.captureSupplier = () -> {
      int frame = frameCounter.incrementAndGet();
      BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
      // Fill with a per-frame sentinel colour so identity check is trivial.
      int rgb = 0xFF000000 | frame;
      for (int y = 0; y < 300; y++) {
        for (int x = 0; x < 400; x++) {
          img.setRGB(x, y, rgb);
        }
      }
      return img;
    };
    Region r = Region.forWindow(w);
    ScreenImage first = r.captureSelf(r);
    ScreenImage second = r.captureSelf(r);
    assertNotSame(first.getImage(), second.getImage(),
        "captureSelf must return distinct images across calls when the source is changing");
    // Sentinel check: first frame = 1, second frame = 2, encoded in pixel(0,0).
    assertEquals(0xFF000001, first.getImage().getRGB(0, 0));
    assertEquals(0xFF000002, second.getImage().getRGB(0, 0));
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

  @Test
  void persistentWholeWindowHighlightUsesSwing_notNative() {
    // highlight() / highlightOn() call doHighlight(-1, color) — persistent
    // toggle mode. The current native overlay (WS_EX_LAYERED) destroys the
    // window in its finally block right after Thread.sleep — so a secs <= 0
    // call would flash and vanish. The Swing Highlight below handles
    // persistent mode via doShow(-1); native must NOT be routed on secs <= 0.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    try {
      r.doHighlight(-1, null);   // persistent
    } catch (Throwable ignored) {
      // Swing fallback may fail in headless-ish setups; the invariant we
      // test lives upstream (native highlight refused on secs <= 0).
    }
    assertEquals(0, w.highlightNativeCalls,
        "persistent whole-window highlight must NOT route to highlightNative (would flash and vanish)");
    assertEquals(0, w.highlightRegionCalls);
  }

  @Test
  void persistentRoiHighlightUsesSwing_notNative() {
    // Same rule for ROI: persistent highlight on a window-backed ROI keeps
    // the Swing path so the toggle API stays functional.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region r = Region.forWindow(w);
    Region roi = r.getInset(new Region(10, 10, 100, 50));
    try {
      roi.doHighlight(-1, null);
    } catch (Throwable ignored) {
    }
    assertEquals(0, w.highlightRegionCalls,
        "persistent ROI highlight must NOT route to highlightRegionNative (would flash and vanish)");
    assertEquals(0, w.highlightNativeCalls);
  }

  // ---------- Spanning-logical tests (no physical mixed-DPI required) ----------
  // These tests simulate a window whose bounds logically straddle several
  // monitors (negative x, extreme width) — a shape initScreen used to mutilate
  // by biggest-intersection clipping. The #444 guards must preserve the bounds
  // verbatim through forWindow, setters and the derivation funnel. Passing
  // these tests on a mono-monitor machine is enough proof that no re-clip
  // sneaks back in later.

  @Test
  void forWindowOnConceptuallyStraddlingBoundsPreservesRect() {
    // Simulate a window spanning: origin at x=-500 (secondary monitor left
    // of primary), width 3000 (crossing two or three logical screens).
    // With sourceWindow attached, initScreen's biggest-intersection clip
    // must be skipped and the raw window rect must survive verbatim.
    Rectangle straddling = new Rectangle(-500, 100, 3000, 600);
    Region r = Region.forWindow(new StubWindow(straddling));
    assertNotNull(r);
    assertTrue(r.tracksSourceWindowBounds);
    assertEquals(-500, r.x, "spanning: negative x preserved (no clip)");
    assertEquals(100, r.y);
    assertEquals(3000, r.w, "spanning: full width preserved across virtual screens");
    assertEquals(600, r.h);
  }

  @Test
  void setterOnStraddlingWindowKeepsBoundsUnclipped() {
    // Same guarantee on the setter path: initScreen(null) runs after setW,
    // and with sourceWindow attached, the biggest-intersection clip that
    // would normally rewrite w back to a single monitor's width must be
    // skipped. tracks flips to false (mutation = ROI) but bounds survive.
    Rectangle straddling = new Rectangle(-500, 100, 3000, 600);
    StubWindow w = new StubWindow(straddling);
    Region r = Region.forWindow(w);
    r.setW(2500);
    assertEquals(2500, r.w, "spanning: setter keeps the requested width, no clip");
    assertEquals(-500, r.x, "spanning: origin untouched by setter");
    assertEquals(100, r.y);
    assertEquals(600, r.h);
    assertFalse(r.tracksSourceWindowBounds, "setter flips to ROI");
    assertSame(w, r.getSourceWindow(), "provenance preserved through the mutation");
  }

  // ---------- Sol's audit follow-up: extended-setter coverage + HWND-change + pixel sentinels ----------
  //
  // Every direct bounds-mutating setter must apply the same invariant guard
  // (markAsDerivedWindowRegion): tracks flips to false, and if the resulting
  // rect leaves the window bounds sourceWindow is detached. The tests below
  // prove the "inside → provenance preserved" case for each setter — the
  // "outside → provenance dropped" case is covered globally by
  // setterLeavingWindowDropsProvenance.

  /** Helper: shrink a window-backed Region to a ROI comfortably inside wb,
   * so the individual setter tests can mutate freely without leaving bounds. */
  private static Region roiInside(StubWindow w, int x, int y, int width, int height) {
    Region r = Region.forWindow(w);
    r.setSize(width, height);   // shrink w/h first
    r.setLocation(new Location(x, y));  // then place
    assertSame(w, r.getSourceWindow(), "setup precondition: ROI still inside window");
    return r;
  }

  @Test
  void setCenterInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);  // (0, 0, 100, 80)
    r.setCenter(new Location(200, 200));  // → (150, 160, 100, 80), inside
    assertSame(w, r.getSourceWindow(), "setCenter inside window preserves sourceWindow");
    assertFalse(r.tracksSourceWindowBounds, "setCenter always flips tracks=false");
  }

  @Test
  void setTopRightInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setTopRight(new Location(300, 100));  // → (201, 100, 100, 80), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setBottomLeftInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setBottomLeft(new Location(50, 300));  // → (50, 221, 100, 80), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setBottomRightInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setBottomRight(new Location(400, 300));  // → (301, 221, 100, 80), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setSizeInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setSize(200, 150);  // → (0, 0, 200, 150), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setRectInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setRect(50, 50, 200, 150);  // → (50, 50, 200, 150), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setLocationInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setLocation(new Location(200, 200));  // → (200, 200, 100, 80), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void addInsideWindowKeepsProvenance() {
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    // add() is grow-like, so start with margin on all sides.
    Region r = roiInside(w, 100, 100, 100, 80);  // (100, 100, 100, 80)
    r.add(10, 10, 10, 10);  // → (90, 90, 120, 100), inside
    assertSame(w, r.getSourceWindow());
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setterLeavingWindowDropsProvenance() {
    // Any direct mutator moving the rect outside the window bounds must
    // detach sourceWindow. setLocation is a convenient probe — one setter
    // is enough to prove the invariant since they all funnel through
    // markAsDerivedWindowRegion which owns the check.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = Region.forWindow(w);
    r.setLocation(new Location(5000, 5000));  // way outside wb
    assertNull(r.getSourceWindow(),
        "setLocation past the window bounds detaches sourceWindow (invariant guard)");
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void delegatingSetterAlsoAppliesInvariant() {
    // setTopLeft(loc) delegates to setLocation(loc). The invariant must still
    // fire — proves the transitive inheritance through delegation.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 500, 400));
    Region r = roiInside(w, 0, 0, 100, 80);
    r.setTopLeft(new Location(200, 200));  // → (200, 200, 100, 80), inside
    assertSame(w, r.getSourceWindow(), "delegating setter reaches markAsDerivedWindowRegion");
    assertFalse(r.tracksSourceWindowBounds);
  }

  @Test
  void setSourceWindowChangeToDifferentHwndFlipsTracks() {
    // The invariant Sol pointed out: a HWND change cannot silently keep
    // tracks=true — that would make the Region claim to be the WHOLE new
    // window, which is a lie unless forWindow was used explicitly.
    StubWindow w1 = new StubWindow(new Rectangle(0, 0, 400, 300));
    StubWindow w2 = new StubWindow(new Rectangle(0, 0, 800, 600));
    Region r = Region.forWindow(w1);
    assertTrue(r.tracksSourceWindowBounds);
    r.setSourceWindow(w2);
    assertFalse(r.tracksSourceWindowBounds,
        "HWND change must flip tracks=false; only forWindow may claim whole-window identity");
    assertSame(w2, r.getSourceWindow());
  }

  @Test
  void captureSelfOnRoiCropsCorrectPixels() {
    // Pixel sentinels: paint the native capture with a decodable pattern
    // so we can assert the crop comes from the RIGHT window coordinates,
    // not just that it has the right size. Without this, a bug in localX =
    // cx - wb.x (e.g. wrong sign, off-by-one, missed offset) would produce
    // a 100x50 crop from the wrong region and the previous test would still
    // pass silently.
    //
    // Encoding: pixel(x, y) = 0xFF000000 | (x << 8) | y  — one byte per
    // coord, fits screens up to 255x255 which is enough for a 400x300 stub.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    BufferedImage bmp = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 300; y++) {
      for (int x = 0; x < 400; x++) {
        bmp.setRGB(x, y, 0xFF000000 | (x << 8) | y);
      }
    }
    w.nextCapture = bmp;

    Region root = Region.forWindow(w);
    Region roi = root.getInset(new Region(10, 10, 100, 50));  // absolute (10, 10, 100, 50)
    ScreenImage si = roi.captureSelf(roi);
    assertNotNull(si);
    BufferedImage crop = si.getImage();
    assertEquals(100, crop.getWidth());
    assertEquals(50, crop.getHeight());

    // crop pixel (0, 0) MUST come from bmp pixel (10, 10)
    assertEquals(0xFF000000 | (10 << 8) | 10, crop.getRGB(0, 0),
        "crop origin comes from the ROI's top-left in the native bitmap");
    // crop pixel (50, 25) MUST come from bmp pixel (60, 35)
    assertEquals(0xFF000000 | (60 << 8) | 35, crop.getRGB(50, 25),
        "crop interior comes from the shifted native coordinates");
    // crop pixel (99, 49) MUST come from bmp pixel (109, 59)
    assertEquals(0xFF000000 | (109 << 8) | 59, crop.getRGB(99, 49),
        "crop bottom-right comes from the ROI's bottom-right in the native bitmap");
  }

  // ---------- Sol's re-review: ROI containment revalidated at native use ----------

  @Test
  void roiAfterWindowMoveFallsBackFromNativeCapture() {
    // markAsDerivedWindowRegion checks containment at ROI construction time.
    // OsWindow is a live object though — the user can move the actual OS
    // window without any OculiX write, and the invariant becomes silently
    // false. captureSelf now revalidates just before the native call: if the
    // ROI escapes the LIVE window bounds, sourceWindow is detached and the
    // classic Screen path takes over. captureNative is NOT invoked.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    w.nextCapture = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
    Region root = Region.forWindow(w);
    Region roi = root.getInset(new Region(10, 10, 100, 50));  // (10, 10, 100, 50) inside
    assertSame(w, roi.getSourceWindow());

    // Simulate the user dragging the OS window elsewhere. The Region's own
    // x/y/w/h stay (10, 10, 100, 50) — no OculiX setter fired — but the
    // window's live bounds jumped to (5000, 5000, 400, 300), which no longer
    // contains the ROI.
    w.bounds = new Rectangle(5000, 5000, 400, 300);

    int callsBefore = w.captureNativeCalls;
    try {
      roi.captureSelf(roi);
    } catch (Throwable ignored) {
      // getScreen().capture(...) may throw in tests without a real display for
      // certain coords; the invariant we test lives upstream of that call.
    }
    assertNull(roi.getSourceWindow(),
        "ROI escaped the live window bounds → sourceWindow detached at capture time");
    assertEquals(callsBefore, w.captureNativeCalls,
        "detach happens BEFORE captureNative — no native call on a stale ROI");
  }

  @Test
  void roiAfterWindowMoveDoesNotUseNativeHighlight() {
    // Same revalidation on the highlight path: if the ROI escaped the live
    // window bounds since it was built, highlightRegionNative must not fire
    // (it would draw the overlay at the wrong physical position because
    // logicalToPhysical anchors on MonitorFromWindow(hWnd), not on the ROI).
    // Detach the provenance and fall through to Swing.
    StubWindow w = new StubWindow(new Rectangle(0, 0, 400, 300));
    Region root = Region.forWindow(w);
    Region roi = root.getInset(new Region(10, 10, 100, 50));
    assertSame(w, roi.getSourceWindow());

    // External window move — ROI now outside the live bounds.
    w.bounds = new Rectangle(5000, 5000, 400, 300);

    try {
      roi.doHighlight(0.1, null);
    } catch (Throwable ignored) {
      // Swing fallback may fail in headless-ish test setups; the invariant
      // we test is upstream (native highlight refused).
    }
    assertEquals(0, w.highlightRegionCalls,
        "ROI escaped live window bounds → highlightRegionNative NOT called");
    assertNull(roi.getSourceWindow(),
        "detach happens at highlight time on stale ROI");
  }

  @Test
  void uweStatusBarPattern_originalConstructorIsOrphan_copyThenMutateInheritsProvenance() {
    // #444 verbatim from uwekoenig's report on the issue tracker (22/07/2026):
    //
    //   Region window = App.focusedWindow();
    //   Region statusBar = new Region(window.getX(),
    //                                 window.getY() + window.getH() - statusBarHeightPx,
    //                                 window.getW(),
    //                                 statusBarHeightPx);
    //   statusBar.highlight(5);
    //
    // This test freezes the two possible outcomes side by side so any future
    // change of behaviour on either side breaks the build.

    StubWindow w = new StubWindow(new Rectangle(0, 0, 800, 600));
    Region window = Region.forWindow(w);
    int statusBarHeightPx = 30;

    // ── Original Uwe pattern: new Region(int, int, int, int) → orphan.
    // Architectural limit already documented by julienmerconsulting in the
    // 27/07 issue comment: "provenance is irreducible" — the 4-int
    // constructor cannot recover which window the numbers came from.
    Region statusBarOrphan = new Region(
        window.getX(),
        window.getY() + window.getH() - statusBarHeightPx,
        window.getW(),
        statusBarHeightPx);
    assertNull(statusBarOrphan.getSourceWindow(),
        "the 4-int constructor cannot recover provenance — architectural limit, not a bug");

    // ── Replacement pattern (what this branch enables): copy the
    // window-backed Region, then setRect() in ONE call. The copy propagates
    // sourceWindow (commit bc8044f2), setRect preserves it because the
    // target rect stays inside the window (commit d37752d6), and highlight()
    // routes to the native ROI overlay (commit bfa657b1).
    //
    // Doctrine note: prefer setRect(x,y,w,h) over sequential setX/Y/W/H
    // when replacing multiple fields at once. Sequential setters go through
    // markAsDerivedWindowRegion at every step; if the intermediate rect
    // (with only some fields updated) escapes the window, sourceWindow is
    // detached mid-sequence. setRect writes all four fields, then runs the
    // guard exactly once on the final rect.
    Region statusBarWindowBacked = new Region(window);
    statusBarWindowBacked.setRect(
        window.getX(),
        window.getY() + window.getH() - statusBarHeightPx,
        window.getW(),
        statusBarHeightPx);
    assertSame(w, statusBarWindowBacked.getSourceWindow(),
        "copy + setRect on a rect inside the window preserve provenance");
    assertFalse(statusBarWindowBacked.tracksSourceWindowBounds,
        "any direct setter flips tracks=false (this Region is a ROI, not the window)");
    assertEquals(window.getX(), statusBarWindowBacked.x);
    assertEquals(window.getY() + window.getH() - statusBarHeightPx, statusBarWindowBacked.y);
    assertEquals(window.getW(), statusBarWindowBacked.w);
    assertEquals(statusBarHeightPx, statusBarWindowBacked.h);

    // highlight() routes to the native ROI overlay, NOT to the Swing fallback.
    statusBarWindowBacked.doHighlight(0.1, null);
    assertEquals(1, w.highlightRegionCalls,
        "window-backed ROI highlight uses highlightRegionNative (physical-space overlay)");
    assertEquals(0, w.highlightNativeCalls,
        "highlightNative (whole HWND) is NOT called on a ROI");
  }

  @Test
  void derivedWithinStraddlingWindowKeepsBoundsUnclipped() {
    // A ROI that itself straddles multiple monitors — the exact shape of
    // Uwe's use case: a status-bar strip that spans the whole width of a
    // straddling window. The funnel must accept it (contained in window
    // bounds) and preserve the sub-rect verbatim.
    Rectangle straddling = new Rectangle(-500, 100, 3000, 600);
    StubWindow w = new StubWindow(straddling);
    Region root = Region.forWindow(w);
    // Status bar spanning the whole width, 30px tall, at the bottom.
    // The inset carrier has to be built via Region.forWindow(stub) rather
    // than new Region(int,int,int,int) — the plain constructor runs
    // initScreen(null) which, on a mono-monitor machine, would clip
    // inset.w to the physical screen width before getInset even reads it.
    // With a stub-backed inset, initScreen's biggest-intersection clip is
    // skipped and inset.w survives at 3000.
    Region insetSpec = Region.forWindow(new StubWindow(new Rectangle(0, 570, 3000, 30)));
    Region statusBar = root.getInset(insetSpec);
    assertSame(w, statusBar.getSourceWindow(),
        "spanning ROI inside spanning window: funnel accepts, provenance inherited");
    assertFalse(statusBar.tracksSourceWindowBounds, "derivation always tracks=false");
    assertEquals(-500, statusBar.x, "spanning ROI: absolute x preserved");
    assertEquals(670, statusBar.y);
    assertEquals(3000, statusBar.w, "spanning ROI: full width preserved");
    assertEquals(30, statusBar.h);
  }
}
