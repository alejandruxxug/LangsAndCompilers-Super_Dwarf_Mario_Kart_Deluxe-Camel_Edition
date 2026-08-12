package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.ds.SimpleQueue;
import com.eia.superdwarfkart.ds.StepCounter;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mode 2: arrival order, over a hand-written FIFO queue.
 *
 * <p>Songs play in the order they were added to the library and there is <strong>no way
 * back</strong>: {@link #previous()} throws and the interface disables the control instead of
 * calling it. That is not a limitation being worked around, it is what a queue is, and the
 * starting-grid view says so out loud.
 *
 * <p><strong>The queue is a view, never the storage.</strong> {@code Library} owns the songs;
 * this builds its own queue from them at load, so dequeuing here consumes the queue and leaves
 * the library untouched. Switching to this mode and playing to the end must not empty anyone's
 * library - that is the failure this design exists to prevent.
 */
public class ArrivalOrderMode extends AbstractPlaybackMode {

    private final SimpleQueue<Song> queue;

    /** Creates an arrival-order mode. */
    public ArrivalOrderMode() {
        this(StepCounter.NO_OP);
    }

    /**
     * Creates an arrival-order mode with instrumentation.
     *
     * @param counter step counter for the queue
     */
    public ArrivalOrderMode(StepCounter counter) {
        super(ModeId.ARRIVAL_ORDER, counter);
        this.queue = new SimpleQueue<>(counter);
    }

    /**
     * Fills the queue from the library, in the library's own order, and takes the front song as
     * the one now playing.
     *
     * <p>Time complexity: O(n), one O(1) enqueue per song.
     *
     * @param songs the library's canonical collection; copied into the queue, never modified
     */
    @Override
    protected void build(Collection<Song> songs) {
        queue.clear();
        for (Song song : songs) {
            queue.enqueue(song);
        }
        // The front song is the one playing, so it leaves the queue: anything still queued is
        // genuinely still waiting. Leaving it in would make the first next() replay it.
        if (!queue.isEmpty()) {
            setCurrent(queue.dequeue());
        }
    }

    /**
     * Takes the song at the head of the queue.
     *
     * <p>Time complexity: O(1) - the queue keeps a tail pointer as well as a head, so neither
     * end costs a traversal.
     *
     * @return the song now current, or {@code null} once the queue has run out
     */
    @Override
    public Song next() {
        if (queue.isEmpty()) {
            return null;
        }
        Song song = queue.dequeue();
        setCurrent(song);
        return song;
    }

    /**
     * Reports the song at the head of the queue <strong>without removing it</strong>.
     *
     * <p>Time complexity: O(1).
     *
     * @return the next song, or {@code null} if the queue is empty
     */
    @Override
    public Song peekNext() {
        return queue.isEmpty() ? null : queue.peek();
    }

    /**
     * <p>Time complexity: O(1).
     *
     * @return whether anything is still queued
     */
    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    /**
     * Skips forward to a song further down the queue.
     *
     * <p>Reaching a song means dequeuing everything in front of it - that is what FIFO means,
     * and there is no other honest way to do it with a queue. A song that is not in the queue
     * (already played, or not in this mode's ordering) leaves the queue untouched.
     *
     * <p>Time complexity: O(n).
     *
     * @param song the song to skip to
     * @return {@code true} if the song was queued and is now current
     */
    @Override
    public boolean select(Song song) {
        if (song == null || !isQueued(song)) {
            return false;
        }
        Song taken = queue.dequeue();
        while (taken != null && !taken.equals(song)) {
            taken = queue.dequeue();
        }
        setCurrent(taken);
        return true;
    }

    /**
     * Looks for a song without consuming the queue.
     *
     * <p>Checked before {@link #select(Song)} touches anything: draining the queue looking for a
     * song that was never in it would throw the user's whole running order away.
     *
     * <p>Time complexity: O(n).
     *
     * @param song the song to look for
     * @return whether it is still queued
     */
    private boolean isQueued(Song song) {
        for (Song queued : queue) {
            if (queued.equals(song)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists what is still waiting, front of the queue first.
     *
     * <p>Iterating the queue does not consume it.
     *
     * <p>Time complexity: O(limit).
     *
     * @param limit how many to return at most
     * @return the queued songs, front first
     */
    @Override
    public List<Song> upcoming(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Song> upcoming = new ArrayList<>(Math.min(limit, queue.size()));
        for (Song song : queue) {
            if (upcoming.size() == limit) {
                break;
            }
            upcoming.add(song);
        }
        return upcoming;
    }

    /**
     * <p>Time complexity: O(1).
     *
     * @return how many songs are still queued; this shrinks as they play
     */
    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public Map<String, String> complexities() {
        Map<String, String> costs = new LinkedHashMap<>();
        costs.put("enqueue", "O(1)");
        costs.put("next() dequeue", "O(1)");
        costs.put("peekNext()", "O(1)");
        costs.put("previous()", "not supported");
        costs.put("select(song)", "O(n)");
        costs.put("build", "O(n)");
        return costs;
    }

    /** @return the queue itself, for the starting-grid view to draw */
    public SimpleQueue<Song> queue() {
        return queue;
    }
}
