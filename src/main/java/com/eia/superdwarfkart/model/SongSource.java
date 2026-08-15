package com.eia.superdwarfkart.model;

/**
 * Where a song's audio comes from.
 *
 * <p>The library holds both kinds side by side and the three playback structures navigate them
 * identically - a ring, a queue and a tree hold {@link Song} objects and have never had an opinion
 * about where the bytes originate. What the source decides is which {@code AudioSource} opens the
 * track, and which features can honestly be offered for it.
 *
 * <p><strong>A local song is the complete one.</strong> It has a file on disk, so it can be
 * analysed before it is played, it gets a beatmap in the cache, and the runner can generate its
 * whole course up front. A streamed song has no file until it has been heard, so its course is
 * built from the audio as it plays and is ready on the next play - see {@code SpotifyAudioSource}.
 * Everything else - rating, favourite, play count, history, statistics, the structures - works the
 * same for both.
 */
public enum SongSource {

    /** A file on this machine, opened directly. */
    LOCAL("LOCAL", "A file on this machine"),

    /** Streamed from Spotify through the go-librespot daemon. */
    SPOTIFY("SPOTIFY", "Streamed from Spotify");

    private final String label;
    private final String description;

    SongSource(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** @return the short caption shown in the interface */
    public String label() {
        return label;
    }

    /** @return a one-line explanation, for a tooltip */
    public String description() {
        return description;
    }

    /**
     * Resolves a stored name, falling back rather than throwing.
     *
     * <p>{@code valueOf} throws on anything it does not recognise, and a library file written by a
     * later build is no reason to refuse to open one song, let alone the whole library. Same
     * decision as {@code Racer.byName} and {@code SpeedClass.byName}.
     *
     * @param name the stored name, possibly {@code null} or from a newer version
     * @return the matching source, or {@link #LOCAL} when the name is unknown
     */
    public static SongSource byName(String name) {
        if (name == null || name.isBlank()) {
            return LOCAL;
        }
        for (SongSource candidate : values()) {
            if (candidate.name().equalsIgnoreCase(name.trim())) {
                return candidate;
            }
        }
        return LOCAL;
    }

    @Override
    public String toString() {
        return label;
    }
}
