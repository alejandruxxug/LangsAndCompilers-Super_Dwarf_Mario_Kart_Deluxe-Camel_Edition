package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Song;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Song fixtures shared by the playback tests.
 */
final class Songs {

    private Songs() {
    }

    /**
     * @param title  the song's title
     * @param artist the song's artist
     * @return a song with a plausible file path
     */
    static Song of(String title, String artist) {
        return new Song(title, artist, Path.of("/music/" + title.replace(' ', '-') + ".mp3"));
    }

    /**
     * @param titles the titles to create, in order
     * @return one song per title, all by the same artist
     */
    static List<Song> list(String... titles) {
        List<Song> songs = new ArrayList<>(titles.length);
        for (String title : titles) {
            songs.add(of(title, "Koji Kondo"));
        }
        return songs;
    }

    /**
     * @param songs the songs to name
     * @return their titles, in the order given
     */
    static List<String> titles(List<Song> songs) {
        return songs.stream().map(Song::getTitle).toList();
    }
}
