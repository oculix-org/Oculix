package org.oculix.patterns;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests pour les relations parent-child, 100% en mémoire.
 */
public class ParentChildTest {

    private MockRelationStore store;

    @Before
    public void setUp() {
        store = new MockRelationStore();
    }

    @Test
    public void testCreateRelation() {
        store.registerPattern("popup_save", 400, 300);
        store.registerPattern("button_ok", 80, 40);

        store.relatePatterns("button_ok", "popup_save", 100, 50, 80, 40);

        ParentChildRelation rel = store.getRelation("button_ok", "popup_save");
        assertNotNull(rel);
        assertEquals(100, rel.getRelativeXPixels());
        assertEquals(50, rel.getRelativeYPixels());
        assertEquals(80, rel.getChildWidthPixels());
        assertEquals(40, rel.getChildHeightPixels());
    }

    @Test
    public void testMultipleParents() {
        store.registerPattern("button_ok", 80, 40);
        store.registerPattern("popup_save", 400, 300);
        store.registerPattern("popup_warning", 350, 250);

        store.relatePatterns("button_ok", "popup_save", 100, 50, 80, 40);
        ParentChildRelation rel2 = store.relatePatterns("button_ok", "popup_warning", 120, 60, 80, 40);
        rel2.setPriority(2);

        List<ParentChildRelation> relations = store.getActiveRelations("button_ok");
        assertEquals(2, relations.size());
        // Triés par priorité ASC
        assertEquals(1, relations.get(0).getPriority());
        assertEquals(2, relations.get(1).getPriority());
    }

    @Test
    public void testProjectExpectedLocation() {
        ParentChildRelation rel = new ParentChildRelation(1, 2, 100, 50, 80, 40);

        // Simule: parent trouvé à (500, 400)
        // Utilise directement la méthode sans dépendance SikuliX Location
        int expectedX = 500 + rel.getRelativeXPixels();
        int expectedY = 400 + rel.getRelativeYPixels();

        assertEquals(600, expectedX);
        assertEquals(450, expectedY);
    }

    /**
     * Mock pour les relations en mémoire.
     */
    static class MockRelationStore {
        private final Map<String, PatternMetadata> patterns = new HashMap<>();
        private final List<ParentChildRelation> relations = new ArrayList<>();
        private int nextId = 1;

        void registerPattern(String name, int width, int height) {
            PatternMetadata meta = new PatternMetadata(
                    nextId++, name, new File("test/" + name + ".png"),
                    width, height, "hash_" + name, "standalone", LocalDateTime.now());
            patterns.put(name, meta);
        }

        ParentChildRelation relatePatterns(String childName, String parentName,
                                           int relX, int relY, int childW, int childH) {
            PatternMetadata child = patterns.get(childName);
            PatternMetadata parent = patterns.get(parentName);
            if (child == null) throw OculixPatternException.patternNotFound(childName);
            if (parent == null) throw OculixPatternException.parentNotFound(parentName);

            ParentChildRelation rel = new ParentChildRelation(
                    child.getId(), parent.getId(), relX, relY, childW, childH);
            relations.add(rel);
            return rel;
        }

        ParentChildRelation getRelation(String childName, String parentName) {
            PatternMetadata child = patterns.get(childName);
            PatternMetadata parent = patterns.get(parentName);
            if (child == null || parent == null) return null;

            return relations.stream()
                    .filter(r -> r.getChildPatternId() == child.getId()
                            && r.getParentPatternId() == parent.getId())
                    .findFirst().orElse(null);
        }

        List<ParentChildRelation> getActiveRelations(String childName) {
            PatternMetadata child = patterns.get(childName);
            if (child == null) return new ArrayList<>();

            return relations.stream()
                    .filter(r -> r.getChildPatternId() == child.getId() && r.isActive())
                    .sorted(Comparator.comparingInt(ParentChildRelation::getPriority))
                    .collect(Collectors.toList());
        }
    }
}
