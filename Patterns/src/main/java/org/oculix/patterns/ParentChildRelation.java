package org.oculix.patterns;

import org.sikuli.script.Location;
import org.sikuli.script.Region;

/**
 * Relation parent-child entre deux patterns.
 * Stocke la position relative de l'enfant dans le parent
 * et les marges ROI pour la recherche.
 */
public class ParentChildRelation {

    private final int childPatternId;
    private final int parentPatternId;
    private final int relativeXPixels;
    private final int relativeYPixels;
    private final int childWidthPixels;
    private final int childHeightPixels;
    private int roiMarginLeftPixels;
    private int roiMarginRightPixels;
    private int roiMarginTopPixels;
    private int roiMarginBottomPixels;
    private int priority;
    private boolean isActive;
    private boolean parentIsRequired;

    public ParentChildRelation(int childPatternId, int parentPatternId,
                               int relativeXPixels, int relativeYPixels,
                               int childWidthPixels, int childHeightPixels) {
        this.childPatternId = childPatternId;
        this.parentPatternId = parentPatternId;
        this.relativeXPixels = relativeXPixels;
        this.relativeYPixels = relativeYPixels;
        this.childWidthPixels = childWidthPixels;
        this.childHeightPixels = childHeightPixels;
        this.roiMarginLeftPixels = 30;
        this.roiMarginRightPixels = 30;
        this.roiMarginTopPixels = 20;
        this.roiMarginBottomPixels = 30;
        this.priority = 1;
        this.isActive = true;
        this.parentIsRequired = false;
    }

    /**
     * Projette la position attendue de l'enfant à partir de la position du parent.
     */
    public Location projectChildExpectedLocation(Location parentLocation) {
        return new Location(parentLocation.x + relativeXPixels,
                parentLocation.y + relativeYPixels);
    }

    /**
     * Construit la région de recherche (ROI) pour l'enfant DANS le parent.
     * La ROI est centrée sur la position relative attendue + marges.
     */
    public Region buildChildSearchROI(Location parentLocation, int parentW, int parentH) {
        int expectedX = parentLocation.x + relativeXPixels;
        int expectedY = parentLocation.y + relativeYPixels;

        int roiX = expectedX - roiMarginLeftPixels;
        int roiY = expectedY - roiMarginTopPixels;
        int roiW = childWidthPixels + roiMarginLeftPixels + roiMarginRightPixels;
        int roiH = childHeightPixels + roiMarginTopPixels + roiMarginBottomPixels;

        // Clamp la ROI dans les limites du parent
        int parentEndX = parentLocation.x + parentW;
        int parentEndY = parentLocation.y + parentH;

        roiX = Math.max(roiX, parentLocation.x);
        roiY = Math.max(roiY, parentLocation.y);
        int roiEndX = Math.min(roiX + roiW, parentEndX);
        int roiEndY = Math.min(roiY + roiH, parentEndY);

        roiW = roiEndX - roiX;
        roiH = roiEndY - roiY;

        if (roiW <= 0 || roiH <= 0) {
            // ROI invalide, retourne la zone entière du parent
            return Region.create(parentLocation.x, parentLocation.y, parentW, parentH);
        }

        return Region.create(roiX, roiY, roiW, roiH);
    }

    // Getters

    public int getChildPatternId() {
        return childPatternId;
    }

    public int getParentPatternId() {
        return parentPatternId;
    }

    public int getRelativeXPixels() {
        return relativeXPixels;
    }

    public int getRelativeYPixels() {
        return relativeYPixels;
    }

    public int getChildWidthPixels() {
        return childWidthPixels;
    }

    public int getChildHeightPixels() {
        return childHeightPixels;
    }

    public int getRoiMarginLeftPixels() {
        return roiMarginLeftPixels;
    }

    public int getRoiMarginRightPixels() {
        return roiMarginRightPixels;
    }

    public int getRoiMarginTopPixels() {
        return roiMarginTopPixels;
    }

    public int getRoiMarginBottomPixels() {
        return roiMarginBottomPixels;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isParentIsRequired() {
        return parentIsRequired;
    }

    // Setters pour les champs modifiables

    public void setRoiMarginLeftPixels(int roiMarginLeftPixels) {
        this.roiMarginLeftPixels = roiMarginLeftPixels;
    }

    public void setRoiMarginRightPixels(int roiMarginRightPixels) {
        this.roiMarginRightPixels = roiMarginRightPixels;
    }

    public void setRoiMarginTopPixels(int roiMarginTopPixels) {
        this.roiMarginTopPixels = roiMarginTopPixels;
    }

    public void setRoiMarginBottomPixels(int roiMarginBottomPixels) {
        this.roiMarginBottomPixels = roiMarginBottomPixels;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setParentIsRequired(boolean parentIsRequired) {
        this.parentIsRequired = parentIsRequired;
    }
}
