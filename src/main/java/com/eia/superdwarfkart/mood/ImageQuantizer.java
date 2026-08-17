package com.eia.superdwarfkart.mood;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The "GBA-ify" pass: turns any imported picture into something that belongs in this application.
 *
 * <p>Three steps, in this order and for a reason:
 *
 * <ol>
 *   <li><strong>Nearest-neighbour downscale</strong> to a base resolution - 240x160 by default,
 *       which is the actual Game Boy Advance screen. Nearest-neighbour rather than smooth, because
 *       the point is to <em>lose</em> detail into blocks; a smooth downscale produces a small
 *       photograph rather than a small picture.</li>
 *   <li><strong>Quantise to the mood's sixteen colours.</strong> Not to sixteen colours chosen from
 *       the image - to <em>the mood's</em> sixteen, which is what makes an imported photograph and
 *       a hand-drawn tile look like they came from the same machine. It is also what makes the
 *       import restyle itself when the palette changes.</li>
 *   <li><strong>Optional ordered dithering</strong> across the quantisation, on the same
 *       {@link Bayer} matrix the gradients use. Sixteen colours is few enough that a smooth sky
 *       comes out as three flat bands without it.</li>
 * </ol>
 *
 * <p>The processed copy is what gets stored. <strong>The original is never touched</strong>, and
 * never referenced either - see {@link ImageLayer} on why a mood carries its own images rather than
 * pointing at where they came from.
 *
 * <p>Nearest-colour matching is done in <em>Lab</em> rather than in RGB. In RGB a mid-grey is
 * closer to a saturated blue than to a slightly different grey, which is how a quantised sky comes
 * out mottled; {@link ColorMath#deltaE} is roughly uniform, so "nearest" means what it looks like it
 * should mean. Sixteen candidates per pixel over 240x160 is 614,400 comparisons, which happens once
 * at import and never in a render loop.
 */
public final class ImageQuantizer {

    /** The Game Boy Advance's own screen, and the default base resolution for an import. */
    public static final int GBA_WIDTH = 240;

    /** The Game Boy Advance's own screen height. */
    public static final int GBA_HEIGHT = 160;

    private ImageQuantizer() {
        throw new AssertionError("ImageQuantizer is a utility holder and must not be instantiated");
    }

    /**
     * Runs the whole pass at the GBA's own resolution.
     *
     * @param source  the imported picture; must not be {@code null}
     * @param palette the mood's palette; must not be {@code null}
     * @param dither  whether to scatter the quantisation boundaries
     * @return the processed copy
     */
    public static Image gbaify(Image source, Palette palette, boolean dither) {
        return gbaify(source, palette, dither, GBA_WIDTH, GBA_HEIGHT);
    }

    /**
     * Runs the whole pass at a chosen base resolution.
     *
     * @param source  the imported picture; must not be {@code null}
     * @param palette the mood's palette; must not be {@code null}
     * @param dither  whether to scatter the quantisation boundaries
     * @param width   base width in pixels
     * @param height  base height in pixels
     * @return the processed copy
     */
    public static Image gbaify(Image source, Palette palette, boolean dither, int width,
            int height) {
        Image scaled = downscale(source, width, height);
        return quantize(scaled, palette, dither);
    }

    /**
     * Reduces a picture to a base resolution by point sampling.
     *
     * <p>Never enlarges: a picture already smaller than the base is left alone, because blowing it
     * up here and then magnifying it again at draw time would double-scale it and lose the crisp
     * edges the whole exercise is about.
     *
     * @param source the picture; must not be {@code null}
     * @param width  the base width
     * @param height the base height
     * @return the reduced picture, or the original when it is already small enough
     */
    public static Image downscale(Image source, int width, int height) {
        int sourceWidth = (int) source.getWidth();
        int sourceHeight = (int) source.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return source;
        }
        // The aspect ratio is preserved rather than forced to the base's. A background stretched to
        // 3:2 on import can never be un-stretched, where a layer's own `fit` can still stretch it
        // at draw time if that is what the user wanted.
        double scale = Math.min(width / (double) sourceWidth, height / (double) sourceHeight);
        if (scale >= 1) {
            return source;
        }
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        PixelReader reader = source.getPixelReader();
        if (reader == null) {
            return source;
        }
        WritableImage out = new WritableImage(targetWidth, targetHeight);
        PixelWriter writer = out.getPixelWriter();
        for (int y = 0; y < targetHeight; y++) {
            // The centre of the source block, not its corner: sampling the corner shifts the whole
            // picture up and left by half a block, which on a 4x reduction is two source pixels.
            int sourceY = Math.min(sourceHeight - 1,
                    (int) ((y + 0.5) * sourceHeight / targetHeight));
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(sourceWidth - 1,
                        (int) ((x + 0.5) * sourceWidth / targetWidth));
                writer.setArgb(x, y, reader.getArgb(sourceX, sourceY));
            }
        }
        return out;
    }

    /**
     * Maps every pixel onto the nearest palette entry.
     *
     * <p>Transparency is preserved rather than quantised: a pixel that was transparent stays
     * transparent, because index 0 of a GBA bank is the transparency key and an imported PNG with a
     * cut-out background must keep it. Anything partly transparent is taken as opaque, since a
     * sixteen-colour bank has no alpha to spend.
     *
     * @param source  the picture; must not be {@code null}
     * @param palette the palette to map onto; must not be {@code null}
     * @param dither  whether to scatter the boundaries on the Bayer matrix
     * @return the quantised copy
     */
    public static Image quantize(Image source, Palette palette, boolean dither) {
        PixelReader reader = source.getPixelReader();
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (reader == null || width <= 0 || height <= 0) {
            return source;
        }

        // The candidates in Lab, computed once. Per pixel this is the difference between sixteen
        // conversions and none, over up to 38,400 pixels.
        Color[] candidates = new Color[PaletteRole.COUNT];
        double[][] labs = new double[PaletteRole.COUNT][];
        for (PaletteRole role : PaletteRole.values()) {
            candidates[role.ordinal()] = palette.color(role);
            labs[role.ordinal()] = ColorMath.lab(candidates[role.ordinal()]);
        }

        WritableImage out = new WritableImage(width, height);
        PixelWriter writer = out.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = reader.getColor(x, y);
                if (pixel.getOpacity() < 0.5) {
                    writer.setColor(x, y, Color.TRANSPARENT);
                    continue;
                }
                double[] lab = ColorMath.lab(pixel);
                writer.setColor(x, y, candidates[dither
                        ? dithered(lab, labs, x, y)
                        : nearest(lab, labs)]);
            }
        }
        return out;
    }

    /**
     * Which palette entry a pixel lands on once the dither has had its say.
     *
     * <p><strong>The dither is applied between the two nearest candidates rather than as a nudge to
     * the colour itself</strong>, and the first version of this got that wrong: nudging lightness by
     * a fixed amount before matching does nothing at all on an arbitrary sixteen-colour palette,
     * because the nearest-neighbour decision is dominated by chroma rather than by lightness. The
     * switch was a switch that changed no pixels, which is the worst kind - it looks like a feature.
     *
     * <p>The formulation here has no tuning constant in it. A pixel's <em>ratio</em> is how far it
     * sits from its nearest candidate as a fraction of the way to its second nearest: 0 when it
     * lands exactly on a palette colour, and 0.5 when it is exactly between two. It takes the second
     * candidate when that ratio exceeds its cell's Bayer threshold - so a pixel on a palette colour
     * never moves (flat areas stay flat), one half way between the two flips in half the cells, and
     * one a quarter of the way flips in a quarter of them. That is exactly what ordered dithering
     * is, stated once.
     *
     * @param lab  the pixel, already in Lab
     * @param labs the candidates, already in Lab
     * @param x    pixel column, for the dither pattern
     * @param y    pixel row
     * @return the index of the candidate to write
     */
    private static int dithered(double[] lab, double[][] labs, int x, int y) {
        int best = 0;
        int second = 0;
        double bestDistance = Double.MAX_VALUE;
        double secondDistance = Double.MAX_VALUE;
        for (int i = 0; i < labs.length; i++) {
            double distance = squaredDistance(lab, labs[i]);
            if (distance < bestDistance) {
                secondDistance = bestDistance;
                second = best;
                bestDistance = distance;
                best = i;
            } else if (distance < secondDistance) {
                secondDistance = distance;
                second = i;
            }
        }
        double near = Math.sqrt(bestDistance);
        double far = Math.sqrt(secondDistance);
        if (near + far <= 0) {
            return best;
        }
        return near / (near + far) > Bayer.threshold(x, y) ? second : best;
    }

    /**
     * Which palette entry a colour is nearest, in Lab.
     *
     * @param lab  the colour, already in Lab
     * @param labs the candidates, already in Lab
     * @return the index of the nearest candidate
     */
    private static int nearest(double[] lab, double[][] labs) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < labs.length; i++) {
            double distance = squaredDistance(lab, labs[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Squared CIE76 distance. Squared because only the ordering matters to the caller. */
    private static double squaredDistance(double[] lab, double[] other) {
        double dl = lab[0] - other[0];
        double da = lab[1] - other[1];
        double db = lab[2] - other[2];
        return dl * dl + da * da + db * db;
    }

    /**
     * Reads a picture into a tile's palette indices, for the pixel editor's PNG import.
     *
     * <p>Refuses rather than downscales anything over {@link PixelTile#MAX_IMPORT_SIZE}. A 512x512
     * illustration reduced to 16x16 is not a small version of itself, it is sixteen pixels of
     * mud - and handing that back as though it were the import working is worse than saying no.
     *
     * @param source the picture; must not be {@code null}
     * @param size   the tile size to import into
     * @param palette the palette to quantise against; must not be {@code null}
     * @return the indices, {@code size * size} of them in raster order
     * @throws IllegalArgumentException if the picture is larger than a tile can sensibly hold
     */
    public static int[] toTileIndices(Image source, int size, Palette palette) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (width > PixelTile.MAX_IMPORT_SIZE || height > PixelTile.MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("That image is " + width + "x" + height
                    + ". A tile import is at most " + PixelTile.MAX_IMPORT_SIZE + "x"
                    + PixelTile.MAX_IMPORT_SIZE + " - anything larger comes out unrecognisable, so "
                    + "it is refused rather than ruined.");
        }

        Image fitted = downscale(source, size, size);
        Image quantised = quantize(fitted, palette, false);
        PixelReader reader = quantised.getPixelReader();
        int[] indices = new int[size * size];
        if (reader == null) {
            return indices;
        }

        int fittedWidth = (int) quantised.getWidth();
        int fittedHeight = (int) quantised.getHeight();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (x >= fittedWidth || y >= fittedHeight) {
                    continue;
                }
                Color pixel = reader.getColor(x, y);
                indices[y * size + x] = pixel.getOpacity() < 0.5
                        ? PixelTile.TRANSPARENT
                        : indexOf(pixel, palette);
            }
        }
        return indices;
    }

    /**
     * Which role a colour was quantised onto.
     *
     * @param color   the colour; must not be {@code null}
     * @param palette the palette; must not be {@code null}
     * @return the role's ordinal, which is the tile's index
     */
    public static int indexOf(Color color, Palette palette) {
        double[] lab = ColorMath.lab(color);
        double[][] labs = new double[PaletteRole.COUNT][];
        for (PaletteRole role : PaletteRole.values()) {
            labs[role.ordinal()] = ColorMath.lab(palette.color(role));
        }
        return nearest(lab, labs);
    }
}
