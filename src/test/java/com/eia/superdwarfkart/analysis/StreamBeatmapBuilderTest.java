package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building a course out of audio that has no file to read.
 *
 * <p>A Spotify track only exists as the bytes going past on the way to the sound card, so this is
 * the only route to a beatmap for one. The behaviour that matters is not that it produces
 * <em>a</em> beatmap - it is that it produces <strong>the same</strong> beatmap the file analyser
 * would, and that it refuses to produce one at all when the audio it heard cannot be trusted.
 *
 * <p>Nothing here needs a sound card, a daemon or a network. The builder is a
 * {@code PcmListener}, so a test can simply be the thing playing the track.
 */
@DisplayName("Beatmaps built from the playback tap")
class StreamBeatmapBuilderTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    /** How long a synthetic track runs. Long enough for a tempo histogram to mean something. */
    private static final double TRACK_SECONDS = 6;

    /** The tempo the clicks are placed at. */
    private static final double TRACK_BPM = 120;

    private static final String KEY = "0123456789abcdef";

    private StreamBeatmapBuilder builder;

    @AfterEach
    void closeBuilder() {
        if (builder != null) {
            builder.close();
        }
    }

    /** @return the mono samples of the track both routes are given */
    private static float[] samples() {
        return ClickTrack.mono(TRACK_SECONDS, ClickTrack.beatsAt(11, TRACK_BPM, 0.35));
    }

    /**
     * Plays audio at the builder the way a sound card would: in blocks, in order.
     *
     * @param target     the builder
     * @param pcm        the whole track, in the playback format
     * @param blockBytes how much to hand over at a time
     */
    private static void play(StreamBeatmapBuilder target, byte[] pcm, int blockBytes) {
        for (int at = 0; at < pcm.length; at += blockBytes) {
            target.pcm(pcm, at, Math.min(blockBytes, pcm.length - at));
        }
    }

    @Nested
    @DisplayName("against the file analyser")
    class AgainstTheFile {

        @Test
        @DisplayName("the same audio heard gives the same beatmap as the same audio read")
        void streamAndFileAgree(@TempDir Path directory) throws IOException {
            float[] mono = samples();
            Path file = ClickTrack.writeWav(directory.resolve("song.wav"), TRACK_SECONDS,
                    ClickTrack.beatsAt(11, TRACK_BPM, 0.35));
            Beatmap read = new BeatmapAnalyzer().analyze(file, KEY, null);

            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            play(builder, ClickTrack.interleave(mono), 4096);
            Beatmap heard = builder.finishAndWait(PATIENCE);

            assertNotNull(heard, "the whole track was played, so there is a beatmap");
            assertEquals(read.bpm(), heard.bpm(), 0.001,
                    "a streamed track and a local copy of it must generate the identical course, "
                            + "or a score earned on one means nothing on the other");
            assertArrayEquals(read.onsets(), heard.onsets(), 1e-9);
            assertArrayEquals(read.strongBeats(), heard.strongBeats(), 1e-9);
            assertArrayEquals(read.strongBeatStrengths(), heard.strongBeatStrengths(), 1e-9);
            assertEquals(read.durationSeconds(), heard.durationSeconds(), 0.05);
        }

        @Test
        @DisplayName("the block size the card happens to use changes nothing")
        void blockSizeDoesNotMatter() {
            byte[] pcm = ClickTrack.interleave(samples());

            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            play(builder, pcm, 4096);
            Beatmap even = builder.finishAndWait(PATIENCE);

            builder.arm(KEY, TRACK_SECONDS);
            // Deliberately not a multiple of a hop, a window or a frame count anybody chose: a
            // curve that depended on where the block boundaries fell would be a different course
            // on a different sound card.
            play(builder, pcm, 1234 * AppConfig.BYTES_PER_FRAME);
            Beatmap odd = builder.finishAndWait(PATIENCE);

            assertNotNull(even);
            assertNotNull(odd);
            assertEquals(even.bpm(), odd.bpm(), 0.001);
            assertArrayEquals(even.onsets(), odd.onsets(), 1e-9);
        }

        @Test
        @DisplayName("the tempo that comes back is the tempo that was played")
        void findsTheRealTempo() {
            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            play(builder, ClickTrack.interleave(samples()), 4096);

            Beatmap heard = builder.finishAndWait(PATIENCE);

            assertNotNull(heard);
            assertEquals(TRACK_BPM, heard.bpm(), 2);
            assertEquals(KEY, heard.sourceHash(), "it is stored under the key it was armed with");
            assertEquals(AppConfig.ANALYZER_VERSION, heard.analyzerVersion());
        }
    }

    @Nested
    @DisplayName("audio that cannot be trusted")
    class Refusals {

        @Test
        @DisplayName("a track skipped part way through produces nothing, not a short course")
        void aPartialTrackIsRefused() {
            byte[] pcm = ClickTrack.interleave(samples());

            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            // Half the track, then the user pressed next.
            play(builder, java.util.Arrays.copyOf(pcm, pcm.length / 2), 4096);

            assertNull(builder.finishAndWait(PATIENCE),
                    "half a track is not a short beatmap, it is one that says the song ends where "
                            + "the listener stopped listening - and it would be cached and believed");
        }

        @Test
        @DisplayName("abandoning straight after finishing does not throw the track's audio away")
        void abandoningAfterFinishingKeepsWhatWasHeard() {
            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            play(builder, ClickTrack.interleave(samples()), 4096);

            // Exactly what BeatmapService.finishStream() does, and the shape of the bug it had:
            // handing the blocks over is asynchronous, so at this instant almost all of the track
            // is still queued. An abandon that invalidated queued work would leave the derivation
            // with a fraction of a second of audio - and every streamed course came back empty,
            // with nothing thrown and nothing logged.
            java.util.concurrent.atomic.AtomicReference<Beatmap> built =
                    new java.util.concurrent.atomic.AtomicReference<>();
            builder.finish(built::set);
            builder.abandon();
            assertTrue(builder.awaitIdle(PATIENCE));

            assertNotNull(built.get(), "the whole track was played before finishing");
            assertEquals(TRACK_SECONDS, built.get().durationSeconds(), 0.05);
            assertEquals(TRACK_BPM, built.get().bpm(), 2);
        }

        @Test
        @DisplayName("audio after a seek is not appended to what came before it")
        void abandoningStopsCollecting() {
            byte[] pcm = ClickTrack.interleave(samples());

            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            play(builder, pcm, 4096);
            builder.abandon();

            assertFalse(builder.isArmed());
            play(builder, pcm, 4096);
            assertNull(builder.finishAndWait(PATIENCE),
                    "nothing is armed, so there is nothing to finish");
        }

        @Test
        @DisplayName("nothing is collected until a track is armed")
        void collectsNothingBeforeArming() {
            builder = new StreamBeatmapBuilder();

            play(builder, ClickTrack.interleave(samples()), 4096);

            assertFalse(builder.isArmed());
            assertEquals(0, builder.heardSeconds(), 1e-9);
            assertNull(builder.finishAndWait(PATIENCE));
        }

        @Test
        @DisplayName("arming a second track does not inherit the first one's audio")
        void armingStartsAgain() {
            byte[] pcm = ClickTrack.interleave(samples());

            builder = new StreamBeatmapBuilder();
            builder.arm("first", TRACK_SECONDS);
            play(builder, pcm, 4096);

            builder.arm("second", TRACK_SECONDS);
            play(builder, pcm, 4096);
            Beatmap second = builder.finishAndWait(PATIENCE);

            assertNotNull(second);
            assertEquals("second", second.sourceHash());
            assertEquals(TRACK_SECONDS, second.durationSeconds(), 0.05,
                    "the second track must be its own length, not two tracks' worth - a curve that "
                            + "carried the previous song's audio would report the wrong tempo and "
                            + "nothing anywhere would say so");
        }
    }

    @Nested
    @DisplayName("what it reports while it is listening")
    class Progress {

        @Test
        @DisplayName("progress tracks how much of the track has been heard")
        void progressAdvances() {
            byte[] pcm = ClickTrack.interleave(samples());

            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, TRACK_SECONDS);
            assertEquals(0, builder.progress(), 0.01);

            play(builder, java.util.Arrays.copyOf(pcm, pcm.length / 2), 4096);
            builder.finishAndWait(PATIENCE);

            // finishAndWait drains the worker, which is what advances the reading. Read before it,
            // this is a race against a background thread rather than a measurement.
            assertEquals(TRACK_SECONDS / 2, builder.heardSeconds(), 0.1);
        }

        @Test
        @DisplayName("a track of unknown length reports no progress rather than a wrong one")
        void unknownLengthHasNoProgress() {
            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, 0);

            assertEquals(-1, builder.progress(), 1e-9);
        }

        @Test
        @DisplayName("a track of unknown length is still kept when it finishes")
        void unknownLengthIsStillUsable() {
            builder = new StreamBeatmapBuilder();
            builder.arm(KEY, 0);
            play(builder, ClickTrack.interleave(samples()), 4096);

            assertNotNull(builder.finishAndWait(PATIENCE),
                    "there is nothing to compare against, so the only honest choice is to trust "
                            + "what was heard - refusing would mean no course ever for a track "
                            + "whose length was not recorded");
        }
    }

    @Test
    @DisplayName("a closed builder collects nothing and answers rather than hanging")
    void closingIsSafe() {
        builder = new StreamBeatmapBuilder();
        builder.arm(KEY, TRACK_SECONDS);
        builder.close();

        play(builder, ClickTrack.interleave(samples()), 4096);
        assertNull(builder.finishAndWait(Duration.ofSeconds(2)));
        assertFalse(builder.isArmed());
    }

    @Test
    @DisplayName("the playback thread is never made to wait for a transform")
    void theTapReturnsImmediately() {
        byte[] pcm = ClickTrack.interleave(samples());
        builder = new StreamBeatmapBuilder();
        builder.arm(KEY, TRACK_SECONDS);

        long before = System.nanoTime();
        play(builder, pcm, 4096);
        long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

        // Six seconds of audio handed over as fast as the loop manages. The transforms alone take
        // longer than this bound, so passing it is evidence they happened somewhere else - which
        // is the whole of ground rule 4 as it applies to this class.
        assertTrue(elapsedMillis < 250,
                "handing over " + (pcm.length / AppConfig.BYTES_PER_FRAME) + " frames took "
                        + elapsedMillis + " ms; the analysis is running on the caller's thread");
        assertNotNull(builder.finishAndWait(PATIENCE));
    }
}
