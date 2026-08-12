package com.eia.superdwarfkart.ds;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A hand-written circular doubly linked list, backing playback mode 1 (shuffle).
 *
 * <p>Written from scratch as required by the assignment: no {@code java.util} collection backs
 * it. Every node holds a {@code prev} and a {@code next} link and <strong>there are no null
 * terminators</strong>. A one-element list points at itself in both directions, so past the
 * tail is the head and before the head is the tail. Traversal never ends.
 *
 * <p>The list keeps a cursor, which is what {@link #next()} and {@link #previous()} move. The
 * shuffle mode inserts songs in a shuffled order <em>once, at load time</em>, rather than
 * picking a random song per step; that is what makes {@link #previous()} return the song
 * actually played before, and what makes {@link #peekNext()} deterministic.
 *
 * @param <T> element type
 */
public class CircularDoublyLinkedList<T> implements Iterable<T> {

    /** A cell with links in both directions. Neither link is ever null while the node is in a list. */
    private static final class Node<T> {
        private final T value;
        private Node<T> prev;
        private Node<T> next;

        private Node(T value) {
            this.value = value;
        }
    }

    /** Entry point of the ring; the element the cursor returns to after a full lap. */
    private Node<T> head;

    /** Current position, moved by {@link #next()} and {@link #previous()}. */
    private Node<T> cursor;

    private int size;

    /** Incremented on every structural change so iterators can detect concurrent modification. */
    private int modCount;

    private final StepCounter counter;

    /**
     * Creates an empty list with instrumentation disabled.
     *
     * <p>Time complexity: O(1).
     */
    public CircularDoublyLinkedList() {
        this(StepCounter.NO_OP);
    }

    /**
     * Creates an empty list that reports its work to the given counter.
     *
     * <p>Time complexity: O(1).
     *
     * @param counter step counter; must not be {@code null}, pass {@link StepCounter#NO_OP} to disable
     */
    public CircularDoublyLinkedList(StepCounter counter) {
        this.counter = Objects.requireNonNull(counter, "counter must not be null; use StepCounter.NO_OP");
    }

    // ------------------------------------------------------------------
    // Modification
    // ------------------------------------------------------------------

    /**
     * Appends an element at the end of the ring, that is, immediately before the head.
     *
     * <p>Time complexity: O(1) - the head's {@code prev} link is the tail, so no walk is needed.
     *
     * @param value the element to insert; may be {@code null}
     */
    public void insert(T value) {
        Node<T> node = new Node<>(value);
        if (head == null) {
            // First element: a ring of one closes onto itself in both directions.
            node.next = node;
            node.prev = node;
            head = node;
            cursor = node;
        } else {
            Node<T> tail = head.prev;
            counter.pointerHop();
            node.prev = tail;
            node.next = head;
            tail.next = node;
            head.prev = node;
        }
        size++;
        modCount++;
    }

    /**
     * Appends every element of the given source, in the order it produces them.
     *
     * <p>Time complexity: O(k) for k elements supplied.
     *
     * @param values elements to append; must not be {@code null}
     */
    public void insertAll(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values must not be null");
        for (T value : values) {
            insert(value);
        }
    }

    /**
     * Removes the first node holding an element equal to the given one.
     *
     * <p>Time complexity: O(n) to find the node, O(1) to unlink it.
     *
     * <p>Removing the head advances the head; removing the cursor moves the cursor forward;
     * removing the last remaining element empties the list.
     *
     * @param value the element to remove; {@code null} matches a stored {@code null}
     * @return {@code true} if an element was removed
     */
    public boolean remove(T value) {
        Node<T> node = findNode(value);
        if (node == null) {
            return false;
        }
        unlink(node);
        return true;
    }

    /**
     * Unlinks a node from the ring and repairs head and cursor if they pointed at it.
     *
     * @param node the node to unlink; must be part of this list
     */
    private void unlink(Node<T> node) {
        if (size == 1) {
            head = null;
            cursor = null;
        } else {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            counter.pointerHop();
            if (head == node) {
                head = node.next;
            }
            if (cursor == node) {
                cursor = node.next;
            }
        }
        node.prev = null;
        node.next = null;
        size--;
        modCount++;
    }

    /**
     * Discards every element.
     *
     * <p>Time complexity: O(1) - dropping the head is enough; the ring becomes unreachable.
     */
    public void clear() {
        head = null;
        cursor = null;
        size = 0;
        modCount++;
    }

    // ------------------------------------------------------------------
    // Traversal
    // ------------------------------------------------------------------

    /**
     * Moves the cursor one step forward and returns the element there.
     *
     * <p>Time complexity: O(1).
     *
     * <p>Past the tail is the head: this never runs out.
     *
     * @return the element the cursor now sits on
     * @throws NoSuchElementException if the list is empty
     */
    public T next() {
        requireNotEmpty();
        cursor = cursor.next;
        counter.pointerHop();
        return cursor.value;
    }

    /**
     * Moves the cursor one step backward and returns the element there.
     *
     * <p>Time complexity: O(1) - this is why the list is doubly linked; a singly linked ring
     * would need a full lap of n-1 hops to step back one position.
     *
     * <p>Before the head is the tail: this never runs out.
     *
     * @return the element the cursor now sits on
     * @throws NoSuchElementException if the list is empty
     */
    public T previous() {
        requireNotEmpty();
        cursor = cursor.prev;
        counter.pointerHop();
        return cursor.value;
    }

    /**
     * Returns the element under the cursor without moving it.
     *
     * <p>Time complexity: O(1).
     *
     * @return the current element
     * @throws NoSuchElementException if the list is empty
     */
    public T current() {
        requireNotEmpty();
        return cursor.value;
    }

    /**
     * Returns the element one step forward without moving the cursor.
     *
     * <p>Time complexity: O(1).
     *
     * @return the element that {@link #next()} would return
     * @throws NoSuchElementException if the list is empty
     */
    public T peekNext() {
        requireNotEmpty();
        return cursor.next.value;
    }

    /**
     * Returns the element one step backward without moving the cursor.
     *
     * <p>Time complexity: O(1).
     *
     * @return the element that {@link #previous()} would return
     * @throws NoSuchElementException if the list is empty
     */
    public T peekPrevious() {
        requireNotEmpty();
        return cursor.prev.value;
    }

    /**
     * Places the cursor on the first node holding the given element.
     *
     * <p>Time complexity: O(n).
     *
     * <p>Used when the user picks a song directly from the library instead of advancing.
     *
     * @param value the element to move to
     * @return {@code true} if the element was found and the cursor moved
     */
    public boolean moveTo(T value) {
        Node<T> node = findNode(value);
        if (node == null) {
            return false;
        }
        cursor = node;
        return true;
    }

    /**
     * Returns the cursor to the head of the ring.
     *
     * <p>Time complexity: O(1).
     */
    public void resetCursor() {
        cursor = head;
    }

    /**
     * Reports whether the cursor currently sits on the head node.
     *
     * <p>Time complexity: O(1).
     *
     * @return {@code true} when a full lap has brought the cursor back to the start
     */
    public boolean isAtHead() {
        return cursor != null && cursor == head;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * Reports whether an equal element is stored.
     *
     * <p>Time complexity: O(n).
     *
     * @param value the element to look for
     * @return {@code true} if found
     */
    public boolean contains(T value) {
        return findNode(value) != null;
    }

    /**
     * Returns the number of elements in the ring.
     *
     * <p>Time complexity: O(1) - the count is maintained, never recomputed by walking, which
     * on a circular list would not terminate without extra care.
     *
     * @return the element count
     */
    public int size() {
        return size;
    }

    /**
     * Reports whether the ring holds no elements.
     *
     * <p>Time complexity: O(1).
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Walks the ring looking for a node holding an equal element.
     *
     * <p>Time complexity: O(n). The loop is bounded by {@link #size} rather than by a null
     * check, because in a circular list there is no null to stop at.
     *
     * @param value the element to find
     * @return the node holding it, or {@code null} if absent
     */
    private Node<T> findNode(T value) {
        Node<T> node = head;
        for (int i = 0; i < size; i++) {
            counter.comparison();
            if (Objects.equals(node.value, value)) {
                return node;
            }
            node = node.next;
            counter.pointerHop();
        }
        return null;
    }

    private void requireNotEmpty() {
        if (cursor == null) {
            throw new NoSuchElementException("The circular list is empty");
        }
    }

    /**
     * Returns an iterator that walks <strong>exactly one lap</strong> and then reports itself
     * exhausted.
     *
     * <p>Time complexity: O(1) to create, O(n) to exhaust.
     *
     * <p>The ring itself is endless, so a naive iterator would make {@code for-each} spin
     * forever. This one counts the elements it has produced and stops after {@code size} of
     * them, always starting from the head so the order is stable regardless of where the
     * playback cursor happens to be.
     *
     * @return an iterator over one full lap, starting at the head
     */
    @Override
    public Iterator<T> iterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private Node<T> nextNode = head;
            private int produced;

            @Override
            public boolean hasNext() {
                return produced < size;
            }

            @Override
            public T next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException("List was modified during iteration");
                }
                if (produced >= size) {
                    throw new NoSuchElementException("Iterator exhausted after one full lap");
                }
                T value = nextNode.value;
                nextNode = nextNode.next;
                produced++;
                return value;
            }
        };
    }

    /**
     * Renders one lap of the ring.
     *
     * <p>Time complexity: O(n). The loop is bounded by the size rather than by a null check,
     * since a circular list has no end to run into.
     *
     * @return a readable representation of one lap
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CircularDoublyLinkedList[");
        Node<T> node = head;
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(node.value);
            node = node.next;
        }
        return sb.append("] (circular)").toString();
    }
}
