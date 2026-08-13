package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * A saved look: a name, and the sixteen colours everything in the application draws with.
 *
 * <p>Selecting a mood restyles the whole interface at once - the canvases because they resolve
 * every colour through {@link Palette#active()}, and the controls because {@link PaletteCss}
 * publishes the same palette as a stylesheet.
 *
 * <p>This is the assignment's dark-mode bonus, and it ships as moods rather than as a boolean on
 * purpose. A switch between two hard-coded themes is one feature; a named look the palette is a
 * value of is the same work and leaves room for the rest of them.
 *
 * <p><strong>Overlay layers arrive with the mood system proper.</strong> A mood is defined as a
 * palette <em>plus an ordered stack of layers</em>; the layers, the customizer, the pixel editor
 * and palette import are all M11. Adding the layer list here later is a compatible change, because
 * nothing outside this package constructs a mood.
 *
 * @param id          stable identifier, used for persistence and never shown to the user
 * @param displayName the name shown in the switcher
 * @param palette     the sixteen colours this mood draws with
 */
public record Mood(String id, String displayName, Palette palette) {

    /**
     * @throws IllegalArgumentException if the id or name is blank
     * @throws NullPointerException     if the palette is missing
     */
    public Mood {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(palette, "palette must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("A mood's id must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("A mood's display name must not be blank");
        }
    }

    /**
     * Returns the colour playing a role in this mood.
     *
     * @param role the role to resolve; must not be {@code null}
     * @return the colour for that role
     */
    public Color color(PaletteRole role) {
        return palette.color(role);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
