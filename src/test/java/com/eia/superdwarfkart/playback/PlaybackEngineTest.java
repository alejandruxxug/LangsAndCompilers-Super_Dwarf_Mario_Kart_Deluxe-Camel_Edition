package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The handover between the running order and the sound card.
 *
 * <p>These are the behaviours that would otherwise only show up during a demonstration: the song
 * that does not restart at the end of a one-song ring, the queue that keeps asking for more after
 * it has drained, the play count that goes up every time somebody unpauses.
 */
class PlaybackEngineTest {

    private final FakeAudioSource audio = new FakeAudioSource();

    /**
     * @param mode   the mode to drive
     * @param titles the songs to load, in order
     * @return an engine over a freshly built player
     */
    private PlaybackEngine engineOver(PlaybackMode mode, String... titles) {
        Library library = new Library(Songs.list(titles));
        return new PlaybackEngine(new Player(library, mode), audio);
    }

    @Test
    @DisplayName("the current song is opened at startup but not started")
    void opensTheCurrentSongWithoutPlaying() {
        PlaybackEngine engine = engineOver(new ArrivalOrderMode(), "Rainbow Road", "Sky Garden");

        assertNotNull(audio.loadedFile(), "the first press of play should not wait for a decode");
        assertFalse(engine.isPlaying(), "the application must not start making noise on its own");
    }

    @Test
    @DisplayName("playing starts the audio and records one play")
    void playCountsThePlayOnce() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        Song current = player.current();

        engine.play();

