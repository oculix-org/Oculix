package org.oculix.report.junit5;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.oculix.report.OculixReporter;
import org.oculix.report.model.Outcome;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * JUnit 5 (Jupiter) integration. Add
 * {@code @ExtendWith(OculixJUnit5Extension.class)} to your test class:
 *
 * <pre>
 * &#64;ExtendWith(OculixJUnit5Extension.class)
 * public class LoginTest {
 *   Screen screen = OculixReporter.wrapScreen();
 *
 *   &#64;Test
 *   void validLogin() {
 *     screen.click("login.png");
 *     // ...
 *   }
 * }
 * </pre>
 *
 * <p>A {@code TestRun} is opened when the first test class starts and
 * closed when the last one finishes. The HTML lands in
 * {@code target/oculix-report.html} (override via system property
 * {@code oculix.report.out}).
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class OculixJUnit5Extension
    implements BeforeAllCallback, AfterAllCallback,
               BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final ExtensionContext.Namespace NS =
        ExtensionContext.Namespace.create("org.oculix.report");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (OculixReporter.isPlatformListenerActive()) return;
        if (OculixReporter.currentRun() == null) {
            OculixReporter.startSuite(context.getDisplayName());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Suite lifetime is root-context-bound: we close only when the
        // root context is the class under test and no other classes share
        // the run. For simple single-class runs this closes correctly; for
        // multi-class suites users should prefer TestNG or write closing
        // glue in their own @AfterAll.
        if (OculixReporter.isPlatformListenerActive()) return;
        if (isTopLevel(context)) {
            OculixReporter.endSuite();
            try {
                OculixReporter.writeTo(Path.of(
                    System.getProperty("oculix.report.out", "target/oculix-report.html")));
            } catch (IOException e) {
                System.err.println("OculiX reporter: failed to write HTML: " + e.getMessage());
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (OculixReporter.isPlatformListenerActive()) return;
        OculixReporter.startTest(context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Actual outcome is reported via the TestWatcher callbacks below.
        // This hook is kept for symmetry; no-op here.
    }

    // ---- TestWatcher — tells us the outcome ----

    @Override
    public void testSuccessful(ExtensionContext context) {
        if (OculixReporter.isPlatformListenerActive()) return;
        OculixReporter.endTest(Outcome.PASSED, null, null);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        if (OculixReporter.isPlatformListenerActive()) return;
        OculixReporter.endTest(cause instanceof AssertionError ? Outcome.FAILED : Outcome.ERROR,
            OculixReporter.messageOf(cause), OculixReporter.stackTraceOf(cause));
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        if (OculixReporter.isPlatformListenerActive()) return;
        OculixReporter.endTest(Outcome.SKIPPED, cause == null ? "" : cause.getMessage(), "");
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        if (OculixReporter.isPlatformListenerActive()) return;
        OculixReporter.skippedTest(context.getDisplayName(), reason.orElse(""));
    }

    private static boolean isTopLevel(ExtensionContext context) {
        return !context.getParent().flatMap(ExtensionContext::getTestClass).isPresent();
    }
}
