package com.eia.superdwarfkart.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Library")
class LibraryTest {

    private Library library;
    private Song rainbow;
    private Song moo;
    private Song bowser;

    private static Song song(String title, String artist, String album, Genre genre) {
        Song song = new Song(title, artist, Path.of("/music/" + title.replace(' ', '-') + ".mp3"));
        song.setAlbum(album);
        song.setGenre(genre);
        return song;
    }

    @BeforeEach
    void setUp() {
        library = new Library();
        rainbow = song("Rainbow Road", "Koji Kondo", "Mario Kart 64", Genre.SOUNDTRACK);
        moo = song("Moo Moo Farm", "Koji Kondo", "Mario Kart 64", Genre.SOUNDTRACK);
        bowser = song("Bowser Castle", "Nintendo Sound Team", "Super Circuit", Genre.CHIPTUNE);
        library.add(rainbow);
        library.add(moo);
        library.add(bowser);
    }

    @Test
    @DisplayName("adds songs and reports its size")
    void addAndSize() {
        assertEquals(3, library.size());
        assertFalse(library.isEmpty());
        assertEquals(List.of(rainbow, moo, bowser), library.all());
    }

    @Test
    @DisplayName("refuses to add the same song twice")
    void refusesDuplicateIdentifier() {
        assertFalse(library.add(rainbow));
        assertEquals(3, library.size());
    }

    @Test
    @DisplayName("accepts two different songs that share a title")
    void acceptsDuplicateTitles() {
        Song cover = song("Rainbow Road", "Some Cover Band", "Tribute", Genre.ROCK);

        assertTrue(library.add(cover));
        assertEquals(4, library.size());
    }

    @Test
    @DisplayName("removes by reference and by identifier")
    void remove() {
        assertTrue(library.remove(moo));
        assertEquals(2, library.size());

        assertTrue(library.removeById(bowser.getId()));
        assertEquals(1, library.size());

        assertFalse(library.remove(moo), "removing twice does nothing the second time");
        assertFalse(library.removeById("no-such-id"));
    }

    @Test
    @DisplayName("finds songs by identifier")
    void findById() {
        assertEquals(rainbow, library.findById(rainbow.getId()).orElseThrow());
        assertTrue(library.findById("no-such-id").isEmpty());
        assertTrue(library.findById(null).isEmpty());
    }

    @Test
    @DisplayName("detects a file that has already been imported")
    void containsFile() {
        assertTrue(library.containsFile(Path.of("/music/Rainbow-Road.mp3")));
        assertFalse(library.containsFile(Path.of("/music/Never-Imported.mp3")));
        assertFalse(library.containsFile(null));
    }

    @Test
    @DisplayName("the returned list cannot be used to modify the library")
    void allIsUnmodifiable() {
        List<Song> all = library.all();

        assertThrows(UnsupportedOperationException.class,
                () -> all.add(song("Sneaky", "Nobody", "", Genre.OTHER)));
        assertEquals(3, library.size());
    }

    @Test
    @DisplayName("search matches title, artist and album, ignoring case")
    void search() {
        assertEquals(List.of(rainbow), library.search("rainbow"));
        assertEquals(List.of(rainbow, moo), library.search("KOJI"));
        assertEquals(List.of(rainbow, moo), library.search("mario kart"));
        assertEquals(List.of(), library.search("nothing matches this"));
    }

    @Test
    @DisplayName("a blank search returns everything")
    void blankSearchReturnsEverything() {
        assertEquals(3, library.search("").size());
        assertEquals(3, library.search("   ").size());
        assertEquals(3, library.search(null).size());
    }

    @Test
    @DisplayName("filters by artist, genre and album, and combines them")
    void filter() {
        assertEquals(List.of(rainbow, moo), library.filter("Koji Kondo", null, null));
        assertEquals(List.of(bowser), library.filter(null, Genre.CHIPTUNE, null));
        assertEquals(List.of(rainbow, moo), library.filter(null, null, "Mario Kart 64"));

        assertEquals(List.of(rainbow, moo), library.filter("koji kondo", Genre.SOUNDTRACK, "mario kart 64"));
        assertEquals(List.of(), library.filter("Koji Kondo", Genre.CHIPTUNE, null),
                "combined filters must all apply");
    }

    @Test
    @DisplayName("a filter with nothing set returns everything")
    void emptyFilterReturnsEverything() {
        assertEquals(3, library.filter(null, null, null).size());
        assertEquals(3, library.filter("", null, "").size());
    }

    @Test
    @DisplayName("lists distinct artists, albums and genres in use for the filter controls")
    void distinctValues() {
        assertEquals(List.of("Koji Kondo", "Nintendo Sound Team"), library.distinctArtists());
        assertEquals(List.of("Mario Kart 64", "Super Circuit"), library.distinctAlbums());
        assertEquals(List.of(Genre.SOUNDTRACK, Genre.CHIPTUNE), library.genresInUse());
    }

    @Test
    @DisplayName("an empty album is not offered as a filter choice")
    void blankAlbumsAreNotListed() {
        library.add(song("No Album", "Someone", "", Genre.POP));

        assertFalse(library.distinctAlbums().contains(""));
    }

    @Test
    @DisplayName("collects favourites")
    void favorites() {
        assertEquals(List.of(), library.favorites());

        rainbow.setFavorite(true);
        bowser.setFavorite(true);

        assertEquals(List.of(rainbow, bowser), library.favorites());
    }

    @Test
    @DisplayName("reports play statistics")
    void statistics() {
        rainbow.setPlayCount(9);
        moo.setPlayCount(4);
        bowser.setPlayCount(1);

        assertEquals(14, library.totalPlayCount());
        assertEquals(List.of(rainbow, moo), library.mostPlayed(2));
        assertEquals(3, library.mostPlayed(50).size(), "asking for more than exist is not an error");
    }

    @Test
    @DisplayName("replaceAll swaps the whole contents")
    void replaceAll() {
        Song fresh = song("Fresh", "New Artist", "New Album", Genre.POP);

        library.replaceAll(List.of(fresh));

        assertEquals(1, library.size());
        assertEquals(List.of(fresh), library.all());
    }

    @Test
    @DisplayName("construction from stored songs drops duplicate identifiers")
    void constructionDeduplicates() {
        Library rebuilt = new Library(List.of(rainbow, moo, rainbow));

        assertEquals(2, rebuilt.size());
    }

    @Test
    @DisplayName("listeners are told what changed")
    void listenersAreNotified() {
        List<String> events = new ArrayList<>();
        library.addListener((change, song) ->
                events.add(change + ":" + (song == null ? "-" : song.getTitle())));

        Song added = song("Added", "Artist", "", Genre.POP);
        library.add(added);
        library.update(added);
        library.remove(added);
        library.clear();

        assertEquals(List.of(
                "ADDED:Added",
                "UPDATED:Added",
                "REMOVED:Added",
                "RELOADED:-"), events);
    }

    @Test
    @DisplayName("updating a song that is not in the library changes nothing")
    void updateOfForeignSongIsIgnored() {
        List<String> events = new ArrayList<>();
        library.addListener((change, song) -> events.add(change.name()));

        assertFalse(library.update(song("Foreign", "Artist", "", Genre.POP)));
        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("a removed listener stops hearing about changes")
    void listenerCanBeRemoved() {
        List<String> events = new ArrayList<>();
        LibraryListener listener = (change, song) -> events.add(change.name());

        library.addListener(listener);
        library.removeListener(listener);
        library.add(song("Quiet", "Artist", "", Genre.POP));

        assertEquals(List.of(), events);
    }
}
