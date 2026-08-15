package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.audio.LevelAnalyzer;
import com.eia.superdwarfkart.audio.PcmListener;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Builds a beatmap out of a track as it is heard, for audio that has no file to read.
 *
 * <p><strong>Why this exists.</strong> {@link BeatmapAnalyzer} opens the audio file, decodes it as
 * fast as the machine manages, and has a beatmap ready before the song starts. A streamed track has
 * no file: go-librespot decodes one track at a time into a pipe, at the speed the sound card drains
 * it, and there is nothing on disk to open a second time. Every route to a course for a Spotify
 * track therefore runs through the audio that is already being played - which is the
 * {@link PcmListener} tap the level meters have used since M5.
 *
 * <p>The consequence is a feature rather than a limitation, and it is the one the milestone notes
 * already described: <strong>the first play of a streamed track builds its course, and every play
 * after that has it.</strong> The road, the meters and the beat flash all work on that first play;
 * what is missing is the entities, and they arrive the next time the track comes round.
 *
 * <h2>Two threads, and the split is the whole design</h2>
 *
 * <p>{@link #pcm} runs on the playback thread, between decoding a block and writing it to the sound
 * card, so it does what {@code PcmListener} demands and nothing more: sum the block to mono into a
 * fresh array and hand it over. No transform, no allocation beyond that one array, no lock held
 * against anything the pump can contend on. A 1024-point FFT costs tens of microseconds and would
 * very probably fit in a 23 ms block - but "very probably fits" is how audio acquires a stutter
 * nobody can reproduce, and the copy costs a microsecond.
 *
 * <p>The transforms happen on one background thread at minimum priority, the same arrangement and
 * for the same reason as {@link BeatmapService}'s. It runs some three hundred times faster than the
 * audio arrives, so the handover queue holds one or two blocks and the analysis is finished within
 * a few milliseconds of the last sample being heard.
 *
 * <h2>What makes a run usable, and what throws one away</h2>
 *
 * <p>The novelty curve is a position in the track measured in samples, so it is only meaningful if
 * the samples were heard <strong>from the beginning, in order, with nothing missing</strong>. Three
 * things break that, and all three are ordinary user actions rather than faults:
 *
 * <ul>
 *   <li><strong>A seek.</strong> The audio after it belongs to a different instant, so every onset
 *       recorded afterwards would be reported at a time it did not happen. {@link #abandon()}.</li>
 *   <li><strong>Skipping the track.</strong> A partial curve is not a short beatmap, it is a wrong
 *       one - {@link Beatmap#durationSeconds()} would say the song ends where the listener stopped
 *       listening, the course would end there too, and it would be cached and believed for good.
 *       {@link #finish} refuses anything short of {@link #COMPLETE_FRACTION}.</li>
 *   <li><strong>Falling behind.</strong> If the handover queue ever grew past
 *       {@link #MAX_PENDING_BLOCKS} the curve would have a hole in it, and a hole is worse than no
 *       curve because nothing downstream could tell. The run is spoilt outright instead.</li>
 * </ul>
 *
 * <p>None of the three is an error and none is reported as one. The track simply has no course yet,
 * which is the state it was already in.
 *
 * <p>No {@code javafx} import, no window, no sound card: this is handed bytes and answers with a
 * {@link Beatmap}, which is what makes {@code StreamBeatmapBuilderTest} able to play it a synthetic
 * track and check the tempo that comes back.
 */
public final class StreamBeatmapBuilder implements PcmListener, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(StreamBeatmapBuilder.class.getName());

    /**
     * How much of the expected running time must be heard before the result is kept.
     *
     * <p>Not 1.0, because the last partial hop is dropped rather than zero-padded and a stream's
     * own idea of a track's length comes from Spotify's metadata rather than from counting frames -
     * the two disagree by a fraction of a second on a good day. Well short of it, and the map is a
     * record of somebody skipping the track.
     */
    static final double COMPLETE_FRACTION = 0.95;

    /**
     * How many blocks may be waiting to be transformed before the run is given up.
     *
     * <p>The worker is hundreds of times faster than realtime, so in practice this holds one or two
     * blocks and this cap is never approached. It exists for the case where it is - a machine
     * paging, or the worker starved - where the alternative to giving up is an unbounded queue and,
     * eventually, a curve with a gap in it that reads as perfectly good data.
     */
    static final int MAX_PENDING_BLOCKS = 2048;

    /** Novelty values allocated up front, enough for about eight minutes before the first grow. */
    private static final int INITIAL_NOVELTY = 40_000;

    /**
     * Fewest novelty values a run must have produced to be worth peak-picking.
     *
     * <p>Two seconds of audio. Below it the adaptive threshold has no neighbourhood to measure
     * against and the tempo histogram has nothing to count, so the answer would be noise presented
     * as a beatmap.
     */
    private static final int MIN_NOVELTY_VALUES = 172;

    /** How long to wait for the queued blocks when finishing. Generous; it is normally instant. */
    private static final long FINISH_TIMEOUT_MILLIS = 5000;

    private final double sensitivity;
    private final ExecutorService worker;

    /**
     * Which run the blocks arriving now belong to.
     *
     * <p><strong>Only {@link #arm} bumps this, and that is the whole subtlety of the class.</strong>
     * It guards exactly one race: {@link #pcm} sees a track armed, the song changes before the block
     * reaches the worker, and a block of the old track's audio lands after the new track's reset and
     * becomes the first thing in its curve.
     *
     * <p>{@link #abandon()} deliberately does <em>not</em> bump it. Abandoning stops further audio
     * being taken - {@link #pcm} refuses outright once the key is cleared - but the blocks already
     * handed over belong to the run that is being finished, and dropping them is not a small loss:
     * {@link BeatmapService#finishStream()} abandons immediately after queueing the derivation, so
     * bumping here discarded almost the entire track's audio and every streamed course came back
     * empty. Ordering on the single worker is what makes the rest safe, and it is exact where a
     * generation counter is a guess about timing.
     */
    private final AtomicLong runId = new AtomicLong();

    /** Blocks handed over but not yet transformed. Read for the overflow check only. */
    private final AtomicLong pending = new AtomicLong();

    /** The cache key of the run in flight, or {@code null} when nothing is being collected. */
    private volatile String key;

    /** How long the track is expected to be, from its metadata; 0 when it is not known. */
    private volatile double expectedSeconds;

    /** Set when the run can no longer produce an honest beatmap. See the class comment. */
    private volatile boolean spoilt;

    /**
     * Mono samples folded into the curve so far, for the progress readout alone.
     *
     * <p>Advanced by the worker and read from anywhere, hence volatile. {@link #arm} also zeroes it
     * from the caller's thread, deliberately: the worker's reset is queued behind whatever it is
     * still folding in, and until that lands a progress bar would otherwise show the previous
     * track's position on the new track. The curve itself is never touched from outside - this is a
     * readout, and {@link #curveFrames} is what the beatmap is derived from.
     */
    private volatile long framesHeard;

    // ------------------------------------------------------------------
    // Owned by the worker thread alone
    // ------------------------------------------------------------------

    private OnsetDetector detector = new OnsetDetector();
    private final float[] window = new float[OnsetDetector.WINDOW];
    private int filled;
    private float[] novelty = new float[INITIAL_NOVELTY];
    private int count;

    /** The worker's own copy of {@link #framesHeard}, read while deriving the beatmap. */
    private long curveFrames;

    /** Builds with the standard sensitivity. */
    public StreamBeatmapBuilder() {
        this(OnsetDetector.DEFAULT_SENSITIVITY);
    }

    /**
     * @param sensitivity how far above its neighbourhood a peak must stand; see
     *                    {@link OnsetDetector#DEFAULT_SENSITIVITY}
     */
    public StreamBeatmapBuilder(double sensitivity) {
        this.sensitivity = sensitivity;
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "sdmk-stream-analysis");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    // ------------------------------------------------------------------
    // Told from outside
    // ------------------------------------------------------------------

    /**
     * Starts collecting a track, throwing away whatever was being collected before.
     *
     * <p>Call this as the track is loaded and before it plays. Blocks that arrive before it are
     * blocks of a different track and are not wanted; blocks that arrive after the track has
     * already been playing for a while would produce a curve missing its own beginning, which
     * {@link #finish} then rejects on length. That rejection is the safety net rather than the
     * mechanism - arming at load is what makes a run usable.
     *
     * @param cacheKey        what the finished beatmap is stored under; must not be {@code null}
     * @param trackSeconds    how long the track is expected to run, or 0 when that is not known
     */
    public void arm(String cacheKey, double trackSeconds) {
        Objects.requireNonNull(cacheKey, "cacheKey must not be null");
        runId.incrementAndGet();
        this.key = cacheKey;
        this.expectedSeconds = Math.max(0, trackSeconds);
        this.spoilt = false;
        this.framesHeard = 0;
        this.pending.set(0);
        // The curve belongs to the worker, so it is reset on the worker rather than from here - a
        // reset racing a block still being transformed would leave the new curve starting
        // mid-window, and the whole track would be analysed half a window out of step.
        long myRun = this.runId.get();
        submitRaw(() -> {
            if (runId.get() == myRun) {
                reset();
            }
        });
    }

    /**
     * Gives up the run in flight.
     *
     * <p>Called on a seek, on a pause long enough to have lost the pipe's contents, and whenever
     * anything else makes the samples stop lining up with the track. Cheap and safe to call when
     * nothing is armed, and safe to call immediately after {@link #finish} - that derivation is
     * already queued behind the audio it needs and this cannot reach into it.
     *
     * <p>The curve itself is left alone rather than cleared here: it belongs to the worker, and
     * {@link #arm} queues the reset there in order. Reaching across to clear it would race a block
     * still being folded in.
     */
    public void abandon() {
        if (key != null) {
            LOG.fine(String.format("Abandoned the stream analysis after %.1fs", heardSeconds()));
        }
        // The run id is deliberately not bumped here - see the field. Clearing the key is what
        // stops more audio being taken; anything already handed over belongs to the run being
        // finished and must still reach it.
        key = null;
        spoilt = false;
        pending.set(0);
    }

    /**
     * Finishes the run and hands back the beatmap, if the whole track was heard.
     *
     * <p><strong>The answer arrives on the analysis thread, and that is what makes the ordering
     * safe rather than merely likely.</strong> This does not wait: it queues the derivation behind
     * the blocks already handed over, on the one worker that consumes them, so "after everything
     * heard so far" is a position in a queue rather than a guess about how long a drain takes. The
     * caller is free to {@link #abandon()} and {@link #arm} the next track the instant this
     * returns - the reset that arming queues lands after this task, not in the middle of it.
     *
     * <p>Waiting instead would mean blocking whichever thread announced the end of the track, which
     * is either the playback thread or the interface one. Neither may be held for the time a
     * four-minute peak-pick takes.
     *
     * @param whenDone told the beatmap, or {@code null} when nothing usable was collected; runs on
     *                 the analysis thread and must not touch the scene graph
     */
    public void finish(java.util.function.Consumer<Beatmap> whenDone) {
        Objects.requireNonNull(whenDone, "whenDone must not be null");
        String finishing = key;
        if (finishing == null) {
            whenDone.accept(null);
            return;
        }
        double expected = expectedSeconds;
        boolean dropped = spoilt;

        boolean queued = submitRaw(() -> whenDone.accept(derive(finishing, expected, dropped)));
        if (!queued) {
            whenDone.accept(null);
        }
    }

    /**
     * Turns the collected curve into a beatmap, or refuses it.
     *
     * <p>Runs on the analysis thread, which owns the curve.
     *
     * @param cacheKey the key the result belongs under
     * @param expected how long the track was expected to run, or 0 when unknown
     * @param dropped  whether any audio was missed
     * @return the beatmap, or {@code null}
     */
    private Beatmap derive(String cacheKey, double expected, boolean dropped) {
        double heard = curveFrames / (double) AppConfig.SAMPLE_RATE;
        boolean whole = expected <= 0 || heard >= expected * COMPLETE_FRACTION;

        if (dropped || !whole || count < MIN_NOVELTY_VALUES) {
            LOG.fine(String.format(
                    "Discarding the stream analysis: heard %.1fs of an expected %.1fs%s",
                    heard, expected, dropped ? ", and blocks were dropped" : ""));
            return null;
        }

        Beatmap map =
                BeatmapAnalyzer.fromNovelty(cacheKey, novelty, count, curveFrames, sensitivity);
        LOG.info(String.format("Built a beatmap from the stream: %.1fs, %.1f BPM, %d onsets, "
                + "%d on the beat", heard, map.bpm(), map.onsetCount(), map.strongBeatCount()));
        return map;
    }

    /**
     * Finishes the run and waits for the answer.
     *
     * <p>For tests and for the smoke test, which have nothing else to do until it arrives. Never
     * call this from the playback thread or the interface thread - {@link #finish} is what the
     * application uses, precisely so that neither is ever held.
     *
     * @param timeout how long to wait at most
     * @return the beatmap, or {@code null} when nothing usable was collected or the wait ran out
     */
    public Beatmap finishAndWait(java.time.Duration timeout) {
        java.util.concurrent.atomic.AtomicReference<Beatmap> answer =
                new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        finish(map -> {
            answer.set(map);
            done.countDown();
        });
        try {
            if (!done.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warning("The stream analysis did not finish in time");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return answer.get();
    }

    /**
     * Waits until the analysis thread has nothing left to do.
     *
     * <p>For tests and for the smoke test. The application never waits for this - what it wants is
     * the callback {@link #finish} already gives it - but a caller that has just asked for a
     * derivation and wants to look at what came of it has to be able to know it has happened.
     *
     * @param timeout how long to wait at most
     * @return whether the thread drained within the timeout
     */
    public boolean awaitIdle(java.time.Duration timeout) {
        CountDownLatch idle = new CountDownLatch(1);
        if (!submitRaw(idle::countDown)) {
            return true;
        }
        try {
            return idle.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** @return whether a run is being collected */
    public boolean isArmed() {
        return key != null;
    }

    /** @return the cache key of the run in flight, or {@code null} */
    public String armedKey() {
        return key;
    }

    /** @return how much of the track has been heard, in seconds */
    public double heardSeconds() {
        return framesHeard / (double) AppConfig.SAMPLE_RATE;
    }

    /**
     * @return how far through the expected running time the collection has got, 0.0 to 1.0, or
     *         {@code -1} when the track's length is not known
     */
    public double progress() {
        double expected = expectedSeconds;
        return expected <= 0 ? -1 : Math.clamp(heardSeconds() / expected, 0d, 1d);
    }

    // ------------------------------------------------------------------
    // The playback thread
    // ------------------------------------------------------------------

    /**
     * Takes one block of audio on its way to the sound card.
     *
     * <p>Runs on the playback thread. Sums to mono and hands the result over; everything else
     * happens on the worker.
     *
     * @param buffer the block, 16-bit signed stereo interleaved, little-endian
     * @param offset first valid byte
     * @param length how many bytes are valid, always a whole number of frames
     */
    @Override
    public void pcm(byte[] buffer, int offset, int length) {
        if (key == null || spoilt || length <= 0) {
            return;
        }
        if (pending.get() >= MAX_PENDING_BLOCKS) {
            // A gap in the curve cannot be detected downstream, so the run is given up rather than
            // completed with a hole in it.
            spoilt = true;
            LOG.warning("The stream analysis fell behind the audio and was given up");
            return;
        }

        int frames = length / AppConfig.BYTES_PER_FRAME;
        if (frames == 0) {
            return;
        }
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            // The same sum MonoPcmReader applies, so a stream-built curve and a file-built one are
            // derived from identical numbers rather than from two spellings of the same idea.
            float left = LevelAnalyzer.sample(buffer, offset, frame, LevelAnalyzer.LEFT);
            float right = LevelAnalyzer.sample(buffer, offset, frame, LevelAnalyzer.RIGHT);
            mono[frame] = (left + right) / 2f;
        }

        long myRun = runId.get();
        pending.incrementAndGet();
        boolean queued = submitRaw(() -> {
            pending.decrementAndGet();
            if (runId.get() == myRun) {
                consume(mono);
            }
            // Otherwise the song changed while this sat in the queue: it belongs to a curve nobody
            // wants, and appending it to the new one would corrupt a track nothing could detect.
        });
        if (!queued) {
            pending.decrementAndGet();
        }
    }

    /**
     * Hands work to the analysis thread.
     *
     * @param work what to do there
     * @return whether it was accepted; {@code false} once the builder is closed
     */
    private boolean submitRaw(Runnable work) {
        try {
            worker.execute(work);
            return true;
        } catch (RejectedExecutionException e) {
            // Closing. Nothing more will be collected and nothing is wrong.
            return false;
        }
    }

    // ------------------------------------------------------------------
    // The analysis thread
    // ------------------------------------------------------------------

    /** Starts a fresh curve. Runs on the worker. */
    private void reset() {
        detector = new OnsetDetector();
        Arrays.fill(window, 0f);
        filled = 0;
        count = 0;
        curveFrames = 0;
        framesHeard = 0;
    }

    /**
     * Folds one block of mono samples into the novelty curve.
     *
     * <p>The windowing is exactly {@link BeatmapAnalyzer}'s: the first window is filled outright and
     * every one after it reuses all but the last hop of its predecessor. A partial final hop is left
     * in the window and never transformed, which is the same eleven milliseconds that analyser
     * drops and for the same reason - padding it with zeros manufactures an onset at every ending.
     *
     * <p>Runs on the worker.
     *
     * @param mono the block's samples
     */
    private void consume(float[] mono) {
        int taken = 0;
        while (taken < mono.length) {
            int room = OnsetDetector.WINDOW - filled;
            int copy = Math.min(room, mono.length - taken);
            System.arraycopy(mono, taken, window, filled, copy);
            filled += copy;
            taken += copy;

            if (filled == OnsetDetector.WINDOW) {
                if (count == novelty.length) {
                    novelty = Arrays.copyOf(novelty, novelty.length * 2);
                }
                novelty[count++] = detector.novelty(window, 0);
                System.arraycopy(window, OnsetDetector.HOP, window, 0,
                        OnsetDetector.WINDOW - OnsetDetector.HOP);
                filled = OnsetDetector.WINDOW - OnsetDetector.HOP;
            }
        }
        curveFrames += mono.length;
        framesHeard = curveFrames;
    }

    /** Releases the analysis thread. Anything in flight is dropped. */
    @Override
    public void close() {
        abandon();
        worker.shutdownNow();
    }
}
