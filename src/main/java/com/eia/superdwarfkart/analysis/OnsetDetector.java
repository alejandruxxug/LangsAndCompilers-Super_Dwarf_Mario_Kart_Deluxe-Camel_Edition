package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;

/**
 * Finds the instants where the music changes - the note attacks the game spawns entities on.
 *
 * <p>Three stages, kept separate because each is wrong in its own way and each is tested on its
 * own:
 *
 * <ol>
 *   <li><strong>Novelty.</strong> Every window is transformed and compared with the one before it.
 *       The measure is <em>spectral flux</em>: the sum of the increases in each frequency bin,
 *       with the decreases discarded. Energy appearing anywhere in the spectrum is an attack;
 *       energy disappearing is a note ending, which nobody claps on. Plain loudness would miss a
 *       hi-hat over a sustained chord entirely, because the total level barely moves.</li>
 *   <li><strong>Adaptive threshold.</strong> A fixed threshold finds every onset in the loud
 *       chorus and none in the quiet verse. This one is the local mean over about half a second
 *       either side, multiplied by a sensitivity, so what counts as a peak is judged against its
 *       own neighbourhood.</li>
 *   <li><strong>Peak picking.</strong> A local maximum above the threshold, with a minimum gap
 *       between accepted onsets: a single drum hit spreads over two or three windows and would
 *       otherwise be reported as a burst of onsets a few milliseconds apart.</li>
 * </ol>
 *
 * <p>No {@code javafx} import appears here, and none may: this is analysis, not presentation.
 */
public final class OnsetDetector {

    /**
     * Samples per analysis window.
     *
     * <p>1024 at 44.1 kHz is 23 ms - long enough to resolve the low end, short enough that two
     * drum hits an eighth note apart at 200 BPM still land in different windows.
     */
    public static final int WINDOW = 1024;

    /** Samples between the start of one window and the next: half a window, so they overlap. */
    public static final int HOP = 512;

    /**
     * How much of the novelty curve either side is averaged to decide what counts as a peak.
     *
     * <p>Half a second is a bar or two of context: wide enough that one loud hit cannot raise the
     * bar against its own neighbours, narrow enough to follow a track that changes dynamics.
     */
    public static final double THRESHOLD_RADIUS_SECONDS = 0.5;

    /**
     * How far above its neighbourhood a peak has to stand.
     *
     * <p>Raising it finds fewer, more confident onsets; lowering it finds more and eventually
     * starts finding texture.
     *
     * <p><strong>Measured, not guessed.</strong> Run across two real tracks - a dense electronic one
     * and a sparse percussive one - the tempo comes out the same at every setting, but how tightly
     * the detected beats sit on the grid does not:
     *
     * <pre>
     *   sensitivity   onsets/s   grid deviation
     *      1.5          5.7         44.2 ms
     *      2.0          5.0         35.3 ms
     *      3.0          3.2         20.6 ms
     *      4.0          1.8         13.8 ms
     * </pre>
     *
     * <p>Everything below 3 is buying extra onsets with accuracy: the surplus is texture rather than
     * attacks, and it drags the grid off the beat. Above 3 the curve flattens and the onset rate
     * falls far enough that the difference between the speed classes would nearly vanish - 200cc
     * places entities on the intermediate onsets, and at 1.8 per second there are hardly any left
     * once the strong beats are taken out.
     *
     * <p>3.0 is also comfortably above the ripple a sustained tone produces on its own (measured at
     * 2.78 times its own local mean), which is what stops a quiet ambient passage from generating a
     * course full of entities that correspond to nothing audible.
     */
    public static final double DEFAULT_SENSITIVITY = 3.0;

    /**
     * Closest two accepted onsets may be, in seconds.
     *
     * <p>A hundred milliseconds is 600 BPM worth of sixteenth notes - far faster than anything the
     * game places an entity on, and slow enough to collapse the two-or-three-window smear a single
     * attack produces into the one event it actually was.
     */
    public static final double MIN_GAP_SECONDS = 0.10;

    /**
     * Fraction of the whole track's mean novelty below which nothing is an onset.
     *
     * <p>The adaptive threshold is a multiple of the local mean, and in a near-silent passage the
     * local mean is near zero - so without a floor, dither and decoder noise clear the bar and the
     * track appears to have a beat during its own fade-out. Deliberately very low: real music in a
     * quiet intro sits far above a tenth of the track's average.
     */
    private static final double GLOBAL_FLOOR_FRACTION = 0.1;

    /**
     * How far the reported time is moved forward from the start of the window that detected it.
     *
     * <p><strong>Detection runs ahead of the sound, and by a knowable amount.</strong> An attack at
     * sample {@code s} falls inside exactly two windows, weighted {@code 1-w} and {@code w} by the
     * Hann taper. The flux peaks in the earlier of the two unless the attack sits in the last fifth
     * of it, so the window whose <em>start</em> gets reported began between 312 and 823 samples
     * before the attack. Adding the midpoint of that range leaves a residual of about &plusmn;6 ms
     * in either direction, which is a third of a video frame and inaudible.
     *
     * <p>Reporting the raw window start instead would put every entity in the rhythm game a
     * consistent 13 ms early - small enough to look correct in a screenshot and exactly the kind of
     * drift that makes a beat-synced game feel loose. {@code OnsetDetectorTest} measures the
     * residual against synthesised clicks rather than trusting the derivation above.
     */
    private static final int DETECTION_LEAD_SAMPLES = 568;

    private final Fft fft;

