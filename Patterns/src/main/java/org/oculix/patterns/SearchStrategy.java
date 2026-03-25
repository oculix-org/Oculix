package org.oculix.patterns;

import org.sikuli.script.Image;
import org.sikuli.script.Location;
import org.sikuli.script.Match;
import org.sikuli.script.Region;

/**
 * Stratégie de recherche en 4 phases pour un pattern enfant.
 *
 * Phase 1: Si relation parent, cherche le parent avec ROI adaptative
 * Phase 2: Si parent trouvé, cherche l'enfant dans la ROI relative
 * Phase 3: Fallback à la ROI entière du parent
 * Phase 4: Fallback à recherche absolue
 */
public class SearchStrategy {

    private final PatternLibrary library;
    private final ParentChildRelation relation;
    private final PatternMetadata childMeta;

    public SearchStrategy(PatternLibrary library, ParentChildRelation relation,
                          PatternMetadata childMeta) {
        this.library = library;
        this.relation = relation;
        this.childMeta = childMeta;
    }

    /**
     * Pipeline de recherche 4 phases.
     * Retourne Match ou null.
     */
    public Match findImage(Region screen, Image childImage) {
        // Phase 1: Cherche le parent
        PatternMetadata parentMeta = findParentMetadata();
        if (parentMeta == null) {
            // Pas de metadata parent, fallback absolu (Phase 4)
            return screen.exists(childImage);
        }

        Image parentImage = Image.create(parentMeta.getFilepath());
        Match parentMatch = screen.exists(parentImage);

        if (parentMatch == null) {
            // Parent non trouvé
            if (relation.isParentIsRequired()) {
                return null; // Parent requis, on ne fallback pas
            }
            // Phase 4: Fallback absolu
            return screen.exists(childImage);
        }

        // Phase 2: Parent trouvé, cherche l'enfant dans la ROI relative
        Location parentLoc = parentMatch.getTarget();
        Region childROI = relation.buildChildSearchROI(
                parentLoc, parentMatch.w, parentMatch.h);

        Match childMatch = childROI.exists(childImage);
        if (childMatch != null) {
            return childMatch;
        }

        // Phase 3: Fallback à la ROI entière du parent
        Region parentRegion = Region.create(
                parentMatch.x, parentMatch.y, parentMatch.w, parentMatch.h);
        childMatch = parentRegion.exists(childImage);
        if (childMatch != null) {
            return childMatch;
        }

        // Phase 4: Fallback absolu (si parent non requis)
        if (!relation.isParentIsRequired()) {
            return screen.exists(childImage);
        }

        return null;
    }

    private PatternMetadata findParentMetadata() {
        DataStore ds = library.getDataStore();
        // Cherche le parent par ID via une requête sur tous les patterns
        for (PatternMetadata meta : ds.getAllPatterns()) {
            if (meta.getId() == relation.getParentPatternId()) {
                return meta;
            }
        }
        return null;
    }
}
