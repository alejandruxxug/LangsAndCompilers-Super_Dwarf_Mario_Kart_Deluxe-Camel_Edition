package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers arrival order, and above all the rule that consuming the queue must not consume the
 * library.
 */
@DisplayName("Arrival order mode")
class ArrivalOrderModeTest {

    private List<Song> songs;
    private ArrivalOrderMode mode;

    @BeforeEach
    void setUp() {
        songs = Songs.list("First", "Second", "Third", "Fourth");
        mode = new ArrivalOrderMode();
        mode.load(songs);
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("plays in the order songs were added")
        void fifo() {
            assertEquals("First", mode.current().getTitle(), "the front song is playing");
            assertEquals("Second", mode.next().getTitle());
            assertEquals("Third", mode.next().getTitle());
            assertEquals("Fourth", mode.next().getTitle());
        }

        @Test
        @DisplayName("runs out at the end rather than looping")
        void endsWhenEmpty() {
            mode.next();
            mode.next();
            mode.next();

            assertFalse(mode.hasNext());
            assertNull(mode.next());
            assertEquals("Fourth", mode.current().getTitle(), "the last song stays current");
        }

        @Test
        @DisplayName("peeking does not consume the queue")
        void peekIsNonDestructive() {
            assertEquals("Second", mode.peekNext().getTitle());
            assertEquals("Second", mode.peekNext().getTitle());
            assertEquals("Second", mode.peekNext().getTitle());

            assertEquals(3, mode.size(), "three peeks must not have eaten anything");
            assertEquals("Second", mode.next().getTitle());
        }

        @Test
        @DisplayName("upcoming lists what is waiting without consuming it")
        void upcomingIsNonDestructive() {
            assertEquals(List.of("Second", "Third"), Songs.titles(mode.upcoming(2)));
            assertEquals(3, mode.size());
            assertEquals(List.of("Second", "Third", "Fourth"), Songs.titles(mode.upcoming(10)));
        }
    }

    @Nested
    @DisplayName("no going back")
    class NoGoingBack {

        @Test
        @DisplayName("the mode reports that it cannot")
        void doesNotSupportPrevious() {
            assertFalse(mode.supportsPrevious());
            assertFalse(ModeId.ARRIVAL_ORDER.supportsPrevious());
        }

        @Test
        @DisplayName("previous throws, and the message explains why")
        void previousThrows() {
            UnsupportedOperationException thrown =
                    assertThrows(UnsupportedOperationException.class, () -> mode.previous());

            assertTrue(thrown.getMessage().contains("SimpleQueue"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("disabled"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the queue is a view, not the storage")
    class NotTheStorage {

        @Test
        @DisplayName("playing to the end leaves the library intact")
        void playingDoesNotEmptyTheLibrary() {
            // The failure this guards against: mode 2 backed by the library's own collection,
            // where listening to everything silently deletes the user's music.
            Library library = new Library(songs);
            ArrivalOrderMode over = new ArrivalOrderMode();
            over.load(library.all());

            while (over.hasNext()) {
                over.next();
            }

            assertEquals(0, over.size(), "the queue is spent");
            assertEquals(4, library.size(), "the library is untouched");
            assertEquals(List.of("First", "Second", "Third", "Fourth"),
                    Songs.titles(library.all()));
        }

        @Test
        @DisplayName("reloading refills the queue from the library")
        void reloadingRestoresTheQueue() {
            while (mode.hasNext()) {
                mode.next();
            }
            assertEquals(0, mode.size());

            mode.load(songs);

            assertEquals(3, mode.size());
            assertEquals("First", mode.current().getTitle());
        }
    }

    @Nested
    @DisplayName("skipping ahead")
    class Skipping {

        @Test
        @DisplayName("reaching a song dequeues everything in front of it")
        void selectDrainsToTheSong() {
            assertTrue(mode.select(songs.get(2)));

            assertEquals("Third", mode.current().getTitle());
            assertEquals(1, mode.size(), "First and Second are gone; only Fourth is left");
            assertEquals("Fourth", mode.peekNext().getTitle());
        }

        @Test
        @DisplayName("a song that is not queued leaves the queue alone")
        void selectingAnAbsentSongIsSafe() {
            // Draining the queue looking for something that was never in it would throw the
            // whole running order away.
            Song stranger = Songs.of("Not In The Queue", "Someone Else");

            assertFalse(mode.select(stranger));
            assertEquals(3, mode.size());
            assertEquals("First", mode.current().getTitle());
        }

        @Test
        @DisplayName("a song already played is not reachable again")
        void cannotSelectBackwards() {
            mode.next();
            mode.next();

            assertFalse(mode.select(songs.get(0)), "First has already been dequeued");
            assertEquals("Third", mode.current().getTitle());
        }
    }

    @Test
    @DisplayName("an empty library is an ordinary state")
    void emptyLibrary() {
        ArrivalOrderMode empty = new ArrivalOrderMode();
        empty.load(List.of());

        assertTrue(empty.isEmpty());
        assertNull(empty.current());
        assertNull(empty.next());
        assertNull(empty.peekNext());
        assertFalse(empty.hasNext());
        assertEquals(List.of(), empty.upcoming(5));
    }

    @Test
    @DisplayName("reports its structure and its costs")
    void describesItself() {
        assertEquals(ModeId.ARRIVAL_ORDER, mode.id());
        assertEquals("SimpleQueue", mode.structureName());
        assertEquals("O(1)", mode.complexities().get("next() dequeue"));
        assertNotNull(mode.complexities().get("previous()"));
    }
}
