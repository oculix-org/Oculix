package org.oculix.patterns;

import org.sikuli.script.FindFailed;
import org.sikuli.script.Image;
import org.sikuli.script.Match;
import org.sikuli.script.Region;

import java.util.List;

/**
 * Gestion de la hiérarchie parent-child des patterns.
 * Utilise SearchStrategy pour la recherche en 4 phases.
 */
public class PatternLibrary {

    private final DataStore dataStore;

    public PatternLibrary(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * Crée une relation parent-child entre deux patterns.
     */
    public void relatePatterns(String childName, String parentName,
                               int relX, int relY, int childW, int childH) {
        dataStore.relatePatterns(childName, parentName, relX, relY, childW, childH);
    }

    /**
     * Recherche un pattern avec sa hiérarchie parent-child.
     *
     * Si relation parent-child existe:
     *   1. Cherche le parent
     *   2. Si parent trouvé: cherche l'enfant dans la ROI relative
     *   3. Fallback: cherche l'enfant dans tout le parent
     * Si pas de parent:
     *   Cherche l'image avec ROI absolue
     *
     * @return Match trouvé
     * @throws FindFailed si le pattern n'est pas trouvé
     */
    public Match findWithHierarchy(String patternName, Region screen) throws FindFailed {
        PatternMetadata childMeta = dataStore.getPattern(patternName);
        if (childMeta == null) {
            throw OculixPatternException.patternNotFound(patternName);
        }

        Image childImage = Image.create(childMeta.getFilepath());

        // Récupère les relations actives
        List<ParentChildRelation> relations = dataStore.getActiveRelations(patternName);

        if (relations.isEmpty()) {
            // Pas de parent: recherche directe dans le screen
            Match result = screen.exists(childImage);
            if (result == null) {
                throw new FindFailed("Pattern '" + patternName + "' not found on screen");
            }
            return result;
        }

        // Essaie chaque relation parent-child par priorité
        for (ParentChildRelation relation : relations) {
            SearchStrategy strategy = new SearchStrategy(this, relation, childMeta);
            Match result = strategy.findImage(screen, childImage);
            if (result != null) {
                return result;
            }
        }

        // Si parentIsRequired sur toutes les relations, on échoue
        boolean allRequired = relations.stream().allMatch(ParentChildRelation::isParentIsRequired);
        if (allRequired) {
            throw new FindFailed("Pattern '" + patternName + "' not found (all parent relations required)");
        }

        // Fallback absolu: cherche dans tout le screen
        Match result = screen.exists(childImage);
        if (result == null) {
            throw new FindFailed("Pattern '" + patternName + "' not found on screen (fallback)");
        }
        return result;
    }

    DataStore getDataStore() {
        return dataStore;
    }
}
