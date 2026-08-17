package com.eia.superdwarfkart.mood;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-drawn tile, stored as <strong>palette indices rather than as colours</strong>.
 *
 * <p>That one decision is what makes the pixel editor worth building, and everything good about it
 * falls out of the data model rather than out of any code:
 *
 * <ul>
 *   <li>The editor's colour picker <em>is</em> the mood's palette, so nothing drawn can be out of
 *       palette or off the GBA grid. The constraint is structural rather than validated afterwards,
 *       which means there is no failure mode to handle.</li>
 *   <li><strong>Changing the palette recolours every tile in the mood, instantly.</strong> Import a
 *       Lospec palette and a background somebody drew by hand restyles itself. Store RGB instead
 *       and that is gone - which is the single most valuable property here, and it costs nothing.</li>
 *   <li>One pixel is one hex digit, so the file is human-readable and diffs line by line.</li>
 * </ul>
 *
 * <p><strong>Index 0 is transparent</strong>, matching the GBA convention where entry 0 of a bank is
 * the transparency key. A tile that wants the background colour draws it explicitly with the index
 * of {@link PaletteRole#BACKGROUND}; there is no way to say "opaque nothing", and there does not
 * need to be.
 *
 * <p>Immutable, like everything else in this package. The editor keeps its own working buffer and
 * builds one of these when it commits, which is also what makes undo a stack of these rather than a
 * log of strokes.
 */
public final class PixelTile {

    /** The sizes offered. Tiles, not paintings - nothing larger is useful as a repeating layer. */
    public static final int[] SIZES = {8, 16, 32};

    /** Largest tile that may be imported, in either axis, before the import is refused outright. */
    public static final int MAX_IMPORT_SIZE = 64;

    /** Most frames a tile may hold. */
    public static final int MAX_FRAMES = 4;

    /** The index that draws nothing, by GBA convention. */
    public static final int TRANSPARENT = 0;

    /** Frames per second unless the tile says otherwise. */
    public static final double DEFAULT_FPS = 4;

    private final int size;
    private final double fps;
    private final List<int[]> frames;

    /**
     * Builds a tile from raw index data.
     *
     * @param size   width and height in pixels; one of {@link #SIZES}
     * @param fps    how fast the frames advance
     * @param frames one entry per frame, each {@code size * size} indices in raster order
     * @throws IllegalArgumentException if the size, the frame count or a frame's length is wrong
     */
    public PixelTile(int size, double fps, List<int[]> frames) {
        if (!isValidSize(size)) {
            throw new IllegalArgumentException("A tile is 8, 16 or 32 pixels square, not " + size);
        }
        if (frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) {
            throw new IllegalArgumentException("A tile holds 1 to " + MAX_FRAMES + " frames, not "
                    + (frames == null ? 0 : frames.size()));
        }
        List<int[]> copies = new ArrayList<>(frames.size());
        for (int[] frame : frames) {
            if (frame == null || frame.length != size * size) {
                throw new IllegalArgumentException("A " + size + "x" + size + " frame holds "
                        + (size * size) + " pixels, not "
                        + (frame == null ? 0 : frame.length));
            }
            int[] copy = new int[frame.length];
            for (int i = 0; i < frame.length; i++) {
                // Clamped rather than rejected. A hand-edited file with a stray digit should draw
                // the tile it mostly describes, not refuse to open the mood (ground rule 5).
                copy[i] = Math.clamp(frame[i], 0, PaletteRole.COUNT - 1);
            }
            copies.add(copy);
        }
        this.size = size;
        this.fps = fps <= 0 ? DEFAULT_FPS : fps;
        this.frames = List.copyOf(copies);
    }

    /**
     * An empty tile: every pixel transparent.
     *
     * @param size width and height in pixels
     * @return the tile
     */
    public static PixelTile blank(int size) {
        return new PixelTile(size, DEFAULT_FPS, List.of(new int[size * size]));
    }

    /**
     * Reads a tile from its stored form: one hex digit per pixel, one string per row.
     *
     * @param size   width and height in pixels
     * @param fps    how fast the frames advance
     * @param frames one list of rows per frame
     * @return the tile
     * @throws IllegalArgumentException if a row is the wrong length or holds a non-hex character
     */
    public static PixelTile fromRows(int size, double fps, List<List<String>> frames) {
        List<int[]> decoded = new ArrayList<>();
        for (List<String> rows : frames) {
            if (rows == null || rows.size() != size) {
                throw new IllegalArgumentException(
                        "A " + size + "-pixel frame has " + size + " rows, not "
                                + (rows == null ? 0 : rows.size()));
            }
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                String row = rows.get(y);
                if (row == null || row.length() != size) {
                    throw new IllegalArgumentException("Row " + y + " is "
                            + (row == null ? 0 : row.length()) + " pixels wide, not " + size);
                }
                for (int x = 0; x < size; x++) {
                    int digit = Character.digit(row.charAt(x), 16);
                    if (digit < 0) {
                        throw new IllegalArgumentException("Row " + y + " holds '" + row.charAt(x)
                                + "', which is not a palette index");
                    }
                    pixels[y * size + x] = digit;
                }
            }
            decoded.add(pixels);
        }
        return new PixelTile(size, fps, decoded);
    }

    /** @return width and height in pixels */
    public int size() {
        return size;
    }

    /** @return how fast the frames advance */
    public double fps() {
        return fps;
    }

    /** @return how many frames the tile holds */
    public int frameCount() {
        return frames.size();
    }

    /**
     * The palette index at a pixel.
     *
     * @param frame which frame
     * @param x     column
     * @param y     row
     * @return the index, 0 to 15
     */
    public int indexAt(int frame, int x, int y) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return TRANSPARENT;
        }
        return frames.get(Math.floorMod(frame, frames.size()))[y * size + x];
    }

    /**
     * Which frame is showing at a moment.
     *
     * <p>The caller supplies the time, exactly as {@code SpriteAnimation} requires: a tile that
     * counted its own ticks would be a second clock in an application whose whole timing argument
     * is that there is one.
     *
     * @param seconds elapsed time
     * @return the frame index
     */
    public int frameAt(double seconds) {
        if (frames.size() == 1) {
            return 0;
        }
        return Math.floorMod((int) Math.floor(seconds * fps), frames.size());
    }

    /**
     * Returns a copy with one pixel changed.
     *
     * @param frame which frame
     * @param x     column
     * @param y     row
     * @param index the palette index to write, or {@link #TRANSPARENT}
     * @return the new tile, or this one when the pixel is outside it
     */
    public PixelTile withPixel(int frame, int x, int y, int index) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return this;
        }
        List<int[]> copy = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            int[] pixels = frames.get(i).clone();
            if (i == Math.floorMod(frame, frames.size())) {
                pixels[y * size + x] = Math.clamp(index, 0, PaletteRole.COUNT - 1);
            }
            copy.add(pixels);
        }
        return new PixelTile(size, fps, copy);
    }

    /**
     * Returns a copy with a whole frame replaced.
     *
     * @param frame  which frame
     * @param pixels {@code size * size} indices in raster order
     * @return the new tile
     */
    public PixelTile withFrame(int frame, int[] pixels) {
        List<int[]> copy = new ArrayList<>(frames);
        copy.set(Math.floorMod(frame, frames.size()), pixels);
        return new PixelTile(size, fps, copy);
    }

    /**
     * Returns a copy with one more frame, cloned from the last one.
     *
     * @return the new tile, or this one when it is already full
     */
    public PixelTile withFrameAdded() {
        if (frames.size() >= MAX_FRAMES) {
            return this;
        }
        List<int[]> copy = new ArrayList<>(frames);
        copy.add(frames.get(frames.size() - 1).clone());
        return new PixelTile(size, fps, copy);
    }

    /**
     * Returns a copy with the last frame removed.
     *
     * @return the new tile, or this one when only one frame is left
     */
    public PixelTile withFrameRemoved() {
        if (frames.size() <= 1) {
            return this;
        }
        List<int[]> copy = new ArrayList<>(frames);
        copy.remove(copy.size() - 1);
        return new PixelTile(size, fps, copy);
    }

    /**
     * Returns a copy running at a different rate.
     *
     * @param newFps frames per second
     * @return the new tile
     */
    public PixelTile withFps(double newFps) {
        return new PixelTile(size, newFps, frames);
    }

    /**
     * Returns one frame's pixels, for an editor to work on.
     *
     * @param frame which frame
     * @return a copy of the frame's indices
     */
    public int[] pixels(int frame) {
        return frames.get(Math.floorMod(frame, frames.size())).clone();
    }

    /**
     * Renders the tile as an image, resolving indices through a palette.
     *
     * <p>At 1:1. The renderer and the editor magnify by an integer factor with smoothing off, which
     * is ground rule 8 - a tile is hand-drawn pixel art and interpolating it is what turns it to
     * mush.
     *
     * @param frame   which frame
     * @param palette the palette to resolve indices against; must not be {@code null}
     * @return an image {@link #size()} pixels square, with index 0 fully transparent
     */
    public Image toImage(int frame, Palette palette) {
        WritableImage image = new WritableImage(size, size);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = indexAt(frame, x, y);
                writer.setColor(x, y, index == TRANSPARENT
                        ? javafx.scene.paint.Color.TRANSPARENT
                        : palette.color(PaletteRole.values()[index]));
            }
        }
        return image;
    }

    /**
     * The stored form: one hex digit per pixel, one string per row, one list per frame.
     *
     * @return the rows, ready for the mood file
     */
    public List<List<String>> rows() {
        List<List<String>> out = new ArrayList<>(frames.size());
        for (int[] pixels : frames) {
            List<String> rows = new ArrayList<>(size);
            for (int y = 0; y < size; y++) {
                StringBuilder row = new StringBuilder(size);
                for (int x = 0; x < size; x++) {
                    row.append(Character.forDigit(pixels[y * size + x], 16));
                }
                rows.add(row.toString());
            }
            out.add(rows);
        }
        return out;
    }

    /**
     * Whether a size is one the editor offers.
     *
     * @param size the size to check
     * @return whether a tile may be that size
     */
    public static boolean isValidSize(int size) {
        for (int allowed : SIZES) {
            if (allowed == size) {
                return true;
            }
        }
        return false;
    }

    /** @return whether every pixel is transparent */
    public boolean isBlank() {
        for (int[] pixels : frames) {
            for (int index : pixels) {
                if (index != TRANSPARENT) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "PixelTile[" + size + "x" + size + ", " + frames.size() + " frames]";
    }
}
