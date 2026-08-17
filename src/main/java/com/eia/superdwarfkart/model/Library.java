package com.eia.superdwarfkart.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The canonical collection of songs.
 *
 * <p><strong>The library owns the songs; the playback modes do not.</strong> Selecting a mode
 * builds that mode's structure - the circular list, the queue or the tree - <em>from</em> this
 * collection. Consuming a mode's structure therefore never destroys anything: draining the
 * arrival-order queue empties only that view, and the mode can be rebuilt from here at any time.
 *
 * <p>This class deliberately stores its songs in a plain {@link ArrayList}. The assignment
 * requires the three <em>playback</em> structures to be written by hand, and they are; the
 * library is the master collection those structures are built from, not one of them. Using a
 * hand-written list here would add nothing to the demonstration and would slow down the ordinary
 * table operations the user interface performs constantly.
 *
 * <p>Part of the domain layer: no {@code javafx.*} import appears here. Views observe through
 * {@link LibraryListener} instead.
 */
public class Library {

    private final List<Song> songs = new ArrayList<>();
    private final List<LibraryListener> listeners = new ArrayList<>();

    /** Creates an empty library. */
    public Library() {
        // nothing to initialise
    }

    /**
     * Creates a library holding the given songs.
     *
     * @param initial songs to start with; must not be {@code null}
     */
    public Library(Iterable<Song> initial) {
        Objects.requireNonNull(initial, "initial must not be null");
        for (Song song : initial) {
            if (song != null && findById(song.getId()).isEmpty()) {
                songs.add(song);
            }
        }
    }

    // ------------------------------------------------------------------
    // Create, update, delete
    // ------------------------------------------------------------------

    /**
     * Adds a song, unless one with the same identifier is already present.
     *
     * @param song the song to add; must not be {@code null}
     * @return {@code true} if it was added
     */
    public boolean add(Song song) {
        Objects.requireNonNull(song, "song must not be null");
        if (findById(song.getId()).isPresent()) {
            return false;
        }
        songs.add(song);
        fire(LibraryListener.LibraryChange.ADDED, song);
        return true;
    }

    /**
     * Removes a song.
     *
     * @param song the song to remove
     * @return {@code true} if it was present and removed
     */
    public boolean remove(Song song) {
        if (song == null) {
            return false;
        }
        boolean removed = songs.remove(song);
        if (removed) {
            fire(LibraryListener.LibraryChange.REMOVED, song);
        }
        return removed;
    }

    /**
     * Removes the song with the given identifier.
     *
     * @param id the identifier to remove
     * @return {@code true} if a song was removed
     */
    public boolean removeById(String id) {
        return findById(id).map(this::remove).orElse(false);
    }

    /**
     * Announces that a song already in the library has been edited.
     *
     * <p>Songs are mutable and the library hands out live references, so an edit is already
     * visible here. This method exists to tell the listeners about it.
     *
     * @param song the edited song
     * @return {@code true} if the song belongs to this library
     */
    public boolean update(Song song) {
        if (song == null || !songs.contains(song)) {
            return false;
        }
        fire(LibraryListener.LibraryChange.UPDATED, song);
        return true;
    }

    /**
     * Replaces the entire contents, for instance after loading from disk.
     *
     * @param replacement the new contents; must not be {@code null}
     */
    public void replaceAll(Iterable<Song> replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        songs.clear();
        for (Song song : replacement) {
            if (song != null && findById(song.getId()).isEmpty()) {
                songs.add(song);
            }
        }
        fire(LibraryListener.LibraryChange.RELOADED, null);
    }

