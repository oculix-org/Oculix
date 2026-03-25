/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.ide;

import nu.pattern.OpenCV;
import org.sikuli.basics.*;
import org.sikuli.support.FileManager;
import org.sikuli.support.runner.SikulixServer;
import org.sikuli.script.SikuliXception;
import org.sikuli.support.runner.IRunner;
import org.sikuli.support.ide.Runner;
import org.sikuli.support.Commons;
import org.sikuli.support.RunTime;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.support.ide.SikuliIDEI18N;
import javax.swing.UIManager;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

import static org.sikuli.util.CommandArgsEnum.*;

public class Sikulix {

  static SXDialog ideSplash = null;
  static int waitStart = 0;

  public static void stopSplash() {
    if (waitStart > 0) {
      try {
        Thread.sleep(waitStart * 1000);
      } catch (InterruptedException e) {
      }
    }

    if (ideSplash != null) {
      ideSplash.setVisible(false);
      ideSplash.dispose();
      ideSplash = null;
    }
  }

  public static void main(String[] args) {
    //region startup
    Commons.setStartClass(Sikulix.class);
    Commons.setStartArgs(args);

    if (Commons.hasArg("h")) {
      Commons.printHelp();
      System.exit(0);
    }
	try {
    UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
} catch (Exception e) {
    // fallback sur le LAF par défaut
}
    Commons.initOptions();

    Commons.globals().setOption("SX_LOCALE", SikuliIDEI18N.getLocaleShow());

    if (Commons.hasOption(APPDATA)) {
      String argValue = Commons.globals().getOption(APPDATA);
      File path = Commons.setAppDataPath(argValue);
      Commons.setTempFolder(new File(path, "Temp"));
    } else {
      Commons.setTempFolder();
    }

    if (Commons.hasOption(VERBOSE)) {
      Commons.show();
      Commons.showOptions("ARG_");
      Debug.globalDebugOn();
    }

    if (Commons.hasOption(CONSOLE)) {
      System.setProperty("sikuli.console", "false");
    }

    if (Commons.hasOption(DEBUG)) {
      Commons.globals().getOptionInteger("ARG_DEBUG", 3);
      Debug.setDebugLevel(3);
    }

    if (Commons.hasOption(RUN)) {
      HotkeyManager.getInstance().addHotkey("Abort", new HotkeyListener() {
        @Override
        public void hotkeyPressed(HotkeyEvent e) {
          if (Commons.hasOption(RUN)) {
            Runner.abortAll();
            RunTime.terminate(254, "AbortKey was pressed: aborting all running scripts");
          }
        }
      });
      String[] scripts = Runner.resolveRelativeFiles(Commons.getArgs("r"));
      int exitCode = Runner.runScripts(scripts, Commons.getUserArgs(), new IRunner.Options());
      if (exitCode > 255) {
        exitCode = 254;
      }
      RunTime.terminate(exitCode, "");
    }

    if (Commons.hasOption(SERVER)) {
      SikulixServer.run();
      RunTime.terminate();
    }

    Commons.startLog(1, "IDE starting (%4.1f)", Commons.getSinceStart());
    //endregion

    // chargement OpenCV natif via nu.pattern — extrait la DLL du jar Apertix automatiquement
    try {
      OpenCV.loadShared();
    } catch (Throwable e) {
      System.err.println("[error] OpenCV chargement echec: " + e.getMessage());
    }

    if (!Commons.hasOption(VERBOSE)) {
      ideSplash = new SXDialog("sxidestartup", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP);
      ideSplash.run();
    }

    if (!Commons.hasOption(MULTI)) {
      File isRunning;
      FileOutputStream isRunningFile;
      String isRunningFilename = "s_i_k_u_l_i-ide-isrunning";
      isRunning = new File(Commons.getTempFolder(), isRunningFilename);
      boolean shouldTerminate = false;
      try {
        isRunning.createNewFile();
        isRunningFile = new FileOutputStream(isRunning);
        if (null == isRunningFile.getChannel().tryLock()) {
          Class<?> classIDE = Class.forName("org.sikuli.ide.SikulixIDE");
          Method stopSplash = classIDE.getMethod("stopSplash", new Class[0]);
          stopSplash.invoke(null, new Object[0]);
          org.sikuli.script.Sikulix.popError("Terminating: IDE already running");
          shouldTerminate = true;
        } else {
          Commons.setIsRunning(isRunning, isRunningFile);
        }
      } catch (Exception ex) {
        org.sikuli.script.Sikulix.popError("Terminating on FatalError: cannot access IDE lock for/n" + isRunning);
        shouldTerminate = true;
      }
      if (shouldTerminate) {
        System.exit(1);
      }
      for (String aFile : Commons.getTempFolder().list()) {
        if ((aFile.startsWith("Sikulix"))
            || (aFile.startsWith("jffi") && aFile.endsWith(".tmp"))) {
          FileManager.deleteFileOrFolder(new File(Commons.getTempFolder(), aFile));
        }
      }
    }

    //region IDE temp folder
    File ideTemp = new File(Commons.getTempFolder(), String.format("Sikulix_%d", FileManager.getRandomInt()));
    ideTemp.mkdirs();
    try {
      File tempTest = new File(ideTemp, "tempTest.txt");
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
        throw new SikuliXception(String.format("init: temp folder not useable: %s", Commons.getTempFolder()));
      }
    } catch (Exception e) {
      throw new SikuliXception(String.format("init: temp folder not useable: %s", Commons.getTempFolder()));
    }
    Commons.setIDETemp(ideTemp);
    //endregion

    if (Commons.runningMac()) {
      try {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
      } catch (Exception e) {
      }
    }


    SikulixIDE.start();
  }
}