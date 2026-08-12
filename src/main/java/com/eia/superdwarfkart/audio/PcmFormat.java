package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Opens any audio file this build can read as one fixed PCM format.
 *
 * <p>Signed 16-bit, 44.1 kHz, stereo, interleaved, little-endian - the format
 * {@link AudioSource} promises its callers, and the only one anything downstream is written
 * against. The meters, the beat analyser and the rhythm game each assume a single shape of buffer
 * and a single sample rate rather than carrying a format around and branching on it.
 *
 * <p><strong>Two callers, deliberately sharing one decode path.</strong>
 * {@link LocalFileAudioSource} opens a file here to play it; {@link MonoPcmReader} opens one here
 * to analyse it. They must agree on the sample rate to the sample, because the analyser's window
 * and hop are expressed in frames and the times it produces are read back against the playback
 * clock. Two copies of the conversion below would be two chances for that agreement to lapse
 * quietly.
 */
public final class PcmFormat {

    private static final Logger LOG = Logger.getLogger(PcmFormat.class.getName());

    /**
     * The one format everything downstream is written against.
     *
     * <p>Every file ends up here whatever it started as.
     */
    public static final AudioFormat PLAYBACK_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            AppConfig.SAMPLE_RATE,
            AppConfig.SAMPLE_SIZE_BITS,
            AppConfig.CHANNELS,
            AppConfig.BYTES_PER_FRAME,
            AppConfig.SAMPLE_RATE,
            false);

    /**
     * Opens a file and inserts whatever conversion it takes to reach {@link #PLAYBACK_FORMAT}.
     *
     * @param audioFile the file to open; must not be {@code null}
     * @return a stream delivering {@link #PLAYBACK_FORMAT}, positioned at the start of the track
     * @throws AudioException if the file is missing or unreadable, or if no installed decoder can
     *                        produce the playback format from it
     */
    public static AudioInputStream open(Path audioFile) {
        if (audioFile == null || !Files.isReadable(audioFile)) {
            throw new AudioException("File not found: " + audioFile);
        }
        AudioInputStream encoded;
        try {
            encoded = AudioSystem.getAudioInputStream(audioFile.toFile());
        } catch (UnsupportedAudioFileException e) {
            throw new AudioException("Not an audio format this build can play: "
                    + audioFile.getFileName(), e);
        } catch (IOException e) {
            throw new AudioException("Could not read " + audioFile.getFileName()
                    + ": " + e.getMessage(), e);
        }
        return convert(encoded, audioFile);
    }

    /**
     * Wraps a stream in however many conversions it takes to reach {@link #PLAYBACK_FORMAT}.
     *
     * <p><strong>Sometimes that is two, and one would silently not be enough.</strong> A decoder
     * declares its output at the file's own sample rate and channel count - so a 22 kHz mono MP3
     * can be asked for 16-bit PCM, but not for 44.1 kHz stereo 16-bit PCM, and asking for both at
     * once simply fails. The plain PCM-to-PCM providers do resample and do mix channels, verified
     * against the resolved jars rather than assumed, so the answer is to decode first and convert
     * the result second.
     *
     * <p>Almost every file takes the one-step path: a 44.1 kHz stereo MP3 or WAV is already
     * arranged the way the second stage would leave it. Do not collapse this back into a single
     * call - it works on every file on this machine and breaks on the first 22 kHz one somebody
     * else brings.
     *
     * @param encoded   the file's own stream
     * @param audioFile the file, for the error message
     * @return a stream delivering {@link #PLAYBACK_FORMAT}
     * @throws AudioException if no combination of installed providers can get there
     */
    private static AudioInputStream convert(AudioInputStream encoded, Path audioFile) {
        AudioFormat source = encoded.getFormat();
        if (AudioSystem.isConversionSupported(PLAYBACK_FORMAT, source)) {
            return AudioSystem.getAudioInputStream(PLAYBACK_FORMAT, encoded);
        }

        AudioFormat decoded = sixteenBitVersionOf(source);
        if (!AudioSystem.isConversionSupported(decoded, source)) {
            closeQuietly(encoded);
            throw new AudioException("No decoder can turn " + source.getEncoding()
                    + " at " + describe(source) + " into 16-bit PCM: " + audioFile.getFileName());
        }

        AudioInputStream pcm = AudioSystem.getAudioInputStream(decoded, encoded);
        if (!AudioSystem.isConversionSupported(PLAYBACK_FORMAT, pcm.getFormat())) {
            closeQuietly(pcm);
            throw new AudioException("Cannot resample " + describe(pcm.getFormat()) + " to "
                    + describe(PLAYBACK_FORMAT) + ": " + audioFile.getFileName());
        }
        return AudioSystem.getAudioInputStream(PLAYBACK_FORMAT, pcm);
    }

    /**
     * Describes the same audio as signed 16-bit little-endian, keeping its rate and channel count.
     *
     * <p>This is the most a decoder will agree to produce in one step, and the input to the
     * resampling stage.
     *
     * @param source the file's own format
     * @return the format to decode into
     */
    private static AudioFormat sixteenBitVersionOf(AudioFormat source) {
        float rate = source.getSampleRate() > 0 ? source.getSampleRate() : AppConfig.SAMPLE_RATE;
        int channels = source.getChannels() > 0 ? source.getChannels() : AppConfig.CHANNELS;
        int bytesPerFrame = channels * AppConfig.SAMPLE_SIZE_BITS / 8;
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                rate,
                AppConfig.SAMPLE_SIZE_BITS,
                channels,
                bytesPerFrame,
                rate,
                false);
    }

    /**
     * Renders a format for a log or an error message.
     *
     * @param format the format to describe
     * @return a short human-readable summary
     */
    public static String describe(AudioFormat format) {
        return (int) format.getSampleRate() + " Hz, " + format.getSampleSizeInBits()
                + "-bit, " + format.getChannels() + " ch";
    }

    /**
     * Closes a stream, swallowing the failure.
     *
     * @param toClose the stream to close, possibly {@code null}
     */
    static void closeQuietly(AudioInputStream toClose) {
        if (toClose == null) {
            return;
        }
        try {
            toClose.close();
        } catch (IOException e) {
            LOG.fine("Ignoring failure to close an audio stream: " + e);
        }
    }

    private PcmFormat() {
        throw new AssertionError("PcmFormat is a utility holder and must not be instantiated");
    }
}
