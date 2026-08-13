package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.game.Entity;
import com.eia.superdwarfkart.game.EntityState;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.scene.canvas.GraphicsContext;

import java.util.Arrays;

/**
 * Measures why the runner feels the way it feels.
 *
 * <p><strong>The number that matters is the gap between one frame and the next, not the time spent
 * inside {@code redraw()}.</strong> A {@code Canvas} call does not draw anything: it appends to a
 * command buffer that the render thread rasterises later, during the pulse. So timing a loop of
 * {@code redraw()} calls measures how fast the commands are <em>written down</em>, which is a
 * fraction of what they cost, and a loop that never yields to a pulse has the renderer coalesce the
 * lot and paint once. That is why the existing smoke-test figure reports three-figure headroom on a
 * game that visibly stutters - it cannot see the thing being complained about, by construction.
 *
 * <p>The interval between {@link javafx.animation.AnimationTimer} callbacks has no such blind spot.
 * The toolkit runs one pulse per displayed frame and will not start another while the previous one
 * is still being painted, so back-pressure from the renderer, from the layout pass, from another
 * view's timer or from a garbage collection all land in the same measurement: the frame took
 * longer. It is the number the player is actually looking at.
 *
 * <p>Three further things are recorded because each one is a distinct cause of "laggy" with a
 * distinct fix, and the symptom alone does not tell them apart:
 *
 * <ul>
 *   <li><strong>The audio clock's own granularity.</strong> {@code position()} is what the sound
 *       card has rendered and it advances in whole buffers, so it repeats the same value for
 *       several frames and then jumps. How wide that step is decides whether smoothing it is
 *       enough. Recorded as the plateau length and the size of the jumps.</li>
 *   <li><strong>How far the smoothed reading sits from the raw one</strong>, and how often it gives
 *       up and snaps. A snap during ordinary playback is a visible lurch, and it means the clock is
 *       being asked to track something it should not be.</li>
 *   <li><strong>When the jump key was pressed, against the beat it was aimed at.</strong> The jump
 *       window is wide on one side and has no width at all on the other, which is not something a
 *       player can feel as anything but a frame-perfect input. Measured rather than argued
 *       about.</li>
 * </ul>
 *
 * <p>No {@code javafx} beyond the {@link GraphicsContext} it draws its overlay into, and it holds
 * no state the game reads - removing it changes nothing about how the runner behaves.
 */
final class RunnerDiagnostics {

    /** How many frames are kept for the percentile figures: about four seconds at sixty a second. */
    private static final int WINDOW = 240;

    /** How often the summary line is printed, in seconds. */
    private static final double REPORT_SECONDS = 2;

    /** A frame longer than this is late enough to be seen as a hitch, in milliseconds. */
    private static final double LATE_MILLIS = 20;

    /** A frame longer than this has visibly dropped one, in milliseconds. */
    private static final double DROPPED_MILLIS = 33;

    /** How many jump attempts are kept for the overlay. */
    private static final int JUMP_HISTORY = 8;

    // ------------------------------------------------------------------
    // Frame pacing
    // ------------------------------------------------------------------

    private final double[] intervals = new double[WINDOW];
    private int intervalCount;
    private int intervalCursor;
    private long previousPulseNanos;

    private double tickMillis;
    private double syncMillis;
    private double updateMillis;
    private double drawMillis;

    private long frames;
    private long lateFrames;
    private long droppedFrames;
    private double worstMillis;

    // ------------------------------------------------------------------
    // The audio clock
    // ------------------------------------------------------------------

    private double previousRaw = -1;
    private int rawPlateauFrames;
    private int longestPlateau;
    private double largestRawJumpMillis;
    private double clockErrorMillis;
    private long clockSnaps;

    // ------------------------------------------------------------------
    // Jump timing
    // ------------------------------------------------------------------

    /** Where the game clock stood the last time the jump key was accepted, in seconds. */
    private double lastJumpAt = Double.NEGATIVE_INFINITY;

