/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support;

import org.sikuli.basics.Debug;

import java.io.*;
import java.net.URL;
import java.util.*;

public class RunTime {

  static {
    Commons.init();
  }

  //<editor-fold defaultstate="collapsed" desc="04 instance">
  private RunTime() {
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="02 logging">
  private static int lvl = 3;
  private int minLvl = lvl;

  private static void log(int level, String message, Object... args) {
    Debug.logx(level, "RunTime: " + message, args);
  }

  private static void logp(String message, Object... args) {
    Debug.logx(-3, message, args);
  }

  private static void logp(int level, String message, Object... args) {
    if (level <= Debug.getDebugLevel()) {
      logp(message, args);
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="03 variables">
  public enum RunType {
    JAR, CLASSES, OTHER
  }

  public static boolean testing = false;

  private static Class clsRef = RunTime.class;

  private List<URL> classPathActual = new ArrayList<>();
  private List<String> classPathList = new ArrayList<>();

  private static boolean areLibsExported = false;
  private static Map<String, Boolean> libsLoaded = new HashMap<String, Boolean>();

  public File fSxBaseJar = null;
  public static String fpContent = "sikulixcontent";

  public boolean runningJar = true;
  public boolean runningWindows = false;
  public boolean runningMac = false;
  public boolean runningLinux = false;
  //</editor-fold>

  //</editor-fold>
}
