package com.eia.superdwarfkart.model;

/**
 * Identifies the three playback modes and the hand-written structure that backs each one.
 *
 * <p>This enum exists so the user interface can list and select modes without knowing the
 * concrete {@code PlaybackMode} classes; the {@code Player} itself never inspects it.
 */
public enum ModeId {

    /** Mode 1: a shuffled ordering baked once into a circular doubly linked list. */
    SHUFFLE("Shuffle", "CircularDoublyLinkedList", true),

    /** Mode 2: strict arrival order, first in first out. Going back is not possible. */
    ARRIVAL_ORDER("Arrival Order", "SimpleQueue", false),

    /** Mode 3: alphabetical by title, walked as in-order successor and predecessor. */
    ALPHABETICAL("Alphabetical", "BinarySearchTree", true);

    private final String displayName;
    private final String structureName;
    private final boolean supportsPrevious;

    ModeId(String displayName, String structureName, boolean supportsPrevious) {
        this.displayName = displayName;
        this.structureName = structureName;
        this.supportsPrevious = supportsPrevious;
    }

    /** @return human-readable mode name for the user interface */
    public String displayName() {
        return displayName;
    }

    /** @return name of the hand-written data structure backing this mode */
    public String structureName() {
        return structureName;
    }

    /**
     * @return whether this mode can move backwards; when {@code false} the user interface
     *         disables the previous-track control rather than letting it throw
     */
    public boolean supportsPrevious() {
        return supportsPrevious;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
