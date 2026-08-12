package com.eia.superdwarfkart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Song")
class SongTest {

    private static Song validSong() {
        return new Song("Rainbow Road", "Koji Kondo", Path.of("/music/rainbow-road.mp3"));
    }

    @Test
    @DisplayName("a new song gets a unique identifier")
    void uniqueIdentifiers() {
        Song a = validSong();
        Song b = validSong();

        assertNotEquals(a.getId(), b.getId());
        assertFalse(a.getId().isBlank());
    }

    @Test
    @DisplayName("rating accepts the whole 0 to 100 range")
    void ratingAcceptsValidRange() {
        Song song = validSong();

        for (int rating : new int[]{0, 1, 50, 99, 100}) {
            song.setRating(rating);
            assertEquals(rating, song.getRating());
        }
    }

    @Test
    @DisplayName("rating rejects anything outside 0 to 100")
    void ratingRejectsOutOfRange() {
        Song song = validSong();

        assertThrows(IllegalArgumentException.class, () -> song.setRating(-1));
        assertThrows(IllegalArgumentException.class, () -> song.setRating(101));
        assertThrows(IllegalArgumentException.class, () -> song.setRating(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> song.setRating(Integer.MIN_VALUE));

        assertEquals(0, song.getRating(), "a rejected rating must not have been applied");
    }

    @Test
    @DisplayName("title is required and must not be blank")
    void titleIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new Song(null, "Artist", Path.of("/music/a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Song("", "Artist", Path.of("/music/a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Song("   ", "Artist", Path.of("/music/a.mp3")));
    }

    @Test
    @DisplayName("artist is required and must not be blank")
    void artistIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new Song("Title", null, Path.of("/music/a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Song("Title", "  ", Path.of("/music/a.mp3")));
    }

    @Test
    @DisplayName("file path is required")
    void filePathIsRequired() {
        assertThrows(NullPointerException.class, () -> new Song("Title", "Artist", null));
    }

    @Test
    @DisplayName("title and artist are trimmed")
    void textIsTrimmed() {
        Song song = new Song("  Rainbow Road  ", "  Koji Kondo ", Path.of("/music/a.mp3"));

        assertEquals("Rainbow Road", song.getTitle());
        assertEquals("Koji Kondo", song.getArtist());
    }

    @Test
    @DisplayName("year accepts a sane range and zero for unknown")
    void yearRange() {
        Song song = validSong();

        song.setYear(Song.UNKNOWN_YEAR);
        assertEquals(Song.UNKNOWN_YEAR, song.getYear());

        song.setYear(1992);
        assertEquals(1992, song.getYear());

        song.setYear(Year.now().getValue());
        assertEquals(Year.now().getValue(), song.getYear());

        assertThrows(IllegalArgumentException.class, () -> song.setYear(1500));
        assertThrows(IllegalArgumentException.class, () -> song.setYear(Year.now().getValue() + 5));
        assertThrows(IllegalArgumentException.class, () -> song.setYear(-100));
    }

    @Test
    @DisplayName("duration rejects negative values and treats null as zero")
    void durationValidation() {
        Song song = validSong();

        song.setDuration(Duration.ofSeconds(210));
        assertEquals(Duration.ofSeconds(210), song.getDuration());

        song.setDuration(null);
        assertEquals(Duration.ZERO, song.getDuration());

        assertThrows(IllegalArgumentException.class, () -> song.setDuration(Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("optional metadata has safe defaults")
    void defaults() {
        Song song = validSong();

        assertEquals("", song.getAlbum());
        assertEquals(Genre.UNKNOWN, song.getGenre());
        assertEquals(Duration.ZERO, song.getDuration());
        assertEquals(0, song.getRating());
        assertEquals(0, song.getPlayCount());
        assertEquals(Song.UNKNOWN_YEAR, song.getYear());
        assertFalse(song.isFavorite());
        assertFalse(song.hasCover(), "a song without a cover falls back to the default cover");
    }

    @Test
    @DisplayName("null album and genre fall back rather than blowing up")
    void nullOptionalsFallBack() {
        Song song = validSong();

        song.setAlbum(null);
        song.setGenre(null);

        assertEquals("", song.getAlbum());
        assertEquals(Genre.UNKNOWN, song.getGenre());
    }

    @Test
    @DisplayName("favourite toggles and play count increments")
    void bonusFeatures() {
        Song song = validSong();

        song.toggleFavorite();
        assertTrue(song.isFavorite());
        song.toggleFavorite();
        assertFalse(song.isFavorite());

        song.incrementPlayCount();
        song.incrementPlayCount();
        assertEquals(2, song.getPlayCount());

        assertThrows(IllegalArgumentException.class, () -> song.setPlayCount(-1));
    }

    @Test
    @DisplayName("identity survives an edit to the title")
    void identityIsStableAcrossEdits() {
        Song song = validSong();
        String id = song.getId();

        song.setTitle("Rainbow Road (Remastered)");

        assertEquals(id, song.getId(), "scores and beatmaps key off this id; it must not move");
    }

    @Test
    @DisplayName("two songs are equal only when identifiers match")
    void equalityUsesIdentifier() {
        Song a = validSong();
        Song b = validSong();
        Song sameId = new Song(a.getId(), "Different Title", "Different Artist", Path.of("/music/x.mp3"));

        assertEquals(a, a);
        assertNotEquals(a, b, "same title and artist, different songs");
        assertEquals(a, sameId);
        assertEquals(a.hashCode(), sameId.hashCode());
    }
}
