package org.oculix.patterns;

import org.sikuli.script.Image;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre central des patterns avec cache en mémoire.
 * Façade statique pour l'accès aux patterns.
 * Appelé UNE FOIS au démarrage via initialize().
 */
public class ImageRegistry {

    private static DataStore dataStore;
    private static Map<String, PatternMetadata> cache = new HashMap<>();

    private ImageRegistry() {
    }

    /**
     * Initialise le registre: lance DataStore, charge patterns en mémoire.
     * Appelé UNE FOIS au démarrage.
     */
    public static void initialize(String projectId) {
        dataStore = new DataStore(projectId);
        dataStore.initializeDatabase();
        reloadCache();
    }

    /**
     * Enregistre un nouveau pattern.
     * Sauvegarde en DB puis recharge le cache.
     */
    public static PatternMetadata register(String name, File imageFile) {
        checkInitialized();
        PatternMetadata meta = dataStore.registerPattern(name, imageFile);
        reloadCache();
        return meta;
    }

    /**
     * Retourne l'Image SikuliX correspondant au pattern.
     * Cherche dans le cache (HashMap).
     * Lance OculixPatternException si introuvable.
     */
    public static Image get(String patternName) {
        checkInitialized();
        PatternMetadata meta = cache.get(patternName);
        if (meta == null) {
            throw OculixPatternException.patternNotFound(patternName);
        }
        return Image.create(meta.getFilepath());
    }

    /**
     * Recherche des patterns par nom (LIKE %query%).
     */
    public static List<PatternMetadata> search(String query) {
        checkInitialized();
        return dataStore.searchPatterns(query);
    }

    /**
     * Retourne tous les patterns (ORDER BY name).
     */
    public static List<PatternMetadata> getAllPatterns() {
        checkInitialized();
        return dataStore.getAllPatterns();
    }

    /**
     * Recharge le cache depuis la DB.
     */
    public static void reload() {
        checkInitialized();
        reloadCache();
    }

    /**
     * Retourne le DataStore sous-jacent (usage interne).
     */
    static DataStore getDataStore() {
        return dataStore;
    }

    /**
     * Retourne le metadata d'un pattern depuis le cache.
     */
    static PatternMetadata getMetadata(String patternName) {
        return cache.get(patternName);
    }

    private static void reloadCache() {
        cache.clear();
        List<PatternMetadata> all = dataStore.getAllPatterns();
        for (PatternMetadata meta : all) {
            cache.put(meta.getName(), meta);
        }
    }

    private static void checkInitialized() {
        if (dataStore == null) {
            throw new OculixPatternException("ImageRegistry not initialized. Call initialize() first.");
        }
    }
}
