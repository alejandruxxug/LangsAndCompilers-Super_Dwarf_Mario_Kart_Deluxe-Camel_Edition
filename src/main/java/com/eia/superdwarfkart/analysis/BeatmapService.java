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
 */
public final class BeatmapService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BeatmapService.class.getName());

    /** How far a request has got. */
    public enum Stage {

        /** Nothing has been asked for. */
        NONE,

        /** The file is being hashed, looked up, and analysed if it is not already known. */
        ANALYZING,

        /** A beatmap is available. */
        READY,

        /** The file could not be analysed; see {@link Status#failure()}. */
        FAILED
    }

    /**
     * A consistent snapshot of what the service knows.
     *
     * @param file      the audio file this is about, or {@code null} when nothing was asked for
     * @param beatmap   the beatmap, or {@link Beatmap#EMPTY} until one is available
     * @param stage     how far the request has got
     * @param progress  how much of the analysis is done, 0.0 to 1.0
     * @param failure   why it failed, or {@code null}
     * @param fromCache whether the beatmap came from the cache rather than being analysed now
     */
    public record Status(Path file, Beatmap beatmap, Stage stage, double progress,
                         String failure, boolean fromCache) {

        /** @return whether a beatmap is available to build a course from */
        public boolean isReady() {
            return stage == Stage.READY;
        }
    }

    private static final Status IDLE =
            new Status(null, Beatmap.EMPTY, Stage.NONE, 0, null, false);

    private final BeatmapCache cache;
    private final BeatmapAnalyzer analyzer;
    private final ExecutorService worker;

    private volatile Status status = IDLE;

    /** The file most recently asked about, so a repeated request is recognised and ignored. */
    private volatile Path requested;

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
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
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
     * Asks for a file's beatmap.
     *
     * <p>Returns at once. Requesting the same file twice does nothing the second time, so this can
     * be called from a playback listener that fires for reasons other than the song changing.
     * Requesting a different file abandons an analysis already in flight - the user has moved on,
     * and the superseded track will be re-analysed for free if they come back to it.
     *
     * @param file the audio file, or {@code null} to clear
     */
    public synchronized void request(Path file) {
        if (Objects.equals(file, requested)) {
            return;
        }
        requested = file;

        if (running != null) {
            running.cancel(true);
            running = null;
        }
        if (file == null) {
            status = IDLE;
            return;
        }

        status = new Status(file, Beatmap.EMPTY, Stage.ANALYZING, 0, null, false);
        try {
            running = worker.submit(() -> resolve(file));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The service is closing. Nothing is wrong and nothing more will happen.
            status = IDLE;
        }
    }

    /**
     * Does the work: hash, look up, analyse on a miss, store.
     *
     * <p>Runs on the analysis thread.
     *
     * @param file the audio file to resolve
     */
    private void resolve(Path file) {
        try {
            String hash = BeatmapCache.hash(file);
            if (isStale(file)) {
                return;
            }

            var cached = cache.loadByHash(hash);
            if (cached.isPresent()) {
                publish(file, new Status(file, cached.get(), Stage.READY, 1, null, true));
                return;
            }

            Beatmap analysed = analyzer.analyze(file, hash, fraction -> reportProgress(file, fraction));
            // Stored before the staleness check: the analysis is finished either way, and a user
            // who skipped forward and came back would otherwise pay for it twice.
            cache.store(analysed);
            publish(file, new Status(file, analysed, Stage.READY, 1, null, false));
        } catch (CancellationException e) {
            // Superseded or shutting down. The request that replaced this one owns the status now.
            LOG.fine("Analysis of " + file.getFileName() + " was cancelled");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not analyse " + file, e);
            publish(file, new Status(file, Beatmap.EMPTY, Stage.FAILED, 0,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), false));
        }
    }

    /**
     * Updates the progress reading, if this file is still the one being asked about.
     *
     * @param file     the file being analysed
     * @param fraction how far along it is, 0.0 to 1.0
     */
    private synchronized void reportProgress(Path file, double fraction) {
        if (isStale(file)) {
            return;
        }
        status = new Status(file, Beatmap.EMPTY, Stage.ANALYZING, fraction, null, false);
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
     * @param file    the file the result is for
     * @param outcome what to publish
     */
    private synchronized void publish(Path file, Status outcome) {
        if (!isStale(file)) {
            status = outcome;
        }
    }

    /**
     * @param file the file a background task is working on
     * @return whether something else has been asked for since
     */
    private boolean isStale(Path file) {
        return !Objects.equals(file, requested);
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
        status = IDLE;
    }
}
