/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OculixKeywordsTest {

  @Test
  void constructorRejectsNull() {
    assertThrows(IllegalArgumentException.class, () ->
        new OculixKeywords(null));
  }

  @Test
  void setRegionRejectsNull() {
    Region region = new Region(0, 0, 100, 100);
    OculixKeywords kw = new OculixKeywords(region);
    assertThrows(IllegalArgumentException.class, () ->
        kw.setRegion(null));
  }

  @Test
  void getRegionReturnsConstructorRegion() {
    Region region = new Region(10, 20, 300, 400);
    OculixKeywords kw = new OculixKeywords(region);
    assertSame(region, kw.getRegion());
  }

  @Test
  void setRegionUpdatesRegion() {
    Region r1 = new Region(0, 0, 100, 100);
    Region r2 = new Region(50, 50, 200, 200);
    OculixKeywords kw = new OculixKeywords(r1);
    kw.setRegion(r2);
    assertSame(r2, kw.getRegion());
  }
}
