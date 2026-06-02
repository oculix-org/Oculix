/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.support.recorder;

import org.apache.commons.io.FileUtils;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.*;
import org.sikuli.basics.Debug;
import org.sikuli.basics.Settings;
import org.sikuli.script.Location;
import org.sikuli.script.Screen;
import org.sikuli.script.ScreenImage;
import org.sikuli.script.SikuliXception;
import org.sikuli.support.Commons;
import org.sikuli.support.recorder.actions.IRecordedAction;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records native input events and transforms them into executable actions.
 *
 * @author balmma
 */
public class Recorder implements NativeKeyListener, NativeMouseListener, NativeMouseMotionListener, NativeMouseWheelListener {

  private static final long MOUSE_SCREENSHOT_DELAY = 500;
  private static final long MOUSE_MOVE_SCREENSHOT_DELAY = 100;
  private static final long KEY_SCREENSHOT_DELAY = 500;

  private static final int MOUSE_MOVE_THRESHOLD = 20;

  private RecordedEventsFlow eventsFlow = new RecordedEventsFlow();
  private File screenshotDir;

  private volatile boolean running = false;

  ScreenImage currentImage = null;
  String currentImageFilePath = null;

  private long currentMouseX = 0;
  private long currentMouseY = 0;
  private Location currentMousePos = null;

  private boolean capturing = false;
  private final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

  /** True once {@link GlobalScreen} has successfully loaded its native lib
   *  AND the hook has been registered. Guards every later GlobalScreen.*
   *  call so a missing native dep (typical on Linux without
   *  libxkbcommon-x11-0) degrades gracefully instead of cascading into
   *  NoClassDefFoundError on the recorder stop path.
   */
  private static volatile boolean nativeHookReady = false;

