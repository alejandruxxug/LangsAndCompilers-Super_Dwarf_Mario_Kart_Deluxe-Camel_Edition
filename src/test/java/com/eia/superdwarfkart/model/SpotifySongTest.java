package com.eia.superdwarfkart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A song that streams instead of sitting on the disk.
 *
 * <p>The point of these is that a streamed song is an ordinary song everywhere it matters - the
 * three structures navigate it, the library holds it, it is rated and counted - and differs in
 * exactly one place: where its audio comes from.
 */
class SpotifySongTest {

    private static final String URI = "spotify:track:4uLU6hMCjMI75M1A2tKUQC";

    @Test
    @DisplayName("a streamed song has a URI instead of a file, and says so")
    void streamedSongCarriesAUri() {
        Song song = Song.spotify(URI, "Never Gonna Give You Up", "Rick Astley");

        assertEquals(URI, song.getSpotifyUri());
        assertNull(song.getFilePath(), "a streamed song has no file");
        assertEquals(SongSource.SPOTIFY, song.getSource());
        assertTrue(song.isSpotify());
    }

    @Test
    @DisplayName("locator answers for both kinds, which is what keeps PlaybackEngine free of a branch")
    void locatorAnswersForBothKinds() {
        Song streamed = Song.spotify(URI, "Title", "Artist");
        Song local = new Song("Title", "Artist", Path.of("/music/song.mp3"));

        assertEquals(URI, streamed.locator());
        assertEquals(Path.of("/music/song.mp3").toString(), local.locator());
        assertFalse(local.isSpotify());
        assertEquals(SongSource.LOCAL, local.getSource());
    }

    /**
     * The narrow check that stops Spotify taking the running order away from the graded structures.
     *
     * <p>An album or playlist URI handed to the daemon starts a whole context playing, and nothing
     * anywhere reports it: the music simply carries on while the ring, the queue and the tree stop
     * deciding what comes next. Refusing it here is the cheapest place to catch it.
     */
    @Test
    @DisplayName("only a track URI is accepted - a playlist or album URI is refused")
    void onlyTrackUrisAreAccepted() {
        assertThrows(IllegalArgumentException.class,
                () -> Song.spotify("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", "T", "A"));
        assertThrows(IllegalArgumentException.class,
                () -> Song.spotify("spotify:album:1DFixLWuPkv3KT3TnV35m3", "T", "A"));
        assertThrows(IllegalArgumentException.class,
                () -> Song.spotify("https://open.spotify.com/track/abc", "T", "A"));
        assertThrows(IllegalArgumentException.class, () -> Song.spotify("  ", "T", "A"));
    }

    @Test
    @DisplayName("setting a file path clears the URI, and setting a URI clears the path")
    void theTwoOriginsAreExclusive() {
        Song song = Song.spotify(URI, "Title", "Artist");
        song.setFilePath(Path.of("/music/song.mp3"));

        assertNull(song.getSpotifyUri());
        assertEquals(SongSource.LOCAL, song.getSource());

        song.setSpotifyUri(URI);
        assertNull(song.getFilePath());
        assertEquals(SongSource.SPOTIFY, song.getSource());
    }

    @Test
    @DisplayName("everything else about a streamed song is an ordinary song")
    void streamedSongsAreOrdinaryEverywhereElse() {
        Song song = Song.spotify(URI, "Title", "Artist");
        song.setRating(80);
        song.setFavorite(true);
        song.incrementPlayCount();

        assertEquals(80, song.getRating());
        assertTrue(song.isFavorite());
        assertEquals(1, song.getPlayCount());
        assertThrows(IllegalArgumentException.class, () -> song.setRating(101));
    }

    @Test
    @DisplayName("a remote cover counts as a cover, so the frame is not drawn empty")
    void aRemoteCoverCountsAsACover() {
        Song song = Song.spotify(URI, "Title", "Artist");
        assertFalse(song.hasCover());

        song.setCoverUrl("https://i.scdn.co/image/abc");
        assertTrue(song.hasCover());
        assertEquals("https://i.scdn.co/image/abc", song.getCoverUrl());

        song.setCoverUrl("   ");
        assertNull(song.getCoverUrl(), "a blank URL is no cover at all");
    }

    @Test
    @DisplayName("an unknown stored source name falls back rather than throwing")
    void unknownSourceNameFallsBack() {
        // A profile written by a later build must not stop this one opening - the same decision
        // Racer.byName and SpeedClass.byName already make.
        assertEquals(SongSource.LOCAL, SongSource.byName("TIDAL"));
        assertEquals(SongSource.LOCAL, SongSource.byName(null));
        assertEquals(SongSource.SPOTIFY, SongSource.byName("spotify"));
    }
}
