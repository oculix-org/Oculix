package org.oculix.patterns;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Metadata d'un pattern visuel stocké en base.
 */
public class PatternMetadata {

    private final int id;
    private final String name;
    private final File filepath;
    private final int widthPixels;
    private final int heightPixels;
    private final String perceptualHash;
    private final String patternType; // standalone, parent, child
    private final LocalDateTime createdAt;

    public PatternMetadata(int id, String name, File filepath,
                           int widthPixels, int heightPixels,
                           String perceptualHash, String patternType,
                           LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.filepath = filepath;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.perceptualHash = perceptualHash;
        this.patternType = patternType;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public File getFilepath() {
        return filepath;
    }

    public int getWidthPixels() {
        return widthPixels;
    }

    public int getHeightPixels() {
        return heightPixels;
    }

    public String getPerceptualHash() {
        return perceptualHash;
    }

    public String getPatternType() {
        return patternType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
