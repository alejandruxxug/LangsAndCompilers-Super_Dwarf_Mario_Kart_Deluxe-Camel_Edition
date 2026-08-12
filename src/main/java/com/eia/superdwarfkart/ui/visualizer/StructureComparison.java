package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.ds.BinarySearchTree;
import com.eia.superdwarfkart.ds.CircularDoublyLinkedList;
import com.eia.superdwarfkart.ds.SimpleQueue;
import com.eia.superdwarfkart.model.Song;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Runs the same search across all three hand-written structures and reports what each one cost.
 *
 * <p>The complexity panel argues that the mode selector picks a data structure rather than a
 * preference. This is that argument reduced to three numbers taken at the same instant, over the
 * same songs, looking for the same song: on a five hundred song library the tree lands near nine
 * comparisons while the ring and the queue walk hundreds. Nothing about it is simulated.
 *
 * <p>Each structure is built fresh here with its own counter, so a comparison never disturbs the
 * structure that is actually driving playback - running one mid-song must not reshuffle the
 * running order or drain the queue.
 */
public final class StructureComparison {

    /** Name of the operation the comparison measures, used on the scatter plot. */
    public static final String OPERATION = "compare: search";

    /**
     * What one structure cost for the search.
     *
     * @param structure   the structure's class name
     * @param measurement the measured cost
     * @param found       whether the search located the song
     */
    public record Result(String structure, Measurement measurement, boolean found) {

        /** @return the total elementary steps the search took */
        public int steps() {
            return measurement.steps();
        }
    }

    /**
     * Searches every structure for one song.
     *
     * <p>The queue has no lookup of its own in the ordinary sense - it is a FIFO, and finding
     * something in it means walking it - which is exactly the result worth showing.
     *
     * @param songs  the library to build each structure from; must not be {@code null}
     * @param target the song to search for; must not be {@code null}
     * @return one result per structure, in the order the modes are listed
     */
    public static List<Result> run(Collection<Song> songs, Song target) {
        Objects.requireNonNull(songs, "songs must not be null");
        Objects.requireNonNull(target, "target must not be null");

        int n = songs.size();
        List<Result> results = new ArrayList<>(3);

        OperationCounter ringCounter = new OperationCounter();
        CircularDoublyLinkedList<Song> ring = new CircularDoublyLinkedList<>(ringCounter);
        ring.insertAll(songs);
        boolean inRing = ringCounter.measure(
                OPERATION, "CircularDoublyLinkedList", n, () -> ring.contains(target));
        results.add(new Result("CircularDoublyLinkedList", ringCounter.mostRecent(), inRing));

        OperationCounter queueCounter = new OperationCounter();
        SimpleQueue<Song> queue = new SimpleQueue<>(queueCounter);
        for (Song song : songs) {
            queue.enqueue(song);
        }
        boolean inQueue = queueCounter.measure(
                OPERATION, "SimpleQueue", n, () -> queue.contains(target));
        results.add(new Result("SimpleQueue", queueCounter.mostRecent(), inQueue));

        OperationCounter treeCounter = new OperationCounter();
        BinarySearchTree<Song> tree = new BinarySearchTree<>(Song.BY_TITLE, treeCounter);
        tree.insertAll(songs);
        boolean inTree = treeCounter.measure(
                OPERATION, "BinarySearchTree", n, () -> tree.search(target));
        results.add(new Result("BinarySearchTree", treeCounter.mostRecent(), inTree));

        return results;
    }

    private StructureComparison() {
        throw new AssertionError("StructureComparison is a utility holder and must not be instantiated");
    }
}
