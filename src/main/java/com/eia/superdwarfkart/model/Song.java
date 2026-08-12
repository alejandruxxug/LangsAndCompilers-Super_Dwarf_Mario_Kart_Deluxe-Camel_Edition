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
    private Path coverPath;
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

    /** @return the audio file backing this song */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * @param filePath the audio file on disk
     * @throws NullPointerException if the path is {@code null}
     */
    public final void setFilePath(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "Song file path is required");
    }

    /** @return the cover image, or {@code null} when the default cover should be used */
    public Path getCoverPath() {
        return coverPath;
    }

    /** @param coverPath cover image path, or {@code null} to fall back to the default cover */
    public void setCoverPath(Path coverPath) {
        this.coverPath = coverPath;
    }

    /** @return whether a cover image has been set for this song */
    public boolean hasCover() {
        return coverPath != null;
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
