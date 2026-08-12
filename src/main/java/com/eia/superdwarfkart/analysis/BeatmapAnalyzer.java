package com.eia.superdwarfkart.analysis;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.audio.MonoPcmReader;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleConsumer;
import java.util.logging.Logger;

/**
 * Reads a whole track and works out where its beats are.
 *
 * <p><strong>This never runs on the playback thread and never touches the one that is playing.</strong>
 * It opens its own decode of the file through {@link MonoPcmReader} and reads it as fast as the
 * decoder manages - a four-minute track is about twenty thousand transforms and takes a second or
 * two, which is a second or two of stuttering audio if it were done anywhere near the sound card.
 * {@link BeatmapService} is what puts it on a background thread; this class is the algorithm.
 *
 * <h2>The four stages</h2>
 *
 * <ol>
 *   <li>{@link OnsetDetector} turns the audio into a novelty curve and the curve into onsets.</li>
 *   <li>The tempo comes from a <strong>histogram of the intervals between onsets</strong>. Every
 *       pair of nearby onsets votes for the tempo it implies, and the winner is the interval the
 *       track keeps returning to. A single wrong onset shifts two votes and changes nothing, which
 *       is exactly why this is not derived from the average gap.</li>
 *   <li>The grid's <strong>phase</strong> is found the same way: where in the beat each onset falls,
 *       histogrammed, and the busiest position wins. Tempo says how far apart the beats are; phase
 *       says where the first one is, and a course built on the right tempo with the wrong phase is
 *       off by half a beat for the entire song.</li>
 *   <li>The <strong>strong beats</strong> are the onsets nearest the resulting grid - real events in
 *       the audio, not grid points. The game spawns on things that can actually be heard, so a
 *       track that drops out for a bar produces no entities in that bar rather than four
 *       entities on silence.</li>
 * </ol>
 */
public final class BeatmapAnalyzer {

    private static final Logger LOG = Logger.getLogger(BeatmapAnalyzer.class.getName());

    /**
     * Slowest tempo considered, in beats per minute.
     *
     * <p>The range spans slightly more than an octave, which is what lets every interval be folded
     * into it: a tempo is only ever ambiguous by a factor of two, so 60 and 240 both fold onto 120
     * and vote together instead of splitting the histogram three ways.
     */
    public static final double MIN_BPM = 70;

    /** Fastest tempo considered, in beats per minute. */
    public static final double MAX_BPM = 180;

    /** Width of one tempo histogram bin, in beats per minute. */
    private static final double BPM_BIN = 1.0;

    /**
     * Bins either side of a candidate that count towards it.
     *
     * <p><strong>This width is what keeps a fast track off its own half-tempo, and the reason is
     * not obvious.</strong> Onsets can only be located to the nearest analysis hop, 11.6 ms, so the
     * gap between two adjacent beats alternates between a whole number of hops either side of the
     * true period. In beats per minute that spread grows with the square of the tempo: about 1.6
     * BPM at 90, but nearly 6 BPM at 175. So a fast track's fundamental arrives smeared across
     * half a dozen bins while the interval to the <em>second</em> onset - twice as long, and
     * therefore half as sensitive - lands squarely in one. Judged bin by bin the concentrated
     * half-tempo outscores the smeared fundamental, and a drum and bass track is reported as a
     * ballad however the votes are weighted.
     *
     * <p>Three bins either side covers the spread at the top of the range. It over-smooths the
     * bottom, where the spread is under two bins, but merging tempos 3 BPM apart at 90 BPM costs
     * nothing - that is well inside the uncertainty of the measurement in the first place.
     */
    private static final int SMOOTHING_BINS = 3;

    /** Longest gap between two onsets that still votes for a tempo, in seconds. */
    private static final double MAX_VOTING_INTERVAL = 2.0;

    /** How many following onsets each onset votes with. */
    private static final int VOTING_NEIGHBOURS = 8;

    /** Bins the beat phase is searched over, across one beat. */
    private static final int PHASE_BINS = 64;

    /**
     * How near the grid an onset must fall to count as landing on the beat, as a fraction of the
     * beat.
     *
     * <p>A quarter beat either side, so the acceptance windows of consecutive beats exactly meet
     * without overlapping and no onset can be claimed by two beats.
     */
    private static final double GRID_TOLERANCE = 0.25;

