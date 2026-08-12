package com.eia.superdwarfkart.assets;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

/**
 * A horizontal strip of equally sized animation frames.
 *
 * <p>Frames are addressed by a viewport rectangle rather than copied out, so an animation costs
 * one image in memory no matter how many frames it has and switching frames is just moving a
 * rectangle.
 *
 * <p><strong>A missing sheet is never an error.</strong> Loading falls back to a magenta
 * placeholder and logs a warning once, because the application has to stay usable with no
 * artwork present at all. Artwork arrives incrementally.
 *
 * <p>This is the loader only. Deciding <em>which</em> file to load, and how many frames it holds,
 * belongs to {@link AssetRegistry}: filenames are not known in advance, so nothing outside that
 * class should name an image file. The frame rule applied here - use the expected count when one
 * is known, otherwise assume square frames - is what the registry relies on to slice a sheet it
 * has never seen before.
 */
public final class SpriteSheet {

    private static final Logger LOG = Logger.getLogger(SpriteSheet.class.getName());

    /** Size of the generated placeholder, in pixels. */
    private static final int PLACEHOLDER_SIZE = 32;

    private final Image image;
    private final int frameCount;
    private final double frameWidth;
    private final double frameHeight;
    private final boolean placeholder;

    private SpriteSheet(Image image, int frameCount, double frameWidth, double frameHeight,
                        boolean placeholder) {
        this.image = image;
        this.frameCount = frameCount;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.placeholder = placeholder;
    }

    /**
     * Loads a sheet from the classpath.
     *
     * @param resourcePath  classpath location, for example {@code /assets/textures/Sprites/Star.png}
     * @param expectedFrames how many frames the strip holds, or a value below one to infer square frames
     * @return the loaded sheet, or a single-frame magenta placeholder if it could not be read
     */
    public static SpriteSheet load(String resourcePath, int expectedFrames) {
        URL location = SpriteSheet.class.getResource(resourcePath);
        if (location == null) {
            LOG.warning("Sprite sheet not found on the classpath: " + resourcePath
                    + " - drawing a placeholder instead");
            return placeholder();
        }
        return load(location, expectedFrames, resourcePath);
    }

    /**
     * Loads a sheet from anywhere it can be read: the jar, or a file the user dropped in.
     *
     * @param location       where the image bytes are
     * @param expectedFrames how many frames the strip holds, or a value below one to infer square frames
     * @param describedAs    name used in log messages, so a warning points at something recognisable
     * @return the loaded sheet, or a single-frame magenta placeholder if it could not be read
     */
    public static SpriteSheet load(URL location, int expectedFrames, String describedAs) {
        try (InputStream in = location.openStream()) {
            Image image = new Image(in);
            if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
                LOG.warning("Sprite sheet could not be decoded: " + describedAs
                        + " - drawing a placeholder instead");
                return placeholder();
            }

            // Known frame count divides the width; otherwise frames are assumed square. Square
            // inference is what handles art that arrives without anyone saying how long it is:
            // a 416x32 strip is 13 frames, a 288x32 strip is 9.
            int frames = expectedFrames > 0
                    ? expectedFrames
                    : Math.max(1, (int) Math.round(image.getWidth() / image.getHeight()));
            double width = image.getWidth() / frames;
            return new SpriteSheet(image, frames, width, image.getHeight(), false);
        } catch (Exception e) {
            LOG.warning("Failed to load the sprite sheet " + describedAs + ": " + e);
            return placeholder();
        }
    }

    /**
     * Builds the stand-in used whenever artwork is missing: a magenta block crossed through, so
     * an absent sprite is loud on screen rather than an invisible gap.
     *
     * @return a single-frame placeholder sheet
     */
    static SpriteSheet placeholder() {
        WritableImage image = new WritableImage(PLACEHOLDER_SIZE, PLACEHOLDER_SIZE);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < PLACEHOLDER_SIZE; y++) {
            for (int x = 0; x < PLACEHOLDER_SIZE; x++) {
                boolean edge = x == 0 || y == 0
                        || x == PLACEHOLDER_SIZE - 1 || y == PLACEHOLDER_SIZE - 1;
                boolean cross = x == y || x == PLACEHOLDER_SIZE - 1 - y;
                writer.setColor(x, y, edge || cross
                        ? javafx.scene.paint.Color.web("#ff00ff")
                        : javafx.scene.paint.Color.web("#2a0a2a"));
            }
        }
        return new SpriteSheet(image, 1, PLACEHOLDER_SIZE, PLACEHOLDER_SIZE, true);
    }

    /**
     * Returns the region of the sheet holding one frame.
     *
     * @param frameIndex frame to show; wrapped into range, so a caller may pass a free-running counter
     * @return the viewport rectangle for that frame
     */
    public Rectangle2D viewport(int frameIndex) {
        int index = Math.floorMod(frameIndex, frameCount);
        return new Rectangle2D(index * frameWidth, 0, frameWidth, frameHeight);
    }

    /** @return the underlying image, shared by every frame */
    public Image image() {
        return image;
    }

    /** @return how many frames the strip holds */
    public int frameCount() {
        return frameCount;
    }

    /** @return the width of one frame, in pixels */
    public double frameWidth() {
        return frameWidth;
    }

    /** @return the height of one frame, in pixels */
    public double frameHeight() {
        return frameHeight;
    }

    /** @return whether this is the stand-in rather than real artwork */
    public boolean isPlaceholder() {
        return placeholder;
    }
}
