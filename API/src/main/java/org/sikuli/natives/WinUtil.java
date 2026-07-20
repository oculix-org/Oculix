/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.natives;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI.SHELLEXECUTEINFO;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HMONITOR;
import com.sun.jna.platform.win32.WinUser.MONITORINFOEX;
import com.sun.jna.ptr.IntByReference;

public class WinUtil extends GenericOsUtil {

	static final SXUser32 user32 = SXUser32.INSTANCE;

	private static final int MONITOR_DEFAULTTONEAREST = 2;

	// GetWindowRect returns physical pixels; AWT (Robot, Region) works in
	// logical pixels since JEP 263 (JDK 9 Per-Monitor DPI Aware). To convert
	// correctly on mixed-DPI multi-monitor layouts we need the true physical
	// origin of the monitor containing the window, which AWT bounds alone
	// cannot give us -- gc.getBounds() * scale reconstructs a fictitious
	// physical space that only matches reality when every monitor shares
	// the primary's scale (#444).
	//
	// Voie 1: interrogate Windows canonically.
	//   1. MonitorFromWindow(hWnd) -> HMONITOR of the window
	//   2. GetMonitorInfoEx -> the monitor's real rcMonitor in the Windows
	//      virtual space
	//   3. Match to an AWT GraphicsDevice by probing MonitorFromPoint at each
	//      device's logical centre -- Windows numbering (\\.\DISPLAYn) is not
	//      contiguous with the JVM's index (\Displayn), name matching would
	//      break silently.
	//   4. Convert with the real origins:
	//        x_logical = logicalBounds.x + (x_physical - rcMonitor.left) / sx
	//        y_logical = logicalBounds.y + (y_physical - rcMonitor.top ) / sy
	//        w_logical = w_physical / sx, h_logical = h_physical / sy
	private static Rectangle physicalToLogical(HWND hWnd, Rectangle physical) {
		if (physical == null) {
			return null;
		}

		HMONITOR hMon = user32.MonitorFromWindow(hWnd, MONITOR_DEFAULTTONEAREST);
		MONITORINFOEX mi = new MONITORINFOEX();
		if (hMon == null || !user32.GetMonitorInfo(hMon, mi).booleanValue()) {
			return physical;
		}

		GraphicsDevice matched = null;
		for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			Rectangle b = gd.getDefaultConfiguration().getBounds();
			POINT.ByValue pt = new POINT.ByValue(b.x + b.width / 2, b.y + b.height / 2);
			HMONITOR probe = user32.MonitorFromPoint(pt, MONITOR_DEFAULTTONEAREST);
			if (probe != null && probe.equals(hMon)) {
				matched = gd;
				break;
			}
		}
		if (matched == null) {
			matched = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		}

		GraphicsConfiguration gc = matched.getDefaultConfiguration();
		Rectangle logicalBounds = gc.getBounds();
		AffineTransform tx = gc.getDefaultTransform();
		double sx = tx.getScaleX();
		double sy = tx.getScaleY();
		if (sx == 1.0 && sy == 1.0) {
			return physical;
		}