    /** Offsets of the last few jumps from the obstacle they met, in milliseconds. */
    private final double[] jumpOffsets = new double[JUMP_HISTORY];
    private final boolean[] jumpCleared = new boolean[JUMP_HISTORY];
    private int jumpCount;

    private double lastReportAt;

    /**
     * Records one frame's pacing.
     *
     * @param pulseNanos the timestamp the toolkit handed the frame callback
     */
    void frameStarted(long pulseNanos) {
        if (previousPulseNanos != 0) {
            double millis = (pulseNanos - previousPulseNanos) / 1_000_000d;
            intervals[intervalCursor] = millis;
            intervalCursor = (intervalCursor + 1) % WINDOW;
            intervalCount = Math.min(WINDOW, intervalCount + 1);
            frames++;
            if (millis > LATE_MILLIS) {
                lateFrames++;
            }
            if (millis > DROPPED_MILLIS) {
                droppedFrames++;
            }
            worstMillis = Math.max(worstMillis, millis);
        }
        previousPulseNanos = pulseNanos;
    }

    /**
     * Records what the frame spent its time on.
     *
     * <p>These are the <em>recording</em> costs and are labelled as such in the overlay. They are
     * worth having only to rule the interface thread in or out: if they are small and the interval
     * is long, the time is going somewhere this class is not standing.
     *
     * @param sync   nanoseconds spent checking whether the course had to be rebuilt
     * @param update nanoseconds spent advancing the run
     * @param draw   nanoseconds spent writing the frame's draw commands
     */
    void tickCost(long sync, long update, long draw) {
        syncMillis = sync / 1_000_000d;
        updateMillis = update / 1_000_000d;
        drawMillis = draw / 1_000_000d;
        tickMillis = syncMillis + updateMillis + drawMillis;
    }

    /**
     * Records how the playback clock behaved this frame.
     *
     * @param raw      the position the sound card reported, in seconds
     * @param smoothed the reading the game was actually driven by, in seconds
     * @param snaps    how many times the smoothing has given up and jumped
     */
    void clock(double raw, double smoothed, long snaps) {
        if (raw == previousRaw) {
            rawPlateauFrames++;
        } else {
            if (previousRaw >= 0) {
                largestRawJumpMillis = Math.max(largestRawJumpMillis, (raw - previousRaw) * 1000);
                longestPlateau = Math.max(longestPlateau, rawPlateauFrames + 1);
            }
            rawPlateauFrames = 0;
            previousRaw = raw;
        }
        clockErrorMillis = (smoothed - raw) * 1000;
        clockSnaps = snaps;
    }

    /**
     * Records that the jump key was accepted.
     *
     * @param gameSeconds where the game clock stood when it was
     */
    void jumpPressed(double gameSeconds) {
        lastJumpAt = gameSeconds;
    }

    /**
     * Records what became of an obstacle that reached the racer's lane.
     *
     * <p>The offset is the whole story. Negative is an early press, positive a late one, and the
     * rule as written accepts everything from {@code -JUMP_SECONDS} up to exactly zero - so a
     * distribution centred anywhere near zero is losing its entire right-hand half, which is what a
     * frame-perfect input feels like even though the window is nearly half a second wide.
     *
     * @param entity the obstacle
     * @param state  what happened to it
     */
    void obstacleResolved(Entity entity, EntityState state) {
        if (state != EntityState.HIT && state != EntityState.CLEARED) {
            return;
        }
        int slot = jumpCount % JUMP_HISTORY;
        jumpOffsets[slot] = Double.isInfinite(lastJumpAt)
                ? Double.NaN
                : (lastJumpAt - entity.beatTime()) * 1000;
        jumpCleared[slot] = state == EntityState.CLEARED;
        jumpCount++;
    }

