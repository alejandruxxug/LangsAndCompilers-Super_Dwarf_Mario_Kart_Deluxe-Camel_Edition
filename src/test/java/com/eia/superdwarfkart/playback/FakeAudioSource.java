package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.audio.AudioException;
import com.eia.superdwarfkart.audio.AudioSource;
import com.eia.superdwarfkart.audio.PcmListener;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An audio source that records what it was told to do instead of making a sound.
 *
 * <p>This is what makes {@link PlaybackEngine} testable at all. The engine's job is the handover
 * between a running order and an output - load this, play that, ask for the next one when this
 * finishes - and every one of those decisions can be checked without a sound card, a real file, or
 * waiting for a song to actually play through.
 *
 * <p>{@link #reachEndOfMedia()} stands in for a track finishing, which is the event that would
 * otherwise take three minutes to arrive.
 */
class FakeAudioSource implements AudioSource {

    /** Every file that was loaded, in order, so a reload is visible and not just a final state. */
    final List<Path> loads = new ArrayList<>();

    /** Files this source pretends it cannot open, standing in for a moved or corrupt file. */
    private final Set<Path> unplayable = new HashSet<>();

    private Path loaded;
    private boolean playing;
    private boolean closed;
    private Duration position = Duration.ZERO;
    private Duration duration = Duration.ofSeconds(180);
    private Runnable onEndOfMedia;
    private double volume = 1;

    /**
     * @param file a file this source should refuse to open
     */
    void makeUnplayable(Path file) {
        unplayable.add(file);
    }

    /** @return how many times {@link #play()} was called */
    int playCalls() {
        return playCalls;
    }

    private int playCalls;

    /** Fires the end-of-track callback, as the playback thread would when a song plays out. */
    void reachEndOfMedia() {
        playing = false;
        if (onEndOfMedia != null) {
            onEndOfMedia.run();
        }
    }

    /** @return the file currently loaded, or {@code null} */
    Path loadedFile() {
        return loaded;
    }

    /** @return whether {@link #close()} has been called */
    boolean isClosed() {
        return closed;
    }

    /**
     * @param value the playing time this source should report
     */
    void setDuration(Duration value) {
        this.duration = value;
    }

    @Override
    public void load(Path file) {
        if (unplayable.contains(file)) {
            throw new AudioException("File not found: " + file);
        }
        loads.add(file);
        loaded = file;
        playing = false;
        position = Duration.ZERO;
    }

    @Override
    public void play() {
        if (loaded == null) {
            return;
        }
        playCalls++;
        playing = true;
    }

    @Override
    public void pause() {
        playing = false;
    }

    @Override
    public void stop() {
        playing = false;
        position = Duration.ZERO;
    }

    @Override
    public void seek(Duration to) {
        position = to == null ? Duration.ZERO : to;
    }

    @Override
    public Duration position() {
        return position;
    }

    @Override
    public Duration duration() {
        return loaded == null ? Duration.ZERO : duration;
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public boolean isLoaded() {
        return loaded != null;
    }

    @Override
    public void addPcmListener(PcmListener listener) {
        // No audio flows through this source, so there is nothing to tap.
    }

    @Override
    public void removePcmListener(PcmListener listener) {
        // See addPcmListener.
    }

    @Override
    public void setOnEndOfMedia(Runnable action) {
        this.onEndOfMedia = action;
    }

    @Override
    public void setVolume(double newVolume) {
        this.volume = newVolume;
    }

    @Override
    public double volume() {
        return volume;
    }

    @Override
    public void close() {
        closed = true;
        playing = false;
    }
}
