/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script.support;

import org.sikuli.android.ADBScreen;
import org.sikuli.basics.*;
import org.sikuli.script.*;
import org.sikuli.support.Commons;
import org.sikuli.support.FileManager;
import org.sikuli.support.Observing;
import org.sikuli.support.devices.RobotDesktop;
import org.sikuli.util.Highlight;
import org.sikuli.vnc.VNCScreen;

import java.awt.AWTException;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.security.CodeSource;
import java.util.*;

/**
 * INTERNAL USE --- NOT official API<br>
 * not as is in version 2
 * <p>
 * Intended to concentrate all, that is needed at startup of sikulix or sikulixapi and may be at runtime by SikuliX or
 * any caller
 */
public class RunTime {

  static {
    Commons.init();
  }

  private static RunTime runTime = null;

  private static final String osNameShort = System.getProperty("os.name").substring(0, 1).toLowerCase();

  private static boolean startAsIDE = true;

  //<editor-fold desc="01 startup">
  private static boolean allowMultiple = false;

  private static boolean shouldRunServer() {
    return asServer;
  }

  private static boolean asServer = false;

  private static String[] getServerOptions() {
    return serverOptions;
  }

  private static String[] serverOptions = null;

  private static String getServerGroups() {
    return serverGroups;
  }

  private static String serverGroups = null;

  private static String getServerExtra() {
    return serverExtra;
  }

  private static String serverExtra = null;

  private static boolean asPythonServer = false;


  private static File asFolder(String option) {
    if (null == option) {
      return null;
    }
    File folder = new File(option);
    if (!folder.isAbsolute()) {
      folder = new File(Commons.getWorkDir(), option);
    }
    if (folder.isDirectory() && folder.exists()) {
      return folder;
    }
    return null;
  }


  private static void setAllowMultiple() {
    allowMultiple = true;
  }

  private static boolean isAllowMultiple() {
    return allowMultiple;
  }




  private static String[] userArgs = new String[0];
  private static String[] sxArgs = new String[0];

  private static boolean shouldRunScript = false;

