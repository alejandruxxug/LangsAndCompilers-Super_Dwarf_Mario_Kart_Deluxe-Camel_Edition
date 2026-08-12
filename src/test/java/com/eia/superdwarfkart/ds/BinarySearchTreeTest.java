package com.eia.superdwarfkart.ds;

import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BinarySearchTree")
class BinarySearchTreeTest {

    /**
     * Builds the reference tree used by most tests below.
     *
     * <pre>
     *            50
     *          /    \
     *        30      70
     *       /  \    /  \
     *     20   40  60   80
     * </pre>
     */
    private static BinarySearchTree<Integer> referenceTree() {
        BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();
        for (int value : new int[]{50, 30, 70, 20, 40, 60, 80}) {
            tree.insert(value);
        }
        return tree;
    }

    @Nested
    @DisplayName("insert and search")
    class InsertAndSearch {

        @Test
        @DisplayName("stores elements and finds them again")
        void insertAndFind() {
            BinarySearchTree<Integer> tree = referenceTree();

            assertEquals(7, tree.size());
            for (int value : new int[]{50, 30, 70, 20, 40, 60, 80}) {
                assertTrue(tree.search(value), "should find " + value);
            }
            assertFalse(tree.search(99));
        }

        @Test
        @DisplayName("in-order traversal comes out sorted")
        void inOrderIsSorted() {
            BinarySearchTree<Integer> tree = referenceTree();

            assertEquals(List.of(20, 30, 40, 50, 60, 70, 80), tree.inOrderTraversal());
        }

        @Test
        @DisplayName("rejects a duplicate rather than storing it twice")
        void rejectsDuplicates() {
            BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();

            assertTrue(tree.insert(10));
            assertFalse(tree.insert(10));
            assertEquals(1, tree.size());
        }

        @Test
        @DisplayName("rejects null elements")
        void rejectsNull() {
            BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();

            assertThrows(NullPointerException.class, () -> tree.insert(null));
        }

        @Test
        @DisplayName("an empty tree reports empty and refuses first/last")
        void emptyTree() {
            BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();

            assertTrue(tree.isEmpty());
            assertEquals(0, tree.size());
            assertEquals(-1, tree.height());
            assertEquals(List.of(), tree.inOrderTraversal());
            assertThrows(NoSuchElementException.class, tree::first);
            assertThrows(NoSuchElementException.class, tree::last);
        }
    }

    @Nested
    @DisplayName("deletion")
    class Deletion {

        @Test
        @DisplayName("case 1: a leaf detaches")
        void deleteLeaf() {
            BinarySearchTree<Integer> tree = referenceTree();

            assertTrue(tree.delete(20));

            assertFalse(tree.search(20));
            assertEquals(6, tree.size());
            assertEquals(List.of(30, 40, 50, 60, 70, 80), tree.inOrderTraversal());
        }

        @Test
        @DisplayName("case 2: a node with only a right child is replaced by it")
        void deleteNodeWithRightChildOnly() {
            BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();
            for (int value : new int[]{50, 30, 40, 70}) {
                tree.insert(value);
            }

            assertTrue(tree.delete(30));

            assertEquals(List.of(40, 50, 70), tree.inOrderTraversal());
            assertTrue(tree.search(40));
            assertEquals(40, tree.first());
            assertEquals(50, tree.successor(40));
        }

        @Test
        @DisplayName("case 2 mirrored: a node with only a left child is replaced by it")
        void deleteNodeWithLeftChildOnly() {
            BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();
            for (int value : new int[]{50, 30, 20, 70}) {
                tree.insert(value);
            }

            assertTrue(tree.delete(30));

            assertEquals(List.of(20, 50, 70), tree.inOrderTraversal());
            assertEquals(20, tree.first());
        }

        @Test
        @DisplayName("case 3: a node with two children is replaced by its in-order successor")
        void deleteNodeWithTwoChildren() {
            BinarySearchTree<Integer> tree = referenceTree();

            // 30 has children 20 and 40; its in-order successor is 40.
            assertTrue(tree.delete(30));

            assertFalse(tree.search(30));
            assertEquals(6, tree.size());
            assertEquals(List.of(20, 40, 50, 60, 70, 80), tree.inOrderTraversal());
            // The subtree must remain a valid search tree afterwards.
            assertEquals(20, tree.predecessor(40));
            assertEquals(50, tree.successor(40));
        }

