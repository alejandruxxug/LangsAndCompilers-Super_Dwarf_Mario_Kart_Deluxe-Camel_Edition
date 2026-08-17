package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * The screen the application closes on: the machine ejecting the cartridge.
 *
 * <p><strong>It exists because shutting down genuinely takes time and used to look like a
 * crash.</strong> Closing the window tears down the go-librespot child, and that is a polite
 * terminate followed by a grace period of up to five seconds before it is killed - all of which used
 * to happen inside {@code Application.stop()} on the interface thread, with the last window already
 * gone. What the user saw was the window vanish and the application sit in the dock, unresponsive,
 * for several seconds; on macOS long enough to earn a spinning cursor. Nothing was wrong, and there
 * was no way to tell that from the outside.
 *
 * <p>So the teardown moved to a background thread and this went in its place. The point is not the
 * picture: it is that <strong>the interface thread is free while the daemon is being stopped</strong>,
 * so the window still paints, still moves and still reports what it is waiting for. The bar is honest
 * about being a beat rather than a measurement - like the boot screen's, it cannot know how long a
 * subprocess will take to die - but the caption under it names the step actually in progress.
 *
 * <p>The colours come from {@link Palette#hardware()}, not from the active mood, and this screen and
 * {@link BootScreen} are the only two that do. They bracket the application: at one the system has
 * not started and at the other it has stopped, and a mood is something the software chose. See
 * {@link Palette#hardware()} for why that is still inside ground rule 7.
 *
 * <p>There is deliberately no way to cancel. A shutdown that can be called off is a state every view
 * behind this one would have to know about, and by the time this is on screen the audio line is
 * already closing.
 */
public class ShutdownScreen extends Pane {

    /** Size of the SHUTTING DOWN caption, in pixels. */
    private static final double PROMPT_SIZE = 14;

    /** Size of the line naming the step in progress, in pixels. */
    private static final double HINT_SIZE = 8;

    /** Largest size the name is splashed at, in pixels. */
    private static final double SPLASH_SIZE_MAX = 32;

    /** Smallest size the splash shrinks to. */
    private static final double SPLASH_SIZE_MIN = 8;

    /** How much of the width the splash may take. */
    private static final double SPLASH_WIDTH_SHARE = 0.7;

    /** How many lines the splash may run to; see {@code BootScreen.SPLASH_MAX_LINES}. */
    private static final int SPLASH_MAX_LINES = 3;

    /** Clear space under the splash before the caption; see {@code BootScreen.SPLASH_GAP}. */
    private static final double SPLASH_GAP = 24;

    /** Width of the drawn rims, in pixels. */
    private static final double RIM = 3;

    /** How wide one block of the bar is, in pixels. */
    private static final double BLOCK = 10;

    /**
     * How long the bar takes to sweep once, in seconds.
     *
     * <p>It sweeps rather than fills, because it is not measuring anything: nothing here knows how
     * long a subprocess will take to exit. A bar that filled to 90% and stopped would be claiming a
     * progress it cannot have, and a stalled progress bar reads as exactly the hang this screen was
     * built to stop looking like.
     */
    private static final double SWEEP_SECONDS = 1.1;

    /** How many blocks of the bar are lit at once as the sweep passes. */
    private static final int SWEEP_WIDTH = 4;

    private final Canvas canvas = new Canvas();

    /**
     * What is being stopped, named under the bar.
     *
     * <p>Volatile because it is set from whichever thread finished a teardown step, and those run off
     * the interface thread by design - that being the whole reason this screen can be drawn at all.
     */
    private volatile String status = "";

    private AnimationTimer timer;

    /** When the sweep started, in seconds on the wall clock. Nothing here is synced to audio. */
    private double startedAt = now();

    /** Builds the screen. Nothing moves until {@link #start()}. */
    public ShutdownScreen() {
        // A Canvas is picked over its whole rectangle whatever it has drawn in it. Nothing on this
        // screen is clickable, but leaving it pickable would put an invisible sheet over the window
        // for as long as this is up - the fault that ate the companion window's transport clicks.
        canvas.setMouseTransparent(true);
        getChildren().add(canvas);
    }

    /**
     * Sets the line printed under the bar.
     *
     * @param text what is being stopped, or {@code null} for nothing; shown as typed, so it should
     *             already be short enough for the screen
     */
    public void setStatus(String text) {
        this.status = text == null ? "" : text;
        // Asked for explicitly: a Parent only lays out when something marked it dirty, and the field
        // above is not observable. Without this the caption would change and the canvas would go on
        // showing the previous one, which reads as a screen that has stopped updating - the exact
        // impression this whole screen exists to avoid.
        requestLayout();
    }

    /** Starts the sweep. */
    public void start() {
        if (timer != null) {
            return;
        }
        startedAt = now();
        timer = new AnimationTimer() {
            @Override
            public void handle(long nanos) {
                requestLayout();
            }
        };
        timer.start();
    }

    /** Stops the sweep. Called when the application is finally going. */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /**
     * Draws the screen at a stated point in the sweep, without a running clock.
     *
     * <p>Unphotographable otherwise, for the reason {@code BootScreen.previewAt} and
     * {@code MiniPlayerView.previewAt} both exist: the smoke test holds the interface thread, so no
     * pulse arrives and the frame loop above never ticks.
     *
     * @param sweep how far through one sweep of the bar, 0 to 1
     */
    public void previewAt(double sweep) {
        startedAt = now() - Math.clamp(sweep, 0, 1) * SWEEP_SECONDS;
        requestLayout();
        applyCss();
        layout();
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.setWidth(width);
        canvas.setHeight(height);
        draw(width, height);
    }

    /** Paints the whole screen. */
    private void draw(double width, double height) {
        Palette palette = Palette.hardware();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(false);

        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, 0, width, height);

        double barWidth = Math.round(Math.min(width * 0.42, 520));
        double barHeight = 22;
        double barX = Math.round((width - barWidth) / 2);
        double barY = Math.round(height * 0.54);

        gc.setTextAlign(TextAlignment.CENTER);
        drawSplash(gc, width, barY, palette);

        gc.setFont(Fonts.pixel(PROMPT_SIZE));
        gc.setFill(palette.color(PaletteRole.PRIMARY));
        gc.fillText("SHUTTING DOWN", Math.round(width / 2), Math.round(barY - 30));

        drawSweep(gc, palette, barX, barY, barWidth, barHeight);

        String reporting = status;
        if (!reporting.isEmpty()) {
            // ACCENT, as on the boot screen and for the same reason: this is the one line that
            // changes while the bar runs, and it is the whole evidence that anything is happening.
            gc.setFont(Fonts.pixel(HINT_SIZE));
            gc.setFill(palette.color(PaletteRole.ACCENT));
            gc.fillText(reporting, Math.round(width / 2), Math.round(barY + barHeight + 24));
        }
    }

    /**
     * Draws the bar with a band of lit blocks travelling along it.
     *
     * <p>Wrapped rather than bounced, so the motion is always in one direction: a bar that reversed
     * would read as progress being undone.
     */
    private void drawSweep(GraphicsContext gc, Palette palette,
            double barX, double barY, double barWidth, double barHeight) {
        gc.setFill(palette.shaded(PaletteRole.SHADOW, 0.5));
        gc.fillRect(barX, barY, barWidth, barHeight);

        int blocks = (int) Math.floor(barWidth / BLOCK);
        if (blocks > 0) {
            double phase = ((now() - startedAt) / SWEEP_SECONDS) % 1;
            // The band starts entirely off the left end and leaves entirely off the right, so it
            // enters and exits rather than appearing in place.
            int head = (int) Math.floor(phase * (blocks + SWEEP_WIDTH)) - SWEEP_WIDTH;
            for (int i = 0; i < SWEEP_WIDTH; i++) {
                int block = head + i;
                if (block < 0 || block >= blocks) {
                    continue;
                }
                // Brightest at the leading edge, so the band has a direction. A distance between
                // roles rather than a colour, exactly as the runner's road surface is.
                gc.setFill(palette.mix(PaletteRole.PRIMARY_DIM, PaletteRole.PRIMARY,
                        (i + 1) / (double) SWEEP_WIDTH));
                gc.fillRect(barX + block * BLOCK + 1, barY + 3, BLOCK - 2, barHeight - 6);
            }
        }

        gc.setStroke(palette.color(PaletteRole.OUTLINE));
        gc.setLineWidth(RIM);
        gc.strokeRect(barX - RIM / 2, barY - RIM / 2, barWidth + RIM, barHeight + RIM);
    }

    /**
     * Splashes the name above the bar, matching the boot screen's.
     *
     * <p>Smaller than the boot screen's on purpose. This is a goodbye rather than an arrival, and the
     * two screens sitting at the same weight would make closing the application feel like as much of
     * an event as opening it.
     */
    private void drawSplash(GraphicsContext gc, double width, double barTopY, Palette palette) {
        double size = splashFontSize(width);
        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME,
                (int) Math.floor(width * SPLASH_WIDTH_SHARE / size));
        double lineHeight = size * 1.35;
        double baseline = barTopY - 30 - PROMPT_SIZE - SPLASH_GAP - lines.size() * lineHeight;

        gc.setFont(Fonts.pixel(size));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM));
        for (int i = 0; i < lines.size(); i++) {
            gc.fillText(lines.get(i), Math.round(width / 2),
                    Math.round(baseline + (i + 1) * lineHeight));
        }
    }

    /**
     * Picks the size the name is splashed at.
     *
     * <p>The same search as {@code BootScreen.splashFontSize} against this screen's own ceiling and
     * width share. It goes through {@code BootScreen.wrapName}, so the two screens break the name in
     * exactly the same places - a second idea of how to wrap it would show up as the title being
     * hyphenated differently on the way out than on the way in.
     *
     * @param width the screen's width, in pixels
     * @return the size, in whole pixels
     */
    static double splashFontSize(double width) {
        if (!(width > 0)) {
            return SPLASH_SIZE_MIN;
        }
        double room = width * SPLASH_WIDTH_SHARE;
        for (double size = SPLASH_SIZE_MAX; size > SPLASH_SIZE_MIN; size -= 1) {
            int perLine = (int) Math.floor(room / size);
            if (perLine < 1) {
                continue;
            }
            if (BootScreen.wrapName(AppConfig.APP_NAME, perLine).size() <= SPLASH_MAX_LINES) {
                return size;
            }
        }
        return SPLASH_SIZE_MIN;
    }

    /** @return the wall clock in seconds; nothing here is synchronised to audio */
    private static double now() {
        return System.nanoTime() / 1_000_000_000d;
    }
}
