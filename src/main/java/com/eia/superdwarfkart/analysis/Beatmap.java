package com.eia.superdwarfkart.analysis;

import java.util.Arrays;
import java.util.Objects;

/**
 * What the analyser found in one track: a tempo, every onset, and which of those fall on the beat.
 *
 * <p>This is the course the rhythm game is generated from. An entity is placed on an event in here
 * and spawned {@code travelTime} earlier, so everything the player sees is downstream of these
 * numbers - which is why the type is immutable and why the times are seconds from the start of the
 * track, matching {@code AudioSource.position()} exactly.
 *
 * <p><strong>Onsets and strong beats are different things and the game uses both.</strong> An onset
 * is any note attack; a strong beat is an onset that also landed on the tempo grid. The lower speed
 * classes place entities only on strong beats, and 200cc uses the intermediate onsets too - so a
 * fast track is denser because the music is, not because a timer was turned up.
 *
 * <p>The accessors come in two shapes deliberately. {@link #onsets()} copies, and is for tests and
 * for storage; {@link #onsetAt(int)} and {@link #firstOnsetAtOrAfter(double)} do not, and are what
 * the game's spawn loop calls sixty times a second.
 */
public final class Beatmap {

    /** A track with no beatmap: no tempo, no events. Never {@code null}, so callers need no guard. */
    public static final Beatmap EMPTY = new Beatmap("", 0, 0, 0, new double[0], new double[0]);

    private final String sourceHash;
    private final int analyzerVersion;
    private final double durationSeconds;
    private final double bpm;
    private final double[] onsets;
    private final double[] strongBeats;

    /**
     * Builds a beatmap.
     *
     * @param sourceHash      content hash of the analysed file, the cache's key
     * @param analyzerVersion the algorithm version that produced it
     * @param durationSeconds the track's playing time
     * @param bpm             the detected tempo, or 0 when none could be established
     * @param onsets          every detected onset, in seconds, ascending
     * @param strongBeats     the subset of onsets that fell on the tempo grid, ascending
     * @throws IllegalArgumentException if either series is not ascending
     */
    public Beatmap(String sourceHash, int analyzerVersion, double durationSeconds, double bpm,
                   double[] onsets, double[] strongBeats) {
        this.sourceHash = Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        this.analyzerVersion = analyzerVersion;
        this.durationSeconds = Math.max(0, durationSeconds);
        this.bpm = Math.max(0, bpm);
        // Copied on the way in as well as on the way out: a caller that kept its array must not be
        // able to reach in and change a course after it has been cached.
        this.onsets = requireAscending(onsets, "onsets");
        this.strongBeats = requireAscending(strongBeats, "strongBeats");
    }

    /**
     * @param times the series to check
     * @param name  what it is called, for the message
     * @return a defensive copy
     */
    private static double[] requireAscending(double[] times, String name) {
        if (times == null) {
            return new double[0];
        }
        double[] copy = times.clone();
        for (int index = 1; index < copy.length; index++) {
            if (copy[index] < copy[index - 1]) {
                throw new IllegalArgumentException(
                        name + " must be ascending, but " + copy[index] + " follows "
                                + copy[index - 1] + " at index " + index);
            }
        }
        return copy;
    }

    // ------------------------------------------------------------------
    // What was found
    // ------------------------------------------------------------------

    /** @return content hash of the file this was made from */
    public String sourceHash() {
        return sourceHash;
    }

    /** @return the algorithm version that produced this map */
    public int analyzerVersion() {
        return analyzerVersion;
    }

    /** @return the track's playing time, in seconds */
    public double durationSeconds() {
        return durationSeconds;
    }

    /** @return the detected tempo in beats per minute, or 0 when none was established */
    public double bpm() {
        return bpm;
    }

    /**
     * @return seconds between beats at the detected tempo, or 0 when there is no tempo
     */
    public double beatPeriod() {
        return bpm <= 0 ? 0 : 60 / bpm;
    }

    /** @return whether a tempo was established */
    public boolean hasTempo() {
        return bpm > 0;
    }

    /** @return whether there is nothing here to build a course from */
    public boolean isEmpty() {
        return onsets.length == 0;
    }

    /** @return every onset, in seconds; a copy, safe to modify */
    public double[] onsets() {
        return onsets.clone();
    }

    /** @return the onsets that fell on the tempo grid, in seconds; a copy, safe to modify */
    public double[] strongBeats() {
        return strongBeats.clone();
    }

    /** @return how many onsets were found */
    public int onsetCount() {
        return onsets.length;
    }

    /** @return how many of them fell on the beat */
    public int strongBeatCount() {
        return strongBeats.length;
    }

    /**
     * @param index which onset, counting from zero
     * @return its time in seconds
     * @throws IndexOutOfBoundsException if there is no such onset
     */
    public double onsetAt(int index) {
        return onsets[index];
    }

