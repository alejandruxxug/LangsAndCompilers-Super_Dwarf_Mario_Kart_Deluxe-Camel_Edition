package com.eia.superdwarfkart.model;

import java.util.List;
import java.util.Locale;

/**
 * Musical genre of a song, used by the library filters.
 */
public enum Genre {

    POP("Pop"),
    ROCK("Rock"),
    METAL("Metal"),
    HIP_HOP("Hip Hop"),
    ELECTRONIC("Electronic"),
    JAZZ("Jazz"),
    BLUES("Blues"),
    CLASSICAL("Classical"),
    FOLK("Folk"),
    LATIN("Latin"),
    SOUNDTRACK("Soundtrack"),
    CHIPTUNE("Chiptune"),
    OTHER("Other"),
    UNKNOWN("Unknown");

    private final String displayName;

    Genre(String displayName) {
        this.displayName = displayName;
    }

    /** @return human-readable name for the user interface */
    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Picks the closest genre for a list of free-text tags.
     *
     * <p><strong>Written for Spotify's artist genres, and kept here rather than in
     * {@code spotify/}.</strong> A streamed track arrives with tags like {@code "witch house"},
     * {@code "electropop"}, {@code "brazilian bass"} or {@code "j-rock"} - dozens of them, invented
     * continuously, and none of them one of the fourteen constants above. Deciding which of ours a
     * phrase describes is a question about <em>our</em> vocabulary, so it lives with the vocabulary;
     * nothing here mentions Spotify, and {@code model/} is not allowed to import it in any case.
     *
     * <p>Matched on <strong>substrings, most specific first</strong>, because the tags are compounds
     * and the compound names the parent: {@code "electropop"} contains both {@code "pop"} and
     * {@code "electro"}, {@code "latin pop"} contains {@code "pop"}, and {@code "video game music"}
     * is a soundtrack rather than nothing. So {@link #PATTERNS} is ordered and the first hit wins,
     * which puts the narrow tags ahead of the wide ones. Getting that order backwards throws nothing;
     * it just files half the library under Pop.
     *
     * <p><strong>The first tag is not weighted above the rest.</strong> Spotify orders an artist's
     * genres by its own relevance and the specific one is frequently not first, so every tag is
     * offered to every pattern in turn and the most specific match across all of them wins.
     *
     * @param tags the tags to read, in any order; {@code null} or empty gives {@link #UNKNOWN}
     * @return the closest genre, or {@link #UNKNOWN} when nothing matched - never {@code null}, and
     *         deliberately not {@link #OTHER}, which means "the user looked and none of these fit"
     */
    public static Genre fromTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return UNKNOWN;
        }
        for (Pattern pattern : PATTERNS) {
            for (String tag : tags) {
                if (tag == null) {
                    continue;
                }
                if (tag.toLowerCase(Locale.ROOT).contains(pattern.needle)) {
                    return pattern.genre;
                }
            }
        }
        return UNKNOWN;
    }

    /**
     * One substring and the genre it implies.
     *
     * @param needle the lower-case substring to look for
     * @param genre  what a tag containing it is filed as
     */
    private record Pattern(String needle, Genre genre) { }

    /**
     * The tag vocabulary, <strong>most specific first</strong> - see {@link #fromTags}.
     *
     * <p>Not exhaustive and not meant to be: this only has to be right often enough that the genre
     * box comes up pre-filled with a sensible answer, and the user can change it in the same dialog
     * before the song is added. An unrecognised tag costs a dropdown selection, not a wrong record.
     */
    private static final List<Pattern> PATTERNS = List.of(
            // Narrow first, and every one of these is a substring of something wider below it.
            new Pattern("chiptune", CHIPTUNE),
            new Pattern("chip ", CHIPTUNE),
            new Pattern("8-bit", CHIPTUNE),
            new Pattern("bitpop", CHIPTUNE),
            new Pattern("video game", SOUNDTRACK),
            new Pattern("soundtrack", SOUNDTRACK),
            new Pattern("score", SOUNDTRACK),
            new Pattern("anime", SOUNDTRACK),

            new Pattern("hip hop", HIP_HOP),
            new Pattern("hip-hop", HIP_HOP),
            new Pattern("rap", HIP_HOP),
            new Pattern("trap", HIP_HOP),
            new Pattern("drill", HIP_HOP),
            new Pattern("grime", HIP_HOP),

            new Pattern("metal", METAL),
            new Pattern("metalcore", METAL),
            new Pattern("hardcore", METAL),
            new Pattern("djent", METAL),
            new Pattern("grindcore", METAL),
            new Pattern("doom", METAL),

            new Pattern("classical", CLASSICAL),
            new Pattern("baroque", CLASSICAL),
            new Pattern("opera", CLASSICAL),
            new Pattern("orchestra", CLASSICAL),
            new Pattern("symphon", CLASSICAL),
            new Pattern("choral", CLASSICAL),

            new Pattern("jazz", JAZZ),
            new Pattern("bebop", JAZZ),
            new Pattern("swing", JAZZ),
            new Pattern("bossa", JAZZ),

            new Pattern("blues", BLUES),
            new Pattern("soul", BLUES),
            new Pattern("motown", BLUES),
            new Pattern("gospel", BLUES),

            new Pattern("reggaeton", LATIN),
            new Pattern("salsa", LATIN),
            new Pattern("cumbia", LATIN),
            new Pattern("bachata", LATIN),
            new Pattern("merengue", LATIN),
            new Pattern("tango", LATIN),
            new Pattern("samba", LATIN),
            new Pattern("bolero", LATIN),
            new Pattern("flamenco", LATIN),
            new Pattern("corrido", LATIN),
            new Pattern("ranchera", LATIN),
            new Pattern("mariachi", LATIN),
            new Pattern("vallenato", LATIN),
            new Pattern("latin", LATIN),
            // Spanish- and Portuguese-language scenes Spotify tags by language rather than by style.
            new Pattern("espanol", LATIN),
            new Pattern("español", LATIN),
            new Pattern("brasileir", LATIN),
            new Pattern("sertanejo", LATIN),

            new Pattern("folk", FOLK),
            new Pattern("bluegrass", FOLK),
            new Pattern("country", FOLK),
            new Pattern("americana", FOLK),
            new Pattern("singer-songwriter", FOLK),
            new Pattern("acoustic", FOLK),

            // Wider than the styles above and narrower than the two catch-alls below it.
            new Pattern("house", ELECTRONIC),
            new Pattern("techno", ELECTRONIC),
            new Pattern("trance", ELECTRONIC),
            new Pattern("dubstep", ELECTRONIC),
            new Pattern("drum and bass", ELECTRONIC),
            new Pattern("edm", ELECTRONIC),
            new Pattern("synth", ELECTRONIC),
            new Pattern("electro", ELECTRONIC),
            new Pattern("ambient", ELECTRONIC),
            new Pattern("idm", ELECTRONIC),
            new Pattern("breakbeat", ELECTRONIC),
            new Pattern("vaporwave", ELECTRONIC),
            new Pattern("bass", ELECTRONIC),
            new Pattern("wave", ELECTRONIC),
            new Pattern("disco", ELECTRONIC),
            // "garage" is deliberately absent: it is UK garage and it is garage rock, and the two are
            // on opposite sides of this list. A tag that would be filed wrongly half the time is worse
            // than one that falls through to UNKNOWN and lets the user answer.

            new Pattern("punk", ROCK),
            new Pattern("grunge", ROCK),
            new Pattern("shoegaze", ROCK),
            new Pattern("emo", ROCK),
            new Pattern("indie", ROCK),
            new Pattern("alternative", ROCK),
            new Pattern("rock", ROCK),

            // Last, because "pop" is a substring of a great many of the tags above - "electropop",
            // "latin pop", "k-pop", "synthpop", "dream pop". Every one of those is caught by its more
            // specific half further up the list, which is exactly what the ordering buys.
            new Pattern("r&b", POP),
            new Pattern("pop", POP),

            // Below "pop", not above it, and that is the whole of what this list is about. "dance" is
            // wider than every genre it appears beside: "dance pop" is pop, "dance rock" is rock, and
            // only "eurodance" and a bare "dance" are actually electronic. Put it up in the electronic
            // block and it swallows all three - which is exactly the failure this ordering exists to
            // avoid, and it was caught by a test rather than by reading the list.
            new Pattern("dance", ELECTRONIC));
}
