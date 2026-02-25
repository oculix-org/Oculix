/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.vnc;

import org.sikuli.basics.Debug;
import org.sikuli.script.*;
import org.sikuli.script.support.IRobot;
import org.sikuli.script.support.IScreen;
import org.sikuli.util.OverlayCapturePrompt;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

import com.sikulix.vnc.*;

public class VNCScreen extends Region implements IScreen {
  private VNCClient client;
  private IRobot robot;
  private ScreenImage lastScreenImage;

  private static String stdIP = "127.0.0.1";
  private static int stdPort = 5900;

  private String ip = "";
  private int port = -1;
  private String id = "";

  private static Map<String, VNCScreen> screens = new HashMap<>();

  private static int startUpWait = 3;

  public static void startUp(int waittime) {
    startUpWait = waittime;
  }

  private VNCScreen() {}

  public static VNCScreen start() {
    return start(stdIP);
  }

  public static VNCScreen start(String theIP) {
    VNCScreen vscr = null;
    vscr = start(theIP, stdPort, null, 3, 0);
    return vscr;
  }

  public static VNCScreen start(String theIP, int thePort) {
    return start(theIP, thePort, null, 3, 0);
  }

  public static VNCScreen start(String theIP, int thePort, int cTimeout, int timeout) {
    return start(theIP, thePort, null, cTimeout, timeout);
  }

  public static VNCScreen start(String theIP, int thePort, String password, int cTimeout, int timeout) {
    VNCScreen scr = canConnect(theIP, thePort, cTimeout);
    if (null != scr) {
      if (scr.id.isEmpty()) {
        scr.init(theIP, thePort, password);
        Debug.log(3, "VNCScreen: start: %s", scr);
      } else
        Debug.log(3, "VNCScreen: start: using existing: %s", scr);
    } else {
      scr = new VNCScreen();
    }
    return scr;
  }

