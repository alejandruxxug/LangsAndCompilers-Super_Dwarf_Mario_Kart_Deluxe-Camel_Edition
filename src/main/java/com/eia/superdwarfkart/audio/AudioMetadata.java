package com.eia.superdwarfkart.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads what can be learned about an audio file without decoding it.
 *
 * <p>Used when importing, to fill in a song's playing time. Every method here is best effort:
 * an unreadable or unsupported file yields {@link Duration#ZERO} rather than an exception, so a
 * single odd file can never block an import.
 *
 * <p>Part of the audio layer, so no {@code javafx.*} import appears here.
 */
public final class AudioMetadata {

    private static final Logger LOG = Logger.getLogger(AudioMetadata.class.getName());

    /**
     * Reads the playing time of an audio file.
     *
     * <p>Three sources are tried in order. Compressed formats report a duration property through
     * the service provider that decodes them; uncompressed formats give an exact frame count;
     * and an MPEG file that reports neither is estimated from its size and bitrate.
     *
     * @param file the audio file to inspect
     * @return the playing time, or {@link Duration#ZERO} when it cannot be determined
     */
    public static Duration readDuration(Path file) {
        if (file == null || !Files.isReadable(file)) {
            return Duration.ZERO;
        }
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file.toFile());

            Duration fromProperties = fromProperties(format.properties());
            if (!fromProperties.isZero()) {
                return fromProperties;
            }

            Duration fromFrames = fromFrameCount(format);
            if (!fromFrames.isZero()) {
                return fromFrames;
            }

            return fromBitrate(format.properties(), Files.size(file));
        } catch (Exception e) {
            LOG.fine("Could not read the duration of " + file + ": " + e);
            return Duration.ZERO;
        }
    }

    /**
     * Reads the duration a decoder reported directly, in microseconds.
     *
     * @param properties the format properties
     * @return the duration, or zero when absent
     */
    private static Duration fromProperties(Map<String, Object> properties) {
        Object duration = properties.get("duration");
        if (duration instanceof Number microseconds) {
            return Duration.ofNanos(microseconds.longValue() * 1_000L);
        }
        return Duration.ZERO;
    }

    /**
     * Computes the duration from an exact frame count, which uncompressed formats provide.
     *
     * @param format the file format
     * @return the duration, or zero when the frame count or rate is unknown
     */
    private static Duration fromFrameCount(AudioFileFormat format) {
        AudioFormat audioFormat = format.getFormat();
        long frames = format.getFrameLength();
        float frameRate = audioFormat.getFrameRate();
        if (frames == AudioSystem.NOT_SPECIFIED || frameRate == AudioSystem.NOT_SPECIFIED || frameRate <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.round(frames / (double) frameRate * 1000.0));
    }

    /**
     * Estimates the duration from the file size and the reported bitrate.
     *
     * <p>Only correct for a constant bitrate stream, so it is the last resort.
     *
     * @param properties the format properties
     * @param sizeBytes  the file size on disk
     * @return the estimated duration, or zero when no bitrate was reported
     */
    private static Duration fromBitrate(Map<String, Object> properties, long sizeBytes) {
        Object bitrate = properties.get("bitrate");
        if (bitrate instanceof Number bitsPerSecond && bitsPerSecond.doubleValue() > 0) {
            double seconds = sizeBytes * 8.0 / bitsPerSecond.doubleValue();
            return Duration.ofMillis(Math.round(seconds * 1000.0));
        }
        return Duration.ZERO;
    }

    /**
     * Reports whether the audio system can open a file at all.
     *
     * @param file the file to test
     * @return {@code true} if a reader accepts it
     */
    public static boolean isSupported(Path file) {
        if (file == null || !Files.isReadable(file)) {
            return false;
        }
        try {
            AudioSystem.getAudioFileFormat(file.toFile());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private AudioMetadata() {
        throw new AssertionError("AudioMetadata is a utility holder and must not be instantiated");
    }
}