        @Test
        @DisplayName("case 3 at the root, where the successor is deep in the right subtree")
        void deleteRootWithTwoChildren() {
            BinarySearchTree<Integer> tree = referenceTree();

            // The root's successor is 60, which sits two levels down, so it must be lifted out
            // and re-parented rather than simply swapped.
            assertTrue(tree.delete(50));

            assertFalse(tree.search(50));
            assertEquals(List.of(20, 30, 40, 60, 70, 80), tree.inOrderTraversal());
            assertEquals(40, tree.predecessor(60));
            assertEquals(70, tree.successor(60));
            assertEquals(20, tree.first());
            assertEquals(80, tree.last());
        }

        @Test
        @DisplayName("case 3 where the successor is the direct right child")
        void deleteWhenSuccessorIsDirectRightChild() {
            BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();
            for (int value : new int[]{50, 30, 70, 60, 80, 85}) {
                tree.insert(value);
            }

            // 70's successor is its own right child, 80: the branch that skips the lift-out.
            assertTrue(tree.delete(70));

            assertEquals(List.of(30, 50, 60, 80, 85), tree.inOrderTraversal());
            assertEquals(60, tree.predecessor(80));
            assertEquals(85, tree.successor(80));
        }

        @Test
        @DisplayName("deleting every element in turn leaves a consistent tree each time")
        void deleteEveryElement() {
            List<Integer> values = new ArrayList<>(List.of(50, 30, 70, 20, 40, 60, 80, 10, 25, 35, 45));

            // Delete in several different orders; each must keep the tree sorted and consistent.
            for (int seed = 0; seed < 5; seed++) {
                BinarySearchTree<Integer> tree = BinarySearchTree.naturalOrder();
                tree.insertAll(values);

                List<Integer> order = new ArrayList<>(values);
                Collections.shuffle(order, new java.util.Random(seed));

                List<Integer> remaining = new ArrayList<>(values);
                Collections.sort(remaining);

                for (Integer victim : order) {
                    assertTrue(tree.delete(victim), "should delete " + victim);
                    remaining.remove(victim);
                    assertEquals(remaining, tree.inOrderTraversal(),
                            "tree stayed sorted after deleting " + victim + " (seed " + seed + ")");
                    assertEquals(remaining.size(), tree.size());
                }
                assertTrue(tree.isEmpty());
            }
        }

        @Test
        @DisplayName("deleting an absent element reports false and changes nothing")
        void deleteAbsent() {
            BinarySearchTree<Integer> tree = referenceTree();

            assertFalse(tree.delete(99));
            assertEquals(7, tree.size());
        }
    }

    @Nested
    @DisplayName("in-order navigation")
    class Navigation {

        @Test
        @DisplayName("successor steps forward through the whole tree")
        void successorWalksForward() {
            BinarySearchTree<Integer> tree = referenceTree();

            List<Integer> walked = new ArrayList<>();
            Integer value = tree.first();
            while (value != null) {
                walked.add(value);
                value = tree.successor(value);
            }

            assertEquals(List.of(20, 30, 40, 50, 60, 70, 80), walked);
        }

        @Test
        @DisplayName("predecessor steps backward through the whole tree")
        void predecessorWalksBackward() {
            BinarySearchTree<Integer> tree = referenceTree();

            List<Integer> walked = new ArrayList<>();
            Integer value = tree.last();
            while (value != null) {
                walked.add(value);
                value = tree.predecessor(value);
            }

            assertEquals(List.of(80, 70, 60, 50, 40, 30, 20), walked);
        }

        @Test
        @DisplayName("successor covers both cases: right subtree, and climbing through parents")
        void successorBothCases() {
            BinarySearchTree<Integer> tree = referenceTree();

            // Has a right subtree: successor is that subtree's minimum.
            assertEquals(60, tree.successor(50));
            // No right subtree: climb until arriving from a left child.
            assertEquals(50, tree.successor(40));
            assertEquals(30, tree.successor(20));
        }

