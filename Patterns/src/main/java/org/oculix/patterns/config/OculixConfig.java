package org.oculix.patterns.config;

import java.io.File;

/**
 * Configuration des chemins OculiX.
 * Gère les chemins AppData selon l'OS.
 */
public class OculixConfig {

    private OculixConfig() {
    }

    /**
     * Retourne le dossier AppData OculiX selon l'OS:
     * - Windows: C:\Users\{user}\AppData\Local\SikulixAppData\OculiX
     * - Linux/Mac: ~/.SikulixAppData/OculiX/
     */
    public static File getOculixAppData() {
        String os = System.getProperty("os.name", "").toLowerCase();
        File baseDir;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null || localAppData.isEmpty()) {
                localAppData = System.getProperty("user.home") + File.separator
                        + "AppData" + File.separator + "Local";
            }
            baseDir = new File(localAppData, "SikulixAppData" + File.separator + "OculiX");
        } else {
            baseDir = new File(System.getProperty("user.home"), ".SikulixAppData" + File.separator + "OculiX");
        }
        return baseDir;
    }

    /**
     * Retourne le chemin de la base SQLite:
     * {OculixAppData}/db/oculix.db
     */
    public static File getDatabasePath() {
        return new File(getOculixAppData(), "db" + File.separator + "oculix.db");
    }
}
