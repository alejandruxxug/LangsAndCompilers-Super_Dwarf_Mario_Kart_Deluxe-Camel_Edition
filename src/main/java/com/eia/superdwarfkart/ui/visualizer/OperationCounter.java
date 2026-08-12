package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.ds.StepCounter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Counts what the hand-written structures actually do, so measured cost can be shown beside
 * theoretical cost.
 *
 * <p>It is the {@link StepCounter} the modes are handed at construction. Between
 * {@link #begin(String, String, int)} and {@link #end()} every comparison and every pointer hop
 * the structure performs is tallied; closing the scope files the result as a {@link Measurement},
 * both as the newest value for that operation and as one more point on the scatter plot.
 *
 * <p>Two rules keep the numbers honest:
 *
 * <ul>
 *   <li><strong>Steps counted outside a scope are discarded.</strong> The interface reads the
 *       structures while redrawing - the "up next" label peeks, which in a tree is a full
 *       successor walk - and that work belongs to no operation. Opening a scope clears whatever
 *       has accumulated since the last one closed, so stray reads cannot inflate the next
 *       measurement.</li>
 *   <li><strong>Nested scopes fold into the outermost.</strong> Selecting a song searches; the
 *       search is part of the select, not a separate measurement that would overwrite it.</li>
 * </ul>
 *
 * <p>This class deliberately holds no JavaFX: it is plain Java so it can be unit-tested without a
 * graphics toolkit, and it publishes changes through listeners the views subscribe to. It must
 * never be given to anything on the audio or render hot path - it exists to instrument user
 * actions, which happen a handful of times a second at most.
 */
public class OperationCounter implements StepCounter {

    /**
     * How many measurements the scatter plot keeps. Old points are dropped from the front once
     * this is reached, so a long demonstration cannot grow the list without bound.
     */
    public static final int MAX_SAMPLES = 400;

    /** Newest measurement per operation name, in the order the operations were first seen. */
    private final Map<String, Measurement> latest = new LinkedHashMap<>();

    /** Every measurement taken, oldest first, capped at {@link #MAX_SAMPLES}. */
    private final List<Measurement> samples = new ArrayList<>();

    private final List<Consumer<Measurement>> listeners = new ArrayList<>();

    private int comparisons;
    private int pointerHops;

    /** Nesting depth; only the outermost scope files a measurement. */
    private int depth;

    private String operation;
    private String structure;
    private int elementCount;

    @Override
    public void comparison() {
        comparisons++;
    }

    @Override
    public void pointerHop() {
        pointerHops++;
    }

    @Override
    public void begin(String operation, String structure, int n) {
        if (depth++ > 0) {
            // Already inside a measurement: this work belongs to the operation that started it.
            return;
        }
        this.operation = operation;
        this.structure = structure;
        this.elementCount = n;
        // Anything counted since the previous scope closed was incidental. Drop it here rather
        // than at end(), so it cannot be attributed to this operation.
        comparisons = 0;
        pointerHops = 0;
    }

    @Override
    public void end() {
        if (depth == 0 || --depth > 0) {
            return;
        }
        record(new Measurement(operation, structure, elementCount, comparisons, pointerHops));
    }

    /**
     * Measures an operation that produces a value.
     *
     * <p>Convenience for callers that are not already bracketing - the structure comparison and
     * the tests. Normal playback is bracketed inside the player, where the scope can close before
     * the interface redraws.
     *
     * @param <R>       the result type
     * @param operation the operation's name
     * @param structure the structure doing the work
     * @param n         how many elements it holds
     * @param work      the operation to run
     * @return whatever the work returned
     */
    public <R> R measure(String operation, String structure, int n, java.util.function.Supplier<R> work) {
        begin(operation, structure, n);
        try {
            return work.get();
        } finally {
            end();
        }
    }

    /**
     * Measures an operation that produces nothing.
     *
     * @param operation the operation's name
     * @param structure the structure doing the work
     * @param n         how many elements it holds
     * @param work      the operation to run
     */
    public void measure(String operation, String structure, int n, Runnable work) {
        measure(operation, structure, n, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Returns the most recent measurement of an operation.
     *
     * @param operation the operation's name, as {@code complexities()} spells it
     * @return the newest measurement, or {@code null} if that operation has not run yet
     */
    public Measurement latest(String operation) {
        return latest.get(operation);
    }

    /** @return every measurement so far, oldest first; a snapshot, safe to iterate */
    public List<Measurement> samples() {
        return List.copyOf(samples);
    }

    /** @return the newest measurement of any operation, or {@code null} if none has run */
    public Measurement mostRecent() {
        return samples.isEmpty() ? null : samples.get(samples.size() - 1);
    }

    /** @return how many measurements have been recorded, including any already dropped */
    public int sampleCount() {
        return samples.size();
    }

    /**
     * Discards every measurement, so a demonstration can start from a clean plot.
     *
     * <p>Listeners are notified with {@code null}, which they read as "everything changed".
     */
    public void reset() {
        latest.clear();
        samples.clear();
        comparisons = 0;
        pointerHops = 0;
        depth = 0;
        notifyListeners(null);
    }

    /**
     * Registers a listener called whenever a measurement is filed.
     *
     * @param listener receives the new measurement, or {@code null} after a {@link #reset()}
     */
    public void addListener(Consumer<Measurement> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(Consumer<Measurement> listener) {
        listeners.remove(listener);
    }

    /**
     * Files a measurement taken elsewhere, such as by the structure comparison's private
     * counters, so it appears on the same plot as everything else.
     *
     * @param measurement the measurement to record; must not be {@code null}
     */
    public void record(Measurement measurement) {
        Objects.requireNonNull(measurement, "measurement must not be null");
        latest.put(measurement.operation(), measurement);
        samples.add(measurement);
        if (samples.size() > MAX_SAMPLES) {
            samples.remove(0);
        }
        notifyListeners(measurement);
    }

    private void notifyListeners(Measurement measurement) {
        // A copy, so a listener may unregister itself while being notified.
        for (Consumer<Measurement> listener : List.copyOf(listeners)) {
            listener.accept(measurement);
        }
    }
}