        assertTrue(engine.isPlaying());
        assertEquals(1, current.getPlayCount());
    }

    @Test
    @DisplayName("pausing and resuming does not count a second play")
    void resumingDoesNotCountAgain() {
        Library library = new Library(Songs.list("Rainbow Road"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        Song current = player.current();

        engine.play();
        engine.pause();
        engine.play();

        assertEquals(1, current.getPlayCount(), "a play is a song started, not a button pressed");
        assertTrue(engine.isPlaying());
    }

    @Test
    @DisplayName("toggle plays when paused and pauses when playing")
    void toggleFlipsPlayback() {
        PlaybackEngine engine = engineOver(new ArrivalOrderMode(), "Rainbow Road");

        engine.toggle();
        assertTrue(engine.isPlaying());

        engine.toggle();
        assertFalse(engine.isPlaying());
    }

    @Test
    @DisplayName("skipping while paused loads the next song and stays paused")
    void skippingWhilePausedStaysPaused() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);

        Song next = player.next();

        assertEquals(next.getFilePath(), audio.loadedFile());
        assertFalse(engine.isPlaying(), "pressing next is not pressing play");
    }

    @Test
    @DisplayName("skipping while playing carries on playing")
    void skippingWhilePlayingKeepsPlaying() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();

        Song next = player.next();

        assertEquals(next.getFilePath(), audio.loadedFile());
        assertTrue(engine.isPlaying());
    }

    @Test
    @DisplayName("a song playing out advances to the next one")
    void endOfTrackAdvances() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        Song first = player.current();

        audio.reachEndOfMedia();

        assertFalse(first.equals(player.current()), "the running order moved on");
        assertEquals(player.current().getFilePath(), audio.loadedFile());
        assertTrue(engine.isPlaying(), "one song following another must not need a keypress");
    }

    @Test
    @DisplayName("a one-song ring replays that song rather than falling silent")
    void endOfTrackRestartsASingleSongRing() {
        // The circular list answers next() with the song that just finished. An engine that
        // recognised it as "already loaded" would leave the room in silence after one play.
        Library library = new Library(Songs.list("Rainbow Road"));
        Player player = new Player(library, new ShuffleMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        int loadsBefore = audio.loads.size();

        audio.reachEndOfMedia();

        assertEquals(loadsBefore + 1, audio.loads.size(), "the song is opened again from the start");
        assertTrue(engine.isPlaying());
    }

    @Test
    @DisplayName("a drained queue stops instead of replaying its last song forever")
    void endOfTrackStopsWhenTheQueueIsEmpty() {
        // A drained queue keeps the last song current and answers next() with null - but it still
        // notifies, and an engine that reacted to that notification would reload the song that had
        // just finished and play it again, with no way out.
        Library library = new Library(Songs.list("Rainbow Road"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        int loadsBefore = audio.loads.size();

        audio.reachEndOfMedia();

        assertFalse(engine.isPlaying());
        assertEquals(loadsBefore, audio.loads.size(), "nothing was reopened");
    }

    @Test
    @DisplayName("the last song in the alphabet ends playback rather than wrapping")
    void endOfTrackStopsAtTheEndOfTheTree() {
        Library library = new Library(Songs.list("Bowser Castle", "Sky Garden"));
        Player player = new Player(library, new AlphabeticalMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        audio.reachEndOfMedia();
        assertTrue(engine.isPlaying(), "there was still a successor to walk to");

        audio.reachEndOfMedia();

        assertFalse(engine.isPlaying(), "an in-order traversal has an end; a ring does not");
    }

    @Test
    @DisplayName("changing mode with the same song current does not restart it")
    void switchingModeKeepsThePositionOfTheCurrentSong() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden", "Bowser Castle"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        Song playing = player.current();
        int loadsBefore = audio.loads.size();

        player.setMode(new AlphabeticalMode());

        assertSame(playing, player.current(), "the mode change carried the song across");
        assertEquals(loadsBefore, audio.loads.size(),
                "reloading here would restart the track under the listener");
        assertTrue(engine.isPlaying());
    }

    @Test
    @DisplayName("editing a song does not restart what is playing")
    void editingTheLibraryDoesNotRestartPlayback() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        int loadsBefore = audio.loads.size();

        player.current().setRating(80);
        library.update(player.current());

        assertEquals(loadsBefore, audio.loads.size());
        assertTrue(engine.isPlaying());
    }

    @Test
    @DisplayName("an unplayable file is reported once and does not stop the application")
    void reportsAnUnplayableFileWithoutThrowing() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        audio.makeUnplayable(player.current().getFilePath());

        List<String> reported = new ArrayList<>();
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.setOnFailure((song, message) -> reported.add(song.getTitle()));

        engine.play();

        assertFalse(engine.isPlaying());
        assertNotNull(engine.failure(), "the interface needs something to show in place of the time");
        // Pressing play retries the open - the file may have come back - and reports if it fails.
        assertEquals(List.of("Rainbow Road"), reported);
        assertEquals(0, player.current().getPlayCount(), "a song that never played was never played");
    }

    @Test
    @DisplayName("moving off an unplayable song clears the failure")
    void movingOnClearsTheFailure() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        audio.makeUnplayable(player.current().getFilePath());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        assertNotNull(engine.failure());

        player.next();

        assertNull(engine.failure());
        assertTrue(engine.isLoaded());
    }

    @Test
    @DisplayName("the playing time falls back to the library when the decoder cannot say")
    void durationFallsBackToTheLibrary() {
        Library library = new Library(Songs.list("Rainbow Road"));
        Player player = new Player(library, new ArrivalOrderMode());
        player.current().setDuration(Duration.ofSeconds(184));
        audio.setDuration(Duration.ZERO);
        PlaybackEngine engine = new PlaybackEngine(player, audio);

        assertEquals(Duration.ofSeconds(184), engine.duration());
    }

    @Test
    @DisplayName("an empty library leaves everything idle rather than failing")
    void handlesAnEmptyLibrary() {
        PlaybackEngine engine = new PlaybackEngine(new Player(new Library(), new ShuffleMode()), audio);

        engine.play();
        engine.seek(Duration.ofSeconds(10));

        assertFalse(engine.isPlaying());
        assertFalse(engine.isLoaded());
        assertEquals(Duration.ZERO, engine.duration());
    }

    @Test
    @DisplayName("closing releases the audio and stops following the player")
    void closeReleasesEverything() {
        Library library = new Library(Songs.list("Rainbow Road", "Sky Garden"));
        Player player = new Player(library, new ArrivalOrderMode());
        PlaybackEngine engine = new PlaybackEngine(player, audio);
        engine.play();
        int loadsBefore = audio.loads.size();

        engine.close();
        player.next();

        assertTrue(audio.isClosed());
        assertEquals(loadsBefore, audio.loads.size(), "a closed engine must stop loading songs");
    }
}
