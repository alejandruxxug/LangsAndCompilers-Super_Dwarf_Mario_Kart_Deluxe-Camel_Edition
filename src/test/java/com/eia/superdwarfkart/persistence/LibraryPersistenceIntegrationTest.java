package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.model.Genre;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the library and its storage together, the way the library view wires them up:
 * every change to the library is written straight back to disk.
 *
 * <p>Deliberately free of JavaFX, so the persistence contract the view depends on is verified
 * without needing a display.
 */
@DisplayName("Library persistence")
class LibraryPersistenceIntegrationTest {

    @TempDir
    Path dir;

    private Library library;
    private LibraryRepository repository;
    private int saveCount;

    @BeforeEach
    void setUp() {
        repository = new LibraryRepository(dir.resolve("library.json"));
        library = new Library(repository.loadAll());
        // The same rule the view applies: persist every change. Loading does not reach a
        // listener, because the library is built from the stored songs before any is attached.
        library.addListener((change, song) -> {
            saveCount++;
            repository.saveAll(library.all());
        });
    }

    private Library reopen() {
        return new Library(new LibraryRepository(dir.resolve("library.json")).loadAll());
    }

    private static Song song(String title, String artist) {
        return new Song(title, artist, Path.of("/music/" + title.replace(' ', '-') + ".mp3"));
    }

    @Test
    @DisplayName("an added song survives a restart")
    void addPersists() {
        library.add(song("Rainbow Road", "Koji Kondo"));

        assertEquals(1, saveCount);
        assertEquals(List.of("Rainbow Road"), reopen().all().stream().map(Song::getTitle).toList());
    }

    @Test
    @DisplayName("a rating edit survives a restart")
    void ratingEditPersists() {
        Song song = song("Rainbow Road", "Koji Kondo");
        library.add(song);

        song.setRating(97);
        library.update(song);

        assertEquals(97, reopen().findById(song.getId()).orElseThrow().getRating());
    }

    @Test
    @DisplayName("a deletion survives a restart")
    void deletePersists() {
        Song keep = song("Keep Me", "Artist");
        Song drop = song("Drop Me", "Artist");
        library.add(keep);
        library.add(drop);

        library.remove(drop);

        List<String> titles = reopen().all().stream().map(Song::getTitle).toList();
        assertEquals(List.of("Keep Me"), titles);
    }

    @Test
    @DisplayName("favourites and play counts survive a restart")
    void bonusFieldsPersist() {
        Song song = song("Rainbow Road", "Koji Kondo");
        library.add(song);

        song.toggleFavorite();
        song.incrementPlayCount();
        song.incrementPlayCount();
        library.update(song);

        Song reloaded = reopen().findById(song.getId()).orElseThrow();
        assertTrue(reloaded.isFavorite());
        assertEquals(2, reloaded.getPlayCount());
    }

    @Test
    @DisplayName("identifiers are stable across a restart, so a reopened library is the same library")
    void identifiersAreStable() {
        Song song = song("Rainbow Road", "Koji Kondo");
        library.add(song);
        String originalId = song.getId();

        song.setTitle("Rainbow Road (Remastered)");
        library.update(song);

        Library reopened = reopen();
        assertTrue(reopened.findById(originalId).isPresent());
        assertEquals("Rainbow Road (Remastered)",
                reopened.findById(originalId).orElseThrow().getTitle());
    }

    @Test
    @DisplayName("search and filters work the same on a reopened library")
    void queriesWorkAfterReload() {
        Song rainbow = song("Rainbow Road", "Koji Kondo");
        rainbow.setGenre(Genre.SOUNDTRACK);
        rainbow.setAlbum("Mario Kart 64");
        Song rock = song("Bohemian Rhapsody", "Queen");
        rock.setGenre(Genre.ROCK);
        library.add(rainbow);
        library.add(rock);

        Library reopened = reopen();

        assertEquals(1, reopened.search("koji").size());
        assertEquals(1, reopened.filter(null, Genre.ROCK, null).size());
        assertEquals(List.of("Mario Kart 64"), reopened.distinctAlbums());
    }

    @Test
    @DisplayName("two songs sharing a title both survive a restart")
    void duplicateTitlesPersist() {
        library.add(song("Bohemian Rhapsody", "Queen"));
        library.add(song("Bohemian Rhapsody", "A Tribute Band"));

        Library reopened = reopen();

        assertEquals(2, reopened.size(), "distinct identifiers must keep both songs alive on disk");
        assertEquals(2, reopened.search("Bohemian").size());
    }

    @Test
    @DisplayName("clearing the library empties the stored file too")
    void clearPersists() {
        library.add(song("One", "Artist"));
        library.add(song("Two", "Artist"));

        library.clear();

        assertEquals(0, reopen().size(), "a cleared library must not come back on restart");
    }

    @Test
    @DisplayName("replacing the whole library persists the replacement")
    void replaceAllPersists() {
        library.add(song("Old", "Artist"));

        library.replaceAll(List.of(song("New One", "Artist"), song("New Two", "Artist")));

        assertEquals(List.of("New One", "New Two"),
                reopen().all().stream().map(Song::getTitle).toList());
    }

    @Test
    @DisplayName("the initial load does not trigger a write")
    void loadingDoesNotSave() {
        assertEquals(0, saveCount);
        assertFalse(java.nio.file.Files.exists(dir.resolve("library.json")),
                "opening an application with no stored library must not create one");
    }
}
