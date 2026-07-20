/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.natives;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

public interface OSUtil {

	public interface OsProcess {

		long getPid();

		String getName();

		boolean isRunning();

		boolean close(boolean force);
	}

	public interface OsWindow {
		OsProcess getProcess();

		String getTitle();

		Rectangle getBounds();

		boolean focus();

		boolean minimize();

		boolean maximize();

		boolean restore();

		/**
		 * Native window capture bypassing {@code Robot.createScreenCapture}.
		 * Windows path uses {@code PrintWindow(PW_RENDERFULLCONTENT)} to ask the
		 * WM to render the window into an off-screen HDC, which handles
		 * mixed-DPI multi-monitor layouts, straddling windows, and partially
		 * off-screen windows correctly (#444).
		 *
		 * <p>Default implementation returns {@code null}. Non-Windows platforms
		 * (macOS, Linux X11, Wayland) inherit the null and callers fall back
		 * to the classic Robot-based capture — macOS Quartz already handles
		 * mixed-DPI correctly, X11 has no equivalent native, and Wayland
		 * requires the portal-based route out of scope of this API.
		 *
		 * @param logicalBounds  target logical size (may be null → physical)
		 * @return the captured window content, or {@code null} to fall back
		 */
		default BufferedImage captureNative(Rectangle logicalBounds) {
			return null;
		}
	}

	/**
	 * check if needed command libraries or packages are installed and working<br>
	 * if not ok, respective features will do nothing but issue error messages
	 */
	void init();

	boolean isUserProcess(OsProcess process);

	List<OsProcess> findProcesses(String name);

	List<OsWindow> findWindows(String title);

	List<OsWindow> getWindows(OsProcess process);

	List<OsWindow> getWindows();

	List<OsProcess> getProcesses();

	OsProcess getProcess();

	OsProcess open(String[] cmd, String workDir);

	// Overload with an explicit wait budget (seconds). Default routes to the
	// timeout-agnostic signature above — implementations that shell out via
	// launchers (WinUtil 'start', MacUtil 'open -a') override this to size
	// their internal PID-lookup poll instead of using a hardcoded 3 s.
	default OsProcess open(String[] cmd, String workDir, int waitSeconds) {
		return open(cmd, workDir);
	}

	OsWindow getFocusedWindow();
}
