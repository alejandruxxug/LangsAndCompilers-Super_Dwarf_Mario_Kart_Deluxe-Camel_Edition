package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.spotify.SpotifyApi;
import com.eia.superdwarfkart.spotify.SpotifyConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Spotify source's pipe reader, driven against a real FIFO and a stub daemon.
 *
 * <p><strong>This class exists for one bug, and it is worth naming.</strong> The reader used to park
 * whenever playback stopped, which left the FIFO unread. go-librespot's pipe driver has no pacing
 * and blocks inside {@code write} the moment the 4 KB kernel buffer fills; v0.8.0 then serves
 * <em>every</em> HTTP request from the single goroutine that was blocked, over an unbuffered channel
 * with no timeout. So a paused player wedged the daemon's whole API, and because {@code play()}
 * asked {@code /player/resume} <em>before</em> restarting the reader, the two waited on each other
 * for good: the pipe could not drain until the daemon answered and the daemon could not answer until
 * the pipe drained. Every later press of play threw {@code "go-librespot would not resume playback"},
 * which was a five second timeout being reported as a refusal.
 *
 * <p>None of that is visible to a unit test that mocks the API, and no screenshot shows it. What
 * pins it is the invariant underneath: <strong>the pipe is consumed in every state</strong>. These
 * tests write far more than any FIFO buffer holds while the source is stopped, and a reader that
 * parks fails them by hanging rather than by disagreeing.
 *
 * <p>A real FIFO and a real socket rather than seams, for the reason {@code SpotifyApiTest} gives:
 * what breaks here is the behaviour of a pipe, and a mock would agree with whatever the code did.
 */
class SpotifyAudioSourceTest {

    /** Comfortably more than any FIFO buffer: macOS starts at 4 KB and grows to 64 KB. */
    private static final int BLOCKS = 64;
    private static final int BLOCK_BYTES = 4096;
    private static final int TOTAL_BYTES = BLOCKS * BLOCK_BYTES;

    /** How long the reader is given to consume it all. It manages this in milliseconds. */
    private static final long DRAIN_TIMEOUT_SECONDS = 10;

    private String previousHome;
    private HttpServer daemon;
    private SpotifyApi api;
    private SpotifyAudioSource source;
    private Path fifo;

    @BeforeEach
    void startStubDaemon(@TempDir Path home) throws IOException {
        previousHome = System.getProperty(AppConfig.HOME_OVERRIDE_PROPERTY);
        System.setProperty(AppConfig.HOME_OVERRIDE_PROPERTY, home.toString());

        // POSIX only: the pipe is made by running mkfifo. Nothing else here is platform specific.
        try {
            fifo = SpotifyConfig.createFifo();
        } catch (IOException e) {
            assumeTrue(false, "mkfifo is not available: " + e.getMessage());
        }

        daemon = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Every player command is accepted. What is under test is this side's reading, not the
        // daemon's answers - and a stub that answered slowly would only be testing the stub.
        daemon.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        daemon.start();

        api = new SpotifyApi("http://127.0.0.1:" + daemon.getAddress().getPort());
        source = new SpotifyAudioSource(api);
    }

    @AfterEach
    void stop() {
        if (source != null) {
            source.close();
        }
        if (daemon != null) {
            daemon.stop(0);
        }
        if (previousHome == null) {
            System.clearProperty(AppConfig.HOME_OVERRIDE_PROPERTY);
        } else {
            System.setProperty(AppConfig.HOME_OVERRIDE_PROPERTY, previousHome);
        }
    }

    @Test
    @DisplayName("the pipe is drained while playback is stopped, so the daemon can never block")
    void theStoppedSourceStillConsumesThePipe() throws Exception {
        AtomicLong received = new AtomicLong();
        CountDownLatch everything = new CountDownLatch(1);
        source.addPcmListener((buffer, offset, length) -> {
            if (received.addAndGet(length) >= TOTAL_BYTES) {
                everything.countDown();
            }
        });

        // Loaded but never played: playing is false, which is exactly the state the old reader
        // parked in. The daemon is told nothing more after this.
        source.load("spotify:track:0000000000000000000000");

        writeToPipeOnItsOwnThread();

        assertTrue(everything.await(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "A stopped source must go on reading the pipe, or go-librespot blocks inside "
                        + "write() and its whole HTTP API stops answering. Consumed "
                        + received.get() + " of " + TOTAL_BYTES + " bytes.");
    }

    @Test
    @DisplayName("audio decoded but never heard still moves the clock")
    void droppedAudioIsBankedOntoThePosition() throws Exception {
        CountDownLatch everything = new CountDownLatch(1);
        AtomicLong received = new AtomicLong();
        source.addPcmListener((buffer, offset, length) -> {
            if (received.addAndGet(length) >= TOTAL_BYTES) {
                everything.countDown();
            }
        });

        source.load("spotify:track:0000000000000000000000");
        assertEquals(0, source.position().toMillis(), "a freshly loaded track starts at zero");

        writeToPipeOnItsOwnThread();
        assertTrue(everything.await(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the reader did not consume the pipe");

        // The daemon resumes from after whatever it emitted, so frames that were dropped rather
        // than played are still time the track advanced by. Left out, the clock falls behind the
        // music at every pause - and the runner's lookahead reads off this clock.
        long expectedMillis = Math.round(
                TOTAL_BYTES / (double) AppConfig.BYTES_PER_FRAME / AppConfig.SAMPLE_RATE * 1000);
        long actualMillis = source.position().toMillis();
        assertTrue(Math.abs(actualMillis - expectedMillis) <= 5,
                "position should have advanced by the dropped audio: expected about "
                        + expectedMillis + " ms, got " + actualMillis + " ms");
    }

    /**
     * Writes the test's audio into the FIFO from a thread of its own.
     *
     * <p>On its own thread because that is the failure being checked: with nothing reading, this
     * write blocks once the kernel buffer fills, and a blocked write on the test thread would hang
     * the run rather than fail it.
     */
    private void writeToPipeOnItsOwnThread() {
        Thread writer = new Thread(() -> {
            try (OutputStream out = new FileOutputStream(fifo.toFile())) {
                byte[] block = new byte[BLOCK_BYTES];
                for (int i = 0; i < BLOCKS; i++) {
                    // Anything non-zero; the content is irrelevant, the flow is the point.
                    java.util.Arrays.fill(block, (byte) (i + 1));
                    out.write(block);
                }
                out.flush();
            } catch (IOException e) {
                // The assertion in the test reports this far better than a stack trace here would.
            }
        }, "test-spotify-pipe-writer");
        writer.setDaemon(true);
        writer.start();
    }
}
