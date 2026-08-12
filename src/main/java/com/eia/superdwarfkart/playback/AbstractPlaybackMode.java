package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.ds.StepCounter;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;

import java.util.Collection;
import java.util.Objects;

/**
 * What the three modes have in common: which mode they are, which song is current, and the
 * rule that a mode which cannot go backwards says so in one consistent way.
 *
 * <p>Subclasses supply the structure and the navigation. They never manage the current song
 * themselves beyond calling {@link #setCurrent(Song)}, and they never decide what
 * {@link #previous()} does when the mode is one-way - that decision is made once, here.
 */
public abstract class AbstractPlaybackMode implements PlaybackMode {

    private final ModeId id;

    /**
     * Instrumentation passed down to the backing structure.
     *
     * <p>Defaults to {@link StepCounter#NO_OP}. The visualizer swaps in a real counter to show
     * measured steps beside theoretical complexity, which is why the modes accept one now
     * rather than being rewritten for it later.
     */
    protected final StepCounter counter;

    private Song current;

    /**
     * @param id      which mode this is
     * @param counter step counter for the backing structure; must not be {@code null}
     */
    protected AbstractPlaybackMode(ModeId id, StepCounter counter) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.counter = Objects.requireNonNull(counter, "counter must not be null");
    }

    @Override
    public final ModeId id() {
        return id;
    }

    @Override
    public final Song current() {
        return current;
    }

    /**
     * Records which song is now playing.
     *
     * @param song the song, or {@code null} when nothing is playing
     */
    protected final void setCurrent(Song song) {
        this.current = song;
    }

    /**
     * Rebuilds this mode from the library, discarding any previous ordering.
     *
     * <p>Final, so that resetting the current song cannot be forgotten by a subclass: a stale
     * current song from the previous ordering is exactly the kind of bug that only shows up
     * after switching modes twice.
     *
     * @param songs the library's canonical collection; never modified
     */
    @Override
    public final void load(Collection<Song> songs) {
        Objects.requireNonNull(songs, "songs must not be null");
        current = null;
        build(songs);
    }

    /**
     * Builds the backing structure and sets the starting song.
     *
     * @param songs the library's canonical collection; must be copied, never retained or modified
     */
    protected abstract void build(Collection<Song> songs);

    /**
     * Steps back, or refuses in one consistent way.
     *
     * <p>Final on purpose. A one-way mode fails here with a message that explains itself, rather
     * than each subclass inventing its own behaviour - and the interface disables the control
     * from {@link #supportsPrevious()} so this is never reached in normal use.
     *
     * @return the song now current, or {@code null} if there is nothing to go back to
     * @throws UnsupportedOperationException if this mode cannot move backwards
     */
    @Override
    public final Song previous() {
        if (!supportsPrevious()) {
            throw new UnsupportedOperationException(id.displayName()
                    + " is backed by a " + id.structureName()
                    + ", which only moves forward. The previous-track control is disabled in this"
                    + " mode rather than throwing this exception at the user.");
        }
        return previousSong();
    }

    /**
     * Steps back within the backing structure.
     *
     * <p>Only called when {@link #supportsPrevious()} is {@code true}, so a one-way mode has no
     * reason to override it.
     *
     * @return the song now current, or {@code null} if there is nothing to go back to
     */
    protected Song previousSong() {
        throw new UnsupportedOperationException(
                id.displayName() + " does not implement backward navigation");
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public String toString() {
        return id.displayName() + " (" + id.structureName() + ", " + size() + " songs)";
    }
}
