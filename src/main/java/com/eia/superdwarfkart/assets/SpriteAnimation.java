package com.eia.superdwarfkart.assets;

import javafx.geometry.Rectangle2D;

import java.util.Objects;

/**
 * A sheet plus the rate it plays at: which frame is showing at a given moment.
 *
 * <p>Deliberately <em>not</em> named {@code Animation} - that collides with
 * {@link javafx.animation.Animation} and would force fully qualified names all over the
 * interface code.
 *
 * <p>This holds no timer and no state. It answers "which frame at time t", and the caller
 * supplies t. That keeps it usable from the shared interface ticker, from the game loop driven by
 * the audio position, and from a test, without three different notions of time getting involved.
 * The game's timing has to come from {@code audioSource.position()} rather than accumulated frame
 * deltas, and an animation that counted its own ticks would quietly break that rule.
 */
public final class SpriteAnimation {

    /** Rate used when nothing else is specified: brisk enough to read, slow enough to look drawn. */
    public static final double DEFAULT_FPS = 12;

    private final SpriteSheet sheet;
    private final double framesPerSecond;
    private final boolean looping;

    private SpriteAnimation(SpriteSheet sheet, double framesPerSecond, boolean looping) {
        this.sheet = Objects.requireNonNull(sheet, "sheet must not be null");
        if (!(framesPerSecond > 0)) {
            throw new IllegalArgumentException(
                    "Frames per second must be positive, got " + framesPerSecond);
        }
        this.framesPerSecond = framesPerSecond;
        this.looping = looping;
    }

    /**
     * Builds an animation that repeats forever: a spinning disk, a twinkling star.
     *
     * @param sheet           the frames to play
     * @param framesPerSecond playback rate; must be positive
     * @return the animation
     * @throws IllegalArgumentException if the rate is not positive
     */
    public static SpriteAnimation looping(SpriteSheet sheet, double framesPerSecond) {
        return new SpriteAnimation(sheet, framesPerSecond, true);
    }

    /**
     * Builds an animation that plays once and holds on its last frame: an explosion.
     *
     * @param sheet           the frames to play
     * @param framesPerSecond playback rate; must be positive
     * @return the animation
     * @throws IllegalArgumentException if the rate is not positive
     */
    public static SpriteAnimation once(SpriteSheet sheet, double framesPerSecond) {
        return new SpriteAnimation(sheet, framesPerSecond, false);
    }

    /**
     * Returns the frame showing at a moment in time.
     *
     * @param seconds elapsed time since the animation started; negative values clamp to the first frame
     * @return the frame index, always within range
     */
    public int frameIndexAt(double seconds) {
        return frameIndexAt(seconds, framesPerSecond, sheet.frameCount(), looping);
    }

    /**
     * Returns the region of the sheet showing at a moment in time.
     *
     * @param seconds elapsed time since the animation started
     * @return the viewport rectangle to draw
     */
    public Rectangle2D viewportAt(double seconds) {
        return sheet.viewport(frameIndexAt(seconds));
    }

    /**
     * Reports whether a one-shot animation has reached its end.
     *
     * @param seconds elapsed time since the animation started
     * @return {@code true} once the last frame of a non-looping animation has been shown;
     *         always {@code false} for a looping one
     */
    public boolean isFinishedAt(double seconds) {
        return !looping && seconds >= durationSeconds();
    }

    /** @return how long one pass through every frame takes, in seconds */
    public double durationSeconds() {
        return sheet.frameCount() / framesPerSecond;
    }

    /** @return the frames being played */
    public SpriteSheet sheet() {
        return sheet;
    }

    /** @return the playback rate, in frames per second */
    public double framesPerSecond() {
        return framesPerSecond;
    }

    /** @return whether the animation repeats */
    public boolean isLooping() {
        return looping;
    }

    /**
     * The frame arithmetic on its own, so it can be tested without a graphics toolkit.
     *
     * <p>Uses {@link Math#floorMod} rather than {@code %} so that a negative time on a looping
     * animation still lands on a real frame instead of a negative index.
     *
     * @param seconds         elapsed time
     * @param framesPerSecond playback rate
     * @param frameCount      how many frames the sheet holds
     * @param looping         whether to wrap around or hold the last frame
     * @return the frame index, from 0 to {@code frameCount - 1}
     */
    static int frameIndexAt(double seconds, double framesPerSecond, int frameCount, boolean looping) {
        if (frameCount <= 1) {
            return 0;
        }
        if (seconds <= 0) {
            return 0;
        }
        long elapsedFrames = (long) Math.floor(seconds * framesPerSecond);
        if (looping) {
            return (int) Math.floorMod(elapsedFrames, (long) frameCount);
        }
        return (int) Math.min(elapsedFrames, frameCount - 1L);
    }
}
