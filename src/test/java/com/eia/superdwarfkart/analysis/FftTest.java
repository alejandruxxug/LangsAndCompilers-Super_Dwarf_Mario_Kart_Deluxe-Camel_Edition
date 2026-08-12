package com.eia.superdwarfkart.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-written transform, checked against signals whose spectrum is known by arithmetic.
 *
 * <p>An FFT that is subtly wrong - a twiddle factor of the wrong sign, a bit-reversal off by one -
 * still returns plausible-looking numbers, and every onset derived from it would be plausible too.
 * These tests pin it to signals where the answer can be worked out on paper.
 */
class FftTest {

    /**
     * @param size   how many samples
     * @param bin    which bin the sine should land in
     * @return a sine that completes exactly {@code bin} cycles across the window
     */
    private static float[] sineAtBin(int size, int bin) {
        float[] samples = new float[size];
        for (int index = 0; index < size; index++) {
            samples[index] = (float) Math.sin(2 * Math.PI * bin * index / size);
        }
        return samples;
    }

    /**
     * @param magnitudes a spectrum
     * @return the index of its largest value
     */
    private static int peakBin(float[] magnitudes) {
        int peak = 0;
        for (int bin = 1; bin < magnitudes.length; bin++) {
            if (magnitudes[bin] > magnitudes[peak]) {
                peak = bin;
            }
        }
        return peak;
    }

    @Test
    @DisplayName("a size that is not a power of two is refused rather than quietly mangled")
    void refusesNonPowerOfTwoSizes() {
        assertThrows(IllegalArgumentException.class, () -> new Fft(1000));
        assertThrows(IllegalArgumentException.class, () -> new Fft(0));
        assertThrows(IllegalArgumentException.class, () -> new Fft(1));
        assertThrows(IllegalArgumentException.class, () -> new Fft(-4));
    }

    @Test
    @DisplayName("half the size comes back, because the other half is the mirror image")
    void producesHalfTheSizeInBins() {
        assertEquals(512, new Fft(1024).bins());
        assertEquals(1024, new Fft(1024).size());
    }

    @Test
    @DisplayName("a constant signal is all at zero frequency")
    void constantSignalLandsInBinZero() {
        Fft fft = new Fft(64);
        float[] samples = new float[64];
        java.util.Arrays.fill(samples, 1f);
        float[] magnitudes = new float[fft.bins()];

        fft.magnitudes(samples, 0, magnitudes);

        assertEquals(64f, magnitudes[0], 1e-3, "the sum of the samples belongs in bin zero");
        for (int bin = 1; bin < magnitudes.length; bin++) {
            assertEquals(0f, magnitudes[bin], 1e-3,
                    "a signal that never changes has no energy at bin " + bin);
        }
    }

    @Test
    @DisplayName("a sine lands in its own bin, at every bin")
    void sineLandsInItsOwnBin() {
        Fft fft = new Fft(256);
        float[] magnitudes = new float[fft.bins()];

        for (int bin = 1; bin < fft.bins(); bin++) {
            fft.magnitudes(sineAtBin(256, bin), 0, magnitudes);

            assertEquals(bin, peakBin(magnitudes),
                    "a sine of exactly " + bin + " cycles must peak in bin " + bin);
            // A real sine splits its energy between the positive and negative frequency, so the
            // half spectrum carries N/2 times the amplitude - here N/2 = 128.
            assertEquals(128f, magnitudes[bin], 0.5,
                    "the magnitude in bin " + bin + " is not the amplitude the sine had");
        }
    }

    @Test
    @DisplayName("silence transforms to silence rather than to numerical dust")
    void silenceTransformsToSilence() {
        Fft fft = new Fft(128);
        float[] magnitudes = new float[fft.bins()];

        fft.magnitudes(new float[128], 0, magnitudes);

        for (float magnitude : magnitudes) {
            assertEquals(0f, magnitude, 1e-9);
        }
    }

    @Test
    @DisplayName("two sines added come back as two peaks, so the transform is linear")
    void superposedSinesGiveTwoPeaks() {
        Fft fft = new Fft(256);
        float[] first = sineAtBin(256, 10);
        float[] second = sineAtBin(256, 40);
        float[] mixed = new float[256];
        for (int index = 0; index < 256; index++) {
            mixed[index] = first[index] + second[index];
        }
        float[] magnitudes = new float[fft.bins()];

        fft.magnitudes(mixed, 0, magnitudes);

        assertEquals(128f, magnitudes[10], 0.5);
        assertEquals(128f, magnitudes[40], 0.5);
        assertEquals(0f, magnitudes[25], 0.5, "nothing was put between them");
    }

    @Test
    @DisplayName("the transform reads from the offset it is given")
    void readsFromTheGivenOffset() {
        Fft fft = new Fft(64);
        float[] padded = new float[128];
        float[] signal = sineAtBin(64, 7);
        System.arraycopy(signal, 0, padded, 64, 64);
        float[] magnitudes = new float[fft.bins()];

        fft.magnitudes(padded, 64, magnitudes);

        assertEquals(7, peakBin(magnitudes), "the second half of the array was not the part read");
    }

    @Test
    @DisplayName("running twice gives the same answer, so no state leaks between windows")
    void repeatedTransformsAreIndependent() {
        Fft fft = new Fft(128);
        float[] loud = sineAtBin(128, 20);
        float[] first = new float[fft.bins()];
        float[] second = new float[fft.bins()];

        fft.magnitudes(loud, 0, first);
        fft.magnitudes(new float[128], 0, second);
        fft.magnitudes(loud, 0, first);

        assertEquals(64f, first[20], 0.5);
        assertTrue(second[20] < 1e-6, "the silent window inherited energy from the loud one");
    }
}
