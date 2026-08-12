package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;

import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Reads a whole file as mono samples, for analysis rather than for playback.
 *
 * <p>The beat analyser wants one number per instant, not two: onsets are events in the music and
 * a snare hit is a snare hit in both channels. Summing to mono here rather than in
 * {@code analysis/} keeps every {@code javax.sound} import inside this package, so the analyser
 * is written against {@code float[]} and could be pointed at any other source of samples.
 *
 * <p><strong>This is not the playback path and must never become it.</strong> It opens its own
 * stream through {@link PcmFormat}, so analysing a track neither disturbs what is playing nor
 * waits for it - the file is read as fast as the decoder manages, on whatever thread the caller
 * is on, which is a background one. It shares the decode path with playback for one reason: the
 * sample rate the analyser measures times against has to be the sample rate the playback clock
 * counts, exactly.
 *
 * <p>Samples arrive in blocks rather than as one array. A four-minute track is ten million frames,
 * and materialising it would cost forty megabytes to produce a novelty curve of twenty thousand
 * values.
 */
public final class MonoPcmReader implements AutoCloseable {

    /** Frames pulled from the decoder at a time. Large enough that per-call overhead disappears. */
    private static final int BLOCK_FRAMES = 8192;

    private final Path file;
    private final AudioInputStream stream;
    private final byte[] block = new byte[BLOCK_FRAMES * AppConfig.BYTES_PER_FRAME];

    /** Bytes of a partly consumed frame carried over to the next read, 0..3. */
    private final byte[] remainder = new byte[AppConfig.BYTES_PER_FRAME];
    private int remainderLength;

    private long framesRead;

    /**
     * Opens a file for analysis.
     *
     * @param file the audio file to read; must not be {@code null}
     * @throws AudioException if the file cannot be opened or decoded
     */
    public MonoPcmReader(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.stream = PcmFormat.open(file);
    }

    /**
     * Reads the next block of mono samples.
     *
     * <p>Left and right are averaged, so a hard-panned track contributes from both sides and the
     * result stays inside -1..1 whatever the two channels do.
     *
     * @param destination where to write the samples
     * @param offset      first index to write
     * @param length      how many samples to read at most
     * @return how many samples were written, or {@code -1} at the end of the track
     * @throws AudioException if the file cannot be read
     */
    public int readMono(float[] destination, int offset, int length) {
        Objects.requireNonNull(destination, "destination must not be null");
        if (length <= 0) {
            return 0;
        }

        int wanted = Math.min(length, BLOCK_FRAMES);
        int bytes = fill(wanted * AppConfig.BYTES_PER_FRAME);
        if (bytes <= 0) {
            return -1;
        }

        int frames = bytes / AppConfig.BYTES_PER_FRAME;
        for (int frame = 0; frame < frames; frame++) {
            float left = LevelAnalyzer.sample(block, 0, frame, LevelAnalyzer.LEFT);
            float right = LevelAnalyzer.sample(block, 0, frame, LevelAnalyzer.RIGHT);
            destination[offset + frame] = (left + right) / 2f;
        }
        framesRead += frames;
        return frames;
    }

    /**
     * Fills the block with whole frames, holding back any partial one.
     *
     * <p>A decoder is free to return any number of bytes, including a count that splits a frame
     * down the middle. Reading a partial frame as a whole one would shift every subsequent sample
     * by two bytes and swap the channels for the rest of the track - the samples would still look
     * like music, and every onset after that point would be measured from noise.
     *
     * @param wantedBytes how many bytes to try for
     * @return how many bytes of whole frames are in the block, or {@code 0} at end of stream
     */
    private int fill(int wantedBytes) {
        int filled = remainderLength;
        System.arraycopy(remainder, 0, block, 0, remainderLength);
        remainderLength = 0;

        try {
            while (filled < wantedBytes) {
                int read = stream.read(block, filled, wantedBytes - filled);
                if (read <= 0) {
                    break;
                }
                filled += read;
            }
        } catch (IOException e) {
            throw new AudioException("Could not read " + file.getFileName()
                    + " for analysis: " + e.getMessage(), e);
        }

        int whole = filled - filled % AppConfig.BYTES_PER_FRAME;
        remainderLength = filled - whole;
        if (remainderLength > 0) {
            System.arraycopy(block, whole, remainder, 0, remainderLength);
        }
        return whole;
    }

    /**
     * Estimates the track's length, for a progress readout.
     *
     * <p>Best effort by design: a compressed stream reports an unknown frame length and the
     * estimate falls back to the header-and-bitrate figure {@link AudioMetadata} already derives
     * for the library import. A progress bar that is a few per cent out is worth far more than
     * decoding the file twice to be exact.
     *
     * @return the estimated playing time, or {@link Duration#ZERO} when it cannot be established
     */
    public Duration estimatedDuration() {
        long frames = stream.getFrameLength();
        if (frames > 0) {
            return Duration.ofNanos(Math.round(frames / (double) AppConfig.SAMPLE_RATE * 1e9));
        }
        return AudioMetadata.readDuration(file);
    }

    /** @return how many frames have been read so far */
    public long framesRead() {
        return framesRead;
    }

    /** @return the file being read */
    public Path file() {
        return file;
    }

    @Override
    public void close() {
        PcmFormat.closeQuietly(stream);
    }
}
