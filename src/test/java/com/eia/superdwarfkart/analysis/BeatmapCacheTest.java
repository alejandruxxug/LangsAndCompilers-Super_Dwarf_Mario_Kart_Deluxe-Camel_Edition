package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storing beatmaps, and refusing to hand back ones that are no longer valid.
 *
 * <p>Two properties matter more than the round trip. <strong>The key is the content</strong>, so
 * moving or renaming a file keeps its analysis; and <strong>a stale analyser version is a miss</strong>,
 * so improving the detector cannot leave the game running on courses built by the old one.
 */
class BeatmapCacheTest {

    private static Beatmap sample(String hash, int version) {
        return new Beatmap(hash, version, 12.5, 128.5,
                new double[] {0.5, 1.0, 1.5, 2.0}, new double[] {0.5, 1.5});
    }

    @Test
    @DisplayName("what goes in comes back out unchanged")
    void storesAndLoadsBack(@TempDir Path directory) {
        BeatmapCache cache = new BeatmapCache(directory);
        Beatmap stored = sample("abc123", AppConfig.ANALYZER_VERSION);

        assertTrue(cache.store(stored));
        Optional<Beatmap> loaded = cache.loadByHash("abc123");

        assertTrue(loaded.isPresent());
        assertEquals(stored.bpm(), loaded.get().bpm());
        assertEquals(stored.durationSeconds(), loaded.get().durationSeconds());
        assertArrayEquals(stored.onsets(), loaded.get().onsets());
        assertArrayEquals(stored.strongBeats(), loaded.get().strongBeats());
        assertEquals("abc123", loaded.get().sourceHash());
    }

    @Test
    @DisplayName("a hash that was never stored is a miss, not a failure")
    void unknownHashIsAMiss(@TempDir Path directory) {
        assertTrue(new BeatmapCache(directory).loadByHash("nothing-here").isEmpty());
    }

    @Test
    @DisplayName("a map from a different analyser version is a miss, so the algorithm can improve")
    void staleAnalyzerVersionIsAMiss(@TempDir Path directory) {
        BeatmapCache cache = new BeatmapCache(directory);
        cache.store(sample("stale", AppConfig.ANALYZER_VERSION + 1));

        assertTrue(cache.loadByHash("stale").isEmpty(),
                "a cached course built by another version of the detector must not be reused");
        assertTrue(Files.exists(cache.fileFor("stale")),
                "the entry is skipped rather than deleted; a rollback would want it back");
    }

    @Test
    @DisplayName("a corrupt entry is a miss with a warning, never an exception")
    void corruptEntryIsAMiss(@TempDir Path directory) throws IOException {
        BeatmapCache cache = new BeatmapCache(directory);
        Files.createDirectories(directory);
        Files.writeString(cache.fileFor("broken"), "{ this is not json");

        assertTrue(cache.loadByHash("broken").isEmpty());
    }

    @Test
    @DisplayName("an entry whose data is invalid is a miss rather than a broken course")
    void invalidEntryIsAMiss(@TempDir Path directory) throws IOException {
        BeatmapCache cache = new BeatmapCache(directory);
        Files.createDirectories(directory);
        // Onsets out of order: valid JSON the Beatmap constructor will refuse.
        Files.writeString(cache.fileFor("jumbled"), """
                { "version": 1, "sourceHash": "jumbled", "analyzerVersion": %d,
                  "durationSeconds": 10, "bpm": 120,
                  "onsets": [3.0, 1.0, 2.0], "strongBeats": [] }
                """.formatted(AppConfig.ANALYZER_VERSION));

        assertTrue(cache.loadByHash("jumbled").isEmpty());
    }

    @Test
    @DisplayName("storing creates the directory rather than failing because it is absent")
    void storeCreatesTheDirectory(@TempDir Path directory) {
        Path nested = directory.resolve("not").resolve("made").resolve("yet");
        BeatmapCache cache = new BeatmapCache(nested);

        assertTrue(cache.store(sample("fresh", AppConfig.ANALYZER_VERSION)));
        assertTrue(Files.exists(cache.fileFor("fresh")));
    }

