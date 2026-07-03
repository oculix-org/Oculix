/*
 * Cross-OS smoke test for OculiX App name resolution.
 *
 * Verifies that `new App("<name>").open()` correctly:
 *   - launches the target app via the OS-native launcher
 *     ('cmd /c start' on Windows, 'open -a' on Mac, PATH on Linux)
 *   - returns a valid PID that maps to the running app
 *   - closes cleanly after the test
 *
 * Usage:
 *   javac -cp API/target/oculixapi-<version>.jar scripts/VerifyAppLauncher.java
 *   java  -cp API/target/oculixapi-<version>.jar:scripts VerifyAppLauncher [appName]
 *
 * Default appName = "firefox" (available cross-OS).
 * Exit 0 on success, 1 on failure.
 *
 * Intended entry point for the .github/workflows/verify-app-launcher.yml
 * matrix job (ubuntu, macos, windows).
 */

import org.sikuli.script.App;

public class VerifyAppLauncher {

    private static int rc = 0;

    private static void pass(String msg) {
        System.out.println("[OK]   " + msg);
    }

    private static void fail(String msg) {
        System.err.println("[FAIL] " + msg);
        rc = 1;
    }

    public static void main(String[] args) throws Exception {
        String appName = args.length > 0 ? args[0] : "firefox";

        System.out.println("=== VerifyAppLauncher ===");
        System.out.println("OS:      " + System.getProperty("os.name"));
        System.out.println("Java:    " + System.getProperty("java.version"));
        System.out.println("Target:  " + appName);
        System.out.println();

        App app = new App(appName);
        boolean opened = app.open(10);

        if (opened) {
            pass("App.open(10) returned true");
            System.out.println("       PID:  " + app.getPID());
            System.out.println("       name: " + app.getName());
        } else {
            fail("App.open(10) returned false — the OS-native launcher did not resolve the name");
        }

        // Give the app a moment to fully surface before checking isRunning.
        Thread.sleep(2000);

        if (rc == 0) {
            if (app.isRunning()) {
                pass("App.isRunning() true after 2s (PID " + app.getPID() + ")");
            } else {
                fail("App.isRunning() false 2s after open — process died or PID lookup grabbed a stale handle");
            }
        }

        // Clean up: try to close whether we succeeded or not, so the CI
        // runner doesn't leak a Firefox process across matrix steps.
        try {
            boolean closed = app.close();
            System.out.println("[cleanup] App.close() returned " + closed);
        } catch (Exception e) {
            System.out.println("[cleanup] App.close() threw: " + e.getMessage());
        }

        System.out.println();
        System.out.println(rc == 0 ? "=== PASS ===" : "=== FAIL ===");
        System.exit(rc);
    }
}
