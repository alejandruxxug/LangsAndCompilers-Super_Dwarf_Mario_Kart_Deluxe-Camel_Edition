package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.ds.StepCounter;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.LibraryListener;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;

import java.util.ArrayList;
import java.util.Collection;
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

    /** Names the operations, spelled as every mode's {@code complexities()} spells them. */
    private static final String OP_NEXT = "next()";
    private static final String OP_PREVIOUS = "previous()";
    private static final String OP_SELECT = "select(song)";
    private static final String OP_BUILD = "build";

    private final Library library;
    private final List<PlaybackListener> listeners = new ArrayList<>();

    /**
     * Instrumentation, wrapped around each structure call.
     *
     * <p>The bracket sits here rather than in the controls because it has to close before the
     * listeners run: redrawing the bar peeks at the next song, and in the tree a peek is a whole
     * successor walk that would otherwise be billed to the navigation that triggered it.
     */
    private final StepCounter counter;

    private PlaybackMode mode;

    /**
     * Creates a player over a library, with instrumentation disabled.
     *
     * @param library     the canonical collection; must not be {@code null}
     * @param initialMode the mode to start in; must not be {@code null}
     */
    public Player(Library library, PlaybackMode initialMode) {
        this(library, initialMode, StepCounter.NO_OP);
    }

    /**
     * Creates a player that reports the cost of each operation it drives.
     *
     * @param library     the canonical collection; must not be {@code null}
     * @param initialMode the mode to start in; must not be {@code null}
     * @param counter     receives the measurement scopes; must not be {@code null}
     */
    public Player(Library library, PlaybackMode initialMode, StepCounter counter) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.mode = Objects.requireNonNull(initialMode, "initialMode must not be null");
        this.counter = Objects.requireNonNull(counter, "counter must not be null; use StepCounter.NO_OP");
        measured(OP_BUILD, library.size(), () -> mode.load(library.all()));
        library.addListener(this::onLibraryChanged);
    }

    /**
     * Runs a mode operation inside a measurement scope.
     *
     * @param <R>       the result type
     * @param operation the operation's name, matching the mode's complexity table
     * @param n         how many elements the structure works over; taken before the call, because
     *                  a queue is smaller afterwards and a build starts from an empty structure
     * @param work      the call to measure
     * @return whatever the work returned
     */
    private <R> R measured(String operation, int n, java.util.function.Supplier<R> work) {
        counter.begin(operation, mode.structureName(), n);
        try {
            return work.get();
        } finally {
            counter.end();
        }
    }

    private void measured(String operation, int n, Runnable work) {
        measured(operation, n, () -> {
            work.run();
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------

    /** @return the active mode */
    public PlaybackMode mode() {
        return mode;
    }

    /** @return the canonical collection every mode is built from */
    public Library library() {
        return library;
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
        measured(OP_BUILD, library.size(), () -> {
            mode.load(library.all());
            if (playing != null) {
                mode.select(playing);
            }
        });
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
        Song song = measured(OP_NEXT, mode.size(), mode::next);
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
        Song song = measured(OP_PREVIOUS, mode.size(), mode::previous);
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
        boolean moved = measured(OP_SELECT, mode.size(), () -> mode.select(song));
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
        reload(library.all());
    }

    /**
     * Rebuilds the active mode from a specific ordering of the songs.
     *
     * <p>Insertion order is the only thing that decides a binary search tree's shape, so this is
     * what lets the tree view demonstrate the worst case: reinserting the library already sorted
     * degenerates the tree into a straight line, and its measured search cost jumps from about
     * log n to n in a single click. Passing the library's own order is the ordinary reload.
     *
     * <p>The ordering affects only how this mode's structure is built. The library itself is
     * untouched, so the next change to it rebuilds from the canonical order again.
     *
     * @param ordering the songs in the order they should be inserted; must not be {@code null}
     */
    public void reload(Collection<Song> ordering) {
        Objects.requireNonNull(ordering, "ordering must not be null");
        Song playing = mode.current();
        measured(OP_BUILD, ordering.size(), () -> {
            mode.load(ordering);
            if (playing != null) {
                mode.select(playing);
            }
        });
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
