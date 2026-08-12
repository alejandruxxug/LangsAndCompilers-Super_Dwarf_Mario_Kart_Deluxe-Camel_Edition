package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.audio.AudioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole chain, on files whose tempo is known because the test wrote them.
 *
 * <p>Decode, mono sum, window, transform, novelty, threshold, peak pick, interval histogram, phase,
 * grid. Every stage has its own test elsewhere; this is the one that catches the stages being
 * individually right and jointly wrong - a sample rate assumed in one place and measured in
 * another, a time in frames used where a time in seconds was meant.
 */
class BeatmapAnalyzerTest {

    /**
     * Writes a track that ticks at a fixed tempo.
     *
     * @param directory where to write it
     * @param bpm       the tempo
     * @param seconds   how long the track runs
     * @return the file written
     */
    private static Path trackAt(Path directory, double bpm, double seconds) throws IOException {
        int beats = (int) (seconds * bpm / 60) - 1;
        return ClickTrack.writeWav(directory.resolve((int) bpm + "bpm.wav"), seconds,
                ClickTrack.beatsAt(beats, bpm, 0.35));
    }

    @Test
    @DisplayName("the tempo of a written track is the tempo that comes back")
    void findsTheTempoItWasGiven(@TempDir Path directory) throws IOException {
        for (double bpm : new double[] {90, 120, 140, 175}) {
            Path file = trackAt(directory, bpm, 20);

            Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "hash", null);

            assertEquals(bpm, beatmap.bpm(), 1.0,
                    "wrote a " + bpm + " BPM track and read back " + beatmap.bpm());
        }
    }

    @Test
    @DisplayName("the beats come back sitting on the grid, not merely near it")
    void beatsLandOnTheGrid(@TempDir Path directory) throws IOException {
        Path file = trackAt(directory, 128, 20);

        Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "hash", null);

        assertTrue(beatmap.strongBeatCount() > 30,
                "only " + beatmap.strongBeatCount() + " of about 41 beats were found on the grid");
        // One analysis hop is 11.6 ms and no onset can be located more precisely than that, so the
        // floor here is the detector's own resolution rather than anything about the grid. What
        // this rules out is a phase error, which would land near a quarter of the 469 ms beat.
        double deviation = beatmap.gridDeviationSeconds();
        assertTrue(deviation < 0.020,
                String.format("beats sit %.1f ms off the grid, far more than the %.1f ms the "
                                + "analysis hop explains; the tempo is right but the phase is not, "
                                + "which puts the whole course out by the same amount",
                        deviation * 1000, OnsetDetector.frameSeconds() * 1000));
    }

    @Test
    @DisplayName("the onsets are the clicks that were written, at the times they were written")
    void onsetsMatchTheWrittenClicks(@TempDir Path directory) throws IOException {
        double[] clicks = ClickTrack.beatsAt(24, 120, 0.4);
        Path file = ClickTrack.writeWav(directory.resolve("known.wav"), 13, clicks);

        Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "hash", null);

        assertEquals(clicks.length, beatmap.onsetCount());
        for (int index = 0; index < clicks.length; index++) {
            assertEquals(clicks[index], beatmap.onsetAt(index), 0.012,
                    "onset " + index + " is not where the click was written");
        }
    }

    @Test
    @DisplayName("the measured length matches the file, so times mean the same as the playback clock")
    void reportsTheTrackLength(@TempDir Path directory) throws IOException {
        Path file = trackAt(directory, 120, 15);

        Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "hash", null);

        assertEquals(15.0, beatmap.durationSeconds(), 0.05);
    }

    @Test
    @DisplayName("silence analyses to an empty map rather than to a crash or to invented beats")
    void silenceGivesAnEmptyMap(@TempDir Path directory) throws IOException {
        Path file = ClickTrack.writeWav(directory.resolve("quiet.wav"), 5, new double[0]);
        // A drone with no attacks: the file is not silent, but nothing in it is an event.
        Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "hash", null);

        assertTrue(beatmap.onsetCount() <= 1,
                "found " + beatmap.onsetCount() + " onsets in a track with no attacks in it");
        assertTrue(beatmap.strongBeatCount() <= 1);
    }

    @Test
    @DisplayName("a track shorter than one analysis window is handled rather than indexed off the end")
    void veryShortTrackIsSafe(@TempDir Path directory) throws IOException {
        Path file = ClickTrack.writeWav(directory.resolve("blink.wav"), 0.01, new double[0]);

        Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "hash", null);

        assertTrue(beatmap.isEmpty());
        assertEquals(0, beatmap.bpm());
    }

    @Test
    @DisplayName("the result carries the hash and version it will be cached under")
    void resultCarriesItsIdentity(@TempDir Path directory) throws IOException {
        Path file = trackAt(directory, 120, 6);

        Beatmap beatmap = new BeatmapAnalyzer().analyze(file, "the-hash", null);

        assertEquals("the-hash", beatmap.sourceHash());
        assertEquals(AppConfig.ANALYZER_VERSION, beatmap.analyzerVersion());
        assertTrue(beatmap.matchesVersion(AppConfig.ANALYZER_VERSION));
    }

    @Test
    @DisplayName("progress runs forward from near zero to exactly one")
    void progressAdvancesToCompletion(@TempDir Path directory) throws IOException {
        Path file = trackAt(directory, 120, 12);
        List<Double> reported = new ArrayList<>();

        new BeatmapAnalyzer().analyze(file, "hash", reported::add);

        assertTrue(reported.size() > 2, "progress was reported " + reported.size() + " times");
        assertEquals(1.0, reported.get(reported.size() - 1),
                "the last thing a progress bar hears has to be that it is finished");
        for (int index = 1; index < reported.size(); index++) {
            assertTrue(reported.get(index) >= reported.get(index - 1),
                    "progress went backwards at report " + index);
        }
    }

    @Test
    @DisplayName("an unreadable file is refused with a message naming it")
    void unreadableFileIsRefused(@TempDir Path directory) throws IOException {
        Path text = Files.writeString(directory.resolve("sleeve-notes.txt"), "not audio");

        assertThrows(AudioException.class,
                () -> new BeatmapAnalyzer().analyze(text, "hash", null));
        assertThrows(AudioException.class,
                () -> new BeatmapAnalyzer().analyze(directory.resolve("absent.wav"), "hash", null));
    }

    @Test
    @DisplayName("an interrupted analysis abandons the work rather than returning half a course")
    void interruptionCancelsRatherThanTruncating(@TempDir Path directory) throws IOException {
        Path file = trackAt(directory, 120, 20);
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,
                    () -> new BeatmapAnalyzer().analyze(file, "hash", null),
                    "a truncated analysis would be cached and used as if it were complete");
        } finally {
            // Clearing it, so the flag cannot leak into whatever test runs next on this thread.
            Thread.interrupted();
        }
    }

    // ------------------------------------------------------------------
    // The two stages that can be checked without any audio at all
    // ------------------------------------------------------------------

    @Test
    @DisplayName("evenly spaced onsets give exactly their own tempo")
    void tempoOfPerfectlySpacedOnsets() {
        assertEquals(120, BeatmapAnalyzer.estimateBpm(ClickTrack.beatsAt(40, 120, 0)), 0.5);
        assertEquals(95, BeatmapAnalyzer.estimateBpm(ClickTrack.beatsAt(40, 95, 0)), 0.5);
        assertEquals(174, BeatmapAnalyzer.estimateBpm(ClickTrack.beatsAt(40, 174, 0)), 0.5);
    }

    @Test
    @DisplayName("a tempo outside the range folds onto its own double or half")
    void tempoFoldsIntoTheConsideredRange() {
        // 60 BPM is below the range and 240 above it; both are the same pulse as 120 and both
        // must vote with it rather than being discarded or landing somewhere unrelated.
        assertEquals(120, BeatmapAnalyzer.estimateBpm(ClickTrack.beatsAt(40, 60, 0)), 0.5);
        assertEquals(120, BeatmapAnalyzer.estimateBpm(ClickTrack.beatsAt(40, 240, 0)), 0.5);
    }

    @Test
    @DisplayName("a few stray onsets do not move the tempo")
    void strayOnsetsDoNotMoveTheTempo() {
        double[] beats = ClickTrack.beatsAt(40, 120, 0);
        double[] withStrays = new double[beats.length + 4];
        System.arraycopy(beats, 0, withStrays, 0, beats.length);
        withStrays[beats.length] = 3.13;
        withStrays[beats.length + 1] = 7.77;
        withStrays[beats.length + 2] = 11.31;
        withStrays[beats.length + 3] = 15.05;
        java.util.Arrays.sort(withStrays);

        assertEquals(120, BeatmapAnalyzer.estimateBpm(withStrays), 1.0,
                "a histogram exists so that a handful of wrong onsets cannot outvote the rest");
    }

    @Test
    @DisplayName("too few onsets to judge gives no tempo rather than a guess")
    void tooFewOnsetsGivesNoTempo() {
        assertEquals(0, BeatmapAnalyzer.estimateBpm(new double[] {1, 2}));
        assertEquals(0, BeatmapAnalyzer.estimateBpm(new double[0]));
        assertEquals(0, BeatmapAnalyzer.estimateBpm(null));
    }

    @Test
    @DisplayName("the grid picks out the on-beat onsets and leaves the rest as intermediates")
    void gridSeparatesBeatsFromIntermediates() {
        double[] beats = ClickTrack.beatsAt(20, 120, 0.25);
        List<Double> all = new ArrayList<>();
        for (int index = 0; index < beats.length; index++) {
            all.add(beats[index]);
            // An off-beat event halfway between two beats: a real onset, but not a beat. Sparse on
            // purpose - fill in every gap and half the onsets sit on each phase, which makes the
            // grid genuinely ambiguous rather than merely hard to find.
            if (index % 3 == 0) {
                all.add(beats[index] + 0.25);
            }
        }
        double[] onsets = all.stream().mapToDouble(Double::doubleValue).sorted().toArray();

        double[] strong = BeatmapAnalyzer.strongBeats(onsets, 120, 11);

        assertEquals(beats.length, strong.length,
                "every beat should be picked out and no off-beat event with it");
        for (int index = 0; index < beats.length; index++) {
            assertEquals(beats[index], strong[index], 1e-6);
        }
    }

    @Test
    @DisplayName("a bar with nothing in it produces no beat, because nothing can be heard there")
    void silentBarsProduceNoBeats() {
        // Beats 8 through 11 removed: the grid runs through them, but the audio has nothing there.
        List<Double> kept = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            if (index < 8 || index > 11) {
                kept.add(0.25 + index * 0.5);
            }
        }
        double[] onsets = kept.stream().mapToDouble(Double::doubleValue).toArray();

        double[] strong = BeatmapAnalyzer.strongBeats(onsets, 120, 12.5);

        assertEquals(onsets.length, strong.length,
                "the gap must stay a gap; entities placed on grid points rather than on onsets "
                        + "would spawn over four bars of silence");
    }

    @Test
    @DisplayName("no tempo means no grid, rather than a grid at some default")
    void noTempoGivesNoBeats() {
        assertEquals(0, BeatmapAnalyzer.strongBeats(new double[] {1, 2, 3}, 0, 10).length);
        assertEquals(0, BeatmapAnalyzer.strongBeats(new double[0], 120, 10).length);
        assertEquals(0, BeatmapAnalyzer.strongBeats(null, 120, 10).length);
    }

    @Test
    @DisplayName("the strong beats come back in order and are all real onsets")
    void strongBeatsAreOnsetsInOrder() {
        double[] onsets = ClickTrack.beatsAt(30, 128, 0.1);

        double[] strong = BeatmapAnalyzer.strongBeats(onsets, 128, 15);

        List<Double> known = java.util.Arrays.stream(onsets).boxed().toList();
        for (int index = 0; index < strong.length; index++) {
            assertTrue(known.contains(strong[index]),
                    "a strong beat must be an onset that happened, not a point on the grid");
            if (index > 0) {
                assertTrue(strong[index] > strong[index - 1], "strong beats came back out of order");
            }
        }
    }
}
