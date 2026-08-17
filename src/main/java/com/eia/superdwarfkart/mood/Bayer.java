package com.eia.superdwarfkart.mood;

/**
 * The 4x4 ordered dithering matrix, and the one detail that sells a banded gradient as hardware
 * rather than as a posterize filter.
 *
 * <p>A gradient cut into eight bands has seven hard edges across the screen. That is the authentic
 * look and it is also the look of a JPEG that has been saved too many times, and the difference
 * between the two is entirely what happens <em>at</em> the edge. Ordered dithering scatters pixels
 * across the boundary on a fixed 4x4 pattern, so the eye reads a gradual change made of two colours
 * instead of a step - which is what a machine with sixteen colours and no alpha channel actually
 * did.
 *
 * <p>Ordered rather than error-diffused, and that is not a shortcut. Floyd-Steinberg gives a better
 * still image and a <em>worse</em> moving one: its pattern depends on every pixel before it, so a
 * layer that scrolls by one pixel re-rolls the whole texture and the background boils. A threshold
 * matrix is a pure function of the pixel's coordinates, so a scrolling layer's dither travels with
 * it.
 */
public final class Bayer {

    /**
     * The classic 4x4 threshold matrix, in raster order.
     *
     * <p>Values 0..15. Each entry is the fraction of the way through a band at which that pixel
     * flips to the next colour, so a pixel with a low threshold flips early and one with a high
     * threshold flips late.
     */
    private static final int[] MATRIX = {
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    };

    /** Width and height of the matrix. */
    public static final int SIZE = 4;

    /** Number of distinct thresholds, which is {@link #SIZE} squared. */
    public static final int LEVELS = SIZE * SIZE;

    private Bayer() {
        throw new AssertionError("Bayer is a constant holder and must not be instantiated");
    }

    /**
     * Returns the threshold for a pixel, as a fraction of one band.
     *
     * @param x pixel column; negative values wrap, so a scrolled layer keeps its pattern
     * @param y pixel row
     * @return a value in {@code [0, 1)}
     */
    public static double threshold(int x, int y) {
        int column = Math.floorMod(x, SIZE);
        int row = Math.floorMod(y, SIZE);
        return MATRIX[row * SIZE + column] / (double) LEVELS;
    }
}