    /**
     * Prints a summary line when one is due.
     *
     * @param gameSeconds where the game clock stands
     */
    void reportIfDue(double gameSeconds) {
        if (gameSeconds - lastReportAt < REPORT_SECONDS) {
            return;
        }
        lastReportAt = gameSeconds;
        System.out.printf(
                "[diag] fps %.1f  frame p50 %.1f p95 %.1f max %.1f ms  late %.0f%% dropped %.0f%%"
                        + "  |  tick %.2f ms (sync %.2f upd %.2f draw %.2f)"
                        + "  |  clock plateau %d frames, jump %.0f ms, err %+.1f ms, snaps %d%n",
                fps(), percentile(50), percentile(95), worstMillis,
                100.0 * lateFrames / Math.max(1, frames),
                100.0 * droppedFrames / Math.max(1, frames),
                tickMillis, syncMillis, updateMillis, drawMillis,
                longestPlateau, largestRawJumpMillis, clockErrorMillis, clockSnaps);
    }

    /** @return the frame rate over the recent window */
    double fps() {
        double median = percentile(50);
        return median <= 0 ? 0 : 1000 / median;
    }

    /**
     * @param which the percentile wanted, 0 to 100
     * @return the frame interval at that percentile over the recent window, in milliseconds
     */
    private double percentile(int which) {
        if (intervalCount == 0) {
            return 0;
        }
        double[] sorted = Arrays.copyOf(intervals, intervalCount);
        Arrays.sort(sorted);
        int index = Math.clamp((int) Math.round(which / 100d * (sorted.length - 1)),
                0, sorted.length - 1);
        return sorted[index];
    }

    /**
     * Draws the readout over the road.
     *
     * @param gc     the context to draw into
     * @param x      left edge
     * @param y      top edge of the first line
     * @param colour resolves a palette role, so the overlay obeys the no-literals rule too
     */
    void draw(GraphicsContext gc, double x, double y, java.util.function.Function<PaletteRole,
            javafx.scene.paint.Color> colour) {
        double size = 8;
        double line = size + 5;
        String[] lines = {
                String.format("FPS %.1f   p50 %.1f  p95 %.1f  max %.1f ms",
                        fps(), percentile(50), percentile(95), worstMillis),
                String.format("LATE >20ms %.0f%%   DROPPED >33ms %.0f%%",
                        100.0 * lateFrames / Math.max(1, frames),
                        100.0 * droppedFrames / Math.max(1, frames)),
                String.format("TICK %.2f ms  sync %.2f  upd %.2f  draw %.2f  (record only)",
                        tickMillis, syncMillis, updateMillis, drawMillis),
                String.format("CLOCK plateau %d fr  jump %.0f ms  err %+.1f ms  snaps %d",
                        longestPlateau, largestRawJumpMillis, clockErrorMillis, clockSnaps),
                jumpLine(),
        };

        gc.setFill(colour.apply(PaletteRole.SHADOW));
        gc.fillRect(x - 6, y - size - 4, 420, lines.length * line + 10);
        gc.setFont(Fonts.pixel(size));
        for (int index = 0; index < lines.length; index++) {
            gc.setFill(colour.apply(index == 0 && fps() < 55
                    ? PaletteRole.NEGATIVE
                    : PaletteRole.TEXT_PRIMARY));
            gc.fillText(lines[index], x, y + index * line);
        }
    }

    /** @return the jump-timing line, oldest attempt first */
    private String jumpLine() {
        if (jumpCount == 0) {
            return "JUMP  - no obstacle met yet -";
        }
        StringBuilder text = new StringBuilder("JUMP ");
        int shown = Math.min(JUMP_HISTORY, jumpCount);
        for (int back = shown - 1; back >= 0; back--) {
            int slot = (jumpCount - 1 - back) % JUMP_HISTORY;
            double offset = jumpOffsets[slot];
            text.append(Double.isNaN(offset) ? "--" : String.format("%+.0f", offset))
                    .append(jumpCleared[slot] ? "ok " : "HIT ");
        }
        return text.toString();
    }
}
