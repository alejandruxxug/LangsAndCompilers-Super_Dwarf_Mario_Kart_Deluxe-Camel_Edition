package com.eia.superdwarfkart.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the clock's resolution.
 *
 * <p>These assertions look trivial and they guard the most expensive bug this project has had.
 * {@code java.time.Duration.toSeconds()} returns a {@code long}, so it discards the fraction, and
 * Java widens the result to {@code double} without a warning - which means
 * {@code position().toSeconds()} compiles, reads exactly like the {@code javafx.util.Duration}
 * method that returns a {@code double}, and quantises the whole application's clock to one second.
 *
 * <p>Nothing failed. The runner simply crawled and then lurched once a second, and jumping over a
 * wall became a matter of luck. Every symptom was in the presentation layer and the cause was one
 * method call, so the fix is only durable if something fails when it comes back.
 */
class AudioSourceSecondsTest {

    /** A source that reports a fixed position, so the arithmetic is the only thing under test. */
    private static final class FixedSource implements AudioSource {
        private final Duration position;

        FixedSource(Duration position) {
            this.position = position;
        }

        @Override
        public void load(String locator) {
            // Not needed for a clock test.
        }

        @Override
        public void play() {
            // Not needed for a clock test.
        }

        @Override
        public void pause() {
            // Not needed for a clock test.
        }

        @Override
        public void stop() {
            // Not needed for a clock test.
        }

        @Override
        public void seek(Duration to) {
            // Not needed for a clock test.
        }

        @Override
        public Duration position() {
            return position;
        }

        @Override
        public Duration duration() {
            return Duration.ofMillis(258_600);
        }

        @Override
        public boolean isPlaying() {
            return true;
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public void addPcmListener(PcmListener listener) {
            // Not needed for a clock test.
        }

        @Override
        public void removePcmListener(PcmListener listener) {
            // Not needed for a clock test.
        }

        @Override
        public void setVolume(double volume) {
            // Not needed for a clock test.
        }

        @Override
        public double volume() {
            return 1;
        }

        @Override
        public void setOnEndOfMedia(Runnable action) {
            // Not needed for a clock test.
        }

        @Override
        public void close() {
            // Not needed for a clock test.
        }
    }

    @Test
    @DisplayName("positionSeconds keeps the fraction that Duration.toSeconds throws away")
    void keepsTheFraction() {
        AudioSource source = new FixedSource(Duration.ofMillis(1750));

        assertEquals(1.75, source.positionSeconds(), 1e-9,
                "the clock must resolve finer than a whole second");
        assertEquals(1L, source.position().toSeconds(),
                "and this is the call that must never drive anything: it truncates");
    }

    @Test
    @DisplayName("the clock resolves finely enough to place a beat")
    void resolvesFinelyEnoughForABeat() {
        // 120 BPM is half a second a beat, and the analyser reports onsets to a few milliseconds.
        // A clock quantised to anything near a beat cannot place an entity on one.
        double previous = -1;
        double smallestStep = Double.MAX_VALUE;
        for (int millis = 0; millis < 2000; millis += 16) {
            double seconds = new FixedSource(Duration.ofMillis(millis)).positionSeconds();
            if (previous >= 0) {
                smallestStep = Math.min(smallestStep, seconds - previous);
            }
            previous = seconds;
        }
        assertTrue(smallestStep > 0,
                "the clock must advance on every frame, not once a second");
        assertEquals(0.016, smallestStep, 1e-9,
                "and it must advance by exactly what it was given");
    }

    @Test
    @DisplayName("durationSeconds keeps the fraction too")
    void durationKeepsTheFraction() {
        assertEquals(258.6, new FixedSource(Duration.ZERO).durationSeconds(), 1e-9);
    }

    @Test
    @DisplayName("toSeconds treats a missing duration as zero rather than throwing")
    void nullIsZero() {
        assertEquals(0, AudioSource.toSeconds(null), 1e-9);
    }
}