    @Test
    @DisplayName("no temporary files are left behind")
    void leavesNoTemporaryFiles(@TempDir Path directory) throws IOException {
        BeatmapCache cache = new BeatmapCache(directory);
        cache.store(sample("one", AppConfig.ANALYZER_VERSION));
        cache.store(sample("two", AppConfig.ANALYZER_VERSION));

        try (var entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(path -> path.toString().endsWith(".tmp")),
                    "a temporary file survived the write");
        }
    }

    @Test
    @DisplayName("a rewrite replaces the entry rather than appending a second one")
    void storingAgainReplaces(@TempDir Path directory) throws IOException {
        BeatmapCache cache = new BeatmapCache(directory);
        cache.store(sample("same", AppConfig.ANALYZER_VERSION));
        cache.store(new Beatmap("same", AppConfig.ANALYZER_VERSION, 20, 90,
                new double[] {1}, new double[] {1}));

        assertEquals(90, cache.loadByHash("same").orElseThrow().bpm());
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    @DisplayName("a beatmap with no hash is not stored, because it could never be found again")
    void refusesToStoreWithoutAHash(@TempDir Path directory) {
        assertFalse(new BeatmapCache(directory).store(Beatmap.EMPTY));
    }

    // ------------------------------------------------------------------
    // Hashing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the same bytes hash the same wherever the file is, so moving a song keeps it")
    void identicalContentHashesIdentically(@TempDir Path directory) throws IOException {
        Path first = Files.writeString(directory.resolve("song.mp3"), "the same bytes");
        Path renamed = Files.writeString(
                directory.resolve("renamed-and-moved.mp3"), "the same bytes");

        assertEquals(BeatmapCache.hash(first), BeatmapCache.hash(renamed));
    }

    @Test
    @DisplayName("different bytes hash differently, so two songs cannot share a course")
    void differentContentHashesDifferently(@TempDir Path directory) throws IOException {
        Path first = Files.writeString(directory.resolve("a.mp3"), "one song");
        Path second = Files.writeString(directory.resolve("b.mp3"), "another song");

        assertNotEquals(BeatmapCache.hash(first), BeatmapCache.hash(second));
    }

    @Test
    @DisplayName("the hash is a full SHA-256 in hexadecimal")
    void hashIsHexadecimalSha256(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("x.mp3"), "content");

        String hash = BeatmapCache.hash(file);

        assertEquals(64, hash.length(), "SHA-256 is 32 bytes, which is 64 hex digits");
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("a file spanning several hash blocks still hashes consistently")
    void hashesFilesLargerThanOneBlock(@TempDir Path directory) throws IOException {
        byte[] large = new byte[300 * 1024];
        for (int index = 0; index < large.length; index++) {
            large[index] = (byte) (index % 251);
        }
        Path first = Files.write(directory.resolve("large-a.bin"), large);
        Path second = Files.write(directory.resolve("large-b.bin"), large);

        assertEquals(BeatmapCache.hash(first), BeatmapCache.hash(second));
    }

    @Test
    @DisplayName("looking up a missing file is a miss rather than an exception")
    void missingFileIsAMiss(@TempDir Path directory) {
        assertTrue(new BeatmapCache(directory).load(directory.resolve("gone.mp3")).isEmpty());
    }

    /**
     * How a track is identified when it is not a file.
     *
     * <p>The test is "does this locator resolve to a readable file", never a prefix. Putting a
     * check for {@code spotify:} here would give {@code analysis/} an opinion about who can open
     * what, which is precisely what it is not supposed to have.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("keying a track by what it plays from")
    class Keys {

        private static final String URI = "spotify:track:4cOdK2wGLETKBW3PvgPWqT";

        @Test
        @DisplayName("a file is still keyed by its contents, not by its name")
        void aFileIsKeyedByContent(@TempDir Path directory) throws IOException {
            Path here = Files.writeString(directory.resolve("song.mp3"), "the same music");
            Path there = Files.writeString(directory.resolve("moved.mp3"), "the same music");

            assertEquals(BeatmapCache.hash(here), BeatmapCache.keyFor(here.toString()));
            assertEquals(BeatmapCache.keyFor(here.toString()),
                    BeatmapCache.keyFor(there.toString()),
                    "two copies of one recording must share one analysis, as they always have");
        }

        @Test
        @DisplayName("a stream is keyed by its URI, which is stable and has no bytes to read")
        void aStreamIsKeyedByItsUri() {
            String key = BeatmapCache.keyFor(URI);

            assertEquals(64, key.length());
            assertTrue(key.matches("[0-9a-f]{64}"));
            assertEquals(key, BeatmapCache.keyFor(URI), "the same track must key the same way");
        }

        @Test
        @DisplayName("two different tracks key differently")
        void differentTracksDiffer() {
            assertNotEquals(BeatmapCache.keyFor(URI),
                    BeatmapCache.keyFor("spotify:track:1301WleyT98MSxVHPZCA6M"));
        }

        @Test
        @DisplayName("a beatmap keyed from a URI round-trips through the cache")
        void aStreamedBeatmapRoundTrips(@TempDir Path directory) {
            BeatmapCache cache = new BeatmapCache(directory);
            String key = BeatmapCache.keyFor(URI);
            Beatmap map = new Beatmap(key, AppConfig.ANALYZER_VERSION, 120, 128,
                    new double[] {0.5, 1.0}, new double[] {0.5});

            assertTrue(cache.store(map));

            assertTrue(cache.loadByHash(key).isPresent(),
                    "a streamed track's course has to be found the same way a file's is, or it is "
                            + "rebuilt from scratch on every play and never appears at all");
            assertEquals(128, cache.loadByHash(key).orElseThrow().bpm());
        }

        @Test
        @DisplayName("a path that names nothing on disk is keyed as text rather than refused")
        void aMissingFileIsKeyedByName(@TempDir Path directory) {
            String missing = directory.resolve("gone.mp3").toString();

            assertNotNull(BeatmapCache.keyFor(missing));
        }
    }
}
