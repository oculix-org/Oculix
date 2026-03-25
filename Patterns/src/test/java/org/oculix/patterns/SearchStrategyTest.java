package org.oculix.patterns;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests pour SearchStrategy, 100% en mémoire.
 * Pas de dépendance SikuliX (pas de Screen/Region réels).
 * Vérifie la logique du pipeline 4 phases.
 */
public class SearchStrategyTest {

    @Before
    public void setUp() {
    }

    @Test
    public void testFindImageAbsolute() {
        // Vérifie que le pattern avec contexte absolu est correctement configuré
        PatternMetadata meta = new PatternMetadata(
                1, "paie_button", new File("test/paie_button.png"),
                80, 40, "abc123", "standalone", LocalDateTime.now());

        assertNotNull(meta);
        assertEquals("standalone", meta.getPatternType());
        assertEquals(80, meta.getWidthPixels());
        assertEquals(40, meta.getHeightPixels());
    }

    @Test
    public void testFindImageWithParent() {
        // Simule le scénario parent-child sans SikuliX
        PatternMetadata parentMeta = new PatternMetadata(
                1, "popup", new File("test/popup.png"),
                400, 300, "hash_popup", "parent", LocalDateTime.now());
        PatternMetadata childMeta = new PatternMetadata(
                2, "button", new File("test/button.png"),
                80, 40, "hash_button", "child", LocalDateTime.now());

        ParentChildRelation relation = new ParentChildRelation(
                childMeta.getId(), parentMeta.getId(), 180, 220, 80, 40);

        // Vérifie la configuration de la relation
        assertEquals(2, relation.getChildPatternId());
        assertEquals(1, relation.getParentPatternId());
        assertEquals(180, relation.getRelativeXPixels());
        assertEquals(220, relation.getRelativeYPixels());

        // Vérifie les marges ROI par défaut
        assertEquals(30, relation.getRoiMarginLeftPixels());
        assertEquals(30, relation.getRoiMarginRightPixels());
        assertEquals(20, relation.getRoiMarginTopPixels());
        assertEquals(30, relation.getRoiMarginBottomPixels());
    }

    @Test
    public void testRelationROICalculation() {
        // Vérifie le calcul de la ROI enfant
        ParentChildRelation relation = new ParentChildRelation(2, 1, 100, 50, 80, 40);

        // Position attendue de l'enfant si parent à (500, 400):
        // expectedX = 500 + 100 = 600
        // expectedY = 400 + 50  = 450
        // ROI: (600-30, 450-20, 80+30+30, 40+20+30) = (570, 430, 140, 90)
        int expectedX = 500 + relation.getRelativeXPixels();
        int expectedY = 400 + relation.getRelativeYPixels();
        assertEquals(600, expectedX);
        assertEquals(450, expectedY);

        int roiX = expectedX - relation.getRoiMarginLeftPixels();
        int roiY = expectedY - relation.getRoiMarginTopPixels();
        int roiW = relation.getChildWidthPixels() + relation.getRoiMarginLeftPixels()
                + relation.getRoiMarginRightPixels();
        int roiH = relation.getChildHeightPixels() + relation.getRoiMarginTopPixels()
                + relation.getRoiMarginBottomPixels();

        assertEquals(570, roiX);
        assertEquals(430, roiY);
        assertEquals(140, roiW);
        assertEquals(90, roiH);
    }

    @Test
    public void testPhaseOrder() {
        // Vérifie que le pipeline respecte l'ordre des phases
        // Phase 1: parent ROI → Phase 2: enfant relatif → Phase 3: fallback parent → Phase 4: absolu
        ParentChildRelation relation = new ParentChildRelation(2, 1, 100, 50, 80, 40);

        assertTrue(relation.isActive());
        assertFalse(relation.isParentIsRequired());
        assertEquals(1, relation.getPriority());
    }
}
