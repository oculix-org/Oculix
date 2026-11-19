package org.sikuli.natives;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser.HMONITOR;
import com.sun.jna.platform.win32.WinUser.MONITORINFOEX;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.sikuli.script.ExtendedScreenCapture;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Windows 10+ adapter for extended monitor captures. Call off the Swing EDT.
 * Uses Robot for the monitor and the existing WinUtil PrintWindow path for
 * complete windows. PrintWindow is synchronous and retains its existing limits
 * (application rendering support and no cancellation timeout).
 *
 * Outside the monitor this reconstructs the selected windows, not the actual
 * desktop of adjacent monitors. Native window captures are opaque rectangles:
 * layered transparency and non-rectangular window shapes are not reconstructed.
 */
public final class WinDesktopCapture {
  private WinDesktopCapture() { }

  private interface NativeWindows extends User32 {
    NativeWindows INSTANCE = Native.load("user32", NativeWindows.class, W32APIOptions.DEFAULT_OPTIONS);
    Pointer SetThreadDpiAwarenessContext(Pointer context);
    boolean IsIconic(HWND window);
    HWND GetShellWindow();
  }

  private interface Dwm extends StdCallLibrary {
    Dwm INSTANCE = Native.load("dwmapi", Dwm.class);
    int DwmGetWindowAttribute(HWND window, int attribute, IntByReference value, int size);
  }

  private record WindowState(HWND handle, Rectangle bounds) { }

  public static ExtendedScreenCapture capture(int awtScreenIndex) throws AWTException {
    return capture(awtScreenIndex, ExtendedScreenCapture.DEFAULT_MAX_PIXELS);
  }

  public static ExtendedScreenCapture capture(int awtScreenIndex, long maxPixels) throws AWTException {
    if (!System.getProperty("os.name").startsWith("Windows")) {
      throw new UnsupportedOperationException("Extended native capture requires Windows 10+");
    }
    if (EventQueue.isDispatchThread()) {
      throw new IllegalStateException("Run native capture outside the Swing EDT");
    }
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    if (awtScreenIndex < 0 || awtScreenIndex >= devices.length) {
      throw new IllegalArgumentException("Invalid AWT screen index: " + awtScreenIndex);
    }
    GraphicsDevice device = devices[awtScreenIndex];
    GraphicsConfiguration configuration = device.getDefaultConfiguration();
    Rectangle logicalBounds = configuration.getBounds();
    HMONITOR monitor = monitorOf(configuration);
    Robot robot = new Robot(device);
    NativeWindows win = NativeWindows.INSTANCE;
    // Physical coordinates for every native query on this thread; restored below.
    Pointer previousDpi = win.SetThreadDpiAwarenessContext(Pointer.createConstant(-4L));
    if (previousDpi == null) {
      throw new IllegalStateException("Cannot enter per-monitor DPI awareness (" + Native.getLastError() + ")");
    }
    try {
      Rectangle physicalBounds = monitorBounds(monitor);
      ExtendedScreenCapture.checkSize(physicalBounds, maxPixels);
      List<WindowState> windows = crossingWindows(physicalBounds);
      Rectangle canvas = new Rectangle(physicalBounds);
      for (WindowState window : windows) {
        // Check before asking GDI to allocate a full-window bitmap.
        ExtendedScreenCapture.checkSize(window.bounds(), maxPixels);
        canvas = ExtendedScreenCapture.checkedUnion(canvas, window.bounds());
        ExtendedScreenCapture.checkSize(canvas, maxPixels);
      }

      MultiResolutionImage shot = robot.createMultiResolutionScreenCapture(logicalBounds);
      BufferedImage base = null;
      for (Image variant : shot.getResolutionVariants()) {
        if (variant instanceof BufferedImage
            && variant.getWidth(null) == physicalBounds.width
            && variant.getHeight(null) == physicalBounds.height) {
          base = (BufferedImage) variant;
          break;
        }
      }
      if (base == null) {
        throw new IllegalStateException("Robot did not return the monitor's native resolution: " + physicalBounds);
      }

      List<ExtendedScreenCapture.Layer> layers = new ArrayList<>();
      List<WindowState> backToFront = new ArrayList<>(windows);
      Collections.reverse(backToFront);
      for (WindowState window : backToFront) {
        requireUnmoved(window);
        BufferedImage image = WinUtil.captureWindowNative(window.handle(), null);
        if (image == null) {
          throw new IllegalStateException("PrintWindow capture failed for " + window.handle());
        }
        requireUnmoved(window);
        layers.add(new ExtendedScreenCapture.Layer(window.bounds(), image));
      }
      // Refuse a known inconsistent layout rather than paste at stale coordinates.
      if (!windows.equals(crossingWindows(physicalBounds))
          || !physicalBounds.equals(monitorBounds(monitor))
          || !logicalBounds.equals(device.getDefaultConfiguration().getBounds())) {
        throw new IllegalStateException("Window order, geometry or monitor layout changed; retry capture");
      }
      return ExtendedScreenCapture.compose(physicalBounds, base, layers, maxPixels);
    } finally {
      win.SetThreadDpiAwarenessContext(previousDpi);
    }
  }

