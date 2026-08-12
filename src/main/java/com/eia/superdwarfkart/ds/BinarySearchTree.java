package com.eia.superdwarfkart.ds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A hand-written binary search tree, backing playback mode 3 (alphabetical).
 *
 * <p>Written from scratch as required by the assignment: no {@code TreeMap}, {@code TreeSet} or
 * any other library collection backs it. Every node carries a {@code parent} pointer in addition
 * to {@code left} and {@code right}, which is what makes in-order successor and predecessor
 * clean to express.
 *
 * <p><strong>Navigation is real tree navigation.</strong> {@link #successor(Object)} descends
 * into the right subtree and runs left to its minimum, or climbs through parents until it
 * arrives from a left child; {@link #predecessor(Object)} mirrors that. The tree is never
 * flattened into an array and indexed. Flattening would produce the same playback order and is
 * the obvious shortcut, but it throws away the structure the mode exists to demonstrate.
 *
 * <p>Deletion relinks nodes rather than copying values between them, so a node reference held
 * elsewhere - by the visualizer, for instance - keeps referring to the same element.
 *
 * <p>Ordering comes from a {@link Comparator} supplied at construction. For songs that
 * comparator compares title, then artist, then identifier: without the tiebreakers two distinct
 * songs sharing a title would compare equal and one of them would silently never be inserted.
 *
 * @param <T> element type
 */
public class BinarySearchTree<T> implements Iterable<T> {

    /**
     * A read-only window onto a node, so a view can draw the tree's actual shape without being
     * able to change it.
     *
     * <p>The visualizer needs real links - which node is whose left child, and how deep each one
     * sits - and it must not be able to relink anything while drawing. Returning this interface
     * rather than the node itself gives it exactly that, with no copying: the node implements the
     * interface directly.
     *
     * @param <T> element type
     */
    public interface NodeRef<T> {

        /** @return the element stored at this node */
        T value();

        /** @return the left child, or {@code null} */
        NodeRef<T> left();

        /** @return the right child, or {@code null} */
        NodeRef<T> right();

        /** @return the parent, or {@code null} at the root */
        NodeRef<T> parent();
    }

    /** A node with both child links and a parent link. */
    private static final class Node<T> implements NodeRef<T> {
        private final T value;
        private Node<T> left;
        private Node<T> right;
        private Node<T> parent;

        private Node(T value) {
            this.value = value;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public NodeRef<T> left() {
            return left;
        }

        @Override
        public NodeRef<T> right() {
            return right;
        }

        @Override
        public NodeRef<T> parent() {
            return parent;
        }
    }

    private Node<T> root;
    private int size;

    /** Incremented on every structural change so iterators can detect concurrent modification. */
    private int modCount;

    private final Comparator<? super T> comparator;
    private final StepCounter counter;

    /**
     * Creates an empty tree ordered by the given comparator, with instrumentation disabled.
     *
     * <p>Time complexity: O(1).
     *
     * @param comparator ordering; must not be {@code null}
     */
    public BinarySearchTree(Comparator<? super T> comparator) {
        this(comparator, StepCounter.NO_OP);
    }

    /**
     * Creates an empty tree ordered by the given comparator that reports its work to a counter.
     *
     * <p>Time complexity: O(1).
     *
     * @param comparator ordering; must not be {@code null}
     * @param counter    step counter; must not be {@code null}, pass {@link StepCounter#NO_OP} to disable
     */
    public BinarySearchTree(Comparator<? super T> comparator, StepCounter counter) {
        this.comparator = Objects.requireNonNull(comparator, "comparator must not be null");
        this.counter = Objects.requireNonNull(counter, "counter must not be null; use StepCounter.NO_OP");
    }

    /**
     * Creates an empty tree ordered by the elements' natural ordering.
     *
     * <p>Time complexity: O(1).
     *
     * @param <E> a comparable element type
     * @return a new empty tree
     */
    public static <E extends Comparable<? super E>> BinarySearchTree<E> naturalOrder() {
        return new BinarySearchTree<>(Comparator.naturalOrder());
    }

    // ------------------------------------------------------------------
    // Modification
    // ------------------------------------------------------------------

    /**
     * Inserts an element, keeping the search-tree ordering.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case. The worst case is not theoretical:
     * inserting an already sorted library degenerates the tree into a straight line, which the
     * visualizer demonstrates on demand.
     *
     * @param value the element to insert; must not be {@code null}
     * @return {@code true} if inserted, {@code false} if an equal element is already present
     */
    public boolean insert(T value) {
        Objects.requireNonNull(value, "BinarySearchTree does not accept null elements");

        if (root == null) {
            root = new Node<>(value);
            size++;
            modCount++;
            return true;
        }

        Node<T> current = root;
        while (true) {
            counter.comparison();
            int cmp = comparator.compare(value, current.value);
            if (cmp == 0) {
                // An equal element is already stored. With the tiebreaking comparator this only
                // happens for the very same song, so it is a duplicate insert, not a collision.
                return false;
            }
            if (cmp < 0) {
                if (current.left == null) {
                    Node<T> node = new Node<>(value);
                    node.parent = current;
                    current.left = node;
                    size++;
                    modCount++;
                    return true;
                }
                current = current.left;
            } else {
                if (current.right == null) {
                    Node<T> node = new Node<>(value);
                    node.parent = current;
                    current.right = node;
                    size++;
                    modCount++;
                    return true;
                }
                current = current.right;
            }
            counter.pointerHop();
        }
    }

    /**
     * Inserts every element of the given source.
     *
     * <p>Time complexity: O(k log n) average for k elements, O(k n) worst case.
     *
     * @param values elements to insert; must not be {@code null}
     */
    public void insertAll(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values must not be null");
        for (T value : values) {
            insert(value);
        }
    }

    /**
     * Removes an element, handling all three deletion cases.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case.
     *
     * <p>The cases are: a leaf, which simply detaches; a node with one child, which is replaced
     * by that child; and a node with two children, which is replaced by its in-order successor.
     * The two-child case is the one that breaks in most implementations, so it is relinked
     * explicitly below rather than approximated by copying a value.
     *
     * @param value the element to remove
     * @return {@code true} if an element was removed
     */
    public boolean delete(T value) {
        Node<T> node = findNode(value);
        if (node == null) {
            return false;
        }
        deleteNode(node);
        size--;
        modCount++;
        return true;
    }

    /**
     * Removes a node from the tree.
     *
     * @param node the node to remove; must belong to this tree
     */
    private void deleteNode(Node<T> node) {
        if (node.left == null) {
            // Cases 1 and 2: no children, or a right child only.
            transplant(node, node.right);
        } else if (node.right == null) {
            // Case 2 mirrored: a left child only.
            transplant(node, node.left);
        } else {
            // Case 3: two children. The in-order successor is the minimum of the right subtree
            // and by definition has no left child, so it can take this node's place.
            Node<T> successor = minimumNode(node.right);
            if (successor.parent != node) {
                // The successor is deeper in the right subtree: lift it out first, putting its
                // own right child in its place.
                transplant(successor, successor.right);
                successor.right = node.right;
                successor.right.parent = successor;
                counter.pointerHop();
            }
            transplant(node, successor);
            successor.left = node.left;
            successor.left.parent = successor;
            counter.pointerHop();
        }
        node.left = null;
        node.right = null;
        node.parent = null;
    }

    /**
     * Replaces the subtree rooted at {@code target} with the subtree rooted at {@code replacement}.
     *
     * <p>Time complexity: O(1).
     *
     * @param target      the subtree being replaced
     * @param replacement the subtree taking its place; may be {@code null}
     */
    private void transplant(Node<T> target, Node<T> replacement) {
        if (target.parent == null) {
            root = replacement;
        } else if (target == target.parent.left) {
            target.parent.left = replacement;
        } else {
            target.parent.right = replacement;
        }
        counter.pointerHop();
        if (replacement != null) {
            replacement.parent = target.parent;
        }
    }

    /**
     * Discards every element.
     *
     * <p>Time complexity: O(1) - dropping the root makes the whole tree unreachable.
     */
    public void clear() {
        root = null;
        size = 0;
        modCount++;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    /**
     * Reports whether an equal element is stored.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case. On a 500 song library a balanced
     * search lands in about 9 comparisons; the same search over the circular list takes about 250.
     *
     * @param value the element to look for
     * @return {@code true} if found
     */
    public boolean search(T value) {
        return findNode(value) != null;
    }

    /**
     * Returns the stored element equal to the given one.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case.
     *
     * @param value the element to look for
     * @return the stored element, or {@code null} if absent
     */
    public T find(T value) {
        Node<T> node = findNode(value);
        return node == null ? null : node.value;
    }

    /**
     * Walks down from the root comparing at each step.
     *
     * @param value the element to find
     * @return the node holding it, or {@code null} if absent
     */
    private Node<T> findNode(T value) {
        return findNode(value, null);
    }

    /**
     * Walks down from the root comparing at each step, optionally recording the nodes it touches.
     *
     * @param value the element to find
     * @param trace collects every node visited, in order; {@code null} to record nothing
     * @return the node holding it, or {@code null} if absent
     */
    private Node<T> findNode(T value, List<Node<T>> trace) {
        if (value == null) {
            return null;
        }
        Node<T> current = root;
        while (current != null) {
            visit(trace, current);
            counter.comparison();
            int cmp = comparator.compare(value, current.value);
            if (cmp == 0) {
                return current;
            }
            current = cmp < 0 ? current.left : current.right;
            counter.pointerHop();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // In-order navigation - the part the oral defense will probe
    // ------------------------------------------------------------------

    /**
     * Returns the in-order successor of an element: the next one alphabetically.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case.
     *
     * <p>Two cases, both pure pointer work. If the node has a right subtree, the successor is
     * that subtree's minimum. Otherwise it is the nearest ancestor the node lies in the left
     * subtree of, found by climbing parents while arriving from the right.
     *
     * @param value the element to step forward from
     * @return the next element in order, or {@code null} if this is the largest
     * @throws NoSuchElementException if the element is not in the tree
     */
    public T successor(T value) {
        Node<T> node = requireNode(value);
        Node<T> next = successorNode(node);
        return next == null ? null : next.value;
    }

    /**
     * Returns the in-order predecessor of an element: the previous one alphabetically.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case.
     *
     * <p>The exact mirror of {@link #successor(Object)}: the left subtree's maximum if there is
     * a left subtree, otherwise the nearest ancestor reached by climbing while arriving from
     * the left.
     *
     * @param value the element to step back from
     * @return the previous element in order, or {@code null} if this is the smallest
     * @throws NoSuchElementException if the element is not in the tree
     */
    public T predecessor(T value) {
        Node<T> node = requireNode(value);
        Node<T> prev = predecessorNode(node);
        return prev == null ? null : prev.value;
    }

    /**
     * Returns the nodes walked to reach the in-order successor, in the order they are touched.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case - the same walk
     * {@link #successor(Object)} performs, recorded rather than discarded.
     *
     * <p>This is what makes the tree view animate the traversal instead of only showing its
     * result. Consecutive elements of the returned list are the edges to light up, in sequence:
     * either a step into the right subtree followed by a run to its minimum, or a climb through
     * parents until arriving from a left child.
     *
     * @param value the element to step forward from
     * @return the elements on the path, starting with {@code value} and ending with its
     *         successor. When there is no successor the path is the climb that established
     *         that - up to the root, arriving from the right every time - because that walk is
     *         work the structure genuinely did and the animation shows it happening.
     * @throws NoSuchElementException if the element is not in the tree
     */
    public List<T> successorPath(T value) {
        List<Node<T>> trace = new ArrayList<>();
        successorNode(requireNode(value), trace);
        return values(trace);
    }

    /**
     * Returns the nodes walked to reach the in-order predecessor, in the order they are touched.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case.
     *
     * @param value the element to step back from
     * @return the elements on the path, starting with {@code value} and ending with its
     *         predecessor, or with the climb to the root when there is none
     * @throws NoSuchElementException if the element is not in the tree
     */
    public List<T> predecessorPath(T value) {
        List<Node<T>> trace = new ArrayList<>();
        predecessorNode(requireNode(value), trace);
        return values(trace);
    }

    /**
     * Returns the nodes compared while searching from the root, in order.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case.
     *
     * <p>The length of this path <em>is</em> the search cost, which is why the tree view can show
     * it and the circular list cannot show anything comparable: one descends, the other walks.
     *
     * @param value the element searched for
     * @return the elements compared against, root first; empty if the tree is empty. The last
     *         element is the match when there was one.
     */
    public List<T> searchPath(T value) {
        List<Node<T>> trace = new ArrayList<>();
        findNode(value, trace);
        return values(trace);
    }

    private Node<T> successorNode(Node<T> node) {
        return successorNode(node, null);
    }

    /**
     * @param node  the node to step forward from
     * @param trace collects every node visited, in order; {@code null} to record nothing
     * @return the in-order successor node, or {@code null} if none
     */
    private Node<T> successorNode(Node<T> node, List<Node<T>> trace) {
        visit(trace, node);
        if (node.right != null) {
            counter.pointerHop();
            return minimumNode(node.right, trace);
        }
        Node<T> child = node;
        Node<T> ancestor = node.parent;
        while (ancestor != null && child == ancestor.right) {
            visit(trace, ancestor);
            child = ancestor;
            ancestor = ancestor.parent;
            counter.pointerHop();
        }
        visit(trace, ancestor);
        return ancestor;
    }

    private Node<T> predecessorNode(Node<T> node) {
        return predecessorNode(node, null);
    }

    /**
     * @param node  the node to step back from
     * @param trace collects every node visited, in order; {@code null} to record nothing
     * @return the in-order predecessor node, or {@code null} if none
     */
    private Node<T> predecessorNode(Node<T> node, List<Node<T>> trace) {
        visit(trace, node);
        if (node.left != null) {
            counter.pointerHop();
            return maximumNode(node.left, trace);
        }
        Node<T> child = node;
        Node<T> ancestor = node.parent;
        while (ancestor != null && child == ancestor.left) {
            visit(trace, ancestor);
            child = ancestor;
            ancestor = ancestor.parent;
            counter.pointerHop();
        }
        visit(trace, ancestor);
        return ancestor;
    }

    /**
     * Appends a node to a trace, ignoring both a missing trace and a missing node.
     *
     * @param trace where to record, or {@code null} to record nothing
     * @param node  the node visited, or {@code null} when a walk ran off the end
     */
    private void visit(List<Node<T>> trace, Node<T> node) {
        if (trace != null && node != null) {
            trace.add(node);
        }
    }

    private List<T> values(List<Node<T>> nodes) {
        List<T> out = new ArrayList<>(nodes.size());
        for (Node<T> node : nodes) {
            out.add(node.value);
        }
        return out;
    }

    /**
     * Returns the smallest element.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case - it is a walk down the left spine.
     *
     * @return the smallest element
     * @throws NoSuchElementException if the tree is empty
     */
    public T first() {
        if (root == null) {
            throw new NoSuchElementException("The tree is empty");
        }
        return minimumNode(root).value;
    }

    /**
     * Returns the largest element.
     *
     * <p>Time complexity: O(log n) average, O(n) worst case - a walk down the right spine.
     *
     * @return the largest element
     * @throws NoSuchElementException if the tree is empty
     */
    public T last() {
        if (root == null) {
            throw new NoSuchElementException("The tree is empty");
        }
        return maximumNode(root).value;
    }

    private Node<T> minimumNode(Node<T> from) {
        return minimumNode(from, null);
    }

    private Node<T> minimumNode(Node<T> from, List<Node<T>> trace) {
        Node<T> current = from;
        visit(trace, current);
        while (current.left != null) {
            current = current.left;
            counter.pointerHop();
            visit(trace, current);
        }
        return current;
    }

    private Node<T> maximumNode(Node<T> from) {
        return maximumNode(from, null);
    }

    private Node<T> maximumNode(Node<T> from, List<Node<T>> trace) {
        Node<T> current = from;
        visit(trace, current);
        while (current.right != null) {
            current = current.right;
            counter.pointerHop();
            visit(trace, current);
        }
        return current;
    }

    private Node<T> requireNode(T value) {
        Node<T> node = findNode(value);
        if (node == null) {
            throw new NoSuchElementException("Element is not in the tree: " + value);
        }
        return node;
    }

    // ------------------------------------------------------------------
    // Traversal and shape
    // ------------------------------------------------------------------

    /**
     * Returns every element in ascending order.
     *
     * <p>Time complexity: O(n).
     *
     * <p>The returned list is a snapshot for display purposes. It is never used to drive
     * playback: {@link #successor(Object)} and {@link #predecessor(Object)} navigate the tree
     * itself.
     *
     * @return a new list holding the elements in order
     */
    public List<T> inOrderTraversal() {
        List<T> out = new ArrayList<>(size);
        inOrderTraversal(out::add);
        return out;
    }

    /**
     * Applies an action to every element in ascending order.
     *
     * <p>Time complexity: O(n).
     *
     * @param action the action to apply; must not be {@code null}
     */
    public void inOrderTraversal(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action must not be null");
        inOrder(root, action);
    }

    private void inOrder(Node<T> node, Consumer<? super T> action) {
        if (node == null) {
            return;
        }
        inOrder(node.left, action);
        action.accept(node.value);
        inOrder(node.right, action);
    }

    /**
     * Returns a read-only handle on the root, from which the whole shape can be walked.
     *
     * <p>Time complexity: O(1).
     *
     * <p>The tree view needs the real links to lay nodes out - in-order position across, depth
     * down - and must not be able to relink anything while it draws. {@link NodeRef} gives it
     * that without copying the tree.
     *
     * @return the root, or {@code null} when the tree is empty
     */
    public NodeRef<T> rootRef() {
        return root;
    }

    /**
     * Returns the height of the tree: the number of edges on the longest root-to-leaf path.
     *
     * <p>Time complexity: O(n) - every node is visited.
     *
     * <p>An empty tree has height -1 and a single node has height 0, so that a balanced tree of
     * n nodes has height about log2(n) and a degenerate one has height n-1. The jump between
     * those two is exactly what the shuffled-versus-alphabetical insert demonstration shows.
     *
     * @return the height, or -1 when empty
     */
    public int height() {
        return height(root);
    }

    private int height(Node<T> node) {
        if (node == null) {
            return -1;
        }
        return 1 + Math.max(height(node.left), height(node.right));
    }

    /**
     * Returns the number of stored elements.
     *
     * <p>Time complexity: O(1) - the count is maintained, never recomputed by walking.
     *
     * @return the element count
     */
    public int size() {
        return size;
    }

    /**
     * Reports whether the tree holds no elements.
     *
     * <p>Time complexity: O(1).
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns an in-order iterator.
     *
     * <p>Time complexity: O(log n) average to create, O(n) to exhaust.
     *
     * <p>It advances with the same successor navigation playback uses, rather than by walking a
     * pre-built list, so iterating exercises the real code path.
     *
     * @return an iterator over the elements in ascending order
     */
    @Override
    public Iterator<T> iterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private Node<T> nextNode = root == null ? null : minimumNode(root);

            @Override
            public boolean hasNext() {
                return nextNode != null;
            }

            @Override
            public T next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException("Tree was modified during iteration");
                }
                if (nextNode == null) {
                    throw new NoSuchElementException("Iterator exhausted");
                }
                T value = nextNode.value;
                nextNode = successorNode(nextNode);
                return value;
            }
        };
    }

    /**
     * Renders the elements in order together with the size and height.
     *
     * <p>Time complexity: O(n) - it traverses the whole tree twice, so it is a debugging aid
     * and not something to call from a render loop.
     *
     * @return a readable representation of the tree
     */
    @Override
    public String toString() {
        return "BinarySearchTree" + inOrderTraversal() + " (size=" + size + ", height=" + height() + ")";
    }
}
