/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

/**
 * Thrown when a wait operation exceeds its timeout.
 */
public class OculixTimeoutException extends SikuliXception {

  public OculixTimeoutException(String message) {
    super(message);
  }

  public OculixTimeoutException(String message, Exception cause) {
    super(message, cause);
  }
}
