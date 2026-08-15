package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.model.Genre;
import com.eia.superdwarfkart.model.Song;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Stores the song library as JSON, by default at {@code ~/.superdwarfkart/library.json}.
 *
 * <p>{@link Song} carries no persistence annotations. It is mapped to and from the flat
 * {@link SongData} record below instead, so the domain model stays unaware that it is being
 * stored and the on-disk format can change without touching it. That mapping is also where
 * {@link Path} and {@link Duration} become plain strings and numbers, which keeps the file
 * readable and hand-editable.
 */
public class LibraryRepository implements Repository<Song> {

    private static final Logger LOG = Logger.getLogger(LibraryRepository.class.getName());

    /**
     * Bumped when the on-disk shape changes in a way older readers could not handle.
     *
     * <p>Version 2 added {@code spotifyUri}, {@code source} and {@code coverUrl}. A version 1 file
     * reads unchanged - every song in one has a file path, which is still exactly what makes a song
     * local - so nothing migrates and nothing is lost.
     */
    private static final int FORMAT_VERSION = 2;

    private final Path file;
    private final ObjectMapper mapper;

    /** Creates a repository storing at the default location under the user's home directory. */
    public LibraryRepository() {
        this(AppConfig.libraryFile());
    }

    /**
     * Creates a repository storing at an explicit location.
     *
     * @param file where to store the library; must not be {@code null}
     */
    public LibraryRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Path storageLocation() {
        return file;
    }

