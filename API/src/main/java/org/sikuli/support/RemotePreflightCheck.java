/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Pre-flight checks before launching a remote VNC session through
 * {@link AppLauncher#launchRemoteVNC}. Local side: sshpass, the X server, the
 * target's host key in known_hosts. Target side: vncviewer, which is what the
 * launch runs over there, so it is looked for over there.
 */
public class RemotePreflightCheck {

  public static class CheckResult {
    public final String name;
    public final boolean passed;
    public final String message;
    /** What to do to pass next time; null when nothing can be done automatically. */
    public final BooleanSupplier autoFix;

    CheckResult(String name, boolean passed, String message, BooleanSupplier autoFix) {
      this.name = name;
      this.passed = passed;
      this.message = message;
      this.autoFix = autoFix;
    }

    /**
     * Run the automatic fix, if there is one.
     *
     * @return true if the fix ran and reported success
     */
    public boolean fix() {
      return autoFix != null && autoFix.getAsBoolean();
    }
  }

  /**
   * Run every check, local ones first. The target check needs ssh to work, so it is
   * skipped, and reported as such, while sshpass or the host key are still missing.
   */
  public static List<CheckResult> runAll(String targetHost, String user, String password) {
    List<CheckResult> results = new ArrayList<>();
    CheckResult sshpass = checkSshpass();
    CheckResult fingerprint = checkFingerprint(targetHost);
    results.add(sshpass);
    results.add(checkXServer());
    results.add(fingerprint);
    results.add(sshpass.passed && fingerprint.passed
        ? checkVncViewer(targetHost, user, password)
        : new CheckResult("vncviewer on " + targetHost, false,
            "not checked: ssh needs sshpass and the host key first", null));
    return results;
  }

  public static boolean allPassed(List<CheckResult> results) {
    return results.stream().allMatch(r -> r.passed);
  }

  /** sshpass on this machine (inside WSL on Windows). apt can install it; Homebrew is the user's call. */
  public static CheckResult checkSshpass() {
    if (CommandExecutor.isToolAvailable("sshpass")) {
      return new CheckResult("sshpass", true, "sshpass found", null);
    }
    if (Commons.runningMac()) {
      return new CheckResult("sshpass", false,
          "sshpass not installed — brew install hudochenkov/sshpass/sshpass", null);
    }
    String where = Commons.runningWindows() ? " in WSL" : "";
    return new CheckResult("sshpass", false,
        "sshpass not installed" + where + " — required for password-based SSH",
        () -> CommandExecutor.aptInstall("sshpass"));
  }

  /**
   * The X server the remote viewer window comes back to: native on Linux, VcXsrv on
   * Windows, XQuartz on macOS. A server that is installed but not running passes: the
   * launch starts it. Only a missing installation fails.
   */
  public static CheckResult checkXServer() {
    if (Commons.runningLinux()) {
      return new CheckResult("X Server", true, "Native X display (Linux)", null);
    }
    String serverName = Commons.runningWindows() ? "VcXsrv" : "XQuartz";
    String process = Commons.runningWindows() ? "vcxsrv.exe" : "XQuartz";
    if (CommandExecutor.isProcessRunning(process)) {
      return new CheckResult("X Server", true, serverName + " running", null);
    }
    boolean installed = Commons.runningWindows()
        ? new File(AppLauncher.VCXSRV_PATH).isFile()
        : new File("/Applications/Utilities/XQuartz.app").isDirectory();
    if (installed) {
      return new CheckResult("X Server", true, serverName + " installed, the launch will start it", null);
    }
    return new CheckResult("X Server", false,
        serverName + " not installed — install it, the launch needs it", null);
  }

  /**
   * vncviewer on the target, through the same ssh path as the launch. Nothing can be
   * installed over there from here: the message says what to install, and on what.
   */
  public static CheckResult checkVncViewer(String host, String user, String password) {
    String name = "vncviewer on " + host;
    try {
      if (AppLauncher.checkRemoteTool(host, user, password, "vncviewer")) {
        return new CheckResult(name, true, "vncviewer found on " + host, null);
      }
      return new CheckResult(name, false,
          "vncviewer not found on " + host + " — install tigervnc-viewer there", null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CheckResult(name, false, "interrupted", null);
    } catch (Exception e) {
      return new CheckResult(name, false, "ssh to " + host + " failed: " + e.getMessage(), null);
    }
  }

  /** The target's host key in ~/.ssh/known_hosts (inside WSL on Windows); ssh-keyscan can add it. */
  public static CheckResult checkFingerprint(String host) {
    if (host == null || host.isEmpty()) {
      return new CheckResult("SSH fingerprint", true, "No host specified", null);
    }
    if (CommandExecutor.isSshHostKnown(host)) {
      return new CheckResult("SSH fingerprint", true, host + " found in known_hosts", null);
    }
    return new CheckResult("SSH fingerprint", false,
        host + " not in known_hosts",
        () -> CommandExecutor.addSshFingerprint(host) && CommandExecutor.isSshHostKnown(host));
  }
}
