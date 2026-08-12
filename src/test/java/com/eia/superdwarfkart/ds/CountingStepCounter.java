package com.eia.superdwarfkart.ds;

/**
 * Test double for {@link StepCounter} that simply tallies what it is told.
 */
class CountingStepCounter implements StepCounter {

    private int comparisons;
    private int pointerHops;

    @Override
    public void comparison() {
        comparisons++;
    }

    @Override
    public void pointerHop() {
        pointerHops++;
    }

    int comparisons() {
        return comparisons;
    }

    int pointerHops() {
        return pointerHops;
    }

    int total() {
        return comparisons + pointerHops;
    }

    void reset() {
        comparisons = 0;
        pointerHops = 0;
    }
}
