package org.oculix.patterns;

/**
 * Exception pour le module Patterns OculiX.
 * Messages explicites pour chaque cas d'erreur.
 */
public class OculixPatternException extends RuntimeException {

    public OculixPatternException(String message) {
        super(message);
    }

    public OculixPatternException(String message, Throwable cause) {
        super(message, cause);
    }

    public static OculixPatternException patternNotFound(String name) {
        return new OculixPatternException("Pattern '" + name + "' not found");
    }

    public static OculixPatternException patternAlreadyExists(String name) {
        return new OculixPatternException("Pattern '" + name + "' already exists");
    }

    public static OculixPatternException parentNotFound(String parentName) {
        return new OculixPatternException("Parent pattern '" + parentName + "' not found");
    }

    public static OculixPatternException databaseInitFailed(Throwable cause) {
        return new OculixPatternException("Database initialization failed", cause);
    }
}
