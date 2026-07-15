/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.natives;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI.SHELLEXECUTEINFO;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

public class WinUtil extends GenericOsUtil {

	static final SXUser32 user32 = SXUser32.INSTANCE;

	// GetWindowRect returns physical pixels; AWT (Robot, Region) works in
	// logical pixels since JEP 263 (JDK 9 Per-Monitor DPI Aware). We divide
	// by the scale that the JVM itself applies -- single source of truth,
	// matching whatever coordinate space Region.getImage() will feed to
	// Robot.createScreenCapture (#444). The device is resolved by the physical
	// centre of the window to support mixed-DPI multi-monitor layouts.
	private static Rectangle physicalToLogical(Rectangle physical) {
		if (physical == null) {
			return null;
		}
		int cx = physical.x + physical.width / 2;
		int cy = physical.y + physical.height / 2;
		AffineTransform tx = null;
		for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			GraphicsConfiguration gc = gd.getDefaultConfiguration();
			AffineTransform gcTx = gc.getDefaultTransform();
			Rectangle bounds = gc.getBounds(); // pixels logiques
			int px = (int) (bounds.x * gcTx.getScaleX());
			int py = (int) (bounds.y * gcTx.getScaleY());
			int pw = (int) (bounds.width * gcTx.getScaleX());
			int ph = (int) (bounds.height * gcTx.getScaleY());
			if (cx >= px && cx < px + pw && cy >= py && cy < py + ph) {
				tx = gcTx;
				break;
			}
		}
		if (tx == null) {
			tx = GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform();
		}
		double sx = tx.getScaleX();
		double sy = tx.getScaleY();
		if (sx == 1.0 && sy == 1.0) {
			return physical;
		}
		return new Rectangle(
				(int) Math.round(physical.x / sx),
				(int) Math.round(physical.y / sy),
				(int) Math.round(physical.width / sx),
				(int) Math.round(physical.height / sy));
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
			return success ? physicalToLogical(rect.toRectangle()) : null;
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