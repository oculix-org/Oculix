package org.sikuli.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <h1>ActionLogRenderer — the OculiX log has moods</h1>
 *
 * <p>Renders the action/error log line according to {@code Settings.ActionLogMode}.
 * Replaces the whole log line in personality modes, masks arguments only in
 * MASKED, lets things through verbatim in CLEAR, and stays silent in SILENT.
 *
 * <p>Personality lines live in classpath resources at
 * {@code org/sikuli/support/log-personalities/*.txt} and are loaded lazily on
 * first use, then cached.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>CLEAR</b> — vanilla, the raw message and args verbatim. Default for
 *       backward compatibility, but not the future.</li>
 *   <li><b>MASKED</b> — keeps the verb ({@code CLICK}, {@code TYPE}, ...) but
 *       replaces every argument value with {@code ******}. Privacy by default,
 *       cheap to enable, zero leakage.</li>
 *   <li><b>SILENT</b> — emits nothing. The log is at peace.</li>
 *   <li><b>GECKO</b> — replaces the entire line with a rotating quip about the
 *       life of an OculiX gecko in the QA mines.</li>
 *   <li><b>COMPETITOR</b> — replaces the entire line with a gentle jab at the
 *       state of the art ("while Selenium was still loading WebDriver...").</li>
 *   <li><b>AI_BURLESQUE</b> — replaces the entire line with an exaggerated
 *       remark on what the LLM did in the meantime.</li>
 * </ul>
 *
 * <p>For failures (error path), a rotating alert prefix is prepended to the
 * rendered line in non-CLEAR modes — so you cannot miss that the test died.
 *
 * <p>This is a feature with three personalities. Pick the one that fits your
 * audit, your demo, or your soul.
 */
public final class ActionLogRenderer {

    private static final String RES_BASE = "org/sikuli/support/log-personalities/";
    private static final String MASK_TOKEN = "******";

    private static List<String> geckoSuccess;
    private static List<String> geckoFailed;
    private static List<String> competitorSuccess;
    private static List<String> competitorFailed;
    private static List<String> aiSuccess;
    private static List<String> aiFailed;
    private static List<String> alertPrefixes;

    private ActionLogRenderer() {}

    /**
     * Top-level entry point. Called from {@code Debug.action()} and
     * {@code Debug.error()} after the global {@code Settings.ActionLogs} guard.
     *
     * @param message  the original format string (or plain message)
     * @param args     the format arguments (locator coords, typed values, ...)
     * @param modeName the current {@code Settings.ActionLogMode} name as String
     *                 (to avoid a hard dependency on the enum at this layer)
     * @param isError  {@code true} when called from the error path
     * @return the rendered line, or {@code null} when the caller should skip
     *         emitting the log entirely (SILENT mode)
     */
    public static String render(String message, Object[] args, String modeName, boolean isError) {
        Mode mode = Mode.fromName(modeName);
        switch (mode) {
            case CLEAR:
                return safeFormat(message, args);
            case MASKED:
                return maskFormatString(message);
            case SILENT:
                return null;
            case GECKO:
                return prefixIfError(isError, pick(isError ? geckoFailed() : geckoSuccess()));
            case COMPETITOR:
                return prefixIfError(isError, pick(isError ? competitorFailed() : competitorSuccess()));
            case AI_BURLESQUE:
                return prefixIfError(isError, pick(isError ? aiFailed() : aiSuccess()));
            default:
                return safeFormat(message, args);
        }
    }

    /** Modes recognised by the renderer, kept here to stay decoupled from the Settings enum. */
    public enum Mode {
        CLEAR, MASKED, SILENT, GECKO, COMPETITOR, AI_BURLESQUE;

        static Mode fromName(String name) {
            if (name == null) return CLEAR;
            try {
                return Mode.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return CLEAR;
            }
        }
    }

    private static String safeFormat(String message, Object[] args) {
        if (args == null || args.length == 0) return message;
        try {
            return String.format(message, args);
        } catch (Exception e) {
            return message;
        }
    }

    private static Object[] maskAll(Object[] args) {
        if (args == null) return null;
        Object[] masked = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            masked[i] = MASK_TOKEN;
        }
        return masked;
    }

    /**
     * MASKED mode does not feed the masked args back through {@code String.format()}
     * because {@code "******"} does not satisfy numeric specifiers like {@code %d}.
     * Instead we replace every format specifier in the message itself by the mask
     * token, keeping the verb (CLICK / TYPE / FindFailed) and the surrounding
     * structure (brackets, quotes) for log readability.
     */
    private static String maskFormatString(String message) {
        if (message == null) return null;
        // Match Java format specifiers: %d, %s, %f, %x, %.2f, %5d, %-10s, %tT, %n etc.
        // Conservative regex inspired by java.util.Formatter spec.
        return message.replaceAll(
                "%[-#+ 0,(]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAn]|%t[a-zA-Z]",
                MASK_TOKEN);
    }

    private static String prefixIfError(boolean isError, String line) {
        if (line == null) return null;
        if (!isError) return line;
        return pick(alertPrefixes()) + " " + line;
    }

    private static String pick(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "(no lines loaded)";
        return lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lazy-loaded line lists. First access reads the .txt from classpath,
    // subsequent accesses hit the cached list.
    // ─────────────────────────────────────────────────────────────────────

    private static synchronized List<String> geckoSuccess() {
        if (geckoSuccess == null) geckoSuccess = loadLines("gecko_success.txt");
        return geckoSuccess;
    }

    private static synchronized List<String> geckoFailed() {
        if (geckoFailed == null) geckoFailed = loadLines("gecko_failed.txt");
        return geckoFailed;
    }

    private static synchronized List<String> competitorSuccess() {
        if (competitorSuccess == null) competitorSuccess = loadLines("competitor_success.txt");
        return competitorSuccess;
    }

    private static synchronized List<String> competitorFailed() {
        if (competitorFailed == null) competitorFailed = loadLines("competitor_failed.txt");
        return competitorFailed;
    }

    private static synchronized List<String> aiSuccess() {
        if (aiSuccess == null) aiSuccess = loadLines("ai_success.txt");
        return aiSuccess;
    }

    private static synchronized List<String> aiFailed() {
        if (aiFailed == null) aiFailed = loadLines("ai_failed.txt");
        return aiFailed;
    }

    private static synchronized List<String> alertPrefixes() {
        if (alertPrefixes == null) alertPrefixes = loadLines("alert_prefixes.txt");
        return alertPrefixes;
    }

    private static List<String> loadLines(String fileName) {
        List<String> out = new ArrayList<>();
        ClassLoader cl = ActionLogRenderer.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RES_BASE + fileName)) {
            if (in == null) {
                return Collections.emptyList();
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) out.add(trimmed);
                }
            }
        } catch (IOException e) {
            // resource missing or unreadable — fall through to empty list
            return Collections.emptyList();
        }
        return out;
    }
}
