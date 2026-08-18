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
 * <p><strong>Draining it is not the same as keeping it, and the second bug was the other half of
 * the first.</strong> The reader consumed the pipe correctly and then discarded everything it read
 * while stopped, banking the frames onto the clock. That is most of a second of music the listener
 * never hears at every pause, because the daemon resumes from where it had decoded to rather than
 * from where the sound stopped - reported as "when I pause a Spotify song and resume it skips a bit
 * of the song". It is held now, and the clock stays where the sound did.
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

        writeToPipe(TOTAL_BYTES);

        assertTrue(everything.await(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "A stopped source must go on reading the pipe, or go-librespot blocks inside "
                        + "write() and its whole HTTP API stops answering. Consumed "
                        + received.get() + " of " + TOTAL_BYTES + " bytes.");
    }

    @Test
    @DisplayName("audio decoded while stopped is kept for the card, not written off the clock")
    void audioDecodedWhileStoppedIsHeldRatherThanSkipped() throws Exception {
        CountDownLatch everything = new CountDownLatch(1);
        AtomicLong received = new AtomicLong();
        source.addPcmListener((buffer, offset, length) -> {
            if (received.addAndGet(length) >= TOTAL_BYTES) {
                everything.countDown();
            }
        });

        source.load("spotify:track:0000000000000000000000");
        assertEquals(0, source.position().toMillis(), "a freshly loaded track starts at zero");

        writeToPipe(TOTAL_BYTES);
        assertTrue(everything.await(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the reader did not consume the pipe");

        // This is the pause skip, in the one place it can be measured without a sound card. The
        // daemon is only paced by our reads, so it runs at decode speed the moment playback stops
        // and empties several hundred milliseconds of music into the pipe before it acts on the
        // pause. That music has to come off the pipe or the daemon's whole API wedges - and the
        // version of this class that then threw it away lost every byte of it, because a resume
        // carries on from where the daemon reached rather than from where the listener was.
        // Held instead, so the clock stays where the music stopped and the card gets it on resume.
        assertEquals(0, source.position().toMillis(),
                "audio waiting for the sound card is not audio the listener has missed, so the "
                        + "clock must not run past it - that gap is exactly the resume skip");
    }

    @Test
    @DisplayName("what will not fit in the hold is written off the clock, so it cannot fall behind")
    void audioBeyondTheHoldIsBankedOntoThePosition() throws Exception {
        int overflow = 16 * BLOCK_BYTES;
        int total = SpotifyAudioSource.HOLD_CAPACITY_BYTES + overflow;

        CountDownLatch everything = new CountDownLatch(1);
        AtomicLong received = new AtomicLong();
        source.addPcmListener((buffer, offset, length) -> {
            if (received.addAndGet(length) >= total) {
                everything.countDown();
            }
        });

        source.load("spotify:track:0000000000000000000000");
        writeToPipe(total);
        assertTrue(everything.await(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the reader did not consume the pipe: got " + received.get() + " of " + total);

        // The hold is bounded on purpose - a daemon that never acts on a pause must not be able to
        // turn a fault this class can absorb into one it cannot. Past the cap the audio genuinely
        // is lost, and the daemon resumes from after it, so the clock has to move past it too or
        // the runner's lookahead reads a position behind the music for the rest of the track.
        long expectedMillis = Math.round(
                overflow / (double) AppConfig.BYTES_PER_FRAME / AppConfig.SAMPLE_RATE * 1000);
        long actualMillis = source.position().toMillis();
        assertTrue(Math.abs(actualMillis - expectedMillis) <= 5,
                "only the overflow should reach the clock: expected about " + expectedMillis
                        + " ms, got " + actualMillis + " ms");
    }

    /**
     * Writes audio into the FIFO from a thread of its own.
     *
     * <p>On its own thread because that is the failure being checked: with nothing reading, this
     * write blocks once the kernel buffer fills, and a blocked write on the test thread would hang
     * the run rather than fail it.
     *
     * @param bytes how much to write, a whole number of blocks
     */
    private void writeToPipe(int bytes) {
        Thread writer = new Thread(() -> {
            try (OutputStream out = new FileOutputStream(fifo.toFile())) {
                byte[] block = new byte[BLOCK_BYTES];
                for (int i = 0; i < bytes / BLOCK_BYTES; i++) {
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
