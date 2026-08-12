package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queries the rhythm game will make sixty times a second.
 *
 * <p>These are boundary tests rather than algorithm tests. An off-by-one in
 * {@link Beatmap#firstOnsetAtOrAfter} spawns an entity a beat late or not at all, and the symptom
 * would appear in M7 as a game that feels wrong rather than as anything that looks like a bug here.
 */
class BeatmapTest {

    private static Beatmap map(double bpm, double[] onsets, double[] strong) {
        return new Beatmap("hash", AppConfig.ANALYZER_VERSION, 10, bpm, onsets, strong);
    }

    @Test
    @DisplayName("a series out of order is refused rather than silently searched wrongly")
    void refusesUnorderedSeries() {
        assertThrows(IllegalArgumentException.class,
                () -> map(120, new double[] {1, 3, 2}, new double[0]));
        assertThrows(IllegalArgumentException.class,
                () -> map(120, new double[0], new double[] {5, 1}));
    }

    @Test
    @DisplayName("the arrays are copied both ways, so a cached course cannot be edited from outside")
    void arraysAreCopiedInAndOut() {
        double[] source = {1, 2, 3};
        Beatmap beatmap = map(120, source, new double[] {2});

        source[0] = 99;
        assertEquals(1, beatmap.onsetAt(0), "the constructor kept the caller's array");

        double[] handed = beatmap.onsets();
        handed[0] = 42;
        assertEquals(1, beatmap.onsetAt(0), "the accessor handed out the live array");
    }

    @Test
    @DisplayName("the empty beatmap answers every query without a guard at the call site")
    void emptyBeatmapIsSafe() {
        Beatmap empty = Beatmap.EMPTY;

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.onsetCount());
        assertEquals(0, empty.bpm());
        assertEquals(0, empty.beatPeriod());
        assertEquals(0, empty.firstOnsetAtOrAfter(5));
        assertEquals(-1, empty.nextStrongBeatAfter(5));
        assertEquals(-1, empty.lastStrongBeatAtOrBefore(5));
        assertEquals(0, empty.onsetsBetween(0, 100));
        assertEquals(-1, empty.gridDeviationSeconds());
    }

    @Test
    @DisplayName("the search finds the first entry at or after the time, including an exact hit")
    void findsTheFirstOnsetAtOrAfter() {
        Beatmap beatmap = map(120, new double[] {1, 2, 3, 4}, new double[0]);

        assertEquals(0, beatmap.firstOnsetAtOrAfter(0.5));
        assertEquals(0, beatmap.firstOnsetAtOrAfter(1.0), "an exact hit is at or after itself");
        assertEquals(1, beatmap.firstOnsetAtOrAfter(1.5));
        assertEquals(3, beatmap.firstOnsetAtOrAfter(4.0));
        assertEquals(4, beatmap.firstOnsetAtOrAfter(4.5), "past the end is the end");
    }

    @Test
    @DisplayName("duplicate times resolve to the first of them, so no event is skipped")
    void duplicateTimesResolveToTheFirst() {
        Beatmap beatmap = map(120, new double[] {1, 2, 2, 2, 3}, new double[0]);

        assertEquals(1, beatmap.firstOnsetAtOrAfter(2.0));
    }

    @Test
    @DisplayName("the next strong beat is strictly after, so a beat cannot fire twice")
    void nextStrongBeatIsStrictlyAfter() {
        Beatmap beatmap = map(120, new double[] {1, 2, 3}, new double[] {1, 2, 3});

        assertEquals(2, beatmap.nextStrongBeatAfter(1));
        assertEquals(2, beatmap.nextStrongBeatAfter(1.5));
        assertEquals(-1, beatmap.nextStrongBeatAfter(3), "nothing follows the last beat");
    }

    @Test
    @DisplayName("the last strong beat is the one the pulse should still be showing")
    void lastStrongBeatIncludesTheCurrentInstant() {
        Beatmap beatmap = map(120, new double[] {1, 2, 3}, new double[] {1, 2, 3});

        assertEquals(-1, beatmap.lastStrongBeatAtOrBefore(0.5), "nothing has happened yet");
        assertEquals(1, beatmap.lastStrongBeatAtOrBefore(1), "the beat happening now counts");
        assertEquals(1, beatmap.lastStrongBeatAtOrBefore(1.9));
        assertEquals(3, beatmap.lastStrongBeatAtOrBefore(99));
    }

    @Test
    @DisplayName("counting a window is half open, so adjacent windows neither drop nor double count")
    void countingAWindowIsHalfOpen() {
        Beatmap beatmap = map(120, new double[] {1, 2, 3, 4}, new double[0]);

        assertEquals(2, beatmap.onsetsBetween(1, 3), "1 and 2 are in, 3 is not");
        assertEquals(2, beatmap.onsetsBetween(3, 5));
        assertEquals(4, beatmap.onsetsBetween(0, 5));
        assertEquals(0, beatmap.onsetsBetween(3, 3), "an empty window holds nothing");
        assertEquals(0, beatmap.onsetsBetween(5, 1), "a backwards window holds nothing");
    }

    @Test
    @DisplayName("beats exactly on the grid deviate from it by nothing")
    void perfectGridHasNoDeviation() {
        double[] beats = ClickTrack.beatsAt(20, 120, 0.25);
        Beatmap beatmap = new Beatmap("h", 1, 12, 120, beats, beats);

        assertEquals(0, beatmap.gridDeviationSeconds(), 1e-9);
    }

    @Test
    @DisplayName("beats scattered across the bar deviate by about a quarter of it")
    void scatteredBeatsDeviateByAQuarterBeat() {
        // The reading that says the tempo is wrong: points unrelated to a grid sit on average a
        // quarter of a period away from it.
        java.util.Random random = new java.util.Random(7);
        double[] scattered = new double[400];
        for (int index = 0; index < scattered.length; index++) {
            scattered[index] = index * 0.5 + random.nextDouble() * 0.5;
        }
        java.util.Arrays.sort(scattered);
        Beatmap beatmap = new Beatmap("h", 1, 200, 120, scattered, scattered);

        double deviation = beatmap.gridDeviationSeconds();

        assertEquals(0.125, deviation, 0.02,
                "a quarter of a 0.5s beat is 125 ms; anything much lower would mean this "
                        + "measure cannot tell a real grid from a coincidence");
    }

    @Test
    @DisplayName("a beat sitting on the bar line is a small deviation, not a whole beat")
    void deviationIsMeasuredAroundTheWrap() {
        // Half the beats a hair before the bar line and half a hair after it. Averaged as plain
        // numbers these come out half a beat apart; as angles they are milliseconds apart.
        double[] beats = new double[40];
        for (int index = 0; index < beats.length; index++) {
            beats[index] = index * 0.5 + (index % 2 == 0 ? 0.002 : -0.002);
        }
        java.util.Arrays.sort(beats);
        Beatmap beatmap = new Beatmap("h", 1, 20, 120, beats, beats);

        assertEquals(0.002, beatmap.gridDeviationSeconds(), 0.001);
    }

    @Test
    @DisplayName("a map made by another analyser version is recognisable as stale")
    void versionIsCheckable() {
        Beatmap beatmap = new Beatmap("h", 3, 10, 120, new double[] {1}, new double[] {1});

        assertTrue(beatmap.matchesVersion(3));
        assertNotEquals(true, beatmap.matchesVersion(4));
    }

    @Test
    @DisplayName("a tempo gives a beat period, and no tempo gives none")
    void beatPeriodFollowsTheTempo() {
        assertEquals(0.5, map(120, new double[0], new double[0]).beatPeriod(), 1e-9);
        assertEquals(0, map(0, new double[0], new double[0]).beatPeriod());
        assertTrue(map(120, new double[0], new double[0]).hasTempo());
        assertEquals(false, map(0, new double[0], new double[0]).hasTempo());
    }
}
