package com.eia.superdwarfkart.ds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CircularDoublyLinkedList")
class CircularDoublyLinkedListTest {

    private static CircularDoublyLinkedList<String> listOf(String... values) {
        CircularDoublyLinkedList<String> list = new CircularDoublyLinkedList<>();
        for (String value : values) {
            list.insert(value);
        }
        return list;
    }

    @Nested
    @DisplayName("wraparound")
    class Wraparound {

        @Test
        @DisplayName("next() past the tail continues at the head")
        void nextWrapsForward() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertEquals("A", list.current());
            assertEquals("B", list.next());
            assertEquals("C", list.next());
            // Past the tail is the head: the traversal never ends.
            assertEquals("A", list.next());
            assertEquals("B", list.next());
        }

        @Test
        @DisplayName("previous() before the head continues at the tail")
        void previousWrapsBackward() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertEquals("A", list.current());
            // Before the head is the tail.
            assertEquals("C", list.previous());
            assertEquals("B", list.previous());
            assertEquals("A", list.previous());
            assertEquals("C", list.previous());
        }

        @Test
        @DisplayName("previous() undoes next() at every position")
        void previousUndoesNext() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C", "D");

            for (int i = 0; i < 12; i++) {
                String before = list.current();
                String advanced = list.next();
                assertEquals(before, list.previous(),
                        "stepping back must return to where we came from, after advancing to " + advanced);
            }
        }

        @Test
        @DisplayName("a single element points at itself in both directions")
        void singleElementRing() {
            CircularDoublyLinkedList<String> list = listOf("ONLY");

            assertEquals("ONLY", list.next());
            assertEquals("ONLY", list.next());
            assertEquals("ONLY", list.previous());
            assertEquals("ONLY", list.current());
        }

        @Test
        @DisplayName("a full lap of n steps returns to the starting element")
        void fullLapReturnsToStart() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C", "D", "E");

            String start = list.current();
            for (int i = 0; i < list.size(); i++) {
                list.next();
            }
            assertEquals(start, list.current());
            assertTrue(list.isAtHead());
        }
    }

    @Nested
    @DisplayName("iterator")
    class IteratorBehaviour {

        @Test
        @DisplayName("walks exactly one lap so for-each terminates on an endless list")
        void iteratesExactlyOneLap() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            List<String> seen = new ArrayList<>();
            for (String value : list) {
                seen.add(value);
                if (seen.size() > 10) {
                    break; // guard: if the iterator were endless this would trip
                }
            }

            assertEquals(List.of("A", "B", "C"), seen);
        }

        @Test
        @DisplayName("always starts at the head regardless of where the cursor sits")
        void iterationOrderIsIndependentOfCursor() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");
            list.next();
            list.next();

            List<String> seen = new ArrayList<>();
            list.forEach(seen::add);

            assertEquals(List.of("A", "B", "C"), seen);
        }

        @Test
        @DisplayName("reports itself exhausted after one lap")
        void throwsWhenExhausted() {
            CircularDoublyLinkedList<String> list = listOf("A", "B");

            var it = list.iterator();
            it.next();
            it.next();
            assertFalse(it.hasNext());
            assertThrows(NoSuchElementException.class, it::next);
        }

        @Test
        @DisplayName("an empty list iterates zero times")
        void emptyListIteratesZeroTimes() {
            CircularDoublyLinkedList<String> list = new CircularDoublyLinkedList<>();

            int count = 0;
            for (String ignored : list) {
                count++;
            }
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("insert and remove")
    class InsertAndRemove {

        @Test
        @DisplayName("insert appends at the tail, keeping insertion order")
        void insertKeepsOrder() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertEquals(3, list.size());
            assertEquals(List.of("A", "B", "C"), toList(list));
        }

        @Test
        @DisplayName("removing the head advances the head and keeps the ring closed")
        void removeHead() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertTrue(list.remove("A"));
            assertEquals(2, list.size());
            assertEquals(List.of("B", "C"), toList(list));
            // The ring must still close: forward from C is B.
            assertEquals("B", list.current());
            assertEquals("C", list.next());
            assertEquals("B", list.next());
        }

        @Test
        @DisplayName("removing the tail keeps the ring closed")
        void removeTail() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertTrue(list.remove("C"));
            assertEquals(List.of("A", "B"), toList(list));
            assertEquals("B", list.next());
            assertEquals("A", list.next());
        }

        @Test
        @DisplayName("removing a middle element relinks both neighbours")
        void removeMiddle() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertTrue(list.remove("B"));
            assertEquals("C", list.next());
            assertEquals("A", list.previous());
        }

        @Test
        @DisplayName("removing the only element empties the list")
        void removeOnlyElement() {
            CircularDoublyLinkedList<String> list = listOf("ONLY");

            assertTrue(list.remove("ONLY"));
            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertThrows(NoSuchElementException.class, list::next);
        }

        @Test
        @DisplayName("removing the element under the cursor moves the cursor forward")
        void removeCursorMovesCursorForward() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");
            list.next(); // cursor on B

            assertTrue(list.remove("B"));
            assertEquals("C", list.current());
        }

        @Test
        @DisplayName("removing an absent element reports false and changes nothing")
        void removeAbsent() {
            CircularDoublyLinkedList<String> list = listOf("A", "B");

            assertFalse(list.remove("Z"));
            assertEquals(2, list.size());
        }

        @Test
        @DisplayName("clear empties the list")
        void clearEmpties() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            list.clear();

            assertTrue(list.isEmpty());
            assertThrows(NoSuchElementException.class, list::current);
        }
    }

    @Nested
    @DisplayName("cursor")
    class Cursor {

        @Test
        @DisplayName("peekNext and peekPrevious do not move the cursor")
        void peeksDoNotMove() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertEquals("B", list.peekNext());
            assertEquals("C", list.peekPrevious());
            assertEquals("A", list.current(), "peeking must not advance the cursor");
            assertEquals("B", list.next(), "next() must still return what peekNext() promised");
        }

        @Test
        @DisplayName("moveTo places the cursor on a chosen element")
        void moveTo() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");

            assertTrue(list.moveTo("C"));
            assertEquals("C", list.current());
            assertEquals("A", list.next());
            assertFalse(list.moveTo("Z"));
        }

        @Test
        @DisplayName("resetCursor returns to the head")
        void resetCursor() {
            CircularDoublyLinkedList<String> list = listOf("A", "B", "C");
            list.next();

            list.resetCursor();

            assertEquals("A", list.current());
        }
    }

    @Test
    @DisplayName("an empty list refuses to traverse rather than returning null")
    void emptyListThrows() {
        CircularDoublyLinkedList<String> list = new CircularDoublyLinkedList<>();

        assertThrows(NoSuchElementException.class, list::next);
        assertThrows(NoSuchElementException.class, list::previous);
        assertThrows(NoSuchElementException.class, list::current);
        assertThrows(NoSuchElementException.class, list::peekNext);
    }

    @Test
    @DisplayName("the step counter observes comparisons and pointer hops")
    void countsSteps() {
        CountingStepCounter counter = new CountingStepCounter();
        CircularDoublyLinkedList<String> list = new CircularDoublyLinkedList<>(counter);
        list.insertAll(List.of("A", "B", "C", "D"));

        counter.reset();
        list.contains("D"); // walks the whole ring

        assertTrue(counter.comparisons() >= 4,
                "a linear search for the last element should compare at least n times, got "
                        + counter.comparisons());
    }

    private static List<String> toList(CircularDoublyLinkedList<String> list) {
        List<String> out = new ArrayList<>();
        list.forEach(out::add);
        return out;
    }
}
