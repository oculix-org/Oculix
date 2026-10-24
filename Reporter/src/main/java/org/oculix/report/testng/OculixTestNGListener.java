package org.oculix.report.testng;

import org.oculix.report.OculixReporter;
import org.oculix.report.model.Outcome;
import org.testng.IConfigurationListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * TestNG integration. TestNG finds it through
 * {@code META-INF/services/org.testng.ITestNGListener}: the test author
 * writes nothing. Projects that already declare it with {@code @Listeners}
 * or in {@code testng.xml} keep working; the instance that sees the suite
 * start first records it, any other stays silent, so nothing is counted twice.
 *
 * <ul>
 *   <li>A {@code TestRun} is opened at the start of the suite.</li>
 *   <li>A {@code Test} is opened in the thread that runs each test method,
 *     so steps land on it, and closed with the verdict TestNG gave:
 *     passed, failed (assertion), error (any other throwable) or skipped
 *     with its reason.</li>
 *   <li>A failing {@code @BeforeXxx} / {@code @AfterXxx} gets its own entry.</li>
 *   <li>At the end of the suite, the HTML report is written to
 *     {@code target/oculix-report.html} (system property {@code oculix.report.out}).</li>
 * </ul>
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class OculixTestNGListener implements ITestListener, ISuiteListener, IConfigurationListener {

    private static final AtomicReference<OculixTestNGListener> OWNER = new AtomicReference<>();

    private boolean owns() {
        return OWNER.get() == this;
    }

    @Override
    public void onStart(ISuite suite) {
        OWNER.compareAndSet(null, this);
        if (!owns()) return;
        OculixReporter.startSuite(suite.getName() == null ? "TestNG Suite" : suite.getName());
    }

    @Override
    public void onStart(ITestContext context) {
        // Called once per <test> element. If the suite start was missed,
        // fall back here so we still produce a report.
        OWNER.compareAndSet(null, this);
        if (!owns()) return;
        if (OculixReporter.currentRun() == null) {
            OculixReporter.startSuite(context.getSuite().getName());
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        if (!owns()) return;
        OculixReporter.startTest(nameOf(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (!owns()) return;
        OculixReporter.endTest(Outcome.PASSED, null, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (!owns()) return;
        Throwable t = result.getThrowable();
        OculixReporter.endTest(t instanceof AssertionError ? Outcome.FAILED : Outcome.ERROR,
            OculixReporter.messageOf(t), OculixReporter.stackTraceOf(t));
    }

    /** Skipped by {@code SkipException}, a failed dependency or a failed configuration method. */
    @Override
    public void onTestSkipped(ITestResult result) {
        if (!owns()) return;
        Throwable t = result.getThrowable();
        String reason = t == null ? "" : OculixReporter.messageOf(t);
        if (OculixReporter.currentTest() != null) {
            OculixReporter.endTest(Outcome.SKIPPED, reason, "");
        } else {
            OculixReporter.skippedTest(nameOf(result), reason);
        }
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        if (!owns()) return;
        // Treated as passed for reporting purposes.
        OculixReporter.endTest(Outcome.PASSED, null, null);
    }

    /**
     * A configuration method that passed: nothing to report. Declared because
     * TestNG 6 makes it abstract, where TestNG 7 gives it a default body.
     */
    @Override
    public void onConfigurationSuccess(ITestResult result) {
    }

    /**
     * A configuration method that was skipped: the tests it guards are reported
     * as skipped through {@link #onTestSkipped}. Declared for TestNG 6, like
     * {@link #onConfigurationSuccess}.
     */
    @Override
    public void onConfigurationSkip(ITestResult result) {
    }

    @Override
    public void onConfigurationFailure(ITestResult result) {
        if (!owns()) return;
        Throwable t = result.getThrowable();
        OculixReporter.startTest(nameOf(result) + " (configuration)");
        OculixReporter.endTest(Outcome.ERROR, OculixReporter.messageOf(t), OculixReporter.stackTraceOf(t));
    }

    @Override
    public void onFinish(ISuite suite) {
        if (!owns()) return;
        try {
            OculixReporter.endSuite();
            OculixReporter.writeTo(Path.of(
                System.getProperty("oculix.report.out", "target/oculix-report.html")));
        } catch (IOException e) {
            System.err.println("OculiX reporter: failed to write HTML report: " + e.getMessage());
        } finally {
            OWNER.compareAndSet(this, null);
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        // No-op here; writing happens in onFinish(ISuite).
    }

    /** "LoginTest › validLogin", with the data-provider parameters when there are any. */
    private static String nameOf(ITestResult result) {
        String name = result.getTestClass().getRealClass().getSimpleName()
            + " › " + result.getMethod().getMethodName();
        Object[] params = result.getParameters();
        if (params != null && params.length > 0) {
            name += Arrays.stream(params).map(String::valueOf).collect(Collectors.joining(", ", "(", ")"));
        }
        return name;
    }
}
