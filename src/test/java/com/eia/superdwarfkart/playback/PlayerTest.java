package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the player: swapping modes without knowing which one is held, keeping the current song
 * across a swap, and the rule that an edit is not a structural change.
 */
@DisplayName("Player")
class PlayerTest {

    private Library library;
    private Player player;

    @BeforeEach
    void setUp() {
        library = new Library(Songs.list("Alpha", "Bravo", "Charlie", "Delta"));
        player = new Player(library, new AlphabeticalMode());
    }

    @Nested
    @DisplayName("swapping modes")
    class Swapping {

        @Test
        @DisplayName("replacing the mode changes the order and the reported structure")
        void modeSwapIsPolymorphic() {
            assertEquals("BinarySearchTree", player.mode().structureName());

            player.setMode(new ArrivalOrderMode());

            assertEquals("SimpleQueue", player.mode().structureName());
            assertEquals(ModeId.ARRIVAL_ORDER, player.mode().id());
        }

        @Test
        @DisplayName("the song being played survives the swap")
        void currentSongIsCarriedAcross() {
            player.next();
            Song playing = player.current();
            assertEquals("Bravo", playing.getTitle());

            player.setMode(new ShuffleMode());

            assertSame(playing, player.current(), "changing the order must not change the song");
        }

        @Test
        @DisplayName("every mode is built from the library, not from the previous mode")
        void everyModeSeesTheWholeLibrary() {
            player.setMode(new ArrivalOrderMode());
            while (player.canGoNext()) {
                player.next();
            }
            assertEquals(0, player.mode().size(), "the queue is spent");

            player.setMode(new ShuffleMode());

            assertEquals(4, player.mode().size(), "shuffle rebuilds from the library");
            assertEquals(4, library.size());
        }
    }

    @Nested
    @DisplayName("previous, without type checks")
    class Previous {

        @Test
        @DisplayName("is enabled for the two modes that can go back")
        void enabledWhereSupported() {
            assertTrue(player.canGoPrevious());

            player.setMode(new ShuffleMode());
            assertTrue(player.canGoPrevious());
        }

        @Test
        @DisplayName("is disabled in arrival order, and calling it anyway does nothing")
        void disabledForTheQueue() {
            player.setMode(new ArrivalOrderMode());

            assertFalse(player.canGoPrevious());
            // A keyboard shortcut can still arrive at a disabled control. It must do nothing
            // rather than raise the mode's exception at the user.
            assertDoesNotThrow(() -> assertNull(player.previous()));
        }

        @Test
        @DisplayName("is disabled when there is nothing loaded")
        void disabledWhenEmpty() {
            Player empty = new Player(new Library(), new ShuffleMode());

            assertFalse(empty.canGoPrevious());
            assertNull(empty.previous());
        }
    }

    @Nested
    @DisplayName("following the library")
    class FollowingTheLibrary {

        @Test
        @DisplayName("adding a song rebuilds the active mode")
        void addingReloads() {
            library.add(Songs.of("Echo", "Koji Kondo"));

            assertEquals(5, player.mode().size());
        }

        @Test
        @DisplayName("removing a song rebuilds the active mode")
        void removingReloads() {
            library.remove(library.all().get(0));

            assertEquals(3, player.mode().size());
        }

        @Test
        @DisplayName("editing a song does not reshuffle the running order")
        void editingDoesNotReshuffle() {
            // Rebuilding on an edit would re-draw the shuffle every time the rating slider moved,
            // so the running order would change under the user mid-listen.
            player.setMode(new ShuffleMode());
            List<Song> orderBefore = player.mode().upcoming(4);

            Song song = library.all().get(0);
            song.setRating(80);
            library.update(song);

            assertEquals(orderBefore, player.mode().upcoming(4), "an edit is not a reorder");
        }
    }

    @Nested
    @DisplayName("listeners")
    class Listeners {

        @Test
        @DisplayName("report the mode and song after every move")
        void listenersAreNotified() {
            List<String> seen = new ArrayList<>();
            player.addListener((mode, song) ->
                    seen.add(mode.id().name() + ":" + (song == null ? "-" : song.getTitle())));

            player.next();
            player.setMode(new ArrivalOrderMode());

            assertEquals(List.of("ALPHABETICAL:Bravo", "ARRIVAL_ORDER:Bravo"), seen);
        }

        @Test
        @DisplayName("a listener can remove itself while being notified")
        void listenerMayUnregisterItself() {
            List<String> seen = new ArrayList<>();
            PlaybackListener once = new PlaybackListener() {
                @Override
                public void playbackChanged(PlaybackMode mode, Song song) {
                    seen.add(song.getTitle());
                    player.removeListener(this);
                }
            };
            player.addListener(once);

            assertDoesNotThrow(() -> {
                player.next();
                player.next();
            });
            assertEquals(List.of("Bravo"), seen);
        }
    }

    @Test
    @DisplayName("selecting a song from the library moves the active mode to it")
    void selectDelegatesToTheMode() {
        Song charlie = library.all().get(2);

        assertTrue(player.select(charlie));
        assertSame(charlie, player.current());
    }

    @Test
    @DisplayName("complexities come from whichever mode is active")
    void complexitiesFollowTheMode() {
        assertTrue(player.complexities().get("next()").contains("log n"));

        player.setMode(new ShuffleMode());

        assertEquals("O(1)", player.complexities().get("next()"));
    }
}
