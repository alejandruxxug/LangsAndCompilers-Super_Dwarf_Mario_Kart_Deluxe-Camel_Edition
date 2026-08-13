package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.game.Rank;
import com.eia.superdwarfkart.game.ScoreEntry;
import com.eia.superdwarfkart.game.SpeedClass;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The score board: the best run for each {@code (song, speed class)}, stored as JSON at
 * {@code ~/.superdwarfkart/scores.json}.
 *
 * <p>Unlike the library, the board is <strong>also its own in-memory model</strong>. A separate
 * {@code ScoreBoard} beside it would be the consistent shape, but there is nothing for one to do:
 * the board is a map with at most one entry per song per class, it is only ever read by key, and
 * every change is a single replacement that has to reach disk immediately anyway - a run that
 * earned an S and was lost because the window was closed is worse than any amount of symmetry.
 *
 * <p><strong>A worse run never displaces a better one.</strong> {@link #record(ScoreEntry)} is the
 * only way in and it compares first, so skipping a track after ten seconds cannot wipe out the
 * complete run that came before it - which matters because a track being skipped is how most runs
 * actually end.
 *
 * <p>A failure to read the board is reported and swallowed: high scores are the last thing that
 * should be able to stop the application from opening (ground rule 5).
 */
public class ScoreRepository implements Repository<ScoreEntry> {

    private static final Logger LOG = Logger.getLogger(ScoreRepository.class.getName());

    /** Bumped when the on-disk shape changes in a way older readers could not handle. */
    private static final int FORMAT_VERSION = 1;

    private final Path file;
    private final ObjectMapper mapper;

    /**
     * The board, keyed by song and class.
     *
     * <p>Insertion-ordered so the file keeps a stable shape between writes and a diff of it shows
     * what actually changed rather than a reshuffle.
     */
    private final Map<String, ScoreEntry> best = new LinkedHashMap<>();

    /** Creates a repository storing at the default location under the user's home directory. */
    public ScoreRepository() {
        this(AppConfig.scoresFile());
    }

    /**
     * Creates a repository storing at an explicit location, and loads whatever is there.
     *
     * @param file where to store the board; must not be {@code null}
     */
    public ScoreRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        loadIntoMemory();
    }

    @Override
    public Path storageLocation() {
        return file;
    }

    // ------------------------------------------------------------------
    // The board
    // ------------------------------------------------------------------

    /**
     * Stores a run if it is better than what is already on the board.
     *
     * <p>Writes through to disk on success, because the next thing that happens after a run is
     * usually the next song and eventually the window closing.
     *
     * @param entry the run to consider; must not be {@code null}
     * @return whether it became the new best for its song and class
     */
    public boolean record(ScoreEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        String key = keyOf(entry.songId(), entry.speedClass());
        if (!entry.beats(best.get(key))) {
            return false;
        }
        best.put(key, entry);
        try {
            saveAll(best.values());
        } catch (PersistenceException e) {
            // The run stays on the board for this session. Refusing to acknowledge it because the
            // disk is full would lose it twice over.
            LOG.warning("Could not store the score board: " + e.getMessage());
        }
        return true;
    }

    /**
     * @param songId     the song to look up
     * @param speedClass the class it was driven at
     * @return the best stored run for that pair, if there is one
     */
    public Optional<ScoreEntry> best(String songId, SpeedClass speedClass) {
        if (songId == null || speedClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(best.get(keyOf(songId, speedClass)));
    }

    /**
     * Finds a song's best run at any speed class.
     *
     * <p>What the library table shows beside a song, where there is room for one badge and not for
     * four. Ranked by {@link Rank} first and by the faster class second, so the badge reports the
     * best <em>drive</em> and breaks a tie in favour of the harder class it was driven at.
     *
     * @param songId the song to look up
     * @return its best run, if it has been driven at all
     */
    public Optional<ScoreEntry> bestAnyClass(String songId) {
        if (songId == null) {
            return Optional.empty();
        }
        ScoreEntry found = null;
        for (SpeedClass speedClass : SpeedClass.values()) {
            ScoreEntry candidate = best.get(keyOf(songId, speedClass));
            if (candidate == null) {
                continue;
            }
            if (found == null
                    || candidate.completion() > found.completion()
                    || (candidate.completion() == found.completion()
                            && candidate.speedClass().ordinal() > found.speedClass().ordinal())) {
                found = candidate;
            }
        }
        return Optional.ofNullable(found);
    }

    /** @return how many runs are on the board */
    public int size() {
        return best.size();
    }

    /**
     * @param songId     the song
     * @param speedClass the class
     * @return the map key for that pair
     */
    private static String keyOf(String songId, SpeedClass speedClass) {
        return songId + '|' + speedClass.name();
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    /**
     * Loads the board, degrading to an empty one if it cannot be read.
     *
     * <p>Called from the constructor, so an unreadable board leaves a usable repository rather than
     * an object that throws from every method.
     */
    private void loadIntoMemory() {
        try {
            for (ScoreEntry entry : loadAll()) {
                best.put(keyOf(entry.songId(), entry.speedClass()), entry);
            }
        } catch (PersistenceException e) {
            LOG.warning("Could not read the score board at " + file + ": " + e.getMessage()
                    + " - starting with an empty board");
        }
    }

    /**
     * Reads the stored board.
     *
     * <p>A missing file is not an error: nothing has been driven yet. An entry that fails
     * validation is skipped with a warning, so one bad record cannot cost the user the rest of
     * their scores.
     *
     * @return the stored runs, or an empty list when nothing has been stored
     * @throws PersistenceException if the file exists but cannot be read or parsed
     */
    @Override
    public List<ScoreEntry> loadAll() {
        if (!Files.exists(file)) {
            return List.of();
        }

        ScoreBoardData data;
        try {
            data = mapper.readValue(file.toFile(), ScoreBoardData.class);
        } catch (JacksonException e) {
            throw new PersistenceException("The score file at " + file
                    + " is not valid JSON and could not be read: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new PersistenceException("Could not read the score file at " + file, e);
        }

        if (data == null || data.scores == null) {
            return List.of();
        }

        List<ScoreEntry> entries = new ArrayList<>(data.scores.size());
        int skipped = 0;
        for (ScoreData record : data.scores) {
            try {
                entries.add(toEntry(record));
            } catch (RuntimeException e) {
                skipped++;
                LOG.warning("Skipping an unreadable score entry in " + file + ": " + e.getMessage());
            }
        }
        if (skipped > 0) {
            LOG.warning("Loaded " + entries.size() + " scores and skipped " + skipped
                    + " invalid entries");
        }
        return entries;
    }

    /**
     * Writes the board.
     *
     * <p>Through a temporary file and a move, exactly as the library is written: an interruption
     * partway leaves the previous board intact rather than a truncated one.
     *
     * @param items the runs to store; must not be {@code null}
     * @throws PersistenceException if the write fails
     */
    @Override
    public void saveAll(Collection<? extends ScoreEntry> items) {
        Objects.requireNonNull(items, "items must not be null");

        ScoreBoardData data = new ScoreBoardData();
        data.version = FORMAT_VERSION;
        data.scores = new ArrayList<>(items.size());
        for (ScoreEntry entry : items) {
            data.scores.add(toRecord(entry));
        }

        Path directory = file.toAbsolutePath().getParent();
        Path temporary = null;
        try {
            if (directory != null) {
                Files.createDirectories(directory);
            }
            temporary = Files.createTempFile(directory, "scores", ".json.tmp");
            mapper.writeValue(temporary.toFile(), data);
            moveIntoPlace(temporary, file);
            temporary = null;
        } catch (IOException e) {
            throw new PersistenceException("Could not write the score file at " + file, e);
        } finally {
            deleteQuietly(temporary);
        }
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
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

    private static ScoreData toRecord(ScoreEntry entry) {
        ScoreData record = new ScoreData();
        record.songId = entry.songId();
        record.speedClass = entry.speedClass().name();
        record.score = entry.score();
        record.coinsCollected = entry.coinsCollected();
        record.coinsAvailable = entry.coinsAvailable();
        record.achievedAtMillis = entry.achievedAt().toEpochMilli();
        // Written for a human reading the file, and ignored on the way back in - the rank is
        // derived from the two counts and a stored copy could only ever contradict them.
        record.rank = entry.rank().name();
        return record;
    }

    private static ScoreEntry toEntry(ScoreData record) {
        if (record.songId == null || record.songId.isBlank()) {
            throw new IllegalArgumentException("stored score has no song");
        }
        return new ScoreEntry(
                record.songId,
                parseSpeedClass(record.speedClass),
                record.score,
                record.coinsCollected,
                record.coinsAvailable,
                Instant.ofEpochMilli(record.achievedAtMillis));
    }

    private static SpeedClass parseSpeedClass(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("stored score has no speed class");
        }
        try {
            return SpeedClass.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown speed class " + name, e);
        }
    }

    // ------------------------------------------------------------------
    // Stored shape
    // ------------------------------------------------------------------

    /** The whole file: a version marker plus the runs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ScoreBoardData {
        public int version;
        public List<ScoreData> scores;
    }

    /** One run, flattened into types JSON represents directly. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ScoreData {
        public String songId;
        public String speedClass;
        public int score;
        public int coinsCollected;
        public int coinsAvailable;
        public long achievedAtMillis;
        public String rank;
    }
}