		int localPhysX = physical.x - mi.rcMonitor.left;
		int localPhysY = physical.y - mi.rcMonitor.top;
		return new Rectangle(
				logicalBounds.x + (int) Math.round(localPhysX / sx),
				logicalBounds.y + (int) Math.round(localPhysY / sy),
				(int) Math.round(physical.width / sx),
				(int) Math.round(physical.height / sy));
	}

	// PW_RENDERFULLCONTENT (Windows 8.1+) — asks the WM to render the window's
	// full content including DirectComposition surfaces. Regular PrintWindow
	// returns black on Chrome/Electron without this flag.
	private static final int PW_RENDERFULLCONTENT = 2;

	/**
	 * Native window capture via {@code PrintWindow(PW_RENDERFULLCONTENT)}: asks
	 * Windows' DWM to re-render the window into an off-screen HDC, bypassing
	 * {@code Robot.createScreenCapture} and its System-DPI / framebuffer clip
	 * limitations. Handles mixed-DPI multi-monitor layouts, windows straddling
	 * monitors at different scales, and windows partially off-screen — none of
	 * which the Robot path resolves correctly (#444).
	 *
	 * <p>The returned image is resized (bicubic) from physical pixels to
	 * {@code logicalBounds} so the caller receives an image whose dimensions
	 * match the Region's declared width and height. This preserves the classic
	 * SikuliX contract: coords from {@code find(pattern)} on the returned image
	 * translate directly to logical screen coords for {@code Robot.mouseMove}.
	 *
	 * <p>Returns {@code null} on any failure (null HWND, zero-size window,
	 * PrintWindow refusal, GDI allocation failure). Callers must fall back to
	 * the classic {@code Screen.capture()} path when null is returned.
	 *
	 * @param hWnd           the target window handle (must not be null)
	 * @param logicalBounds  target logical size (may be null → returns physical)
	 * @return the captured window content, or {@code null} on failure
	 */
	public static BufferedImage captureWindowNative(HWND hWnd, Rectangle logicalBounds) {
		if (hWnd == null) {
			return null;
		}

		RECT rect = new RECT();
		if (!user32.GetWindowRect(hWnd, rect)) {
			return null;
		}
		int physW = rect.right - rect.left;
		int physH = rect.bottom - rect.top;
		if (physW <= 0 || physH <= 0) {
			return null;
		}

		HDC hdcScreen = user32.GetDC(null);
		HDC hdcMem = GDI32.INSTANCE.CreateCompatibleDC(hdcScreen);
		HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcScreen, physW, physH);
		HANDLE hOld = GDI32.INSTANCE.SelectObject(hdcMem, hBitmap);

		try {
			if (!user32.PrintWindow(hWnd, hdcMem, PW_RENDERFULLCONTENT)) {
				return null;
			}

			BITMAPINFO bmi = new BITMAPINFO();
			bmi.bmiHeader.biSize = bmi.bmiHeader.size();
			bmi.bmiHeader.biWidth = physW;
			bmi.bmiHeader.biHeight = -physH;  // negative = top-down DIB
			bmi.bmiHeader.biPlanes = 1;
			bmi.bmiHeader.biBitCount = 32;
			bmi.bmiHeader.biCompression = 0;  // BI_RGB

			Memory pixels = new Memory((long) physW * physH * 4);
			int scanLines = GDI32.INSTANCE.GetDIBits(hdcMem, hBitmap, 0, physH, pixels, bmi, 0);
			if (scanLines == 0) {
				return null;
			}

			// Convert BGRA (Windows DIB layout) → ARGB Java in a single pass.
			// The byte-per-byte loop is slower than DataBufferByte direct access
			// but matches the classic captureNative pattern and avoids surprises
			// when the returned image is later resized or serialised.
			BufferedImage physImg = new BufferedImage(physW, physH, BufferedImage.TYPE_INT_RGB);
			int total = physW * physH;
			int[] argb = new int[total];
			for (int i = 0; i < total; i++) {
				long off = i * 4L;
				int b = pixels.getByte(off) & 0xFF;
				int g = pixels.getByte(off + 1) & 0xFF;
				int r = pixels.getByte(off + 2) & 0xFF;
				argb[i] = (r << 16) | (g << 8) | b;
			}
			physImg.setRGB(0, 0, physW, physH, argb, 0, physW);

			// Resize physical → logical bounds. Bicubic gives visually crisp
			// text and edges at the cost of ~5-15 ms. Skipped when caller
			// passes null bounds or when physical already matches logical.
			if (logicalBounds == null
					|| (logicalBounds.width == physW && logicalBounds.height == physH)) {
				return physImg;
			}
			BufferedImage logImg = new BufferedImage(
					logicalBounds.width, logicalBounds.height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = logImg.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2.drawImage(physImg, 0, 0, logicalBounds.width, logicalBounds.height, null);
			g2.dispose();
			return logImg;
		} finally {
			GDI32.INSTANCE.SelectObject(hdcMem, hOld);
			GDI32.INSTANCE.DeleteObject(hBitmap);
			GDI32.INSTANCE.DeleteDC(hdcMem);
			user32.ReleaseDC(null, hdcScreen);
		}
	}

	private static final class WinWindow implements OsWindow {
		private HWND hWnd;

		public WinWindow(HWND hWnd) {
			this.hWnd = hWnd;
		}

		@Override
		public OsProcess getProcess() {
			IntByReference pid = new IntByReference();
			user32.GetWindowThreadProcessId(hWnd, pid);

			Optional<ProcessHandle> handle = ProcessHandle.of(pid.getValue());

			if (handle.isPresent()) {
				return new GenericOsProcess(ProcessHandle.of(pid.getValue()).get());
			}

			return null;
		}

		@Override
		public String getTitle() {
			char[] text = new char[1024];
			int length = user32.GetWindowText(hWnd, text, 1024);
			return length > 0 ? new String(text, 0, length) : "";
		}

		@Override
		public Rectangle getBounds() {
			RECT rect = new User32.RECT();
			boolean success = user32.GetWindowRect(hWnd, rect);
			return success ? physicalToLogical(hWnd, rect.toRectangle()) : null;
		}

		@Override
		public BufferedImage captureNative(Rectangle logicalBounds) {
			return WinUtil.captureWindowNative(hWnd, logicalBounds);
		}

		@Override
		public boolean focus() {
			WinUser.WINDOWPLACEMENT lpwndpl = new WinUser.WINDOWPLACEMENT();

			user32.GetWindowPlacement(hWnd, lpwndpl);

			if (lpwndpl.showCmd == WinUser.SW_SHOWMINIMIZED || lpwndpl.showCmd == WinUser.SW_MINIMIZE) {
				user32.ShowWindow(hWnd, WinUser.SW_RESTORE);
			}

			boolean success = user32.SetForegroundWindow(hWnd);

			if (success) {
				return (user32.SetFocus(hWnd) != null);
			}

			return false;
		}

		@Override
		public boolean minimize() {
			return user32.ShowWindow(hWnd, WinUser.SW_MINIMIZE);
		}

		@Override
		public boolean maximize() {
			return user32.ShowWindow(hWnd, WinUser.SW_MAXIMIZE);
		}

		@Override
		public boolean restore() {
			return user32.ShowWindow(hWnd, WinUser.SW_RESTORE);
		}

		@Override
		public boolean equals(Object other) {
			return other != null && other instanceof WinWindow && this.hWnd.equals(((WinWindow) other).hWnd);
		}

		@Override
		public int hashCode() {
			return hWnd != null ? hWnd.hashCode() : 0;
		}
	}

	@Override
	public OsProcess open(String[] cmd, String workDir) {
		// Back-compat entry: callers who don't know about the wait budget get 3 s.
		return open(cmd, workDir, 3);
	}

	@Override
	public OsProcess open(String[] cmd, String workDir, int waitSeconds) {
		if (cmd == null || cmd.length == 0 || StringUtils.isBlank(cmd[0])) {
			return super.open(cmd, workDir);
		}

		// Delegate to Windows' own ShellExecuteEx — the same API Explorer uses
		// to launch things. It resolves the target via App Paths registry, PATH,
		// .lnk files, and default verbs, and returns hProcess directly. No 'cmd
		// /c start' quoting land-mines (JDK-6518827 drops empty title placeholders
		// on ProcessBuilder) and no "poll allProcesses and hope" band-aid.
		SHELLEXECUTEINFO info = new SHELLEXECUTEINFO();
		info.fMask = 0x00000040; // SEE_MASK_NOCLOSEPROCESS — keep hProcess alive
		info.lpFile = cmd[0];
		if (cmd.length > 1) {
			info.lpParameters = String.join(" ", Arrays.copyOfRange(cmd, 1, cmd.length));
		}
		if (StringUtils.isNotBlank(workDir)) {
			info.lpDirectory = workDir;
		}
		info.nShow = WinUser.SW_SHOWDEFAULT;

		if (!Shell32.INSTANCE.ShellExecuteEx(info) || info.hProcess == null) {
			// DDE-only apps, UWP single-instance where the shell reused an
			// existing process, or genuine miss — fall back to POSIX-style spawn.
			return super.open(cmd, workDir);
		}

		int pid = Kernel32.INSTANCE.GetProcessId(info.hProcess);
		return ProcessHandle.of(pid)
				.map(h -> (OsProcess) new GenericOsProcess(h))
				.orElse(null);
	}

	@Override
	public List<OsWindow> findWindows(String title) {
		if (StringUtils.isNotBlank(title)) {
			return allWindows().stream().filter((w) -> w.getTitle().contains(title)).collect(Collectors.toList());
		}
		return new ArrayList<>(0);
	}

	@Override
	public List<OsWindow> getWindows(OsProcess process) {
		if (process != null) {
			return allWindows().stream().filter((w) -> process.equals(w.getProcess())).collect(Collectors.toList());
		}
		return new ArrayList<>(0);
	}

	@Override
	public OsWindow getFocusedWindow() {
		HWND hWnd = user32.GetForegroundWindow();
		return new WinWindow(hWnd);
	}

	private List<OsWindow> allWindows() {
		/* Initialize the empty window list. */
		final List<OsWindow> windows = new ArrayList<>();

		boolean result = user32.EnumWindows(new WinUser.WNDENUMPROC() {
			public boolean callback(final HWND hWnd, final Pointer data) {
				// Only visible and top level. Ensures that top level window is at index 0
				if (user32.IsWindowVisible(hWnd) && user32.GetWindow(hWnd, new DWORD(WinUser.GW_OWNER)) == null) {
					windows.add(new WinWindow(hWnd));

					// get child windows as well
					user32.EnumChildWindows(hWnd, new WinUser.WNDENUMPROC() {
						public boolean callback(final HWND hWnd, final Pointer data) {
							if (user32.IsWindowVisible(hWnd)) {
								windows.add(new WinWindow(hWnd));
							}

							return true;
						}
					}, null);
				}

				return true;
			}
		}, null);

		/* Handle errors. */
		if (!result && Kernel32.INSTANCE.GetLastError() != 0) {
			throw new RuntimeException("Couldn't enumerate windows.");
		}

		return windows;
	}

	// https://msdn.microsoft.com/pt-br/library/windows/desktop/dd375731
	// VK_NUM_LOCK 0x90
	// VK_SCROLL 0x91
	// VK_CAPITAL 0x14

	public static int isNumLockOn() {
		int winNumLock = 0x90;
		return user32.GetKeyState(winNumLock);
	}

	public static int isScrollLockOn() {
		int winScrollLock = 0x91;
		return user32.GetKeyState(winScrollLock);
	}

	public static int isCapsLockOn() {
		int winCapsLock = 0x14;
		return user32.GetKeyState(winCapsLock);
	}

	static final int BUFFERSIZE = 32 * 1024 - 1;
	static final Kernel32 kernel32 = Kernel32.INSTANCE;

	public static String getEnv(String envKey) {
		char[] retChar = new char[BUFFERSIZE];
		String envVal = null;
		int retInt = kernel32.GetEnvironmentVariable(envKey, retChar, BUFFERSIZE);
		if (retInt > 0) {
			envVal = new String(Arrays.copyOfRange(retChar, 0, retInt));
		}
		return envVal;
	}

	public static String setEnv(String envKey, String envVal) {
		boolean retOK = kernel32.SetEnvironmentVariable(envKey, envVal);
		if (retOK) {
			return getEnv(envKey);
		}
		return null;
	}
}