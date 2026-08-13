package com.eia.superdwarfkart.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clock that smooths a sound card's stepwise position into something a road can be drawn from.
 *
 * <p>These tests run in real wall time, because that is the input: the class has no injectable
 * clock and giving it one would be a seam whose only user is the test. The waits are therefore
 * short and every assertion is a bound rather than an equality.
 */
class SmoothClockTest {

    /** Roughly one frame at sixty a second. */
    private static void frame() {
        try {
            Thread.sleep(16);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("a paused clock reports exactly what the card says and does not move")
    void pausedIsExact() {
        SmoothClock clock = new SmoothClock();
        assertEquals(12.5, clock.advance(12.5, false), 1e-9);
        frame();
        assertEquals(12.5, clock.advance(12.5, false), 1e-9,
                "a clock that kept sliding while the music was stopped would drive the road on "
                        + "past silence");
    }

    @Test
    @DisplayName("the reading moves between the card's steps rather than standing still")
    void itMovesBetweenSteps() {
        SmoothClock clock = new SmoothClock();
        clock.advance(0, true);
        frame();

        // The card still says zero, as it does for several frames at a time.
        double first = clock.advance(0, true);
        frame();
        double second = clock.advance(0, true);

        assertTrue(second > first,
                "standing still between the card's steps is exactly the stutter this exists to fix");
    }

    @Test
    @DisplayName("it stays locked to the card rather than drifting away from it")
    void itStaysLockedToTheCard() {
        SmoothClock clock = new SmoothClock();
        double position = 0;
        clock.advance(position, true);

        // Sixty frames of a card that only reports every fourth one, which is what a buffered
        // output actually looks like.
        double reading = 0;
        for (int step = 0; step < 60; step++) {
            frame();
            if (step % 4 == 0) {
                position += 4 * 0.016;
            }
            reading = clock.advance(position, true);
        }

        assertTrue(Math.abs(reading - position) < 0.1,
                "the reading is " + reading + " against a card at " + position
                        + "; an accumulator with nothing correcting it would be a beat out inside "
                        + "a minute, which is the whole reason this is not one");
    }

    @Test
    @DisplayName("a seek snaps rather than crawling to the new position")
    void aSeekSnaps() {
        SmoothClock clock = new SmoothClock();
        clock.advance(10, true);
        frame();

        assertEquals(90, clock.advance(90, true), 0.05,
                "easing across would animate eighty seconds of road that nobody drove");
    }

    @Test
    @DisplayName("a small correction is eased, so the picture never jumps")
    void asmallCorrectionIsEased() {
        SmoothClock clock = new SmoothClock();
        clock.advance(10, true);
        frame();

        double reading = clock.advance(10.05, true);
        assertTrue(reading < 10.05,
                "a 50 ms correction taken in one frame is a visible jolt in the road");
        assertTrue(reading > 10, "and it has to be moving towards it");
    }

    @Test
    @DisplayName("a stalled frame timer cannot run the clock away")
    void aStalledTimerIsCapped() throws InterruptedException {
        SmoothClock clock = new SmoothClock();
        clock.advance(5, true);
        // A minimised window, or a machine that went to sleep.
        Thread.sleep(400);

        assertTrue(clock.advance(5, true) < 5.2,
                "one gap in the frames must not throw the reading forward by the length of it");
    }

    @Test
    @DisplayName("resetting starts again from wherever it is next told")
    void resetting() {
        SmoothClock clock = new SmoothClock();
        clock.advance(200, true);
        clock.reset();

        assertEquals(3, clock.advance(3, true), 1e-9);
    }
}
