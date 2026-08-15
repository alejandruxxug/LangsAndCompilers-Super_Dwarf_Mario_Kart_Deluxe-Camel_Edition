package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Running and stopping the child process, against a stub that behaves like the real one.
 *
 * <p>A stub rather than go-librespot itself, because the real daemon needs a Spotify Premium
 * account and a browser, and a build that depends on either is a build that fails on somebody
 * else's machine. What is under test here is entirely this side of the process boundary: that the
 * authorisation link is recognised, that the log is captured, and above all <strong>that the child
 * dies</strong>.
 *
 * <p>The stub prints the real message verbatim - {@code "to complete authentication visit the
 * following link: %s"}, which is what {@code session/session.go} logs - so the pattern is matched
 * against the thing it will actually meet rather than against a paraphrase of it.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "The stub is a shell script, and the Spotify "
        + "path is POSIX-only in any case: it needs a named pipe")
class SpotifyDaemonTest {

    private String previousHome;

    /**
     * Points the application home at a scratch directory.
     *
     * <p>Starting a daemon writes a configuration file and creates a FIFO, and doing that in the
     * user's real {@code ~/.superdwarfkart} during a test run would overwrite a configuration and
     * leave a pipe behind.
     */
    @BeforeEach
    void redirectHome(@TempDir Path home) {
        previousHome = System.getProperty(AppConfig.HOME_OVERRIDE_PROPERTY);
        System.setProperty(AppConfig.HOME_OVERRIDE_PROPERTY, home.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousHome == null) {
            System.clearProperty(AppConfig.HOME_OVERRIDE_PROPERTY);
        } else {
            System.setProperty(AppConfig.HOME_OVERRIDE_PROPERTY, previousHome);
        }
    }

    @Test
    @DisplayName("the authorisation link is picked out of the log, in the real message's wording")
    void theAuthorisationLinkIsRecognised(@TempDir Path dir) throws Exception {
        // Single-quoted in the shell so the double quotes survive into the output. logrus wraps its
        // message in them, and an earlier version of this test used double quotes here - which the
        // shell strips, so the line under test was not the line the daemon actually prints and the
        // trailing-quote bug sailed straight through.
        Path stub = stub(dir, """
                #!/bin/sh
                echo 'time="2026-08-13T21:19:04-05:00" level=info msg="running go-librespot 0.8.0"'
                echo 'time="2026-08-13T21:19:04-05:00" level=info msg="to complete authentication visit the following link: https://accounts.spotify.com/authorize?client_id=abc&code_challenge_method=S256&redirect_uri=http%3A%2F%2F127.0.0.1%3A5555%2Flogin&response_type=code&scope=user-library-read+user-top-read"'
                sleep 30
                """);

        SpotifyDaemon daemon = new SpotifyDaemon(stub);
        CountDownLatch seen = new CountDownLatch(1);
        AtomicReference<String> url = new AtomicReference<>();
        daemon.setOnAuthorizationUrl(link -> {
            url.set(link);
            seen.countDown();
        });

        try {
            daemon.start();
            assertTrue(seen.await(10, TimeUnit.SECONDS), "the link was never reported");
            assertNotNull(url.get());
            assertTrue(url.get().startsWith("https://accounts.spotify.com/authorize"), url.get());
            // The whole URL, query string included - a link cut at the first ampersand authorises
            // nothing and the failure would look like Spotify rejecting the login.
            assertTrue(url.get().contains("response_type=code"), url.get());
            assertEquals(url.get(), daemon.authorizationUrl());

            // The assertion that matters, and the one that was missing. logrus closes its quoted
            // message right after the URL, so a pattern that stops at whitespace takes the quote
            // too - and the link is then correct except for one trailing character, which
            // URI.create rejects and the browser never opens.
            assertFalse(url.get().endsWith("\""), "the log's closing quote was taken as part of the URL");
            assertDoesNotThrow(() -> java.net.URI.create(url.get()),
                    "the link has to survive URI.create, which is what opens the browser");
            assertTrue(url.get().endsWith("user-top-read"), url.get());
        } finally {
            daemon.stop();
        }
    }

    @Test
    @DisplayName("a log line that is not the link leaves the daemon waiting for one")
    void ordinaryLogLinesAreNotMistakenForTheLink(@TempDir Path dir) throws Exception {
        Path stub = stub(dir, """
                #!/bin/sh
                echo "msg=connected to accounts.spotify.com"
                echo "msg=see https://github.com/devgianlu/go-librespot for help"
                sleep 30
                """);

        SpotifyDaemon daemon = new SpotifyDaemon(stub);
        try {
            daemon.start();
            // Waits for the lines to have been read rather than for a fixed interval, so this
            // asserts that they were seen and rejected instead of that they had not arrived yet.
            assertTrue(awaitLogLines(daemon, 2), "the stub's output was never read");
            assertNull(daemon.authorizationUrl());
        } finally {
            daemon.stop();
        }
    }

