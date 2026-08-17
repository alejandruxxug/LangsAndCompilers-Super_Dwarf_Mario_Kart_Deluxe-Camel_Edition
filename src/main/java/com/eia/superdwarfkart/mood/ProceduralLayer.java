package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

/**
 * A layer computed from a formula, needing no artwork at all.
 *
 * <p>These are worth far more than they cost, and the reason is a sequencing problem rather than an
 * aesthetic one: <strong>the first mood a user builds has no art in it.</strong> A palette on its
 * own restyles the interface and leaves it looking like the same application in different colours;
 * one scanline pass or one vignette and it looks like a different machine. Four of them ship, all
 * off by default, and any of them combined with an imported palette produces a finished-looking
 * mood in two clicks.
 *
 * <p>Every pattern here draws in a palette role, so all four follow the mood they are part of.
 * There is not a colour in this file.
 *
 * @param style      band, opacity, blend, scroll and visibility
 * @param pattern    which of the four
 * @param pixelScale the pattern's own grid, in pixels; what a scanline's spacing means
 * @param seed       the starfield's arrangement, so the same mood has the same stars every launch
 */
public record ProceduralLayer(LayerStyle style, Pattern pattern, int pixelScale, long seed)
        implements MoodLayer {

    /** Coarsest grid offered. Past this a scanline is a stripe and an LCD grid is a window frame. */
    public static final int MAX_PIXEL_SCALE = 16;

    /** What the layer draws. */
    public enum Pattern {

        /**
         * Alternating rows of transparent and {@link PaletteRole#SHADOW}.
         *
         * <p>The cheapest convincing effect there is: a CRT never drew the rows between its
         * scanlines, and one dark row in every {@code pixelScale} is the whole of it.
         */
        SCANLINES("Scanlines"),

        /**
         * The faint grid between a GBA's own pixels, drawn at {@code pixelScale}.
         *
         * <p>Not the same thing as scanlines and it is worth knowing why: a handheld LCD has a gap
         * on <em>both</em> axes and a television has one on neither. This is the one that makes a
         * screenshot look like a photograph of a handheld.
         */
        LCD_GRID("LCD grid"),

        /**
         * A radial falloff into {@link PaletteRole#SHADOW} at the corners.
         *
         * <p>The one pattern that helps rather than decorates: it darkens exactly the parts of the
         * screen the interface puts nothing in, so it deepens the picture without touching the
         * road, the table or the tree.
         */
        VIGNETTE("Vignette"),

        /**
         * Seeded dots in {@link PaletteRole#TEXT_PRIMARY}, which scroll with the layer.
         *
         * <p>Seeded rather than random for the reason everything in this project is: an effect
         * nobody can reproduce is an effect nobody can check, and a starfield that re-rolled on
         * every mood switch would be a different mood each time it was chosen.
         */
        STARFIELD("Starfield");

        private final String displayName;

        Pattern(String displayName) {
            this.displayName = displayName;
        }

        /** @return the caption shown in the customizer */
        public String displayName() {
            return displayName;
        }

        /**
         * Reads a stored pattern, tolerating anything a later version might have written.
         *
         * @param name the stored name; {@code null} or unknown yields {@link #SCANLINES}
         * @return the pattern
         */
        public static Pattern byName(String name) {
            if (name != null) {
                for (Pattern value : values()) {
                    if (value.name().equalsIgnoreCase(name.strip())) {
                        return value;
                    }
                }
            }
            return SCANLINES;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** Canonical constructor, clamping the grid to something that can be seen. */
    public ProceduralLayer {
        if (style == null) {
            style = LayerStyle.behind();
        }
        if (pattern == null) {
            pattern = Pattern.SCANLINES;
        }
        pixelScale = Math.clamp(pixelScale, 2, MAX_PIXEL_SCALE);
    }

    /**
     * One of the four at its usual settings.
     *
     * @param pattern which pattern
     * @param opacity how strongly to draw it
     * @return the layer
     */
    public static ProceduralLayer of(Pattern pattern, double opacity) {
        // Above the content, because all four of these are things seen *through*: a scanline
        // pattern behind the interface is a scanline pattern nobody can see. Which is also why the
        // band's 0.35 cap matters here more than anywhere else.
        return new ProceduralLayer(LayerStyle.above().withOpacity(opacity), pattern, 4, 1);
    }

    @Override
    public MoodLayer withStyle(LayerStyle newStyle) {
        return new ProceduralLayer(newStyle, pattern, pixelScale, seed);
    }

    /**
     * Returns a copy on a different grid.
     *
     * @param scale the pattern's spacing in pixels
     * @return the new layer
     */
    public ProceduralLayer withPixelScale(int scale) {
        return new ProceduralLayer(style, pattern, scale, seed);
    }

    /**
     * Returns a copy with a different arrangement.
     *
     * @param newSeed the seed
     * @return the new layer
     */
    public ProceduralLayer withSeed(long newSeed) {
        return new ProceduralLayer(style, pattern, pixelScale, newSeed);
    }

    /**
     * The role this pattern draws in.
     *
     * <p>Named here rather than in the renderer so that the customizer can say what a pattern will
     * look like before it is added, and so that there is one answer rather than two.
     *
     * @return the palette role
     */
    public PaletteRole role() {
        return switch (pattern) {
            case SCANLINES, LCD_GRID, VIGNETTE -> PaletteRole.SHADOW;
            case STARFIELD -> PaletteRole.TEXT_PRIMARY;
        };
    }

    /**
     * The colour this pattern draws in, at a given strength.
     *
     * @param palette  the palette in force; must not be {@code null}
     * @param strength 0 to 1
     * @return the colour
     */
    public Color color(Palette palette, double strength) {
        return palette.color(role(), strength);
    }

    /**
     * Whether a star sits at a given cell of the starfield's grid.
     *
     * <p>Hashed rather than drawn from a generator, so the answer for one cell needs no knowledge
     * of any other and a scrolled starfield can be rasterised from any offset. The mixer is the
     * <strong>SplitMix64 finaliser</strong>, for the reason recorded against the boot screen's
     * glitch: FNV-1a avalanches poorly in its high bits over two small integers, and a field whose
     * every cell cleared the same threshold is not a starfield, it is a grid.
     *
     * @param cellX  cell column
     * @param cellY  cell row
     * @param oneIn  roughly one cell in this many carries a star
     * @return whether to draw a star there
     */
    public boolean hasStar(int cellX, int cellY, int oneIn) {
        if (oneIn <= 1) {
            return true;
        }
        long mixed = mix(seed * 0x9E3779B97F4A7C15L + cellX * 0xC2B2AE3D27D4EB4FL + cellY);
        return Long.remainderUnsigned(mixed >>> 11, oneIn) == 0;
    }

    /**
     * How bright a star is, so a field has depth rather than being one flat scatter.
     *
     * @param cellX cell column
     * @param cellY cell row
     * @return a brightness in 0.3 to 1.0
     */
    public double starBrightness(int cellX, int cellY) {
        long mixed = mix(seed * 0x2545F4914F6CDD1DL + cellY * 0x9E3779B97F4A7C15L + cellX);
        return 0.3 + 0.7 * ((mixed >>> 12) & 0xFFFF) / 65535d;
    }

    /** The SplitMix64 finaliser: a strong mixer over small seeds, unlike FNV-1a's high bits. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public String describe() {
        return pattern.displayName() + " at " + pixelScale + "px";
    }
}
