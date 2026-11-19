package org.oculix.report.junit5;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.oculix.report.OculixReporter;
import org.oculix.report.model.Outcome;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * JUnit Platform listener, found by the launcher through
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}:
 * the test author writes nothing. Surefire, Gradle, IntelliJ and Eclipse all
 * run tests through that launcher, whatever the engine (Jupiter, Vintage...).
 *
 * <p>One run per execution of the test plan, one report written at its end
 * to {@code target/oculix-report.html} (system property {@code oculix.report.out}).
 * Each test is named after its path in the plan, class included, so two
 * {@code login()} in two classes stay apart. Assumptions that do not hold are
 * skipped, as JUnit reports them; assertion errors are failures, any other
 * throwable an error; a failure outside a test ({@code @BeforeAll},
 * {@code @AfterAll}) gets its own entry instead of vanishing.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 5.0.0
 */
public class OculixPlatformListener implements TestExecutionListener {

    private volatile TestPlan plan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        plan = testPlan;
        OculixReporter.markPlatformListenerActive(true);
        OculixReporter.startSuite(System.getProperty("oculix.report.title", "Test run"));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            OculixReporter.endSuite();
            OculixReporter.writeTo(Path.of(
                System.getProperty("oculix.report.out", "target/oculix-report.html")));
        } catch (IOException e) {
            System.err.println("OculiX reporter: failed to write HTML: " + e.getMessage());
        } finally {
            OculixReporter.markPlatformListenerActive(false);
        }
    }

    /** Opens the test in the thread that is about to run it, so steps land on it. */
    @Override
    public void executionStarted(TestIdentifier id) {
        if (id.isTest()) {
            OculixReporter.startTest(nameOf(id));
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (id.isTest()) {
            OculixReporter.skippedTest(nameOf(id), reason);
            return;
        }
        TestPlan current = plan;
        if (current == null) return;
        for (TestIdentifier descendant : current.getDescendants(id)) {
            if (descendant.isTest()) {
                OculixReporter.skippedTest(nameOf(descendant), reason);
            }
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        Throwable t = result.getThrowable().orElse(null);
        if (id.isTest()) {
            switch (result.getStatus()) {
                case SUCCESSFUL:
                    OculixReporter.endTest(Outcome.PASSED, null, null);
                    break;
                case ABORTED:
                    OculixReporter.endTest(Outcome.SKIPPED, t == null ? "" : t.getMessage(), "");
                    break;
                default:
                    OculixReporter.endTest(outcomeOf(t),
                        OculixReporter.messageOf(t), OculixReporter.stackTraceOf(t));
            }
        } else if (result.getStatus() == TestExecutionResult.Status.FAILED) {
            OculixReporter.startTest(nameOf(id) + " (setup / teardown)");
            OculixReporter.endTest(Outcome.ERROR,
                OculixReporter.messageOf(t), OculixReporter.stackTraceOf(t));
        }
    }

    private static Outcome outcomeOf(Throwable t) {
        return t instanceof AssertionError ? Outcome.FAILED : Outcome.ERROR;
    }

    /** "LoginTest › validLogin()": display names from the class down, engine left out. */
    private String nameOf(TestIdentifier id) {
        TestPlan current = plan;
        if (current == null) return id.getDisplayName();
        Deque<String> names = new ArrayDeque<>();
        TestIdentifier node = id;
        while (node != null) {
            Optional<TestIdentifier> parent = current.getParent(node);
            if (!parent.isPresent()) break;
            names.addFirst(node.getDisplayName());
            node = parent.get();
        }
        return String.join(" › ", names);
    }
}
