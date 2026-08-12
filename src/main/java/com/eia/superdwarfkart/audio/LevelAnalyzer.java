package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;

import java.util.Objects;

/**
 * Turns a block of interleaved PCM into the four numbers the meters draw.
 *
 * <p>Registered as a {@link PcmListener} on whichever {@link AudioSource} is playing, so it works
 * unchanged whether the bytes came from a file on disk or, later, from a pipe.
 *
 * <p><strong>Deinterleaving is the whole job.</strong> The stream arrives as
 * {@code [L0 R0 L1 R1 ...]}; even sample indices are the left channel and odd ones the right, and
 * each is measured entirely on its own. Mixing them - or measuring every second byte, or forgetting
 * the sign extension - produces a meter that moves plausibly and means nothing.
 *
 * <p>The four passes below are separate on purpose. Each is a few thousand additions over a 4 KB
 * block once every 23 ms, which is nothing next to decoding that block, and keeping them apart
 * means the same code the meters use is the code the tests measure.
 */
public final class LevelAnalyzer implements PcmListener {

    /** Channel index of the left channel within a frame. */
    public static final int LEFT = 0;

    /** Channel index of the right channel within a frame. */
    public static final int RIGHT = 1;

    /** Divisor that maps a signed 16-bit sample onto -1..1. */
    private static final float FULL_SCALE = 32768f;

    private final Levels levels;

    /**
     * @param levels where each block's measurements are published; must not be {@code null}
     */
    public LevelAnalyzer(Levels levels) {
        this.levels = Objects.requireNonNull(levels, "levels must not be null");
    }

    /**
     * Measures one block and publishes it.
     *
     * <p>Runs on the playback thread: four passes over the buffer, four atomic stores, return.
     *
     * @param buffer the decoded block
     * @param offset first valid byte
     * @param length how many bytes are valid
     */
    @Override
    public void pcm(byte[] buffer, int offset, int length) {
        levels.publish(
                rms(buffer, offset, length, LEFT),
                rms(buffer, offset, length, RIGHT),
                peak(buffer, offset, length, LEFT),
                peak(buffer, offset, length, RIGHT));
    }

    /** @return the number of whole stereo frames in a block of the given length */
    public static int frameCount(int length) {
        return Math.max(0, length / AppConfig.BYTES_PER_FRAME);
    }

    /**
     * Reads one sample as a number between -1 and 1.
     *
     * <p><strong>Little-endian with sign extension.</strong> The low byte comes first and must be
     * masked to stay unsigned; the high byte is shifted up and the cast to {@code short} restores
     * the sign. Get the byte order backwards and the meters show noise that looks like a signal.
     *
     * @param buffer  the decoded block
     * @param offset  first valid byte of the block
     * @param frame   which frame within the block, counting from zero
     * @param channel {@link #LEFT} or {@link #RIGHT}
     * @return the sample, -1..1
     */
    public static float sample(byte[] buffer, int offset, int frame, int channel) {
        int index = offset + frame * AppConfig.BYTES_PER_FRAME + channel * (AppConfig.SAMPLE_SIZE_BITS / 8);
        short value = (short) ((buffer[index + 1] << 8) | (buffer[index] & 0xFF));
        return value / FULL_SCALE;
    }

    /**
     * Root mean square of one channel over one block: how loud it sounded.
     *
     * @param buffer  the decoded block
     * @param offset  first valid byte
     * @param length  how many bytes are valid
     * @param channel {@link #LEFT} or {@link #RIGHT}
     * @return the level, 0..1
     */
    public static float rms(byte[] buffer, int offset, int length, int channel) {
        int frames = frameCount(length);
        if (frames == 0) {
            return 0;
        }
        double sumOfSquares = 0;
        for (int frame = 0; frame < frames; frame++) {
            float value = sample(buffer, offset, frame, channel);
            sumOfSquares += value * value;
        }
        return (float) Math.sqrt(sumOfSquares / frames);
    }

    /**
     * Largest absolute sample of one channel over one block: the transient the RMS smooths away.
     *
     * @param buffer  the decoded block
     * @param offset  first valid byte
     * @param length  how many bytes are valid
     * @param channel {@link #LEFT} or {@link #RIGHT}
     * @return the peak, 0..1
     */
    public static float peak(byte[] buffer, int offset, int length, int channel) {
        int frames = frameCount(length);
        float highest = 0;
        for (int frame = 0; frame < frames; frame++) {
            float magnitude = Math.abs(sample(buffer, offset, frame, channel));
            if (magnitude > highest) {
                highest = magnitude;
            }
        }
        return highest;
    }
}