    /**
     * Loads the stored library.
     *
     * <p>A missing file is not an error: a first run simply has no library yet. An entry that
     * fails validation is skipped with a warning rather than aborting the whole load, so one
     * bad record cannot lock the user out of the rest of their library.
     *
     * @return the stored songs, or an empty list when nothing has been stored
     * @throws PersistenceException if the file exists but cannot be read or parsed
     */
    @Override
    public List<Song> loadAll() {
        if (!Files.exists(file)) {
            LOG.info("No library file at " + file + " - starting with an empty library");
            return List.of();
        }

        LibraryData data;
        try {
            data = mapper.readValue(file.toFile(), LibraryData.class);
        } catch (JacksonException e) {
            throw new PersistenceException("The library file at " + file
                    + " is not valid JSON and could not be read: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new PersistenceException("Could not read the library file at " + file, e);
        }

        if (data == null || data.songs == null) {
            return List.of();
        }

        List<Song> songs = new ArrayList<>(data.songs.size());
        int skipped = 0;
        for (SongData record : data.songs) {
            try {
                songs.add(toSong(record));
            } catch (RuntimeException e) {
                skipped++;
                LOG.warning("Skipping an unreadable song entry in " + file + ": " + e.getMessage());
            }
        }
        if (skipped > 0) {
            LOG.warning("Loaded " + songs.size() + " songs and skipped " + skipped + " invalid entries");
        }
        return songs;
    }

    /**
     * Writes the library.
     *
     * <p>The write goes to a temporary file in the same directory and is then moved into place,
     * so an interruption partway through leaves the previous library intact rather than a
     * half-written file.
     *
     * @param items the songs to store; must not be {@code null}
     * @throws PersistenceException if the write fails
     */
    @Override
    public void saveAll(Collection<? extends Song> items) {
        Objects.requireNonNull(items, "items must not be null");

        LibraryData data = new LibraryData();
        data.version = FORMAT_VERSION;
        data.songs = new ArrayList<>(items.size());
        for (Song song : items) {
            data.songs.add(toRecord(song));
        }

        Path directory = file.toAbsolutePath().getParent();
        Path temporary = null;
        try {
            if (directory != null) {
                Files.createDirectories(directory);
            }
            temporary = Files.createTempFile(directory, "library", ".json.tmp");
            mapper.writeValue(temporary.toFile(), data);
            moveIntoPlace(temporary, file);
            temporary = null;
        } catch (IOException e) {
            throw new PersistenceException("Could not write the library file at " + file, e);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /**
     * Moves the freshly written file over the real one, preferring an atomic move.
     *
     * @param source      the temporary file
     * @param destination the real library file
     * @throws IOException if neither move succeeds
     */
    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Some filesystems cannot move atomically; a plain replace is still better than
            // writing over the original in place.
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warning("Could not delete the temporary file " + path + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Mapping between the domain model and the stored shape
    // ------------------------------------------------------------------

    private static SongData toRecord(Song song) {
        SongData record = new SongData();
        record.id = song.getId();
        record.title = song.getTitle();
        record.artist = song.getArtist();
        record.album = song.getAlbum();
        record.durationMillis = song.getDuration().toMillis();
        record.genre = song.getGenre().name();
        record.year = song.getYear();
        record.rating = song.getRating();
        // Exactly one of these two is set, and which one is what makes the song local or streamed.
        record.filePath = song.getFilePath() == null ? null : song.getFilePath().toString();
        record.spotifyUri = song.getSpotifyUri();
        record.source = song.getSource().name();
        record.coverPath = song.getCoverPath() == null ? null : song.getCoverPath().toString();
        record.coverUrl = song.getCoverUrl();
        record.favorite = song.isFavorite();
        record.playCount = song.getPlayCount();
        return record;
    }

    /**
     * Rebuilds a song from stored data, running every value back through the validating setters
     * so a hand-edited file cannot introduce an invalid song.
     *
     * @param record the stored data
     * @return the reconstructed song
     */
    private static Song toSong(SongData record) {
        String id = record.id == null || record.id.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : record.id;

        // The URI is read before the path, and the stored "source" name is deliberately not
        // consulted to decide which this is. A song is streamed because it has a track URI, full
        // stop - so a hand-edited or older file with a mismatched source field still loads as
        // whatever it actually holds, rather than as whatever it claims.
        Song song;
        if (record.spotifyUri != null && !record.spotifyUri.isBlank()) {
            song = Song.spotify(id, record.spotifyUri, record.title, record.artist);
        } else if (record.filePath != null && !record.filePath.isBlank()) {
            song = new Song(id, record.title, record.artist, Path.of(record.filePath));
        } else {
            throw new IllegalArgumentException("stored song has neither a file path nor a Spotify URI");
        }
        song.setAlbum(record.album);
        song.setDuration(Duration.ofMillis(Math.max(0, record.durationMillis)));
        song.setGenre(parseGenre(record.genre));
        song.setYear(record.year);
        song.setRating(record.rating);
        song.setCoverPath(record.coverPath == null || record.coverPath.isBlank()
                ? null : Path.of(record.coverPath));
        song.setCoverUrl(record.coverUrl);
        song.setFavorite(record.favorite);
        song.setPlayCount(Math.max(0, record.playCount));
        return song;
    }

    private static Genre parseGenre(String name) {
        if (name == null || name.isBlank()) {
            return Genre.UNKNOWN;
        }
        try {
            return Genre.valueOf(name);
        } catch (IllegalArgumentException e) {
            LOG.warning("Unknown genre in the stored library: " + name + " - falling back to UNKNOWN");
            return Genre.UNKNOWN;
        }
    }

    // ------------------------------------------------------------------
    // Stored shape
    // ------------------------------------------------------------------

    /** The whole file: a version marker plus the songs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LibraryData {
        public int version;
        public List<SongData> songs;
    }

    /** One song, flattened into types JSON represents directly. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SongData {
        public String id;
        public String title;
        public String artist;
        public String album;
        public long durationMillis;
        public String genre;
        public int year;
        public int rating;
        public String filePath;
        public String spotifyUri;
        /**
         * Written for a human reading the file, and ignored on the way back in - the URI decides.
         * A letter that can disagree with the two fields beside it is a bug waiting for a hand
         * edit, the same reasoning that keeps the runner's rank derived rather than stored.
         */
        public String source;
        public String coverPath;
        public String coverUrl;
        public boolean favorite;
        public int playCount;
    }
}
