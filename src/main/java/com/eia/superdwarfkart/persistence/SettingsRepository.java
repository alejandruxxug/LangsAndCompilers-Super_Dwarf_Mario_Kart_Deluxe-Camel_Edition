package com.eia.superdwarfkart.persistence;

import com.eia.superdwarfkart.app.AppConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The handful of choices that should still be true next time the application opens: the mood, the
 * racer and the speed class.
 *
 * <p>Stored as JSON at {@code ~/.superdwarfkart/settings.json}. Like the score board and unlike the
 * library, this is its own in-memory model - there is no collection to hold, only three values that
 * are read once at startup and written whenever one of them changes.
 *
 * <p><strong>Nothing here may stop the application from opening.</strong> A missing file is the
 * ordinary first-run state, and an unreadable or half-written one is reported and then treated as
 * missing: the defaults are all valid, so a corrupt settings file costs the user their mood and
 * nothing else (ground rule 5). The same applies to a value this build does not recognise - a mood
 * id written by a later version resolves to nothing and falls back, rather than throwing.
 */
public class SettingsRepository {

    private static final Logger LOG = Logger.getLogger(SettingsRepository.class.getName());

    /** Bumped when the on-disk shape changes in a way older readers could not handle. */
    private static final int FORMAT_VERSION = 1;

    private final Path file;
    private final ObjectMapper mapper;

    private String moodId;
    private String racerId;
    private String speedClass;
    private String spotifyBinaryPath;

    /**
     * The registered Spotify application catalogue search runs as.
     *
     * <p><strong>The secret is stored in clear, and that is worth being explicit about.</strong>
     * This file already sits in the user's own home directory under their own account, the
     * credential grants nothing but catalogue search against a free application they registered
     * themselves, and there is no key store on this project's dependency list to put it in.
     * Obscuring it would buy nothing and would suggest a protection that was not there. What does
     * matter is that it never goes anywhere else: it is not logged, not printed by the smoke test,
     * and not carried in the library or mood exports.
     */
    private String spotifyClientId;
    private String spotifyClientSecret;

    /**
     * Whether to fetch the go-librespot binary in the background at startup.
     *
     * <p>Defaults to on, so the daemon is simply there by the time anybody opens the Spotify page.
     * It downloads into this application's own folder and starts nothing - the process, the socket
     * and the login all still wait to be asked for. Turning it off is for a metered connection.
     */
    private boolean spotifyAutoFetch = true;

    /** Whether every kind of motion the look produces is suppressed. See {@link #reduceMotion()}. */
    private boolean reduceMotion;

    /** Creates a repository over the default location, {@code ~/.superdwarfkart/settings.json}. */
    public SettingsRepository() {
        this(AppConfig.settingsFile());
    }

    /**
     * Creates a repository over a specific file.
     *
     * @param file where the settings live; must not be {@code null}
     */
    public SettingsRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    /** @return the stored mood id, or {@code null} when the user has never chosen one */
    public String moodId() {
        return moodId;
    }

    /** @return the stored racer name, or {@code null} when the user has never chosen one */
    public String racerId() {
        return racerId;
    }

    /**
     * @return whether the user has asked for motion to be suppressed - scrolling mood layers,
     *         beat-reactive palettes and the runner's own full-screen beat effects
     */
    public boolean reduceMotion() {
        return reduceMotion;
    }

    /**
     * Stores whether motion is suppressed and writes it out.
     *
     * <p>Persisted, unlike the structure column's fold: somebody who needs this needs it at every
     * launch, and being made to find the switch again each time would be worse than not having it.
     *
     * @param on whether to suppress motion
     */
    public void setReduceMotion(boolean on) {
        this.reduceMotion = on;
        save();
    }

    /** @return the stored speed class name, or {@code null} when the user has never chosen one */
    public String speedClass() {
        return speedClass;
    }

    /**
     * @return the path the user pointed at their own go-librespot build, or {@code null} to search
     *         the usual places
     */
    public String spotifyBinaryPath() {
        return spotifyBinaryPath;
    }

    /** @return whether the daemon may be downloaded in the background at startup */
    public boolean spotifyAutoFetch() {
        return spotifyAutoFetch;
    }

    /** @return the registered Spotify application's client id, or {@code null} */
    public String spotifyClientId() {
        return spotifyClientId;
    }

