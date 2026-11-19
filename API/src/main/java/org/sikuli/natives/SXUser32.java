/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.natives;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinUser.BLENDFUNCTION;
import com.sun.jna.platform.win32.WinUser.SIZE;
import com.sun.jna.win32.W32APIOptions;

public interface SXUser32 extends User32 {

  SXUser32 INSTANCE = (SXUser32) Native.load("user32", SXUser32.class, W32APIOptions.DEFAULT_OPTIONS);

  short GetKeyState(int vKey);

  int MapVirtualKeyExW (int uCode, int nMapType, int dwhkl);

  int MapVirtualKeyW(int uCode, int uMapType);

  boolean GetKeyboardState(byte[] lpKeyState);

  int ToUnicodeEx(int wVirtKey, int wScanCode, byte[] lpKeyState, char[] pwszBuff, int cchBuff, int wFlags, int dwhkl);

  void keybd_event(byte bVk, byte bScan, DWORD dwFlags, ULONG_PTR dwExtraInfo);

  // ── #444 SpanningHighlight: layered-window overlay in physical pixels ──
  // Draws a single continuous red frame around a window that may straddle
  // several monitors at different DPI scales, by compositing a premultiplied
  // ARGB bitmap onto a WS_EX_LAYERED window positioned in the physical
  // virtual-screen coordinate space (where a straddling window IS one rect).

  boolean UpdateLayeredWindow(HWND hwnd, HDC hdcDst, POINT pptDst, SIZE psize,
      HDC hdcSrc, POINT pptSrc, int crKey, BLENDFUNCTION pblend, int dwFlags);

  // CreateWindowEx and DestroyWindow are already declared by JNA's User32 —
  // we reuse those directly (redeclaring CreateWindowEx here made the call
  // ambiguous). UpdateLayeredWindow is the only one JNA's User32 lacks.
}

