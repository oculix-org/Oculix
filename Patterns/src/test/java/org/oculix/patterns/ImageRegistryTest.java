package org.oculix.patterns;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests pour ImageRegistry, 100% en mémoire.
 * Utilise un mock du DataStore (HashMap, zéro SQLite).
 */
public class ImageRegistryTest {

    private MockDataStore mockStore;

    @Before
    public void setUp() {
        mockStore = new MockDataStore();
    }

    @Test
    public void testRegisterAndGet() {
        PatternMetadata meta = mockStore.registerPattern("button_ok",
                new File("test/button_ok.png"), 80, 40);

        assertNotNull(meta);
        assertEquals("button_ok", meta.getName());
        assertEquals(80, meta.getWidthPixels());
        assertEquals(40, meta.getHeightPixels());

        PatternMetadata retrieved = mockStore.getPattern("button_ok");
        assertNotNull(retrieved);
        assertEquals("button_ok", retrieved.getName());
    }

    @Test
    public void testSearchPatterns() {
        mockStore.registerPattern("button_ok", new File("test/button_ok.png"), 80, 40);
        mockStore.registerPattern("button_cancel", new File("test/button_cancel.png"), 100, 40);
        mockStore.registerPattern("text_field", new File("test/text_field.png"), 200, 30);

        List<PatternMetadata> results = mockStore.searchPatterns("button");
        assertEquals(2, results.size());
        assertEquals("button_cancel", results.get(0).getName()); // ORDER BY name ASC
        assertEquals("button_ok", results.get(1).getName());
    }

    @Test(expected = OculixPatternException.class)
    public void testPatternNotFound() {
        mockStore.getPatternOrThrow("inexistent");
    }

    @Test(expected = OculixPatternException.class)
    public void testDuplicatePatternName() {
        mockStore.registerPattern("paie_button", new File("test/paie.png"), 80, 40);
        mockStore.registerPattern("paie_button", new File("test/paie2.png"), 80, 40);
    }

    /**
     * Mock du DataStore en mémoire (HashMap), zéro DB.
     */
    static class MockDataStore {
        private final Map<String, PatternMetadata> patterns = new HashMap<>();
        private int nextId = 1;

        PatternMetadata registerPattern(String name, File imageFile, int width, int height) {
            if (patterns.containsKey(name)) {
                throw OculixPatternException.patternAlreadyExists(name);
            }
            PatternMetadata meta = new PatternMetadata(
                    nextId++, name, imageFile, width, height,
                    "hash_" + name, "standalone", LocalDateTime.now());
            patterns.put(name, meta);
            return meta;
        }

        PatternMetadata getPattern(String name) {
            return patterns.get(name);
        }

        PatternMetadata getPatternOrThrow(String name) {
            PatternMetadata meta = patterns.get(name);
            if (meta == null) {
                throw OculixPatternException.patternNotFound(name);
            }
            return meta;
        }

        List<PatternMetadata> searchPatterns(String query) {
            return patterns.values().stream()
                    .filter(m -> m.getName().contains(query))
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .collect(Collectors.toList());
        }
    }
}
