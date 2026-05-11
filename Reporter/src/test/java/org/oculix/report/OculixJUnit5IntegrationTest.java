package org.oculix.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtendWith;
import org.oculix.report.junit5.OculixJUnit5Extension;
import org.oculix.report.model.Outcome;
import org.oculix.report.model.Step;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end integration of the JUnit 5 extension and the
 * {@link OculixReporter} facade. Pure facade exercise — no OpenCV, no
 * display required — so it runs in headless CI.
 *
 * <p>Produces {@code target/oculix-report.html} that you can open to
 * verify the full pipeline: extension hooks fire → facade keeps state →
 * HtmlRenderer writes the report.
 *
 * <p>One test simulates a FAILED outcome to demonstrate the failure
 * rendering path in the HTML report (see {@link #simulatedFailedTest()}).
 * Rather than actually throwing — which would surface as an ERROR in the
 * {@code mvn install} console output (#271) — it injects a FAILED step
 * directly through the model API. The "worst wins" rule in {@link
 * org.oculix.report.model.Test#addStep(org.oculix.report.model.Step)}
 * promotes the whole test to FAILED in the HTML report, exactly as a
 * real {@code FindFailed} would.
 */
@ExtendWith(OculixJUnit5Extension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("OculiX Reporter — JUnit 5 integration")
/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
class OculixJUnit5IntegrationTest {

    @org.junit.jupiter.api.Test
    @Order(1)
    @DisplayName("passed: click + type + wait sequence")
    void passedTest() {
        // Sanity: the listener opened a Test for us
        assertNotNull(OculixReporter.currentTest(),
            "Extension should have pushed a Test into the ThreadLocal");

        // Simulate three OculiX actions (we're not wiring a real Screen —
        // real Screen construction would require OpenCV natives which aren't
        // available in headless test envs).
        addFakeStep("oculix.click", "username.png", Outcome.PASSED, null, null);
        addFakeStep("oculix.type", "admin", Outcome.PASSED, null, null);
        addFakeStep("oculix.click", "login.png", Outcome.PASSED, null, null);

        assertEquals(3, OculixReporter.currentTest().steps().size(),
            "Three steps should be attached to this test");
    }

    @org.junit.jupiter.api.Test
    @Order(2)
    @DisplayName("failed: simulated FindFailed on dashboard.png")
    void simulatedFailedTest() {
        addFakeStep("oculix.click", "menu.png", Outcome.PASSED, null, null);
        // Inject a FAILED step directly through the model API so the HTML
        // demo shows a FAILED test row with stack trace, without actually
        // throwing — which would surface as ERROR in `mvn install` output
        // (#271). The Test.addStep "worst wins" rule promotes the test
        // outcome to FAILED on its own.
        String fakeStack = String.join("\n",
            "org.sikuli.script.FindFailed: can not find dashboard.png after 5.0s",
            "\tat org.sikuli.script.Region.wait(Region.java:1234)",
            "\tat com.example.demo.LoginFlow.openDashboard(LoginFlow.java:42)");
        addFakeStep("oculix.click", "dashboard.png", Outcome.FAILED,
            "FindFailed: can not find dashboard.png after 5.0s (min similarity 0.70)",
            fakeStack);
        assertEquals(Outcome.FAILED, OculixReporter.currentTest().outcome(),
            "addStep(FAILED) should promote the whole test to FAILED");
    }

    @org.junit.jupiter.api.Test
    @Order(3)
    @Disabled("demo skipped test to illustrate the SKIPPED outcome")
    @DisplayName("skipped: illustrates the SKIPPED outcome")
    void skippedTest() {
        // Never runs.
    }

    // ---- Helpers -----------------------------------------------------

    private static void addFakeStep(String action, String target,
                                    Outcome outcome, String errorMessage, String stack) {
        Step step = new Step(action, target, Instant.now());
        // Tiny sleep so step duration is visible in the timeline
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        step.end(Instant.now(), outcome);
        if (errorMessage != null) step.withError(errorMessage, stack == null ? "" : stack);
        OculixReporter.currentTest().addStep(step);
    }
}
