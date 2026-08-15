package com.eia.superdwarfkart.model;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * A song in the library.
 *
 * <p>Fully encapsulated: every field is private and every setter validates before assigning,
 * so an invalid {@code Song} cannot exist. The identifier is a UUID generated once and kept
 * stable across edits, which is what lets scores, beatmaps and playback cursors keep pointing
 * at the same song after its title or artist is corrected.
 *
 * <p>This class is part of the domain layer and must never import {@code javafx.*}; the
 * duration below is a {@link java.time.Duration}, not the JavaFX one.
 */
public class Song {

    /** Lowest rating a song may carry. */
    public static final int MIN_RATING = 0;

    /** Highest rating a song may carry. */
    public static final int MAX_RATING = 100;

    /** Earliest accepted release year: roughly the first sound recordings. */
    public static final int MIN_YEAR = 1877;

    /** A year of zero means "not known", and is always accepted. */
    public static final int UNKNOWN_YEAR = 0;

    /**
     * The only Spotify URI shape a song may hold.
     *
     * <p>Deliberately narrow: an album or playlist URI handed to the daemon starts a whole context
     * playing, which would leave Spotify choosing the running order instead of the active
     * {@code PlaybackMode} - the exact silent failure the daemon is configured against.
     */
    public static final String SPOTIFY_TRACK_PREFIX = "spotify:track:";

    /**
     * Playback ordering used by the alphabetical mode: title, then artist, then identifier,
     * all case-insensitive for the text parts.
     *
     * <p>The tiebreakers are not decoration. Ordering on title alone makes two different songs
     * that share a title compare equal, and a binary search tree would then silently drop one
     * of them.
     */
    public static final Comparator<Song> BY_TITLE = Comparator
            .comparing(Song::getTitle, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Song::getArtist, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Song::getId);

    private String id;
    private String title;
    private String artist;
    private String album = "";
    private Duration duration = Duration.ZERO;
    private Genre genre = Genre.UNKNOWN;
    private int year = UNKNOWN_YEAR;
    private int rating = MIN_RATING;
    private Path filePath;
    private String spotifyUri;
    private Path coverPath;
    private String coverUrl;
    private boolean favorite;
    private int playCount;

    /**
     * Creates a song with the minimum information the library requires.
     *
     * @param title    song title; must not be blank
     * @param artist   performing artist; must not be blank
     * @param filePath the audio file on disk; must not be {@code null}
     * @throws IllegalArgumentException if the title or artist is blank
     * @throws NullPointerException     if the file path is {@code null}
     */
    public Song(String title, String artist, Path filePath) {
        this.id = UUID.randomUUID().toString();
        setTitle(title);
        setArtist(artist);
        setFilePath(filePath);
    }

