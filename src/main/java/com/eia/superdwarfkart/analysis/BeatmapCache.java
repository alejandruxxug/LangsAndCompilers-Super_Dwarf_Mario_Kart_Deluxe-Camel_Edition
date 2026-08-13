package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Remembers what the analyser found, so a track is analysed once and never again.
 *
 * <p>Files live at {@code ~/.superdwarfkart/beatmaps/<sha256-of-file>.json}.
 *
 * <p><strong>Keyed by what is in the file, not by where it is.</strong> A path is not an identity:
 * a library is reorganised, a song is renamed, the same track is imported twice from two folders.
 * Hashing the content means moving a file keeps its beatmap and its high scores stay meaningful,
 * and it means two copies of one track share one analysis for free.
 *
 * <p><strong>The algorithm version is part of the key's validity.</strong> A cached map produced by
 * a different {@link AppConfig#ANALYZER_VERSION} is treated as a miss, so improving the detector
 * cannot leave the game running on courses built by the old one. Bump the constant and every
 * beatmap re-derives itself on next play.
 *
 * <p>Nothing here throws for an ordinary failure. A cache is an optimisation: an unreadable or
 * corrupt entry is a miss with a warning, and a directory that cannot be written to costs a
 * re-analysis rather than a broken application.
 */
public final class BeatmapCache {

    private static final Logger LOG = Logger.getLogger(BeatmapCache.class.getName());

    /** Bumped when the stored shape changes in a way an older reader could not handle. */
    private static final int FORMAT_VERSION = 1;

    /** Bytes hashed at a time when fingerprinting a file. */
    private static final int HASH_BLOCK = 64 * 1024;

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Creates a cache in the default location under the user's home directory. */
    public BeatmapCache() {
        this(AppConfig.beatmapsDir());
    }

    /**
     * Creates a cache in an explicit directory.
     *
     * @param directory where beatmaps are stored; must not be {@code null}
     */
    public BeatmapCache(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
    }

    /** @return the directory beatmaps are stored in; not guaranteed to exist */
    public Path directory() {
        return directory;
    }

    /**
     * Fingerprints a file by its contents.
     *
     * @param audioFile the file to hash; must not be {@code null}
     * @return the SHA-256 of the file, in lower-case hexadecimal
     * @throws IOException if the file cannot be read
     */
    public static String hash(Path audioFile) throws IOException {
        Objects.requireNonNull(audioFile, "audioFile must not be null");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256; if this one does not, nothing
            // sensible can be cached and that is a broken installation rather than a runtime case.
            throw new IllegalStateException("SHA-256 is not available on this platform", e);
        }
        byte[] scratch = new byte[HASH_BLOCK];
        try (InputStream in = Files.newInputStream(audioFile);
             DigestInputStream digesting = new DigestInputStream(in, digest)) {
            while (digesting.read(scratch) >= 0) {
                // Reading is what feeds the digest; there is nothing to do with the bytes.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * @param sourceHash a file's content hash
     * @return where that file's beatmap is stored
     */
    public Path fileFor(String sourceHash) {
        return directory.resolve(sourceHash + ".json");
    }

    /**
     * Looks up a file's beatmap.
     *
     * @param audioFile the audio file; must not be {@code null}
     * @return the cached beatmap, or empty on a miss, an unreadable entry, or one produced by a
     *         different analyser version
     */
    public Optional<Beatmap> load(Path audioFile) {
        try {
            return loadByHash(hash(audioFile));
        } catch (IOException e) {
            LOG.warning("Could not fingerprint " + audioFile + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Looks up a beatmap by the hash of the file it came from.
     *
     * @param sourceHash the content hash
     * @return the cached beatmap, or empty
     */
    public Optional<Beatmap> loadByHash(String sourceHash) {
        Path file = fileFor(sourceHash);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        BeatmapData data;
        try {
            data = mapper.readValue(file.toFile(), BeatmapData.class);
        } catch (JacksonException e) {
            LOG.warning("Discarding an unreadable cached beatmap at " + file
                    + ": " + e.getOriginalMessage());
            return Optional.empty();
        } catch (IOException e) {
            LOG.warning("Could not read the cached beatmap at " + file + ": " + e.getMessage());
            return Optional.empty();
        }
        if (data == null || data.analyzerVersion != AppConfig.ANALYZER_VERSION) {
            // Not deleted. The old entry costs a few kilobytes and would come back into use if the
            // version were ever rolled back; a re-analysis overwrites it in place anyway.
            return Optional.empty();
        }
        try {
            return Optional.of(new Beatmap(
                    data.sourceHash == null ? sourceHash : data.sourceHash,
                    data.analyzerVersion,
                    data.durationSeconds,
                    data.bpm,
                    data.onsets == null ? new double[0] : data.onsets,
                    data.strongBeats == null ? new double[0] : data.strongBeats,
                    data.strongBeatStrengths));
        } catch (RuntimeException e) {
            LOG.warning("Discarding an invalid cached beatmap at " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores a beatmap.
     *
     * <p>Written to a temporary file and moved into place, so an interruption leaves the previous
     * entry rather than a half-written one - which would otherwise be read back as a valid course
     * with the onsets after the cut missing.
     *
     * @param beatmap the beatmap to store; must not be {@code null}
     * @return whether it was stored
     */
    public boolean store(Beatmap beatmap) {
        Objects.requireNonNull(beatmap, "beatmap must not be null");
        if (beatmap.sourceHash().isBlank()) {
            return false;
        }

        BeatmapData data = new BeatmapData();
        data.version = FORMAT_VERSION;
        data.sourceHash = beatmap.sourceHash();
        data.analyzerVersion = beatmap.analyzerVersion();
        data.durationSeconds = beatmap.durationSeconds();
        data.bpm = beatmap.bpm();
        data.onsets = beatmap.onsets();
        data.strongBeats = beatmap.strongBeats();
        data.strongBeatStrengths = beatmap.strongBeatStrengths();

        Path destination = fileFor(beatmap.sourceHash());
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "beatmap", ".json.tmp");
            mapper.writeValue(temporary.toFile(), data);
            moveIntoPlace(temporary, destination);
            temporary = null;
            return true;
        } catch (IOException e) {
            LOG.warning("Could not cache the beatmap at " + destination + ": " + e.getMessage());
            return false;
        } finally {
            deleteQuietly(temporary);
        }
    }

    /**
     * @param source      the temporary file
     * @param destination the real cache entry
     * @throws IOException if neither move succeeds
     */
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

    /**
     * The stored shape: a flat record of primitives, so the file stays readable and hand-editable
     * and {@link Beatmap} carries no persistence annotations.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BeatmapData {
        public int version;
        public String sourceHash;
        public int analyzerVersion;
        public double durationSeconds;
        public double bpm;
        public double[] onsets;
        public double[] strongBeats;
        public double[] strongBeatStrengths;
    }
}
