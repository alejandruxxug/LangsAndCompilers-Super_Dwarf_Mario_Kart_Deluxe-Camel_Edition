package com.eia.superdwarfkart.audio;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Something that turns audio into sound, one track at a time.
 *
 * <p><strong>This interface is the seam.</strong> Everything above it - the transport controls, the
 * meters, the beat analyser, the rhythm game - is written against these ten methods and knows
 * nothing about where the bytes come from. Today the only implementation decodes a file from disk.
 * If the Spotify path is ever built it arrives as a second implementation reading a pipe, and
 * nothing outside this package learns that a subprocess exists.
 *
 * <p>Three guarantees the rest of the application relies on:
 *
 * <ul>
 *   <li><strong>One format, always.</strong> Whatever the file was - mono, 22 kHz, MP3 - listeners
 *       receive 16-bit signed stereo, interleaved, little-endian. Nothing downstream inspects a
 *       format or handles a mono special case.</li>
 *   <li><strong>{@link #position()} is the clock.</strong> It reports what has actually been
 *       rendered by the sound card, not what has been decoded or how many frames have been drawn.
 *       Game timing is driven from here and from nowhere else; accumulated frame deltas drift out
 *       of sync with the music within a minute.</li>
 *   <li><strong>The tap never swallows audio.</strong> A registered {@link PcmListener} sees every
 *       block, and every block still reaches the speakers.</li>
 * </ul>
 *
 * <p>Missing and unplayable files are ordinary, not exceptional: the library stores paths and paths
 * go stale. Only {@link #load(Path)} and {@link #play()} throw, and only for a file or a device that
 * genuinely cannot be opened. Everything else is a no-op when nothing is loaded.
 */
public interface AudioSource extends AutoCloseable {

    /**
     * Opens a track and rewinds to its start, without playing it.
     *
     * <p>Replaces whatever was loaded before. Does not start playback, so selecting a song in the
     * library while paused leaves the application paused.
     *
     * @param file the audio file to open; must not be {@code null}
     * @throws AudioException if the file is missing, unreadable, or in a format no installed
     *                        decoder can turn into the application's PCM format
     */
    void load(Path file);

    /**
     * Starts or resumes playback from the current position.
     *
     * <p>Does nothing when no track is loaded or when it is already playing.
     *
     * @throws AudioException if the sound card cannot be opened
     */
    void play();

    /**
     * Suspends playback, holding the position.
     *
     * <p>{@link #play()} resumes from exactly here, and {@link #position()} stops advancing.
     */
    void pause();

    /** Stops playback and rewinds to the start of the loaded track. */
    void stop();

    /**
     * Jumps to a position within the loaded track.
     *
     * <p>Playing before the jump means playing after it.
     *
     * @param position where to move to; clamped into the track, {@code null} means the start
     */
    void seek(Duration position);

    /**
     * Reports how much of the track has actually been heard.
     *
     * <p>Measured from the sound card, so it excludes audio that has been decoded and buffered but
     * not yet rendered. This is the application's clock.
     *
     * @return the current position, or {@link Duration#ZERO} when nothing is loaded
     */
    Duration position();

    /**
     * @return the loaded track's playing time, or {@link Duration#ZERO} when it is not known -
     *         which a variable-bitrate file with no header can genuinely be
     */
    Duration duration();

    /** @return whether audio is being rendered right now */
    boolean isPlaying();

    /** @return whether a track is open and ready to play */
    boolean isLoaded();

    /**
     * Registers a tap on the decoded stream.
     *
     * <p>Read {@link PcmListener} before writing one: it is called on the playback thread.
     *
     * @param listener the listener to add; must not be {@code null}
     */
    void addPcmListener(PcmListener listener);

    /**
     * Removes a previously registered tap.
     *
     * @param listener the listener to remove
     */
    void removePcmListener(PcmListener listener);

    /**
     * Sets what happens when a track plays through to its end.
     *
     * <p><strong>Called on the playback thread</strong>, after the last block has actually been
     * rendered. Anything that touches the interface must marshal onto the interface thread itself.
     *
     * @param action what to run, or {@code null} to do nothing
     */
    void setOnEndOfMedia(Runnable action);

    /**
     * Sets the output level.
     *
     * <p>Silently ignored on a sound card that exposes no gain control, which is a real possibility
     * and not worth failing over - the operating system's own volume still works.
     *
     * @param volume 0.0 silent to 1.0 full, clamped
     */
    void setVolume(double volume);

    /** @return the output level last set, 0.0 to 1.0 */
    double volume();

    /**
     * Stops playback and releases the sound card.
     *
     * <p>Overridden to drop the checked exception: there is nothing a caller could do about a
     * failure to close, and every shutdown path calls this.
     */
    @Override
    void close();
}
