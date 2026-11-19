/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Cross-platform command execution: local, PowerShell, and the tools the remote
 * launch relies on (which, apt-get, ssh-keyscan, ssh-keygen), run inside WSL on Windows.
 */
public class CommandExecutor {

  /**
   * Execute a local command and return stdout as a stream of lines.
   * On Windows uses cmd /c, on Unix uses bash -c.
   */
  public static Stream<String> execLocal(String command) throws IOException {
    String[] cmd;
    if (Commons.runningWindows()) {
      cmd = new String[]{"cmd", "/c", command};
    } else {
      cmd = new String[]{"bash", "-c", command};
    }
    Process process = Runtime.getRuntime().exec(cmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    return reader.lines().onClose(() -> {
      try { reader.close(); } catch (IOException ignored) {}
      process.destroy();
    });
  }

  /**
   * Execute a PowerShell command.
   * Uses 'powershell' on Windows, 'pwsh' on Mac/Linux.
   */
  public static Stream<String> execPowershell(String command) throws IOException {
    String ps = Commons.runningWindows() ? "powershell" : "pwsh";
    Process process = Runtime.getRuntime().exec(
        new String[]{ps, "-Command", command});
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    return reader.lines().onClose(() -> {
      try { reader.close(); } catch (IOException ignored) {}
      process.destroy();
    });
  }

  /**
   * Run a command to completion, as an argument list (never re-parsed by a shell).
   * On Windows the command runs inside WSL through {@code wsl.exe --exec}.
   *
   * @param timeoutSeconds how long to wait before killing the process
   * @param args           the program and its arguments
   * @return true if the process ended in time with exit code 0
   */
  static boolean runToEnd(int timeoutSeconds, String... args) {
    List<String> command = new ArrayList<>();
    if (Commons.runningWindows()) {
      command.add("C:\\Windows\\System32\\wsl.exe");
      command.add("--exec");
    }
    command.addAll(Arrays.asList(args));
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      Process process = builder.start();
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * Launch a command without waiting for output (fire-and-forget).
   */
  public static void execFireAndForget(String command) throws IOException {
    Runtime.getRuntime().exec(command);
  }

  /**
   * Launch a command array without waiting for output.
   */
  public static void execFireAndForget(String[] command) throws IOException {
    Runtime.getRuntime().exec(command);
  }

  /**
   * Check if a command/tool is available on the system (or in WSL on Windows).
   */
  public static boolean isToolAvailable(String toolName) {
    return runToEnd(15, "which", toolName);
  }

  /**
   * Check if a process is running by name.
   */
  public static boolean isProcessRunning(String processName) {
    try {
      String cmd;
      if (Commons.runningWindows()) {
        cmd = "tasklist";
      } else if (Commons.runningMac()) {
        cmd = "ps aux";
      } else {
        cmd = "ps aux";
      }
      try (Stream<String> lines = execLocal(cmd)) {
        return lines.anyMatch(l -> l.toLowerCase().contains(processName.toLowerCase()));
      }
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Install a package via apt (WSL on Windows, direct on Linux), with {@code sudo -n}:
   * when sudo needs a password there is no terminal to type it, so the install fails
   * instead of hanging. Not available on macOS.
   *
   * @return true if apt-get ended with exit code 0
   */
  public static boolean aptInstall(String packageName) {
    if (Commons.runningMac()) {
      return false;
    }
    return runToEnd(600, "sudo", "-n", "apt-get", "install", "-y", packageName);
  }

  /**
   * Add a host to {@code ~/.ssh/known_hosts} via ssh-keyscan (WSL on Windows).
   * The host name is passed as an argument, never through a shell.
   *
   * @return true if the keys were written
   */
  public static boolean addSshFingerprint(String host) {
    return runToEnd(30, "sh", "-c",
        "mkdir -p ~/.ssh && ssh-keyscan -H \"$1\" >> ~/.ssh/known_hosts", "keyscan", host);
  }

  /**
   * Check if a host is in {@code ~/.ssh/known_hosts} (WSL on Windows).
   */
  public static boolean isSshHostKnown(String host) {
    return runToEnd(15, "ssh-keygen", "-F", host);
  }
}