  private static boolean runningScripts() {
    return shouldRunScript;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="02 logging">
  private int lvl = 3;
  private int minLvl = lvl;
  private static String preLogMessages = "";

  public static String arrayToQuotedString(String[] args) {
    String ret = "";
    for (String s : args) {
      if (s.contains(" ")) {
        s = "\"" + s + "\"";
      }
      ret += s + " ";
    }
    return ret;
  }

  private void log(int level, String message, Object... args) {
    Debug.logx(level, "RunTime:" + message, args);
  }

  private void logp(String message, Object... args) {
    Debug.logx(-3, message, args);
  }

  private void logp(int level, String message, Object... args) {
    if (level <= Debug.getDebugLevel()) {
      logp(message, args);
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="03 variables">
  public enum Type {
    IDE, API, INIT
  }

  private enum theSystem {
    WIN, MAC, LUX, FOO
  }

  public enum RunType {
    JAR, CLASSES, OTHER
  }

  public RunType runningAs = RunType.OTHER;

  public boolean runningIDE() {
    return Type.IDE.equals(runType);
  }

  private static Options sxOptions = null;

  private static boolean isTerminating = false;
  private static boolean hasDoneCleanUpTerminating = false;

  public static String appDataMsg = "";

  public static boolean testing = false;
  public static boolean testingWinApp = false;

  public Type runType = Type.INIT;

  public String jreVersion = java.lang.System.getProperty("java.runtime.version");

  private Class clsRef = RunTime.class;

  private List<URL> classPathActual = new ArrayList<>();
  private List<String> classPathList = new ArrayList<>();
  public String fpJarLibs = "/sikulixlibs/";
  public String fpSysLibs = null;

  public File fSikulixAppPath = null;
  public File fSikulixExtensions = null;
  public File fSikulixLib = null;
  public File fSikulixStore;
  public File fSikulixDownloadsGeneric = null;
  public File fSikulixSetup;

  public File fSxBase = null;
  public File fSxBaseJar = null;
  public File fSxProject = null;
  public File fSxProjectTestScriptsJS = null;
  public File fSxProjectTestScripts = null;
  public String fpContent = "sikulixcontent";

  public boolean runningJar = true;
  public boolean runningInProject = false;
  public boolean runningWindows = false;
  public boolean runningMac = false;
  public boolean runningLinux = false;
  private final String osNameSysProp = System.getProperty("os.name");
  private final String osVersionSysProp = System.getProperty("os.version");
  public int javaArch = 64;
  public String osName = "NotKnown";
  public String sysName = "NotKnown";
  public String osVersion = "";
  public String linuxDistro = "???LINUX???";
  public final static String runCmdError = "*****error*****";
  public static String NL = "\n";
  public File fLibsProvided;
  public File fLibsLocal;
  private String lastResult = "";

  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="04 instance">
  private RunTime() {
  }

  public static synchronized RunTime get() {
    if (runTime == null) {
      return get(Type.API);
    }
    return runTime;
  }

  static final long started = new Date().getTime();
  static final long obsolete = started - 2 * 24 * 60 * 60 * 1000;

  static boolean isObsolete(long refTime) {
    if (refTime == 0) {
      return false;
    }
    return refTime < obsolete;
  }

  static boolean optTesting = false;

  public boolean isTesting() {
    return optTesting;
  }

  public static synchronized RunTime get(Type typ) {
    if (runTime != null) {
      return runTime;
    }
    runTime = new RunTime();

    //<editor-fold defaultstate="collapsed" desc="versions">
    runTime.osVersion = runTime.osVersionSysProp;
    String os = runTime.osNameSysProp.toLowerCase();
    if (os.startsWith("windows")) {
      runTime.sysName = "windows";
      runTime.osName = "Windows";
      runTime.runningWindows = true;
      runTime.NL = "\r\n";
    } else if (os.startsWith("mac")) {
      runTime.sysName = "mac";
      runTime.osName = "Mac OSX";
      runTime.runningMac = true;
    } else if (os.startsWith("linux")) {
      runTime.sysName = "linux";
      runTime.osName = "Linux";
      runTime.runningLinux = true;
    } else {
      // Presume Unix -- pretend to be Linux
      runTime.sysName = os;
      runTime.osName = runTime.osNameSysProp;
      runTime.runningLinux = true;
      runTime.linuxDistro = runTime.osNameSysProp;
    }
    runTime.fpJarLibs += runTime.sysName + "/libs" + runTime.javaArch;
    runTime.fpSysLibs = runTime.fpJarLibs.substring(1);

    runTime.fSikulixAppPath = Commons.getAppDataPath();
    runTime.fSikulixStore = Commons.getAppDataStore();
    //</editor-fold>

    sxOptions = Options.create();
    optTesting = sxOptions.isOption("testing", false);
    if (optTesting) {
      Debug.info("Options: testing = on");
    }

    int optDebugLevel = optTesting ? Debug.getDebugLevel() : sxOptions.getOptionInteger("Debug.level", -1);
    if (optDebugLevel > Debug.getDebugLevel()) {
      Debug.info("Options: Debug.level = %d", optDebugLevel);
      Debug.on(optDebugLevel);
    }

    //TODO addShutdownHook
    hasDoneCleanUpTerminating = false;
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        runShutdownHook();
      }
    });

    runTime.init(typ);
    if (Type.IDE.equals(typ)) {
      runTime.initIDEbefore();
      runTime.initAPI();
      runTime.initIDEafter();
    } else {
      runTime.initAPI();
    }
    return runTime;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="05 global init">
  File isRunning = null;
  FileOutputStream isRunningFile = null;
  String isRunningFilename = "s_i_k_u_l_i-ide-isrunning";
  public static File fTempPath = null;
  public File fBaseTempPath = null;
  public String fpBaseTempPath = "";

  private void init(Type typ) {
    if ("winapp".equals(sxOptions.getOption("testing"))) {
      log(lvl, "***** for testing: simulating WinApp");
      testingWinApp = true;
    }
    for (String line : preLogMessages.split(";")) {
      if (!line.isEmpty()) {
        log(lvl, line);
      }
    }
    log(4, "global init: entering as: %s", typ);

    if (fTempPath == null) {
      String tmpdir = System.getProperty("java.io.tmpdir");
      if (tmpdir != null && !tmpdir.isEmpty()) {
        fTempPath = new File(tmpdir);
      } else {
        throw new SikuliXception("init: java.io.tmpdir not valid (null or empty");
      }
    }
    fBaseTempPath = new File(fTempPath, String.format("Sikulix_%d", FileManager.getRandomInt()));
    fpBaseTempPath = fBaseTempPath.getAbsolutePath();
    fBaseTempPath.mkdirs();
    try {
      File tempTest = new File(fBaseTempPath, "tempTest.txt");
      FileManager.writeStringToFile("temp test", tempTest);
      boolean success = true;
      if (tempTest.exists()) {
        tempTest.delete();
        if (tempTest.exists()) {
          success = false;
        }
      } else {
        success = false;
      }
      if (!success) {
        throw new SikuliXception(String.format("init: temp folder not useable: %s", fTempPath));
      }
    } catch (Exception e) {
      throw new SikuliXception(String.format("init: temp folder not useable: %s", fTempPath));
    }
    log(3, "temp folder ok: %s", fpBaseTempPath);
    if (Type.IDE.equals(typ) && !runningScripts() && !isAllowMultiple()) {
      isRunning = new File(fTempPath, isRunningFilename);
      boolean shouldTerminate = false;
      try {
        isRunning.createNewFile();
        isRunningFile = new FileOutputStream(isRunning);
        if (null == isRunningFile.getChannel().tryLock()) {
          Class<?> classIDE = Class.forName("org.sikuli.ide.SikulixIDE");
          Method stopSplash = classIDE.getMethod("stopSplash", new Class[0]);
          stopSplash.invoke(null, new Object[0]);
          Sikulix.popError("Terminating: IDE already running");
          shouldTerminate = true;
        }
      } catch (Exception ex) {
        Sikulix.popError("Terminating on FatalError: cannot access IDE lock for/n" + isRunning);
        shouldTerminate = true;
      }
      if (shouldTerminate) {
        System.exit(1);
      }
    }

    for (String aFile : fTempPath.list()) {
      if ((aFile.startsWith("Sikulix") && (new File(aFile).isFile()))
          || (aFile.startsWith("jffi") && aFile.endsWith(".tmp"))) {
        FileManager.deleteFileOrFolder(new File(fTempPath, aFile));
      }
    }

    try {
      if (!fSikulixAppPath.exists()) {
        fSikulixAppPath.mkdirs();
      }
      if (!fSikulixAppPath.exists()) {
        throw new SikuliXception(String.format(appDataMsg, fSikulixAppPath));
      }
      fSikulixExtensions = new File(fSikulixAppPath, "Extensions");
      if (!fSikulixExtensions.exists()) {
        fSikulixExtensions.mkdir();
      }
      fSikulixDownloadsGeneric = new File(fSikulixAppPath, "SikulixDownloads");
      if (!fSikulixDownloadsGeneric.exists()) {
        fSikulixDownloadsGeneric.mkdir();
      }
      fSikulixLib = new File(fSikulixAppPath, "Lib");
      fSikulixSetup = new File(fSikulixAppPath, "SikulixSetup");
      fLibsProvided = new File(fSikulixAppPath, fpSysLibs);
      fLibsLocal = fLibsProvided.getParentFile().getParentFile();
    } catch (Exception ex) {
      throw new SikuliXception(String.format(appDataMsg + ex.toString(), fSikulixAppPath));
    }

    clsRef = RunTime.class;
    CodeSource codeSrc = clsRef.getProtectionDomain().getCodeSource();
    fSxBaseJar = null;
    URL urlCodeSrc = null;
    String urlCodeSrcProto = "not-set";
    if (codeSrc != null) {
      urlCodeSrc = codeSrc.getLocation();
      urlCodeSrcProto = urlCodeSrc.getProtocol();
      if (null != codeSrc) {
        fSxBaseJar = new File(codeSrc.getLocation().getPath());
        if (urlCodeSrcProto == "file") {
          runningAs = RunType.CLASSES;
          if (urlCodeSrc.getPath().endsWith(".jar")) {
            runningAs = RunType.JAR;
          }
        } else {
          runningAs = RunType.OTHER;
        }
      }
    }
    if (fSxBaseJar != null) {
      String baseJarName = fSxBaseJar.getName();
      fSxBase = fSxBaseJar.getParentFile();
      log(4, "runningAs: %s (%s) in: %s", runningAs, baseJarName, fSxBase.getAbsolutePath());
      Debug.startTimer();
      if (baseJarName.contains("classes")) {
        runningJar = false;
        fSxProject = fSxBase.getParentFile().getParentFile();
        log(4, "not jar - supposing Maven project: %s", fSxProject);
        runningInProject = true;
      } else if ("target".equals(fSxBase.getName())) {
        fSxProject = fSxBase.getParentFile().getParentFile();
        log(4, "folder target detected - supposing Maven project: %s", fSxProject);
        runningInProject = true;
      } else {
        //TODO what???
      }
    } else {
      throw new SikuliXception(String.format("no valid Java context (%s)", clsRef));
    }
    if (runningInProject) {
      fSxProjectTestScriptsJS = new File(fSxProject, "StuffContainer/testScripts/testJavaScript");
      fSxProjectTestScripts = new File(fSxProject, "StuffContainer/testScripts");
    }

    runType = typ;
    if (Debug.getDebugLevel() == minLvl) {
      Commons.show();
    }
    log(4, "global init: leaving");
  }
  //</editor-fold>

  //<editor-fold desc="99 cleanUp">
  public static void terminate() {
    terminate(0, "");
  }

  public static void terminate(int retval, String message, Object... args) {
    String outMsg = String.format(message, args);
    if (!outMsg.isEmpty()) {
      System.out.println("TERMINATING: " + outMsg);
    }
    if (retval < 999) {
      isTerminating = true;
      cleanUp();
      System.exit(retval);
    }
    throw new SikuliXception(String.format("fatal: " + outMsg));
  }

  public static void cleanUp() {
    if (hasDoneCleanUpTerminating) {
      return;
    }
    if (!isTerminating) {
      runTime.log(3, "***** running cleanUp *****");
      Highlight.closeAll();
      Settings.DefaultHighlightColor = "RED";
      Settings.DefaultHighlightTime = 2.0f;
      Settings.Highlight = false;
      Settings.setShowActions(false);
      FindFailed.reset();
      OCR.reset();
      Settings.OcrLanguage = Settings.OcrLanguageDefault;
      Settings.OcrDataPath = null;
    }

    try {
      VNCScreen.stopAll();
      ADBScreen.stop();
    } catch (Exception e) {
      Debug.info("Error while stopping VNCScreen: %s", e.getMessage());
    }

    Observing.cleanUp();
    HotkeyManager.reset(isTerminating);
    if (null != cleanupRobot) {
      cleanupRobot.keyUp();
    }
    Mouse.reset();
    //TODO 2.0.5 cannot be done in shutdownhook: PreferencesUser.get().store();
    if (isTerminating) {
      hasDoneCleanUpTerminating = true;
    }
  }

  private static void runShutdownHook() {
    isTerminating = true;
    if (Debug.isGlobalDebug()) {
      Debug.on(3);
      Debug.globalDebugOn();
    }
    runTime.log(runTime.lvl, "***** final cleanup at System.exit() *****");
    cleanUp();

    if (runTime.isRunning != null) {
      try {
        runTime.isRunningFile.close();
      } catch (IOException ex) {
      }
      runTime.isRunning.delete();
    }

    for (File f : runTime.fTempPath.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        if (name.toLowerCase().contains("sikuli")) {
          if (name.contains("Sikulix_")) {
            if (isObsolete(new File(dir, name).lastModified()) || name.equals(runTime.fBaseTempPath.getName())) {
              return true;
            }
          } else {
            return true;
          }
        }
        return false;
      }
    })) {
      runTime.log(4, "cleanTemp: " + f.getName());
      FileManager.deleteFileOrFolder(f.getAbsolutePath());
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="13 Sikulix options handling">
  public String getOption(String oName) {
    return sxOptions.getOption(oName);
  }

  public Options options() {
    return sxOptions;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="07 init for IDE">
  public static boolean isRunningIDE = false;

  private void initIDEbefore() {
    log(4, "initIDEbefore: entering");
    isRunningIDE = true;
    log(4, "initIDEbefore: leaving");
  }

  private void initIDEafter() {
    log(4, "initIDEafter: entering");
    try {
      cIDE = Class.forName("org.sikuli.ide.SikulixIDE");
      mHide = cIDE.getMethod("hideIDE", new Class[0]);
      mShow = cIDE.getMethod("showIDE", new Class[0]);
    } catch (Exception ex) {
      log(-1, "SikulixIDE: reflection: %s", ex.getMessage());
    }
    log(4, "initIDEafter: leaving");
  }

  Class<?> cIDE = null;
  Method mHide = null;
  Method mShow = null;

  public void hideIDE() {
    if (null != cIDE) {
      try {
        mHide.invoke(null, new Object[0]);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }
  }

  public void showIDE() {
    if (null != cIDE) {
      try {
        mShow.invoke(null, new Object[0]);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="06 init for API">
  private static RobotDesktop cleanupRobot = null;

  private void initAPI() {
    log(4, "initAPI: entering");
    try {
      cleanupRobot = new RobotDesktop();
    } catch (AWTException e) {
    }
    log(4, "initAPI: leaving");
  }
  //</editor-fold>
}
