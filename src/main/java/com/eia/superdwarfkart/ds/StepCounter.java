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
}