    /**
     * @param index which strong beat, counting from zero
     * @return its time in seconds
     * @throws IndexOutOfBoundsException if there is no such beat
     */
    public double strongBeatAt(int index) {
        return strongBeats[index];
    }

    // ------------------------------------------------------------------
    // Queries the game's spawn loop makes
    // ------------------------------------------------------------------

    /**
     * Finds where to start reading, for a lookahead window that has just moved forward.
     *
     * <p>Binary search rather than a scan: the spawn loop asks this at the display's rate and the
     * onsets of a long track number in the thousands.
     *
     * @param seconds the time to search from
     * @return the index of the first onset at or after that time, or {@link #onsetCount()} when
     *         there is none
     */
    public int firstOnsetAtOrAfter(double seconds) {
        return firstAtOrAfter(onsets, seconds);
    }

    /**
     * @param seconds the time to search from
     * @return the index of the first strong beat at or after that time, or
     *         {@link #strongBeatCount()} when there is none
     */
    public int firstStrongBeatAtOrAfter(double seconds) {
        return firstAtOrAfter(strongBeats, seconds);
    }

    /**
     * @param times   an ascending series
     * @param seconds the time to search from
     * @return the index of the first entry at or after that time
     */
    private static int firstAtOrAfter(double[] times, double seconds) {
        int found = Arrays.binarySearch(times, seconds);
        // A miss encodes the insertion point, which is exactly the answer; a hit may be one of
        // several equal entries, so walk back to the first of them.
        if (found < 0) {
            return -(found + 1);
        }
        while (found > 0 && times[found - 1] >= seconds) {
            found--;
        }
        return found;
    }

    /**
     * @param seconds the time to search from
     * @return the next strong beat strictly after that time, or {@code -1} when there is none
     */
    public double nextStrongBeatAfter(double seconds) {
        for (int index = firstStrongBeatAtOrAfter(seconds); index < strongBeats.length; index++) {
            if (strongBeats[index] > seconds) {
                return strongBeats[index];
            }
        }
        return -1;
    }

    /**
     * Reports the most recent strong beat, for the pulse the course flashes on.
     *
     * @param seconds the time to search back from
     * @return the last strong beat at or before that time, or {@code -1} when there is none
     */
    public double lastStrongBeatAtOrBefore(double seconds) {
        int next = firstStrongBeatAtOrAfter(seconds);
        if (next < strongBeats.length && strongBeats[next] == seconds) {
            return strongBeats[next];
        }
        return next == 0 ? -1 : strongBeats[next - 1];
    }

    /**
     * Counts the onsets inside a half-open interval.
     *
     * @param fromSeconds start of the interval, inclusive
     * @param toSeconds   end of the interval, exclusive
     * @return how many onsets fall in it
     */
    public int onsetsBetween(double fromSeconds, double toSeconds) {
        if (toSeconds <= fromSeconds) {
            return 0;
        }
        return firstOnsetAtOrAfter(toSeconds) - firstOnsetAtOrAfter(fromSeconds);
    }

    /**
     * Reports how tightly the strong beats sit on the tempo grid.
     *
     * <p><strong>This is the confidence measure, and it is worth more than the tempo itself.</strong>
     * A detected tempo always looks plausible - it is a number in the right range whatever the
     * audio was. What distinguishes a tempo the track really has from one the histogram merely
     * preferred is whether the beats land in the same place every bar. A correct grid pulls this
     * down to a few milliseconds; a wrong one leaves the beats scattered across the bar and the
     * figure approaches a quarter of the beat, which is the average distance of random points from
     * a grid they have nothing to do with.
     *
     * <p>Measured as an angle rather than a number, so a beat sitting on the wrap-around between
     * one bar and the next is a small deviation and not an entire beat's worth.
     *
     * @return the mean absolute deviation from the grid in seconds, or {@code -1} when there is no
     *         tempo or too few beats to judge
     */
    public double gridDeviationSeconds() {
        double period = beatPeriod();
        if (period <= 0 || strongBeats.length < 2) {
            return -1;
        }

        double sine = 0;
        double cosine = 0;
        for (double beat : strongBeats) {
            double angle = 2 * Math.PI * (beat % period) / period;
            sine += Math.sin(angle);
            cosine += Math.cos(angle);
        }
        double centre = Math.atan2(sine, cosine);

        double total = 0;
        for (double beat : strongBeats) {
            double angle = 2 * Math.PI * (beat % period) / period;
            double difference = Math.IEEEremainder(angle - centre, 2 * Math.PI);
            total += Math.abs(difference);
        }
        return total / strongBeats.length / (2 * Math.PI) * period;
    }

    /**
     * @param version the version the current algorithm reports
     * @return whether this map was produced by that version and is still usable
     */
    public boolean matchesVersion(int version) {
        return analyzerVersion == version;
    }

    @Override
    public String toString() {
        return "Beatmap[" + String.format("%.1f", bpm) + " BPM, " + onsets.length + " onsets, "
                + strongBeats.length + " strong, " + String.format("%.1f", durationSeconds) + "s]";
    }
}
