package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

/**
 * The two measurements a mood is judged by: how readable text is, and how far apart two colours
 * look.
 *
 * <p>Both are perceptual rather than arithmetic, and the difference matters. Plain RGB distance
 * calls {@code #0000ff} and {@code #000080} far apart and {@code #808000} and {@code #808040}
 * close, which is the wrong way round for how they read across a room - and a room is precisely
 * where this application is judged.
 *
 * <ul>
 *   <li><strong>WCAG contrast</strong> for text, because that is the standard the four-and-a-half
 *       to one figure comes from and there is no reason to invent another.</li>
 *   <li><strong>CIE76 ΔE</strong> for "are these two the same colour", because Lab is roughly
 *       uniform: a distance of 25 means about the same amount of difference wherever in the space
 *       it is measured.</li>
 * </ul>
 *
 * <p>Held in {@code mood/} rather than in a test helper because {@link MoodValidator} runs at
 * runtime on every load and every edit, not only in the build.
 */
public final class ColorMath {

    private ColorMath() {
        throw new AssertionError("ColorMath is a utility holder and must not be instantiated");
    }

    /**
     * The WCAG relative-luminance contrast ratio between two colours.
     *
     * @param a the first colour; must not be {@code null}
     * @param b the second colour; must not be {@code null}
     * @return the ratio, 1.0 for identical colours and 21.0 for black against white
     */
    public static double contrast(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    /**
     * WCAG relative luminance.
     *
     * @param color the colour; must not be {@code null}
     * @return the luminance, 0 to 1
     */
    public static double luminance(Color color) {
        return 0.2126 * linear(color.getRed())
                + 0.7152 * linear(color.getGreen())
                + 0.0722 * linear(color.getBlue());
    }

    /**
     * CIE76: plain Euclidean distance in Lab.
     *
     * @param a the first colour; must not be {@code null}
     * @param b the second colour; must not be {@code null}
     * @return the distance; 25 is about where two colours stop reading as one
     */
    public static double deltaE(Color a, Color b) {
        double[] first = lab(a);
        double[] second = lab(b);
        double dl = first[0] - second[0];
        double da = first[1] - second[1];
        double db = first[2] - second[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * Converts to CIE Lab under a D65 white point.
     *
     * @param color the colour; must not be {@code null}
     * @return {@code {L, a, b}}
     */
    public static double[] lab(Color color) {
        double r = linear(color.getRed());
        double g = linear(color.getGreen());
        double b = linear(color.getBlue());

        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;

        double fx = pivot(x);
        double fy = pivot(y);
        double fz = pivot(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    /**
     * Moves a colour's lightness without changing its hue, and snaps the result to the GBA grid.
     *
     * <p>This is the substitution the validator makes when a mood breaks a protected pair. It moves
     * lightness rather than hue on purpose: hue is what the user chose and what the mood is
     * <em>about</em>, and a repair that recoloured a mood's green into a blue would be less
     * obviously correct and far more obviously an intrusion.
     *
     * @param color  the colour to move; must not be {@code null}
     * @param amount how far, from -1 (towards black) to 1 (towards white)
     * @return the moved colour, on the hardware grid
     */
    public static Color shiftLightness(Color color, double amount) {
        double t = Math.clamp(amount, -1d, 1d);
        Color target = t >= 0 ? Color.WHITE : Color.BLACK;
        return GbaColor.snap(color.interpolate(target, Math.abs(t)));
    }

    /**
     * Pushes a colour's lightness until it is readable against every ground it will be drawn on.
     *
     * <p>Both directions are searched and the better one wins, rather than assuming which way there
     * is room. On a mid-grey ground - which is exactly where a user lands half-way through dragging
     * a colour picker - one direction runs out of headroom and the other does not, and guessing
     * gets it wrong half the time.
     *
     * <p>When nothing clears the bar the best attempt is returned rather than the original. A
     * palette whose background is mid-grey has nowhere for text to go, and the nearest-to-readable
     * version of it is strictly better than the unreadable one the user typed.
     *
     * @param color   the colour to move; must not be {@code null}
     * @param grounds every ground it is drawn on; must not be empty
     * @param target  the contrast ratio to reach
     * @return the moved colour, on the hardware grid
     */
    public static Color forContrast(Color color, java.util.List<Color> grounds, double target) {
        double best = worstContrast(color, grounds);
        if (best >= target) {
            return color;
        }
        Color winner = color;
        for (double amount = 0.03; amount <= 1.0; amount += 0.03) {
            for (double direction : new double[] {1, -1}) {
                Color candidate = shiftLightness(color, direction * amount);
                double score = worstContrast(candidate, grounds);
                if (score >= target) {
                    return candidate;
                }
                if (score > best) {
                    best = score;
                    winner = candidate;
                }
            }
        }
        return winner;
    }

    /**
     * The contrast against whichever ground is worst for this colour.
     *
     * @param color   the colour; must not be {@code null}
     * @param grounds the grounds it is drawn on; must not be empty
     * @return the lowest contrast ratio of the set
     */
    public static double worstContrast(Color color, java.util.List<Color> grounds) {
        double worst = Double.MAX_VALUE;
        for (Color ground : grounds) {
            worst = Math.min(worst, contrast(color, ground));
        }
        return worst;
    }

    /**
     * Plain RGB distance, normalised to 0..1.
     *
     * <p>Deliberately <em>not</em> {@link #deltaE}: this answers "is this literally a different
     * colour from that", which is the question a selected table row asks of an unselected one, and
     * for that a perceptual metric is more machinery than the question needs.
     *
     * @param a the first colour; must not be {@code null}
     * @param b the second colour; must not be {@code null}
     * @return the distance, 0 for identical colours and 1 for black against white
     */
    public static double distance(Color a, Color b) {
        double dr = a.getRed() - b.getRed();
        double dg = a.getGreen() - b.getGreen();
        double db = a.getBlue() - b.getBlue();
        return Math.sqrt((dr * dr + dg * dg + db * db) / 3);
    }

    /**
     * Whether a colour is nearer the dark end than the light one.
     *
     * <p>Which direction a repair should push in: a colour on a dark ground has room upwards and a
     * colour on a light one has room downwards, and pushing the wrong way makes the problem worse
     * while appearing to do something about it.
     *
     * @param color the colour; must not be {@code null}
     * @return {@code true} when it is dark
     */
    public static boolean isDark(Color color) {
        return luminance(color) < 0.18;
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double pivot(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (903.3 * t + 16) / 116;
    }
}
