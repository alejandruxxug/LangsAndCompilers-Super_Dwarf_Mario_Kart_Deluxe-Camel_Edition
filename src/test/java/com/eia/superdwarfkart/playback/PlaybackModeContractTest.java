package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every mode must do, whichever structure backs it.
 *
 * <p>Run against all three, so a fourth mode added later is held to the same contract by adding
 * one line. These are the guarantees {@link Player} relies on when it declines to ask which mode
 * it is holding.
 */
@DisplayName("Every playback mode")
class PlaybackModeContractTest {

    /** @return one factory per mode, named for the test report */
    static Stream<org.junit.jupiter.params.provider.Arguments> modes() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("Shuffle",
                        (Supplier<PlaybackMode>) ShuffleMode::new),
                org.junit.jupiter.params.provider.Arguments.of("Arrival order",
                        (Supplier<PlaybackMode>) ArrivalOrderMode::new),
                org.junit.jupiter.params.provider.Arguments.of("Alphabetical",
                        (Supplier<PlaybackMode>) AlphabeticalMode::new));
    }

    private static final List<Song> LIBRARY = Songs.list("Alpha", "Bravo", "Charlie", "Delta");

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("starts on a song once loaded")
    void loadsAndStarts(String name, Supplier<PlaybackMode> factory) {
        PlaybackMode mode = factory.get();
        mode.load(LIBRARY);

        assertNotNull(mode.current(), "a loaded mode must have something playing");
        assertFalse(mode.isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("copes with an empty library instead of failing")
    void handlesEmptyLibrary(String name, Supplier<PlaybackMode> factory) {
        PlaybackMode mode = factory.get();
        mode.load(List.of());

        assertTrue(mode.isEmpty());
        assertEquals(0, mode.size());
        assertNull(mode.current());
        assertNull(mode.next());
        assertNull(mode.peekNext());
        assertFalse(mode.hasNext());
        assertEquals(List.of(), mode.upcoming(5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("peekNext agrees with next, and does not consume")
    void peekMatchesNext(String name, Supplier<PlaybackMode> factory) {
        PlaybackMode mode = factory.get();
        mode.load(LIBRARY);

        int sizeBefore = mode.size();
        Song peeked = mode.peekNext();
        assertEquals(sizeBefore, mode.size(), "peeking must not consume anything");

        assertSame(peeked, mode.next(), "the up-next label must match what plays");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("reloading restores the mode to its freshly loaded state")
    void reloadResets(String name, Supplier<PlaybackMode> factory) {
        PlaybackMode mode = factory.get();
        mode.load(LIBRARY);
        int sizeWhenFresh = mode.size();
        String firstSong = mode.current().getTitle();

        mode.next();
        mode.next();
        mode.load(LIBRARY);

        assertEquals(sizeWhenFresh, mode.size(), "a reload restores the full ordering");
        assertNotNull(mode.current());
        if (!name.equals("Shuffle")) {
            // Shuffle draws a new ordering each time by design; the other two are deterministic.
            assertEquals(firstSong, mode.current().getTitle());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("playing never removes songs from the library")
    void playingDoesNotConsumeTheLibrary(String name, Supplier<PlaybackMode> factory) {
        // The queue is the one that could get this wrong, but the guarantee belongs to every
        // mode: a mode is a view built from the library, never the storage of it.
        PlaybackMode mode = factory.get();
        mode.load(LIBRARY);

        for (int step = 0; step < LIBRARY.size() * 2; step++) {
            mode.next();
        }

        assertEquals(4, LIBRARY.size());
        assertEquals(List.of("Alpha", "Bravo", "Charlie", "Delta"), Songs.titles(LIBRARY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("describes its own costs for the complexity panel")
    void publishesComplexities(String name, Supplier<PlaybackMode> factory) {
        PlaybackMode mode = factory.get();
        mode.load(LIBRARY);

        assertFalse(mode.complexities().isEmpty());
        assertNotNull(mode.structureName());
        assertEquals(mode.id().structureName(), mode.structureName());
        assertEquals(mode.id().supportsPrevious(), mode.supportsPrevious());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modes")
    @DisplayName("refuses a song it does not hold, without moving")
    void selectRejectsStrangers(String name, Supplier<PlaybackMode> factory) {
        PlaybackMode mode = factory.get();
        mode.load(LIBRARY);
        Song before = mode.current();

        assertFalse(mode.select(Songs.of("Not Loaded", "Nobody")));
        assertFalse(mode.select(null));
        assertSame(before, mode.current());
    }
}
