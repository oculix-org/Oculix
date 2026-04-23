/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

/**
 * Thrown when a screen operation fails (image not found, click failed, OCR error, etc.)
 */
public class ScreenOperationException extends SikuliXception {

  public ScreenOperationException(String message) {
    super(message);
  }

  public ScreenOperationException(String message, Exception cause) {
    super(message, cause);
  }
}
