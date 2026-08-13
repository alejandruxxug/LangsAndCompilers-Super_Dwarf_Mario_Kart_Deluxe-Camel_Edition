package com.eia.superdwarfkart.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Which files already have a beatmap in the cache, answered instantly.
 *
 * <p>The library table wants to mark the songs that already have a course, and the obvious way to
 * find out - ask the cache - is the one way it must not be done: the cache is keyed by content
 * hash, so answering for one song means reading the whole audio file. At sixty repaints a second
 * over a scrolling table that is several megabytes per frame per row.
 *
 * <p>So the hashing happens <strong>once, on a background thread</strong>, and the answers are kept
 * here. {@link #isReady(Path)} is a map lookup and never blocks or reads anything; a file that has
 * not been indexed yet simply reads "not ready" until it has, which is the correct thing for a
 * badge to say while it does not know.
 *
 * <p>Answers are invalidated by the file's own size and modification time, so replacing a track
 * with a different recording under the same name is noticed rather than remembered wrongly.
 *
 * <p>No {@code javafx} import. The callback that says "there is something new to draw" is handed
 * back through a {@link Consumer} of {@link Runnable} the caller supplies - {@code Platform::runLater}
 * in the application, a direct call in a test - which is the same arrangement
 * {@code PlaybackEngine} uses to get off the playback thread.
 */
public final class BeatmapIndex implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BeatmapIndex.class.getName());

    /** What was found for one file, and what it was found for. */
    private record Known(long size, long modifiedMillis, boolean ready) { }

    private final BeatmapCache cache;
    private final ExecutorService worker;
    private final Map<Path, Known> known = new ConcurrentHashMap<>();

    private volatile Consumer<Runnable> onUpdated = Runnable::run;
    private volatile Runnable listener = () -> { };

    /** Creates an index over the default cache directory. */
    public BeatmapIndex() {
        this(new BeatmapCache());
    }

    /**
     * @param cache the cache to look in; must not be {@code null}
     */
    public BeatmapIndex(BeatmapCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "sdmk-beatmap-index");
            thread.setDaemon(true);
            // Below everything. A badge appearing a moment late costs nothing; a dropped frame or
            // a gap in the audio costs a great deal.
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /**
     * Sets what happens when new answers are available.
     *
     * @param onUiThread how to get back onto the interface thread; must not be {@code null}
     * @param listener   run there once a batch has been indexed; must not be {@code null}
     */
    public void setOnUpdated(Consumer<Runnable> onUiThread, Runnable listener) {
        this.onUpdated = Objects.requireNonNull(onUiThread, "onUiThread must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    /**
     * Asks for a set of files to be indexed, skipping any already known and unchanged.
     *
     * <p>Returns at once. Calling it with the whole library on every change is intended: the work
     * is proportional to what has actually changed, not to the size of the library.
     *
     * @param files the audio files to look up; {@code null} entries are ignored
     */
    public void request(Collection<Path> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Path> pending = new ArrayList<>();
        for (Path file : files) {
            if (file != null && !isCurrent(file)) {
                pending.add(file);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        try {
            worker.execute(() -> index(pending));
        } catch (RejectedExecutionException e) {
            // Shutting down. Nothing is wrong and nothing more will happen.
            LOG.fine("Beatmap index is closed; skipping " + pending.size() + " files");
        }
    }

    /**
     * @param file an audio file
     * @return whether the answer held for it still describes the file on disk
     */
    private boolean isCurrent(Path file) {
        Known answer = known.get(file);
        if (answer == null) {
            return false;
        }
        try {
            return answer.size() == Files.size(file)
                    && answer.modifiedMillis() == Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            // The file has gone. Re-index it, which will record that it has no course.
            return false;
        }
    }

    /**
     * Hashes a batch and records what the cache holds for each.
     *
     * <p>Runs on the background thread.
     *
     * @param files the files to look up
     */
    private void index(Collection<Path> files) {
        boolean changed = false;
        for (Path file : files) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            changed |= record(file);
        }
        if (changed) {
            Consumer<Runnable> hop = onUpdated;
            Runnable notify = listener;
            hop.accept(notify);
        }
    }

    /**
     * @param file the file to look up
     * @return whether the stored answer changed
     */
    private boolean record(Path file) {
        try {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            boolean ready = cache.loadByHash(BeatmapCache.hash(file)).isPresent();
            Known was = known.put(file, new Known(size, modified, ready));
            return was == null || was.ready() != ready;
        } catch (IOException | RuntimeException e) {
            // A missing or unreadable file has no course, which is exactly what the badge should
            // say. Recorded rather than retried, so a library of stale paths is not re-walked.
            Known was = known.put(file, new Known(-1, -1, false));
            return was == null || was.ready();
        }
    }

    /**
     * @param file an audio file, or {@code null}
     * @return whether a beatmap for it is already in the cache, as far as this index knows; never
     *         blocks and never reads the file
     */
    public boolean isReady(Path file) {
        if (file == null) {
            return false;
        }
        Known answer = known.get(file);
        return answer != null && answer.ready();
    }

    /**
     * Looks a file up again even though it has been looked up before.
     *
     * <p>{@link #request(Collection)} deliberately skips what it already knows, which is what makes
     * it cheap enough to call with the whole library. This is the other case: the file has not
     * changed, but the <em>cache</em> has - an analysis that was running when the song was first
     * indexed has since finished, and the badge should appear without waiting for something to
     * touch the file.
     *
     * <p>Scheduled on the background thread like everything else. Hashing on the interface thread
     * to save a hop would read the whole file inside a frame.
     *
     * @param file the file to look up again, or {@code null} to do nothing
     */
    public void recheck(Path file) {
        if (file == null) {
            return;
        }
        try {
            worker.execute(() -> index(List.of(file)));
        } catch (RejectedExecutionException e) {
            LOG.fine("Beatmap index is closed; skipping " + file);
        }
    }

    /** @return how many files have been looked up */
    public int size() {
        return known.size();
    }

    /** Abandons any indexing in flight and releases the background thread. */
    @Override
    public void close() {
        worker.shutdownNow();
    }
}
