package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.audio.Levels;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * The left and right level meters.
 *
 * <p><strong>Two bars, never one.</strong> A single mixed meter is the version of this feature that
 * looks finished and proves nothing; two independent bars are the version where a hard-panned track
 * visibly throws the needle to one side. Each channel is measured on its own from the deinterleaved
 * stream and drawn on its own here, and the pair is the check that the whole PCM path is correct.
 *
 * <p>Each bar carries two readings. The stack of lit blocks is the block's root mean square - how
 * loud it sounded - and the single bright block floating above it is the peak, held and let down
 * slowly. Without the peak cap a transient is over before the next frame is drawn and never appears
 * at all.
 *
 * <p><strong>Blocks rather than a smooth bar, and a logarithmic scale.</strong> The blocks are the
 * 8-bit convention and read from across a room; the scale is what makes the meter usable at all,
 * since music sits around a tenth of full scale and a linear bar would never leave its own floor.
 *
 * <p>Nothing here talks to the audio thread. The playback thread publishes four numbers into
 * {@link Levels} and this view polls them from an {@code AnimationTimer} at the display's rate,
 * which is exactly the arrangement ground rule 4 asks for: no work and no callback per audio block.
 */
public class LevelMeterView extends Pane {

    /** Height of one block, in whole pixels. */
    private static final double BLOCK_HEIGHT = 5;

    /** Gap between blocks. */
    private static final double BLOCK_GAP = 2;

    /** Width of each channel's bar. */
    private static final double BAR_WIDTH = 26;

    /** Space between the two bars. */
    private static final double BAR_GAP = 14;

    /** Width reserved on the left for the decibel scale. */
    private static final double SCALE_WIDTH = 26;

    /** Padding inside the panel. */
    private static final double PADDING = 10;

    /** Caption and label size. Whole pixels: a fractional pixel font is blurry. */
    private static final double LABEL_SIZE = 7;

    /** Decibel marks drawn beside the bars. */
    private static final int[] SCALE_MARKS = {0, -12, -24, -36, -48};

    /** How bright an unlit block is, as a fraction of its lit colour. */
    private static final double UNLIT_OPACITY = 0.14;

    private final Canvas canvas = new Canvas();
    private final Levels levels;

    /**
     * What the meters are showing, as opposed to what was last published.
     *
     * <p>These fall towards the published values one interface frame at a time. Drawing the raw
     * readings instead gives a meter that flickers between blocks, because a block is 23 ms of
     * audio and a frame is 16 ms of screen.
     */
    private float displayedLeftRms;
    private float displayedRightRms;
    private float displayedLeftPeak;
    private float displayedRightPeak;

    private AnimationTimer timer;

    /**
     * The low-to-high colour of each block, worked out once instead of per block per frame.
     *
     * <p>Two bars of seventy blocks at sixty frames a second is eight thousand colour
     * interpolations a second, each of them snapping back onto the 5-bit grid, for a ramp that only
     * changes when the bar is resized or the mood is switched.
     */
    private Color[] ramp;
    private int rampBlocks;

    /**
     * The palette the cached ramp was built from.
     *
     * <p>Compared by identity, which is exactly right: a palette is immutable and changing mood
     * installs a different object, so this invalidates itself when the mood system arrives.
     */
    private Palette rampPalette;

    /**
     * @param levels where the playback thread publishes; must not be {@code null}
     */
    public LevelMeterView(Levels levels) {
        this.levels = Objects.requireNonNull(levels, "levels must not be null");
        getStyleClass().add("level-meters");
        canvas.setManaged(false);
        getChildren().add(canvas);
    }

    /** Begins polling the levels and repainting. */
    public void start() {
        if (timer != null) {
            return;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        };
        timer.start();
    }

    /**
     * Stops polling.
     *
     * <p>Called on shutdown. A timer left running would repaint a canvas nobody can see, sixty
     * times a second, for as long as the application is open.
     */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /**
     * Reads the four published values, lets the displayed ones fall towards them, and repaints.
     *
     * <p>Skips the repaint entirely once everything has reached silence, so an idle player costs
     * nothing per frame.
     */
    private void tick() {
        float leftRms = levels.leftRms();
        float rightRms = levels.rightRms();
        float leftPeak = levels.leftPeak();
        float rightPeak = levels.rightPeak();

        boolean wasSilent = displayedLeftRms == 0 && displayedRightRms == 0
                && displayedLeftPeak == 0 && displayedRightPeak == 0;
        if (wasSilent && leftRms == 0 && rightRms == 0 && leftPeak == 0 && rightPeak == 0) {
            return;
        }

        displayedLeftRms = settle(Levels.decay(displayedLeftRms, leftRms));
        displayedRightRms = settle(Levels.decay(displayedRightRms, rightRms));
        displayedLeftPeak = settle(Levels.decay(displayedLeftPeak, leftPeak));
        displayedRightPeak = settle(Levels.decay(displayedRightPeak, rightPeak));
        redraw();
    }

