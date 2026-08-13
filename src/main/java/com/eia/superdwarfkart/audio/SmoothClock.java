package com.eia.superdwarfkart.audio;

/**
 * A smooth reading of the playback position, locked to the real one.
 *
 * <p><strong>The problem this solves is visible and nothing else fixes it.</strong>
 * {@link AudioSource#position()} reports what the sound card has actually rendered, which is the
 * only honest clock there is - but a card reports it in whole buffers. Read sixty times a second it
 * does not advance sixty times a second: it sits still for several frames and then jumps. Anything
 * drawn straight off it therefore moves in visible steps, and a scrolling road drawn that way
 * stutters no matter how fast the machine is. It reads as a slow game, and it is not one.
 *
 * <p>So the reading here free-runs on wall time between updates and is <strong>continuously pulled
 * back onto the audio position</strong>, a fraction of the error per frame. That is emphatically
 * not the accumulated frame delta the project forbids: an accumulator has nothing to correct it and
 * is a beat out within a minute, whereas this one cannot be more than a few milliseconds from the
 * card at any point, because it is being told the card's answer every frame and moves towards it
 * every frame. Take the audio away and it stops - it has no opinion of its own.
 *
 * <p>Three cases it has to survive, all of which turn up in ordinary use:
 *
 * <ul>
 *   <li><strong>Paused.</strong> The reading snaps to the position and stays there. A clock that
 *       kept sliding while the music was stopped would drive the road on past silence.</li>
 *   <li><strong>Seeking, or a new song.</strong> The error is suddenly large; correcting gently
 *       would crawl to the new position over several seconds, so past
 *       {@value #RESYNC_SECONDS} seconds it snaps instead.</li>
 *   <li><strong>A stalled frame timer</strong> - a minimised window, a machine that went to sleep.
 *       One wall-time step is capped at {@value #MAX_STEP_SECONDS} seconds, so the reading cannot
 *       be thrown minutes into the track by a gap in the frames.</li>
 * </ul>
 */
public final class SmoothClock {

    /** Error past which the reading snaps rather than easing: a seek, or a different song. */
    private static final double RESYNC_SECONDS = 0.25;

    /** Largest wall-time step honoured in one update, so a stalled timer cannot run the clock away. */
    private static final double MAX_STEP_SECONDS = 0.1;

    /**
     * Share of the remaining error taken out per update.
     *
     * <p>Small enough that the correction is invisible - at sixty frames a second the reading
     * closes on the card in about a fifth of a second - and large enough that it can never sit
     * measurably behind.
     */
    private static final double CORRECTION_PER_UPDATE = 0.08;

    private double reading;
    private long lastNanos;
    private boolean started;

    /**
     * How many times the reading has given up easing and jumped.
     *
     * <p>Counted rather than merely done, because a snap is a visible lurch and the three legitimate
     * causes - a seek, a new song, a stalled timer - are all things the user did. One appearing
     * during ordinary playback means the reading is being asked to track something that is not a
     * playback position, and no amount of tuning the correction rate will help.
     */
    private long snaps;

    /**
     * Advances the reading.
     *
     * @param positionSeconds where the sound card says it has got to
     * @param playing         whether audio is actually being rendered
     * @return a smooth position, in seconds, never more than a few milliseconds from the card
     */
    public double advance(double positionSeconds, boolean playing) {
        long now = System.nanoTime();
        double target = Math.max(0, positionSeconds);

        if (!playing || !started) {
            started = true;
            lastNanos = now;
            reading = target;
            return reading;
        }

        double step = Math.clamp((now - lastNanos) / 1_000_000_000d, 0d, MAX_STEP_SECONDS);
        lastNanos = now;
        reading += step;

        double error = target - reading;
        if (Math.abs(error) > RESYNC_SECONDS) {
            reading = target;
            snaps++;
        } else {
            reading += error * CORRECTION_PER_UPDATE;
        }
        return reading;
    }

    /**
     * Forgets where it had got to, so the next update starts from the position it is given.
     *
     * <p>For a song change, where easing from the old song's position into the new one's would
     * animate a journey that did not happen.
     */
    public void reset() {
        started = false;
        reading = 0;
    }

    /** @return the reading, without advancing it */
    public double seconds() {
        return reading;
    }

    /** @return how many times the reading has snapped rather than eased, since it was built */
    public long snaps() {
        return snaps;
    }
}
