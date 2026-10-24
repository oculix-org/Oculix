package org.oculix.report.steps;

import org.oculix.report.OculixReporter;
import org.oculix.report.model.Outcome;
import org.oculix.report.model.Step;
import org.oculix.report.model.Test;

import java.time.Instant;

/**
 * Turns one library call into one step of the test running in the calling
 * thread. Shared by every aspect (Selenium today, OculiX next), which only
 * decide what to intercept and how to name it.
 *
 * <p>A call made while another step of the same thread is running is not a
 * step of its own: {@code Select.selectByVisibleText} clicks an option, and
 * the report shows the selection, not the click inside it. Outside a test,
 * the call simply runs.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 5.0.0
 */
public final class StepRecorder {

    /** The intercepted call itself, as the aspect hands it over. */
    @FunctionalInterface
    public interface Call {
        Object proceed() throws Throwable;
    }

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private StepRecorder() {}

    /**
     * Runs {@code call} and records it as a step named {@code action} on
     * {@code target}: passed when it returns, failed or error with the full
     * exception when it throws. The exception is rethrown unchanged.
     */
    public static Object record(String action, String target, Call call) throws Throwable {
        Test test = OculixReporter.currentTest();
        if (test == null || inStep()) {
            return call.proceed();
        }
        Step step = new Step(action, target, Instant.now());
        DEPTH.get()[0]++;
        try {
            Object result = call.proceed();
            step.end(Instant.now(), Outcome.PASSED);
            return result;
        } catch (Throwable t) {
            fail(step, t);
            throw t;
        } finally {
            DEPTH.get()[0]--;
            test.addStep(step);
        }
    }

    /**
     * Runs {@code call} and records a step only when it throws. For lookups
     * such as {@code findElement}: the action that follows names the element,
     * a step per lookup would only double every line.
     */
    public static Object recordOnFailure(String action, String target, Call call) throws Throwable {
        Test test = OculixReporter.currentTest();
        if (test == null || inStep()) {
            return call.proceed();
        }
        Instant start = Instant.now();
        DEPTH.get()[0]++;
        try {
            return call.proceed();
        } catch (Throwable t) {
            Step step = new Step(action, target, start);
            fail(step, t);
            test.addStep(step);
            throw t;
        } finally {
            DEPTH.get()[0]--;
        }
    }

    /**
     * Runs {@code work} as part of the current step, so that whatever library
     * call it makes (reading an attribute to name a target) is not recorded.
     */
    public static <T> T quietly(java.util.function.Supplier<T> work) {
        DEPTH.get()[0]++;
        try {
            return work.get();
        } finally {
            DEPTH.get()[0]--;
        }
    }

    /** A step opened by a listener's "before" event, waiting for its "after". */
    private static final class Open {
        final String key;
        final Test test;
        final Step step;

        Open(String key, Test test, Step step) {
            this.key = key;
            this.test = test;
            this.step = step;
        }
    }

    private static final ThreadLocal<java.util.Deque<Open>> OPEN =
        ThreadLocal.withInitial(java.util.ArrayDeque::new);

    /**
     * Opens a step from a listener's "before" event. {@code key} is the name of
     * the intercepted method, used to close the right step when the error event
     * does not say which call failed in the same terms. Every call to begin
     * must be balanced by {@link #end} or {@link #fail}.
     */
    public static void begin(String key, String action, String target) {
        Test test = OculixReporter.currentTest();
        if (test == null || inStep()) {
            OPEN.get().push(new Open(key, null, null));
            return;
        }
        DEPTH.get()[0]++;
        OPEN.get().push(new Open(key, test, new Step(action, target, Instant.now())));
    }

    /** Closes, as passed, the step opened for {@code key}. */
    public static void end(String key) {
        Open open = pop(key);
        if (open == null || open.step == null) return;
        DEPTH.get()[0]--;
        open.step.end(Instant.now(), Outcome.PASSED);
        open.test.addStep(open.step);
    }

    /**
     * Closes, as failed, the step opened for {@code key}, or the last one
     * opened when {@code key} is null (Selenium 3 reports errors without the
     * method).
     */
    public static void fail(String key, Throwable t) {
        Open open = pop(key);
        if (open == null || open.step == null) return;
        DEPTH.get()[0]--;
        fail(open.step, t);
        open.test.addStep(open.step);
    }

    private static Open pop(String key) {
        java.util.Deque<Open> stack = OPEN.get();
        Open top = stack.peek();
        if (top == null) return null;
        if (key != null && !key.equals(top.key)) return null;
        return stack.pop();
    }

    /** True while a step of the calling thread is running. */
    public static boolean inStep() {
        return DEPTH.get()[0] > 0;
    }

    /**
     * True when a call made now would become a step: a test is open in this
     * thread and no step is running. Lets an aspect skip the work of naming
     * its target when nothing would be recorded.
     */
    public static boolean isRecording() {
        return OculixReporter.currentTest() != null && !inStep();
    }

    private static void fail(Step step, Throwable t) {
        step.end(Instant.now(), t instanceof AssertionError ? Outcome.FAILED : Outcome.ERROR)
            .withError(OculixReporter.messageOf(t), OculixReporter.stackTraceOf(t));
    }
}
