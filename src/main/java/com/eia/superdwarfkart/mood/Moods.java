package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The moods that ship with the application.
 *
 * <p>Two of them for now, which is exactly the assignment's dark-mode bonus expressed the way this
 * project wants it: a look is a value, not a boolean. The eight presets named after
 * <em>Mario Kart: Super Circuit</em> tracks, and the customizer that lets the user build their own,
 * arrive with the mood system proper.
 *
 * <p>The hexadecimal literals in this file are legal for the same reason the ones in
 * {@link Palette#defaultPalette()} are: this is the <em>definition</em> of a palette rather than a
 * use of a colour. Everywhere else names a {@link PaletteRole}.
 */
public final class Moods {

    /**
     * The look the application has had since its first window: dark purple ground, amber primary,
     * cyan accents.
     */
    public static final Mood DARK = new Mood("dark", "Dark", Palette.defaultPalette());

    /**
     * The light mood.
     *
     * <p>Every value here was chosen against the <em>derivations</em> in {@link PaletteCss} rather
     * than by eye, because a light palette breaks an interface of beveled blocks in ways that look
     * fine in a colour picker:
     *
     * <ul>
     *   <li>{@link PaletteRole#SURFACE_RAISED} is a warm grey rather than white. A white face has
     *       no headroom above it, so the bevel's lit edge lands on the same colour as the face it
     *       is meant to be lighting and every button in the application goes flat.</li>
     *   <li>{@link PaletteRole#POSITIVE} and {@link PaletteRole#NEGATIVE} are dark enough to read
     *       against paper. The dark mood's bright green and red are close to invisible on it, and
     *       those two are protected roles: coins and obstacles telling each other apart is the
     *       whole of the runner's readability.</li>
     *   <li>{@link PaletteRole#TEXT_PRIMARY} is near-black and {@link PaletteRole#SHADOW} is a warm
     *       dark grey rather than black, so a shadow under dark text still reads as a shadow.</li>
     * </ul>
     */
    public static final Mood LIGHT = new Mood("light", "Light", lightPalette());

    private Moods() {
        throw new AssertionError("Moods is a constant holder and must not be instantiated");
    }

    /** @return every built-in mood, in the order the switcher shows them */
    public static List<Mood> builtIns() {
        return List.of(DARK, LIGHT);
    }

    /** @return the mood used when nothing has been chosen, or when a stored choice is unknown */
    public static Mood defaultMood() {
        return DARK;
    }

    /**
     * Looks a mood up by its stored identifier.
     *
     * <p>An unknown id is not an error: a mood may have been deleted, or a profile written by a
     * later version may name one this build has never heard of. The caller falls back to
     * {@link #defaultMood()} rather than refusing to start (ground rule 5).
     *
     * @param id the identifier to resolve; {@code null} yields an empty result
     * @return the mood with that id, if there is one
     */
    public static Optional<Mood> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return builtIns().stream().filter(mood -> mood.id().equals(id)).findFirst();
    }

    private static Palette lightPalette() {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        colors.put(PaletteRole.BACKGROUND, GbaColor.web("#e8e4d8"));
        colors.put(PaletteRole.BACKGROUND_ALT, GbaColor.web("#dcd8c8"));
        colors.put(PaletteRole.SURFACE, GbaColor.web("#f0ece0"));
        colors.put(PaletteRole.SURFACE_RAISED, GbaColor.web("#d8d4c4"));
        colors.put(PaletteRole.OUTLINE, GbaColor.web("#8a8270"));
        colors.put(PaletteRole.TEXT_PRIMARY, GbaColor.web("#1a1814"));
        colors.put(PaletteRole.TEXT_DIM, GbaColor.web("#5a5448"));
        // Dark amber, not the bright one this started as. PRIMARY is a text colour as often as it
        // is a fill - the application name, the table headings, the now-playing line, every focus
        // ring - and a bright amber that reads beautifully on the dark mood's near-black ground
        // came out at 2.0:1 against the light mood's own header. This is the shallowest value that
        // clears 4.5:1 on all three of the grounds it is ever drawn on.
        colors.put(PaletteRole.PRIMARY, GbaColor.web("#7a4400"));
        colors.put(PaletteRole.PRIMARY_DIM, GbaColor.web("#a89868"));
        // Darkened for the same reason as PRIMARY: ACCENT carries the section headings and the
        // playback status, so it is read rather than looked at.
        colors.put(PaletteRole.ACCENT, GbaColor.web("#004c80"));
        colors.put(PaletteRole.METER_LOW, GbaColor.web("#389838"));
        colors.put(PaletteRole.METER_HIGH, GbaColor.web("#d05000"));
        colors.put(PaletteRole.POSITIVE, GbaColor.web("#187818"));
        colors.put(PaletteRole.NEGATIVE, GbaColor.web("#b81818"));
        colors.put(PaletteRole.HIGHLIGHT, GbaColor.web("#b000b0"));
        colors.put(PaletteRole.SHADOW, GbaColor.web("#38342c"));
        return new Palette("Light", colors);
    }
}
