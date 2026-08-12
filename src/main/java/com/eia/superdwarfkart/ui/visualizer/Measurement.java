package com.eia.superdwarfkart.ui.visualizer;

/**
 * What one operation actually cost, measured rather than predicted.
 *
 * <p>This is the number that makes the complexity panel worth looking at. {@code O(log n)} is a
 * claim; 9 steps against a library of 500 is the claim being kept, and 250 steps for the same
 * search over the circular list is the contrast the whole project is built to show.
 *
 * @param operation   the operation's name, spelled as the mode's {@code complexities()} spells it
 * @param structure   the hand-written structure that did the work
 * @param n           how many elements the structure held when the operation ran
 * @param comparisons element comparisons performed
 * @param pointerHops links traversed
 */
public record Measurement(String operation, String structure, int n, int comparisons, int pointerHops) {

    /**
     * @return the total elementary steps: comparisons plus pointer hops
     */
    public int steps() {
        return comparisons + pointerHops;
    }

    @Override
    public String toString() {
        return operation + " on " + structure + ": " + steps() + " steps (n = " + n + ")";
    }
}