  private void init(String theIP, int thePort, String password) {
    ip = theIP;
    port = thePort;
    id = String.format("%s:%d", ip, port);
    client = VNCClient.connect(ip, port, password, true);
    robot = new VNCRobot(this);
    setOtherScreen(this);
    setRect(getBounds());
    initScreen(this);

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          client.processMessages();
        } catch (RuntimeException e) {
          if (isRunning()) {
            throw e;
          }
        }
      }
    }).start();
    client.refreshFramebuffer();

    screens.put(id, this);
    this.wait((double) startUpWait);
  }

  private static VNCScreen canConnect(String theIP, int thePort, int timeout) {
    String address = theIP + ":" + thePort;
    boolean validIP;
    VNCScreen vncScreen;
    String[] parts = theIP.split("\\.");
    if (parts.length == 4) {
      validIP = true;
      for (String part : parts) {
        try {
          int numIP = Integer.parseInt(part);
          if (numIP < 0 || numIP > 255) {
            return null;
          }
          break;
        } catch (NumberFormatException nex) {
          return null;
        }
      }
    } else {
      validIP = !new InetSocketAddress(theIP, thePort).isUnresolved();
    }
    if (validIP) {
      if (screens.size() > 0) {
        vncScreen = screens.get(address);
        if (null != vncScreen) {
          return vncScreen;
        }
      }
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(theIP, thePort), timeout * 1000);
        vncScreen = new VNCScreen();
        return vncScreen;
      } catch (Exception ex) {
        Debug.error("VNCScreen: start: connection %s:%d not possible", theIP, thePort);
        return null;
      }
    }
    Debug.error("VNCScreen: start: given ip/hostname %s not valid", theIP);
    return null;
  }

  public String getIDString() {
    return (isRunning() ? "VNC " : "VNC:INVALID ") + id;
  }

  public void stop() {
    close();
    screens.remove(this.id);
  }

  public static void stopAll() {
    if (screens.size() > 0) {
      Debug.log(3, "VNCScreen: stopping all");
      for (VNCScreen scr : screens.values()) {
        scr.close();
      }
      screens.clear();
    }
  }

  private void close() {
    if (isRunning()) {
      Debug.log(3, "VNCScreen: stopping: %s", this);
      client.close();
      client = null;
      robot = null;
    }
  }

  public boolean isRunning() {
    return null != client;
  }

  @Override
  public IRobot getRobot() {
    return robot;
  }

  @Override
  public Rectangle getBounds() {
    if (isRunning()) {
      return client.getBounds();
    }
    return new Rectangle();
  }

  @Override
  public ScreenImage capture() {
    return capture(getBounds());
  }

  @Override
  public ScreenImage capture(Region reg) {
    return capture(reg.x, reg.y, reg.w, reg.h);
  }

  @Override
  public ScreenImage capture(Rectangle rect) {
    return capture(rect.x, rect.y, rect.width, rect.height);
  }

  @Override
  public ScreenImage capture(int x, int y, int w, int h) {
    if (!isRunning()) {
      return null;
    }
    // CDC fix: force framebuffer refresh before reading to avoid stale/corrupted data
    client.refreshFramebuffer(x, y, w, h, false);
    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    client.refreshFramebuffer(x, y, w, h, false);
    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    BufferedImage image = client.getFrameBuffer(x, y, w, h);
    ScreenImage img = new ScreenImage(
            new Rectangle(x, y, w, h),
            image
    );
    lastScreenImage = img;
    Debug.log(3, "VNCScreen: capture: (%d,%d) %dx%d on %s", x, y, w, h, this);
    return img;
  }

  /**
   * Wait for the screen to stabilize by comparing successive captures.
   * Useful after UI transitions to ensure the framebuffer is fully updated.
   *
   * @param maxWaitMs maximum time to wait in milliseconds
   * @param stabilityMs how long the screen must remain unchanged to be considered stable
   * @return true if screen stabilized within the timeout, false otherwise
   */
  public boolean waitForScreenStable(long maxWaitMs, long stabilityMs) {
    if (!isRunning()) {
      return false;
    }
    long startTime = System.currentTimeMillis();
    long lastChangeTime = startTime;
    BufferedImage previousImage = null;

    while (System.currentTimeMillis() - startTime < maxWaitMs) {
      client.refreshFramebuffer();
      try { Thread.sleep(50); } catch (InterruptedException ignored) {}
      Rectangle bounds = getBounds();
      BufferedImage currentImage = client.getFrameBuffer(bounds.x, bounds.y, bounds.width, bounds.height);

      if (previousImage != null && imagesEqual(previousImage, currentImage)) {
        if (System.currentTimeMillis() - lastChangeTime >= stabilityMs) {
          Debug.log(3, "VNCScreen: screen stable after %d ms", System.currentTimeMillis() - startTime);
          return true;
        }
      } else {
        lastChangeTime = System.currentTimeMillis();
      }
      previousImage = currentImage;
      try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }
    Debug.log(3, "VNCScreen: screen did not stabilize within %d ms", maxWaitMs);
    return false;
  }

  /**
   * Wait for screen stability with default parameters (5s max, 500ms stability).
   */
  public boolean waitForScreenStable() {
    return waitForScreenStable(5000, 500);
  }

  private static boolean imagesEqual(BufferedImage img1, BufferedImage img2) {
    if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
      return false;
    }
    for (int y = 0; y < img1.getHeight(); y += 10) {
      for (int x = 0; x < img1.getWidth(); x += 10) {
        if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public int getID() {
    return 0;
  }

  @Override
  public int getIdFromPoint(int srcx, int srcy) {
    return 0;
  }

  @Override
  protected <PSIMRL> Location getLocationFromTarget(PSIMRL target) throws FindFailed {
    Location location = super.getLocationFromTarget(target);
    if (location != null) {
      location.setOtherScreen(this);
    }
    return location;
  }

  @Override
  public ScreenImage getLastScreenImageFromScreen() {
    return lastScreenImage;
  }

  @Override
  public ScreenImage userCapture(final String msg) {
    if (!isRunning()) {
      return null;
    }

    final OverlayCapturePrompt prompt = new OverlayCapturePrompt(this);

    Thread th = new Thread() {
      @Override
      public void run() {
        prompt.prompt(msg);
      }
    };

    th.start();

    boolean hasShot = false;
    ScreenImage simg = null;
    int count = 0;
    while (!hasShot) {
      this.wait(0.1f);
      if (count++ > 300) {
        break;
      }
      if (prompt.isComplete()) {
        simg = prompt.getSelection();
        if (simg != null) {
          lastScreenImage = simg;
        }
        hasShot = true;
        prompt.close();
      }
    }
    if (!hasShot) {
      prompt.close();
    }

    return simg;
  }

  public VNCClient getClient() {
    return client;
  }

  public Region set(Region element) {
    return setOther(element);
  }

  public Location set(Location element) {
    return setOther(element);
  }

  public Region setOther(Region element) {
    element.setOtherScreen(this);
    return element;
  }

  public Location setOther(Location element) {
    element.setOtherScreen(this);
    return element;
  }

  public Location newLocation(int x, int y) {
    Location loc = new Location(x, y);
    loc.setOtherScreen(this);
    return loc;
  }

  public Location newLocation(Location loc) {
    return newLocation(loc.x, loc.y);
  }

  public Region newRegion(int x, int y, int w, int h) {
    Region reg = Region.create(x, y, w, h, this);
    reg.setOtherScreen(this);
    return reg;
  }

  public Region newRegion(Location loc, int w, int h) {
    return newRegion(loc.x, loc.y, w, h);
  }

  public Region newRegion(Region reg) {
    return newRegion(reg.x, reg.y, reg.w, reg.h);
  }
}
