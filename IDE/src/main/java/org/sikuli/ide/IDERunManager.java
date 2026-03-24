/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.sikuli.basics.Debug;
import org.sikuli.basics.HotkeyEvent;
import org.sikuli.basics.HotkeyListener;
import org.sikuli.basics.HotkeyManager;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.basics.Settings;
import org.sikuli.script.Image;
import org.sikuli.script.Key;
import org.sikuli.script.Sikulix;
import org.sikuli.support.Commons;
import org.sikuli.support.RunTime;
import org.sikuli.support.ide.JythonSupport;
import org.sikuli.support.ide.Runner;
import org.sikuli.support.runner.IRunner;
import org.sikuli.support.runner.JythonRunner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URL;

class IDERunManager {

  private static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private final SikulixIDE ide;

  IDERunManager(SikulixIDE ide) {
    this.ide = ide;
  }

  // --- Run buttons ---

  private ButtonRun btnRun;
  private ButtonRunViz btnRunSlow;

  ButtonRun getBtnRun() {
    return btnRun;
  }

  ButtonRunViz getBtnRunSlow() {
    return btnRunSlow;
  }

  ButtonRun createBtnRun() {
    btnRun = new ButtonRun();
    return btnRun;
  }

  ButtonRunViz createBtnRunSlow() {
    btnRunSlow = new ButtonRunViz();
    return btnRunSlow;
  }

  // --- Error marks ---

  void addErrorMark(int line) {
    if (line < 1) {
      return;
    }
    JScrollPane scrPane = (JScrollPane) ide.getTabs().getSelectedComponent();
    EditorLineNumberView lnview = (EditorLineNumberView) (scrPane.getRowHeader().getView());
    lnview.addErrorMark(line);
    EditorPane codePane = ide.getCurrentCodePane();
    codePane.jumpTo(line);
    codePane.requestFocus();
  }

  void resetErrorMark() {
    JScrollPane scrPane = (JScrollPane) ide.getTabs().getSelectedComponent();
    EditorLineNumberView lnview = (EditorLineNumberView) (scrPane.getRowHeader().getView());
    lnview.resetErrorMark();
  }

  // --- Stop / hotkey ---

  void installStopHotkey() {
    HotkeyManager.getInstance().addHotkey("Abort", new HotkeyListener() {
      @Override
      public void hotkeyPressed(HotkeyEvent e) {
        onStopRunning();
      }
    });
  }

  void onStopRunning() {
    log("AbortKey was pressed: aborting all running scripts");
    Runner.abortAll();
    EventQueue.invokeLater(() -> {
      ide.getBtnRecord().stopRecord();
    });
  }

  // --- Inner classes: ButtonRun / ButtonRunViz ---

  class ButtonRun extends ButtonOnToolbar {

    private Thread thread = null;

    ButtonRun() {
      super();

      URL imageURL = SikulixIDE.class.getResource("/icons/run_big_green.png");
      setIcon(new ImageIcon(imageURL));
      initTooltip();
      addActionListener(this);
      setText(SikulixIDE._I("btnRunLabel"));
      //setMaximumSize(new Dimension(45,45));
    }

    private void initTooltip() {
      PreferencesUser pref = PreferencesUser.get();
      String strHotkey = Key.convertKeyToText(
              pref.getStopHotkey(), pref.getStopHotkeyModifiers());
      String stopHint = SikulixIDE._I("btnRunStopHint", strHotkey);
      setToolTipText(SikulixIDE._I("btnRun", stopHint));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
      runCurrentScript();
    }

    void runCurrentScript() {
      log("************** before RunScript"); //TODO
      //doBeforeQuitOrRun();

      SikulixIDE.getStatusbar().resetMessage();
      SikulixIDE.doHide();

      final PaneContext context = ide.getActiveContext();
      EditorPane editorPane = context.getPane();
      if (editorPane.getDocument().getLength() == 0) {
        log("Run script not possible: Script is empty");
        return;
      }
      context.save();

      new Thread(new Runnable() {
        @Override
        public void run() {
          if (System.out.checkError()) {
            boolean shouldContinue = Sikulix.popAsk("System.out is broken (console output)!"
                    + "\nYou will not see any messages anymore!"
                    + "\nSave your work and restart the IDE!"
                    + "\nYou may ignore this on your own risk!" +
                    "\nYes: continue  ---  No: back to IDE", "Fatal Error");
            if (!shouldContinue) {
              log("Run script aborted: System.out is broken (console output)");
              SikulixIDE.showAgain();
              return;
            }
            log("Run script continued, though System.out is broken (console output)");
          }

          RunTime.pause(0.1f);
          ide.clearMessageArea();
          resetErrorMark();
          doBeforeRun();

          IRunner.Options runOptions = new IRunner.Options();
          runOptions.setRunningInIDE();

          int exitValue = -1;
          try {
            IRunner runner = context.getRunner();
            //TODO make reloadImported specific for each editor tab
            if (runner.getType().equals(JythonRunner.TYPE)) {
              JythonSupport.get().reloadImported();
            }
            exitValue = runner.runScript(context.getFile().getAbsolutePath(), Commons.getUserArgs(), runOptions);
          } catch (Exception e) {
            log("Run Script: internal error:");
            e.printStackTrace();
          } finally {
            Runner.setLastScriptRunReturnCode(exitValue);
          }

          log("************** after RunScript");
          addErrorMark(runOptions.getErrorLine());
          if (Image.getIDEshouldReload()) {
            int line = context.getPane().getLineNumberAtCaret(context.getPane().getCaretPosition());
            context.reparse();
            context.getPane().jumpTo(line);
          }

          RunTime.cleanUpAfterScript();
          SikulixIDE.showAgain();
        }
      }).start();
    }

    void doBeforeRun() {
      PreferencesUser prefs = PreferencesUser.get();
      Settings.ActionLogs = prefs.getPrefMoreLogActions();
      Settings.DebugLogs = prefs.getPrefMoreLogDebug();
      Settings.InfoLogs = prefs.getPrefMoreLogInfo();
      Settings.Highlight = prefs.getPrefMoreHighlight();
    }
  }

  class ButtonRunViz extends ButtonRun {

    ButtonRunViz() {
      super();
      URL imageURL = SikulixIDE.class.getResource("/icons/run_big_yl.png");
      setIcon(new ImageIcon(imageURL));
      setToolTipText(SikulixIDE._I("menuRunRunAndShowActions"));
      setText(SikulixIDE._I("btnRunSlowMotionLabel"));
    }

    @Override
    protected void doBeforeRun() {
      super.doBeforeRun();
      Settings.setShowActions(true);
    }

  }
}
