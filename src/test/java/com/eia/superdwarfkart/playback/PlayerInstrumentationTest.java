package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.ui.visualizer.Measurement;
import com.eia.superdwarfkart.ui.visualizer.OperationCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player as the thing that measures, and the boundary that keeps the measurements honest.
 */
@DisplayName("Player instrumentation")
class PlayerInstrumentationTest {

    private static Library library(String... titles) {
        return new Library(Songs.list(titles));
    }

    @Test
    @DisplayName("measures each navigation under the name the complexity table uses")
    void measuresUnderCanonicalNames() {
        OperationCounter counter = new OperationCounter();
        Library library = library("Alpha", "Bravo", "Charlie", "Delta");
        Player player = new Player(library, new AlphabeticalMode(counter), counter);

        // Building is measured on construction, before anything else has happened.
        Measurement build = counter.latest("build");
        assertNotNull(build, "constructing the player must measure the build");
        assertEquals(4, build.n());
        assertEquals("BinarySearchTree", build.structure());

        player.next();
        assertNotNull(counter.latest("next()"));

        player.previous();
        assertNotNull(counter.latest("previous()"));

        player.select(library.all().get(2));
        assertNotNull(counter.latest("select(song)"));
    }

    @Test
    @DisplayName("closes the measurement before the listeners run")
    void listenersDoNotInflateTheMeasurement() {
        Library library = library("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot");

        OperationCounter quiet = new OperationCounter();
        Player withoutListener = new Player(library, new AlphabeticalMode(quiet), quiet);
        withoutListener.next();
        int expected = quiet.latest("next()").steps();

        OperationCounter noisy = new OperationCounter();
        Player withListener = new Player(library, new AlphabeticalMode(noisy), noisy);
        // The real bar does exactly this on every change: redrawing "up next" peeks, and in a
        // tree a peek is a whole successor walk. Billing it to the navigation that triggered it
        // would roughly double every reported cost.
        withListener.addListener((mode, song) -> {
            withListener.peekNext();
            withListener.canGoNext();
        });
        withListener.next();

        assertEquals(expected, noisy.latest("next()").steps(),
                "work done by a listener must not be added to the operation it was notified about");
    }

    @Test
    @DisplayName("takes n before the operation, so a draining queue reports the size it worked over")
    void sizeIsTakenBeforeTheCall() {
        OperationCounter counter = new OperationCounter();
        Library library = library("Alpha", "Bravo", "Charlie");
        Player player = new Player(library, new ArrivalOrderMode(counter), counter);

        // Building takes the front song as current, leaving two queued.
        int before = player.mode().size();
        player.next();

        assertEquals(before, counter.latest("next()").n());
        assertEquals(before - 1, player.mode().size());
    }

    @Test
    @DisplayName("does not measure a previous that the mode refuses")
    void oneWayModeIsNotMeasured() {
        OperationCounter counter = new OperationCounter();
        Player player = new Player(library("Alpha", "Bravo"), new ArrivalOrderMode(counter), counter);

        assertNull(player.previous(), "a queue cannot go back");
        assertNull(counter.latest("previous()"),
                "an operation that never ran must not appear as a measurement");
    }

    @Test
    @DisplayName("rebuilding from a sorted ordering degenerates the tree and costs more to search")
    void reloadWithOrderingChangesShape() {
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < 63; i++) {
            songs.add(Songs.of(String.format("Track %02d", i), "Koji Kondo"));
        }
        Library library = new Library(songs);

        OperationCounter counter = new OperationCounter();
        AlphabeticalMode mode = new AlphabeticalMode(counter);
        Player player = new Player(library, mode, counter);

        List<Song> shuffled = new ArrayList<>(songs);
        Collections.shuffle(shuffled, new Random(11));
        player.reload(shuffled);
        int shuffledHeight = mode.height();

        List<Song> sorted = new ArrayList<>(songs);
        sorted.sort(Song.BY_TITLE);
        player.reload(sorted);
        int sortedHeight = mode.height();

        // This is what the tree view's two shape buttons demonstrate, in one click, live.
        assertEquals(songs.size() - 1, sortedHeight,
                "inserting in order must produce a straight line");
        assertTrue(shuffledHeight < sortedHeight / 3,
                "a shuffled insert should stay near log n, was " + shuffledHeight);

        // And the extra height is paid for on every search.
        Song last = sorted.get(sorted.size() - 1);
        player.select(last);
        assertTrue(counter.latest("select(song)").steps() >= songs.size(),
                "searching a degenerate tree must cost about n");
    }

    @Test
    @DisplayName("an uninstrumented player still works")
    void counterIsOptional() {
        Player player = new Player(library("Alpha", "Bravo"), new ShuffleMode());
        assertNotNull(player.next());
    }
}
