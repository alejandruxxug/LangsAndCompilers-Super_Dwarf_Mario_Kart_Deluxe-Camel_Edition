package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.model.Song;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * One track as the Spotify Web API describes it.
 *
 * <p>A transport record, not a domain object: it exists to carry a search result from the daemon's
 * proxy into the interface, and to be turned into a {@link Song} at the moment the user adds one.
 * Nothing downstream of {@link #toSong()} keeps a reference to it.
 *
 * @param uri        the track URI, {@code spotify:track:...}
 * @param title      the track name
 * @param artist     the artists, already joined for display
 * @param artistId   the <em>first</em> credited artist's id, or {@code null} when the track names
 *                   none. Carried because <strong>a track object has no genre in it</strong>: Spotify
 *                   files genres against the artist, so turning a search result into a
 *                   {@link com.eia.superdwarfkart.model.Genre} needs a second call to
 *                   {@code v1/artists/{id}} and this is the only thing that identifies which one. The
 *                   first artist rather than all of them, because a feature credit describes the
 *                   guest and the track is the host's.
 * @param album      the album name, or an empty string
 * @param duration   the playing time
 * @param coverUrl   the album art address, or {@code null}
 * @param releaseYear the album's release year, or {@link Song#UNKNOWN_YEAR}
 */
public record SpotifyTrack(String uri,
                           String title,
                           String artist,
                           String artistId,
                           String album,
                           Duration duration,
                           String coverUrl,
                           int releaseYear) {

    /** Separator between artist names, matching how Spotify's own interface joins them. */
    private static final String ARTIST_SEPARATOR = ", ";

    /**
     * Reads a list of track objects, skipping any that are not playable.
     *
     * @param items the array node holding track objects; a missing or non-array node reads as empty
     * @return the tracks, in the order given
     */
    public static List<SpotifyTrack> listFrom(JsonNode items) {
        List<SpotifyTrack> tracks = new ArrayList<>();
        if (items == null) {
            return tracks;
        }
        for (JsonNode item : items) {
            SpotifyTrack track = fromJson(item);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    /**
     * Reads one track object as the Web API describes it.
     *
     * <p><strong>This is the one parser</strong>, because a track object is shaped identically
     * whichever way it was fetched - the daemon's proxy and a direct call with this application's
     * own client credentials return exactly the same document, and a second copy of this method
     * would be free to drift from the first in ways nothing would report.
     *
     * <p>Returns {@code null} rather than a partial record for anything unplayable: a local file
     * somebody added to a playlist has no URI, and a removed track comes back as a null object.
     * Both are ordinary and neither should reach the library.
     *
     * @param node the track object
     * @return the track, or {@code null} when it is not a usable Spotify track
     */
    public static SpotifyTrack fromJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String uri = node.path("uri").asText("");
        String title = node.path("name").asText("");
        if (!uri.startsWith(Song.SPOTIFY_TRACK_PREFIX) || title.isBlank()) {
            return null;
        }

        List<String> artists = new ArrayList<>();
        String firstArtistId = null;
        for (JsonNode artist : node.path("artists")) {
            String name = artist.path("name").asText("");
            if (!name.isBlank()) {
                artists.add(name);
            }
            // The first one with an id, which is not always the first one in the array: a local
            // track or a removed artist appears as a name with nothing to look up.
            String id = artist.path("id").asText("");
            if (firstArtistId == null && !id.isBlank()) {
                firstArtistId = id;
            }
        }

        JsonNode album = node.path("album");
        String coverUrl = pickCover(album.path("images"));

        return new SpotifyTrack(
                uri,
                title,
                joinArtists(artists),
                firstArtistId,
                album.path("name").asText(""),
                Duration.ofMillis(Math.max(0, node.path("duration_ms").asLong(0))),
                coverUrl,
                parseYear(album.path("release_date").asText("")));
    }

    /**
     * The width the cover is chosen against.
     *
     * <p>The library's details panel draws the cover across the whole details column - about 250
     * pixels - and the companion card draws it at 124. Spotify offers each album at roughly 640,
     * 300 and 64, so the smallest is right for the card and <strong>four times too small for the
     * panel</strong>, where it arrives as a visibly blurred square. Taking the largest instead would
     * pull 640px artwork per row of an imported playlist to draw it at a quarter of that.
     */
    private static final int PREFERRED_COVER_WIDTH = 300;

    /**
     * Chooses which of the album's images to keep.
     *
     * <p>The smallest that still covers {@link #PREFERRED_COVER_WIDTH}, falling back to the largest
     * available when every one of them is smaller. Chosen by the declared width rather than by
     * position in the array: they happen to arrive widest-first, but that is not promised anywhere
     * and an ordering assumption fails silently by picking artwork of the wrong size.
     *
     * @param images the album's image array, possibly missing or empty
     * @return the address to store, or {@code null} when the album has no artwork
     */
    private static String pickCover(JsonNode images) {
        if (images == null || !images.isArray() || images.isEmpty()) {
            return null;
        }
        JsonNode smallestBigEnough = null;
        JsonNode largest = null;
        int bigEnoughWidth = Integer.MAX_VALUE;
        int largestWidth = -1;

        for (JsonNode image : images) {
            int width = image.path("width").asInt(0);
            if (width >= PREFERRED_COVER_WIDTH && width < bigEnoughWidth) {
                smallestBigEnough = image;
                bigEnoughWidth = width;
            }
            if (width > largestWidth) {
                largest = image;
                largestWidth = width;
            }
        }

        JsonNode chosen = smallestBigEnough != null ? smallestBigEnough : largest;
        return chosen == null ? null : chosen.path("url").asText(null);
    }

    /**
     * @param releaseDate Spotify's release date, which may be {@code YYYY}, {@code YYYY-MM} or
     *                    {@code YYYY-MM-DD} depending on how precisely the album is dated
     * @return the year, or zero when it cannot be read
     */
    private static int parseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Joins a list of artist names the way the interface shows them.
     *
     * @param names the artist names, possibly empty
     * @return the joined names, or {@code "Unknown Artist"} when there are none - {@link Song}
     *         requires a non-blank artist and a track with no credited artist is a real thing
     */
    public static String joinArtists(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "Unknown Artist";
        }
        String joined = String.join(ARTIST_SEPARATOR, names).trim();
        return joined.isEmpty() ? "Unknown Artist" : joined;
    }

    /**
     * Turns this result into a library song.
     *
     * <p>The song carries the URI rather than a file, and the cover as a URL rather than a
     * downloaded image - see {@link Song#getCoverUrl()} for why importing a playlist must not cost
     * one network request per row.
     *
     * @return a new song, with a fresh identifier
     */
    public Song toSong() {
        Song song = Song.spotify(uri, title, artist);
        song.setAlbum(album);
        song.setDuration(duration);
        song.setCoverUrl(coverUrl);
        if (releaseYear != Song.UNKNOWN_YEAR) {
            song.setYear(releaseYear);
        }
        return song;
    }

    /** @return the bare track identifier, without the {@code spotify:track:} prefix */
    public String trackId() {
        return uri.startsWith(Song.SPOTIFY_TRACK_PREFIX)
                ? uri.substring(Song.SPOTIFY_TRACK_PREFIX.length())
                : uri;
    }

    @Override
    public String toString() {
        return title + " - " + artist;
    }
}
