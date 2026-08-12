package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers alphabetical order over the search tree: successor and predecessor navigation, the
 * tiebreaker that keeps songs with the same title apart, and the end of the alphabet.
 */
@DisplayName("Alphabetical mode")
class AlphabeticalModeTest {

    private AlphabeticalMode mode;

    @BeforeEach
    void setUp() {
        mode = new AlphabeticalMode();
        // Deliberately not in alphabetical order, and mixed case.
        mode.load(Songs.list("delta", "Bravo", "echo", "Alpha", "Charlie"));
    }

    /** @return every title reachable by advancing from the current song */
    private List<String> walkToEnd() {
        List<String> visited = new ArrayList<>();
        visited.add(mode.current().getTitle());
        while (mode.hasNext()) {
            visited.add(mode.next().getTitle());
        }
        return visited;
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("advances in alphabetical order, ignoring case")
        void inOrder() {
            assertEquals(List.of("Alpha", "Bravo", "Charlie", "delta", "echo"), walkToEnd());
        }

        @Test
        @DisplayName("starts at the alphabetically first song")
        void startsAtTheFirst() {
            assertEquals("Alpha", mode.current().getTitle());
        }

        @Test
        @DisplayName("predecessor mirrors successor exactly")
        void previousMirrorsNext() {
            mode.next();
            mode.next();
            assertEquals("Charlie", mode.current().getTitle());

            assertEquals("Bravo", mode.previous().getTitle());
            assertEquals("Alpha", mode.previous().getTitle());
        }

        @Test
        @DisplayName("upcoming lists the songs that follow")
        void upcomingFollowsTheOrder() {
            assertEquals(List.of("Bravo", "Charlie"), Songs.titles(mode.upcoming(2)));
            assertEquals("Alpha", mode.current().getTitle(), "listing must not advance anything");
        }
    }

    @Nested
    @DisplayName("the ends of the alphabet")
    class Ends {

        @Test
        @DisplayName("there is nothing past the last song")
        void stopsAtTheEnd() {
            while (mode.hasNext()) {
                mode.next();
            }

            assertEquals("echo", mode.current().getTitle());
            assertFalse(mode.hasNext());
            assertNull(mode.peekNext());
            assertNull(mode.next());
            assertEquals("echo", mode.current().getTitle(), "a refused next must not move");
        }

        @Test
        @DisplayName("there is nothing before the first song")
        void stopsAtTheStart() {
            assertNull(mode.previous());
            assertEquals("Alpha", mode.current().getTitle());
        }
    }

    @Nested
    @DisplayName("duplicate titles")
    class Duplicates {

        @Test
        @DisplayName("two songs with the same title both survive, ordered by artist")
        void tiebreakerKeepsBoth() {
            // Comparing on title alone would treat the second Intro as a duplicate and lose it.
            AlphabeticalMode duplicates = new AlphabeticalMode();
            Song byZara = Songs.of("Intro", "Zara");
            Song byAdam = Songs.of("Intro", "Adam");
            duplicates.load(List.of(byZara, byAdam, Songs.of("Outro", "Adam")));

            assertEquals(3, duplicates.size(), "neither Intro may be swallowed");
            assertSame(byAdam, duplicates.current(), "artist breaks the tie");
            assertSame(byZara, duplicates.next());
            assertEquals("Outro", duplicates.next().getTitle());
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("selecting a song moves to it")
        void selectFindsTheSong() {
            Song charlie = mode.upcoming(5).stream()
                    .filter(s -> s.getTitle().equals("Charlie")).findFirst().orElseThrow();

            assertTrue(mode.select(charlie));
            assertSame(charlie, mode.current());
            assertEquals("delta", mode.peekNext().getTitle());
        }

        @Test
        @DisplayName("a song the tree does not hold is refused")
        void selectRejectsUnknownSongs() {
            assertFalse(mode.select(Songs.of("Foxtrot", "Nobody")));
            assertEquals("Alpha", mode.current().getTitle());
        }
    }

    @Test
    @DisplayName("sorted input degenerates the tree into a straight line")
    void sortedInsertionDegenerates() {
        // The BST worst case the visualizer demonstrates in one click: inserting in order gives
        // every node a single child, so height grows with n instead of log n. Height counts
        // edges, so eight nodes in a line measure seven.
        AlphabeticalMode degenerate = new AlphabeticalMode();
        degenerate.load(Songs.list("A", "B", "C", "D", "E", "F", "G", "H"));

        assertEquals(8, degenerate.size());
        assertEquals(7, degenerate.height(), "sorted input gives a straight line");

        AlphabeticalMode balanced = new AlphabeticalMode();
        balanced.load(Songs.list("D", "B", "F", "A", "C", "E", "G", "H"));

        assertEquals(3, balanced.height(), "the same songs, inserted evenly, stay shallow");
    }

    @Test
    @DisplayName("navigation is unaffected by the tree's shape")
    void degenerateTreeStillWalksInOrder() {
        AlphabeticalMode degenerate = new AlphabeticalMode();
        degenerate.load(Songs.list("A", "B", "C", "D"));

        List<String> visited = new ArrayList<>();
        visited.add(degenerate.current().getTitle());
        while (degenerate.hasNext()) {
            visited.add(degenerate.next().getTitle());
        }

        assertEquals(List.of("A", "B", "C", "D"), visited);
    }

    @Test
    @DisplayName("an empty library is an ordinary state")
    void emptyLibrary() {
        AlphabeticalMode empty = new AlphabeticalMode();
        empty.load(List.of());

        assertTrue(empty.isEmpty());
        assertNull(empty.current());
        assertNull(empty.next());
        assertNull(empty.peekNext());
        assertFalse(empty.hasNext());
        assertEquals(List.of(), empty.upcoming(3));
    }

    @Test
    @DisplayName("reports its structure and its costs")
    void describesItself() {
        assertEquals(ModeId.ALPHABETICAL, mode.id());
        assertEquals("BinarySearchTree", mode.structureName());
        assertTrue(mode.supportsPrevious());
        assertTrue(mode.complexities().get("next() successor").contains("log n"));
    }
}
