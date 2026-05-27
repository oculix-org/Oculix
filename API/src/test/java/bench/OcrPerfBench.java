/*
 * OcrPerfBench — measures end-to-end OCR.readWords() time on a screen capture.
 *
 * Usage:
 *   mvn -pl API test -Dtest=OcrPerfBench
 *
 * First run captures the screen (10s grace period for the user to bring the
 * target app to foreground), saves it to .claude/tmp/bench_screen.png, runs
 * 1 warmup + 3 timed OCR passes, and dumps text to .claude/tmp/bench_<label>.txt.
 *
 * Subsequent runs reuse the same PNG so the comparison is on identical input.
 * Use -Dbench.label=modified for the second (post-optimization) run.
 *
 * NOT a regression test — meant to be run manually for perf comparisons.
 * The class name (no "Test" suffix) keeps it out of the Surefire default scan,
 * so it only runs when explicitly invoked via -Dtest=OcrPerfBench.
 */
package bench;

import org.junit.jupiter.api.Test;
import org.sikuli.script.Finder;
import org.sikuli.script.Match;
import org.sikuli.script.OCR;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OcrPerfBench {

    private static final File TMP_DIR = new File(".claude/tmp");
    private static final File SCREEN_PNG = new File(TMP_DIR, "bench_screen.png");
    private static final int CAPTURE_GRACE_MS = 10_000;
    private static final int RUNS = 3;

    @Test
    public void benchScreenOcr() throws Exception {
        TMP_DIR.mkdirs();
        BufferedImage img = loadOrCaptureScreen();
        System.out.printf("[BENCH] image: %dx%d (%d MP)%n",
                img.getWidth(), img.getHeight(),
                (img.getWidth() * img.getHeight()) / 1_000_000);

        // Warmup — pays the cold-init cost on the static TextRecognizer once.
        System.out.println("[BENCH] warmup OCR (throwaway)...");
        long tWarm0 = System.nanoTime();
        OCR.readWords(img);
        long warmMs = (System.nanoTime() - tWarm0) / 1_000_000;
        System.out.println("[BENCH] warmup: " + warmMs + "ms");

        // The word we search for in findText / findWords / findLines.
        // 'review' is reliably present as a standalone token on the bench
        // image (issue #254 has "Report a translation issue" + the "review"
        // label). System property override if you need to bench another.
        String needle = System.getProperty("bench.needle", "review");
        System.out.println("[BENCH] needle=" + needle);

        // --- A) OCR.readWords(image) ----------------------------------
        // Direct, naive path — what `findWord` calls under the hood.
        // Bounds the worst case.
        long[] timesRW = new long[RUNS];
        List<Match> words = null;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            words = OCR.readWords(img);
            timesRW[i] = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("[BENCH] readWords  run %d: %4dms (%d words)%n", i + 1, timesRW[i], words.size());
        }

        // --- B) Finder.findText (line + drill-down) -------------------
        // The path Region.findText actually uses. Generally faster than
        // readWords on large images because pass-1 is at line level (fewer
        // bboxes) and pass-2 is on a tiny sub-image.
        long[] timesFT = new long[RUNS];
        int hitsFT = 0;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            Finder f = new Finder(img);
            f.findText(needle);
            int hits = 0;
            while (f.hasNext()) { f.next(); hits++; }
            timesFT[i] = (System.nanoTime() - t0) / 1_000_000;
            hitsFT = hits;
            System.out.printf("[BENCH] findText   run %d: %4dms (%d hits)%n", i + 1, timesFT[i], hits);
        }

        // --- C) Finder.findWords (direct WORD-level) ------------------
        long[] timesFW = new long[RUNS];
        int hitsFW = 0;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            Finder f = new Finder(img);
            f.findWords(needle);
            int hits = 0;
            while (f.hasNext()) { f.next(); hits++; }
            timesFW[i] = (System.nanoTime() - t0) / 1_000_000;
            hitsFW = hits;
            System.out.printf("[BENCH] findWords  run %d: %4dms (%d hits)%n", i + 1, timesFW[i], hits);
        }

        // --- D) Finder.findLines (direct LINE-level) ------------------
        long[] timesFL = new long[RUNS];
        int hitsFL = 0;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            Finder f = new Finder(img);
            f.findLines(needle);
            int hits = 0;
            while (f.hasNext()) { f.next(); hits++; }
            timesFL[i] = (System.nanoTime() - t0) / 1_000_000;
            hitsFL = hits;
            System.out.printf("[BENCH] findLines  run %d: %4dms (%d hits)%n", i + 1, timesFL[i], hits);
        }

        long minRW = min(timesRW), medRW = median(timesRW), maxRW = max(timesRW);
        long minFT = min(timesFT), medFT = median(timesFT), maxFT = max(timesFT);
        long minFW = min(timesFW), medFW = median(timesFW), maxFW = max(timesFW);
        long minFL = min(timesFL), medFL = median(timesFL), maxFL = max(timesFL);

        System.out.println();
        System.out.println("[BENCH] === SUMMARY ===");
        System.out.printf("[BENCH]   readWords (worst case)   : min=%4dms med=%4dms max=%4dms  | %d words found%n", minRW, medRW, maxRW, words.size());
        System.out.printf("[BENCH]   findText '%s' (line+drill): min=%4dms med=%4dms max=%4dms  | %d hits%n",   needle, minFT, medFT, maxFT, hitsFT);
        System.out.printf("[BENCH]   findWords '%s' (word)     : min=%4dms med=%4dms max=%4dms  | %d hits%n",   needle, minFW, medFW, maxFW, hitsFW);
        System.out.printf("[BENCH]   findLines '%s' (line)     : min=%4dms med=%4dms max=%4dms  | %d hits%n",   needle, minFL, medFL, maxFL, hitsFL);
        System.out.printf("[BENCH]   warmup=%dms%n", warmMs);

        // Local refs used by the file dump below
        long min = minRW, median = medRW, max = maxRW;

        // Dump words to a stable, diffable text file.
        String label = System.getProperty("bench.label", "baseline");
        File out = new File(TMP_DIR, "bench_" + label + ".txt");
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8)) {
            pw.printf("# screen: %dx%d  | warmup=%dms  min=%dms  median=%dms  max=%dms  | %d words%n",
                    img.getWidth(), img.getHeight(), warmMs, min, median, max, words.size());
            pw.println("# format: text [confidence] (x,y,w,h)");
            pw.println("---");
            // Sort by y then x for stable diff between runs.
            List<Match> stable = new ArrayList<>(words);
            stable.sort((a, b) -> {
                if (a.y != b.y) return Integer.compare(a.y, b.y);
                return Integer.compare(a.x, b.x);
            });
            for (Match w : stable) {
                pw.printf("%-50s [%.2f] (%d,%d,%d,%d)%n",
                        truncate(w.getText(), 50), w.getScore(),
                        w.x, w.y, w.w, w.h);
            }
        }
        System.out.println("[BENCH] results saved to " + out.getAbsolutePath());
    }

    private static BufferedImage loadOrCaptureScreen() throws Exception {
        // 1. Classpath resource — committed image used by CI (GitHub Actions, no display).
        InputStream classpathImg = OcrPerfBench.class.getResourceAsStream("/bench/bench_screen.png");
        if (classpathImg != null) {
            try (InputStream is = classpathImg) {
                System.out.println("[BENCH] using committed bench image from classpath: /bench/bench_screen.png");
                return ImageIO.read(is);
            }
        }
        // 2. Local cache — previous run kept its capture, reuse for stable comparison.
        if (SCREEN_PNG.exists()) {
            System.out.println("[BENCH] reusing local screen capture: " + SCREEN_PNG.getAbsolutePath());
            System.out.println("[BENCH] (delete this file if you want a fresh capture)");
            return ImageIO.read(SCREEN_PNG);
        }
        // 3. Live capture — first local run, gives user 10s to bring target app to fg.
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                "OcrPerfBench: no committed image at /bench/bench_screen.png, no local cache at "
                    + SCREEN_PNG + ", and JVM is headless. Cannot capture screen.");
        }
        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  TU AS " + (CAPTURE_GRACE_MS / 1000) + " SECONDES POUR METTRE TON APP AU PREMIER PLAN   │");
        System.out.println("│  Capture full-screen dans " + (CAPTURE_GRACE_MS / 1000) + "s...                          │");
        System.out.println("└─────────────────────────────────────────────────────────────┘");
        System.out.println();
        for (int s = CAPTURE_GRACE_MS / 1000; s > 0; s--) {
            System.out.print("  " + s + "...  ");
            Thread.sleep(1000);
        }
        System.out.println();
        System.out.println("[BENCH] capturing screen now");
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = new Robot().createScreenCapture(screen);
        ImageIO.write(img, "png", SCREEN_PNG);
        System.out.println("[BENCH] saved: " + SCREEN_PNG.getAbsolutePath());
        return img;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static long min(long[] a)    { long m = Long.MAX_VALUE; for (long v : a) if (v < m) m = v; return m; }
    private static long max(long[] a)    { long m = Long.MIN_VALUE; for (long v : a) if (v > m) m = v; return m; }
    private static long median(long[] a) { long[] s = a.clone(); Arrays.sort(s); return s[s.length / 2]; }
}
