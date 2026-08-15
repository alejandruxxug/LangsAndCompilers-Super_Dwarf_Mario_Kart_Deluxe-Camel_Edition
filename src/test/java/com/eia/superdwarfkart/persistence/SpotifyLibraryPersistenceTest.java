package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.model.SongSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storing a library that holds both kinds of song.
 *
 * <p>The interesting cases are all about a file written by a different version of this
 * application: an older one that had never heard of Spotify, and a hand-edited one whose
 * {@code source} field disagrees with what it actually holds.
 */
class SpotifyLibraryPersistenceTest {

    private static final String URI = "spotify:track:4uLU6hMCjMI75M1A2tKUQC";

    @Test
    @DisplayName("a streamed song round trips, URI and remote cover included")
    void streamedSongRoundTrips(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));
        Song original = Song.spotify(URI, "Crimewave", "Crystal Castles");
        original.setAlbum("Crystal Castles");
        original.setDuration(Duration.ofSeconds(258));
        original.setYear(2008);
        original.setRating(90);
        original.setFavorite(true);
        original.setCoverUrl("https://i.scdn.co/image/abc");

        repository.saveAll(List.of(original));
        Song restored = repository.loadAll().get(0);

        assertEquals(original.getId(), restored.getId());
        assertEquals(URI, restored.getSpotifyUri());
        assertNull(restored.getFilePath());
        assertEquals(SongSource.SPOTIFY, restored.getSource());
        assertEquals("https://i.scdn.co/image/abc", restored.getCoverUrl());
        assertEquals(90, restored.getRating());
        assertTrue(restored.isFavorite());
        assertEquals(Duration.ofSeconds(258), restored.getDuration());
    }

    @Test
    @DisplayName("both kinds live in one file without disturbing each other")
    void bothKindsShareOneFile(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));
        Song local = new Song("Local", "Artist", Path.of("/music/song.mp3"));
        Song streamed = Song.spotify(URI, "Streamed", "Artist");

        repository.saveAll(List.of(local, streamed));
        List<Song> loaded = repository.loadAll();

        assertEquals(2, loaded.size());
        assertEquals(SongSource.LOCAL, loaded.get(0).getSource());
        assertEquals(SongSource.SPOTIFY, loaded.get(1).getSource());
    }

    /**
     * A library written before Spotify existed.
     *
     * <p>Nothing migrates: every song in a version 1 file has a file path, which is still exactly
     * what makes a song local. Worth pinning, because a format bump that quietly dropped everyone's
     * library would be the worst possible way to ship this.
     */
    @Test
    @DisplayName("a version 1 file loads unchanged")
    void anOlderFileStillLoads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        Files.writeString(file, """
                {"version":1,"songs":[
                  {"id":"abc","title":"Old","artist":"Artist","album":"Album",
                   "durationMillis":120000,"genre":"ROCK","year":1999,"rating":50,
                   "filePath":"/music/old.mp3","coverPath":null,"favorite":true,"playCount":3}
                ]}""");

        List<Song> loaded = new LibraryRepository(file).loadAll();

        assertEquals(1, loaded.size());
        assertEquals("Old", loaded.get(0).getTitle());
        assertEquals(Path.of("/music/old.mp3"), loaded.get(0).getFilePath());
        assertEquals(SongSource.LOCAL, loaded.get(0).getSource());
        assertEquals(3, loaded.get(0).getPlayCount());
    }

    /**
     * The stored {@code source} field is written for a human and ignored on the way back in.
     *
     * <p>Same decision as the runner's rank: a letter that can disagree with the two fields beside
     * it is a bug waiting for a hand edit. What a song is follows from what it holds.
     */
    @Test
    @DisplayName("a mismatched source field does not override what the entry actually holds")
    void theUriDecidesNotTheSourceField(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        Files.writeString(file, """
                {"version":2,"songs":[
                  {"id":"a","title":"Claims local","artist":"Artist","source":"LOCAL",
                   "spotifyUri":"spotify:track:abc"},
                  {"id":"b","title":"Claims spotify","artist":"Artist","source":"SPOTIFY",
                   "filePath":"/music/real.mp3"}
                ]}""");

        List<Song> loaded = new LibraryRepository(file).loadAll();

        assertEquals(SongSource.SPOTIFY, loaded.get(0).getSource());
        assertEquals(SongSource.LOCAL, loaded.get(1).getSource());
    }

    @Test
    @DisplayName("an entry with neither a path nor a URI is skipped, not fatal")
    void anUnplayableEntryIsSkipped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        Files.writeString(file, """
                {"version":2,"songs":[
                  {"id":"a","title":"Nowhere","artist":"Artist"},
                  {"id":"b","title":"Fine","artist":"Artist","filePath":"/music/ok.mp3"}
                ]}""");

        List<Song> loaded = new LibraryRepository(file).loadAll();

        assertEquals(1, loaded.size(), "one bad record must not lock the user out of the rest");
        assertEquals("Fine", loaded.get(0).getTitle());
    }
}