    /** Removes every song. */
    public void clear() {
        songs.clear();
        fire(LibraryListener.LibraryChange.RELOADED, null);
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * Returns every song, in insertion order.
     *
     * @return an unmodifiable view; edit songs through their own setters, not through this list
     */
    public List<Song> all() {
        return List.copyOf(songs);
    }

    /**
     * Finds a song by identifier.
     *
     * @param id the identifier to look for
     * @return the song, if present
     */
    public Optional<Song> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Song song : songs) {
            if (song.getId().equals(id)) {
                return Optional.of(song);
            }
        }
        return Optional.empty();
    }

    /**
     * Reports whether a song already refers to the given audio file, so the same file is not
     * imported twice.
     *
     * @param file the audio file to look for
     * @return {@code true} if some song already uses it
     */
    public boolean containsFile(Path file) {
        if (file == null) {
            return false;
        }
        Path normalized = file.toAbsolutePath().normalize();
        for (Song song : songs) {
            Path stored = song.getFilePath();
            // Streamed songs have no file at all, and skipping them here is what keeps this from
            // throwing the moment the first Spotify track joins the library.
            if (stored != null && stored.toAbsolutePath().normalize().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reports whether a song already refers to the given Spotify track, so the same track is not
     * added twice.
     *
     * @param uri the track URI to look for
     * @return {@code true} if some song already uses it
     */
    public boolean containsSpotifyUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        for (Song song : songs) {
            if (uri.equals(song.getSpotifyUri())) {
                return true;
            }
        }
        return false;
    }

    /** @return how many songs the library holds */
    public int size() {
        return songs.size();
    }

    /** @return whether the library holds no songs */
    public boolean isEmpty() {
        return songs.isEmpty();
    }

    // ------------------------------------------------------------------
    // Search and filters
    // ------------------------------------------------------------------

    /**
     * Returns the songs whose title, artist or album contains the query, ignoring case.
     *
     * @param query the text to look for; blank returns everything
     * @return the matching songs
     */
    public List<Song> search(String query) {
        if (query == null || query.isBlank()) {
            return all();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Song> matches = new ArrayList<>();
        for (Song song : songs) {
            if (contains(song.getTitle(), needle)
                    || contains(song.getArtist(), needle)
                    || contains(song.getAlbum(), needle)) {
                matches.add(song);
            }
        }
        return matches;
    }

    /**
     * Returns the songs matching every filter that is set.
     *
     * @param artist artist to match exactly, ignoring case; {@code null} or blank means any
     * @param genre  genre to match; {@code null} means any
     * @param album  album to match exactly, ignoring case; {@code null} or blank means any
     * @return the matching songs
     */
    public List<Song> filter(String artist, Genre genre, String album) {
        List<Song> matches = new ArrayList<>();
        for (Song song : songs) {
            if (artist != null && !artist.isBlank() && !song.getArtist().equalsIgnoreCase(artist)) {
                continue;
            }
            if (genre != null && song.getGenre() != genre) {
                continue;
            }
            if (album != null && !album.isBlank() && !song.getAlbum().equalsIgnoreCase(album)) {
                continue;
            }
            matches.add(song);
        }
        return matches;
    }

    /** @return the songs marked as favourites */
    public List<Song> favorites() {
        List<Song> matches = new ArrayList<>();
        for (Song song : songs) {
            if (song.isFavorite()) {
                matches.add(song);
            }
        }
        return matches;
    }

    /** @return every distinct artist, sorted, for the filter controls */
    public List<String> distinctArtists() {
        return distinct(Song::getArtist);
    }

    /** @return every distinct non-empty album, sorted, for the filter controls */
    public List<String> distinctAlbums() {
        return distinct(Song::getAlbum);
    }

    /**
     * What this library already says an artist's genre is.
     *
     * <p><strong>This exists because Spotify stopped answering the question.</strong> Its Web API used
     * to file genres against the artist, which is what the add dialog looked them up from; measured
     * against the live service on 2026-08-17, {@code v1/artists/{id}} on an application token now
     * returns HTTP 200 with <em>no {@code genres} field at all</em> - the key is absent, not empty -
     * for The Beatles, Ariana Grande and Don Diablo alike, and the album object carries none either.
     * So the best remaining answer is the one already in front of the user: if they have filed three
     * songs by this artist as Electronic, the fourth is almost certainly Electronic too.
     *
     * <p>Matched case-insensitively on the whole credit, so {@code "Crystal Castles"} and
     * {@code "Crystal Castles, HEALTH"} are deliberately <em>different</em> artists here - a feature
     * credit is a different act and guessing across them would be worse than not guessing.
     *
     * <p>The most common answer wins rather than the first, because one mis-filed song should not
     * decide it; {@link Genre#UNKNOWN} songs do not vote, since "nobody has said" is not an opinion.
     *
     * @param artist the artist credit to look up; {@code null} or blank gives {@link Genre#UNKNOWN}
     * @return the genre this artist's songs are mostly filed under, or {@link Genre#UNKNOWN} when the
     *         library has nothing to say
     */
    public Genre genreForArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            return Genre.UNKNOWN;
        }
        java.util.Map<Genre, Integer> votes = new java.util.EnumMap<>(Genre.class);
        for (Song song : songs) {
            if (song.getGenre() == Genre.UNKNOWN || !artist.equalsIgnoreCase(song.getArtist())) {
                continue;
            }
            votes.merge(song.getGenre(), 1, Integer::sum);
        }
        Genre best = Genre.UNKNOWN;
        int bestVotes = 0;
        for (var vote : votes.entrySet()) {
            if (vote.getValue() > bestVotes) {
                best = vote.getKey();
                bestVotes = vote.getValue();
            }
        }
        return best;
    }

    /** @return every genre actually in use, in enum order, for the filter controls */
    public List<Genre> genresInUse() {
        List<Genre> found = new ArrayList<>();
        for (Genre genre : Genre.values()) {
            for (Song song : songs) {
                if (song.getGenre() == genre) {
                    found.add(genre);
                    break;
                }
            }
        }
        return found;
    }

    private List<String> distinct(java.util.function.Function<Song, String> field) {
        List<String> values = new ArrayList<>();
        for (Song song : songs) {
            String value = field.apply(song);
            if (value != null && !value.isBlank() && !containsIgnoreCase(values, value)) {
                values.add(value);
            }
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return values;
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String haystack, String lowercaseNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }

    // ------------------------------------------------------------------
    // Statistics, used by the sweep milestone and the library header
    // ------------------------------------------------------------------

    /** @return the total number of plays recorded across the library */
    public int totalPlayCount() {
        int total = 0;
        for (Song song : songs) {
            total += song.getPlayCount();
        }
        return total;
    }

    /**
     * Returns the most played songs, highest first.
     *
     * @param limit how many to return at most
     * @return the most played songs
     */
    public List<Song> mostPlayed(int limit) {
        List<Song> sorted = new ArrayList<>(songs);
        sorted.sort(Comparator.comparingInt(Song::getPlayCount).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    /**
     * Registers a listener, which is called after every change.
     *
     * @param listener the listener to add; must not be {@code null}
     */
    public void addListener(LibraryListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * Unregisters a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(LibraryListener listener) {
        listeners.remove(listener);
    }

    private void fire(LibraryListener.LibraryChange change, Song song) {
        // Iterate a copy: a listener is allowed to unregister itself while being notified.
        for (LibraryListener listener : List.copyOf(listeners)) {
            listener.libraryChanged(change, song);
        }
    }

    @Override
    public String toString() {
        return "Library(" + songs.size() + " songs)";
    }
}
