package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.model.Genre;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LibraryRepository")
class LibraryRepositoryTest {

    private static Song fullyPopulatedSong() {
        Song song = new Song("Rainbow Road", "Koji Kondo", Path.of("/music/rainbow-road.mp3"));
        song.setAlbum("Mario Kart 64");
        song.setDuration(Duration.ofSeconds(214));
        song.setGenre(Genre.SOUNDTRACK);
        song.setYear(1996);
        song.setRating(97);
        song.setCoverPath(Path.of("/covers/mk64.png"));
        song.setFavorite(true);
        song.setPlayCount(12);
        return song;
    }

    @Test
    @DisplayName("a round trip preserves every field")
    void roundTripPreservesEveryField(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));
        Song original = fullyPopulatedSong();

        repository.saveAll(List.of(original));
        List<Song> loaded = repository.loadAll();

        assertEquals(1, loaded.size());
        Song restored = loaded.get(0);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getTitle(), restored.getTitle());
        assertEquals(original.getArtist(), restored.getArtist());
        assertEquals(original.getAlbum(), restored.getAlbum());
        assertEquals(original.getDuration(), restored.getDuration());
        assertEquals(original.getGenre(), restored.getGenre());
        assertEquals(original.getYear(), restored.getYear());
        assertEquals(original.getRating(), restored.getRating());
        assertEquals(original.getFilePath(), restored.getFilePath());
        assertEquals(original.getCoverPath(), restored.getCoverPath());
        assertEquals(original.isFavorite(), restored.isFavorite());
        assertEquals(original.getPlayCount(), restored.getPlayCount());
        assertEquals(original, restored, "identity is carried by the stored id");
    }

    @Test
    @DisplayName("a song without a cover round trips with no cover")
    void nullCoverRoundTrips(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));
        Song song = new Song("No Cover", "Artist", Path.of("/music/x.mp3"));

        repository.saveAll(List.of(song));

        assertNull(repository.loadAll().get(0).getCoverPath());
    }

    @Test
    @DisplayName("order is preserved")
    void orderIsPreserved(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));
        List<Song> songs = List.of(
                new Song("First", "A", Path.of("/music/1.mp3")),
                new Song("Second", "B", Path.of("/music/2.mp3")),
                new Song("Third", "C", Path.of("/music/3.mp3")));

        repository.saveAll(songs);

        assertEquals(List.of("First", "Second", "Third"),
                repository.loadAll().stream().map(Song::getTitle).toList());
    }

    @Test
    @DisplayName("a missing file is a first run, not an error")
    void missingFileReturnsEmpty(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("does-not-exist.json"));

        assertEquals(List.of(), repository.loadAll());
    }

    @Test
    @DisplayName("saving creates the directory when it is missing")
    void savingCreatesTheDirectory(@TempDir Path dir) {
        Path nested = dir.resolve("nested").resolve("deeper").resolve("library.json");
        LibraryRepository repository = new LibraryRepository(nested);

        repository.saveAll(List.of(new Song("Song", "Artist", Path.of("/music/a.mp3"))));

        assertTrue(Files.exists(nested));
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    @DisplayName("saving an empty library writes a readable empty file")
    void savingEmptyLibrary(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));

        repository.saveAll(List.of());

        assertEquals(List.of(), repository.loadAll());
    }

    @Test
    @DisplayName("saving twice replaces the contents rather than appending")
    void savingReplaces(@TempDir Path dir) {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));

        repository.saveAll(List.of(new Song("One", "A", Path.of("/music/1.mp3"))));
        repository.saveAll(List.of(new Song("Two", "B", Path.of("/music/2.mp3"))));

        List<Song> loaded = repository.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("Two", loaded.get(0).getTitle());
    }

    @Test
    @DisplayName("corrupt JSON reports a clear failure instead of starting empty")
    void corruptFileThrows(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        Files.writeString(file, "{ this is not valid json");
        LibraryRepository repository = new LibraryRepository(file);

        PersistenceException thrown = assertThrows(PersistenceException.class, repository::loadAll);

        assertTrue(thrown.getMessage().contains(file.toString()),
                "the message must name the file so the user can find it: " + thrown.getMessage());
    }

    @Test
    @DisplayName("an invalid entry is skipped and the rest of the library still loads")
    void invalidEntryIsSkipped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        // The middle entry carries a rating of 500, which the model rejects. Losing that one
        // record must not cost the user the other two.
        Files.writeString(file, """
                {
                  "version": 1,
                  "songs": [
                    { "id": "a", "title": "Good One", "artist": "Artist",
                      "filePath": "/music/a.mp3", "rating": 50 },
                    { "id": "b", "title": "Bad Rating", "artist": "Artist",
                      "filePath": "/music/b.mp3", "rating": 500 },
                    { "id": "c", "title": "Good Two", "artist": "Artist",
                      "filePath": "/music/c.mp3", "rating": 10 }
                  ]
                }
                """);
        LibraryRepository repository = new LibraryRepository(file);

        List<Song> loaded = repository.loadAll();

        assertEquals(List.of("Good One", "Good Two"), loaded.stream().map(Song::getTitle).toList());
    }

    @Test
    @DisplayName("an unknown genre falls back rather than failing the load")
    void unknownGenreFallsBack(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "songs": [
                    { "id": "a", "title": "Song", "artist": "Artist",
                      "filePath": "/music/a.mp3", "genre": "VAPORWAVE" }
                  ]
                }
                """);
        LibraryRepository repository = new LibraryRepository(file);

        assertEquals(Genre.UNKNOWN, repository.loadAll().get(0).getGenre());
    }

    @Test
    @DisplayName("unknown fields in the file are ignored, so a newer format still opens")
    void unknownFieldsAreIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        Files.writeString(file, """
                {
                  "version": 99,
                  "someFutureField": true,
                  "songs": [
                    { "id": "a", "title": "Song", "artist": "Artist",
                      "filePath": "/music/a.mp3", "loudnessLufs": -14.0 }
                  ]
                }
                """);
        LibraryRepository repository = new LibraryRepository(file);

        assertEquals(1, repository.loadAll().size());
    }

    @Test
    @DisplayName("no temporary files are left behind after a successful save")
    void noTemporaryFilesRemain(@TempDir Path dir) throws IOException {
        LibraryRepository repository = new LibraryRepository(dir.resolve("library.json"));

        repository.saveAll(List.of(fullyPopulatedSong()));

        try (Stream<Path> entries = Files.list(dir)) {
            List<String> names = entries.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of("library.json"), names,
                    "the atomic write must clean up after itself");
        }
    }

    @Test
    @DisplayName("a failed write leaves the previous library intact")
    void failedWriteKeepsThePreviousFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("library.json");
        LibraryRepository repository = new LibraryRepository(file);
        repository.saveAll(List.of(new Song("Original", "Artist", Path.of("/music/a.mp3"))));
        String before = Files.readString(file);

        // A directory where the temporary file would go cannot be written to.
        Path readOnlyTarget = dir.resolve("locked").resolve("library.json");
        Files.createDirectories(readOnlyTarget.getParent());
        assertTrue(readOnlyTarget.getParent().toFile().setWritable(false),
                "this test needs to be able to make a directory read-only");
        try {
            LibraryRepository blocked = new LibraryRepository(readOnlyTarget);
            assertThrows(PersistenceException.class,
                    () -> blocked.saveAll(List.of(new Song("New", "Artist", Path.of("/music/b.mp3")))));
        } finally {
            readOnlyTarget.getParent().toFile().setWritable(true);
        }

        assertEquals(before, Files.readString(file), "the untouched library must be byte-identical");
    }

    @Test
    @DisplayName("the storage location is reported for messages and settings")
    void reportsStorageLocation(@TempDir Path dir) {
        Path file = dir.resolve("library.json");

        assertEquals(file, new LibraryRepository(file).storageLocation());
    }

    @Test
    @DisplayName("the default repository points inside the application directory")
    void defaultLocation() {
        Path location = new LibraryRepository().storageLocation();

        assertNotNull(location);
        assertEquals("library.json", location.getFileName().toString());
        assertTrue(location.toString().contains(".superdwarfkart"),
                "expected the per-user application directory, got " + location);
        assertFalse(Files.isDirectory(location), "the library is a file, not a directory");
    }
}