  /**
   * Let AWT place an unrealized Frame on its own GraphicsConfiguration, then
   * resolve its native HWND. The peer is never shown. This avoids guessing a
   * monitor from logical coordinates or assuming Windows/AWT numbering matches.
   */
  private static HMONITOR monitorOf(GraphicsConfiguration configuration) {
    AtomicReference<HMONITOR> result = new AtomicReference<>();
    try {
      EventQueue.invokeAndWait(() -> {
        Frame probe = new Frame(configuration);
        try {
          Rectangle b = configuration.getBounds();
          probe.setUndecorated(true);
          probe.setFocusableWindowState(false);
          probe.setBounds(b.x + b.width / 2, b.y + b.height / 2, 1, 1);
          probe.addNotify();
          HWND handle = new HWND(Native.getComponentPointer(probe));
          result.set(NativeWindows.INSTANCE.MonitorFromWindow(handle, 0));
        } finally {
          probe.dispose();
        }
      });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while resolving AWT monitor", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException("Cannot resolve AWT monitor", e.getCause());
    }
    return Objects.requireNonNull(result.get(), "No native monitor for AWT screen");
  }

  private static Rectangle monitorBounds(HMONITOR monitor) {
    MONITORINFOEX info = new MONITORINFOEX();
    if (!NativeWindows.INSTANCE.GetMonitorInfo(monitor, info).booleanValue()) {
      throw new IllegalStateException("GetMonitorInfo failed");
    }
    return info.rcMonitor.toRectangle();
  }

  private record Covering(HWND handle, Rectangle physical, Rectangle logical) { }

  /**
   * Paints into {@code canvas}, a capture of the logical rectangle {@code request},
   * the windows covering {@code missing}: the part of the request outside every
   * monitor, in logical desktop coordinates. Each window is placed like a window
   * Region (WinUtil.physicalToLogical) and captured with WinUtil.captureWindowNative.
   * Space covered by no window keeps the canvas pixels (black).
   *
   * @throws IllegalStateException when a covering window cannot be captured,
   *         or moves or resizes during its capture
   */
  public static void fillOffScreen(BufferedImage canvas, Rectangle request, Area missing) {
    List<Covering> covering = new ArrayList<>();
    for (HWND handle : visibleWindows()) {
      RECT rect = new RECT();
      if (!NativeWindows.INSTANCE.GetWindowRect(handle, rect)) {
        continue;
      }
      Rectangle physical = rect.toRectangle();
      Rectangle logical = WinUtil.physicalToLogical(handle, physical);
      if (logical.width > 0 && logical.height > 0 && missing.intersects(logical)) {
        covering.add(new Covering(handle, physical, logical));
      }
    }
    Graphics2D g = canvas.createGraphics();
    try {
      g.translate(-request.x, -request.y);
      g.setClip(missing);
      for (int i = covering.size() - 1; i >= 0; i--) {
        Covering window = covering.get(i);
        BufferedImage image = WinUtil.captureWindowNative(window.handle(), window.logical());
        if (image == null) {
          throw new IllegalStateException("PrintWindow capture failed for window "
              + window.handle() + " at " + window.logical());
        }
        if (!window.physical().equals(boundsOf(window.handle()))) {
          throw new IllegalStateException("Window moved or resized during capture: " + window.handle());
        }
        g.drawImage(image, window.logical().x, window.logical().y, null);
      }
    } finally {
      g.dispose();
    }
  }

  /** EnumWindows order is front to back; no child-window duplication. */
  private static List<WindowState> crossingWindows(Rectangle monitor) {
    List<WindowState> selected = new ArrayList<>();
    for (HWND handle : visibleWindows()) {
      Rectangle bounds = boundsOf(handle);
      if (ExtendedScreenCapture.extendsMonitor(monitor, bounds)) {
        selected.add(new WindowState(handle, bounds));
      }
    }
    return selected;
  }

  /** Visible, non-minimized, non-cloaked top-level windows, front to back, desktop surfaces excluded. */
  private static List<HWND> visibleWindows() {
    NativeWindows win = NativeWindows.INSTANCE;
    List<HWND> handles = new ArrayList<>();
    if (!win.EnumWindows((handle, data) -> { handles.add(handle); return true; }, null)) {
      throw new IllegalStateException("EnumWindows failed");
    }
    List<HWND> selected = new ArrayList<>();
    for (HWND handle : handles) {
      if (!win.IsWindowVisible(handle) || win.IsIconic(handle)
          || handle.equals(win.GetShellWindow()) || handle.equals(win.GetDesktopWindow())) {
        continue;
      }
      char[] className = new char[256];
      win.GetClassName(handle, className, className.length);
      String windowClass = Native.toString(className);
      if ("Progman".equals(windowClass) || "WorkerW".equals(windowClass)) { continue; }
      IntByReference cloaked = new IntByReference();
      int status = Dwm.INSTANCE.DwmGetWindowAttribute(handle, 14, cloaked, Integer.BYTES);
      if (status != 0) {
        throw new IllegalStateException("Cannot query window visibility: " + handle);
      }
      if (cloaked.getValue() != 0) { continue; }
      selected.add(handle);
    }
    return selected;
  }

  private static Rectangle boundsOf(HWND window) {
    RECT bounds = new RECT();
    if (!NativeWindows.INSTANCE.GetWindowRect(window, bounds)) {
      throw new IllegalStateException("Window disappeared during capture: " + window);
    }
    return bounds.toRectangle();
  }

  private static void requireUnmoved(WindowState window) {
    if (!window.bounds().equals(boundsOf(window.handle()))) {
      throw new IllegalStateException("Window moved or resized during capture: " + window.handle());
    }
  }
}
