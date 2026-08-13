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

    /** @return the stored speed class name, or {@code null} when the user has never chosen one */
    public String speedClass() {
        return speedClass;
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** The on-disk shape. Unknown fields are ignored so a newer file still loads. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SettingsDto {
        public int version;
        public String moodId;
        public String racerId;
        public String speedClass;
    }
}
