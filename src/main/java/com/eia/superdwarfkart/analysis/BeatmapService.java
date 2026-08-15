package com.eia.superdwarfkart.analysis;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a beatmap ready for whatever is playing, without ever making anyone wait for one.
 *
 * <p>Analysis takes a second or two and the cache lookup hashes several megabytes, so both happen
 * on one background daemon thread. Nothing here blocks the caller: {@link #request(Path)} returns
 * immediately and the answer turns up in {@link #status()} some time later.
 *
 * <p><strong>The interface polls; this class does not call back.</strong> That is the same
 * arrangement the level meters use, and for the same reason - the view already repaints from an
 * {@code AnimationTimer}, so a callback would only add a thread hop and the need to marshal it.
 * One immutable {@link Status} in one volatile field means a reader always sees a matched set of
 * values and neither side ever waits on the other.
 *
 * <p><strong>The cache is consulted first, always.</strong> A track analysed on a previous run
 * comes back in the time it takes to hash the file. A result is stored even when the request that
 * asked for it has been superseded - the work is already done, and throwing it away would mean
 * doing it again the moment the user came back to that song.
 *
 * <h2>Two ways a beatmap gets built, and callers cannot tell them apart</h2>
 *
 * <p>This service is asked for a <strong>locator</strong> rather than a file, because half the
 * library has no file. A local song is analysed the moment it becomes current, from its own decode,
 * and the course is ready before the music starts. A streamed song has nothing to decode: the only
 * copy of the audio is the one going past on its way to the sound card, so
 * {@link StreamBeatmapBuilder} takes it from the playback tap and the beatmap lands when the track
 * finishes. That is {@link Stage#LISTENING}, and it is the one state a caller has to be able to
 * distinguish - not because it needs to know where the audio came from, but because it is the
 * difference between "no course yet" and "no course, and playing it is what makes one".
 *
 * <p>Everything downstream - the runner, the timeline, the library badge - reads {@link Status} and
 * gets a beatmap that came out of {@link BeatmapAnalyzer#fromNovelty} either way.
 */
public final class BeatmapService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BeatmapService.class.getName());

    /** How far a request has got. */
    public enum Stage {

        /** Nothing has been asked for. */
        NONE,

        /** The file is being hashed, looked up, and analysed if it is not already known. */
        ANALYZING,

        /**
         * The track has no file to analyse and is being listened to as it plays.
         *
         * <p>A streamed track only exists as the audio going past. The beatmap arrives when the
         * track finishes and is cached, so the course is there on the next play; this play gets the
         * road, the meters and the beat flash with no entities on it. Distinct from
         * {@link #ANALYZING} because the two want opposite things said to the user - one is work
         * already happening that will finish on its own, the other finishes only if the track is
         * left to play to the end.
         */
        LISTENING,

        /** A beatmap is available. */
        READY,

        /** The file could not be analysed; see {@link Status#failure()}. */
        FAILED
    }

    /**
     * A consistent snapshot of what the service knows.
     *
     * @param source    what the song plays from - a file path or a {@code spotify:track:...} URI -
     *                  or {@code null} when nothing was asked for
     * @param beatmap   the beatmap, or {@link Beatmap#EMPTY} until one is available
     * @param stage     how far the request has got
     * @param progress  how much of the analysis is done, 0.0 to 1.0, or {@code -1} when unknown
     * @param failure   why it failed, or {@code null}
     * @param fromCache whether the beatmap came from the cache rather than being analysed now
     */
    public record Status(String source, Beatmap beatmap, Stage stage, double progress,
                         String failure, boolean fromCache) {

        /** @return whether a beatmap is available to build a course from */
        public boolean isReady() {
            return stage == Stage.READY;
        }

        /**
         * @param locator what a song plays from, from {@code Song.locator()}
         * @return whether this status is about that song
         */
        public boolean isAbout(String locator) {
            return source != null && source.equals(locator);
        }
    }

    private static final Status IDLE =
            new Status(null, Beatmap.EMPTY, Stage.NONE, 0, null, false);

    private final BeatmapCache cache;
    private final BeatmapAnalyzer analyzer;
    private final StreamBeatmapBuilder stream;
    private final ExecutorService worker;

    private volatile Status status = IDLE;

    /** The locator most recently asked about, so a repeated request is recognised and ignored. */
    private volatile String requested;

    private Future<?> running;

    /** Creates a service over the default cache directory. */
    public BeatmapService() {
        this(new BeatmapCache(), new BeatmapAnalyzer());
    }

    /**
     * Creates a service over an explicit cache and analyser.
     *
     * @param cache    where results are stored and looked up; must not be {@code null}
     * @param analyzer the algorithm to run on a miss; must not be {@code null}
     */
    public BeatmapService(BeatmapCache cache, BeatmapAnalyzer analyzer) {
        this(cache, analyzer, new StreamBeatmapBuilder());
    }

    /**
     * Creates a service over an explicit cache, analyser and stream builder.
     *
     * @param cache    where results are stored and looked up; must not be {@code null}
     * @param analyzer the algorithm to run on a file; must not be {@code null}
     * @param stream   builds a beatmap from the playback tap, for audio with no file; must not be
     *                 {@code null}
     */
    public BeatmapService(BeatmapCache cache, BeatmapAnalyzer analyzer,
                          StreamBeatmapBuilder stream) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
        this.stream = Objects.requireNonNull(stream, "stream must not be null");
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "sdmk-analysis");
            // A daemon, so an analysis in flight can never hold the application open on exit.
            thread.setDaemon(true);
            // Below the interface and well below the playback thread: a beatmap is wanted soon,
            // but never at the cost of a dropped frame or a gap in the audio.
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /**
     * Asks for a song's beatmap.
     *
     * <p>Returns at once. Requesting the same locator twice does nothing the second time, so this
     * can be called from a playback listener that fires for reasons other than the song changing.
     * Requesting a different one abandons an analysis already in flight - the user has moved on,
     * and the superseded track will be re-analysed for free if they come back to it.
     *
     * @param locator      what the song plays from, from {@code Song.locator()}, or {@code null} to
     *                     clear
     * @param trackSeconds how long the song is expected to run, for the case where the audio has to
     *                     be listened to rather than read; 0 when it is not known
     */
    public synchronized void request(String locator, double trackSeconds) {
        if (Objects.equals(locator, requested)) {
            return;
        }

        // Whatever the outgoing track was, this service is no longer collecting it. A streamed
        // track is finished by finishStream() at the end of the audio, before the next request
        // arrives; anything still armed here was skipped, and its partial curve is not a beatmap.
        stream.abandon();

        requested = locator;
        if (running != null) {
            running.cancel(true);
            running = null;
        }
        if (locator == null) {
            status = IDLE;
            return;
        }

        status = new Status(locator, Beatmap.EMPTY, Stage.ANALYZING, 0, null, false);
        try {
            running = worker.submit(() -> resolve(locator, trackSeconds));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The service is closing. Nothing is wrong and nothing more will happen.
            status = IDLE;
        }
    }

    /**
     * Asks for a song's beatmap without saying how long it is.
     *
     * @param locator what the song plays from, or {@code null} to clear
     */
    public void request(String locator) {
        request(locator, 0);
    }

    /**
     * Does the work: key, look up, and either analyse the file or arm the listener.
     *
     * <p>Runs on the analysis thread.
     *
     * @param locator      what the song plays from
     * @param trackSeconds how long it is expected to run, or 0
     */
    private void resolve(String locator, double trackSeconds) {
        try {
            String key = BeatmapCache.keyFor(locator);
            if (isStale(locator)) {
                return;
            }

            var cached = cache.loadByHash(key);
            if (cached.isPresent()) {
                publish(locator, new Status(locator, cached.get(), Stage.READY, 1, null, true));
                return;
            }

            Path file = asFile(locator);
            if (file == null) {
                // Nothing to open. The audio exists only as it is played, so the tap is the only
                // way to a beatmap and the course arrives on the next play rather than this one.
                armStream(locator, key, trackSeconds);
                return;
            }

            Beatmap analysed =
                    analyzer.analyze(file, key, fraction -> reportProgress(locator, fraction));
            // Stored before the staleness check: the analysis is finished either way, and a user
            // who skipped forward and came back would otherwise pay for it twice.
            cache.store(analysed);
            publish(locator, new Status(locator, analysed, Stage.READY, 1, null, false));
        } catch (CancellationException e) {
            // Superseded or shutting down. The request that replaced this one owns the status now.
            LOG.fine("Analysis of " + locator + " was cancelled");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not analyse " + locator, e);
            publish(locator, new Status(locator, Beatmap.EMPTY, Stage.FAILED, 0,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), false));
        }
    }

    /**
     * Starts collecting a track from the playback tap.
     *
     * @param locator      what the song plays from
     * @param key          the cache key the result will be stored under
     * @param trackSeconds how long it is expected to run, or 0
     */
    private synchronized void armStream(String locator, String key, double trackSeconds) {
        if (isStale(locator)) {
            return;
        }
        stream.arm(key, trackSeconds);
        status = new Status(locator, Beatmap.EMPTY, Stage.LISTENING, 0, null, false);
    }

    /**
     * Finishes a track that was being listened to, and caches the result.
     *
     * <p>Call this when a track plays out, before the running order moves on. Returns at once: the
     * derivation is queued on the analysis thread behind the audio it needs, so this is safe on the
     * interface thread and on the playback thread alike.
     *
     * <p>Does nothing at all when no stream is being collected, which is every local file and every
     * streamed track that already had a beatmap. Safe to call on any end of track.
     *
     * <p>The result is stored, and published if the track is still the current one - which it is
     * when the running order wraps straight back to it, and is not when it has already moved on.
     * Either way the map is in the cache, and the next request for that track is a hit.
     */
    public void finishStream() {
        if (!stream.isArmed()) {
            return;
        }
        String locator = requested;
        stream.finish(built -> {
            if (built == null) {
                return;
            }
            cache.store(built);
            publish(locator, new Status(locator, built, Stage.READY, 1, null, false));
        });
        // Ordered after the derivation, which is already queued: this only stops further audio
        // being collected, and the curve itself is the worker's until it resets it.
        stream.abandon();
    }

    /**
     * @param locator what a song plays from
     * @return the file it names, or {@code null} when it names no readable file
     */
    private static Path asFile(String locator) {
        try {
            Path path = Path.of(locator);
            return java.nio.file.Files.isRegularFile(path) ? path : null;
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    /** @return the builder that collects streamed tracks; register it as a PCM tap */
    public StreamBeatmapBuilder streamTap() {
        return stream;
    }

    /**
     * @return how much of a track being listened to has been heard, 0.0 to 1.0, or {@code -1} when
     *         nothing is being listened to or the track's length is not known
     */
    public double listeningProgress() {
        return stream.isArmed() ? stream.progress() : -1;
    }

    /**
     * Gives up a stream being collected, because the audio has stopped lining up with the track.
     *
     * <p>Wired to seeking. Everything recorded so far is at a known offset into the song and
     * everything after a seek is not, so the curve can no longer be believed - and a beatmap built
     * from it would be wrong in a way that is cached, silent, and looks exactly like a correct one.
     */
    public void abandonStream() {
        if (stream.isArmed()) {
            stream.abandon();
            Status snapshot = status;
            if (snapshot.stage() == Stage.LISTENING) {
                status = new Status(snapshot.source(), Beatmap.EMPTY, Stage.LISTENING, -1,
                        null, false);
            }
        }
    }

    /**
     * Updates the progress reading, if this song is still the one being asked about.
     *
     * @param locator  the song being analysed
     * @param fraction how far along it is, 0.0 to 1.0
     */
    private synchronized void reportProgress(String locator, double fraction) {
        if (isStale(locator)) {
            return;
        }
        status = new Status(locator, Beatmap.EMPTY, Stage.ANALYZING, fraction, null, false);
    }

    /**
     * Publishes a result unless the request has been superseded.
     *
     * <p><strong>Checking and storing have to be one step.</strong> Reading {@code requested} and
     * then assigning as two separate actions leaves a window in which the song changes in between -
     * and a finishing analysis then overwrites the new song's freshly published state with the old
     * song's beatmap. That is not a flicker: nothing publishes again until the new analysis
     * finishes, so the timeline draws the previous track's onsets for as long as that takes. Sharing
     * the lock with {@link #request} closes it, and costs one uncontended acquisition per track.
     *
     * @param locator the song the result is for
     * @param outcome what to publish
     */
    private synchronized void publish(String locator, Status outcome) {
        if (!isStale(locator)) {
            status = outcome;
        }
    }

    /**
     * @param locator the song a background task is working on
     * @return whether something else has been asked for since
     */
    private boolean isStale(String locator) {
        return !Objects.equals(locator, requested);
    }

    /** @return a consistent snapshot of what is known, never {@code null} */
    public Status status() {
        return status;
    }

    /** @return the beatmap for the requested file, or {@link Beatmap#EMPTY} when there is none */
    public Beatmap beatmap() {
        Status snapshot = status;
        return snapshot.stage() == Stage.READY ? snapshot.beatmap() : Beatmap.EMPTY;
    }

    /** @return the cache results are stored in */
    public BeatmapCache cache() {
        return cache;
    }

    /**
     * Waits for the request in flight to finish.
     *
     * <p>For the smoke test and for tests, which need the answer before they can report on it.
     * Never call this from the interface thread in normal running - the whole design is that
     * nobody waits.
     *
     * @param timeout how long to wait at most
     * @return whether the work finished within the timeout
     */
    public boolean await(Duration timeout) {
        Future<?> current;
        synchronized (this) {
            current = running;
        }
        if (current == null) {
            return true;
        }
        try {
            current.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (CancellationException e) {
            return true;
        } catch (ExecutionException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Waits for a stream derivation to finish and be stored.
     *
     * <p>The counterpart of {@link #await} for the other route to a beatmap, and for the same
     * callers: the smoke test and the unit tests, which have nothing to do until the answer is
     * there. The application never waits - {@link #finishStream()} hands the result on through a
     * callback precisely so that neither the playback thread nor the interface thread ever does.
     *
     * @param timeout how long to wait at most
     * @return whether it finished within the timeout
     */
    public boolean awaitStream(Duration timeout) {
        return stream.awaitIdle(timeout);
    }

    /**
     * Abandons any analysis in flight and releases the background thread.
     *
     * <p>The thread is a daemon and would not hold the application open, but an analysis left
     * running would go on decoding a file after the window has closed.
     */
    @Override
    public synchronized void close() {
        requested = null;
        if (running != null) {
            running.cancel(true);
            running = null;
        }
        worker.shutdownNow();
        stream.close();
        status = IDLE;
    }
}
