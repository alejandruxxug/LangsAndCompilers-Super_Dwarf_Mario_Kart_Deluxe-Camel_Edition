package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Loads and hands out the bundled 8-bit font.
 *
 * <p>The TTF ships inside the jar and is never fetched at runtime, so the application works
 * offline. If loading fails the application still runs: every accessor falls back to a system
 * font and a warning is logged once.
 */
public final class Fonts {

    private static final Logger LOG = Logger.getLogger(Fonts.class.getName());

    private static boolean loaded;
    private static boolean loadAttempted;

    /**
     * Registers the bundled font with JavaFX. Safe to call more than once; the work happens
     * on the first call only.
     *
     * @return {@code true} if the 8-bit font is available, {@code false} if the fallback is in use
     */
    public static synchronized boolean load() {
        if (loadAttempted) {
            return loaded;
        }
        loadAttempted = true;

        try (InputStream in = Fonts.class.getResourceAsStream(AppConfig.FONT_RESOURCE)) {
            if (in == null) {
                LOG.warning("Bundled font not found on the classpath at " + AppConfig.FONT_RESOURCE
                        + " - falling back to a system font");
                return false;
            }
            // A size must be given here, but any size registers the whole family.
            Font font = Font.loadFont(in, 12);
            if (font == null) {
                LOG.warning("JavaFX refused to load the bundled font at " + AppConfig.FONT_RESOURCE
                        + " - falling back to a system font");
                return false;
            }
            loaded = true;
            LOG.info("Loaded bundled font: " + font.getFamily());
        } catch (Exception e) {
            LOG.warning("Failed to load the bundled font: " + e);
        }
        return loaded;
    }

    /**
     * Returns the 8-bit font at the requested size, or a monospaced system font of the same
     * size if the bundled font could not be loaded.
     *
     * @param size point size
     * @return a usable font, never {@code null}
     */
    public static Font pixel(double size) {
        Font cached = BY_SIZE.get(size);
        if (cached != null) {
            return cached;
        }
        Font font = load()
                ? Font.font(AppConfig.FONT_FAMILY, size)
                : Font.font("Monospaced", size);
        BY_SIZE.put(size, font);
        return font;
    }

    /**
     * Every font handed out so far, by size.
     *
     * <p>The canvases ask for a font several times per repaint - the runner's head-up display alone
     * wants four sizes every frame - and each of those calls otherwise takes the monitor on
     * {@link #load()} and goes back through the platform's font lookup. Sizes here are whole pixels
     * and there are under a dozen of them in the whole interface, so this fills once and never
     * grows again.
     *
     * <p>Concurrent because a font is asked for from the interface thread and, during a smoke test,
     * from whatever thread is taking a snapshot.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Double, Font> BY_SIZE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Returns how wide one character actually is at a size, in pixels.
     *
     * <p>The whole interface is laid out on the rule that this font advances about one em per glyph,
     * and for deciding how many characters to <em>shorten</em> a caption to that is close enough -
     * being a glyph out costs three dots nobody counts. It is not close enough for text that has to
     * fit outright, like the companion window's scrolling title: there the difference between the
     * nominal em and the real advance is the difference between a title that scrolls and one the
     * label truncates a second time, and the second truncation looks exactly like a bug.
     *
     * <p>Measured once per size and remembered. Needs a toolkit, so callers must already be on the
     * interface thread - which anything laying out text is.
     *
     * @param size point size
     * @return the advance width of one character, falling back to the size itself if it cannot be
     *         measured
     */
    public static double advance(double size) {
        Double cached = ADVANCE_BY_SIZE.get(size);
        if (cached != null) {
            return cached;
        }
        double measured = size;
        try {
            // The difference between two lengths, not one length divided by its count. Layout
            // bounds are the *ink*, so a single measurement is short by the first glyph's left
            // bearing and the last one's right - which underestimates the advance, hands out a
            // budget a character or two too generous, and truncates the very text this exists to
            // fit. Subtracting one from the other cancels both ends.
            javafx.scene.text.Text shorter = new javafx.scene.text.Text("M".repeat(10));
            javafx.scene.text.Text longer = new javafx.scene.text.Text("M".repeat(20));
            shorter.setFont(pixel(size));
            longer.setFont(pixel(size));
            double difference = longer.getLayoutBounds().getWidth()
                    - shorter.getLayoutBounds().getWidth();
            if (difference > 0) {
                measured = difference / 10;
            }
        } catch (Exception e) {
            LOG.warning("Could not measure the pixel font at " + size + "px: " + e
                    + " - falling back to one em per glyph");
        }
        ADVANCE_BY_SIZE.put(size, measured);
        return measured;
    }

    /** Measured advance widths, by size. See {@link #advance(double)}. */
    private static final java.util.concurrent.ConcurrentHashMap<Double, Double> ADVANCE_BY_SIZE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** @return whether the bundled 8-bit font is in use rather than the fallback */
    public static boolean isPixelFontAvailable() {
        return load();
    }

    private Fonts() {
        throw new AssertionError("Fonts is a utility holder and must not be instantiated");
    }
}
