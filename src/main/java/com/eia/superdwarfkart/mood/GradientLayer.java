package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A gradient built from the palette, banded and dithered so it reads as 8-bit rather than as a web
 * page.
 *
 * <p>Two properties do all of that work and neither is decoration. <strong>{@code bands}</strong>
 * posterises the ramp into a small number of flat steps - the default is eight, because a hardware
 * gradient <em>was</em> a handful of flat bands and a smooth one instantly reads as a modern
 * toolkit whatever the palette is. <strong>{@code dither}</strong> scatters the boundary between
 * two bands on the fixed {@link Bayer} pattern, which is the difference between a deliberate
 * hardware gradient and a posterize filter somebody left on.
 *
 * <p>The stops resolve through {@link GradientStop}, so a gradient made of roles restyles itself
 * when the palette changes and one made of fixed colours does not. Both are on the GBA grid.
 *
 * @param style   band, opacity, blend, scroll and visibility
 * @param kind    a linear ramp at an angle, or a radial one from a point
 * @param angle   for {@link Kind#LINEAR}: the ramp's direction in degrees, 0 pointing right and
 *                90 pointing down
 * @param centerX for {@link Kind#RADIAL}: the centre across, as a fraction of the width
 * @param centerY for {@link Kind#RADIAL}: the centre down, as a fraction of the height
 * @param radius  for {@link Kind#RADIAL}: how far out the last stop sits, as a fraction of the
 *                canvas's half-diagonal
 * @param stops   two to four colours along the ramp, sorted by position
 * @param bands   0 for a smooth ramp, otherwise how many flat steps to cut it into
 * @param dither  whether to scatter the band boundaries on the Bayer matrix
 */
public record GradientLayer(LayerStyle style, Kind kind, double angle,
        double centerX, double centerY, double radius,
        List<GradientStop> stops, int bands, boolean dither) implements MoodLayer {

    /** Fewest stops a gradient can have and still be one. */
    public static final int MIN_STOPS = 2;

    /**
     * Most stops a gradient may have.
     *
     * <p>Four, not because more is hard but because more is wrong here: a sixteen-colour palette
     * cut into eight bands has about two levels per band to work with, and a fifth stop spends them
     * on a transition nobody can see while making the customizer's stop list too long for the
     * column it sits in.
     */
    public static final int MAX_STOPS = 4;

    /** Steps a banded gradient is cut into unless the mood says otherwise. */
    public static final int DEFAULT_BANDS = 8;

    /** Most bands worth offering: past this the ramp is smooth and the point has been lost. */
    public static final int MAX_BANDS = 32;

    /** Which shape the ramp takes. */
    public enum Kind {

        /** A straight ramp across the canvas at {@link GradientLayer#angle()} degrees. */
        LINEAR("Linear"),

        /** A ramp outwards from a point, for a spotlight or a dusk sky. */
        RADIAL("Radial");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        /** @return the caption shown in the customizer */
        public String displayName() {
            return displayName;
        }

        /**
         * Reads a stored kind, tolerating anything a later version might have written.
         *
         * @param name the stored name; {@code null} or unknown yields {@link #LINEAR}
         * @return the kind
         */
        public static Kind byName(String name) {
            if (name != null) {
                for (Kind value : values()) {
                    if (value.name().equalsIgnoreCase(name.strip())) {
                        return value;
                    }
                }
            }
            return LINEAR;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * @throws IllegalArgumentException if the stop count is outside {@value #MIN_STOPS} to
     *                                  {@value #MAX_STOPS}
     */
    public GradientLayer {
        if (style == null) {
            style = LayerStyle.behind();
        }
        if (kind == null) {
            kind = Kind.LINEAR;
        }
        if (stops == null || stops.size() < MIN_STOPS || stops.size() > MAX_STOPS) {
            throw new IllegalArgumentException("A gradient holds " + MIN_STOPS + " to " + MAX_STOPS
                    + " stops, not " + (stops == null ? 0 : stops.size()));
        }
        // Sorted here rather than trusted, so a hand-edited mood file whose stops are out of order
        // draws the ramp it describes instead of a ramp that folds back on itself.
        List<GradientStop> sorted = new ArrayList<>(stops);
        sorted.sort(Comparator.comparingDouble(GradientStop::position));
        stops = List.copyOf(sorted);

        bands = Math.clamp(bands, 0, MAX_BANDS);
        radius = Math.max(0.01, radius);
        centerX = Math.clamp(centerX, -1d, 2d);
        centerY = Math.clamp(centerY, -1d, 2d);
    }

    /**
     * A linear gradient between two roles, banded at the default and dithered - the shape almost
     * every preset uses.
     *
     * @param from   the role at the top
     * @param to     the role at the bottom
     * @param degrees the ramp's direction, 90 pointing down
     * @return the layer
     */
    public static GradientLayer between(PaletteRole from, PaletteRole to, double degrees) {
        return new GradientLayer(LayerStyle.behind(), Kind.LINEAR, degrees, 0.5, 0.5, 1.0,
                List.of(GradientStop.of(0, from), GradientStop.of(1, to)), DEFAULT_BANDS, true);
    }

    @Override
    public MoodLayer withStyle(LayerStyle newStyle) {
        return new GradientLayer(newStyle, kind, angle, centerX, centerY, radius, stops, bands,
                dither);
    }

    /**
     * Returns a copy with different stops.
     *
     * @param newStops two to four stops
     * @return the new layer
     */
    public GradientLayer withStops(List<GradientStop> newStops) {
        return new GradientLayer(style, kind, angle, centerX, centerY, radius, newStops, bands,
                dither);
    }

    /**
     * Returns a copy cut into a different number of bands.
     *
     * @param newBands 0 for smooth, otherwise the step count
     * @return the new layer
     */
    public GradientLayer withBands(int newBands) {
        return new GradientLayer(style, kind, angle, centerX, centerY, radius, stops, newBands,
                dither);
    }

    /**
     * Returns a copy with dithering on or off.
     *
     * @param on whether to dither the band boundaries
     * @return the new layer
     */
    public GradientLayer withDither(boolean on) {
        return new GradientLayer(style, kind, angle, centerX, centerY, radius, stops, bands, on);
    }

    /**
     * Returns a copy of a different shape.
     *
     * @param newKind linear or radial
     * @return the new layer
     */
    public GradientLayer withKind(Kind newKind) {
        return new GradientLayer(style, newKind, angle, centerX, centerY, radius, stops, bands,
                dither);
    }

    /**
     * Returns a copy pointing a different way.
     *
     * @param degrees the ramp's direction, 90 pointing down
     * @return the new layer
     */
    public GradientLayer withAngle(double degrees) {
        return new GradientLayer(style, kind, degrees, centerX, centerY, radius, stops, bands,
                dither);
    }

    /**
     * Where a pixel sits along the ramp, before any banding.
     *
     * <p>Held here rather than in the renderer because it is the layer's geometry rather than the
     * canvas's, and because it is the half of this class a test can check without a window.
     *
     * @param x      pixel column
     * @param y      pixel row
     * @param width  canvas width in pixels
     * @param height canvas height in pixels
     * @return position along the ramp, 0 to 1
     */
    public double positionAt(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        if (kind == Kind.RADIAL) {
            double dx = x - centerX * width;
            double dy = y - centerY * height;
            double half = Math.hypot(width, height) / 2;
            return Math.clamp(Math.hypot(dx, dy) / (half * radius), 0d, 1d);
        }
        // A straight ramp is the projection of the pixel onto the ramp's own direction, divided by
        // how far the canvas extends along it. Both terms use the same unit vector, so the ends
        // land exactly on 0 and 1 whatever the angle - a ramp that stopped short of the corner
        // would leave a flat band along one edge at 45 degrees and nowhere else.
        double radians = Math.toRadians(angle);
        double ux = Math.cos(radians);
        double uy = Math.sin(radians);
        double span = Math.abs(ux) * width + Math.abs(uy) * height;
        if (span <= 0) {
            return 0;
        }
        double origin = (ux < 0 ? width : 0) * ux + (uy < 0 ? height : 0) * uy;
        return Math.clamp((x * ux + y * uy - origin) / span, 0d, 1d);
    }

    /**
     * The colour at a point along the ramp, banded and dithered.
     *
     * <p>The dither is applied by nudging the position by up to one band's worth before the
     * quantisation, which is the ordered-dithering trick stated the short way: a pixel whose
     * threshold is low crosses into the next band early and one whose threshold is high crosses
     * late, so the boundary becomes a scatter of both colours rather than a line.
     *
     * @param position where along the ramp, 0 to 1
     * @param palette  the palette to resolve roles against; must not be {@code null}
     * @param x        pixel column, for the dither pattern
     * @param y        pixel row, for the dither pattern
     * @return the colour to write
     */
    public Color colorAt(double position, Palette palette, int x, int y) {
        double t = Math.clamp(position, 0d, 1d);
        if (bands > 0) {
            double scaled = t * bands;
            if (dither) {
                // Bayer's thresholds run 0/16 to 15/16; centring them on zero means the dither
                // pushes a pixel either way rather than always later, so the banded ramp keeps the
                // same average position as the smooth one it replaces.
                scaled += Bayer.threshold(x, y) - 0.5;
            }
            // Floor rather than round: the band a position falls *in*. Rounding would make the
            // first and last bands half the width of every other one, which shows up as a thin
            // stripe of the end colour along both edges and nowhere else.
            //
            // The clamp is what makes the top band a whole band. floor(1.0 * bands) is `bands`,
            // one past the last index, so without it the ramp's final pixel row lands in a band of
            // its own - a one-pixel line of the end colour, which is exactly the artefact a banded
            // gradient is accused of when it is drawn badly.
            double step = Math.clamp(Math.floor(scaled), 0d, bands - 1d);
            t = bands == 1 ? 0 : step / (bands - 1d);
        }
        return sample(t, palette);
    }

    /**
     * The colour at a point on the unbanded ramp.
     *
     * @param t       position along the ramp, 0 to 1
     * @param palette the palette to resolve roles against; must not be {@code null}
     * @return the interpolated colour, snapped to the GBA grid
     */
    public Color sample(double t, Palette palette) {
        double position = Math.clamp(t, 0d, 1d);
        GradientStop first = stops.get(0);
        if (position <= first.position()) {
            return first.color(palette);
        }
        for (int i = 1; i < stops.size(); i++) {
            GradientStop previous = stops.get(i - 1);
            GradientStop next = stops.get(i);
            if (position <= next.position()) {
                double span = next.position() - previous.position();
                double local = span <= 0 ? 0 : (position - previous.position()) / span;
                return GbaColor.snap(
                        previous.color(palette).interpolate(next.color(palette), local));
            }
        }
        return stops.get(stops.size() - 1).color(palette);
    }

    @Override
    public String describe() {
        String shape = kind == Kind.RADIAL ? "Radial" : "Linear " + Math.round(angle) + "deg";
        return shape + ", " + stops.size() + " stops, "
                + (bands == 0 ? "smooth" : bands + " bands") + (dither ? " dithered" : "");
    }
}
