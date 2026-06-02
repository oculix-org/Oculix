/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.ide;

import org.sikuli.basics.*;
import org.sikuli.script.SX;
import org.sikuli.support.FileManager;
import org.sikuli.support.runner.SikulixServer;
import org.sikuli.script.SikuliXception;
import org.sikuli.support.runner.IRunner;
import org.sikuli.support.runner.Runner;
import org.sikuli.support.Commons;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.support.ide.SikuliIDEI18N;

import com.formdev.flatlaf.FlatLaf;
import org.sikuli.ide.theme.OculixDarkLaf;
import org.sikuli.ide.theme.OculixFonts;
import org.sikuli.ide.theme.OculixLightLaf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

  private static File ideIsRunningFile = null;
  private static FileOutputStream ideIsRunningStrean;

  public static void setIsRunning(File tokenFile, FileOutputStream tokenStream) {
    ideIsRunningFile = tokenFile;
    ideIsRunningStrean = tokenStream;
  }

  public static void main(String[] args) {

    //region startup
    Commons.setStartClass(Sikulix.class);
    Commons.setStartArgs(args);

    if (Commons.hasArg("h")) {
      Commons.printHelp();
      System.exit(0);
    }

    //TODO CmdArgs vs. Options
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
      Commons.loadOpenCV();
      HotkeyManager.getInstance().addHotkey("Abort", new HotkeyListener() {
        @Override
        public void hotkeyPressed(HotkeyEvent e) {
          if (Commons.hasOption(RUN)) {
            Runner.abortAll();
            Commons.terminate(254, "AbortKey was pressed: aborting all running scripts");
          }
        }
      });
      String[] scripts = Runner.resolveRelativeFiles(Commons.getArgs("r"));
      int exitCode = Runner.runScripts(scripts, Commons.getUserArgs(), new IRunner.Options());
      if (exitCode > 255) {
        exitCode = 254;
      }
      Commons.terminate(exitCode, "");
    }

    if (Commons.hasOption(SERVER)) {
      SikulixServer.run();
      Commons.terminate();
    }
    //TODO obsolete?
    // Mode serveur: java -jar oculixapi.jar -s
    // Demarre le ServerRunner legacy sur le port 50001
    //    for (String arg : args) {
    //      if ("-s".equals(arg)) {
    //        try {
    //          Class<?> cServer = Class.forName("org.sikuli.support.runner.ServerRunner");
    //          cServer.getMethod("run").invoke(null);
    //          System.exit(0);
    //        } catch (Exception e) {
    //          System.err.println("[ERROR] Failed to start ServerRunner: " + e.getMessage());
    //          e.printStackTrace();
    //          System.exit(1);
    //        }
    //      }
    //    }


    Commons.startLog(1, "IDE starting (%4.1f)", Commons.getSinceStart());
    //endregion

    // FlatLaf must be initialized before any Swing component creation.
    // Order matters: first register the bundled OculiX fonts (Inter / JetBrains
    // Mono / Fraunces) so FlatLaf can resolve them via the .properties theme,
    // then set preferred font families (used as fallback when our brand
    // families are not yet referenced explicitly), then install the LaF
    // (OculixDarkLaf / OculixLightLaf — FlatDarkLaf / FlatLightLaf subclasses
    // that layer the OculiX brand tokens on top).
    OculixFonts.setup();
    // Brand families (Inter, JetBrains Mono) are Latin-only and produce
    // tofu glyphs on CJK / Arabic / Cyrillic / Indic scripts. When the
    // active user locale falls in that category, switch FlatLaf's preferred
    // families to Java's logical "Dialog" / "Monospaced": those auto-
    // composite with system fonts (Segoe UI on Windows, .AppleSystemUI on
    // macOS, DejaVu / Noto on Linux) that all carry full Unicode coverage.
    // Trade-off: the user loses the brand typography on their locale — an
    // acceptable cost since unreadable boxes are the worst outcome.
    if (OculixFonts.currentLocaleNeedsFallback()) {
      FlatLaf.setPreferredFontFamily(java.awt.Font.DIALOG);
      FlatLaf.setPreferredMonospacedFontFamily(java.awt.Font.MONOSPACED);
    } else {
      FlatLaf.setPreferredFontFamily("Inter");
      FlatLaf.setPreferredMonospacedFontFamily("JetBrains Mono");
    }
    String ideTheme = PreferencesUser.get().getIdeTheme();
    if (PreferencesUser.THEME_LIGHT.equals(ideTheme)) {
      OculixLightLaf.setup();
    } else {
      OculixDarkLaf.setup();
    }

    ideSplash = new SXDialog("sxidestartup", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP);
    ideSplash.run();

    if (Commons.hasOption(VERBOSE)) {
      Commons.show();
      Commons.showOptions("ARG_");
    }

    // Belt-and-suspenders: make sure the splash is dismissed on any JVM exit
    // (crash mid-startup, Ctrl+C, uncaught exception, etc.). Without this an
    // abrupt termination can leave the splash window on top indefinitely.
    Runtime.getRuntime().addShutdownHook(new Thread(Sikulix::stopSplash, "oculix-splash-closer"));

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        if (ideIsRunningFile != null) {
          try {
            ideIsRunningStrean.close();
          } catch (IOException ex) {
          }
          ideIsRunningFile.delete();
        }
      }
    });

    File ideLockFile = new File(Commons.getTempFolder(), "s_i_k_u_l_i-ide-isrunning");
    boolean shouldTerminate = false;
    String terminateMsg = null;

    try {
      boolean possibleSecondIDE = ! ideLockFile.createNewFile();
      FileOutputStream fileOutputStream = new FileOutputStream(ideLockFile);
      if (null == fileOutputStream.getChannel().tryLock()) {
        terminateMsg = "Terminating: another IDE instance is already running";
        shouldTerminate = true;
      } else {
        if (possibleSecondIDE) {Commons.debug("IDE startup: Reusing an existing IDE lockfile");}
        setIsRunning(ideLockFile, fileOutputStream);
      }
    } catch (Exception ex) {
      terminateMsg = "Terminating on FatalError: cannot access IDE lock\n"
          + ideLockFile + "\n" + ex.getMessage();
      shouldTerminate = true;
    }
    if (shouldTerminate) {
      // Dismiss the splash BEFORE showing the popup - otherwise the top-most
      // splash hides the error dialog and the user only sees a stuck splash.
      stopSplash();
      SX.popError(terminateMsg);
      System.exit(1);
    }

    for (String aFile : Commons.getTempFolder().list()) {
      if ((aFile.startsWith("Sikulix"))
          || (aFile.startsWith("jffi") && aFile.endsWith(".tmp"))) {
        FileManager.deleteFileOrFolder(new File(Commons.getTempFolder(), aFile));
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

    // apple.laf.useScreenMenuBar removed — FlatLaf handles macOS menu integration natively

    SikulixIDE.start();

    //TODO start IDE in subprocess?
    //region IDE subprocess
    if (false) {
      /*
      if (false) {
        RunTime.terminate(999, "//TODO start IDE in subprocess?");
        List<String> cmd = new ArrayList<>();
        System.getProperty("java.home");
        if (Commons.runningWindows()) {
          cmd.add(System.getProperty("java.home") + "\\bin\\java.exe");
        } else {
          cmd.add(System.getProperty("java.home") + "/bin/java");
        }
        if (!Commons.isJava8()) {
      */
//      Suppress Java 9+ warnings
//      --add-opens
//      java.desktop/javax.swing.plaf.basic=ALL-UNNAMED
//      --add-opens
//      java.base/sun.nio.ch=ALL-UNNAMED
//      --add-opens
//      java.base/java.io=ALL-UNNAMED
/*

//TODO IDE start: --add-opens supress warnings
          cmd.add("--add-opens");
          cmd.add("java.desktop/javax.swing.plaf.basic=ALL-UNNAMED");
          cmd.add("--add-opens");
          cmd.add("java.base/sun.nio.ch=ALL-UNNAMED");
          cmd.add("--add-opens");
          cmd.add("java.base/java.io=ALL-UNNAMED");
        }

        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dsikuli.IDE_should_run");

        if (!classPath.isEmpty()) {
          cmd.add("-cp");
          cmd.add(classPath);
        }

        cmd.add("org.sikuli.ide.SikulixIDE");
//      cmd.addAll(finalArgs);

        RunTime.startLog(3, "*********************** leaving start");
        //TODO detach IDE: for what does it make sense?
*/
/*
    if (shouldDetach()) {
      ProcessRunner.detach(cmd);
      System.exit(0);
    } else {
      int exitCode = ProcessRunner.runBlocking(cmd);
      System.exit(exitCode);
    }
*/
/*

        int exitCode = ProcessRunner.runBlocking(cmd);
        System.exit(exitCode);
      }
      //endregion
*/
    }
    //endregion
  }
}
