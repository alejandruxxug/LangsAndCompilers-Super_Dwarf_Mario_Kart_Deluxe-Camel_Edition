package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sixteen colours, one per {@link PaletteRole}.
 *
 * <p>This is the seam ground rule 7 exists for. No drawing code anywhere names a colour; it names
 * a role and asks the palette. Every {@code gc.setFill(...)} in the visualizer, the meters, the
 * road and the tree resolves through here, so the mood system can later swap the whole look by
 * replacing one object rather than by a find-and-replace across a finished interface.
 *
 * <p>A palette is immutable. Editing one in the customizer produces a new palette and installs
 * it, which keeps every reader safe without any locking.
 *
 * <p>{@link #active()} is a static holder rather than an injected dependency, matching the way
 * {@code Theme} and {@code AssetRegistry} are already reached in this project. When the mood
 * system arrives it sets the active palette from the mood stored in {@code AppState}, and the
 * drawing code does not change.
 */
public final class Palette {

    private static volatile Palette active = defaultPalette();

    private final String name;
    private final Map<PaletteRole, Color> colors;

    /**
     * Builds a palette from a complete set of roles.
     *
     * @param name   human-readable palette name
     * @param colors every role mapped to a colour; must contain all sixteen
     * @throws IllegalArgumentException if a role is missing
     */
    public Palette(String name, Map<PaletteRole, Color> colors) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(colors, "colors must not be null");

        Map<PaletteRole, Color> snapped = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            Color color = colors.get(role);
            if (color == null) {
                throw new IllegalArgumentException(
                        "Palette \"" + name + "\" is missing a colour for " + role.name()
                                + "; a palette holds exactly " + PaletteRole.COUNT + " colours");
            }
            // Snapped on the way in, so nothing downstream has to remember to do it.
            snapped.put(role, GbaColor.snap(color));
        }
        this.colors = snapped;
    }

    /**
     * Returns the colour playing the given role.
     *
     * @param role the role to resolve; must not be {@code null}
     * @return the colour for that role, never {@code null}
     */
    public Color color(PaletteRole role) {
        return colors.get(Objects.requireNonNull(role, "role must not be null"));
    }

    /**
     * Returns a role's colour at a reduced opacity, for washes and falloffs.
     *
     * <p>Opacity is not a colour, so varying it does not break the no-literals rule: the hue
     * still comes from the role.
     *
     * @param role    the role to resolve
     * @param opacity 0.0 fully transparent to 1.0 fully opaque
     * @return the role's colour at that opacity
     */
    public Color color(PaletteRole role, double opacity) {
        return color(role).deriveColor(0, 1, 1, Math.clamp(opacity, 0d, 1d));
    }

    /**
     * Mixes two roles, for gradients and for shading one surface towards another.
     *
     * @param from role at {@code t = 0}
     * @param to   role at {@code t = 1}
     * @param t    position between them, clamped to 0..1
     * @return the interpolated colour, snapped back onto the GBA grid
     */
    public Color mix(PaletteRole from, PaletteRole to, double t) {
        return GbaColor.snap(color(from).interpolate(color(to), Math.clamp(t, 0d, 1d)));
    }

    /** @return the palette's human-readable name */
    public String name() {
        return name;
    }

    /**
     * Returns the palette every view currently draws with.
     *
     * @return the active palette, never {@code null}
     */
    public static Palette active() {
        return active;
    }

    /**
     * Installs a palette as the one every view draws with.
     *
     * <p>Views must redraw themselves afterwards; this does not notify anyone, because until the
     * mood system exists there is nothing to change it at runtime.
     *
     * @param palette the palette to install; {@code null} restores the default
     */
    public static void setActive(Palette palette) {
        active = palette == null ? defaultPalette() : palette;
    }

    /**
     * Builds the palette shipped with the application: the dark purple and amber look the
     * stylesheet already uses, expressed as roles.
     *
     * <p>The hexadecimal literals below are the one legal place for them in this project - this
     * is the definition of a palette, not a use of a colour. Every value is snapped to the 5-bit
     * grid on construction, so what is written here and what is drawn may differ by a step.
     *
     * @return a new copy of the default palette
     */
    public static Palette defaultPalette() {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        colors.put(PaletteRole.BACKGROUND, GbaColor.web("#12121c"));
        colors.put(PaletteRole.BACKGROUND_ALT, GbaColor.web("#1c1b35"));
        colors.put(PaletteRole.SURFACE, GbaColor.web("#1a1930"));
        colors.put(PaletteRole.SURFACE_RAISED, GbaColor.web("#2a2750"));
        colors.put(PaletteRole.OUTLINE, GbaColor.web("#443e7a"));
        colors.put(PaletteRole.TEXT_PRIMARY, GbaColor.web("#e6e8f0"));
        colors.put(PaletteRole.TEXT_DIM, GbaColor.web("#9aa3b8"));
        colors.put(PaletteRole.PRIMARY, GbaColor.web("#ffd23f"));
        colors.put(PaletteRole.PRIMARY_DIM, GbaColor.web("#7a6520"));
        colors.put(PaletteRole.ACCENT, GbaColor.web("#6ee7ff"));
        colors.put(PaletteRole.METER_LOW, GbaColor.web("#3fd36b"));
        colors.put(PaletteRole.METER_HIGH, GbaColor.web("#ffd23f"));
        colors.put(PaletteRole.POSITIVE, GbaColor.web("#5ce65c"));
        colors.put(PaletteRole.NEGATIVE, GbaColor.web("#ff4d5a"));
        colors.put(PaletteRole.HIGHLIGHT, GbaColor.web("#ff5edb"));
        colors.put(PaletteRole.SHADOW, GbaColor.web("#05040e"));
        return new Palette("Default", colors);
    }

    @Override
    public String toString() {
        return "Palette[" + name + "]";
    }
}
