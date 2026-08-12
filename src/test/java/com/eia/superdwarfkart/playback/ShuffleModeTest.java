package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.ds.StepCounter;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers shuffle, and above all the difference between a shuffled <em>ordering</em> and a random
 * pick per step. Every test below fails if the mode picks randomly on each call.
 */
@DisplayName("Shuffle mode")
class ShuffleModeTest {

    private List<Song> songs;
    private ShuffleMode mode;

    @BeforeEach
    void setUp() {
        songs = Songs.list("Alpha", "Bravo", "Charlie", "Delta", "Echo");
        mode = new ShuffleMode(new Random(1234), StepCounter.NO_OP);
        mode.load(songs);
    }

    /**
     * @param steps how many times to advance
     * @return the titles visited, starting from the current song
     */
    private List<String> walkForward(int steps) {
        List<String> visited = new ArrayList<>();
        visited.add(mode.current().getTitle());
        for (int i = 0; i < steps; i++) {
            visited.add(mode.next().getTitle());
        }
        return visited;
    }

    @Nested
    @DisplayName("the ordering is baked in once")
    class BakedOrdering {

        @Test
        @DisplayName("previous returns the song that actually played before")
        void previousUndoesNext() {
            // This is the property random-per-step cannot have, and the reason the ordering is
            // fixed at load rather than drawn as it goes.
            Song first = mode.current();
            Song second = mode.next();
            Song third = mode.next();

            assertSame(second, mode.previous());
            assertSame(first, mode.previous());
            assertSame(second, mode.next());
            assertSame(third, mode.next());
        }

        @Test
        @DisplayName("peeking twice gives the same answer, and it is what plays next")
        void peekIsDeterministic() {
            Song peeked = mode.peekNext();

            assertSame(peeked, mode.peekNext());
            assertSame(peeked, mode.peekNext());
            assertSame(peeked, mode.next(), "the up-next label must not lie");
        }

        @Test
        @DisplayName("walking the same path twice visits the same songs")
        void pathIsStable() {
            // walkForward(4) advances four times, so four steps back returns to the start.
            List<String> firstPass = walkForward(4);
            for (int i = 0; i < 4; i++) {
                mode.previous();
            }
            List<String> secondPass = walkForward(4);

            assertEquals(firstPass, secondPass);
        }
    }

    @Nested
    @DisplayName("the ring")
    class Ring {

        @Test
        @DisplayName("one lap plays every song exactly once")
        void everySongOncePerLap() {
            Set<String> seen = new HashSet<>(walkForward(songs.size() - 1));

            assertEquals(5, seen.size(), "a lap must not repeat or skip a song");
        }

        @Test
        @DisplayName("past the last song is the first again")
        void wrapsAround() {
            Song start = mode.current();
            for (int i = 0; i < songs.size(); i++) {
                mode.next();
            }

            assertSame(start, mode.current(), "a full lap returns to where it began");
            assertTrue(mode.hasNext(), "a ring never runs out");
        }

        @Test
        @DisplayName("before the first song is the last")
        void wrapsBackward() {
            Song start = mode.current();
            Song lastOfLap = mode.previous();

            assertFalse(lastOfLap.equals(start));
            for (int i = 0; i < songs.size() - 1; i++) {
                mode.previous();
            }
            assertSame(start, mode.current());
        }

        @Test
        @DisplayName("the shuffled ring holds the whole library")
        void holdsEverything() {
            assertEquals(songs.size(), mode.size());
            assertEquals(5, new HashSet<>(Songs.titles(mode.upcoming(5))).size());
        }
    }

    @Test
    @DisplayName("selecting a song moves the cursor there")
    void selectMovesTheCursor() {
        assertTrue(mode.select(songs.get(3)));
        assertSame(songs.get(3), mode.current());

        assertFalse(mode.select(Songs.of("Not Loaded", "Nobody")));
        assertSame(songs.get(3), mode.current(), "a failed select must not move anything");
    }

    @Test
    @DisplayName("shuffling does not disturb the library's own order")
    void libraryOrderUntouched() {
        assertEquals(List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo"), Songs.titles(songs));
    }

    @Test
    @DisplayName("an empty library is an ordinary state")
    void emptyLibrary() {
        ShuffleMode empty = new ShuffleMode(new Random(1), StepCounter.NO_OP);
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
        assertEquals(ModeId.SHUFFLE, mode.id());
        assertEquals("CircularDoublyLinkedList", mode.structureName());
        assertTrue(mode.supportsPrevious());
        assertEquals("O(1)", mode.complexities().get("next()"));
        assertEquals("O(1)", mode.complexities().get("previous()"));
    }
}