    /**
     * Waits until the daemon has captured at least this many log lines.
     *
     * @param daemon the daemon to watch
     * @param count  how many lines to wait for
     * @return whether they arrived inside the timeout
     */
    private static boolean awaitLogLines(SpotifyDaemon daemon, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (daemon.recentLog().size() >= count) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    /**
     * The one that matters most.
     *
     * <p>An orphaned daemon holds a Spotify session and keeps the API port bound, and the next
     * launch simply finds the port taken - Spotify does not work and nothing on screen says why.
     */
    @Test
    @DisplayName("stopping kills the child, and a child that ignores the request is killed anyway")
    void stoppingKillsTheChild(@TempDir Path dir) throws Exception {
        // Traps SIGTERM and keeps running, which is exactly the process that would be left behind
        // if destroyForcibly were not there behind the grace period.
        Path stub = stub(dir, """
                #!/bin/sh
                trap '' TERM
                sleep 60
                """);

        SpotifyDaemon daemon = new SpotifyDaemon(stub);
        daemon.start();
        long pid = daemon.pid();
        assertTrue(pid > 0);
        assertTrue(daemon.isRunning());

        daemon.stop();

        assertFalse(daemon.isRunning(), "the daemon must not survive stop()");
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                "process " + pid + " is still alive after stop()");
    }

    @Test
    @DisplayName("the daemon is told to use the private config directory, not a default one")
    void theConfigDirectoryIsPassed(@TempDir Path dir) throws Exception {
        Path marker = dir.resolve("args.txt");
        Path stub = stub(dir, """
                #!/bin/sh
                echo "$@" > "%s"
                sleep 5
                """.formatted(marker));

        SpotifyDaemon daemon = new SpotifyDaemon(stub);
        try {
            daemon.start();
            // Polled rather than slept on: a fixed wait is a race against a loaded machine, and
            // losing it here throws NoSuchFileException rather than failing an assertion.
            assertTrue(awaitFile(marker), "the stub never recorded its arguments");
            String args = Files.readString(marker);
            assertTrue(args.contains("--config_dir"), args);
            assertTrue(args.contains(SpotifyConfig.configDir().toString()), args);
        } finally {
            daemon.stop();
        }
    }

    /**
     * Waits for a file to appear and be non-empty.
     *
     * @param file the file to wait for
     * @return whether it arrived inside the timeout
     */
    private static boolean awaitFile(Path file) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try {
                if (Files.isRegularFile(file) && Files.size(file) > 0) {
                    return true;
                }
            } catch (IOException e) {
                // Being written to right now; look again.
            }
            Thread.sleep(25);
        }
        return false;
    }

    @Test
    @DisplayName("starting writes the configuration and creates the pipe")
    void startingPreparesTheConfigurationAndPipe(@TempDir Path dir) throws Exception {
        SpotifyDaemon daemon = new SpotifyDaemon(stub(dir, "#!/bin/sh\nsleep 5\n"));
        try {
            daemon.start();

            assertTrue(Files.isRegularFile(SpotifyConfig.configFile()));
            assertTrue(Files.exists(SpotifyConfig.fifoPath()));
            assertTrue(Files.readString(SpotifyConfig.configFile()).contains("audio_backend: pipe"));
        } finally {
            daemon.stop();
        }
    }

    @Test
    @DisplayName("a daemon that exits on its own is reported rather than assumed to be running")
    void anEarlyExitIsReported(@TempDir Path dir) throws Exception {
        // The real case is a port already bound by an orphan from a previous run. Left unreported,
        // the session would sit on "starting" forever.
        Path stub = stub(dir, """
                #!/bin/sh
                echo "msg=failed to bind: address already in use"
                exit 1
                """);

        SpotifyDaemon daemon = new SpotifyDaemon(stub);
        CountDownLatch exited = new CountDownLatch(1);
        daemon.setOnExit(exited::countDown);

        daemon.start();

        assertTrue(exited.await(10, TimeUnit.SECONDS), "the exit was never reported");
        assertFalse(daemon.recentLog().isEmpty(), "the reason has to survive the process");
        assertTrue(daemon.recentLog().stream().anyMatch(line -> line.contains("address already in use")));
    }

    /**
     * Writes an executable shell script standing in for the daemon.
     *
     * @param dir    where to write it
     * @param script the script body
     * @return the executable
     */
    private static Path stub(Path dir, String script) throws IOException {
        Path file = dir.resolve("go-librespot-stub");
        Files.writeString(file, script);
        assertTrue(file.toFile().setExecutable(true), "could not mark the stub executable");
        return file;
    }
}
