package com.eia.superdwarfkart.analysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Analysis happening off to one side, with nobody waiting for it.
 *
 * <p>The behaviour worth pinning is not that it eventually produces a beatmap - it is that
 * {@link BeatmapService#request} <strong>returns immediately</strong> and that a caller can read a
 * consistent answer at any moment without a lock. Everything above this is a sixty-frame-a-second
 * render loop, and a request that blocked for the second and a half an analysis takes would drop
 * ninety frames every time the song changed.
 */
class BeatmapServiceTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);

    private BeatmapService service;

    @AfterEach
    void closeService() {
        if (service != null) {
            service.close();
        }
    }

    private static Path track(Path directory, String name) throws IOException {
        return ClickTrack.writeWav(directory.resolve(name), 6,
                ClickTrack.beatsAt(11, 120, 0.35));
    }

    @Test
    @DisplayName("nothing asked for is a defined state, not a null")
    void startsIdle(@TempDir Path directory) {
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        BeatmapService.Status status = service.status();

        assertNotNull(status);
        assertEquals(BeatmapService.Stage.NONE, status.stage());
        assertSame(Beatmap.EMPTY, status.beatmap());
        assertFalse(status.isReady());
    }

    @Test
    @DisplayName("a request returns at once and the answer arrives later")
    void requestDoesNotBlock(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        long before = System.nanoTime();
        service.request(file.toString());
        long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

        assertTrue(elapsedMillis < 250,
                "request() took " + elapsedMillis + " ms; the analysis is running on the caller");
        assertEquals(BeatmapService.Stage.ANALYZING, service.status().stage());

        assertTrue(service.await(PATIENCE));
        assertEquals(BeatmapService.Stage.READY, service.status().stage());
        assertEquals(120, service.beatmap().bpm(), 2);
    }

    @Test
    @DisplayName("the second time a track comes round it arrives from the cache")
    void secondRequestComesFromTheCache(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        BeatmapCache cache = new BeatmapCache(directory.resolve("cache"));

        service = new BeatmapService(cache, new BeatmapAnalyzer());
        service.request(file.toString());
        assertTrue(service.await(PATIENCE));
        assertFalse(service.status().fromCache(), "the first pass had nothing to read");
        double firstBpm = service.beatmap().bpm();
        service.close();

        service = new BeatmapService(cache, new BeatmapAnalyzer());
        service.request(file.toString());
        assertTrue(service.await(PATIENCE));

        assertTrue(service.status().fromCache(), "the analysis was run a second time");
        assertEquals(firstBpm, service.beatmap().bpm());
    }

    @Test
    @DisplayName("asking for the same track again does not start the work over")
    void repeatedRequestIsIgnored(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(file.toString());
        assertTrue(service.await(PATIENCE));
        BeatmapService.Status settled = service.status();

        service.request(file.toString());

        assertSame(settled, service.status(),
                "a listener that fires for reasons other than a song change must be free");
    }

    @Test
    @DisplayName("requesting another track abandons the first rather than queueing behind it")
    void newRequestSupersedesTheOld(@TempDir Path directory) throws IOException {
        Path first = track(directory, "first.wav");
        Path second = track(directory, "second.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(first.toString());
        service.request(second.toString());
        assertTrue(service.await(PATIENCE));

        assertEquals(second.toString(), service.status().source(),
                "the status must describe what is playing now, not what was skipped past");
    }

    @Test
    @DisplayName("clearing the request returns to the idle state")
    void requestingNullClears(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());
        service.request(file.toString());
        assertTrue(service.await(PATIENCE));

        service.request((String) null);

        assertEquals(BeatmapService.Stage.NONE, service.status().stage());
        assertSame(Beatmap.EMPTY, service.beatmap());
    }

    @Test
    @DisplayName("a file that cannot be analysed reports why and leaves the app running")
    void failureIsReportedRatherThanThrown(@TempDir Path directory) throws IOException {
        Path text = Files.writeString(directory.resolve("notes.txt"), "not audio at all");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(text.toString());
        assertTrue(service.await(PATIENCE));

        assertEquals(BeatmapService.Stage.FAILED, service.status().stage());
        assertNotNull(service.status().failure());
        assertSame(Beatmap.EMPTY, service.beatmap(), "a failed analysis has no course to offer");
    }

    @Test
    @DisplayName("beatmap() only hands out a finished map, never one still being built")
    void beatmapIsEmptyUntilReady(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(file.toString());
        assertSame(Beatmap.EMPTY, service.beatmap(),
                "a half-built course must never reach the game");

        assertTrue(service.await(PATIENCE));
        assertFalse(service.beatmap().isEmpty());
    }

    @Test
    @DisplayName("closing twice is harmless and a request afterwards does not fail")
    void closeIsIdempotent(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());
        service.request(file.toString());

        service.close();
        service.close();
        service.request(file.toString());

        assertEquals(BeatmapService.Stage.NONE, service.status().stage());
    }

    @Test
    @DisplayName("waiting with nothing in flight returns rather than hanging")
    void awaitWithNothingRunningReturns(@TempDir Path directory) {
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        assertTrue(service.await(Duration.ofMillis(50)));
    }

    /**
     * A song with no file at all.
     *
     * <p>This is the case the whole locator refactor exists for. Before it, a streamed song reached
     * here as a {@code null} path, the service went idle, and the runner drew "NO COURSE" for the
     * rest of the session - no exception, no log line, and a rhythm game that silently did not work
     * for half the library.
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("a track with no file")
    class Streamed {

        private static final String URI = "spotify:track:4cOdK2wGLETKBW3PvgPWqT";

        @Test
        @DisplayName("is listened to rather than declared unanalysable")
        void goesToListening(@TempDir Path directory) {
            service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

            service.request(URI, 6);
            assertTrue(service.await(PATIENCE));

            assertEquals(BeatmapService.Stage.LISTENING, service.status().stage());
            assertTrue(service.status().isAbout(URI));
            assertTrue(service.streamTap().isArmed(),
                    "the tap has to be collecting, or playing the track achieves nothing");
        }

        @Test
        @DisplayName("has a course the next time it comes round")
        void playingItBuildsTheCourse(@TempDir Path directory) {
            BeatmapCache cache = new BeatmapCache(directory);
            service = new BeatmapService(cache, new BeatmapAnalyzer());

            service.request(URI, 6);
            assertTrue(service.await(PATIENCE));

            play(service.streamTap());
            service.finishStream();
            assertTrue(service.awaitStream(PATIENCE));

            // The map is stored under the URI's key, so a later request is an ordinary cache hit
            // and the course is there before a note is played.
            service.close();
            service = new BeatmapService(cache, new BeatmapAnalyzer());
            service.request(URI, 6);
            assertTrue(service.await(PATIENCE));

            assertEquals(BeatmapService.Stage.READY, service.status().stage());
            assertTrue(service.status().fromCache());
            assertEquals(120, service.beatmap().bpm(), 2);
        }

        @Test
        @DisplayName("seeking gives the run up rather than filing a beatmap built across the jump")
        void seekingAbandonsTheRun(@TempDir Path directory) {
            service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());
            service.request(URI, 6);
            assertTrue(service.await(PATIENCE));

            service.abandonStream();

            assertFalse(service.streamTap().isArmed());
            play(service.streamTap());
            service.finishStream();
            assertTrue(service.awaitStream(PATIENCE));
            assertEquals(BeatmapService.Stage.LISTENING, service.status().stage(),
                    "nothing was collected, so nothing became ready");
        }

        @Test
        @DisplayName("moving to a local file stops it collecting that file's audio")
        void movingToAFileDisarmsTheTap(@TempDir Path directory) throws IOException {
            Path file = track(directory, "song.wav");
            service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());
            service.request(URI, 6);
            assertTrue(service.await(PATIENCE));
            assertTrue(service.streamTap().isArmed());

            service.request(file.toString());
            assertTrue(service.await(PATIENCE));

            assertFalse(service.streamTap().isArmed(),
                    "left armed, the local file's audio would be collected and filed under the "
                            + "Spotify track's key - a course for one song built from another");
        }

        /**
         * Plays six seconds of a 120 BPM click track at the tap.
         *
         * @param tap the builder to feed
         */
        private static void play(StreamBeatmapBuilder tap) {
            byte[] pcm = ClickTrack.interleave(
                    ClickTrack.mono(6, ClickTrack.beatsAt(11, 120, 0.35)));
            for (int at = 0; at < pcm.length; at += 4096) {
                tap.pcm(pcm, at, Math.min(4096, pcm.length - at));
            }
        }
    }
}
