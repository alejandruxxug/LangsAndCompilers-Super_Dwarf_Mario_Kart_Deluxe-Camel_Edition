package com.eia.superdwarfkart.audio;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * One audio output that opens local files and Spotify tracks alike, choosing by locator.
 *
 * <p><strong>This is the only class in the application that knows there is more than one kind of
 * song.</strong> {@code PlaybackEngine} holds an {@link AudioSource} and calls
 * {@code load(song.locator())}; the running order, the meters, the beat analyser, the rhythm game
 * and every transport control are written against the interface and cannot tell which one answered.
 * That is what let Spotify arrive without touching the three graded structures, the game or the
 * visualiser.
 *
 * <h2>Taps and callbacks are registered on both, always</h2>
 *
 * <p>The level meters, the beat analyser and the end-of-track handler are attached once, at
 * startup, to whatever the engine was given. If they were attached only to the source that happened
 * to be active, the first switch from a local file to a Spotify track would leave the meters dead
 * and the running order stuck - and neither failure throws anything. So every listener registered
 * here is forwarded to both sources and stays there, and only the transport is routed.
 *
 * <p>The Spotify source is built lazily through a supplier rather than taken in the constructor.
 * Most sessions never touch Spotify, and constructing it early would open a pipe and a REST client
 * for a feature nobody asked for - the same rule the daemon itself is under.
 */
public class RoutingAudioSource implements AudioSource {

    private static final Logger LOG = Logger.getLogger(RoutingAudioSource.class.getName());

    /** Locator prefix that routes to Spotify. Everything else is a file path. */
    private static final String SPOTIFY_PREFIX = "spotify:";

    private final AudioSource local;
    private final Supplier<AudioSource> spotifyFactory;

    /**
     * Every tap registered so far.
     *
     * <p>Kept here as well as on the sources, because the Spotify source is built long after the
     * meters and the beat analyser attached themselves. Without this list it would come up with no
     * taps at all - the meters would sit dead through every streamed song and nothing would say so.
     */
    private final java.util.List<PcmListener> taps = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Built on first use, then kept. {@code null} until a Spotify track is loaded. */
    private AudioSource spotify;

    /** Whichever source holds the current track. Never {@code null} after the first load. */
    private AudioSource active;

    private Runnable onEndOfMedia;
    private double volume = 1.0;
    private boolean closed;

    /**
     * Wires a local source to a Spotify source that is built on demand.
     *
     * @param local          opens files from disk; must not be {@code null}
     * @param spotifyFactory builds the Spotify source the first time one is needed, and may return
     *                       {@code null} when Spotify is unavailable on this machine; must not be
     *                       {@code null} itself
     */
    public RoutingAudioSource(AudioSource local, Supplier<AudioSource> spotifyFactory) {
        this.local = Objects.requireNonNull(local, "local must not be null");
        this.spotifyFactory = Objects.requireNonNull(spotifyFactory, "spotifyFactory must not be null");
        this.active = local;
    }

    /**
     * @param locator the locator to inspect
     * @return whether this locator names a Spotify track rather than a file
     */
    public static boolean isSpotify(String locator) {
        return locator != null && locator.startsWith(SPOTIFY_PREFIX);
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    @Override
    public void load(String locator) {
        Objects.requireNonNull(locator, "locator must not be null");
        if (closed) {
            throw new AudioException("The audio output has been closed");
        }

        AudioSource target = isSpotify(locator) ? spotifySource() : local;
        if (target != active) {
            // Stop what the other one was doing before handing over, or two sources would hold the
            // sound card at once and the old one would go on playing under the new one.
            active.pause();
            active = target;
        }
        target.load(locator);
    }

    /**
     * @return the Spotify source, building it on first use
     * @throws AudioException if Spotify is not available on this machine
     */
    private AudioSource spotifySource() {
        if (spotify == null) {
            spotify = spotifyFactory.get();
            if (spotify == null) {
                throw new AudioException("Spotify is not connected on this machine");
            }
            LOG.fine("Built the Spotify audio source on first use");
            // Everything registered before this source existed has to reach it too, or the meters
            // go dead and the running order stops at the first streamed song.
            for (PcmListener tap : taps) {
                spotify.addPcmListener(tap);
            }
            spotify.setOnEndOfMedia(onEndOfMedia);
            spotify.setVolume(volume);
        }
        return spotify;
    }

    /** @return the source currently holding a track */
    public AudioSource active() {
        return active;
    }

    /** @return whether the active source is the Spotify one */
    public boolean isStreaming() {
        return spotify != null && active == spotify;
    }

    // ------------------------------------------------------------------
    // Transport, delegated to whichever source is active
    // ------------------------------------------------------------------

    @Override
    public void play() {
        active.play();
    }

    @Override
    public void pause() {
        active.pause();
    }

    @Override
    public void stop() {
        active.stop();
    }

    @Override
    public void seek(Duration position) {
        active.seek(position);
    }

    @Override
    public Duration position() {
        return active.position();
    }

    @Override
    public Duration duration() {
        return active.duration();
    }

    @Override
    public boolean isPlaying() {
        return active.isPlaying();
    }

    @Override
    public boolean isLoaded() {
        return active.isLoaded();
    }

    // ------------------------------------------------------------------
    // Listeners, registered on both
    // ------------------------------------------------------------------

    @Override
    public void addPcmListener(PcmListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        local.addPcmListener(listener);
        if (spotify != null) {
            spotify.addPcmListener(listener);
        }
        taps.add(listener);
    }

    @Override
    public void removePcmListener(PcmListener listener) {
        local.removePcmListener(listener);
        if (spotify != null) {
            spotify.removePcmListener(listener);
        }
        taps.remove(listener);
    }

    @Override
    public void setOnEndOfMedia(Runnable action) {
        this.onEndOfMedia = action;
        local.setOnEndOfMedia(action);
        if (spotify != null) {
            spotify.setOnEndOfMedia(action);
        }
    }

    @Override
    public void setVolume(double newVolume) {
        this.volume = Math.clamp(newVolume, 0d, 1d);
        local.setVolume(this.volume);
        if (spotify != null) {
            spotify.setVolume(this.volume);
        }
    }

    @Override
    public double volume() {
        return volume;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        local.close();
        if (spotify != null) {
            spotify.close();
        }
        taps.clear();
    }
}
