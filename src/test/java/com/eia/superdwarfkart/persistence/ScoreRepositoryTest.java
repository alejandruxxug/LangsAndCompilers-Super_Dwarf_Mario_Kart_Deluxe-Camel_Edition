package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.game.Rank;
import com.eia.superdwarfkart.game.ScoreEntry;
import com.eia.superdwarfkart.game.SpeedClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The score board: keyed by song and class, and never spoiled by a worse run. */
class ScoreRepositoryTest {

    @TempDir
    Path directory;

    private ScoreRepository repository() {
        return new ScoreRepository(directory.resolve("scores.json"));
    }

    private static ScoreEntry run(String songId, SpeedClass speedClass, int collected,
                                  int available) {
        return new ScoreEntry(songId, speedClass, collected, collected, available, Instant.now());
    }

    @Test
    @DisplayName("a first run is stored")
    void aFirstRunIsStored() {
        ScoreRepository scores = repository();
        assertTrue(scores.record(run("song", SpeedClass.CC50, 40, 100)));

        Optional<ScoreEntry> best = scores.best("song", SpeedClass.CC50);
        assertTrue(best.isPresent());
        assertEquals(40, best.get().coinsCollected());
    }

    @Test
    @DisplayName("a better run replaces the one before it")
    void aBetterRunReplaces() {
        ScoreRepository scores = repository();
        scores.record(run("song", SpeedClass.CC50, 40, 100));

        assertTrue(scores.record(run("song", SpeedClass.CC50, 90, 100)));
        assertEquals(90, scores.best("song", SpeedClass.CC50).orElseThrow().coinsCollected());
    }

    @Test
    @DisplayName("a worse run is refused, so skipping a track cannot wipe out the run before it")
    void aWorseRunIsRefused() {
        ScoreRepository scores = repository();
        scores.record(run("song", SpeedClass.CC50, 90, 100));

        assertFalse(scores.record(run("song", SpeedClass.CC50, 3, 100)),
                "a track being skipped ten seconds in is how most runs actually end");
        assertEquals(90, scores.best("song", SpeedClass.CC50).orElseThrow().coinsCollected());
    }

    @Test
    @DisplayName("the same song at a different class is a different course and a different record")
    void classesAreKeyedSeparately() {
        ScoreRepository scores = repository();
        scores.record(run("song", SpeedClass.CC50, 90, 100));
        scores.record(run("song", SpeedClass.CC200, 20, 400));

        assertEquals(90, scores.best("song", SpeedClass.CC50).orElseThrow().coinsCollected());
        assertEquals(20, scores.best("song", SpeedClass.CC200).orElseThrow().coinsCollected());
        assertEquals(2, scores.size());
    }

    @Test
    @DisplayName("the badge shows the best rank across the classes, breaking a tie towards the faster")
    void bestAnyClassPrefersTheBetterDrive() {
        ScoreRepository scores = repository();
        scores.record(run("song", SpeedClass.CC50, 50, 100));
        scores.record(run("song", SpeedClass.CC200, 380, 400));

        assertSame(SpeedClass.CC200, scores.bestAnyClass("song").orElseThrow().speedClass());

        scores.record(run("tie", SpeedClass.CC50, 50, 100));
        scores.record(run("tie", SpeedClass.CC150, 50, 100));
        assertSame(SpeedClass.CC150, scores.bestAnyClass("tie").orElseThrow().speedClass());
    }

    @Test
    @DisplayName("a song never driven has no badge")
    void anUndrivenSongHasNoBest() {
        assertTrue(repository().bestAnyClass("never-driven").isEmpty());
        assertTrue(repository().bestAnyClass(null).isEmpty());
    }

    @Test
    @DisplayName("the board survives a restart")
    void theBoardSurvivesARestart() {
        repository().record(run("song", SpeedClass.CC150, 77, 100));

        ScoreRepository reopened = repository();
        ScoreEntry best = reopened.best("song", SpeedClass.CC150).orElseThrow();
        assertEquals(77, best.coinsCollected());
        assertEquals(100, best.coinsAvailable());
        assertSame(Rank.A, best.rank());
    }

    @Test
    @DisplayName("a missing file is a first run, not an error")
    void aMissingFileIsEmpty() {
        ScoreRepository scores = repository();
        assertTrue(scores.loadAll().isEmpty());
        assertEquals(0, scores.size());
    }

    @Test
    @DisplayName("an unreadable board leaves a usable repository rather than a broken window")
    void anUnreadableBoardDegrades() throws IOException {
        Path file = directory.resolve("scores.json");
        Files.writeString(file, "{ this is not json");

        ScoreRepository scores = new ScoreRepository(file);
        assertEquals(0, scores.size(), "high scores must never stop the application opening");
        assertThrows(PersistenceException.class, scores::loadAll,
                "the failure is still reported to anyone who asks for the data directly");
    }

    @Test
    @DisplayName("one bad entry does not cost the user the rest of their scores")
    void oneBadEntryIsSkipped() throws IOException {
        Path file = directory.resolve("scores.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "scores": [
                    { "songId": "good", "speedClass": "CC50", "score": 9,
                      "coinsCollected": 9, "coinsAvailable": 10, "achievedAtMillis": 1 },
                    { "songId": "bad", "speedClass": "CC9000", "score": 1,
                      "coinsCollected": 1, "coinsAvailable": 1, "achievedAtMillis": 1 }
                  ]
                }
                """);

        ScoreRepository scores = new ScoreRepository(file);
        assertEquals(1, scores.size());
        assertTrue(scores.best("good", SpeedClass.CC50).isPresent());
    }

    @Test
    @DisplayName("the rank is derived, so a hand-edited file cannot contradict itself")
    void theRankIsDerived() throws IOException {
        Path file = directory.resolve("scores.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "scores": [
                    { "songId": "s", "speedClass": "CC50", "score": 1, "rank": "S",
                      "coinsCollected": 1, "coinsAvailable": 100, "achievedAtMillis": 1 }
                  ]
                }
                """);

        assertSame(Rank.D, new ScoreRepository(file).best("s", SpeedClass.CC50).orElseThrow().rank(),
                "one coin in a hundred is a D whatever the file claims");
    }

    @Test
    @DisplayName("a run cannot claim more coins than the course held")
    void impossibleRunsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreEntry("s", SpeedClass.CC50, 10, 20, 10, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreEntry("s", SpeedClass.CC50, -1, 0, 10, Instant.now()));
    }
}
