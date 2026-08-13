package com.eia.superdwarfkart.model;

import java.util.Arrays;
import java.util.Optional;

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

    /**
     * Resolves a stored racer name.
     *
     * <p>{@link #valueOf} would throw on a name this build does not know, which is a plausible
     * thing to find in a profile written by a later version - and no reason at all to refuse to
     * open. An unknown name is simply absent, and the caller keeps its default.
     *
     * @param name the constant name to resolve; {@code null} yields an empty result
     * @return the racer with that name, if there is one
     */
    public static Optional<Racer> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(racer -> racer.name().equals(name)).findFirst();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
