package com.eia.superdwarfkart.assets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the frame arithmetic only. Building a sheet needs a graphics toolkit, but the mapping
 * from a moment in time to a frame index is where an off-by-one would show up on screen, and it
 * is static.
 */
@DisplayName("Sprite animation timing")
class SpriteAnimationTest {

    private static final int FRAMES = 9;
    private static final double FPS = 12;

    private static int loopingAt(double seconds) {
        return SpriteAnimation.frameIndexAt(seconds, FPS, FRAMES, true);
    }

    private static int onceAt(double seconds) {
        return SpriteAnimation.frameIndexAt(seconds, FPS, FRAMES, false);
    }

    @Test
    @DisplayName("time zero shows the first frame")
    void startsAtTheFirstFrame() {
        assertEquals(0, loopingAt(0));
        assertEquals(0, onceAt(0));
    }

    @Test
    @DisplayName("frames advance at the stated rate")
    void advancesAtTheRate() {
        // At 12 fps each frame lasts one twelfth of a second.
        assertEquals(0, loopingAt(1 / 12.0 - 0.001));
        assertEquals(1, loopingAt(1 / 12.0));
        assertEquals(5, loopingAt(5 / 12.0));
        assertEquals(8, loopingAt(8 / 12.0));
    }

    @Test
    @DisplayName("a looping animation wraps back to the first frame")
    void loops() {
        assertEquals(0, loopingAt(FRAMES / FPS));
        assertEquals(1, loopingAt(FRAMES / FPS + 1 / 12.0));
        assertEquals(3, loopingAt(4 * FRAMES / FPS + 3 / 12.0));
    }

    @Test
    @DisplayName("a one-shot animation holds on its last frame forever")
    void oneShotHoldsTheLastFrame() {
        assertEquals(8, onceAt(FRAMES / FPS));
        assertEquals(8, onceAt(60));
        assertEquals(8, onceAt(3600));
    }

    @Test
    @DisplayName("a negative time lands on a real frame")
    void negativeTimeClamps() {
        // Playback position can read very slightly negative around a seek; an index of -1 would
        // be a crash in the middle of a run.
        assertEquals(0, loopingAt(-0.5));
        assertEquals(0, onceAt(-0.5));
    }

    @Test
    @DisplayName("a single-frame sheet always shows frame zero")
    void singleFrameSheet() {
        assertEquals(0, SpriteAnimation.frameIndexAt(0, FPS, 1, true));
        assertEquals(0, SpriteAnimation.frameIndexAt(99, FPS, 1, true));
        assertEquals(0, SpriteAnimation.frameIndexAt(99, FPS, 1, false));
    }

    @Test
    @DisplayName("a very large time does not overflow the index")
    void longRunningAnimation() {
        // Frames elapsed is computed as a long before wrapping: an int would overflow after
        // about fifty days of playback and start returning negative indices.
        int frame = SpriteAnimation.frameIndexAt(1e9, FPS, FRAMES, true);
        assertEquals(true, frame >= 0 && frame < FRAMES, "frame index out of range: " + frame);
    }
}