    /** @return the registered Spotify application's client secret, or {@code null} */
    public String spotifyClientSecret() {
        return spotifyClientSecret;
    }

    /**
     * Stores the Spotify application catalogue search runs as.
     *
     * <p>Both together, because half a credential pair is not a usable state and storing them
     * separately would let a save leave one of the two behind.
     *
     * @param clientId     the application id; {@code null} or blank turns catalogue search off
     * @param clientSecret the application secret
     */
    public void setSpotifyCredentials(String clientId, String clientSecret) {
        this.spotifyClientId = blankToNull(clientId);
        this.spotifyClientSecret = blankToNull(clientSecret);
        save();
    }

    /**
     * Stores an explicit path to the go-librespot executable.
     *
     * @param path the executable's path; {@code null} restores the usual search
     */
    public void setSpotifyBinaryPath(String path) {
        this.spotifyBinaryPath = blankToNull(path);
        save();
    }

    /**
     * Stores whether the daemon may be downloaded at startup.
     *
     * @param enabled whether to fetch it
     */
    public void setSpotifyAutoFetch(boolean enabled) {
        this.spotifyAutoFetch = enabled;
        save();
    }

    /**
     * Stores the chosen mood and writes it out.
     *
     * @param id the mood's identifier; {@code null} clears the choice
     */
    public void setMoodId(String id) {
        this.moodId = id;
        save();
    }

    /**
     * Stores the chosen racer and writes it out.
     *
     * @param id the racer's name; {@code null} clears the choice
     */
    public void setRacerId(String id) {
        this.racerId = id;
        save();
    }

    /**
     * Stores the chosen speed class and writes it out.
     *
     * @param name the class's name; {@code null} clears the choice
     */
    public void setSpeedClass(String name) {
        this.speedClass = name;
        save();
    }

    /** @return where these settings are stored */
    public Path storageLocation() {
        return file;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            SettingsDto dto = mapper.readValue(file.toFile(), SettingsDto.class);
            if (dto != null) {
                moodId = blankToNull(dto.moodId);
                racerId = blankToNull(dto.racerId);
                speedClass = blankToNull(dto.speedClass);
                spotifyBinaryPath = blankToNull(dto.spotifyBinaryPath);
                spotifyClientId = blankToNull(dto.spotifyClientId);
                spotifyClientSecret = blankToNull(dto.spotifyClientSecret);
                // Absent in a file written before Spotify existed, where Jackson leaves the
                // boxed field null. Only an explicit false turns it off.
                spotifyAutoFetch = dto.spotifyAutoFetch == null || dto.spotifyAutoFetch;
                reduceMotion = dto.reduceMotion != null && dto.reduceMotion;
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING,
                    "Could not read " + file + " - carrying on with the default settings", e);
        }
    }

    /**
     * Writes the settings out.
     *
     * <p>Through a temporary file and an atomic move, so a crash mid-write cannot leave a
     * half-written file that the next launch has to recover from.
     */
    private void save() {
        SettingsDto dto = new SettingsDto();
        dto.version = FORMAT_VERSION;
        dto.moodId = moodId;
        dto.racerId = racerId;
        dto.speedClass = speedClass;
        dto.spotifyBinaryPath = spotifyBinaryPath;
        dto.spotifyAutoFetch = spotifyAutoFetch;
        dto.reduceMotion = reduceMotion;
        dto.spotifyClientId = spotifyClientId;
        dto.spotifyClientSecret = spotifyClientSecret;

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(temporary.toFile(), dto);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // A preference that failed to persist is worth a line in the log and nothing more.
            LOG.log(Level.WARNING, "Could not write " + file, e);
        }
    }

    /**
     * @param value a stored value as typed
     * @return the value stripped of surrounding whitespace, or {@code null} when nothing is left -
     *         a pasted credential or path routinely carries a trailing newline, which is invisible
     *         in the field and breaks the value everywhere it is used
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The on-disk shape. Unknown fields are ignored so a newer file still loads. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SettingsDto {
        public int version;
        public String moodId;
        public String racerId;
        public String speedClass;
        public String spotifyBinaryPath;
        /** Boxed so an older file, which has no such key, is told apart from an explicit false. */
        public Boolean spotifyAutoFetch;
        /** Boxed for the same reason: absent in a file written before the mood system existed. */
        public Boolean reduceMotion;
        public String spotifyClientId;
        public String spotifyClientSecret;
    }
}
