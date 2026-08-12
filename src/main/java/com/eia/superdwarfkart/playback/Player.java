package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.LibraryListener;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Drives playback through whichever {@link PlaybackMode} is active.
 *
 * <p><strong>This class never asks which mode it is holding.</strong> There is no
 * {@code instanceof}, no switch over {@link ModeId}, and no special case for the queue not
 * supporting {@link PlaybackMode#previous()} - that is answered by
 * {@link PlaybackMode#supportsPrevious()} and surfaced through {@link #canGoPrevious()} so the
 * interface can disable the control. Swapping modes is pure polymorphism: replace the object.
 *
 * <p>The library remains the single source of truth. Each mode builds its own structure from it,
 * and the player rebuilds the active mode when songs are added or removed, keeping the current
 * song where it can.
 */
public class Player {

    private final Library library;
    private final List<PlaybackListener> listeners = new ArrayList<>();

    private PlaybackMode mode;

    /**
     * Creates a player over a library.
     *
     * @param library     the canonical collection; must not be {@code null}
     * @param initialMode the mode to start in; must not be {@code null}
     */
    public Player(Library library, PlaybackMode initialMode) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.mode = Objects.requireNonNull(initialMode, "initialMode must not be null");
        this.mode.load(library.all());
        library.addListener(this::onLibraryChanged);
    }

    // ------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------

    /** @return the active mode */
    public PlaybackMode mode() {
        return mode;
    }

    /**
     * Switches to another mode, rebuilding its structure from the library.
     *
     * <p>The song currently playing is carried across where the new mode can reach it, so
     * changing how the library is ordered does not interrupt what is playing.
     *
     * @param newMode the mode to switch to; must not be {@code null}
     */
    public void setMode(PlaybackMode newMode) {
        Objects.requireNonNull(newMode, "newMode must not be null");
        if (newMode == mode) {
            return;
        }
        Song playing = mode.current();
        mode = newMode;
        mode.load(library.all());
        if (playing != null) {
            mode.select(playing);
        }
        notifyListeners();
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /** @return the song currently playing, or {@code null} if there is none */
    public Song current() {
        return mode.current();
    }

    /**
     * Advances to the next song in the active mode's order.
     *
     * @return the song now playing, or {@code null} if there is nothing to advance to
     */
    public Song next() {
        Song song = mode.next();
        notifyListeners();
        return song;
    }

    /**
     * Goes back to the previously played song.
     *
     * <p>Guarded rather than propagating: a mode that cannot go back returns {@code null} here
     * instead of throwing, because the control that reaches this is already disabled and a
     * keyboard shortcut arriving anyway should do nothing rather than raise an error at the user.
     *
     * @return the song now playing, or {@code null} if this mode cannot go back
     */
    public Song previous() {
        if (!canGoPrevious()) {
            return null;
        }
        Song song = mode.previous();
        notifyListeners();
        return song;
    }

    /**
     * Jumps to a song the user picked from the library.
     *
     * @param song the song to play
     * @return whether the active mode could move to it
     */
    public boolean select(Song song) {
        boolean moved = mode.select(song);
        if (moved) {
            notifyListeners();
        }
        return moved;
    }

    /** @return the song that {@link #next()} would move to, without moving */
    public Song peekNext() {
        return mode.peekNext();
    }

    /** @return whether there is a song to advance to */
    public boolean canGoNext() {
        return mode.hasNext();
    }

    /**
     * Reports whether going back is possible right now.
     *
     * <p>Two separate reasons it may not be: the mode is one-way, or there is nothing loaded.
     * The interface disables the control on this rather than calling {@link #previous()} and
     * handling a failure.
     *
     * @return whether the previous control should be enabled
     */
    public boolean canGoPrevious() {
        return mode.supportsPrevious() && !mode.isEmpty();
    }

    /** @return the active mode's operation costs, for the complexity panel */
    public Map<String, String> complexities() {
        return mode.complexities();
    }

    // ------------------------------------------------------------------
    // Library changes
    // ------------------------------------------------------------------

    /**
     * Rebuilds the active mode after the library gains or loses songs.
     *
     * <p><strong>An edit is not a structural change.</strong> Rebuilding on
     * {@link LibraryListener.LibraryChange#UPDATED} would reshuffle the ring every time a rating
     * slider moved, so the running order would change under the user while they were listening.
     *
     * @param change what happened
     * @param song   the song involved, or {@code null} for a wholesale replacement
     */
    private void onLibraryChanged(LibraryListener.LibraryChange change, Song song) {
        if (change == LibraryListener.LibraryChange.UPDATED) {
            return;
        }
        reload();
    }

    /**
     * Rebuilds the active mode from the library, keeping the current song where possible.
     */
    public void reload() {
        Song playing = mode.current();
        mode.load(library.all());
        if (playing != null) {
            mode.select(playing);
        }
        notifyListeners();
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    /**
     * Registers a listener for mode and current-song changes.
     *
     * @param listener the listener to add
     */
    public void addListener(PlaybackListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        // A copy, so a listener may unregister itself while being notified.
        for (PlaybackListener listener : List.copyOf(listeners)) {
            listener.playbackChanged(mode, mode.current());
        }
    }
}
