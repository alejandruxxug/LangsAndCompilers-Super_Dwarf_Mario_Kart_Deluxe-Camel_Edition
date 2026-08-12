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
        service.request(file);
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
        service.request(file);
        assertTrue(service.await(PATIENCE));
        assertFalse(service.status().fromCache(), "the first pass had nothing to read");
        double firstBpm = service.beatmap().bpm();
        service.close();

        service = new BeatmapService(cache, new BeatmapAnalyzer());
        service.request(file);
        assertTrue(service.await(PATIENCE));

        assertTrue(service.status().fromCache(), "the analysis was run a second time");
        assertEquals(firstBpm, service.beatmap().bpm());
    }

    @Test
    @DisplayName("asking for the same track again does not start the work over")
    void repeatedRequestIsIgnored(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(file);
        assertTrue(service.await(PATIENCE));
        BeatmapService.Status settled = service.status();

        service.request(file);

        assertSame(settled, service.status(),
                "a listener that fires for reasons other than a song change must be free");
    }

    @Test
    @DisplayName("requesting another track abandons the first rather than queueing behind it")
    void newRequestSupersedesTheOld(@TempDir Path directory) throws IOException {
        Path first = track(directory, "first.wav");
        Path second = track(directory, "second.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(first);
        service.request(second);
        assertTrue(service.await(PATIENCE));

        assertEquals(second, service.status().file(),
                "the status must describe what is playing now, not what was skipped past");
    }

    @Test
    @DisplayName("clearing the request returns to the idle state")
    void requestingNullClears(@TempDir Path directory) throws IOException {
        Path file = track(directory, "song.wav");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());
        service.request(file);
        assertTrue(service.await(PATIENCE));

        service.request(null);

        assertEquals(BeatmapService.Stage.NONE, service.status().stage());
        assertSame(Beatmap.EMPTY, service.beatmap());
    }

    @Test
    @DisplayName("a file that cannot be analysed reports why and leaves the app running")
    void failureIsReportedRatherThanThrown(@TempDir Path directory) throws IOException {
        Path text = Files.writeString(directory.resolve("notes.txt"), "not audio at all");
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        service.request(text);
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

        service.request(file);
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
        service.request(file);

        service.close();
        service.close();
        service.request(file);

        assertEquals(BeatmapService.Stage.NONE, service.status().stage());
    }

    @Test
    @DisplayName("waiting with nothing in flight returns rather than hanging")
    void awaitWithNothingRunningReturns(@TempDir Path directory) {
        service = new BeatmapService(new BeatmapCache(directory), new BeatmapAnalyzer());

        assertTrue(service.await(Duration.ofMillis(50)));
    }
}
