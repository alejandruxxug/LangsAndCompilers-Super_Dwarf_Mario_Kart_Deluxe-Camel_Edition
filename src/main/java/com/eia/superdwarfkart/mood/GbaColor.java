package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

/**
 * The only way a colour enters the application.
 *
 * <p>The Game Boy Advance framebuffer is BGR555: five bits per channel, so thirty-two levels
 * each and 32,768 colours in total. Every colour picked, imported or typed round-trips
 * {@link #quantize(int)} then {@link #expand(int)} before it is stored, which is what keeps an
 * arbitrary user palette looking like a GBA game rather than a website theme. The constraint is
 * the aesthetic; it is not a limitation being tolerated.
 *
 * <p>The expansion deliberately is <em>not</em> {@code c5 << 3}. That maps 31 to 248 rather than
 * to 255, so pure white comes out slightly grey and every palette drifts dark. Replicating the
 * top bits into the bottom ones maps 0 to 0 and 31 to 255 exactly.
 */
public final class GbaColor {

    /** Levels available per channel: five bits. */
    public static final int LEVELS = 32;

    /** Highest value a quantized channel can take. */
    public static final int MAX_LEVEL = LEVELS - 1;

    /**
     * Snaps an 8-bit channel value onto the GBA's 5-bit grid.
     *
     * @param c8 channel value in 0..255; values outside that range are clamped
     * @return the channel value in 0..31
     */
    public static int quantize(int c8) {
        int clamped = Math.clamp(c8, 0, 255);
        return Math.round(clamped * (float) MAX_LEVEL / 255f);
    }

    /**
     * Expands a 5-bit channel value back to eight bits.
     *
     * <p>The low three bits are filled from the high three rather than with zeroes, so the range
     * covers 0 to 255 exactly instead of stopping at 248.
     *
     * @param c5 channel value in 0..31; values outside that range are clamped
     * @return the channel value in 0..255
     */
    public static int expand(int c5) {
        int clamped = Math.clamp(c5, 0, MAX_LEVEL);
        return (clamped << 3) | (clamped >> 2);
    }

    /**
     * Rounds an 8-bit channel value to the nearest one the hardware could actually display.
     *
     * @param c8 channel value in 0..255
     * @return the nearest representable 8-bit value
     */
    public static int snap(int c8) {
        return expand(quantize(c8));
    }

    /**
     * Builds a colour from 8-bit channels, snapped to the GBA grid.
     *
     * @param r red in 0..255
     * @param g green in 0..255
     * @param b blue in 0..255
     * @return the nearest colour the hardware could display
     */
    public static Color of(int r, int g, int b) {
        return Color.rgb(snap(r), snap(g), snap(b));
    }

    /**
     * Parses a hex string and snaps it to the GBA grid.
     *
     * <p>This is the one place in the application where a hexadecimal colour literal is legal.
     * Everywhere else names a {@link PaletteRole} and lets the active {@link Palette} answer.
     *
     * @param hex a colour in any form {@link Color#web(String)} accepts, such as {@code "#12121c"}
     * @return the nearest colour the hardware could display
     */
    public static Color web(String hex) {
        return snap(Color.web(hex));
    }

    /**
     * Snaps an arbitrary colour onto the GBA grid, preserving its opacity.
     *
     * @param color the colour to snap; must not be {@code null}
     * @return the nearest colour the hardware could display
     */
    public static Color snap(Color color) {
        return Color.rgb(
                snap(eightBit(color.getRed())),
                snap(eightBit(color.getGreen())),
                snap(eightBit(color.getBlue())),
                color.getOpacity());
    }

    /**
     * Renders a colour as {@code #rrggbb}, for storage and for the customizer's readout.
     *
     * @param color the colour to render; must not be {@code null}
     * @return the colour as a six-digit hex string with a leading hash
     */
    public static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                eightBit(color.getRed()), eightBit(color.getGreen()), eightBit(color.getBlue()));
    }

    private static int eightBit(double channel) {
        return (int) Math.round(Math.clamp(channel, 0d, 1d) * 255);
    }

    private GbaColor() {
        throw new AssertionError("GbaColor is a utility holder and must not be instantiated");
    }
}
