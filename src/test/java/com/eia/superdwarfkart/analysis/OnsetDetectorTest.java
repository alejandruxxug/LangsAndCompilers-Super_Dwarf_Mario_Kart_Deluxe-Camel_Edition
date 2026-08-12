package com.eia.superdwarfkart.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding note attacks, and finding them at the right instant.
 *
 * <p><strong>Accuracy is the point of this class, not merely the count.</strong> The rhythm game
 * spawns an entity so that it arrives at the player exactly when an onset sounds, and a detector
 * that finds every beat 20 ms early produces a course that is wrong by a fifth of a beat all the
 * way through - which looks completely correct in a screenshot and feels loose to play. The
 * measurement below is what pins {@code DETECTION_LEAD_SAMPLES}.
 */
class OnsetDetectorTest {

    /**
     * Runs the detector over a whole signal.
     *
     * @param samples     the audio
     * @param sensitivity how far above its neighbourhood a peak must stand
     * @return the onset times in seconds
     */
    private static double[] detect(float[] samples, double sensitivity) {
        OnsetDetector detector = new OnsetDetector();
        int windows = (samples.length - OnsetDetector.WINDOW) / OnsetDetector.HOP + 1;
        float[] novelty = new float[Math.max(1, windows)];
        int count = 0;
        for (int start = 0; start + OnsetDetector.WINDOW <= samples.length;
             start += OnsetDetector.HOP) {
            novelty[count++] = detector.novelty(samples, start);
        }
        return OnsetDetector.pickPeaks(novelty, count, sensitivity);
    }

    @Test
    @DisplayName("every click is found, and none is invented in between")
    void findsExactlyTheClicks() {
        double[] clicks = ClickTrack.beatsAt(16, 120, 0.4);
        float[] samples = ClickTrack.mono(9, clicks);

        double[] onsets = detect(samples, OnsetDetector.DEFAULT_SENSITIVITY);

        assertEquals(clicks.length, onsets.length,
                "expected one onset per click but got " + onsets.length
                        + "; a surplus means the steady drone is being read as change");
    }

    @Test
    @DisplayName("the reported instant is the instant the click sounded, within a few milliseconds")
    void onsetTimesLandOnTheClicks() {
        double[] clicks = ClickTrack.beatsAt(24, 132, 0.35);
        float[] samples = ClickTrack.mono(12, clicks);

        double[] onsets = detect(samples, OnsetDetector.DEFAULT_SENSITIVITY);
        assertEquals(clicks.length, onsets.length, "the count has to match before timing means anything");

        double worst = 0;
        double bias = 0;
        for (int index = 0; index < clicks.length; index++) {
            double error = onsets[index] - clicks[index];
            bias += error;
            worst = Math.max(worst, Math.abs(error));
        }
        bias /= clicks.length;

        // A whole analysis hop is 11.6 ms and a video frame is 16.7 ms. Staying inside one hop
        // means no entity in the game can be placed on the wrong side of a frame.
        assertTrue(worst < 0.012,
                String.format("worst error was %.1f ms; onsets are not landing on the attack", worst * 1000));
        // The bias is what a compensation constant can fix and a tolerance cannot: a consistent
        // lead or lag shifts the entire course rather than jittering it.
        assertTrue(Math.abs(bias) < 0.005,
                String.format("mean error was %+.1f ms, a systematic offset; "
                        + "DETECTION_LEAD_SAMPLES needs adjusting by that much", bias * 1000));
    }

    @Test
    @DisplayName("two attacks closer than the minimum gap are one onset, not a burst")
    void collapsesOnsetsInsideTheMinimumGap() {
        // 30 ms apart: a single hit smeared across windows, not two events.
        float[] samples = ClickTrack.mono(3, new double[] {1.0, 1.03});

        double[] onsets = detect(samples, OnsetDetector.DEFAULT_SENSITIVITY);

        assertEquals(1, onsets.length,
                "a smeared attack must collapse to the one event it was");
    }

    @Test
    @DisplayName("attacks further apart than the minimum gap stay separate")
    void keepsOnsetsOutsideTheMinimumGap() {
        float[] samples = ClickTrack.mono(3, new double[] {1.0, 1.2});

        double[] onsets = detect(samples, OnsetDetector.DEFAULT_SENSITIVITY);

        assertEquals(2, onsets.length);
    }

    @Test
    @DisplayName("a steady tone has no onsets, however loud it is")
    void steadyToneProducesNothing() {
        float[] samples = ClickTrack.mono(5, new double[0]);

        double[] onsets = detect(samples, OnsetDetector.DEFAULT_SENSITIVITY);

        // Only the first moment of the drone is a change; everything after it is the same spectrum.
        assertTrue(onsets.length <= 1,
                "a sustained note produced " + onsets.length + " onsets - this is measuring level, "
                        + "not change");
    }

    @Test
    @DisplayName("silence produces nothing rather than noise")
    void silenceProducesNothing() {
        double[] onsets = detect(new float[44100], OnsetDetector.DEFAULT_SENSITIVITY);

        assertEquals(0, onsets.length);
    }

    @Test
    @DisplayName("a curve too short to have a peak is handled rather than indexed off the end")
    void shortCurvesAreSafe() {
        assertEquals(0, OnsetDetector.pickPeaks(new float[] {1, 2}, 2, 1.5).length);
        assertEquals(0, OnsetDetector.pickPeaks(null, 0, 1.5).length);
        assertEquals(0, OnsetDetector.pickPeaks(new float[8], 0, 1.5).length);
    }

    @Test
    @DisplayName("raising the sensitivity finds fewer onsets, never more")
    void sensitivityFiltersRatherThanShifts() {
        double[] clicks = ClickTrack.beatsAt(20, 150, 0.3);
        float[] samples = ClickTrack.mono(9, clicks);

        int loose = detect(samples, 1.0).length;
        int standard = detect(samples, OnsetDetector.DEFAULT_SENSITIVITY).length;
        int strict = detect(samples, 6.0).length;

        assertTrue(loose >= standard, "a lower threshold cannot find fewer peaks");
        assertTrue(standard >= strict, "a higher threshold cannot find more peaks");
    }

    @Test
    @DisplayName("the first window is not an onset against a spectrum of zeroes")
    void firstWindowIsNotAnOnset() {
        OnsetDetector detector = new OnsetDetector();
        float[] loud = new float[OnsetDetector.WINDOW];
        java.util.Arrays.fill(loud, 0.9f);

        assertEquals(0f, detector.novelty(loud, 0),
                "the opening window has nothing to be different from");
    }

    @Test
    @DisplayName("resetting forgets the previous window")
    void resetClearsTheHistory() {
        OnsetDetector detector = new OnsetDetector();
        float[] loud = new float[OnsetDetector.WINDOW];
        java.util.Arrays.fill(loud, 0.9f);
        detector.novelty(loud, 0);

        detector.reset();

        assertEquals(0f, detector.novelty(loud, 0));
    }

    @Test
    @DisplayName("times advance by exactly one hop per frame")
    void timesAdvanceByOneHop() {
        double step = OnsetDetector.timeOf(101) - OnsetDetector.timeOf(100);

        assertEquals(OnsetDetector.frameSeconds(), step, 1e-9);
    }
}
