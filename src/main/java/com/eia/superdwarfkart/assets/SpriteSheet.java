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

    /** Mean channel value below which a pixel counts as part of a dark panel. */
    private static final int DARK_LUMINANCE = 60;

    /** Smallest share of a frame a dark region may cover and still be treated as a panel. */
    private static final double MIN_PANEL_SHARE = 0.2;

    /**
     * Largest share of a frame a dark region may cover: past this it is the artwork, not a panel.
     *
     * <p>Deliberately well under the whole frame. The magenta placeholder is drawn on a near-black
     * ground covering 88% of itself, which at a looser bar is reported as a perfectly good panel -
     * and a window that laid its contents into it would be arranging them over a missing-artwork
     * marker. A panel is something a picture has room for <em>beside</em>; the real cartridge's
     * label is 32% of its frame.
     */
    private static final double MAX_PANEL_SHARE = 0.7;

    /** How much of its own bounding box a panel's dark pixels must actually fill. */
    private static final double MIN_PANEL_FILL = 0.9;

    private final Image image;
    private final int frameCount;
    private final double frameWidth;
    private final double frameHeight;
    private final boolean placeholder;

    /** Where the ink is in each frame, measured on demand. See {@link #opaqueBounds(int)}. */
    private final Rectangle2D[] opaqueBounds;

    /** Where each frame's dark panel is, measured on demand. See {@link #darkRegion(int)}. */
    private final java.util.Optional<Rectangle2D>[] darkRegions;

    @SuppressWarnings("unchecked")
    private SpriteSheet(Image image, int frameCount, double frameWidth, double frameHeight,
                        boolean placeholder) {
        this.image = image;
        this.frameCount = frameCount;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.placeholder = placeholder;
        this.opaqueBounds = new Rectangle2D[frameCount];
        this.darkRegions = new java.util.Optional[frameCount];
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

    /**
     * Returns where the ink actually is inside a frame, ignoring the transparent margin.
     *
     * <p><strong>A frame is not the same shape as what is drawn in it,</strong> and any view that
     * has to stand one sprite on another needs to know the difference. The spinning disk occupies
     * the lower 60% of its frame and nothing at all above that; the racer's driving frames leave 14
     * transparent rows above the kart and 10 below it. Laying those out by frame rectangle puts the
     * kart in the air over a record whose top edge is nowhere near where the frame says it is.
     *
     * <p>Measured rather than written down, for the same reason the frame count is inferred rather
     * than tabulated: filenames, frame counts and now margins are all facts about artwork that
     * arrives after the code does, and this package is where that knowledge belongs. New art with a
     * different margin needs no change anywhere.
     *
     * <p>Computed once per frame and remembered. One frame of a sheet this size is a thousand pixel
     * reads, so this is cheap even done for every frame - but it must never be called per repaint.
     *
     * @param frameIndex frame to measure; wrapped into range
     * @return the opaque bounds in frame-local coordinates, or the whole frame when it is empty or
     *         cannot be read
     */
    public Rectangle2D opaqueBounds(int frameIndex) {
        int index = Math.floorMod(frameIndex, frameCount);
        Rectangle2D cached = opaqueBounds[index];
        if (cached != null) {
            return cached;
        }

        Rectangle2D whole = new Rectangle2D(0, 0, frameWidth, frameHeight);
        javafx.scene.image.PixelReader reader = image.getPixelReader();
        if (reader == null) {
            opaqueBounds[index] = whole;
            return whole;
        }

        int originX = (int) Math.round(index * frameWidth);
        int width = (int) Math.round(frameWidth);
        int height = (int) Math.round(frameHeight);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((reader.getArgb(originX + x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        // A frame with nothing in it is answered with the whole frame rather than an empty
        // rectangle, so a caller anchoring to it still gets a usable position instead of a
        // collapsed one.
        Rectangle2D bounds = maxX < 0
                ? whole
                : new Rectangle2D(minX, minY, maxX - minX + 1d, maxY - minY + 1d);
        opaqueBounds[index] = bounds;
        return bounds;
    }

    /**
     * Returns the span of a frame's bottom edge - where the thing drawn in it meets the ground.
     *
     * <p>Not the same as the width of {@link #opaqueBounds(int)}, and the difference is the point:
     * an object narrower at its foot than at its shoulders - a cartridge, a bottle, a trophy - has a
     * footprint, and anything meant to line up with what it is standing on has to line up with
     * <em>that</em> rather than with its widest part. The cartridge steps in by 27 pixels a side for
     * its bottom section, so a record matched to its full width would be visibly too wide for the
     * part actually resting on it.
     *
     * <p>Read off the lowest row with anything in it, which is literally where it touches down.
     *
     * @param frameIndex frame to measure; wrapped into range
     * @return the opaque span of the bottom row, in frame-local coordinates, or empty for an empty
     *         frame
     */
    public java.util.Optional<Rectangle2D> footprint(int frameIndex) {
        int index = Math.floorMod(frameIndex, frameCount);
        javafx.scene.image.PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return java.util.Optional.empty();
        }

        int originX = (int) Math.round(index * frameWidth);
        int width = (int) Math.round(frameWidth);
        int height = (int) Math.round(frameHeight);
        for (int y = height - 1; y >= 0; y--) {
            int minX = -1;
            int maxX = -1;
            for (int x = 0; x < width; x++) {
                if ((reader.getArgb(originX + x, y) >>> 24) == 0) {
                    continue;
                }
                if (minX < 0) {
                    minX = x;
                }
                maxX = x;
            }
            if (minX >= 0) {
                return java.util.Optional.of(
                        new Rectangle2D(minX, y, maxX - minX + 1d, 1));
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Returns the dark panel inside a frame - the label on a cartridge, the screen on a console.
     *
     * <p>Artwork of this kind is a picture of an object with somewhere to <em>put</em> something,
     * and a view laying its own content into that space has to know where it is. Measuring it is the
     * same bargain as inferring frame counts from dimensions: the alternative is four numbers copied
     * out of an image editor into a class that has no way to notice when the art is replaced.
     *
     * <p>The bounding box of everything below {@value #DARK_LUMINANCE}, which for a solid panel on a
     * lighter body is the panel. It is <strong>refused</strong> rather than guessed at when the
     * result is not panel-shaped - under a fifth of the frame, over nine tenths of it, or a box the
     * dark pixels do not actually fill - because a dark outline round the whole sprite produces a
     * bounding box of the entire frame, and content laid into that would sit on the artwork rather
     * than in its panel. A caller that gets nothing back is expected to fall back to a layout of its
     * own.
     *
     * <p>Computed once per frame and remembered. Never call it per repaint.
     *
     * @param frameIndex frame to measure; wrapped into range
     * @return the panel in frame-local coordinates, or empty when the frame has no panel in it
     */
    public java.util.Optional<Rectangle2D> darkRegion(int frameIndex) {
        int index = Math.floorMod(frameIndex, frameCount);
        if (darkRegions[index] == null) {
            darkRegions[index] = java.util.Optional.ofNullable(measureDarkRegion(index));
        }
        return darkRegions[index];
    }

    /**
     * @param index frame to measure, already in range
     * @return the dark panel, or {@code null} when the frame does not have one
     */
    private Rectangle2D measureDarkRegion(int index) {
        javafx.scene.image.PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return null;
        }

        int originX = (int) Math.round(index * frameWidth);
        int width = (int) Math.round(frameWidth);
        int height = (int) Math.round(frameHeight);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        long darkPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!isDark(reader.getArgb(originX + x, y))) {
                    continue;
                }
                darkPixels++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return null;
        }

        double area = (maxX - minX + 1d) * (maxY - minY + 1d);
        double frameArea = (double) width * height;
        // Panel-shaped, or nothing: a thin border, a scattering of dark detail and an outline round
        // the whole sprite all produce a box, and none of them is somewhere to put content.
        if (area < frameArea * MIN_PANEL_SHARE
                || area > frameArea * MAX_PANEL_SHARE
                || darkPixels < area * MIN_PANEL_FILL) {
            return null;
        }
        return new Rectangle2D(minX, minY, maxX - minX + 1d, maxY - minY + 1d);
    }

    /**
     * @param argb a pixel
     * @return whether it is opaque and dark enough to be part of a panel
     */
    private static boolean isDark(int argb) {
        if ((argb >>> 24) == 0) {
            return false;
        }
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (red + green + blue) / 3 < DARK_LUMINANCE;
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
