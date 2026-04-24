/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsTest {

  @Test
  void screenOperationExceptionIsRuntimeException() {
    ScreenOperationException ex = new ScreenOperationException("test");
    assertInstanceOf(RuntimeException.class, ex);
    assertInstanceOf(SikuliXception.class, ex);
    assertEquals("test", ex.getMessage());
  }

  @Test
  void screenOperationExceptionWithCause() {
    Exception cause = new Exception("root cause");
    ScreenOperationException ex = new ScreenOperationException("failed", cause);
    assertEquals("failed", ex.getMessage());
    assertSame(cause, ex.getCause());
  }

  @Test
  void oculixTimeoutExceptionIsRuntimeException() {
    OculixTimeoutException ex = new OculixTimeoutException("timeout");
    assertInstanceOf(RuntimeException.class, ex);
    assertInstanceOf(SikuliXception.class, ex);
    assertEquals("timeout", ex.getMessage());
  }

  @Test
  void oculixTimeoutExceptionWithCause() {
    Exception cause = new Exception("root cause");
    OculixTimeoutException ex = new OculixTimeoutException("timeout", cause);
    assertEquals("timeout", ex.getMessage());
    assertSame(cause, ex.getCause());
  }
}
