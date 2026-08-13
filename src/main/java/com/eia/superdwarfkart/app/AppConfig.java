package com.eia.superdwarfkart.app;

import java.nio.file.Path;

/**
 * Central, immutable application configuration.
 *
 * <p>Every user-visible occurrence of the application name must come from one of the three
 * name constants below rather than from a hardcoded string literal, so that the name can
 * never drift between the window title, the title screen and the about box.
 */
public final class AppConfig {

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /**
     * The full display name, used for the window title, the README heading, the about box
     * and the fullscreen title screen.
     *
     * <p>The underscores and the hyphen are deliberate: the name is styled after a ROM
     * filename and pairs with the 8-bit font. It must never be reformatted into spaces or
     * title case.
     *
     * <p>At 44 characters in Press Start 2P this is several times the width of the mini
     * player window. Use {@link #APP_NAME_SHORT} anywhere width is constrained.
     */
    public static final String APP_NAME = "Super_Dwarf_Mario_Kart_Deluxe-Camel_Edition";

    /** Short name for the mini player and any other tight label. */
    public static final String APP_NAME_SHORT = "SDMK_Deluxe";

    /** Name of the per-user configuration and cache directory, created under the home directory. */
    public static final String APP_DIR = ".superdwarfkart";

    /** Application version, reported in the about box. */
    public static final String APP_VERSION = "1.0-SNAPSHOT";

    // ------------------------------------------------------------------
    // Resources bundled in the jar
    // ------------------------------------------------------------------

    /** Classpath location of the bundled 8-bit font. Never fetched at runtime: the app works offline. */
    public static final String FONT_RESOURCE = "/fonts/PressStart2P-Regular.ttf";

    /** Font family name as registered by JavaFX once the TTF above is loaded. */
    public static final String FONT_FAMILY = "Press Start 2P";

    /** Classpath location of the stylesheet. */
    public static final String STYLESHEET_RESOURCE = "/css/app.css";

    /** Classpath root that {@code AssetRegistry} scans for artwork. */
    public static final String ASSETS_RESOURCE_ROOT = "/assets";

    // ------------------------------------------------------------------
    // Audio
    // ------------------------------------------------------------------

    /** Playback sample rate, in Hz. The whole application assumes this one format. */
    public static final float SAMPLE_RATE = 44100f;

    /** Bits per sample. */
    public static final int SAMPLE_SIZE_BITS = 16;

    /** Channel count. Stereo is required: the level meters are per channel. */
    public static final int CHANNELS = 2;

    /** Bytes per frame: 2 channels x 16 bits. */
    public static final int BYTES_PER_FRAME = CHANNELS * SAMPLE_SIZE_BITS / 8;

    /** Bytes of PCM per second of audio at the format above. */
    public static final int BYTES_PER_SECOND = (int) SAMPLE_RATE * BYTES_PER_FRAME;

    /**
     * Decay applied to a displayed meter value each UI frame, giving the peak-hold caps their
     * slow fall: {@code displayed = max(newValue, displayed * PEAK_DECAY)}.
     */
    public static final float PEAK_DECAY = 0.85f;

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    /**
     * Version of the beat detection algorithm. Cached beatmaps are keyed by content hash
     * <em>and</em> this number, so incrementing it invalidates every previously cached map.
     *
     * <p>Bumped to 2 in M7, when each strong beat gained a strength - how far it stood above its
     * own surroundings. The beats themselves did not move; the game needs the strengths to know
     * which of them deserve a wall of obstacles, and a map without them cannot supply one.
     */
    public static final int ANALYZER_VERSION = 2;

    // ------------------------------------------------------------------
    // Windows
    // ------------------------------------------------------------------

    /**
     * Mini player width, in pixels: the width of the cartridge, which is the wider of the two things
     * in that window and therefore what sets its size.
     *
     * <p>{@link #APP_NAME} comes nowhere near fitting here - it is three times this wide - which is
     * why that window draws no application name at all.
     */
    public static final double MINI_WIDTH = 280;

    /**
     * Mini player height, in pixels.
     *
     * <p>The companion window is sized to its own content, and these two are the ceiling that
     * content is checked against rather than the size it is forced to. The smoke test prints the
     * measured size against both: overflow in a fixed-width pixel font is invisible to a unit test
     * and unmistakable in a picture.
     */
    public static final double MINI_HEIGHT = 440;

    /**
     * Initial fullscreen-mode window width, in pixels.
     *
     * <p>Wide on purpose: the whole interface runs in a fixed-width pixel font whose glyphs are
     * about one em across, so the same table needs considerably more room than it would in a
     * proportional font.
     */
    public static final double MAIN_WIDTH = 1440;

    /** Initial fullscreen-mode window height, in pixels. */
    public static final double MAIN_HEIGHT = 800;

    // ------------------------------------------------------------------
    // Per-user directories
    // ------------------------------------------------------------------

    /**
     * System property that relocates {@link #appHome()}, so the application can be run against
     * a scratch profile instead of the real one. Used for manual testing and demonstrations;
     * unset in normal use.
     */
    public static final String HOME_OVERRIDE_PROPERTY = "sdmk.home";

    /**
     * Returns the per-user application directory, {@code ~/.superdwarfkart}, or the directory
     * named by {@value #HOME_OVERRIDE_PROPERTY} when that property is set.
     *
     * @return the configuration and cache root; not guaranteed to exist yet
     */
    public static Path appHome() {
        String override = System.getProperty(HOME_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), APP_DIR);
    }

    /** @return directory holding cached beatmaps, one JSON file per analysed audio file */
    public static Path beatmapsDir() {
        return appHome().resolve("beatmaps");
    }

    /** @return the JSON file backing the song library */
    public static Path libraryFile() {
        return appHome().resolve("library.json");
    }

    /** @return the JSON file holding the best score per (song, speed class) */
    public static Path scoresFile() {
        return appHome().resolve("scores.json");
    }

    /**
     * Returns the user's own artwork folder.
     *
     * <p>Artwork found here overrides the artwork bundled in the jar, so new art can be dropped
     * in without rebuilding the application - which is how art actually arrives on this project.
     *
     * @return the assets folder; not guaranteed to exist yet
     */
    public static Path assetsDir() {
        return appHome().resolve("assets");
    }

    private AppConfig() {
        throw new AssertionError("AppConfig is a constant holder and must not be instantiated");
    }
}