        @Test
        @DisplayName("predecessor covers both cases: left subtree, and climbing through parents")
        void predecessorBothCases() {
            BinarySearchTree<Integer> tree = referenceTree();

            // Has a left subtree: predecessor is that subtree's maximum.
            assertEquals(40, tree.predecessor(50));
            // No left subtree: climb until arriving from a right child.
            assertEquals(50, tree.predecessor(60));
            assertEquals(70, tree.predecessor(80));
        }

        @Test
        @DisplayName("successor of the largest and predecessor of the smallest are absent")
        void endsOfTheTree() {
            BinarySearchTree<Integer> tree = referenceTree();

            assertNull(tree.successor(80), "nothing follows the largest element");
            assertNull(tree.predecessor(20), "nothing precedes the smallest element");
            assertEquals(20, tree.first());
            assertEquals(80, tree.last());
        }

        @Test
        @DisplayName("stepping forward then back returns to the same element everywhere")
        void forwardThenBackRoundTrips() {
            BinarySearchTree<Integer> tree = referenceTree();

            for (Integer value : tree.inOrderTraversal()) {
                Integer forward = tree.successor(value);
                if (forward != null) {
                    assertEquals(value, tree.predecessor(forward),
                            "predecessor(successor(" + value + ")) must be " + value);
                }
            }
        }

        @Test
        @DisplayName("navigating an element that is not in the tree is an error, not a null")
        void navigatingAbsentElement() {
            BinarySearchTree<Integer> tree = referenceTree();

            assertThrows(NoSuchElementException.class, () -> tree.successor(99));
            assertThrows(NoSuchElementException.class, () -> tree.predecessor(99));
        }

        @Test
        @DisplayName("navigation stays correct after a deletion")
        void navigationAfterDeletion() {
            BinarySearchTree<Integer> tree = referenceTree();

            tree.delete(50);
            tree.delete(20);

            List<Integer> walked = new ArrayList<>();
            Integer value = tree.first();
            while (value != null) {
                walked.add(value);
                value = tree.successor(value);
            }
            assertEquals(List.of(30, 40, 60, 70, 80), walked);
        }

        @Test
        @DisplayName("the iterator produces the same order as repeated successor calls")
        void iteratorMatchesSuccessorWalk() {
            BinarySearchTree<Integer> tree = referenceTree();

            List<Integer> iterated = new ArrayList<>();
            tree.forEach(iterated::add);

            assertEquals(tree.inOrderTraversal(), iterated);
        }
    }

    @Nested
    @DisplayName("shape")
    class Shape {

        @Test
        @DisplayName("inserting in sorted order degenerates the tree into a straight line")
        void sortedInsertDegenerates() {
            BinarySearchTree<Integer> degenerate = BinarySearchTree.naturalOrder();
            for (int i = 1; i <= 15; i++) {
                degenerate.insert(i);
            }

            BinarySearchTree<Integer> balanced = BinarySearchTree.naturalOrder();
            for (int value : new int[]{8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15}) {
                balanced.insert(value);
            }

            assertEquals(15, degenerate.size());
            assertEquals(15, balanced.size());

            // A straight line of n nodes has height n-1; a balanced tree of 15 has height 3.
            assertEquals(14, degenerate.height(), "sorted inserts produce a linked list, not a tree");
            assertEquals(3, balanced.height());
            assertNotEquals(degenerate.height(), balanced.height());

            // Both still hold the same elements in the same order.
            assertEquals(balanced.inOrderTraversal(), degenerate.inOrderTraversal());
        }

