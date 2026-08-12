package com.eia.superdwarfkart.model;

/**
 * Musical genre of a song, used by the library filters.
 */
public enum Genre {

    POP("Pop"),
    ROCK("Rock"),
    METAL("Metal"),
    HIP_HOP("Hip Hop"),
    ELECTRONIC("Electronic"),
    JAZZ("Jazz"),
    BLUES("Blues"),
    CLASSICAL("Classical"),
    FOLK("Folk"),
    LATIN("Latin"),
    SOUNDTRACK("Soundtrack"),
    CHIPTUNE("Chiptune"),
    OTHER("Other"),
    UNKNOWN("Unknown");

    private final String displayName;

    Genre(String displayName) {
        this.displayName = displayName;
    }

    /** @return human-readable name for the user interface */
    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
