package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.Song;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Where a song's cover comes from, whichever kind of song it is.
 *
 * <p>A local song carries a {@link Path} to an image on disk; a streamed one carries a URL, because
 * importing a playlist must not cost one download per row before anything is shown. Both views that
 * draw a cover - the library's details panel and the companion window - used to read the path
 * alone, so <strong>every Spotify track showed the "no artwork" placeholder</strong> while its
 * address sat in the song object unused. This is the one place that knows about both.
 *
 * <p><strong>A remote image is loaded in the background and never on the interface thread.</strong>
 * JavaFX will happily fetch a URL synchronously inside {@code new Image(...)}, which freezes the
 * window, the meters and the runner for as long as the network takes - and the failure mode of a
 * slow server is an application that appears to have hung. The consequence is that the image has no
 * dimensions yet when it is handed back, so the centre-crop cannot be computed until it arrives;
 * {@link #fit} is what waits.
 *
 * <p>Results are cached, because the details panel repopulates on every selection change and the
 * companion window on every song: without it, clicking up and down a list would re-fetch the same
 * artwork once per keystroke.
 */
public final class CoverArt {

    private static final Logger LOG = Logger.getLogger(CoverArt.class.getName());

    /**
     * How many decoded covers to keep.
     *
     * <p>Bounded rather than unbounded: these are full images, and a library of any size would
     * otherwise hold every cover the user has ever selected for the life of the session.
     */
    private static final int CACHE_LIMIT = 64;

    private static final Map<String, Image> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private CoverArt() {
    }

    /**
     * Finds the cover for a song, from disk or from the network.
     *
     * @param song       the song, possibly {@code null}
     * @param decodeSize the longest side to decode at; the image is only ever drawn small
     * @return the image, or {@code null} when the song has no cover at all - a remote image may
     *         still be loading, so the result is not necessarily ready to measure
     */
    public static Image of(Song song, double decodeSize) {
        if (song == null) {
            return null;
        }
        String source = sourceOf(song);
        if (source == null) {
            return null;
        }

        String key = source + "@" + (int) decodeSize;
        synchronized (CACHE) {
            Image cached = CACHE.get(key);
            // An entry that failed is dropped rather than returned forever: the usual cause is a
            // network that was down, and the next look is a fair chance to succeed.
            if (cached != null && !cached.isError()) {
                return cached;
            }
            CACHE.remove(key);
        }

        boolean remote = song.getCoverPath() == null;
        // backgroundLoading only for the remote case. A local file is a disk read that has already
        // been checked for readability, and loading it in the background would make the common
        // path flicker through a placeholder for no reason.
        Image image = remote
                ? new Image(source, decodeSize, decodeSize, true, true, true)
                : new Image(source, decodeSize, decodeSize, true, true);

        synchronized (CACHE) {
            CACHE.put(key, image);
        }
        return image;
    }

    /**
     * @param song the song
     * @return the address to load, or {@code null} when there is no cover
     */
    private static String sourceOf(Song song) {
        Path path = song.getCoverPath();
        if (path != null && Files.isReadable(path)) {
            return path.toUri().toString();
        }
        String url = song.getCoverUrl();
        return url == null || url.isBlank() ? null : url;
    }

    /**
     * Centre-crops the image into the view, waiting for it if it is still arriving.
     *
     * <p>Album art is square and both frames are square, so the image is cropped to its middle
     * rather than letterboxed. A background-loaded image has width zero until it lands, and cropping
     * against that produces an empty viewport - a frame that stays blank forever with no error
     * anywhere. So the crop is applied now if the image is ready and on completion otherwise.
     *
     * @param image          the image, which may still be loading
     * @param view           where to draw it
     * @param onUnavailable  run when the image cannot be shown, to put a placeholder up instead;
     *                       always called on the interface thread
     */
    public static void fit(Image image, ImageView view, Runnable onUnavailable) {
        if (image.isError()) {
            report(image);
            onUnavailable.run();
            return;
        }
        if (image.getProgress() >= 1.0 && image.getWidth() > 0) {
            view.setViewport(LibraryView.centeredSquare(image.getWidth(), image.getHeight()));
            return;
        }

        // Still in flight. Both listeners fire on the interface thread, so the view may be touched
        // from them directly.
        image.progressProperty().addListener((observable, was, now) -> {
            if (now.doubleValue() >= 1.0 && !image.isError() && image.getWidth() > 0) {
                view.setViewport(LibraryView.centeredSquare(image.getWidth(), image.getHeight()));
            }
        });
        image.errorProperty().addListener((observable, was, failed) -> {
            if (failed) {
                report(image);
                onUnavailable.run();
            }
        });
    }

    /**
     * @param image the image that failed
     */
    private static void report(Image image) {
        Exception cause = image.getException();
        // A cover that will not load is never an error the user has to act on - the application is
        // required to run with no artwork at all - so this is a line in the log and a placeholder
        // on screen, exactly as a missing sprite is.
        LOG.warning("Could not load a cover image"
                + (cause == null ? "" : " - " + cause.getMessage()));
    }
}
