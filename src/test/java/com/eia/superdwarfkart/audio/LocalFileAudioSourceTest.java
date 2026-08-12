package com.eia.superdwarfkart.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opening, measuring and repositioning a file.
 *
 * <p>Everything here runs without a sound card, which is why the line is opened lazily on the first
 * {@link LocalFileAudioSource#play()} rather than on load: a build machine has no audio output, and
 * a test that needed one would simply be deleted the first time it failed. What actually reaches
 * the speakers is checked by the smoke test, which plays a second of a real file and prints the
 * measured levels.
 */
class LocalFileAudioSourceTest {

    private static final float RATE = 44100f;

    private LocalFileAudioSource source;

    @AfterEach
    void closeSource() {
        if (source != null) {
            source.close();
        }
    }

    /**
     * Writes a WAV file of a given length at the playback rate.
     *
     * @param file     where to write
     * @param seconds  how long it should play for
     * @param channels 1 for mono, 2 for stereo
     * @return the file written
     */
    private static Path writeWav(Path file, double seconds, int channels) throws IOException {
        return writeWav(file, seconds, channels, RATE);
    }

    /**
     * Writes a WAV file.
     *
     * @param file     where to write
     * @param seconds  how long it should play for
     * @param channels 1 for mono, 2 for stereo
     * @param rate     sample rate in Hz
     * @return the file written
     */
    private static Path writeWav(Path file, double seconds, int channels, float rate)
            throws IOException {
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        int frames = (int) Math.round(seconds * rate);
        byte[] pcm = new byte[frames * channels * 2];
        for (int frame = 0; frame < frames; frame++) {
            // A quiet sine, so the bytes are not all zero and a decoder cannot shortcut them.
            short value = (short) (Math.sin(frame / rate * 2 * Math.PI * 440) * 8000);
            for (int channel = 0; channel < channels; channel++) {
                int index = (frame * channels + channel) * 2;
                pcm[index] = (byte) (value & 0xFF);
                pcm[index + 1] = (byte) ((value >> 8) & 0xFF);
            }
        }
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }

    @Test
    @DisplayName("a file that does not exist is refused with a readable message, not a crash")
    void missingFileThrowsAudioException(@TempDir Path directory) {
        source = new LocalFileAudioSource();

        AudioException thrown = assertThrows(AudioException.class,
                () -> source.load(directory.resolve("nothing-here.mp3")));

        assertTrue(thrown.getMessage().contains("nothing-here.mp3"),
                "the message reaches the user, so it has to name the file");
        assertFalse(source.isLoaded());
    }

    @Test
    @DisplayName("a file that is not audio at all is refused rather than played as noise")
    void nonAudioFileThrowsAudioException(@TempDir Path directory) throws IOException {
        Path text = directory.resolve("cover.txt");
        java.nio.file.Files.writeString(text, "this is not a song");
        source = new LocalFileAudioSource();

        assertThrows(AudioException.class, () -> source.load(text));
    }

    @Test
    @DisplayName("loading opens the track, measures it and rewinds")
    void loadReadsTheDuration(@TempDir Path directory) throws IOException {
        Path wav = writeWav(directory.resolve("two-seconds.wav"), 2, 2);
        source = new LocalFileAudioSource();

        source.load(wav);

        assertTrue(source.isLoaded());
        assertFalse(source.isPlaying(), "loading a song must not start it playing");
        assertEquals(2000, source.duration().toMillis(), 20);
        assertEquals(Duration.ZERO, source.position());
    }

    @Test
    @DisplayName("a mono file loads, and is widened rather than refused")
    void loadsMonoFiles(@TempDir Path directory) throws IOException {
        Path wav = writeWav(directory.resolve("mono.wav"), 1, 1);
        source = new LocalFileAudioSource();

        source.load(wav);

        assertTrue(source.isLoaded());
        assertEquals(1000, source.duration().toMillis(), 20);
    }

    @Test
    @DisplayName("a file at another sample rate loads, and is resampled rather than refused")
    void loadsOtherSampleRates(@TempDir Path directory) throws IOException {
        Path wav = writeWav(directory.resolve("half-rate.wav"), 2, 1, 22050f);
        source = new LocalFileAudioSource();

        source.load(wav);

        assertTrue(source.isLoaded());
        // Two seconds is two seconds whatever the rate. Reading this as one second, or as four,
        // would mean the frame count was being divided by the wrong rate.
        assertEquals(2000, source.duration().toMillis(), 40);
    }

    @Test
    @DisplayName("seeking moves the reported position")
    void seekMovesThePosition(@TempDir Path directory) throws IOException {
        Path wav = writeWav(directory.resolve("five-seconds.wav"), 5, 2);
        source = new LocalFileAudioSource();
        source.load(wav);

        source.seek(Duration.ofMillis(3200));

        assertEquals(3200, source.position().toMillis(), 5);
    }

    @Test
    @DisplayName("seeking past either end lands inside the track")
    void seekClampsToTheTrack(@TempDir Path directory) throws IOException {
        Path wav = writeWav(directory.resolve("two-seconds.wav"), 2, 2);
        source = new LocalFileAudioSource();
        source.load(wav);

        source.seek(Duration.ofSeconds(-30));
        assertEquals(Duration.ZERO, source.position());

        source.seek(Duration.ofSeconds(30));
        assertTrue(source.position().toMillis() <= 2000,
                "a seek beyond the end must land in the track, not past it");
    }

    @Test
    @DisplayName("loading a second track replaces the first and rewinds")
    void loadingAgainReplacesTheTrack(@TempDir Path directory) throws IOException {
        Path first = writeWav(directory.resolve("first.wav"), 4, 2);
        Path second = writeWav(directory.resolve("second.wav"), 1, 2);
        source = new LocalFileAudioSource();
        source.load(first);
        source.seek(Duration.ofSeconds(2));

        source.load(second);

        assertEquals(second, source.file());
        assertEquals(Duration.ZERO, source.position(), "the new track starts at its beginning");
        assertEquals(1000, source.duration().toMillis(), 20);
    }

    @Test
    @DisplayName("transport calls with nothing loaded do nothing rather than fail")
    void transportIsSafeWithNothingLoaded() {
        source = new LocalFileAudioSource();

        source.play();
        source.pause();
        source.stop();
        source.seek(Duration.ofSeconds(10));

        assertFalse(source.isLoaded());
        assertFalse(source.isPlaying());
        assertEquals(Duration.ZERO, source.position());
        assertEquals(Duration.ZERO, source.duration());
    }

    @Test
    @DisplayName("closing twice is harmless, and a closed source refuses to load")
    void closeIsIdempotent(@TempDir Path directory) throws IOException {
        Path wav = writeWav(directory.resolve("one.wav"), 1, 2);
        source = new LocalFileAudioSource();
        source.load(wav);

        source.close();
        source.close();

        assertThrows(AudioException.class, () -> source.load(wav));
    }

    @Test
    @DisplayName("volume is clamped to the usable range")
    void volumeIsClamped() {
        source = new LocalFileAudioSource();

        source.setVolume(3.5);
        assertEquals(1.0, source.volume());

        source.setVolume(-2);
        assertEquals(0.0, source.volume());
    }

    @Test
    @DisplayName("a track that has played out plays again from the top when asked")
    void playingAnExhaustedTrackRewinds(@TempDir Path directory) throws Exception {
        // The one behaviour here that genuinely needs a sound card, because it is about what
        // happens after the last block has actually been rendered. Skipped rather than failed on a
        // machine with no audio output, and silent when it does run.
        AudioFormat format = new AudioFormat(RATE, 16, 2, true, false);
        assumeTrue(AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, format)),
                "no audio output on this machine");

        Path wav = writeWav(directory.resolve("brief.wav"), 0.25, 2);
        source = new LocalFileAudioSource();
        source.setVolume(0);
        source.load(wav);

        CountDownLatch firstEnd = new CountDownLatch(1);
        CountDownLatch secondEnd = new CountDownLatch(1);
        source.setOnEndOfMedia(() -> {
            if (firstEnd.getCount() > 0) {
                firstEnd.countDown();
            } else {
                secondEnd.countDown();
            }
        });

        AtomicLong replayedBytes = new AtomicLong();
        AtomicBoolean counting = new AtomicBoolean();
        source.addPcmListener((buffer, offset, length) -> {
            if (counting.get()) {
                replayedBytes.addAndGet(length);
            }
        });

        source.play();
        assertTrue(firstEnd.await(5, TimeUnit.SECONDS), "the track never reported reaching its end");
        assertFalse(source.isPlaying(), "playing stops when the track runs out");

        counting.set(true);
        source.play();

        // Two separate failures are pinned here, and each hides behind the other's symptom:
        // reaching the end again proves the retired playback thread was replaced rather than left
        // dead in the field, and the byte count proves the exhausted stream was actually rewound
        // instead of read straight to an immediate end-of-file.
        assertTrue(secondEnd.await(5, TimeUnit.SECONDS),
                "the second play never started - the dead playback thread was still in the field");
        assertTrue(replayedBytes.get() >= 44100,
                "only " + replayedBytes.get() + " bytes came out on replay; the stream was at its "
                        + "end and play() did not rewind it");
    }

    @Test
    @DisplayName("the taps see the whole track, as stereo at the playback rate, whatever it was")
    void everyBlockReachesTheTapsInOneFormat(@TempDir Path directory) throws Exception {
        AudioFormat format = new AudioFormat(RATE, 16, 2, true, false);
        assumeTrue(AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, format)),
                "no audio output on this machine");

        // Mono at half the playback rate: the file that needs both conversions, and the one whose
        // byte count says unambiguously which of them actually happened.
        Path wav = writeWav(directory.resolve("tapped.wav"), 0.5, 1, 22050f);
        source = new LocalFileAudioSource();
        source.setVolume(0);

        AtomicLong bytesSeen = new AtomicLong();
        AtomicBoolean wholeFrames = new AtomicBoolean(true);
        source.addPcmListener((buffer, offset, length) -> {
            if (length % 4 != 0) {
                wholeFrames.set(false);
            }
            bytesSeen.addAndGet(length);
        });

        source.load(wav);
        // The silent block published on load is not part of the track.
        bytesSeen.set(0);
        CountDownLatch finished = new CountDownLatch(1);
        source.setOnEndOfMedia(finished::countDown);
        source.play();
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        // Half a second at the playback format is 88200 bytes. Un-resampled it would be 44100 and
        // un-widened 22050, so this number alone says the conversion chain ran to the end - and
        // that the taps were given every block of it rather than a sample of them.
        assertEquals(88200, bytesSeen.get(), 4096,
                "the taps saw " + bytesSeen.get() + " bytes; 44100 means it was never resampled "
                        + "and 22050 means it was never widened to stereo");
        assertTrue(wholeFrames.get(), "a block split mid-frame would deinterleave into noise");
    }

}
