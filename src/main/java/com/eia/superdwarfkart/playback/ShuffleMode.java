package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.ds.CircularDoublyLinkedList;
import com.eia.superdwarfkart.ds.StepCounter;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Mode 1: shuffle, over a circular doubly linked list.
 *
 * <p><strong>The shuffle happens once, at load, and is then baked into the ring.</strong> It is
 * not a random pick per step. That distinction is the whole design:
 *
 * <ul>
 *   <li>{@link #previous()} returns the song actually played before, because the ordering is a
 *       real sequence rather than a series of independent draws;</li>
 *   <li>{@link #peekNext()} is deterministic, so an "up next" label cannot disagree with what
 *       plays a moment later;</li>
 *   <li>every song plays once per lap - random-per-step would repeat some and starve others.</li>
 * </ul>
 *
 * <p>Because the ring is circular there is no end: past the last song is the first, and
 * {@link #hasNext()} is true for as long as anything is loaded.
 */
public class ShuffleMode extends AbstractPlaybackMode {

    private final CircularDoublyLinkedList<Song> ring;
    private final Random random;

    /** Creates a shuffle mode with an unpredictable ordering. */
    public ShuffleMode() {
        this(new Random(), StepCounter.NO_OP);
    }

    /**
     * Creates a shuffle mode with an explicit source of randomness.
     *
     * @param random  supplies the ordering; seed it to get a reproducible shuffle in a test
     * @param counter step counter for the ring
     */
    public ShuffleMode(Random random, StepCounter counter) {
        super(ModeId.SHUFFLE, counter);
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.ring = new CircularDoublyLinkedList<>(counter);
    }

    /**
     * Shuffles a copy of the library and bakes that order into the ring.
     *
     * <p>Time complexity: O(n).
     *
     * @param songs the library's canonical collection; copied, never modified
     */
    @Override
    protected void build(Collection<Song> songs) {
        ring.clear();
        if (songs.isEmpty()) {
            return;
        }
        // Shuffling a copy is what keeps the library's own order intact.
        List<Song> shuffled = new ArrayList<>(songs);
        Collections.shuffle(shuffled, random);
        ring.insertAll(shuffled);
        ring.resetCursor();
        setCurrent(ring.current());
    }

    /**
     * Advances one position around the ring.
     *
     * <p>Time complexity: O(1).
     *
     * @return the song now current, or {@code null} if nothing is loaded
     */
    @Override
    public Song next() {
        if (ring.isEmpty()) {
            return null;
        }
        Song song = ring.next();
        setCurrent(song);
        return song;
    }

    /**
     * Steps back one position around the ring.
     *
     * <p>Time complexity: O(1) - the list is doubly linked precisely so this does not cost a lap.
     *
     * @return the song now current, or {@code null} if nothing is loaded
     */
    @Override
    protected Song previousSong() {
        if (ring.isEmpty()) {
            return null;
        }
        Song song = ring.previous();
        setCurrent(song);
        return song;
    }

    /**
     * Reports the next song without moving the cursor.
     *
     * <p>Time complexity: O(1).
     *
     * @return the next song, or {@code null} if nothing is loaded
     */
    @Override
    public Song peekNext() {
        return ring.isEmpty() ? null : ring.peekNext();
    }

    /**
     * Reports whether there is another song. On a ring, there always is while it holds anything.
     *
     * <p>Time complexity: O(1).
     *
     * @return {@code true} unless the ring is empty
     */
    @Override
    public boolean hasNext() {
        return !ring.isEmpty();
    }

    /**
     * Moves the cursor onto a song chosen from the library.
     *
     * <p>Time complexity: O(n) - the ring has no index, so finding a node means walking it.
     *
     * @param song the song to move to
     * @return {@code true} if it was found
     */
    @Override
    public boolean select(Song song) {
        if (song == null || !ring.moveTo(song)) {
            return false;
        }
        setCurrent(song);
        return true;
    }

    /**
     * Lists the songs coming up, following the ring around past the end.
     *
     * <p>Time complexity: O(n) - one lap to locate the cursor, which is why this is a display
     * call and not something the navigation itself uses.
     *
     * @param limit how many to return at most
     * @return the upcoming songs, nearest first
     */
    @Override
    public List<Song> upcoming(int limit) {
        if (limit <= 0 || ring.isEmpty()) {
            return List.of();
        }
        List<Song> lap = new ArrayList<>(ring.size());
        for (Song song : ring) {
            lap.add(song);
        }
        int at = lap.indexOf(current());
        List<Song> upcoming = new ArrayList<>(Math.min(limit, lap.size()));
        for (int i = 1; i <= Math.min(limit, lap.size()); i++) {
            upcoming.add(lap.get(Math.floorMod(at + i, lap.size())));
        }
        return upcoming;
    }

    /**
     * <p>Time complexity: O(1).
     *
     * @return how many songs the ring holds
     */
    @Override
    public int size() {
        return ring.size();
    }

    @Override
    public Map<String, String> complexities() {
        Map<String, String> costs = new LinkedHashMap<>();
        costs.put("next()", "O(1)");
        costs.put("previous()", "O(1)");
        costs.put("peekNext()", "O(1)");
        costs.put("insert", "O(1)");
        costs.put("remove(song)", "O(n)");
        costs.put("select(song)", "O(n)");
        costs.put("build (shuffle)", "O(n)");
        return costs;
    }

    /** @return the ring itself, for the circuit view to draw */
    public CircularDoublyLinkedList<Song> ring() {
        return ring;
    }
}
