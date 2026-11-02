/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.script.App;
import org.sikuli.support.AppLauncher;
import org.sikuli.support.RemotePreflightCheck;

import javax.swing.*;
import java.util.List;

import static org.sikuli.support.ide.SikuliIDEI18N._I;
/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */

class RecorderAppScope {

  private App currentApp = null;
  private String appVarName = null;
  private boolean firstActionDone = false;
  private boolean remoteMode = false;

  private final JButton btnLaunchApp;
  private final JButton btnCloseApp;
  private final JCheckBox chkScopeToApp;
  private final RecorderCodeGen codeGen;

  RecorderAppScope(JButton btnLaunchApp, JButton btnCloseApp, JCheckBox chkScopeToApp, RecorderCodeGen codeGen) {
    this.btnLaunchApp = btnLaunchApp;
    this.btnCloseApp = btnCloseApp;
    this.chkScopeToApp = chkScopeToApp;
    this.codeGen = codeGen;
  }

  boolean isAppScoped() {
    return currentApp != null && chkScopeToApp.isSelected();
  }

  String getAppVarName() {
    return appVarName;
  }

  App getCurrentApp() {
    return currentApp;
  }

  boolean warnIfNoApp(JDialog parent) {
    if (!firstActionDone && currentApp == null) {
      int answer = JOptionPane.showConfirmDialog(parent,
          _I("recorderAppScopeLaunchFirstBody"),
          _I("recorderAppScopeLaunchFirstTitle"),
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
      if (answer == JOptionPane.YES_OPTION) {
        handleLaunchApp(parent);
        return currentApp == null;
      }
    }
    firstActionDone = true;
    return false;
  }

  void handleLaunchApp(JDialog parent) {
    String[] modes = {"Local application", "Remote (VNC via SSH)"};
    int mode = JOptionPane.showOptionDialog(parent,
        "Choose launch mode:",
        _I("recorderAppScopeRemoteDlgTitle"),
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, modes, modes[0]);

    if (mode < 0) return;

    if (mode == 0) {
      launchLocal(parent);
    } else {
      launchRemoteVNC(parent);
    }
  }

  private void launchLocal(JDialog parent) {
    String appPath = JOptionPane.showInputDialog(parent,
        "Application path or command:", "Launch Local App", JOptionPane.PLAIN_MESSAGE);
    if (appPath == null || appPath.trim().isEmpty()) return;
    appPath = appPath.trim();

    try {
      currentApp = App.open(appPath);
      if (currentApp == null) {
        RecorderNotifications.error("Failed to launch: " + appPath);
        return;
      }

      remoteMode = false;
      onAppLaunched(appPath);
      codeGen.generateLaunchApp(appPath, appVarName, chkScopeToApp.isSelected());
      RecorderNotifications.success("Launched: " + appPath);
    } catch (Exception ex) {
      RecorderNotifications.error("Launch failed: " + ex.getMessage());
    }
  }

  private void launchRemoteVNC(JDialog parent) {
    JPanel panel = new JPanel(new MigLayout("wrap 2, insets 8", "[right][grow, fill]"));
    JTextField tfHost = new JTextField(20);
    JTextField tfUser = new JTextField(20);
    JPasswordField tfPass = new JPasswordField(20);
    JTextField tfPort = new JTextField("5900", 8);
    JPasswordField tfVncPass = new JPasswordField(20);

    panel.add(new JLabel(_I("recorderAppScopePromptHost")));
    panel.add(tfHost);
    panel.add(new JLabel(_I("recorderAppScopePromptUser")));
    panel.add(tfUser);
    panel.add(new JLabel(_I("recorderAppScopePromptPassword")));
    panel.add(tfPass);
    panel.add(new JLabel(_I("recorderAppScopePromptVncPort")));
    panel.add(tfPort);
    panel.add(new JLabel(_I("recorderAppScopePromptVncPassword")));
    panel.add(tfVncPass);

    int result = JOptionPane.showConfirmDialog(parent, panel,
        _I("recorderAppScopeRemoteVncTitle"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

    if (result != JOptionPane.OK_OPTION) return;

    final String host = tfHost.getText().trim();
    final String user = tfUser.getText().trim();
    final String password = new String(tfPass.getPassword());
    final String port = tfPort.getText().trim();
    final String vncPassword = tfVncPass.getPassword().length == 0 ? null : new String(tfVncPass.getPassword());

    if (host.isEmpty() || user.isEmpty()) {
      RecorderNotifications.error(_I("recorderAppScopeHostUserRequired"));
      return;
    }
    if (!port.matches("\\d{1,5}")) {
      RecorderNotifications.error(_I("recorderAppScopeVncPortInvalid"));
      return;
    }

    JDialog waitDialog = new JDialog(parent, _I("recorderAppScopePreflightTitle"), false);
    waitDialog.setSize(320, 80);
    waitDialog.setLocationRelativeTo(parent);
    waitDialog.setLayout(new java.awt.BorderLayout());
    JLabel waitLabel = new JLabel("  \u23F3 " + _I("recorderAppScopePreflightStarting"), SwingConstants.CENTER);
    waitLabel.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
    waitLabel.setFont(javax.swing.UIManager.getFont("defaultFont"));
    waitDialog.add(waitLabel, java.awt.BorderLayout.CENTER);
    waitDialog.setVisible(true);

    new javax.swing.SwingWorker<List<RemotePreflightCheck.CheckResult>, String>() {
      @Override
      protected List<RemotePreflightCheck.CheckResult> doInBackground() {
        publish(_I("recorderAppScopePreflightStarting"));
        return RemotePreflightCheck.runAll(host, user, password);
      }

      @Override
      protected void process(java.util.List<String> chunks) {
        if (!chunks.isEmpty()) waitLabel.setText("  \u23F3 " + chunks.get(chunks.size() - 1));
      }

      @Override
      protected void done() {
        waitDialog.dispose();
        try {
          List<RemotePreflightCheck.CheckResult> checks = get(90, java.util.concurrent.TimeUnit.SECONDS);

          if (!RemotePreflightCheck.allPassed(checks)) {
            StringBuilder msg = new StringBuilder(_I("recorderAppScopePreflightChecks"));
            for (RemotePreflightCheck.CheckResult check : checks) {
              msg.append(check.passed ? "  \u2713  " : "  \u2717  ");
              msg.append(check.name).append(": ").append(check.message).append("\n");
            }
            msg.append(_I("recorderAppScopePreflightFooter"));

            String[] options = {_I("recorderAppScopeOptionFixAll"),
                _I("recorderAppScopeOptionContinueAnyway"), _I("recorderAppScopeOptionCancel")};
            int choice = JOptionPane.showOptionDialog(parent, msg.toString(),
                _I("recorderAppScopePreflightResultsTitle"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);

            if (choice == 0) {
              applyFixes(checks);
              return;
            }
            if (choice == 2 || choice < 0) return;
          }

          waitLabel.setText("  \u23F3 " + _I("recorderAppScopeLaunchingVnc"));
          waitDialog.setVisible(true);

          AppLauncher.launchRemoteVNC(host, user, password, port);

          waitDialog.dispose();
          remoteMode = true;
          currentApp = new App("vncviewer");
          appVarName = "vncSession";

          btnLaunchApp.setEnabled(false);
          btnCloseApp.setEnabled(true);
          chkScopeToApp.setEnabled(true);

          codeGen.generateRemoteVnc(host, user, port, vncPassword);
          RecorderNotifications.success(_I("recorderAppScopeNotifyVncConnected") + " " + host);

        } catch (java.util.concurrent.TimeoutException ex) {
          waitDialog.dispose();
          RecorderNotifications.error(_I("recorderAppScopePreflightTimeout"));
        } catch (Exception ex) {
          waitDialog.dispose();
          RecorderNotifications.error(_I("recorderAppScopeNotifyRemoteFailed") + " " + ex.getMessage());
        }
      }
    }.execute();
  }

  /**
   * Runs every automatic fix off the event thread, then reports which ones passed and
   * which ones did not, instead of claiming success without looking.
   */
  private void applyFixes(List<RemotePreflightCheck.CheckResult> checks) {
    new javax.swing.SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() {
        StringBuilder report = new StringBuilder();
        for (RemotePreflightCheck.CheckResult check : checks) {
          if (check.passed) continue;
          String outcome;
          if (check.autoFix == null) {
            outcome = _I("recorderAppScopeFixManual");
          } else {
            outcome = check.fix() ? _I("recorderAppScopeFixDone") : _I("recorderAppScopeFixFailed");
          }
          report.append(check.name).append(": ").append(outcome).append("\n");
        }
        return report.toString();
      }

      @Override
      protected void done() {
        try {
          RecorderNotifications.info(_I("recorderAppScopeNotifyFixReport") + "\n" + get());
        } catch (Exception ex) {
          RecorderNotifications.error(_I("recorderAppScopeNotifyRemoteFailed") + " " + ex.getMessage());
        }
      }
    }.execute();
  }

  private void onAppLaunched(String appPath) {
    btnLaunchApp.setEnabled(false);
    btnCloseApp.setEnabled(true);
    chkScopeToApp.setEnabled(true);

    appVarName = appPath.replaceAll(".*[/\\\\]", "")
        .replaceAll("\\.[^.]+$", "")
        .replaceAll("[^a-zA-Z0-9]", "")
        .toLowerCase();
    if (appVarName.isEmpty()) appVarName = "app";
  }

  void handleCloseApp() {
    if (currentApp != null) {
      try {
        currentApp.close();
        codeGen.generateCloseApp(appVarName);
        RecorderNotifications.success("App closed");
      } catch (Exception ex) {
        RecorderNotifications.error("Close failed: " + ex.getMessage());
      }
    }
    currentApp = null;
    appVarName = null;
    remoteMode = false;
    btnLaunchApp.setEnabled(true);
    btnCloseApp.setEnabled(false);
    chkScopeToApp.setEnabled(false);
  }

  void focusAppIfScoped() {
    if (currentApp != null && chkScopeToApp.isSelected()) {
      try {
        currentApp.focus();
      } catch (Exception ignored) {
      }
    }
  }
}
