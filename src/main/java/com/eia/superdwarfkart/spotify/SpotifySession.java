package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.audio.SpotifyAudioSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Everything to do with Spotify, behind one object with one state.
 *
 * <p>The interface asks this class what is going on and tells it to connect; it owns the binary
 * lookup, the daemon process, the REST client, the event socket and the audio source, and it is
 * the only place their ordering is decided. That ordering is most of the work: the daemon has to be
 * running before the API answers, the API has to be ready before a track can be loaded, and the
 * event socket has to be connected before the first track ends or the running order stops.
 *
 * <p><strong>Nothing here runs unless the user asks.</strong> {@link #connect} is reached from one
 * button. A user who never opens the Spotify view starts no subprocess, opens no socket, downloads
 * nothing and is never told the feature exists - which is deliberate, because a music player that
 * silently launches a daemon and takes a network port at startup has done something the user did
 * not ask for.
 *
 * <p>The one exception is {@link #refreshBinary()}, which only looks at the filesystem, and the
 * optional background {@link #prefetchBinary} - a download into this application's own folder, on
 * a platform that has a published build, which the user opted into.
 */
public final class SpotifySession implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SpotifySession.class.getName());

    /** How long to wait for a freshly launched daemon to answer before giving up. */
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(90);

    /** How often the daemon is polled while waiting for it to bootstrap. */
    private static final Duration READY_POLL = Duration.ofMillis(400);

    /** Where the session is up to. */
    public enum State {

        /** The daemon is not installed, or this platform cannot run it. */
        UNAVAILABLE("Not installed"),

        /** Installed, not running. The ordinary resting state. */
        READY_TO_CONNECT("Not connected"),

        /** The daemon is starting. */
        STARTING("Starting..."),

        /** The daemon is waiting for the user to authorise it in a browser. */
        AWAITING_LOGIN("Waiting for login"),

        /** Logged in and able to play. */
        CONNECTED("Connected"),

        /** Something went wrong; {@link SpotifySession#detail()} says what. */
        FAILED("Failed");

        private final String label;

        State(String label) {
            this.label = label;
        }

        /** @return the caption shown on the rail and in the Spotify view */
        public String label() {
            return label;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sdmk-spotify");
        thread.setDaemon(true);
        return thread;
    });

    private final SpotifyApi api = new SpotifyApi();

    /**
     * Catalogue search on this application's own quota, independent of the daemon.
     *
     * <p>Unconfigured until the user supplies a client id and secret, at which point it takes over
     * searching entirely - see {@link #searchTracks}.
     */
    private final SpotifyCatalog catalog = new SpotifyCatalog(null, null);

    private final Consumer<Runnable> onUiThread;

    private SpotifyBinary.Resolution binary = new SpotifyBinary.Resolution(
            null, SpotifyBinary.Origin.NOT_FOUND, "Not looked for yet");
    private SpotifyDaemon daemon;
    private SpotifyEvents events;
    private SpotifyAudioSource audioSource;

    private volatile State state = State.UNAVAILABLE;
    private volatile String detail = "";
    private volatile String username;
    private volatile String authorizationUrl;
    private volatile String daemonVersion;

    /**
     * Whether this daemon build can reach the Spotify Web API.
     *
     * <p>Probed once on connect. No released version has the proxy, so on a stock install this is
     * false and the view says so rather than letting every search come back empty - which is what
     * a missing endpoint and "nothing matched" look like from here.
     */
    private volatile boolean searchAvailable;

    /**
     * Everything watching the session, in the order it registered.
     *
     * <p>A list rather than one handler because two quite different things need to hear about a
     * state change: the Spotify page redraws itself, and playback resumes a song that was waiting
     * for the daemon to come up. A single slot meant whichever registered last silently replaced
     * the other.
     */
    private final java.util.List<Runnable> changeListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private Consumer<String> onAuthorizationUrl = url -> { };

    /**
     * Creates a session.
     *
     * @param onUiThread how to get back onto the interface thread from a background one -
     *                   {@code Platform::runLater} in the application, a direct call in tests;
     *                   must not be {@code null}
     */
    public SpotifySession(Consumer<Runnable> onUiThread) {
        this.onUiThread = Objects.requireNonNull(onUiThread, "onUiThread must not be null");
        refreshBinary();
    }

    // ------------------------------------------------------------------
    // Observation
    // ------------------------------------------------------------------

    /** @return where the session is up to */
    public State state() {
        return state;
    }

    /** @return a sentence for the interface explaining the current state */
    public String detail() {
        return detail;
    }

    /** @return the logged-in account name, or {@code null} */
    public String username() {
        return username;
    }

    /** @return the daemon's REST client, usable once the state is {@link State#CONNECTED} */
    public SpotifyApi api() {
        return api;
    }

    /** @return where the daemon executable was found, and how */
    public SpotifyBinary.Resolution binary() {
        return binary;
    }

    /** @return the authorisation link the daemon is waiting on, or {@code null} */
    public String authorizationUrl() {
        return authorizationUrl;
    }

    /** @return the daemon's version as it announced itself, or {@code null} */
    public String daemonVersion() {
        return daemonVersion;
    }

    /**
     * @return whether tracks can be searched for, by either route
     */
    public boolean isSearchAvailable() {
        return catalog.isConfigured() || searchAvailable;
    }

    /**
     * @return whether search is going through this application's own Spotify application rather
     *         than the daemon's proxy - which is what makes a stock released daemon enough
     */
    public boolean isCatalogSearch() {
        return catalog.isConfigured();
    }

    /**
     * @return whether the daemon build exposes the Web API proxy, which is the only route to the
     *         signed-in user's own saved tracks and playlists
     */
    public boolean isUserLibraryAvailable() {
        return searchAvailable;
    }

    /** @return the catalogue client, for configuring and verifying credentials */
    public SpotifyCatalog catalog() {
        return catalog;
    }

    /**
     * Points catalogue search at a registered Spotify application.
     *
     * @param clientId     the application id, or {@code null} to turn catalogue search off
     * @param clientSecret the application secret, or {@code null}
     */
    public void setCatalogCredentials(String clientId, String clientSecret) {
        catalog.setCredentials(clientId, clientSecret);
        changed();
    }

    /**
     * Tries a credential pair against Spotify, keeping the previous one if it is refused.
     *
     * @param clientId     the application id to try
     * @param clientSecret the application secret to try
     * @return whether the new pair works; on {@code false} the previous pair is still in force and
     *         {@link #searchProblem()} explains the refusal
     */
    public boolean applyCatalogCredentials(String clientId, String clientSecret) {
        boolean accepted = catalog.applyCredentials(clientId, clientSecret);
        changed();
        return accepted;
    }

    /**
     * Searches for tracks by whichever route is available.
     *
     * <p>The catalogue wins when it is configured, because it is the one with a quota of our own -
     * the daemon's proxy borrows librespot's shared client id and is rate-limited by strangers.
     *
     * @param query what the user typed
     * @param limit how many results to ask for
     * @return the tracks found, or an empty list
     */
    public java.util.List<SpotifyTrack> searchTracks(String query, int limit) {
        return catalog.isConfigured()
                ? catalog.searchTracks(query, limit)
                : api.searchTracks(query, limit);
    }

    /**
     * Explains why the last search came back empty, or {@code null} when it simply matched nothing.
     *
     * @return a sentence for the interface, or {@code null}
     */
    public String searchProblem() {
        return catalog.isConfigured() ? catalog.lastProblem() : api.lastWebApiProblem();
    }

    /** @return whether tracks can be played right now */
    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    /** @return whether the daemon is installed and this platform can run it */
    public boolean isAvailable() {
        return binary.isFound() && SpotifyBinary.isSupportedPlatform();
    }

    /**
     * Sets what to run whenever the state changes.
     *
     * <p>Always called on the interface thread, through the marshaller given to the constructor -
     * unlike the lower-level callbacks in this package, because everything listening to this one is
     * a view.
     *
     * @param handler run after every change; must not be {@code null}
     */
    public void setOnChanged(Runnable handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        changeListeners.clear();
        changeListeners.add(handler);
    }

    /**
     * Adds a listener without displacing the ones already there.
     *
     * <p>Use this rather than {@link #setOnChanged} for anything registering after the views have
     * been built - the Spotify page claims the first slot in its own constructor, and a second
     * {@code set} would take it away with nothing anywhere reporting that the page had stopped
     * redrawing.
     *
     * @param handler run after every change; must not be {@code null}
     */
    public void addOnChanged(Runnable handler) {
        changeListeners.add(Objects.requireNonNull(handler, "handler must not be null"));
    }

    /**
     * Sets what to do when the daemon needs a browser opened.
     *
     * @param handler receives the authorisation URL, on the interface thread; must not be
     *                {@code null}
     */
    public void setOnAuthorizationUrl(Consumer<String> handler) {
        this.onAuthorizationUrl = Objects.requireNonNull(handler, "handler must not be null");
    }

    // ------------------------------------------------------------------
    // The binary
    // ------------------------------------------------------------------

    /**
     * Looks for the daemon executable, without touching the network.
     *
     * <p>Cheap, and safe to call whenever the Spotify view is opened - which is what picks up a
     * {@code brew install} the user ran while the application was already running.
     *
     * @return where it was found, and how
     */
    public SpotifyBinary.Resolution refreshBinary() {
        binary = SpotifyBinary.resolve(configuredPath);
        if (state == State.UNAVAILABLE || state == State.READY_TO_CONNECT) {
            if (!SpotifyBinary.isSupportedPlatform()) {
                set(State.UNAVAILABLE, "Spotify needs a named pipe, which this platform lacks");
            } else if (binary.isFound()) {
                set(State.READY_TO_CONNECT, binary.detail());
            } else {
                set(State.UNAVAILABLE, binary.detail());
            }
        }
        return binary;
    }

    private String configuredPath;

    /**
     * Points the session at a particular executable.
     *
     * @param path the path the user configured, or {@code null} to search as usual
     */
    public void setConfiguredPath(String path) {
        this.configuredPath = path;
        refreshBinary();
    }

    /**
     * Downloads the daemon, on a platform that has a published build.
     *
     * <p>Runs on the session's worker thread and reports through the state. Does nothing at all
     * where there is no asset to fetch - macOS most of all, where {@link
     * SpotifyBinary#installCommand()} is the route instead.
     */
    public void prefetchBinary() {
        if (binary.isFound() || !SpotifyBinary.isDownloadable()) {
            return;
        }
        worker.submit(() -> {
            SpotifyBinary.Resolution fetched =
                    SpotifyBinary.fetch(message -> set(State.STARTING, message));
            binary = fetched;
            if (fetched.isFound()) {
                set(State.READY_TO_CONNECT, fetched.detail());
            } else {
                set(State.UNAVAILABLE, fetched.detail());
            }
        });
    }

    /**
     * Builds the daemon from source, which is the only way to get a build that can search.
     *
     * <p>The Web API proxy is on the project's {@code master} branch and in no release, so this is
     * not an alternative route to the same thing - it is the only route to search at all. Playback
     * works on a released build and is unaffected either way.
     *
     * <p>Runs on the session's worker thread and reports through the state. If a daemon is running
     * it is stopped first: the new binary replaces the file the old one was started from, and the
     * session would otherwise go on talking to a process that no longer matches what is on disk.
     */
    public void buildFromSource() {
        if (state == State.STARTING) {
            return;
        }
        boolean wasConnected = isConnected();
        set(State.STARTING, "Preparing to build");
        worker.submit(() -> {
            if (wasConnected || daemon != null) {
                closeDaemon();
            }
            SpotifyBinary.Resolution built =
                    SpotifyBinary.buildFromSource(message -> set(State.STARTING, message));
            binary = built;
            if (built.isFound()) {
                searchAvailable = false;
                set(State.READY_TO_CONNECT, built.detail() + " - connect to use it");
            } else {
                // The reason is carried in the detail: no Go toolchain, a compile error, a timeout.
                // A dead button with no explanation is the failure mode this whole view avoids.
                set(binary.isFound() ? State.READY_TO_CONNECT : State.UNAVAILABLE, built.detail());
            }
        });
    }

    /** @return whether the Go toolchain is available, so a source build could be attempted */
    public boolean isGoAvailable() {
        return SpotifyBinary.findGo() != null;
    }

    /**
     * @return what a source build still needs on this machine, checked fresh each time so a
     *         prerequisite installed while the application is running is picked up
     */
    public SpotifyBinary.BuildPrerequisites buildPrerequisites() {
        return SpotifyBinary.checkBuildPrerequisites();
    }

    /**
     * Installs whatever a source build is missing.
     *
     * <p>macOS only, where the answer is one Homebrew command. On Linux the equivalent needs root,
     * and this application must never ask for that - the command is shown to be copied instead.
     */
    public void installBuildPrerequisites() {
        SpotifyBinary.BuildPrerequisites prerequisites = SpotifyBinary.checkBuildPrerequisites();
        if (prerequisites.isSatisfied() || !SpotifyBinary.isMac()) {
            return;
        }
        runShellCommand(prerequisites.installCommand(), () -> {
            SpotifyBinary.BuildPrerequisites now = SpotifyBinary.checkBuildPrerequisites();
            set(binary.isFound() ? State.READY_TO_CONNECT : State.UNAVAILABLE,
                    now.isSatisfied()
                            ? "Build tools installed - a daemon can now be built from source"
                            : "Still missing: " + now.describe());
        });
    }

    /**
     * Stops the daemon and the event stream, leaving the binary lookup alone.
     *
     * <p>Split out of {@link #close()} because a rebuild has to replace a running daemon without
     * tearing down the session object itself.
     */
    private void closeDaemon() {
        SpotifyEvents stream = events;
        events = null;
        if (stream != null) {
            stream.close();
        }
        SpotifyAudioSource source = audioSource;
        audioSource = null;
        if (source != null) {
            source.close();
        }
        SpotifyDaemon running = daemon;
        daemon = null;
        if (running != null) {
            running.stop();
        }
        username = null;
        authorizationUrl = null;
        searchAvailable = false;
    }

    /**
     * Runs the platform's install command, which on macOS is Homebrew.
     *
     * <p>Only ever reached from a button the user pressed, with the command shown on it. This
     * installs software on the machine, which is not something to do on the user's behalf without
     * them having asked for it in so many words.
     */
    public void runInstallCommand() {
        runShellCommand(SpotifyBinary.installCommand(), this::refreshBinary);
    }

    /**
     * Installs the Go toolchain, which a source build needs and which is not the daemon.
     *
     * <p>Separate from {@link #runInstallCommand()} because it succeeds without producing a daemon:
     * refreshing the binary lookup afterwards would find nothing and report the wrong thing. All it
     * does is make the build button reachable.
     */
    public void runGoInstallCommand() {
        runShellCommand(SpotifyBinary.goInstallCommand(),
                () -> set(binary.isFound() ? State.READY_TO_CONNECT : State.UNAVAILABLE,
                        "Go is installed - a daemon can now be built from source"));
    }

    /**
     * Runs one shell command, reporting through the state.
     *
     * <p>Only ever reached from a button with the command printed on it. These change the machine
     * rather than this application's own folder, which is why none of them happens on the user's
     * behalf without them having asked in so many words.
     *
     * @param command what to run; {@code null} does nothing
     * @param onSuccess what to do afterwards, on the worker thread
     */
    private void runShellCommand(String command, Runnable onSuccess) {
        if (command == null) {
            return;
        }
        worker.submit(() -> {
            set(State.STARTING, "Running: " + command);
            try {
                Process process = new ProcessBuilder("/bin/sh", "-c", command)
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes());
                boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
                if (!finished) {
                    process.destroyForcibly();
                    set(State.UNAVAILABLE, "\"" + command + "\" did not finish");
                    return;
                }
                if (process.exitValue() != 0) {
                    LOG.warning("Command failed: " + command + "\n" + output);
                    set(State.UNAVAILABLE, "Failed - run it in a terminal: " + command);
                    return;
                }
                onSuccess.run();
            } catch (IOException e) {
                set(State.UNAVAILABLE, "Could not run \"" + command + "\": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // ------------------------------------------------------------------
    // Connecting
    // ------------------------------------------------------------------

    /**
     * Starts the daemon and waits for it to be usable, logging in if it has never been.
     *
     * <p>Returns immediately; everything happens on the worker thread and is reported through the
     * state. Calling it while already connecting or connected does nothing.
     */
    public void connect() {
        if (state == State.STARTING || state == State.AWAITING_LOGIN || state == State.CONNECTED) {
            return;
        }
        if (!binary.isFound()) {
            refreshBinary();
        }
        Path executable = binary.path();
        if (executable == null) {
            set(State.UNAVAILABLE, binary.detail());
            return;
        }
        set(State.STARTING, "Starting go-librespot");
        worker.submit(() -> startDaemon(executable));
    }

    private void startDaemon(Path executable) {
        try {
            SpotifyDaemon started = new SpotifyDaemon(executable);
            started.setOnAuthorizationUrl(url -> {
                authorizationUrl = url;
                set(State.AWAITING_LOGIN, "Waiting for you to authorise this device");
                onUiThread.accept(() -> onAuthorizationUrl.accept(url));
            });
            started.setOnLogLine(line -> {
                String version = SpotifyBinary.versionFrom(line);
                if (version != null) {
                    daemonVersion = version;
                }
            });
            started.setOnExit(() -> {
                // Only a surprise if we thought we were up; stop() clears the field first.
                if (daemon != null) {
                    set(State.FAILED, "go-librespot stopped unexpectedly");
                }
            });
            started.start();
            this.daemon = started;

            if (!awaitReady()) {
                set(State.FAILED, "go-librespot did not become ready - " + lastLogLine());
                return;
            }

            openEventStream();
            username = api.username();
            authorizationUrl = null;
            // Probed once, here, rather than per search: it is a round trip, and the answer cannot
            // change while one daemon build is running.
            searchAvailable = api.hasWebApiProxy();
            set(State.CONNECTED, (username == null ? "Connected" : "Connected as " + username)
                    + (daemonVersion == null ? "" : " - go-librespot " + daemonVersion));
            LOG.info("Spotify session is connected"
                    + (username == null ? "" : " as " + username));

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not start go-librespot", e);
            set(State.FAILED, "Could not start go-librespot: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "The Spotify session failed to start", e);
            set(State.FAILED, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /**
     * Polls the daemon until it reports it can accept playback.
     *
     * <p>Polling rather than a fixed sleep: a first run has to wait for a person to finish an OAuth
     * flow in a browser, and a later one is ready in a second or two. A sleep long enough for the
     * first is absurd for the second.
     *
     * @return whether the daemon became ready inside {@link #READY_TIMEOUT}
     */
    private boolean awaitReady() {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (daemon == null || !daemon.isRunning()) {
                return false;
            }
            if (api.isReady()) {
                return true;
            }
            try {
                Thread.sleep(READY_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String lastLogLine() {
        SpotifyDaemon running = daemon;
        if (running == null) {
            return "no output";
        }
        return running.recentLog().stream()
                .reduce((first, second) -> second)
                .orElse("no output");
    }

    /**
     * Opens the event socket and wires it to the audio source.
     *
     * <p>This is what makes the running order advance: the pipe cannot report the end of a track,
     * so {@code stopped} and {@code not_playing} arriving here are turned into the one call
     * {@code PlaybackEngine} is waiting for.
     */
    private void openEventStream() {
        SpotifyEvents stream = new SpotifyEvents();
        stream.addListener(this::handleEvent);
        stream.start();
        this.events = stream;
    }

    private void handleEvent(SpotifyEvents.Event event) {
        SpotifyAudioSource source = audioSource;
        if (source == null) {
            return;
        }
        switch (event.type()) {
            case "metadata" -> {
                long millis = event.durationMillis();
                if (millis > 0) {
                    source.setTrackDuration(Duration.ofMillis(millis));
                }
            }
            case "stopped", "not_playing" -> source.trackEnded();
            default -> {
                // playing, paused, seek, volume and the rest are this application's own doing -
                // it issued the command that caused them, so reacting would be an echo.
            }
        }
    }

    // ------------------------------------------------------------------
    // The audio source
    // ------------------------------------------------------------------

    /**
     * Builds the audio source, or returns the one already built.
     *
     * <p>Handed to {@code RoutingAudioSource} as a supplier, so it is only ever reached when a
     * streamed song is actually loaded.
     *
     * @return the source, or {@code null} when Spotify is not connected
     */
    public SpotifyAudioSource audioSource() {
        if (!isConnected()) {
            return null;
        }
        if (audioSource == null) {
            audioSource = new SpotifyAudioSource(api);
        }
        return audioSource;
    }

    // ------------------------------------------------------------------
    // State changes
    // ------------------------------------------------------------------

    private void set(State newState, String newDetail) {
        this.state = newState;
        this.detail = newDetail == null ? "" : newDetail;
        changed();
    }

    /**
     * Tells the interface to redraw, without changing the state.
     *
     * <p>Configuring catalogue credentials turns searching on and off without the session's own
     * state moving at all, so the view needs a way to hear about it that is not a state change.
     */
    private void changed() {
        onUiThread.accept(() -> {
            for (Runnable listener : changeListeners) {
                try {
                    listener.run();
                } catch (RuntimeException e) {
                    // One listener throwing must not stop the others: the page redrawing and
                    // playback resuming are independent, and losing the second silently would look
                    // like a song that simply refused to start.
                    LOG.log(Level.WARNING, "A Spotify state listener failed", e);
                }
            }
        });
    }

    /** Stops the daemon and releases everything, leaving nothing running on the machine. */
    @Override
    public void close() {
        closeDaemon();
        worker.shutdownNow();
        state = binary.isFound() ? State.READY_TO_CONNECT : State.UNAVAILABLE;
    }
}