        @Test
        @DisplayName("the degenerate tree costs measurably more to search")
        void degenerateSearchCostsMore() {
            CountingStepCounter degenerateCounter = new CountingStepCounter();
            BinarySearchTree<Integer> degenerate =
                    new BinarySearchTree<>(java.util.Comparator.naturalOrder(), degenerateCounter);
            for (int i = 1; i <= 15; i++) {
                degenerate.insert(i);
            }

            CountingStepCounter balancedCounter = new CountingStepCounter();
            BinarySearchTree<Integer> balanced =
                    new BinarySearchTree<>(java.util.Comparator.naturalOrder(), balancedCounter);
            for (int value : new int[]{8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15}) {
                balanced.insert(value);
            }

            degenerateCounter.reset();
            balancedCounter.reset();

            degenerate.search(15);
            balanced.search(15);

            assertEquals(15, degenerateCounter.comparisons(),
                    "a degenerate tree compares against every node");
            assertEquals(4, balancedCounter.comparisons(),
                    "a balanced tree of 15 nodes finds any element in about log2(n) comparisons");
            assertTrue(balancedCounter.comparisons() < degenerateCounter.comparisons());
        }

        @Test
        @DisplayName("clear empties the tree")
        void clearEmpties() {
            BinarySearchTree<Integer> tree = referenceTree();

            tree.clear();

            assertTrue(tree.isEmpty());
            assertEquals(0, tree.size());
            assertEquals(-1, tree.height());
        }
    }

    @Nested
    @DisplayName("songs with duplicate titles")
    class DuplicateTitles {

        private Song song(String title, String artist) {
            return new Song(title, artist, Path.of("/music/" + title + "-" + artist + ".mp3"));
        }

        @Test
        @DisplayName("two different songs sharing a title are both stored")
        void duplicateTitlesBothSurvive() {
            BinarySearchTree<Song> tree = new BinarySearchTree<>(Song.BY_TITLE);

            Song a = song("Bohemian Rhapsody", "Queen");
            Song b = song("Bohemian Rhapsody", "A Tribute Band");

            assertTrue(tree.insert(a));
            assertTrue(tree.insert(b), "a same-titled song by another artist is a different song");
            assertEquals(2, tree.size(), "ordering on title alone would have silently dropped one");

            assertTrue(tree.search(a));
            assertTrue(tree.search(b));
        }

        @Test
        @DisplayName("same title and same artist are still distinguished by identifier")
        void sameTitleAndArtistDistinguishedById() {
            BinarySearchTree<Song> tree = new BinarySearchTree<>(Song.BY_TITLE);

            Song a = song("Untitled", "Unknown Artist");
            Song b = song("Untitled", "Unknown Artist");

            assertNotEquals(a.getId(), b.getId());
            assertTrue(tree.insert(a));
            assertTrue(tree.insert(b));
            assertEquals(2, tree.size());
        }

        @Test
        @DisplayName("the very same song is not inserted twice")
        void sameSongInsertedOnce() {
            BinarySearchTree<Song> tree = new BinarySearchTree<>(Song.BY_TITLE);
            Song a = song("One Song", "One Artist");

            assertTrue(tree.insert(a));
            assertFalse(tree.insert(a));
            assertEquals(1, tree.size());
        }

        @Test
        @DisplayName("ordering is case-insensitive on the title")
        void orderingIgnoresCase() {
            BinarySearchTree<Song> tree = new BinarySearchTree<>(Song.BY_TITLE);
            tree.insert(song("banana", "X"));
            tree.insert(song("Apple", "X"));
            tree.insert(song("CHERRY", "X"));

            List<String> titles = tree.inOrderTraversal().stream().map(Song::getTitle).toList();

            assertEquals(List.of("Apple", "banana", "CHERRY"), titles);
        }

        @Test
        @DisplayName("navigation works across songs that share a title")
        void navigationAcrossDuplicateTitles() {
            BinarySearchTree<Song> tree = new BinarySearchTree<>(Song.BY_TITLE);
            Song queen = song("Bohemian Rhapsody", "Queen");
            Song tribute = song("Bohemian Rhapsody", "A Tribute Band");
            Song other = song("Zebra", "Someone");
            tree.insert(queen);
            tree.insert(tribute);
            tree.insert(other);

            // Ordered by title then artist: "A Tribute Band" precedes "Queen".
            assertEquals(tribute, tree.first());
            assertEquals(queen, tree.successor(tribute));
            assertEquals(tribute, tree.predecessor(queen));
            assertEquals(other, tree.successor(queen));
        }
    }
}