    /**
     * How far either side of the histogram's estimate the tempo is fitted, as a fraction.
     *
     * <p>Three per cent is several times the histogram's own bin width, so the true value is
     * comfortably inside the search, and narrow enough that the search cannot wander onto a
     * neighbouring multiple of the beat.
     */
    private static final double SEARCH_FRACTION = 0.03;

    /**
     * Steps taken across that range.
     *
     * <p>The fit is sharp - at 120 BPM over four minutes it is a fifth of a beat per minute wide -
     * so a coarse search would step straight over it. A thousand steps resolves it several times
     * over and costs a few milliseconds once per track.
     */
    private static final int SEARCH_STEPS = 1000;

    /**
     * How far above chance the fit has to score before its answer is used.
     *
     * <p>Scattered onsets do not score zero. A set of {@code n} unrelated angles still has a
     * resultant, of length about {@code 1/sqrt(n)} - so the raw score means nothing until it is
     * measured in those units, which is what multiplying by {@code sqrt(n)} does.
     *
     * <p>The case this exists for is a track whose events are twice as fast as the tempo it folded
     * onto: on the folded beat, half its onsets sit at the start and half exactly halfway through,
     * they point in opposite directions, and the fit collapses to nothing and then locks onto
     * whatever noise is nearest. Measured, that case scores 0.16 while every genuine fit scored
     * between 6.3 and 6.8 - so the two are not close, and anything under this bar falls back to the
     * histogram's own answer instead. A short track scores low too, and rightly: with few beats
     * there is nothing to fit and almost no drift to correct.
     */
    private static final double MIN_FIT_SIGNIFICANCE = 2.0;

    /** How often the progress callback is invoked, in analysis windows. */
    private static final int PROGRESS_EVERY = 128;

    private final double sensitivity;

    /** Analyses with the standard sensitivity. */
    public BeatmapAnalyzer() {
        this(OnsetDetector.DEFAULT_SENSITIVITY);
    }

    /**
     * @param sensitivity how far above its neighbourhood a peak must stand; see
     *                    {@link OnsetDetector#DEFAULT_SENSITIVITY}
     */
    public BeatmapAnalyzer(double sensitivity) {
        this.sensitivity = sensitivity;
    }

    /**
     * Analyses a file from beginning to end.
     *
     * <p>Interruptible: a thread interrupted part way through abandons the work rather than
     * finishing a decode nobody is waiting for. A half-analysed track must never be returned,
     * because it would be indistinguishable from a real result and would be cached as one.
     *
     * @param file       the audio file to analyse; must not be {@code null}
     * @param sourceHash the file's content hash, stored in the result as the cache key
     * @param progress   told how far along the analysis is, 0.0 to 1.0; may be {@code null}
     * @return the beatmap
     * @throws com.eia.superdwarfkart.audio.AudioException if the file cannot be read or decoded
     * @throws CancellationException                        if the calling thread was interrupted
     */
    public Beatmap analyze(Path file, String sourceHash, DoubleConsumer progress) {
        long startedAt = System.nanoTime();

        float[] novelty;
        int count;
        long frames;

        try (MonoPcmReader reader = new MonoPcmReader(file)) {
            long expectedFrames = Math.max(1, Math.round(
                    reader.estimatedDuration().toNanos() / 1e9 * AppConfig.SAMPLE_RATE));
            int expectedWindows = (int) Math.max(16, expectedFrames / OnsetDetector.HOP);

            OnsetDetector detector = new OnsetDetector();
            float[] window = new float[OnsetDetector.WINDOW];
            novelty = new float[expectedWindows];
            count = 0;

            // The first window is the only one that has to be filled outright; every one after it
            // reuses the second half of its predecessor, which is what the overlap means.
            if (!fill(reader, window, 0, OnsetDetector.WINDOW)) {
                return new Beatmap(sourceHash, AppConfig.ANALYZER_VERSION, 0, 0,
                        new double[0], new double[0]);
            }

            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Analysis of " + file.getFileName()
                            + " was cancelled");
                }
                if (count == novelty.length) {
                    novelty = Arrays.copyOf(novelty, novelty.length * 2);
                }
                novelty[count++] = detector.novelty(window, 0);

                if (count % PROGRESS_EVERY == 0 && progress != null) {
                    progress.accept(Math.min(0.99, reader.framesRead() / (double) expectedFrames));
                }

