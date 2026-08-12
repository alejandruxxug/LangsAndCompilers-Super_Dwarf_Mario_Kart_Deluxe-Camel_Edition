package com.eia.superdwarfkart.ds;

/**
 * Counts the elementary steps a structure performs, so the user interface can show measured
 * cost next to theoretical complexity.
 *
 * <p>The interface is declared here, in the data structure package, rather than in the
 * visualizer that consumes it. The structures must not depend on the presentation layer, so
 * {@code ui.visualizer.OperationCounter} implements this seam instead and is passed in through
 * a constructor.
 *
 * <p>Implementations must be cheap. A counter is invoked once per comparison and once per
 * pointer hop, which on a large traversal is a very hot loop. {@link #NO_OP} is the default and
 * costs nothing beyond a non-virtual call the JIT can remove entirely.
 */
public interface StepCounter {

    /** A counter that records nothing, used whenever instrumentation is disabled. */
    StepCounter NO_OP = new StepCounter() {
        @Override
        public void comparison() {
            // intentionally empty
        }

        @Override
        public void pointerHop() {
            // intentionally empty
        }
    };

    /** Records one comparison between two elements. */
    void comparison();

    /** Records one traversal of a link between nodes. */
    void pointerHop();

    /**
     * Marks the start of an operation whose cost is being measured.
     *
     * <p>The steps counted between this call and the matching {@link #end()} are what the
     * complexity panel shows beside the theoretical figure, so the bracket has to sit tightly
     * around the structure call itself. Wrapping the notification that follows it as well would
     * fold the cost of redrawing the "up next" label - which peeks, and in a tree that is a whole
     * successor walk - into the measurement of the navigation.
     *
     * <p>Nested calls fold into the outermost one rather than starting a second measurement.
     *
     * @param operation the operation's name, spelled as {@code complexities()} spells it so the
     *                  measured value lands on the matching row
     * @param structure the hand-written structure doing the work
     * @param n         how many elements the structure holds as the operation begins
     */
    default void begin(String operation, String structure, int n) {
        // intentionally empty
    }

    /** Marks the end of the operation started by {@link #begin(String, String, int)}. */
    default void end() {
        // intentionally empty
    }
}
