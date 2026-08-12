package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

/**
 * Builds audio whose beats are known exactly, so what the analyser finds can be compared with the
 * truth rather than with a previous run.
 *
 * <p>Two ingredients, and both are load bearing. The <strong>clicks</strong> are short bursts of
 * noise - broadband, like a drum hit, and unlike a single-sample impulse which no real recording
 * contains. The <strong>drone</strong> underneath them is a quiet steady tone, which produces
 * almost no spectral flux of its own: an analyser that reported onsets during the drone would be
 * measuring loudness rather than change, and the whole point of using flux is that it does not.
 */
final class ClickTrack {

    /** How long one click lasts, in seconds. About the length of a snare's attack. */
    private static final double CLICK_SECONDS = 0.008;

    /** Amplitude of a click, well below full scale so nothing clips. */
    private static final double CLICK_LEVEL = 0.7;

    /** Amplitude of the continuous tone underneath. */
    private static final double DRONE_LEVEL = 0.08;

    /** Frequency of that tone, in Hz - a low note, as a bass line would be. */
    private static final double DRONE_HZ = 110;

    /**
     * Builds one channel of audio with clicks at the given times.
     *
     * @param seconds    how long the track should be
     * @param clickTimes when the clicks happen, in seconds
     * @return the samples, at {@link AppConfig#SAMPLE_RATE}
     */
    static float[] mono(double seconds, double[] clickTimes) {
        int total = (int) Math.round(seconds * AppConfig.SAMPLE_RATE);
        float[] samples = new float[total];

        for (int index = 0; index < total; index++) {
            samples[index] = (float) (DRONE_LEVEL
                    * Math.sin(2 * Math.PI * DRONE_HZ * index / AppConfig.SAMPLE_RATE));
        }

        // Seeded, so a failing run can be reproduced exactly rather than blamed on the noise.
        Random random = new Random(20260812L);
        int clickSamples = (int) Math.round(CLICK_SECONDS * AppConfig.SAMPLE_RATE);
        for (double time : clickTimes) {
            int start = (int) Math.round(time * AppConfig.SAMPLE_RATE);
            for (int offset = 0; offset < clickSamples; offset++) {
                int index = start + offset;
                if (index < 0 || index >= total) {
                    continue;
                }
                // Decaying, so the attack is at the front where the detector should find it.
                double envelope = Math.exp(-6.0 * offset / clickSamples);
                samples[index] += (float) (CLICK_LEVEL * envelope * (random.nextDouble() * 2 - 1));
            }
        }
        return samples;
    }

    /**
     * Writes the same audio to a stereo WAV file at the playback rate.
     *
     * @param file       where to write
     * @param seconds    how long the track should be
     * @param clickTimes when the clicks happen, in seconds
     * @return the file written
     * @throws IOException if it cannot be written
     */
    static Path writeWav(Path file, double seconds, double[] clickTimes) throws IOException {
        float[] samples = mono(seconds, clickTimes);
        AudioFormat format = new AudioFormat(AppConfig.SAMPLE_RATE, 16, 2, true, false);
        byte[] pcm = new byte[samples.length * 4];
        for (int frame = 0; frame < samples.length; frame++) {
            short value = (short) Math.round(Math.clamp(samples[frame], -1f, 1f) * 32767);
            for (int channel = 0; channel < 2; channel++) {
                int index = frame * 4 + channel * 2;
                pcm[index] = (byte) (value & 0xFF);
                pcm[index + 1] = (byte) ((value >> 8) & 0xFF);
            }
        }
        try (AudioInputStream in =
                     new AudioInputStream(new ByteArrayInputStream(pcm), format, samples.length)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }

    /**
     * @param count  how many beats
     * @param bpm    the tempo
     * @param offset when the first beat happens, in seconds
     * @return the beat times, in seconds
     */
    static double[] beatsAt(int count, double bpm, double offset) {
        double period = 60 / bpm;
        double[] times = new double[count];
        for (int index = 0; index < count; index++) {
            times[index] = offset + index * period;
        }
        return times;
    }

    private ClickTrack() {
    }
}