    /**
     * Creates a song with an explicit identifier, used when reloading a stored library so that
     * identifiers survive a restart.
     *
     * @param id       stable identifier; must not be blank
     * @param title    song title; must not be blank
     * @param artist   performing artist; must not be blank
     * @param filePath the audio file on disk; must not be {@code null}
     */
    public Song(String id, String title, String artist, Path filePath) {
        this(title, artist, filePath);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Song id must not be blank");
        }
        this.id = id;
    }

    /**
     * Private constructor for a streamed song, which has a Spotify URI where a local song has a
     * file. Reached through {@link #spotify(String, String, String)}.
     */
    private Song(String spotifyUri, String title, String artist) {
        this.id = UUID.randomUUID().toString();
        setTitle(title);
        setArtist(artist);
        setSpotifyUri(spotifyUri);
    }

    /**
     * Creates a song streamed from Spotify.
     *
     * <p>A streamed song carries no {@link #getFilePath() file}: the audio arrives through the
     * go-librespot daemon rather than off the disk, and {@link #locator()} answers with the URI
     * instead. Everything else about it is an ordinary song - it is rated, favourited, counted,
     * placed in the running order and navigated by all three structures exactly like any other.
     *
     * @param spotifyUri the track URI, {@code spotify:track:...}; must not be blank
     * @param title      song title; must not be blank
     * @param artist     performing artist; must not be blank
     * @return the new song
     * @throws IllegalArgumentException if any argument is blank, or the URI is not a track URI
     */
    public static Song spotify(String spotifyUri, String title, String artist) {
        return new Song(spotifyUri, title, artist);
    }

    /**
     * Creates a streamed song with an explicit identifier, used when reloading a stored library.
     *
     * @param id         stable identifier; must not be blank
     * @param spotifyUri the track URI, {@code spotify:track:...}; must not be blank
     * @param title      song title; must not be blank
     * @param artist     performing artist; must not be blank
     * @return the new song
     */
    public static Song spotify(String id, String spotifyUri, String title, String artist) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Song id must not be blank");
        }
        Song song = new Song(spotifyUri, title, artist);
        song.id = id;
        return song;
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /** @return the stable identifier, unchanged for the lifetime of this song */
    public String getId() {
        return id;
    }

    // ------------------------------------------------------------------
    // Required text
    // ------------------------------------------------------------------

    /** @return the song title */
    public String getTitle() {
        return title;
    }

    /**
     * @param title new title; must not be blank
     * @throws IllegalArgumentException if the title is {@code null} or blank
     */
    public final void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Song title is required and must not be blank");
        }
        this.title = title.trim();
    }

    /** @return the performing artist */
    public String getArtist() {
        return artist;
    }

    /**
     * @param artist new artist; must not be blank
     * @throws IllegalArgumentException if the artist is {@code null} or blank
     */
    public final void setArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("Song artist is required and must not be blank");
        }
        this.artist = artist.trim();
    }

    // ------------------------------------------------------------------
    // Optional metadata
    // ------------------------------------------------------------------

    /** @return the album name, or an empty string when unknown */
    public String getAlbum() {
        return album;
    }

    /** @param album album name; {@code null} is stored as an empty string */
    public void setAlbum(String album) {
        this.album = album == null ? "" : album.trim();
    }

    /** @return the playing time, or {@link Duration#ZERO} when it has not been read yet */
    public Duration getDuration() {
        return duration;
    }

    /**
     * @param duration playing time; {@code null} is stored as zero
     * @throws IllegalArgumentException if the duration is negative
     */
    public void setDuration(Duration duration) {
        if (duration == null) {
            this.duration = Duration.ZERO;
            return;
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Song duration must not be negative: " + duration);
        }
        this.duration = duration;
    }

    /** @return the genre, never {@code null} */
    public Genre getGenre() {
        return genre;
    }

    /** @param genre new genre; {@code null} is stored as {@link Genre#UNKNOWN} */
    public void setGenre(Genre genre) {
        this.genre = genre == null ? Genre.UNKNOWN : genre;
    }

    /** @return the release year, or {@link #UNKNOWN_YEAR} when unknown */
    public int getYear() {
        return year;
    }

    /**
     * @param year release year, or {@link #UNKNOWN_YEAR} for unknown
     * @throws IllegalArgumentException if the year is outside {@value #MIN_YEAR}..(current year + 1)
     */
    public void setYear(int year) {
        if (year == UNKNOWN_YEAR) {
            this.year = UNKNOWN_YEAR;
            return;
        }
        int maxYear = Year.now().getValue() + 1;
        if (year < MIN_YEAR || year > maxYear) {
            throw new IllegalArgumentException(
                    "Song year must be 0 (unknown) or between " + MIN_YEAR + " and " + maxYear + ", got " + year);
        }
        this.year = year;
    }

    /** @return the rating, always between {@value #MIN_RATING} and {@value #MAX_RATING} */
    public int getRating() {
        return rating;
    }

    /**
     * @param rating new rating
     * @throws IllegalArgumentException if the rating is outside {@value #MIN_RATING}..{@value #MAX_RATING}
     */
    public void setRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "Song rating must be between " + MIN_RATING + " and " + MAX_RATING + ", got " + rating);
        }
        this.rating = rating;
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    /**
     * Returns the audio file backing this song.
     *
     * <p><strong>{@code null} for a streamed song</strong>, which has a {@link #getSpotifyUri()
     * URI} instead. Ask {@link #isSpotify()} before dereferencing this, or go through
     * {@link #locator()}, which always answers.
     *
     * @return the file on disk, or {@code null} when this song is streamed
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * @param filePath the audio file on disk
     * @throws NullPointerException if the path is {@code null}
     */
    public final void setFilePath(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "Song file path is required");
        this.spotifyUri = null;
    }

    /**
     * @return the Spotify track URI backing this song, or {@code null} when it is a local file
     */
    public String getSpotifyUri() {
        return spotifyUri;
    }

    /**
     * Points this song at a Spotify track, clearing any file path.
     *
     * <p>The prefix is checked rather than assumed. A playlist or album URI here would be handed
     * to the daemon as though it were a track, and go-librespot would happily start playing a
     * whole context - so the running order would advance while the structure sat still, which is
     * the one failure mode the Spotify integration must not have.
     *
     * @param uri the track URI, {@code spotify:track:...}; must not be blank
     * @throws IllegalArgumentException if the URI is blank or is not a track URI
     */
    public final void setSpotifyUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Song Spotify URI is required and must not be blank");
        }
        String trimmed = uri.trim();
        if (!trimmed.startsWith(SPOTIFY_TRACK_PREFIX)) {
            throw new IllegalArgumentException(
                    "Song Spotify URI must be a track URI starting \"" + SPOTIFY_TRACK_PREFIX
                            + "\", got: " + trimmed);
        }
        this.spotifyUri = trimmed;
        this.filePath = null;
    }

    /** @return where this song's audio comes from, never {@code null} */
    public SongSource getSource() {
        return spotifyUri == null ? SongSource.LOCAL : SongSource.SPOTIFY;
    }

    /** @return whether this song is streamed rather than read from a file */
    public boolean isSpotify() {
        return spotifyUri != null;
    }

    /**
     * Returns the string an {@code AudioSource} is opened with.
     *
     * <p>One accessor rather than a branch at every call site: a file path for a local song, a
     * {@code spotify:track:...} URI for a streamed one. {@code PlaybackEngine} passes this straight
     * to {@code AudioSource.load(String)}, which is what lets the engine stay ignorant of which
     * kind of song it is driving.
     *
     * @return the locator, never {@code null}
     */
    public String locator() {
        return spotifyUri != null ? spotifyUri : filePath.toString();
    }

    /** @return the cover image, or {@code null} when the default cover should be used */
    public Path getCoverPath() {
        return coverPath;
    }

    /** @param coverPath cover image path, or {@code null} to fall back to the default cover */
    public void setCoverPath(Path coverPath) {
        this.coverPath = coverPath;
    }

    /**
     * Returns the remote cover image address, which a streamed song has instead of a file.
     *
     * <p>Spotify supplies album art as a URL. It is kept as a plain string rather than downloaded
     * on import, so adding fifty tracks from a playlist costs fifty rows and no network at all; the
     * interface loads it lazily and falls back to the same magenta placeholder a missing local
     * cover gets.
     *
     * @return the cover URL, or {@code null} when there is none
     */
    public String getCoverUrl() {
        return coverUrl;
    }

    /** @param coverUrl remote cover address; blank is stored as {@code null} */
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl == null || coverUrl.isBlank() ? null : coverUrl.trim();
    }

    /** @return whether a cover image, local or remote, has been set for this song */
    public boolean hasCover() {
        return coverPath != null || coverUrl != null;
    }

    // ------------------------------------------------------------------
    // Bonus features
    // ------------------------------------------------------------------

    /** @return whether the user marked this song as a favourite */
    public boolean isFavorite() {
        return favorite;
    }

    /** @param favorite whether this song is a favourite */
    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    /** Flips the favourite flag. */
    public void toggleFavorite() {
        this.favorite = !this.favorite;
    }

    /** @return how many times this song has been played */
    public int getPlayCount() {
        return playCount;
    }

    /**
     * @param playCount new play count
     * @throws IllegalArgumentException if the count is negative
     */
    public void setPlayCount(int playCount) {
        if (playCount < 0) {
            throw new IllegalArgumentException("Song play count must not be negative: " + playCount);
        }
        this.playCount = playCount;
    }

    /** Records one more play of this song. */
    public void incrementPlayCount() {
        playCount++;
    }

    // ------------------------------------------------------------------
    // Identity semantics
    // ------------------------------------------------------------------

    /**
     * Two songs are the same song when they carry the same identifier. Title and artist are
     * deliberately excluded: the library is allowed to hold two different songs with identical
     * titles, and editing a title must not turn a song into a different one.
     *
     * @param o the object to compare with
     * @return whether both are songs with the same identifier
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Song other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return title + " - " + artist;
    }
}