  /**
   * Registers the JNativeHook global hook so the Recorder can listen to
   * OS-level mouse / keyboard events.
   *
   * <p>JNativeHook ships an x86_64 .so that dynamically links against
   * {@code libxkbcommon-x11.so.0} on Linux. Some minimal installs (Ubuntu
   * Server, slim WSL distros, CI containers) do not have that package by
   * default and {@code System.load()} throws {@link UnsatisfiedLinkError}
   * during {@code GlobalScreen.<clinit>}. That is an {@link Error},
   * not an {@link Exception}, so the previous {@code catch
   * (NativeHookException)} let it through and the subsequent
   * {@code GlobalScreen.addNativeMouseListener(...)} calls in
   * {@link #start} kept the cascade going as NoClassDefFoundError on every
   * later access.
   *
   * <p>This implementation catches {@link Throwable} on purpose to swallow
   * any class-init failure, sets {@link #nativeHookReady} accordingly, and
   * returns the success flag so the caller can abort cleanly with an
   * actionable message instead of crashing the EDT.
   *
   * @return {@code true} if the hook is live, {@code false} if anything
   *         went wrong (native lib missing, hook refused by OS, etc.)
   */
  private static boolean registerNativeHook() {
    try {
      // Make Global Screen logger quiet.
      // Floods the console otherwise
      Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
      logger.setLevel(Level.OFF);
      logger.setUseParentHandlers(false);

      GlobalScreen.registerNativeHook();
      nativeHookReady = true;
      return true;
    } catch (NativeHookException e) {
      Debug.error("Error registering native hook: %s", e.getMessage());
      nativeHookReady = false;
      return false;
    } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
      // The native lib could not be loaded — typically the missing system
      // dep on Linux. Surface a clear error so the caller can prompt the
      // user with the apt install command instead of dumping a 60-line
      // stack trace into the console.
      Debug.error("Recorder: JNativeHook native library failed to load: %s",
          e.getMessage());
      Debug.error("Recorder: on Linux, install: sudo apt install "
          + "libxkbcommon-x11-0 libxcb-xkb1 libx11-xcb1");
      nativeHookReady = false;
      return false;
    } catch (Throwable t) {
      Debug.error("Recorder: unexpected error registering native hook: %s",
          t.getMessage());
      nativeHookReady = false;
      return false;
    }
  }

  private static void unregisterNativeHook() {
    // Nothing to unregister if the hook never came up (e.g. native lib
    // load failure). Touching GlobalScreen.* now would only trigger a
    // fresh NoClassDefFoundError.
    if (!nativeHookReady) return;
    try {
      /*
       * We unregister the native hook on Windows because it blocks some special keys
       * in AWT while registered (e.g. AltGr).
       *
       * We do not unregister on Linux because this terminates the whole JVM.
       * Interestingly, the special keys are not blocked on Linux at all.
       *
       * TODO: Has to be checked on Mac OS, but I guess that not unregistering is
       * the better option here.
       *
       * Re-registering doesn't hurt anyway, because JNativeHook checks the register
       * state before registering again. So unregister is only really needed on Windows.
       */
      if (Settings.isWindows()) {
        GlobalScreen.unregisterNativeHook();
      }
    } catch (NativeHookException e) {
      Debug.error("Error unregistering native hook: %s", e.getMessage());
    } catch (Throwable t) {
      // Defensive: never let cleanup throw on the stop path.
      Debug.error("Recorder: unexpected error unregistering native hook: %s",
          t.getMessage());
    }
  }

  /*
   * Captures the screen after a given delay. During the delay, other calls to
   * this method are ignored.
   */
  private void screenshot(long delayMillis) {
    synchronized (SCHEDULER) {
      if (!capturing) {
        capturing = true;
        SCHEDULER.schedule((() -> {
          try {
            synchronized (screenshotDir) {
              if (screenshotDir.exists()) {
                final Screen screen = getRelevantScreen();
                ScreenImage img = screen.capture();
                // Dedupe screenshots
                if (img.diffPercentage(currentImage) > 0.0001) {
                  currentImage = img;
                  currentImageFilePath = currentImage.saveInto(screenshotDir);
                }
                final int screenID = screen.getID();
                if (screenID > 9) {
                  Commons.terminate(999, "Recorder: screen id > 9 --- not implemented");
                }
                String pathToSave = screenID + currentImageFilePath;
                eventsFlow.addScreenshot(pathToSave);
              }
            }
          } finally {
            synchronized (SCHEDULER) {
              capturing = false;
            }
          }
        }), delayMillis, TimeUnit.MILLISECONDS);
      }
    }
  }

  private Screen getRelevantScreen() {
    return currentMousePos.getMonitor();
  }

  private void add(NativeInputEvent e, long screenshotDelayMillis) {
    eventsFlow.addEvent(e);
    screenshot(screenshotDelayMillis);
  }

  @Override
  public void nativeKeyPressed(NativeKeyEvent e) {
    add(e, KEY_SCREENSHOT_DELAY);
  }

  @Override
  public void nativeKeyReleased(NativeKeyEvent e) {
    add(e, KEY_SCREENSHOT_DELAY);
  }

  @Override
  public void nativeKeyTyped(NativeKeyEvent e) {
    // do not handle
  }

  /**
   * starts recording
   */
  public void start() {
    if (!running) {
      Commons.loadOpenCV();
      running = true;

      eventsFlow.clear();
      currentImage = null;
      currentImageFilePath = null;

      try {
        screenshotDir = Files.createTempDirectory("sikulix").toFile();
        screenshotDir.deleteOnExit();
      } catch (IOException e) {
        throw new SikuliXception("Recorder: createTempDirectory: not possible");
      }

      screenshot(0);

      if (!Recorder.registerNativeHook()) {
        // Hook could not be set up (most commonly the JNativeHook native
        // lib could not load on Linux without libxkbcommon-x11-0). Abort
        // the start sequence with a SikuliXception so the IDE button can
        // surface an actionable dialog instead of dragging the EDT into
        // a NoClassDefFoundError cascade on the next GlobalScreen.*
        // method call.
        running = false;
        throw new SikuliXception(
            "Recorder: native hook unavailable on this system.\n"
            + "On Linux, install the JNativeHook runtime deps:\n"
            + "    sudo apt install libxkbcommon-x11-0 libxcb-xkb1 libx11-xcb1\n"
            + "Then restart OculiX.");
      }
      //GlobalScreen.addNativeKeyListener(this);
      GlobalScreen.addNativeMouseListener(this);
      GlobalScreen.addNativeMouseMotionListener(this);
      GlobalScreen.addNativeMouseWheelListener(this);
    }
  }

  /**
   * Stops recording and transforms the recorded events into actions.
   *
   * @param progress optional ProgressMonitor
   * @return actions resulted from the recorded events
   */
  public List<IRecordedAction> stop(ProgressMonitor progress) {
    if (running) {
      running = false;

      // Touching GlobalScreen here when its class init failed (missing
      // native lib) would throw NoClassDefFoundError and bury the real
      // SikuliXception from start(). Guard via the same readiness flag.
      if (nativeHookReady) {
        try {
          GlobalScreen.removeNativeMouseWheelListener(this);
          GlobalScreen.removeNativeMouseMotionListener(this);
          GlobalScreen.removeNativeMouseListener(this);
//      GlobalScreen.removeNativeKeyListener(this);
        } catch (Throwable t) {
          Debug.error("Recorder.stop: removing listeners failed: %s",
              t.getMessage());
        }
      }
      Recorder.unregisterNativeHook();

      synchronized (screenshotDir) {
        List<IRecordedAction> actions = eventsFlow.compile(progress);

        // remove screenshots after compile to free up disk space
        try {
          FileUtils.deleteDirectory(screenshotDir);
        } catch (IOException e) {
          e.printStackTrace();
        }
        return actions;
      }
    }
    return new ArrayList<>();
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public void nativeMouseClicked(NativeMouseEvent e) {
    // do not handle
  }

  @Override
  public void nativeMousePressed(NativeMouseEvent e) {
    saveMousePosition(e);
    add(e, MOUSE_SCREENSHOT_DELAY);
  }

  @Override
  public void nativeMouseReleased(NativeMouseEvent e) {
    saveMousePosition(e);
    add(e, MOUSE_SCREENSHOT_DELAY);
  }

  @Override
  public void nativeMouseMoved(NativeMouseEvent e) {
    addMouseIfRelevantMove(e);
  }

  @Override
  public void nativeMouseDragged(NativeMouseEvent e) {
    addMouseIfRelevantMove(e);
  }

  private void saveMousePosition(NativeMouseEvent e) {
    currentMouseX = e.getX();
    currentMouseY = e.getY();
    currentMousePos = new Location(currentMouseX, currentMouseY);
  }

  private void addMouseIfRelevantMove(NativeMouseEvent e) {
    // only add relevant mouse moves > MOUSE_MOVE_THRESHOLD px
    if (Math.abs(e.getX() - currentMouseX) > MOUSE_MOVE_THRESHOLD
        || Math.abs(e.getY() - currentMouseY) > MOUSE_MOVE_THRESHOLD) {
      saveMousePosition(e);
      add(e, MOUSE_MOVE_SCREENSHOT_DELAY);
    }
  }

  @Override
  public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
    saveMousePosition(e);
    add(e, MOUSE_SCREENSHOT_DELAY);
  }
}