    /** The Hann taper, precomputed. Rectangular windows leak across every bin and swamp the flux. */
    private final float[] taper;

    private final float[] windowed;
    private final float[] spectrum;
    private final float[] previousSpectrum;

    /** Whether {@link #novelty} has seen a window yet, so the first one is not flux against zero. */
    private boolean primed;

    /** Prepares a detector with the standard window and hop. */
    public OnsetDetector() {
        this.fft = new Fft(WINDOW);
        this.windowed = new float[WINDOW];
        this.spectrum = new float[fft.bins()];
        this.previousSpectrum = new float[fft.bins()];

        this.taper = new float[WINDOW];
        for (int index = 0; index < WINDOW; index++) {
            // Periodic rather than symmetric: consecutive windows overlap by half, and the periodic
            // form is the one that sums to a constant across that overlap.
            taper[index] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * index / WINDOW));
        }
    }

    /**
     * Measures how much this window differs from the one before it.
     *
     * <p>Call once per hop, in order. The detector keeps the previous spectrum, so the sequence of
     * calls is the novelty curve.
     *
     * @param samples mono samples
     * @param offset  first sample of this window; {@link #WINDOW} samples must be readable from here
     * @return the rectified spectral flux, zero for the first window
     */
    public float novelty(float[] samples, int offset) {
        for (int index = 0; index < WINDOW; index++) {
            windowed[index] = samples[offset + index] * taper[index];
        }
        fft.magnitudes(windowed, 0, spectrum);

        float flux = 0;
        if (primed) {
            for (int bin = 0; bin < spectrum.length; bin++) {
                float rise = spectrum[bin] - previousSpectrum[bin];
                if (rise > 0) {
                    flux += rise;
                }
            }
        }
        System.arraycopy(spectrum, 0, previousSpectrum, 0, spectrum.length);
        primed = true;
        return flux;
    }

    /** Forgets the previous window, so the next call starts a fresh curve. */
    public void reset() {
        primed = false;
        java.util.Arrays.fill(previousSpectrum, 0f);
    }

    // ------------------------------------------------------------------
    // Turning the curve into times
    // ------------------------------------------------------------------

    /**
     * Picks the onsets out of a finished novelty curve.
     *
     * @param novelty     the curve, one value per hop
     * @param count       how many entries of it are valid
     * @param sensitivity how far above its neighbourhood a peak must stand; see
     *                    {@link #DEFAULT_SENSITIVITY}
     * @return the onset times in seconds, ascending, never {@code null}
     */
    public static double[] pickPeaks(float[] novelty, int count, double sensitivity) {
        if (novelty == null || count < 3) {
            return new double[0];
        }

        double[] localMean = localMeans(novelty, count);
        double floor = GLOBAL_FLOOR_FRACTION * mean(novelty, count);
        double minimumGap = MIN_GAP_SECONDS;

        double[] onsets = new double[count];
        int found = 0;
        double lastAccepted = Double.NEGATIVE_INFINITY;

        // The ends are skipped: a local maximum needs a neighbour on each side, and an onset in the
        // first or last 12 ms of a track is not worth a special case.
        for (int frame = 1; frame < count - 1; frame++) {
            float value = novelty[frame];
            if (value <= novelty[frame - 1] || value < novelty[frame + 1]) {
                continue;
            }
            if (value <= sensitivity * localMean[frame] || value <= floor) {
                continue;
            }
            double time = timeOf(frame);
            if (time - lastAccepted < minimumGap) {
                continue;
            }
            onsets[found++] = time;
            lastAccepted = time;
        }
        return java.util.Arrays.copyOf(onsets, found);
    }

    /**
     * Averages the curve over a window either side of every point.
     *
     * <p>Computed from a running sum rather than by re-adding the neighbourhood at each point,
     * which turns an O(n&#215;radius) pass into an O(n) one - at 86 points a second and a radius of
     * 43, that is the difference between one pass and eighty-six.
     *
     * @param novelty the curve
     * @param count   how many entries are valid
     * @return the local mean at each point
     */
    private static double[] localMeans(float[] novelty, int count) {
        int radius = (int) Math.round(THRESHOLD_RADIUS_SECONDS / frameSeconds());
        double[] prefix = new double[count + 1];
        for (int index = 0; index < count; index++) {
            prefix[index + 1] = prefix[index] + novelty[index];
        }

        double[] means = new double[count];
        for (int index = 0; index < count; index++) {
            int from = Math.max(0, index - radius);
            int to = Math.min(count, index + radius + 1);
            means[index] = (prefix[to] - prefix[from]) / (to - from);
        }
        return means;
    }

    /**
     * @param novelty the curve
     * @param count   how many entries are valid
     * @return the mean of the whole curve
     */
    private static double mean(float[] novelty, int count) {
        if (count <= 0) {
            return 0;
        }
        double total = 0;
        for (int index = 0; index < count; index++) {
            total += novelty[index];
        }
        return total / count;
    }

    /** @return how much of the track one hop covers, in seconds */
    public static double frameSeconds() {
        return HOP / (double) AppConfig.SAMPLE_RATE;
    }

    /**
     * Converts a position in the novelty curve into a position in the track.
     *
     * <p>Compensated by {@link #DETECTION_LEAD_SAMPLES}, so the time returned is when the attack
     * was heard rather than when the window that noticed it began.
     *
     * @param frame index into the novelty curve
     * @return the time in the track, in seconds
     */
    public static double timeOf(int frame) {
        return (frame * (double) HOP + DETECTION_LEAD_SAMPLES) / AppConfig.SAMPLE_RATE;
    }
}
