package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A way of ordering the library for playback, backed by one hand-written data structure.
 *
 * <p>This is the seam the whole playback design turns on. {@link Player} holds a
 * {@code PlaybackMode} and <strong>never asks which one it is</strong> - no {@code instanceof},
 * no switch on {@link ModeId}. Changing mode replaces the object and nothing else, so the three
 * structures are genuinely interchangeable rather than three branches wearing an interface.
 *
 * <p><strong>A mode is a view, not storage.</strong> {@code Library} owns the canonical
 * collection; {@link #load(Collection)} builds this mode's structure <em>from</em> it. Consuming
 * a mode - dequeuing in arrival order, in particular - must never remove a song from the
 * library.
 *
 * <p>Where there is nothing to play, navigation returns {@code null} rather than throwing. An
 * empty library is an ordinary state, not an error. The single exception is {@link #previous()}
 * in a mode that cannot go backwards; see the note there.
 */
public interface PlaybackMode {

    /** @return which mode this is, and which structure backs it */
    ModeId id();

    /**
     * Builds this mode's structure from the library.
     *
     * <p>Called on every mode change and whenever the library changes. Any previously loaded
     * ordering is discarded, and the current song is reset.
     *
     * @param songs the library's canonical collection; never modified by this call
     */
    void load(Collection<Song> songs);

    /** @return the song being played, or {@code null} if nothing is loaded */
    Song current();

    /**
     * Advances to the next song in this mode's order.
     *
     * @return the song now current, or {@code null} if there is nothing left to play
     */
    Song next();

    /**
     * Steps back to the previously played song.
     *
     * @return the song now current, or {@code null} if there is nothing to go back to
     * @throws UnsupportedOperationException if {@link #supportsPrevious()} is {@code false}.
     *         The interface must disable the control rather than call this and catch it.
     */
    Song previous();

    /**
     * Reports what {@link #next()} would return, without moving.
     *
     * <p>Non-mutating on purpose: this is what a prefetch or an "up next" label reads, and in
     * arrival order a peek that dequeued would silently eat a song every time it was called.
     *
     * @return the next song, or {@code null} if there is none
     */
    Song peekNext();

    /**
     * @return whether there is another song to advance to
     */
    boolean hasNext();

    /**
     * Moves to a song the user picked directly, rather than by advancing.
     *
     * @param song the song to move to
     * @return {@code true} if this mode could move there
     */
    boolean select(Song song);

    /**
     * Lists the songs coming up, in this mode's order, <strong>without consuming them</strong>.
     *
     * @param limit how many to return at most
     * @return the upcoming songs, nearest first; empty when there are none
     */
    List<Song> upcoming(int limit);

    /** @return how many songs this mode currently holds */
    int size();

    /** @return whether this mode holds no songs */
    boolean isEmpty();

    /**
     * Describes the cost of this mode's operations, for the complexity panel.
     *
     * <p>Keyed by operation name and ordered, so the panel can render it directly without
     * knowing anything about the concrete mode.
     *
     * <p><strong>The four navigated operations are spelled identically in every mode</strong> -
     * {@code "next()"}, {@code "previous()"}, {@code "select(song)"} and {@code "build"} - because
     * the player brackets those calls under exactly those names and the panel puts the measured
     * step count on the row whose key matches. Spelling one of them differently does not fail; it
     * silently leaves that row without a measured value, which is worse. Structure-specific rows
     * such as {@code "enqueue"} or {@code "in-order traversal"} are free-form and simply show no
     * measurement.
     *
     * <p>What the operation costs is what distinguishes the modes, so the value alone carries the
     * detail: {@code next()} is O(1) over a queue because it is a dequeue, and O(log n) over the
     * tree because it is a successor walk. The structure's name is shown above the table.
     *
     * @return operation name to theoretical complexity
     */
    Map<String, String> complexities();

    /**
     * Reports whether this mode can move backwards.
     *
     * <p>Answered from {@link ModeId} rather than by type, so that the interface can disable the
     * previous control without knowing which mode is active.
     *
     * @return {@code true} unless the backing structure is one-way
     */
    default boolean supportsPrevious() {
        return id().supportsPrevious();
    }

    /** @return the name of the hand-written structure backing this mode */
    default String structureName() {
        return id().structureName();
    }
}
