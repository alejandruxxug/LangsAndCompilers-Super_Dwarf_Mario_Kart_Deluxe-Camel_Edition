package com.eia.superdwarfkart.ds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The paths the traversals actually walk, which is what the tree view animates.
 *
 * <p>These matter more than they look. A view that only moved a highlight from one node to the
 * next would be indistinguishable from one backed by a sorted array, so the walk itself has to be
 * observable - and it has to be the <em>real</em> walk, not a plausible-looking reconstruction.
 * The assertions below pin the two cases the oral defense asks about: descending into the right
 * subtree and running to its minimum, and climbing through parents until arriving from a left
 * child.
 */
@DisplayName("BinarySearchTree navigation paths")
class BinarySearchTreeNavigationPathTest {

    private BinarySearchTree<Integer> tree;

    /**
     * A tree with a known shape, built by inserting in this order:
     * <pre>
     *          50
     *        /    \
     *      30      70
     *     /  \    /  \
     *   20   40  60   80
     *          \
     *          45
     * </pre>
     */
    @BeforeEach
    void buildTree() {
        tree = BinarySearchTree.naturalOrder();
        for (int value : new int[] {50, 30, 70, 20, 40, 60, 80, 45}) {
            tree.insert(value);
        }
    }

    @Nested
    @DisplayName("successor path")
    class SuccessorPath {

        @Test
        @DisplayName("descends into the right subtree and runs to its minimum")
        void descendsRightThenLeft() {
            // 30 has a right subtree; its minimum is 40, reached through 40 itself.
            assertEquals(List.of(30, 40), tree.successorPath(30));

            // 50's right subtree is rooted at 70, whose minimum is 60: down then left.
            assertEquals(List.of(50, 70, 60), tree.successorPath(50));
        }

        @Test
        @DisplayName("climbs through parents until arriving from a left child")
        void climbsThroughParents() {
            // 20 is a leaf and a left child, so its successor is its parent, reached in one hop.
            assertEquals(List.of(20, 30), tree.successorPath(20));

            // 45 is a right child of 40, which is a right child of 30: the climb passes both
            // before arriving at 50 from the left.
            assertEquals(List.of(45, 40, 30, 50), tree.successorPath(45));
        }

        @Test
        @DisplayName("climbs all the way to the root when there is no successor")
        void largestHasNoSuccessor() {
            // 80 is the rightmost node, so the walk climbs arriving from the right every time and
            // runs out of parents. That climb is real work the structure did, and the animation
            // shows it happening rather than reporting "no successor" out of nowhere.
            assertEquals(List.of(80, 70, 50), tree.successorPath(80));
            assertNull(tree.successor(80));
        }

        @Test
        @DisplayName("agrees with the successor it reports")
        void pathEndsAtTheSuccessor() {
            for (int value : new int[] {20, 30, 40, 45, 50, 60, 70}) {
                List<Integer> path = tree.successorPath(value);
                assertEquals(tree.successor(value), path.get(path.size() - 1),
                        "the path for " + value + " must end on its successor");
                assertEquals(value, path.get(0), "the path must start at the node it steps from");
            }
        }
    }

    @Nested
    @DisplayName("predecessor path")
    class PredecessorPath {

        @Test
        @DisplayName("descends into the left subtree and runs to its maximum")
        void descendsLeftThenRight() {
            // 50's left subtree is rooted at 30, whose maximum is 45: down, right, right.
            assertEquals(List.of(50, 30, 40, 45), tree.predecessorPath(50));
        }

        @Test
        @DisplayName("climbs through parents until arriving from a right child")
        void climbsThroughParents() {
            assertEquals(List.of(60, 70, 50), tree.predecessorPath(60));
        }

        @Test
        @DisplayName("climbs all the way to the root when there is no predecessor")
        void smallestHasNoPredecessor() {
            assertEquals(List.of(20, 30, 50), tree.predecessorPath(20));
            assertNull(tree.predecessor(20));
        }
    }

    @Nested
    @DisplayName("search path")
    class SearchPath {

        @Test
        @DisplayName("is the descent from the root, and its length is the search cost")
        void recordsTheDescent() {
            assertEquals(List.of(50, 30, 40, 45), tree.searchPath(45));
            assertEquals(List.of(50), tree.searchPath(50));
        }

        @Test
        @DisplayName("records the comparisons made before giving up on a missing element")
        void recordsAFailedSearch() {
            List<Integer> path = tree.searchPath(35);
            assertEquals(List.of(50, 30, 40), path);
            assertTrue(path.isEmpty() || !path.contains(35));
        }

        @Test
        @DisplayName("is empty on an empty tree rather than throwing")
        void emptyTree() {
            assertEquals(List.of(), BinarySearchTree.<Integer>naturalOrder().searchPath(1));
        }
    }

    @Nested
    @DisplayName("read-only node access")
    class NodeAccess {

        @Test
        @DisplayName("exposes the real shape from the root")
        void walksTheRealLinks() {
            BinarySearchTree.NodeRef<Integer> root = tree.rootRef();
            assertNotNull(root);
            assertEquals(50, root.value());
            assertEquals(30, root.left().value());
            assertEquals(70, root.right().value());
            assertNull(root.parent());

            // Parent links are what make the upward walk possible, so they have to be real.
            assertEquals(30, root.left().right().parent().value());
            assertSame(root, root.left().parent());
        }

        @Test
        @DisplayName("is null on an empty tree")
        void emptyTreeHasNoRoot() {
            assertNull(BinarySearchTree.<Integer>naturalOrder().rootRef());
        }
    }

    @Test
    @DisplayName("refuses a path from an element that is not in the tree")
    void pathFromAbsentElement() {
        assertThrows(java.util.NoSuchElementException.class, () -> tree.successorPath(99));
        assertThrows(java.util.NoSuchElementException.class, () -> tree.predecessorPath(99));
    }

    @Test
    @DisplayName("a degenerate tree walks a path as long as the tree is tall")
    void degenerateTreeWalksFar() {
        BinarySearchTree<Integer> sorted = BinarySearchTree.naturalOrder();
        for (int i = 1; i <= 10; i++) {
            sorted.insert(i);
        }
        // Inserted in order, every node is its parent's right child: height n-1, a straight line.
        assertEquals(9, sorted.height());

        // Stepping back from the last element climbs nothing - the predecessor is the parent.
        assertEquals(List.of(10, 9), sorted.predecessorPath(10));
        // Stepping forward from the first has to descend the entire right spine.
        assertEquals(List.of(1, 2), sorted.successorPath(1));
        // And searching for the deepest element compares against every node above it.
        assertEquals(10, sorted.searchPath(10).size());
    }
}
