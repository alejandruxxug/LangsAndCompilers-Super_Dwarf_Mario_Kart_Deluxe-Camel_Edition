package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

/**
 * One colour along a {@link GradientLayer}, held either as a palette role or as a free colour.
 *
 * <p>The two are not the same thing and the difference is the point. A stop that names a
 * <em>role</em> follows the mood: recolour the palette and the gradient recolours with it, which is
 * the same property that makes a {@link PixelTile} restyle itself. A stop that names a colour is
 * fixed, which is what an imported look sometimes needs - a sunset whose orange is not any of the
 * sixteen roles.
 *
 * <p>Both are on the GBA grid. A free colour is snapped on the way in, exactly as
 * {@link Palette} snaps its own entries, so there is no way to smuggle an off-grid colour into a
 * mood through a gradient.
 *
 * @param position where along the gradient this stop sits, 0 to 1
 * @param role     the role this stop follows, or {@code null} when it is a fixed colour
 * @param fixed    the fixed colour, or {@code null} when the stop follows a role
 */
public record GradientStop(double position, PaletteRole role, Color fixed) {

    /**
     * @throws IllegalArgumentException if the stop names neither a role nor a colour
     */
    public GradientStop {
        position = Math.clamp(position, 0d, 1d);
        if (role == null && fixed == null) {
            throw new IllegalArgumentException(
                    "A gradient stop must name a palette role or a colour");
        }
        if (fixed != null) {
            fixed = GbaColor.snap(fixed);
        }
    }

    /**
     * A stop that follows a palette role.
     *
     * @param position where along the gradient, 0 to 1
     * @param role     the role to follow; must not be {@code null}
     * @return the stop
     */
    public static GradientStop of(double position, PaletteRole role) {
        return new GradientStop(position, role, null);
    }

    /**
     * A stop fixed to one colour, snapped to the hardware grid.
     *
     * @param position where along the gradient, 0 to 1
     * @param color    the colour; must not be {@code null}
     * @return the stop
     */
    public static GradientStop of(double position, Color color) {
        return new GradientStop(position, null, color);
    }

    /**
     * Resolves this stop against a palette.
     *
     * @param palette the palette in force; must not be {@code null}
     * @return the colour to draw
     */
    public Color color(Palette palette) {
        return role != null ? palette.color(role) : fixed;
    }

    /** @return {@code true} when this stop follows the mood rather than a fixed colour */
    public boolean followsRole() {
        return role != null;
    }
}
