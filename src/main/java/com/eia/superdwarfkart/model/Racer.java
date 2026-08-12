package com.eia.superdwarfkart.model;

/**
 * A selectable racer.
 *
 * <p>Deliberately <em>not</em> named {@code Character}: that would collide with
 * {@link java.lang.Character} and force fully qualified names throughout the game code.
 *
 * <p>{@link #spriteKey()} is the lookup key handed to the asset registry. If the matching
 * artwork is missing the registry returns a labelled placeholder, so an unselected or
 * unfinished racer never prevents the application from running.
 */
public enum Racer {

    MARIO("Mario", "mario"),
    LUIGI("Luigi", "luigi"),
    PEACH("Peach", "peach"),
    YOSHI("Yoshi", "yoshi"),
    BOWSER("Bowser", "bowser");

    private final String displayName;
    private final String spriteKey;

    Racer(String displayName, String spriteKey) {
        this.displayName = displayName;
        this.spriteKey = spriteKey;
    }

    /** @return human-readable name shown on the racer select screen */
    public String displayName() {
        return displayName;
    }

    /** @return key used to look this racer's artwork up in the asset registry */
    public String spriteKey() {
        return spriteKey;
    }

    /** @return the racer selected when the application has no stored preference */
    public static Racer defaultRacer() {
        return MARIO;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
