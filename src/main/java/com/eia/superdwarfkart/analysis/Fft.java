package com.eia.superdwarfkart.analysis;

/**
 * A fast Fourier transform, written by hand because the platform has none.
 *
 * <p>Iterative radix-2 Cooley-Tukey, decimation in time, in place. The size is fixed at
 * construction so the twiddle factors and the bit-reversal permutation are worked out once and
 * reused for every window - a four-minute track is roughly twenty thousand transforms, and
 * recomputing a thousand sines and cosines for each of them would dominate the whole analysis.
 *
 * <p><strong>This is not on the audio path.</strong> It runs on the analysis thread, over a file
 * being read separately from the one being played. Ground rule 4 is about the playback thread and
 * nothing here ever touches it.
 *
 * <p>One instance is not safe to share between threads: the working arrays are fields, which is
 * the point - the analysis allocates nothing per window. Give each thread its own.
 */
public final class Fft {

    private final int size;

    /** Where each input sample goes, so the transform can run in place afterwards. */
    private final int[] reversed;

    /** Real and imaginary parts of the twiddle factors, indexed by {@code k} in {@code -2πk/N}. */
    private final float[] twiddleCos;
    private final float[] twiddleSin;

    private final float[] real;
    private final float[] imaginary;

    /**
     * Prepares a transform of a fixed size.
     *
     * @param size how many samples each transform takes; must be a power of two and at least 2
     * @throws IllegalArgumentException if the size is not a power of two
     */
    public Fft(int size) {
        if (size < 2 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException(
                    "FFT size must be a power of two and at least 2, but was " + size);
        }
        this.size = size;
        this.real = new float[size];
        this.imaginary = new float[size];

        this.reversed = new int[size];
        int bits = Integer.numberOfTrailingZeros(size);
        for (int index = 0; index < size; index++) {
            reversed[index] = Integer.reverse(index) >>> (32 - bits);
        }

        // Half a turn's worth is all the butterflies ever ask for.
        this.twiddleCos = new float[size / 2];
        this.twiddleSin = new float[size / 2];
        for (int k = 0; k < size / 2; k++) {
            double angle = -2 * Math.PI * k / size;
            twiddleCos[k] = (float) Math.cos(angle);
            twiddleSin[k] = (float) Math.sin(angle);
        }
    }

    /** @return how many samples one transform consumes */
    public int size() {
        return size;
    }

    /** @return how many magnitude bins one transform produces */
    public int bins() {
        return size / 2;
    }

    /**
     * Transforms one block of real samples into a magnitude spectrum.
     *
     * <p>The caller applies its own analysis window first - {@link OnsetDetector} uses a Hann -
     * because what the window should be is a property of the measurement, not of the transform.
     *
     * <p>Only the lower half of the spectrum is written: a real signal's transform is conjugate
     * symmetric, so the upper half is the mirror image and carries no information.
     *
     * @param samples     the input, already windowed
     * @param offset      first sample to read
     * @param destination where to write {@link #bins()} magnitudes
     */
    public void magnitudes(float[] samples, int offset, float[] destination) {
        // Loading through the permutation is what lets every butterfly below work in place.
        for (int index = 0; index < size; index++) {
            real[index] = samples[offset + reversed[index]];
            imaginary[index] = 0;
        }

        for (int span = 2; span <= size; span <<= 1) {
            int half = span >> 1;
            int stride = size / span;
            for (int start = 0; start < size; start += span) {
                for (int pair = 0; pair < half; pair++) {
                    int twiddle = pair * stride;
                    float cos = twiddleCos[twiddle];
                    float sin = twiddleSin[twiddle];

                    int low = start + pair;
                    int high = low + half;

                    float rotatedReal = real[high] * cos - imaginary[high] * sin;
                    float rotatedImaginary = real[high] * sin + imaginary[high] * cos;

                    real[high] = real[low] - rotatedReal;
                    imaginary[high] = imaginary[low] - rotatedImaginary;
                    real[low] += rotatedReal;
                    imaginary[low] += rotatedImaginary;
                }
            }
        }

        int bins = bins();
        for (int bin = 0; bin < bins; bin++) {
            float re = real[bin];
            float im = imaginary[bin];
            destination[bin] = (float) Math.sqrt(re * re + im * im);
        }
    }
}
