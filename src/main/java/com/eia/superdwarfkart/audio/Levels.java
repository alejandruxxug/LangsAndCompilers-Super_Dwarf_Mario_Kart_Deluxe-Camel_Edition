package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The four numbers the level meters draw, published across the thread boundary.
 *
 * <p><strong>Per channel, never one combined figure.</strong> A single mixed level is the easy
 * mistake here and it destroys the only thing the meters are for: a hard-panned track has to make
 * the left and right bars visibly diverge. Left and right therefore carry their own RMS and their
 * own peak, four independent values, from the deinterleave right through to the canvas.
 *
 * <p>RMS and peak answer different questions and both are drawn. RMS is how loud the block
 * <em>sounded</em> and drives the bar's height; peak is the largest single sample in it and drives
 * the slow-falling cap above the bar, which is what makes a transient visible at all - a snare hit
 * is over long before the next frame is drawn.
 *
 * <p>Written on the playback thread and read on the interface thread, with no lock on either side.
 * Each value is one {@code float} smuggled through an {@link AtomicInteger} by
 * {@link Float#floatToIntBits}, so a reader can never see a half-written number and a writer never
 * waits. The four are not written atomically as a group, which is deliberate: a reader that catches
 * one frame's left channel beside the next frame's right is 20 ms out of step and nobody can see
 * it, whereas a lock on the playback thread is exactly what ground rule 4 forbids.
 */
public final class Levels {

    /**
     * Quietest level the meters show, in dBFS.
     *
     * <p>Everything below this reads as silence. Sixty decibels of range is the usual choice for a
     * mixing meter: wide enough that a fade-out keeps moving all the way down, narrow enough that
     * ordinary music occupies the top half of the bar rather than a sliver at the tip.
     */
    public static final float FLOOR_DB = -60f;

    private final AtomicInteger leftRms = new AtomicInteger();
    private final AtomicInteger rightRms = new AtomicInteger();
    private final AtomicInteger leftPeak = new AtomicInteger();
    private final AtomicInteger rightPeak = new AtomicInteger();

    /**
     * Publishes one block's measurements.
     *
     * <p>Called from the playback thread once per decoded block. Four stores, no allocation, no
     * blocking.
     *
     * @param leftRms   left channel root mean square, 0..1
     * @param rightRms  right channel root mean square, 0..1
     * @param leftPeak  largest absolute left sample in the block, 0..1
     * @param rightPeak largest absolute right sample in the block, 0..1
     */
    public void publish(float leftRms, float rightRms, float leftPeak, float rightPeak) {
        this.leftRms.set(Float.floatToIntBits(leftRms));
        this.rightRms.set(Float.floatToIntBits(rightRms));
        this.leftPeak.set(Float.floatToIntBits(leftPeak));
        this.rightPeak.set(Float.floatToIntBits(rightPeak));
    }

    /** @return left channel root mean square of the most recent block, 0..1 */
    public float leftRms() {
        return Float.intBitsToFloat(leftRms.get());
    }

    /** @return right channel root mean square of the most recent block, 0..1 */
    public float rightRms() {
        return Float.intBitsToFloat(rightRms.get());
    }

    /** @return largest absolute left sample in the most recent block, 0..1 */
    public float leftPeak() {
        return Float.intBitsToFloat(leftPeak.get());
    }

    /** @return largest absolute right sample in the most recent block, 0..1 */
    public float rightPeak() {
        return Float.intBitsToFloat(rightPeak.get());
    }

    /**
     * Drops every value to silence.
     *
     * <p>Called when playback stops or a new file is loaded, so the meters fall away instead of
     * freezing on the last block of the previous song.
     */
    public void reset() {
        publish(0, 0, 0, 0);
    }

    // ------------------------------------------------------------------
    // Display mapping - applied by the interface, once per frame
    // ------------------------------------------------------------------

    /**
     * Falls a displayed value towards a new reading: instantly up, slowly down.
     *
     * <p>{@code displayed = max(incoming, displayed * decay)}. Rising immediately is what makes a
     * hit register; falling over several frames is what makes it stay visible long enough to be
     * seen. Applied once per interface frame, never per audio block - the two run at different
     * rates and tying the fall to the audio rate would make it change with the buffer size.
     *
     * @param displayed what the meter is showing now
     * @param incoming  the latest reading
     * @return what the meter should show next frame
     */
    public static float decay(float displayed, float incoming) {
        return Math.max(incoming, displayed * AppConfig.PEAK_DECAY);
    }

    /**
     * Maps a linear amplitude onto the meter's scale.
     *
     * <p>Drawn linearly, a meter is useless: music sits around an RMS of 0.1 to 0.3, so the bar
     * would spend the whole song in its bottom quarter and every song would look the same. Loudness
     * is logarithmic, so the scale is too - {@value #FLOOR_DB} dBFS at the bottom, full scale at the
     * top.
     *
     * @param linear amplitude in 0..1, as published above
     * @return position on the bar in 0..1
     */
    public static float scale(float linear) {
        if (linear <= 0) {
            return 0;
        }
        double decibels = 20 * Math.log10(linear);
        return (float) Math.clamp((decibels - FLOOR_DB) / -FLOOR_DB, 0d, 1d);
    }
}