    /**
     * Snaps a value that has decayed below the meter's floor to exactly zero.
     *
     * <p>Geometric decay never reaches zero, so without this the view would repaint forever after
     * the last note, chasing a number in the seventh decimal place.
     *
     * @param value the decayed value
     * @return the value, or zero once it is below anything the meter could show
     */
    private static float settle(float value) {
        return Levels.scale(value) <= 0 ? 0 : value;
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width != canvas.getWidth() || height != canvas.getHeight()) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            redraw();
        }
    }

    /** Repaints both bars. */
    public void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(color(PaletteRole.SURFACE));
        gc.fillRect(0, 0, width, height);

        gc.setFont(Fonts.pixel(LABEL_SIZE));
        gc.setFill(color(PaletteRole.TEXT_DIM));
        gc.fillText("LEVELS", PADDING, PADDING + LABEL_SIZE);

        double top = PADDING + LABEL_SIZE + 10;
        double bottom = height - PADDING - LABEL_SIZE - 6;
        double barHeight = bottom - top;
        if (barHeight < BLOCK_HEIGHT) {
            return;
        }

        double barsWidth = BAR_WIDTH * 2 + BAR_GAP;
        double left = Math.max(PADDING + SCALE_WIDTH, (width - barsWidth + SCALE_WIDTH) / 2);

        drawScale(gc, left - 6, top, barHeight);
        drawBar(gc, left, top, barHeight, displayedLeftRms, displayedLeftPeak, "L");
        drawBar(gc, left + BAR_WIDTH + BAR_GAP, top, barHeight, displayedRightRms,
                displayedRightPeak, "R");
    }

    /**
     * Draws one channel.
     *
     * @param gc     the context to draw into
     * @param x      left edge of the bar
     * @param y      top edge of the bar
     * @param height how tall the bar is
     * @param rms    the channel's root mean square, 0..1 linear
     * @param peak   the channel's held peak, 0..1 linear
     * @param label  the letter drawn beneath it
     */
    private void drawBar(GraphicsContext gc, double x, double y, double height,
                         float rms, float peak, String label) {
        gc.setFill(color(PaletteRole.BACKGROUND));
        gc.fillRect(x, y, BAR_WIDTH, height);
        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        // Half-pixel offsets keep a one-pixel stroke on the pixel grid instead of straddling it.
        gc.strokeRect(x + 0.5, y + 0.5, BAR_WIDTH - 1, height - 1);

        double pitch = BLOCK_HEIGHT + BLOCK_GAP;
        int blocks = (int) Math.floor((height - BLOCK_GAP * 2) / pitch);
        if (blocks <= 0) {
            return;
        }

        int lit = (int) Math.round(Levels.scale(rms) * blocks);
        // The cap sits on the block the peak reached, so it is visible even when it is the only
        // thing lit - which is exactly the case a transient produces.
        int peakBlock = (int) Math.round(Levels.scale(peak) * blocks) - 1;

        double blockWidth = BAR_WIDTH - 6;
        double blockX = x + 3;
        Color[] colors = ramp(blocks);
        for (int index = 0; index < blocks; index++) {
            double blockY = y + height - BLOCK_GAP - (index + 1) * pitch + BLOCK_GAP;
            Color face = colors[index];
            if (index == peakBlock) {
                gc.setFill(color(PaletteRole.METER_HIGH));
            } else if (index < lit) {
                gc.setFill(face);
            } else {
                gc.setFill(face.deriveColor(0, 1, 1, UNLIT_OPACITY));
            }
            gc.fillRect(blockX, blockY, blockWidth, BLOCK_HEIGHT);
        }

        gc.setFill(color(PaletteRole.TEXT_DIM));
        gc.fillText(label, x + BAR_WIDTH / 2 - LABEL_SIZE / 2, y + height + LABEL_SIZE + 4);
    }

    /**
     * Returns the meter's colour ramp, rebuilding it only when it could have changed.
     *
     * @param blocks how many blocks a bar holds
     * @return one colour per block, bottom to top
     */
    private Color[] ramp(int blocks) {
        Palette current = Palette.active();
        if (ramp != null && rampBlocks == blocks && rampPalette == current) {
            return ramp;
        }
        Color[] built = new Color[blocks];
        for (int index = 0; index < blocks; index++) {
            built[index] = current.mix(PaletteRole.METER_LOW, PaletteRole.METER_HIGH,
                    index / (double) Math.max(1, blocks - 1));
        }
        ramp = built;
        rampBlocks = blocks;
        rampPalette = current;
        return built;
    }

    /**
     * Draws the decibel marks the bars are read against.
     *
     * @param gc     the context to draw into
     * @param right  where the labels end, right-aligned to this
     * @param y      top edge of the bars
     * @param height how tall the bars are
     */
    private void drawScale(GraphicsContext gc, double right, double y, double height) {
        gc.setFill(color(PaletteRole.TEXT_DIM, 0.65));
        for (int mark : SCALE_MARKS) {
            double fraction = (mark - Levels.FLOOR_DB) / -Levels.FLOOR_DB;
            double markY = y + height * (1 - fraction);
            String text = String.valueOf(mark);
            // Press Start 2P is fixed-width at about one em per glyph, so no measuring pass is
            // needed and this stays right-aligned under the fallback font too.
            gc.fillText(text, right - text.length() * LABEL_SIZE, markY + LABEL_SIZE / 2);
        }
    }

    /**
     * @param role the role to resolve
     * @return the active palette's colour for that role
     */
    private static Color color(PaletteRole role) {
        return Palette.active().color(role);
    }

    /**
     * @param role    the role to resolve
     * @param opacity 0.0 transparent to 1.0 opaque
     * @return the role's colour at that opacity
     */
    private static Color color(PaletteRole role, double opacity) {
        return Palette.active().color(role, opacity);
    }
}
