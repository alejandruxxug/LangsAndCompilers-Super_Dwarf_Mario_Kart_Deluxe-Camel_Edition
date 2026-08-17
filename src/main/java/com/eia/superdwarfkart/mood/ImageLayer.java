package com.eia.superdwarfkart.mood;

import java.util.Locale;
import java.util.Objects;

/**
 * A picture: something imported, or something drawn in the pixel editor and saved out.
 *
 * <p><strong>The file is named, never pathed.</strong> {@code fileName} is relative to the mood's
 * own folder, and importing an image <em>copies</em> it there rather than referencing where it came
 * from. A mood has to survive the source file being moved, renamed or deleted, and it has to
 * survive being zipped up and handed to a teammate - both of which an absolute path fails at
 * silently, leaving a mood that looks perfect on the machine it was built on and shows nothing
 * anywhere else.
 *
 * <p>Animated GIFs are supported because JavaFX decodes their frames natively and
 * {@code drawImage} blits whichever one is current - so a two-frame twinkling background costs the
 * renderer nothing beyond noticing that it cannot cache the layer.
 *
 * @param style      band, opacity, blend, scroll and visibility
 * @param fileName   the image's name inside the mood's folder
 * @param fit        how it is laid over the canvas
 * @param pixelScale integer magnification applied before fitting; 1 leaves the artwork alone
 * @param animated   whether the file is a GIF whose frames advance
 */
public record ImageLayer(LayerStyle style, String fileName, Fit fit, int pixelScale,
        boolean animated) implements MoodLayer {

    /** Largest magnification offered. A 16x16 tile at 8x is 128 pixels, which is a large tile. */
    public static final int MAX_PIXEL_SCALE = 8;

    /** How a picture is laid over the canvas. */
    public enum Fit {

        /**
         * Repeated across the whole canvas.
         *
         * <p>The default for anything the pixel editor produced, and the reason the editor has a
         * 3x3 preview: a seam is invisible on one tile and unmissable once it fills a screen.
         */
        TILE("Tile"),

        /** Stretched to the canvas, ignoring the artwork's own proportions. */
        STRETCH("Stretch"),

        /** Scaled until it fits entirely, leaving bars where the proportions differ. */
        CONTAIN("Contain"),

        /** Scaled until it covers, cropping whichever axis is longer. */
        COVER("Cover"),

        /** Drawn once at its own size, in the middle. */
        CENTER("Center");

        private final String displayName;

        Fit(String displayName) {
            this.displayName = displayName;
        }

        /** @return the caption shown in the customizer */
        public String displayName() {
            return displayName;
        }

        /**
         * Reads a stored fit, tolerating anything a later version might have written.
         *
         * @param name the stored name; {@code null} or unknown yields {@link #TILE}
         * @return the fit
         */
        public static Fit byName(String name) {
            if (name != null) {
                for (Fit value : values()) {
                    if (value.name().equalsIgnoreCase(name.strip())) {
                        return value;
                    }
                }
            }
            return TILE;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * @throws IllegalArgumentException if the file name is blank or escapes the mood's folder
     */
    public ImageLayer {
        Objects.requireNonNull(fileName, "fileName must not be null");
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("An image layer must name a file");
        }
        // A mood folder is unzipped from something a teammate sent, so the file name in it is
        // untrusted input. "../../.ssh/id_rsa" is not a picture and must not be resolvable.
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException(
                    "An image layer names a file inside its own mood folder, not a path: "
                            + fileName);
        }
        if (style == null) {
            style = LayerStyle.behind();
        }
        if (fit == null) {
            fit = Fit.TILE;
        }
        pixelScale = Math.clamp(pixelScale, 1, MAX_PIXEL_SCALE);
    }

    /**
     * A tiled wallpaper at 1:1 - what saving a tile out of the pixel editor produces.
     *
     * @param fileName the image's name inside the mood's folder
     * @return the layer
     */
    public static ImageLayer tiled(String fileName) {
        return new ImageLayer(LayerStyle.behind(), fileName, Fit.TILE, 1, isGif(fileName));
    }

    /**
     * Whether a file name looks like an animated format.
     *
     * @param fileName the name to inspect
     * @return {@code true} for a GIF
     */
    public static boolean isGif(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    @Override
    public MoodLayer withStyle(LayerStyle newStyle) {
        return new ImageLayer(newStyle, fileName, fit, pixelScale, animated);
    }

    /**
     * Returns a copy laid over the canvas a different way.
     *
     * @param newFit the wanted fit
     * @return the new layer
     */
    public ImageLayer withFit(Fit newFit) {
        return new ImageLayer(style, fileName, newFit, pixelScale, animated);
    }

    /**
     * Returns a copy magnified differently.
     *
     * @param scale integer magnification, 1 to {@value #MAX_PIXEL_SCALE}
     * @return the new layer
     */
    public ImageLayer withPixelScale(int scale) {
        return new ImageLayer(style, fileName, fit, scale, animated);
    }

    /**
     * An animated GIF has to be re-blitted every frame, so it can never join the flattened
     * backdrop.
     */
    @Override
    public boolean isAnimated() {
        return animated;
    }

    @Override
    public String describe() {
        return fileName + ", " + fit.displayName().toLowerCase(Locale.ROOT)
                + (pixelScale > 1 ? " at " + pixelScale + "x" : "")
                + (animated ? ", animated" : "");
    }
}