                System.arraycopy(window, OnsetDetector.HOP, window, 0,
                        OnsetDetector.WINDOW - OnsetDetector.HOP);
                if (!fill(reader, window, OnsetDetector.WINDOW - OnsetDetector.HOP,
                        OnsetDetector.HOP)) {
                    // A partial final hop is dropped rather than zero-padded: eleven milliseconds
                    // of a track, and padding it would manufacture an onset at every fade-out.
                    break;
                }
            }
            frames = reader.framesRead();
        }

        double duration = frames / (double) AppConfig.SAMPLE_RATE;
        double[] onsets = OnsetDetector.pickPeaks(novelty, count, sensitivity);
        double bpm = estimateBpm(onsets);
        double[] strong = strongBeats(onsets, bpm, duration);

        if (progress != null) {
            progress.accept(1.0);
        }
        LOG.info(String.format("Analysed %s in %.2fs: %.1f BPM, %d onsets, %d on the beat",
                file.getFileName(), (System.nanoTime() - startedAt) / 1e9, bpm,
                onsets.length, strong.length));
        return new Beatmap(sourceHash, AppConfig.ANALYZER_VERSION, duration, bpm, onsets, strong);
    }

    /**
     * Reads exactly the requested number of samples, or reports the end of the track.
     *
     * @param reader      where the samples come from
     * @param destination where to put them
     * @param offset      first index to write
     * @param length      how many are needed
     * @return whether the full amount was read
     */
    private static boolean fill(MonoPcmReader reader, float[] destination, int offset, int length) {
        int filled = 0;
        while (filled < length) {
            int read = reader.readMono(destination, offset + filled, length - filled);
            if (read <= 0) {
                return false;
            }
            filled += read;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Tempo
    // ------------------------------------------------------------------

    /**
     * Works out the tempo from the gaps between onsets.
     *
     * <p>Each onset votes with the several that follow it rather than only the next one, so a
     * missed onset costs a few votes instead of turning one beat into two. Every interval is folded
     * into {@link #MIN_BPM}..{@link #MAX_BPM} first, which is what makes half-time and double-time
     * readings of the same rhythm reinforce each other instead of splitting the histogram.
     *
     * <p><strong>A vote is worth less the further apart the two onsets are, and this is what keeps
     * the answer on the right octave.</strong> The considered range spans more than a factor of two,
     * so a fast track can be read correctly at its own tempo <em>and</em> incorrectly at half of it,
     * with both readings inside the range and neither folded onto the other. At 174 BPM the gap to
     * the next onset votes for 174, but the gaps to the second and the fourth both vote for 87 - so
     * counting every vote equally, the half-tempo wins two to one and a drum and bass track comes
     * back as a ballad. Weighting by {@code 1/distance} says what is actually true: the interval
     * between <em>adjacent</em> events is the direct evidence, and a gap spanning three onsets is
     * weaker evidence because it may equally well span a beat that was missed.
     *
     * @param onsets the onset times in seconds, ascending
     * @return the tempo in beats per minute, or 0 when there were too few onsets to tell
     */
    public static double estimateBpm(double[] onsets) {
        if (onsets == null || onsets.length < 4) {
            return 0;
        }

        int bins = (int) Math.ceil((MAX_BPM - MIN_BPM) / BPM_BIN) + 1;
        double[] histogram = new double[bins];

        for (int first = 0; first < onsets.length; first++) {
            int last = Math.min(onsets.length, first + 1 + VOTING_NEIGHBOURS);
            for (int second = first + 1; second < last; second++) {
                double interval = onsets[second] - onsets[first];
                if (interval <= 0 || interval > MAX_VOTING_INTERVAL) {
                    break;
                }
                int bin = binOf(fold(60 / interval));
                if (bin >= 0) {
                    histogram[bin] += 1.0 / (second - first);
                }
            }
        }

        int best = smoothedPeak(histogram);
        if (best < 0) {
            return 0;
        }
        return lockOnto(onsets, refine(histogram, best));
    }

    /**
     * Sharpens a rough tempo by fitting the grid to every onset at once.
     *
     * <p><strong>A histogram cannot be precise enough on its own, and the reason is that the error
     * accumulates.</strong> Its bins are a beat per minute wide, so its answer is good to a few
     * tenths - which sounds like plenty until the arithmetic is done: three tenths of a beat per
     * minute, over a four-minute track at 120, is two thirds of a second of drift by the end. The
     * grid starts on the beat and finishes most of a beat away from it.
     *
     * <p>So the histogram is used only to establish which tempo, and roughly where; the value is
     * then fitted against the onsets themselves. Each onset is treated as an angle around the beat
     * and the period that makes those angles agree most closely wins - the standard measure of how
     * concentrated a set of directions is. The peak is sharp precisely because errors accumulate:
     * the wrong period by a fraction of a per cent puts the late onsets out of step with the early
     * ones, and the sum collapses.
     *
     * @param onsets the onset times in seconds, ascending
     * @param roughBpm the histogram's estimate
     * @return the fitted tempo in beats per minute
     */
    private static double lockOnto(double[] onsets, double roughBpm) {
        if (roughBpm <= 0 || onsets.length < 8) {
            return roughBpm;
        }
        double span = roughBpm * SEARCH_FRACTION;
        double step = span * 2 / SEARCH_STEPS;

        double bestBpm = roughBpm;
        double bestScore = -1;
        for (int index = 0; index <= SEARCH_STEPS; index++) {
            double candidate = roughBpm - span + index * step;
            if (candidate <= 0) {
                continue;
            }
            double score = coherence(onsets, 60 / candidate);
            if (score > bestScore) {
                bestScore = score;
                bestBpm = candidate;
            }
        }
        // Believed only if it beat what scattered onsets would have managed; see the constant.
        return bestScore * Math.sqrt(onsets.length) >= MIN_FIT_SIGNIFICANCE ? bestBpm : roughBpm;
    }

    /**
     * Measures how consistently the onsets fall at the same point in the beat.
     *
     * @param onsets the onset times in seconds
     * @param period seconds per beat
     * @return 0 when the onsets are scattered across the beat, 1 when they all share a position
     */
    private static double coherence(double[] onsets, double period) {
        double sine = 0;
        double cosine = 0;
        for (double onset : onsets) {
            double angle = 2 * Math.PI * onset / period;
            sine += Math.sin(angle);
            cosine += Math.cos(angle);
        }
        return Math.hypot(sine, cosine) / onsets.length;
    }

    /**
     * Folds a tempo into the considered range by doubling or halving it.
     *
     * @param bpm any positive tempo
     * @return the equivalent tempo inside {@link #MIN_BPM}..{@link #MAX_BPM}
     */
    private static double fold(double bpm) {
        double folded = bpm;
        while (folded < MIN_BPM) {
            folded *= 2;
        }
        while (folded > MAX_BPM) {
            folded /= 2;
        }
        return folded;
    }

    /**
     * @param bpm a tempo inside the considered range
     * @return its histogram bin, or {@code -1} if it fell outside
     */
    private static int binOf(double bpm) {
        int bin = (int) Math.round((bpm - MIN_BPM) / BPM_BIN);
        int bins = (int) Math.ceil((MAX_BPM - MIN_BPM) / BPM_BIN) + 1;
        return bin >= 0 && bin < bins ? bin : -1;
    }

    /**
     * Finds the busiest neighbourhood, rather than the single tallest bin.
     *
     * <p>A real tempo never lands on an exact bin and, worse, arrives spread across several of them
     * for the reason set out on {@link #SMOOTHING_BINS}. Judging each candidate by the votes around
     * it as well as on it is what lets a broad, strong peak beat a narrow spike.
     *
     * @param histogram the vote counts
     * @return the winning bin, or {@code -1} if nothing voted
     */
    private static int smoothedPeak(double[] histogram) {
        int best = -1;
        double bestScore = 0;
        for (int bin = 0; bin < histogram.length; bin++) {
            double score = 0;
            for (int near = Math.max(0, bin - SMOOTHING_BINS);
                 near <= Math.min(histogram.length - 1, bin + SMOOTHING_BINS); near++) {
                score += histogram[near];
            }
            if (score > bestScore) {
                bestScore = score;
                best = bin;
            }
        }
        return best;
    }

    /**
     * Interpolates the true tempo within the winning neighbourhood.
     *
     * <p>The centre of mass of the same span the peak was scored over, so a tempo whose votes fell
     * either side of a bin comes back between them rather than snapped to whichever side happened
     * to be counted. Using a narrower span here than {@link #smoothedPeak} used would be worse than
     * useless: at the top of the range the winning bin itself can hold no votes at all, with the
     * whole peak sitting in the bins around it.
     *
     * @param histogram the vote counts
     * @param peak      the winning bin
     * @return the tempo in beats per minute
     */
    private static double refine(double[] histogram, int peak) {
        double weighted = 0;
        double total = 0;
        for (int bin = Math.max(0, peak - SMOOTHING_BINS);
             bin <= Math.min(histogram.length - 1, peak + SMOOTHING_BINS); bin++) {
            weighted += bin * histogram[bin];
            total += histogram[bin];
        }
        double centre = total > 0 ? weighted / total : peak;
        return MIN_BPM + centre * BPM_BIN;
    }

    // ------------------------------------------------------------------
    // The grid, and which onsets sit on it
    // ------------------------------------------------------------------

    /**
     * Picks out the onsets that fall on the tempo grid.
     *
     * <p>The phase is found before the grid can be laid: an onset's position <em>within</em> the
     * beat is histogrammed across every onset in the track, and the busiest position is where the
     * beat is. The circular mean of that neighbourhood then refines it, which is what stops a beat
     * that straddles the wrap-around from being averaged to the opposite side of the bar.
     *
     * @param onsets   the onset times in seconds, ascending
     * @param bpm      the detected tempo, or 0 when there is none
     * @param duration the track's playing time in seconds
     * @return the onsets nearest the grid, ascending; empty when there is no tempo
     */
    public static double[] strongBeats(double[] onsets, double bpm, double duration) {
        if (onsets == null || onsets.length == 0 || bpm <= 0) {
            return new double[0];
        }
        double period = 60 / bpm;
        double phase = estimatePhase(onsets, period);
        double tolerance = period * GRID_TOLERANCE;

        double[] found = new double[onsets.length];
        int count = 0;
        int previous = -1;

        for (double beat = phase; beat <= duration + tolerance; beat += period) {
            int nearest = nearestOnset(onsets, beat);
            if (nearest < 0 || nearest == previous) {
                continue;
            }
            if (Math.abs(onsets[nearest] - beat) > tolerance) {
                continue;
            }
            found[count++] = onsets[nearest];
            previous = nearest;
        }
        return Arrays.copyOf(found, count);
    }

    /**
     * Finds where in the beat the music actually lands.
     *
     * @param onsets the onset times in seconds, ascending
     * @param period seconds per beat
     * @return the time of the first beat, in seconds, inside {@code [0, period)}
     */
    private static double estimatePhase(double[] onsets, double period) {
        double[] histogram = new double[PHASE_BINS];
        for (double onset : onsets) {
            double within = onset % period;
            if (within < 0) {
                within += period;
            }
            int bin = (int) (within / period * PHASE_BINS);
            histogram[Math.min(PHASE_BINS - 1, bin)]++;
        }

        int best = 0;
        double bestScore = -1;
        for (int bin = 0; bin < PHASE_BINS; bin++) {
            // Wrapped neighbours, because the phase is a circle: the bin before bin 0 is the last.
            double score = histogram[bin]
                    + histogram[(bin + PHASE_BINS - 1) % PHASE_BINS]
                    + histogram[(bin + 1) % PHASE_BINS];
            if (score > bestScore) {
                bestScore = score;
                best = bin;
            }
        }

        // Averaged as angles rather than as numbers, so a peak sitting across the wrap does not
        // come back pointing at the middle of the bar.
        double sine = 0;
        double cosine = 0;
        for (double onset : onsets) {
            double within = onset % period;
            if (within < 0) {
                within += period;
            }
            int bin = Math.min(PHASE_BINS - 1, (int) (within / period * PHASE_BINS));
            int distance = Math.min(Math.abs(bin - best), PHASE_BINS - Math.abs(bin - best));
            if (distance > 1) {
                continue;
            }
            double angle = 2 * Math.PI * within / period;
            sine += Math.sin(angle);
            cosine += Math.cos(angle);
        }
        if (sine == 0 && cosine == 0) {
            return (best + 0.5) / PHASE_BINS * period;
        }
        double mean = Math.atan2(sine, cosine);
        if (mean < 0) {
            mean += 2 * Math.PI;
        }
        return mean / (2 * Math.PI) * period;
    }

    /**
     * @param onsets an ascending series
     * @param time   the time to search around
     * @return the index of the onset closest to that time, or {@code -1} when there are none
     */
    private static int nearestOnset(double[] onsets, double time) {
        if (onsets.length == 0) {
            return -1;
        }
        int found = Arrays.binarySearch(onsets, time);
        if (found >= 0) {
            return found;
        }
        int after = -(found + 1);
        if (after == 0) {
            return 0;
        }
        if (after == onsets.length) {
            return onsets.length - 1;
        }
        return time - onsets[after - 1] <= onsets[after] - time ? after - 1 : after;
    }
}
