package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.ds.BinarySearchTree;
import com.eia.superdwarfkart.ds.CircularDoublyLinkedList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counter that turns the complexity panel from a claim into an observation.
 *
 * <p>The two rules worth testing are the ones that keep the numbers honest: work done outside a
 * measurement scope must not be billed to the next operation, and a nested scope must fold into
 * the one that contains it rather than overwriting it.
 */
@DisplayName("OperationCounter")
class OperationCounterTest {

    @Nested
    @DisplayName("measurement scopes")
    class Scopes {

        @Test
        @DisplayName("records the steps taken between begin and end")
        void recordsSteps() {
            OperationCounter counter = new OperationCounter();
            counter.begin("next()", "TestStructure", 10);
            counter.comparison();
            counter.comparison();
            counter.pointerHop();
            counter.end();

            Measurement measurement = counter.latest("next()");
            assertNotNull(measurement);
            assertEquals(2, measurement.comparisons());
            assertEquals(1, measurement.pointerHops());
            assertEquals(3, measurement.steps());
            assertEquals(10, measurement.n());
            assertEquals("TestStructure", measurement.structure());
        }

        @Test
        @DisplayName("discards steps taken outside a scope")
        void discardsUnbracketedWork() {
            OperationCounter counter = new OperationCounter();

            // The interface reads the structures while redrawing - peeking at the next song, for
            // instance - and that work belongs to no operation. It must not inflate the next one.
            counter.comparison();
            counter.comparison();
            counter.pointerHop();

            counter.begin("next()", "TestStructure", 4);
            counter.comparison();
            counter.end();

            assertEquals(1, counter.latest("next()").steps());
        }

        @Test
        @DisplayName("folds a nested scope into the one containing it")
        void nestedScopesFold() {
            OperationCounter counter = new OperationCounter();

            counter.begin("select(song)", "TestStructure", 8);
            counter.comparison();
            counter.begin("search", "TestStructure", 8);
            counter.comparison();
            counter.comparison();
            counter.end();
            counter.comparison();
            counter.end();

            // One measurement, holding everything the outer operation cost.
            assertEquals(4, counter.latest("select(song)").steps());
            assertNull(counter.latest("search"), "a nested scope must not file its own measurement");
            assertEquals(1, counter.sampleCount());
        }

        @Test
        @DisplayName("closes the scope even when the work throws")
        void closesOnException() {
            OperationCounter counter = new OperationCounter();

            assertThrows(IllegalStateException.class, () ->
                    counter.measure("next()", "TestStructure", 2, () -> {
                        counter.comparison();
                        throw new IllegalStateException("boom");
                    }));

            // The measurement is still filed, and the counter is not left inside a scope.
            assertEquals(1, counter.latest("next()").steps());
            counter.begin("previous()", "TestStructure", 2);
            counter.pointerHop();
            counter.end();
            assertEquals(1, counter.latest("previous()").steps());
        }

        @Test
        @DisplayName("ignores an unmatched end")
        void unmatchedEndIsHarmless() {
            OperationCounter counter = new OperationCounter();
            counter.end();
            assertEquals(0, counter.sampleCount());
        }
    }

    @Nested
    @DisplayName("samples")
    class Samples {

        @Test
        @DisplayName("keeps the newest measurement per operation and every point for the plot")
        void keepsLatestAndHistory() {
            OperationCounter counter = new OperationCounter();
            counter.measure("next()", "S", 1, () -> counter.comparison());
            counter.measure("next()", "S", 2, () -> {
                counter.comparison();
                counter.comparison();
            });

            assertEquals(2, counter.latest("next()").steps(), "the newest value wins the row");
            assertEquals(2, counter.sampleCount(), "both points stay on the plot");
        }

        @Test
        @DisplayName("drops the oldest points rather than growing without bound")
        void capsTheHistory() {
            OperationCounter counter = new OperationCounter();
            for (int i = 0; i < OperationCounter.MAX_SAMPLES + 25; i++) {
                counter.measure("next()", "S", i, () -> counter.comparison());
            }
            assertEquals(OperationCounter.MAX_SAMPLES, counter.sampleCount());
            // The surviving window ends at the most recent measurement.
            assertEquals(OperationCounter.MAX_SAMPLES + 24, counter.mostRecent().n());
        }

        @Test
        @DisplayName("notifies listeners as measurements arrive and once on reset")
        void notifiesListeners() {
            OperationCounter counter = new OperationCounter();
            List<Measurement> seen = new ArrayList<>();
            counter.addListener(seen::add);

            counter.measure("next()", "S", 3, () -> counter.pointerHop());
            assertEquals(1, seen.size());
            assertEquals(1, seen.get(0).steps());

            counter.reset();
            assertEquals(2, seen.size());
            assertNull(seen.get(1), "a reset is published as a null measurement");
            assertEquals(0, counter.sampleCount());
            assertNull(counter.latest("next()"));
        }
    }

    @Nested
    @DisplayName("against the real structures")
    class AgainstRealStructures {

        @Test
        @DisplayName("a tree search costs far fewer steps than a walk of the ring")
        void treeBeatsRing() {
            int n = 512;
            OperationCounter counter = new OperationCounter();

            BinarySearchTree<Integer> tree = new BinarySearchTree<>(Integer::compare, counter);
            CircularDoublyLinkedList<Integer> ring = new CircularDoublyLinkedList<>(counter);
            // Inserted in an interleaved order so the tree does not degenerate; the ring's cost
            // does not depend on the order at all, which is itself the point.
            for (int i = 0; i < n; i++) {
                int value = (i * 37) % n;
                tree.insert(value);
                ring.insert(value);
            }

            counter.measure("search", "BinarySearchTree", n, () -> tree.search(n - 1));
            int treeSteps = counter.latest("search").steps();

            counter.measure("contains", "CircularDoublyLinkedList", n, () -> ring.contains(n - 1));
            int ringSteps = counter.latest("contains").steps();

            // A balanced tree over 512 elements is about nine deep; the ring has to walk.
            assertTrue(treeSteps < 40,
                    "a tree search over " + n + " elements should be tens of steps, was " + treeSteps);
            assertTrue(ringSteps > treeSteps * 4,
                    "the ring walk (" + ringSteps + ") should dwarf the tree search (" + treeSteps + ")");
        }

        @Test
        @DisplayName("a degenerate tree costs what a linked list costs")
        void degenerateTreeLosesItsAdvantage() {
            int n = 200;
            OperationCounter counter = new OperationCounter();
            BinarySearchTree<Integer> sorted = new BinarySearchTree<>(Integer::compare, counter);
            for (int i = 0; i < n; i++) {
                sorted.insert(i);
            }

            counter.measure("search", "BinarySearchTree", n, () -> sorted.search(n - 1));

            // Inserted already in order, the tree is a straight line and the search walks it all.
            // This is exactly what the visualizer's sorted-insert button demonstrates.
            assertEquals(n - 1, sorted.height());
            assertTrue(counter.latest("search").steps() >= n,
                    "a degenerate tree must cost about n, was " + counter.latest("search").steps());
        }
    }
}
