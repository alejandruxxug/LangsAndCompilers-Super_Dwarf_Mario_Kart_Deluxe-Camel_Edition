package com.eia.superdwarfkart.ds;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A hand-written strict first-in-first-out queue, backing playback mode 2 (arrival order).
 *
 * <p>Written from scratch as required by the assignment: no {@code java.util} collection backs
 * it. It keeps a head and a tail pointer, so both ends are constant time; enqueuing at the tail
 * without a tail pointer would be O(n) and is the usual mistake here.
 *
 * <p><strong>This queue is a view, not storage.</strong> It is built from the library's master
 * collection when the mode is selected, and dequeuing consumes only this view. The library keeps
 * every song, so emptying the queue loses nothing and the mode can be rebuilt at any time.
 *
 * <p>A queue cannot be walked backwards, so the arrival-order mode reports that it does not
 * support {@code previous()} and the user interface disables that control.
 *
 * @param <T> element type
 */
public class SimpleQueue<T> implements Iterable<T> {

    /**
     * A singly linked cell. Only a forward link exists: nothing in a FIFO ever needs to look
     * backwards, and the missing link is what makes going back impossible by construction
     * rather than by convention.
     */
    private static final class Node<T> {
        private final T value;
        private Node<T> next;

        private Node(T value) {
            this.value = value;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    /** Incremented on every structural change so iterators can detect concurrent modification. */
    private int modCount;

    private final StepCounter counter;

    /**
     * Creates an empty queue with instrumentation disabled.
     *
     * <p>Time complexity: O(1).
     */
    public SimpleQueue() {
        this(StepCounter.NO_OP);
    }

    /**
     * Creates an empty queue that reports its work to the given counter.
     *
     * <p>Time complexity: O(1).
     *
     * @param counter step counter; must not be {@code null}, pass {@link StepCounter#NO_OP} to disable
     */
    public SimpleQueue(StepCounter counter) {
        this.counter = Objects.requireNonNull(counter, "counter must not be null; use StepCounter.NO_OP");
    }

    /**
     * Adds an element at the tail of the queue.
     *
     * <p>Time complexity: O(1) - the tail pointer removes the need to walk the list.
     *
     * @param value the element to add; may be {@code null}
     */
    public void enqueue(T value) {
        Node<T> node = new Node<>(value);
        if (tail == null) {
            head = node;
        } else {
            tail.next = node;
            counter.pointerHop();
        }
        tail = node;
        size++;
        modCount++;
    }

    /**
     * Removes and returns the element at the head of the queue.
     *
     * <p>Time complexity: O(1).
     *
     * <p>This consumes the queue view only. The song remains in the library, which owns the
     * canonical collection.
     *
     * @return the element that had been waiting longest
     * @throws NoSuchElementException if the queue is empty
     */
    public T dequeue() {
        if (head == null) {
            throw new NoSuchElementException("Cannot dequeue from an empty queue");
        }
        Node<T> first = head;
        head = first.next;
        counter.pointerHop();
        if (head == null) {
            tail = null;
        }
        first.next = null;
        size--;
        modCount++;
        return first.value;
    }

    /**
     * Returns the element at the head without removing it.
     *
     * <p>Time complexity: O(1).
     *
     * <p>This is the non-mutating read the prefetcher needs to know which song comes next.
     * Implementing it by dequeuing and re-enqueuing would silently eat a song per call.
     *
     * @return the element that has been waiting longest
     * @throws NoSuchElementException if the queue is empty
     */
    public T peek() {
        if (head == null) {
            throw new NoSuchElementException("Cannot peek into an empty queue");
        }
        return head.value;
    }

    /**
     * Reports whether the queue holds no elements.
     *
     * <p>Time complexity: O(1).
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Reports whether an equal element is waiting, without consuming anything.
     *
     * <p>Time complexity: O(n), and it cannot be better. A queue has no ordering to exploit and
     * no index, so looking something up means comparing against every element in turn. That is
     * precisely the point the structure comparison makes: the same search that costs about nine
     * comparisons in the tree costs a walk of half the queue here.
     *
     * @param value the element to look for; {@code null} matches a stored {@code null}
     * @return {@code true} if found
     */
    public boolean contains(T value) {
        for (Node<T> node = head; node != null; node = node.next) {
            counter.comparison();
            if (Objects.equals(node.value, value)) {
                return true;
            }
            counter.pointerHop();
        }
        return false;
    }

    /**
     * Returns how many elements are queued.
     *
     * <p>Time complexity: O(1) - the count is maintained, never recomputed by walking.
     *
     * @return the element count
     */
    public int size() {
        return size;
    }

    /**
     * Discards every element.
     *
     * <p>Time complexity: O(1) - dropping the head is enough; the garbage collector reclaims
     * the unreachable chain.
     */
    public void clear() {
        head = null;
        tail = null;
        size = 0;
        modCount++;
    }

    /**
     * Returns an iterator that walks from head to tail without consuming anything.
     *
     * <p>Time complexity: O(1) to create, O(n) to exhaust.
     *
     * <p>This is what lets the starting-grid visualizer draw every waiting kart without
     * destroying the queue it is drawing.
     *
     * @return a non-destructive front-to-back iterator
     */
    @Override
    public Iterator<T> iterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private Node<T> nextNode = head;

            @Override
            public boolean hasNext() {
                return nextNode != null;
            }

            @Override
            public T next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException("Queue was modified during iteration");
                }
                if (nextNode == null) {
                    throw new NoSuchElementException("Iterator exhausted");
                }
                T value = nextNode.value;
                nextNode = nextNode.next;
                return value;
            }
        };
    }

    /**
     * Renders the queue from head to tail without consuming it.
     *
     * <p>Time complexity: O(n).
     *
     * @return a readable representation of the waiting elements
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SimpleQueue[head -> ");
        for (Node<T> n = head; n != null; n = n.next) {
            sb.append(n.value);
            if (n.next != null) {
                sb.append(", ");
            }
        }
        return sb.append(" <- tail]").toString();
    }
}
