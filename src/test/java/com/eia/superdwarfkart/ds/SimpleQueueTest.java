package com.eia.superdwarfkart.ds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SimpleQueue")
class SimpleQueueTest {

    @Test
    @DisplayName("dequeues in exactly the order elements arrived")
    void fifoOrder() {
        SimpleQueue<String> queue = new SimpleQueue<>();
        queue.enqueue("first");
        queue.enqueue("second");
        queue.enqueue("third");

        assertEquals("first", queue.dequeue());
        assertEquals("second", queue.dequeue());
        assertEquals("third", queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("interleaved enqueue and dequeue keep arrival order")
    void fifoOrderInterleaved() {
        SimpleQueue<String> queue = new SimpleQueue<>();
        queue.enqueue("A");
        queue.enqueue("B");

        assertEquals("A", queue.dequeue());

        queue.enqueue("C");

        assertEquals("B", queue.dequeue());
        assertEquals("C", queue.dequeue());
    }

    @Test
    @DisplayName("enqueueing after the queue drains still works")
    void enqueueAfterDraining() {
        SimpleQueue<String> queue = new SimpleQueue<>();
        queue.enqueue("A");
        queue.dequeue();
        assertTrue(queue.isEmpty());

        // The tail pointer must have been cleared with the head, or this element is lost.
        queue.enqueue("B");

        assertEquals(1, queue.size());
        assertEquals("B", queue.peek());
        assertEquals("B", queue.dequeue());
    }

    @Test
    @DisplayName("peek returns the head without consuming it")
    void peekDoesNotConsume() {
        SimpleQueue<String> queue = new SimpleQueue<>();
        queue.enqueue("A");
        queue.enqueue("B");

        assertEquals("A", queue.peek());
        assertEquals("A", queue.peek());
        assertEquals(2, queue.size(), "peeking must not shrink the queue");
        assertEquals("A", queue.dequeue(), "peek must have promised what dequeue delivers");
    }

    @Test
    @DisplayName("size and isEmpty track the contents")
    void sizeTracking() {
        SimpleQueue<String> queue = new SimpleQueue<>();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());

        queue.enqueue("A");
        queue.enqueue("B");
        assertFalse(queue.isEmpty());
        assertEquals(2, queue.size());

        queue.dequeue();
        assertEquals(1, queue.size());

        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("an empty queue refuses to dequeue or peek rather than returning null")
    void emptyQueueThrows() {
        SimpleQueue<String> queue = new SimpleQueue<>();

        assertThrows(NoSuchElementException.class, queue::dequeue);
        assertThrows(NoSuchElementException.class, queue::peek);
    }

    @Test
    @DisplayName("iteration walks head to tail without consuming anything")
    void iterationIsNonDestructive() {
        SimpleQueue<String> queue = new SimpleQueue<>();
        queue.enqueue("A");
        queue.enqueue("B");
        queue.enqueue("C");

        List<String> seen = new ArrayList<>();
        queue.forEach(seen::add);

        assertEquals(List.of("A", "B", "C"), seen);
        assertEquals(3, queue.size(), "drawing the starting grid must not empty the grid");
    }

    @Test
    @DisplayName("draining the queue leaves the master library untouched")
    void drainingDoesNotDestroyTheLibrary() {
        // The queue is a view built from the library, never the storage of it. This is the
        // constraint that stops mode 2 from deleting the user's songs as it plays them.
        List<String> library = new ArrayList<>(List.of("song1", "song2", "song3"));

        SimpleQueue<String> queue = new SimpleQueue<>();
        library.forEach(queue::enqueue);

        while (!queue.isEmpty()) {
            queue.dequeue();
        }

        assertTrue(queue.isEmpty());
        assertEquals(List.of("song1", "song2", "song3"), library,
                "the library must survive the queue being drained");

        // And the mode can be rebuilt from the untouched library.
        library.forEach(queue::enqueue);
        assertEquals(3, queue.size());
        assertEquals("song1", queue.peek());
    }

    @Test
    @DisplayName("the step counter observes pointer hops")
    void countsSteps() {
        CountingStepCounter counter = new CountingStepCounter();
        SimpleQueue<String> queue = new SimpleQueue<>(counter);

        queue.enqueue("A");
        queue.enqueue("B");
        counter.reset();
        queue.dequeue();

        assertTrue(counter.pointerHops() >= 1, "dequeue moves the head pointer exactly once");
        assertTrue(counter.pointerHops() <= 2, "a FIFO dequeue must stay constant time, got "
                + counter.pointerHops() + " hops");
    }
}
