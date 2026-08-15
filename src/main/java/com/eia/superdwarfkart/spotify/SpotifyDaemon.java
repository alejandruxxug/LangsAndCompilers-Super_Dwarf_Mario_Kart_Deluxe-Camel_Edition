package com.eia.superdwarfkart.spotify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the go-librespot daemon as a child of this process, and never leaves one behind.
 *
 * <p><strong>An orphaned daemon is the failure mode of this whole feature.</strong> It holds a
 * Spotify session, it keeps port {@value com.eia.superdwarfkart.app.AppConfig#SPOTIFY_API_PORT}
 * bound, and it is invisible - the next launch simply finds the port taken and Spotify silently
 * does not work, with nothing on screen to say why. So the child is destroyed on every exit path:
 * {@link #stop()} on the ordinary one, and a shutdown hook for the ones nobody planned, including
 * a crash and a kill of the JVM. {@link Process#destroyForcibly()} follows a grace period, because
 * a daemon that ignored the polite request is exactly the one that would be left running.
 *
 * <p>Nothing here starts by itself. The daemon is launched only when the user asks for Spotify,
 * which is the rule this feature is built around: anyone who never opens that view must never
 * learn it exists, and must certainly never have a subprocess started on their behalf.
 *
 * <h2>The login is the only step that needs a person</h2>
 *
 * <p>Configured for interactive credentials, the daemon prints an authorisation link on its first
 * run and waits. The link is picked out of its log by {@link #AUTH_LINK} - the message is
 * {@code "to complete authentication visit the following link: ..."} - and handed to the caller,
 * which opens a browser. The redirect goes to {@code http://127.0.0.1:<port>/login}, a server the
 * daemon itself is running, so the flow completes without the user copying anything. Credentials
 * then land in {@code state.json} and no later launch asks again.
 */
public final class SpotifyDaemon implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SpotifyDaemon.class.getName());

    /**
     * The authorisation link, as the daemon logs it.
     *
     * <p>Matched on the URL rather than on the sentence, so a reworded log message still works.
     * The message itself is {@code "to complete authentication visit the following link: %s"} and
     * goes to standard error, which is where logrus writes.
     *
     * <p><strong>The excluded quote is the whole point of this character class.</strong> logrus
     * writes structured output and wraps the message in double quotes, so the real line ends
     * {@code ...user-top-read"} - and {@code \S+} happily takes that closing quote as part of the
     * URL, because a quote is not whitespace. The result is a link that is correct for 766
     * characters and then has one character too many, which
     * {@code URI.create} rejects with {@code Illegal character in query at index 766} and the
     * browser never opens. Nothing else about the flow looks wrong: the link is found, the state
     * moves to "waiting for login", and the button simply does nothing.
     */
    private static final Pattern AUTH_LINK =
            Pattern.compile("(https://accounts\\.spotify\\.com/[^\\s\"']+)");

    /** How long the daemon is given to exit politely before it is killed. */
    private static final long GRACE_SECONDS = 5;

    /** How many log lines are kept for the interface to show. */
    private static final int LOG_HISTORY = 200;

    private final Path binary;
    private final Deque<String> recentLog = new ArrayDeque<>();

    private volatile Process process;
    private volatile Thread reader;
    private volatile Thread shutdownHook;
    private volatile String authorizationUrl;

    private Consumer<String> onAuthorizationUrl = url -> { };
    private Consumer<String> onLogLine = line -> { };
    private Runnable onExit = () -> { };

    /**
     * Prepares to run a particular executable.
     *
     * @param binary the daemon executable; must not be {@code null}
     */
    public SpotifyDaemon(Path binary) {
        this.binary = Objects.requireNonNull(binary, "binary must not be null");
    }

    // ------------------------------------------------------------------
    // Callbacks
    // ------------------------------------------------------------------

    /**
     * Sets what to do when the daemon asks for authorisation.
     *
     * <p><strong>Called on the log reader's thread</strong>; marshal before touching the interface.
     *
     * @param handler receives the authorisation URL; must not be {@code null}
     */
    public void setOnAuthorizationUrl(Consumer<String> handler) {
        this.onAuthorizationUrl = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * Sets what to do with each line the daemon logs.
     *
     * @param handler receives the line; must not be {@code null}
     */
    public void setOnLogLine(Consumer<String> handler) {
        this.onLogLine = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * Sets what to do when the daemon exits of its own accord.
     *
     * @param handler run once, after the process ends; must not be {@code null}
     */
    public void setOnExit(Runnable handler) {
        this.onExit = Objects.requireNonNull(handler, "handler must not be null");
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Writes the configuration, makes the audio pipe, and launches the daemon.
     *
     * <p>Returns as soon as the process is spawned - the daemon takes a second or two to bootstrap
     * and longer if it has to authenticate. Poll {@code SpotifyApi.isReady()} for that rather than
     * sleeping.
     *
     * @throws IOException if the configuration cannot be written, the pipe cannot be made, or the
     *                     process cannot be started
     */
    public synchronized void start() throws IOException {
        if (isRunning()) {
            return;
        }
        SpotifyConfig.write();
        SpotifyConfig.createFifo();

        ProcessBuilder builder = new ProcessBuilder(
                binary.toString(),
                "--config_dir", SpotifyConfig.configDir().toString());
        // One stream to read: the authorisation link goes to standard error and the rest of the
        // log is split across both, so keeping them apart would mean two reader threads and two
        // chances to miss the one line that matters.
        builder.redirectErrorStream(true);

        Process started = builder.start();
        this.process = started;
        this.authorizationUrl = null;

        installShutdownHook();
        startReader(started);

        LOG.info("Started go-librespot (pid " + started.pid() + ") from " + binary);
    }

    /**
     * Registers the hook that kills the child if this process ends without calling {@link #stop()}.
     *
     * <p>Belt and braces on purpose. The ordinary path stops the daemon explicitly; this covers
     * the window between launch and that call, an uncaught error on the way down, and a kill.
     */
    private void installShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        Thread hook = new Thread(this::destroyProcess, "sdmk-spotify-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            shutdownHook = hook;
        } catch (IllegalStateException e) {
            // Already shutting down. The daemon is about to be orphaned either way; log it loudly
            // because this is the one state that leaves something running on the user's machine.
            LOG.warning("Could not register the go-librespot shutdown hook: " + e.getMessage());
        }
    }

    /**
     * Reads the daemon's log on a thread of its own, watching for the authorisation link.
     *
     * @param started the process to read
     */
    private void startReader(Process started) {
        Thread thread = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(
                    new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    handleLogLine(line);
                }
            } catch (IOException e) {
                LOG.log(Level.FINE, "The go-librespot log stream ended", e);
            } finally {
                // The stream ending means the process ended. Report it, so a daemon that died -
                // a bad login, a port already taken - shows up in the interface rather than as
                // Spotify quietly never working.
                LOG.info("go-librespot exited with status " + exitStatus(started));
                try {
                    onExit.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "The go-librespot exit handler failed", e);
                }
            }
        }, "sdmk-spotify-log");
        thread.setDaemon(true);
        thread.start();
        this.reader = thread;
    }

    private static String exitStatus(Process process) {
        try {
            return String.valueOf(process.exitValue());
        } catch (IllegalThreadStateException e) {
            return "still running";
        }
    }

    private void handleLogLine(String line) {
        synchronized (recentLog) {
            recentLog.addLast(line);
            while (recentLog.size() > LOG_HISTORY) {
                recentLog.removeFirst();
            }
        }
        Matcher matcher = AUTH_LINK.matcher(line);
        if (matcher.find()) {
            String url = matcher.group(1);
            authorizationUrl = url;
            LOG.info("go-librespot is waiting for authorisation");
            try {
                onAuthorizationUrl.accept(url);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "The authorisation handler failed", e);
            }
        }
        try {
            onLogLine.accept(line);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "A log line handler failed", e);
        }
    }

    /** @return whether the daemon process is alive */
    public boolean isRunning() {
        Process running = process;
        return running != null && running.isAlive();
    }

    /** @return the daemon's process id, or {@code -1} when it is not running */
    public long pid() {
        Process running = process;
        return running != null && running.isAlive() ? running.pid() : -1;
    }

    /**
     * @return the authorisation link the daemon last asked for, or {@code null} when it has not
     *         asked - which is the normal case once credentials are stored
     */
    public String authorizationUrl() {
        return authorizationUrl;
    }

    /** @return the most recent log lines, oldest first */
    public List<String> recentLog() {
        synchronized (recentLog) {
            return List.copyOf(recentLog);
        }
    }

    /**
     * Stops the daemon and waits for it to go.
     *
     * <p>Idempotent, and safe to call when nothing was ever started.
     */
    public synchronized void stop() {
        destroyProcess();
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException e) {
                // The JVM is already going down and is running the hooks itself. Nothing to undo.
                LOG.finer("Shutdown already in progress; leaving the hook in place");
            }
        }
        Thread readerThread = reader;
        reader = null;
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    /**
     * Ends the child process, politely and then not.
     *
     * <p>Runs from {@link #stop()} and from the shutdown hook, so it must be safe to call twice and
     * must never throw - a hook that throws stops the rest of the shutdown.
     */
    private void destroyProcess() {
        Process running = process;
        if (running == null) {
            return;
        }
        process = null;
        if (!running.isAlive()) {
            return;
        }
        running.destroy();
        try {
            if (!running.waitFor(GRACE_SECONDS, TimeUnit.SECONDS)) {
                LOG.warning("go-librespot did not stop when asked; killing it");
                running.destroyForcibly();
                running.waitFor(GRACE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            running.destroyForcibly();
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to stop go-librespot cleanly", e);
        }
    }

    @Override
    public void close() {
        stop();
    }
}
