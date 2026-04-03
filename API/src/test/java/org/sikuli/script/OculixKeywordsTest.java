/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OculixKeywordsTest {

  private OculixKeywords kw;
  private Region region;

  @BeforeEach
  void setUp() {
    region = new Region(0, 0, 100, 100);
    kw = new OculixKeywords(region);
  }

  // ── Constructor & Region management ───────────────────────────────────

  @Test
  void constructorRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        new OculixKeywords(null));
  }

  @Test
  void setRegionRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.setRegion(null));
  }

  @Test
  void getRegionReturnsConstructorRegion() {
    assertSame(region, kw.getRegion());
  }

  @Test
  void setRegionUpdatesRegion() {
    Region r2 = new Region(50, 50, 200, 200);
    kw.setRegion(r2);
    assertSame(r2, kw.getRegion());
  }

  // ── Timeout ───────────────────────────────────────────────────────────

  @Test
  void defaultTimeoutIs3() {
    assertEquals(3.0, kw.getTimeout(), 0.001);
  }

  @Test
  void setTimeoutUpdatesValue() {
    kw.setTimeout(10.0);
    assertEquals(10.0, kw.getTimeout(), 0.001);
  }

  // ── clickText guards ──────────────────────────────────────────────────

  @Test
  void clickTextRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.clickText(null));
  }

  @Test
  void clickTextRejectsEmpty() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.clickText(""));
  }

  @Test
  void regionClickTextRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.regionClickText(null));
  }

  @Test
  void regionClickTextRejectsEmpty() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.regionClickText(""));
  }

  // ── clickOnMatch / clickOnRegion guards ───────────────────────────────

  @Test
  void clickOnMatchRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.clickOnMatch(null));
  }

  @Test
  void clickOnRegionRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.clickOnRegion(null));
  }

  @Test
  void doubleClickOnMatchRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.doubleClickOnMatch(null));
  }

  @Test
  void doubleClickOnRegionRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        kw.doubleClickOnRegion(null));
  }
}
