/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import org.json.JSONObject;
import org.sikuli.script.Match;
import org.sikuli.script.Region;
import org.sikuli.support.devices.IScreen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Persistent per-text last-seen cache for findText / existsText / click(text) /
 * any OCR-driven lookup. Mirrors the image lastSeen mechanism (issue #353)
 * but for the text dimension.
 *
 * <p>Catalog file: {@code tesseract-lastseen.json} written in the JVM working
 * directory ({@code user.dir}). This colocates the cache with the user's
 * test project so it can be committed, shared across machines, and survives
 * across runs. Out of {@code ~/.oculix/} on purpose: it is a project asset,
 * not a per-user setting.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Entries are keyed by <strong>(text, search-region context)</strong>.
 *       The same text can be sought in different sub-regions
 *       ({@code notepad.findText("Save")} vs {@code chrome.findText("Save")});
 *       each pair gets its own independent cache slot so the two never
 *       overwrite each other.</li>
 *   <li>On first OCR lookup for a given (text, context), the full search
 *       region is scanned. On hit, the bounding box of the found
 *       {@link Match} is persisted under that pair as key.</li>
 *   <li>On subsequent lookups for the same pair, only the cached bounding
 *       box is rescanned — typically &lt;50 ms vs &gt;1 s for a full screen.</li>
 *   <li>If the cached region no longer contains the text (UI changed,
 *       element moved), the caller falls back to a full scan; the stale
 *       entry is invalidated.</li>
 *   <li>Padding is zero on purpose. The user asked for deterministic
 *       behaviour: the cached bounding box is exact. Any UI shift &gt; 0 px
 *       triggers a fallback.</li>
 * </ul>
 *
 * <p>The catalog is screen-resolution-aware: entries captured on a 1920x1080
 * monitor are ignored when the current screen is a different size, so the
 * coordinates never point off-screen on a different setup.
 *
 * <p>All reads and writes are guarded by a single intra-process lock. The
 * write path is atomic ({@code write tmp + ATOMIC_MOVE}) so a crash during
 * persistence never leaves a corrupt JSON. Any IO or parse failure is
 * swallowed and the OCR call proceeds as if no cache existed — the cache
 * layer must never break the user's automation.
 */
public final class TesseractLastSeen {

    private static final String FILENAME = "tesseract-lastseen.json";
    private static final int FORMAT_VERSION = 1;
    private static final Object LOCK = new Object();

    private TesseractLastSeen() {
    }

    /**
     * Returns the cached region of interest for the given text inside the
     * given search region, or {@code null} if no usable cache entry exists.
     *
     * <p>An entry is considered usable when:
     * <ul>
     *   <li>it exists in the catalog;</li>
     *   <li>its captured screen resolution matches the current screen;</li>
     *   <li>its bounding box is fully contained inside {@code searchRegion}
     *       (a cache entry from a previous full-screen scan must not be
     *       used when the current call asks for a sub-region).</li>
     * </ul>
     *
     * @param searchRegion the region in which the OCR search is about to run
     * @param text         the text being searched for
     * @return the cached ROI or {@code null}
     */
    public static Region getRegion(Region searchRegion, String text) {
        if (text == null || text.isEmpty()) return null;
        String ctxKey = contextKey(searchRegion);
        synchronized (LOCK) {
            JSONObject root = loadOrEmpty();
            JSONObject entries = root.optJSONObject("entries");
            if (entries == null) return null;
            JSONObject textBucket = entries.optJSONObject(text);
            if (textBucket == null) return null;
            JSONObject entry = textBucket.optJSONObject(ctxKey);
            if (entry == null) return null;

            IScreen screen = searchRegion.getScreen();
            if (screen != null) {
                int curW = screen.getBounds().width;
                int curH = screen.getBounds().height;
                if (entry.optInt("screenW") != curW || entry.optInt("screenH") != curH) {
                    return null;
                }
            }

            int x = entry.getInt("x");
            int y = entry.getInt("y");
            int w = entry.getInt("w");
            int h = entry.getInt("h");

            if (x < searchRegion.x
                || y < searchRegion.y
                || x + w > searchRegion.x + searchRegion.w
                || y + h > searchRegion.y + searchRegion.h) {
                return null;
            }

            return new Region(x, y, w, h, searchRegion.getScreen());
        }
    }

    /**
     * Records a successful text match under the given text as key. Overwrites
     * any previous entry for that text.
     *
     * @param searchRegion the region in which the search was performed (used
     *                     to capture the current screen resolution)
     * @param text         the text that was looked for
     * @param match        the match that was found
     */
    public static void put(Region searchRegion, String text, Match match) {
        if (text == null || text.isEmpty() || match == null) return;
        String ctxKey = contextKey(searchRegion);
        synchronized (LOCK) {
            JSONObject root = loadOrEmpty();
            JSONObject entries = root.optJSONObject("entries");
            if (entries == null) {
                entries = new JSONObject();
                root.put("entries", entries);
            }
            JSONObject textBucket = entries.optJSONObject(text);
            if (textBucket == null) {
                textBucket = new JSONObject();
                entries.put(text, textBucket);
            }
            int hits = 1;
            JSONObject existing = textBucket.optJSONObject(ctxKey);
            if (existing != null) {
                hits = existing.optInt("hits", 0) + 1;
            }
            JSONObject entry = new JSONObject();
            entry.put("x", match.x);
            entry.put("y", match.y);
            entry.put("w", match.w);
            entry.put("h", match.h);
            IScreen screen = searchRegion.getScreen();
            if (screen != null) {
                entry.put("screenW", screen.getBounds().width);
                entry.put("screenH", screen.getBounds().height);
            }
            entry.put("lastSeen", Instant.now().toString());
            entry.put("hits", hits);
            textBucket.put(ctxKey, entry);
            root.put("version", FORMAT_VERSION);
            save(root);
        }
    }

    /**
     * Removes the cache entry for the given (text, context) pair. Called when
     * a cached ROI lookup misses, so the next call goes straight to the
     * full-region scan. The text bucket itself is dropped when its last
     * context entry is removed.
     */
    public static void invalidate(Region searchRegion, String text) {
        if (text == null || text.isEmpty()) return;
        String ctxKey = contextKey(searchRegion);
        synchronized (LOCK) {
            JSONObject root = loadOrEmpty();
            JSONObject entries = root.optJSONObject("entries");
            if (entries == null) return;
            JSONObject textBucket = entries.optJSONObject(text);
            if (textBucket == null) return;
            if (!textBucket.has(ctxKey)) return;
            textBucket.remove(ctxKey);
            if (textBucket.isEmpty()) entries.remove(text);
            save(root);
        }
    }

    /**
     * Absolute path of the catalog file, for diagnostics / tests.
     */
    public static File catalogFile() {
        return new File(System.getProperty("user.dir", "."), FILENAME);
    }

    // ── internals ──────────────────────────────────────────────────────────

    /**
     * Builds the search-context key from a Region's absolute bounds. Two
     * Regions with identical bounds produce identical keys; otherwise they
     * are distinct contexts and get independent cache slots.
     */
    private static String contextKey(Region r) {
        return r.x + "," + r.y + "," + r.w + "," + r.h;
    }

    private static JSONObject loadOrEmpty() {
        File f = catalogFile();
        if (!f.exists()) {
            return new JSONObject()
                .put("version", FORMAT_VERSION)
                .put("entries", new JSONObject());
        }
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            if (!root.has("entries")) root.put("entries", new JSONObject());
            return root;
        } catch (Exception ignored) {
            return new JSONObject()
                .put("version", FORMAT_VERSION)
                .put("entries", new JSONObject());
        }
    }

    private static void save(JSONObject root) {
        File f = catalogFile();
        File parent = f.getParentFile();
        if (parent == null) parent = new File(".");
        File tmp = new File(parent, f.getName() + ".tmp");
        Path tmpPath = tmp.toPath();
        Path finalPath = f.toPath();
        try {
            Files.write(tmpPath, root.toString(2).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmpPath, finalPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // Cache write failure must never crash the OCR call.
        }
    }
}
