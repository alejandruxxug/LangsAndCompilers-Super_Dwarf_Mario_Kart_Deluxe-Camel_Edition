package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.assets.RacerFrame;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.ui.Fonts;
import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * What the three structure views have in common: a canvas that fills the panel, an animation
 * clock, and the drawing helpers that keep the pixel-art rules from having to be remembered at
 * every call site.
 *
 * <p>Each view draws <em>one</em> hand-written structure as something from a racing game - a
 * circuit, a starting grid, a tree of karts - and the view for the active mode is the one on
 * screen. Between them they are the reason this project is the lecture rather than a decoration
 * of it: circularity, first-in-first-out and in-order traversal are demonstrated by watching them
 * happen rather than explained over a static diagram.
 *
 * <p>Two rules are enforced here so no subclass can forget them:
 *
 * <ul>
 *   <li><strong>No colour is ever named.</strong> Everything resolves through {@link #color} by
 *       role, so the mood system reskins all three views without touching a drawing call.</li>
 *   <li><strong>Pixel art is never smoothed and never scaled fractionally.</strong>
 *       {@link #drawSprite} takes an integer scale, and image smoothing is switched off on every
 *       context before a subclass sees it.</li>
 * </ul>
 *
 * <p>Animation runs on a timer that is started when something moves and stopped when it settles,
 * so a view sitting still costs nothing per frame.
 */
public abstract class StructureView extends BorderPane {

    /** Body text size on the canvases. Whole pixels: a fractional pixel font is blurry. */
    protected static final double TEXT_SIZE = 7;

    /** Size for a heading drawn on the canvas. */
    protected static final double HEADING_SIZE = 9;

    private final Canvas canvas = new Canvas();
    private final Pane canvasHolder = new CanvasHolder();

    private AnimationTimer timer;
    private long clockStartNanos;
    private long animationStartNanos;
    private double animationDurationSeconds;
    private Runnable onAnimationFinished;

    /** How far the current animation has run, 0 at the start and 1 once it has settled. */
    private double progress = 1;

    /** Whether an animation is in flight, as opposed to the clock merely ticking. */
    private boolean animating;

    /** Steadily increasing seconds, for anything that animates without going anywhere. */
    private double clockSeconds;

    /**
     * Seconds already on the clock before the current timer run began.
     *
     * <p>The clock has to survive the timer stopping. Everything phase-based reads it - the
     * scrolling road most of all - and restarting from zero would snap the road to a new phase
     * every time the racer set off.
     */
    private double clockBaseSeconds;

    /** Repaints per second while nothing is moving; 0 leaves a settled view costing nothing. */
    private double idleFps;

    private long lastIdleRedrawNanos;

    protected StructureView() {
        getStyleClass().add("structure-view");
        canvas.setManaged(false);
        canvasHolder.getChildren().add(canvas);
        setCenter(canvasHolder);
    }

    /**
     * Draws the structure.
     *
     * <p>Called on every resize, on every change to the structure and on every animation frame.
     * The context has already been cleared and had image smoothing switched off.
     *
     * @param gc     the context to draw into
     * @param width  canvas width in pixels
     * @param height canvas height in pixels
     */
    protected abstract void draw(GraphicsContext gc, double width, double height);

    /** @return the mode this view visualizes */
    public abstract ModeId modeId();

    /**
     * Brings the view back in step with the structure behind it.
     *
     * <p>Subclasses override this to recompute whatever they cache - node positions, the lap
     * order - and must call {@code super.refresh()} or {@link #redraw()} to repaint.
     */
    public void refresh() {
        redraw();
    }

    /** Repaints immediately. */
    public void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Pixel art must not be interpolated, at any scale. JavaFX interpolates by default.
        gc.setImageSmoothing(false);
        gc.setFill(color(PaletteRole.BACKGROUND));
        gc.fillRect(0, 0, width, height);
        draw(gc, width, height);
    }

    /**
     * Places a control bar under the canvas.
     *
     * @param controls the bar, or {@code null} for a view that needs none
     */
    protected void setControls(Node controls) {
        setBottom(controls);
    }

    // ------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------

    /**
     * Starts an animation, redrawing every frame until it settles.
     *
     * <p>Starting a second animation replaces the first rather than queueing behind it: pressing
     * next twice quickly should land on the second song, not play two moves in sequence and fall
     * behind what is actually playing.
     *
     * @param seconds    how long the animation runs
     * @param onFinished run once the animation settles, or {@code null}
     */
    protected void animate(double seconds, Runnable onFinished) {
        stopAnimating();
        animationDurationSeconds = Math.max(seconds, 0.001);
        animationStartNanos = 0;
        onAnimationFinished = onFinished;
        progress = 0;
        animating = true;
        ensureTimerRunning();
    }

    /**
     * Asks for a steady repaint even when nothing is moving.
     *
     * <p>A kart parked on the current song still has its wheels turning, and a view that only
     * repainted while something travelled would freeze it mid-cycle. The rate is deliberately far
     * below the display refresh: sprite art of this kind reads better at eight frames a second
     * than at sixty, and it keeps an idle panel close to free.
     *
     * @param framesPerSecond repaints per second, or 0 to let the view settle and stop
     */
    protected void setIdleAnimation(double framesPerSecond) {
        idleFps = Math.max(0, framesPerSecond);
        if (idleFps > 0) {
            ensureTimerRunning();
        } else if (!animating) {
            stopTimer();
        }
    }

    private void ensureTimerRunning() {
        if (timer != null) {
            return;
        }
        clockStartNanos = 0;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (clockStartNanos == 0) {
                    clockStartNanos = now;
                    lastIdleRedrawNanos = now;
                }
                clockSeconds = clockBaseSeconds + (now - clockStartNanos) / 1_000_000_000d;

                if (animating) {
                    if (animationStartNanos == 0) {
                        animationStartNanos = now;
                    }
                    double elapsed = (now - animationStartNanos) / 1_000_000_000d;
                    progress = Math.min(1, elapsed / animationDurationSeconds);
                    redraw();
                    if (progress >= 1) {
                        finish();
                    }
                    return;
                }

                // Idling: repaint at the requested rate rather than every frame.
                if (idleFps <= 0) {
                    stopTimer();
                    return;
                }
                if ((now - lastIdleRedrawNanos) / 1_000_000_000d >= 1 / idleFps) {
                    lastIdleRedrawNanos = now;
                    redraw();
                }
            }
        };
        timer.start();
    }

    /**
     * Jumps any animation in flight straight to its end state.
     *
     * <p>What the view settles into is the state worth looking at - the kart parked at its new
     * position, the whole traversal lit - so this is what a screenshot needs, and catching a view
     * a few milliseconds into a move shows neither where it came from nor where it is going.
     */
    public void settle() {
        if (animating) {
            progress = 1;
            finish();
        }
    }

    /**
     * Releases the frame timer, for a view being taken off screen.
     *
     * <p>A swapped-out view whose timer kept running would repaint a canvas nobody can see, once
     * per frame, for as long as the application is open.
     */
    public void stop() {
        animating = false;
        onAnimationFinished = null;
        progress = 1;
        stopTimer();
    }

    /**
     * Ends any animation in flight and leaves the view settled.
     *
     * <p>The clock keeps running if the view idles, so the kart's wheels do not stop turning just
     * because it arrived somewhere.
     */
    protected void stopAnimating() {
        animating = false;
        onAnimationFinished = null;
        progress = 1;
        if (idleFps <= 0) {
            stopTimer();
        }
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
            // Bank the elapsed time so the next run picks the phase up where this one left it.
            clockBaseSeconds = clockSeconds;
        }
    }

    private void finish() {
        Runnable finished = onAnimationFinished;
        stopAnimating();
        if (finished != null) {
            finished.run();
        }
        redraw();
    }

    /** @return how far the current animation has run: 0 at the start, 1 once settled */
    protected double progress() {
        return progress;
    }

    /** @return whether an animation is currently running */
    protected boolean isAnimating() {
        return animating;
    }

    /** @return steadily increasing seconds since this view started animating anything */
    protected double clockSeconds() {
        return clockSeconds;
    }

    /**
     * The frame the racer should show right now.
     *
     * <p>Cycling while he travels and <strong>frozen on a still frame the moment he stops</strong>.
     * The freeze is the point: a kart whose wheels spin while nothing is happening is just noise
     * on screen, and a stopped kart that has visibly stopped is information. It is the same
     * language the mini player uses, where the disk turning or not turning is the play indicator.
     *
     * @return the sheet frame index to draw
     */
    protected int racerFrame() {
        return isAnimating()
                ? RacerFrame.driving(clockSeconds())
                : RacerFrame.DRIVE_A.index();
    }

    /**
     * Eases a linear progress value so movement starts and stops gently.
     *
     * <p>A kart that snaps to constant speed and stops dead reads as a redraw. The same movement
     * eased reads as a kart.
     *
     * @param t linear progress in 0..1
     * @return eased progress in 0..1
     */
    protected static double ease(double t) {
        double clamped = Math.clamp(t, 0d, 1d);
        return clamped < 0.5
                ? 2 * clamped * clamped
                : 1 - Math.pow(-2 * clamped + 2, 2) / 2;
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    /** @return the palette every colour in this view resolves through */
    protected static Palette palette() {
        return Palette.active();
    }

    /**
     * Resolves a colour by the role it plays.
     *
     * @param role the role to resolve
     * @return the active palette's colour for that role
     */
    protected static Color color(PaletteRole role) {
        return Palette.active().color(role);
    }

    /**
     * Resolves a colour by role at reduced opacity.
     *
     * @param role    the role to resolve
     * @param opacity 0.0 transparent to 1.0 opaque
     * @return the role's colour at that opacity
     */
    protected static Color color(PaletteRole role, double opacity) {
        return Palette.active().color(role, opacity);
    }

    /**
     * Draws a hard-edged beveled block: light on the top and left, dark on the bottom and right,
     * the same convention the stylesheet uses for buttons.
     *
     * @param gc   the context to draw into
     * @param x    left edge
     * @param y    top edge
     * @param w    width
     * @param h    height
     * @param face role filling the block
     * @param edge role drawing its border
     */
    protected static void drawPanel(GraphicsContext gc, double x, double y, double w, double h,
                                    PaletteRole face, PaletteRole edge) {
        gc.setFill(color(face));
        gc.fillRect(x, y, w, h);
        gc.setStroke(color(edge));
        gc.setLineWidth(1);
        // Half-pixel offsets keep a one-pixel stroke on the pixel grid instead of straddling it.
        gc.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    }

    /**
     * Draws one frame of a sprite sheet at an integer scale.
     *
     * <p>The scale is an {@code int} by design. Hand-drawn 8-bit art scaled by 1.5 is mush, and
     * the mistake is invisible until someone looks at the window.
     *
     * @param gc     the context to draw into
     * @param sheet  the sheet to take a frame from
     * @param frame  which frame
     * @param x      left edge of the destination
     * @param y      top edge of the destination
     * @param scale  integer magnification, at least 1
     */
    protected static void drawSprite(GraphicsContext gc, SpriteSheet sheet, int frame,
                                     double x, double y, int scale) {
        drawSprite(gc, sheet, frame, x, y, scale, false);
    }

    /**
     * Draws one frame of a sprite sheet at an integer scale, optionally mirrored.
     *
     * <p>The racer art is drawn facing one way only, so travelling the other way means flipping
     * it. Mirroring costs nothing and is exact - it moves whole pixels - where a second set of
     * hand-drawn frames would have to be authored.
     *
     * @param gc      the context to draw into
     * @param sheet   the sheet to take a frame from
     * @param frame   which frame
     * @param x       left edge of the destination
     * @param y       top edge of the destination
     * @param scale   integer magnification, at least 1
     * @param flipped whether to mirror the frame horizontally
     */
    protected static void drawSprite(GraphicsContext gc, SpriteSheet sheet, int frame,
                                     double x, double y, int scale, boolean flipped) {
        int factor = Math.max(1, scale);
        Rectangle2D source = sheet.viewport(frame);
        Image image = sheet.image();
        double width = source.getWidth() * factor;
        double height = source.getHeight() * factor;
        double left = Math.round(x);
        double top = Math.round(y);

        if (!flipped) {
            gc.drawImage(image,
                    source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                    left, top, width, height);
            return;
        }
        // Mirror about the sprite's own centre, so the flipped frame occupies the same box.
        gc.save();
        gc.translate(left + width, top);
        gc.scale(-1, 1);
        gc.drawImage(image,
                source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                0, 0, width, height);
        gc.restore();
    }

    /**
     * Draws text in the 8-bit font.
     *
     * @param gc   the context to draw into
     * @param text the string to draw
     * @param x    left edge of the baseline
     * @param y    baseline
     * @param size point size, a whole number
     * @param role the colour role for the text
     */
    protected static void drawText(GraphicsContext gc, String text, double x, double y,
                                   double size, PaletteRole role) {
        gc.setFont(Fonts.pixel(size));
        gc.setFill(color(role));
        gc.fillText(text, Math.round(x), Math.round(y));
    }

    /**
     * Shortens a string to fit a pixel width, since the font is fixed at about one em per glyph.
     *
     * @param text  the string to fit
     * @param width available width in pixels
     * @param size  the point size it will be drawn at
     * @return the string, shortened with a trailing ellipsis if it did not fit
     */
    protected static String fit(String text, double width, double size) {
        if (text == null) {
            return "";
        }
        int characters = (int) Math.max(1, Math.floor(width / size));
        if (text.length() <= characters) {
            return text;
        }
        return characters <= 3 ? text.substring(0, characters) : text.substring(0, characters - 3) + "...";
    }

    /** @return the width one string occupies at the given size, in pixels */
    protected static double textWidth(String text, double size) {
        // Press Start 2P is fixed-width at roughly one em per glyph, so this needs no measuring
        // pass and stays correct when the fallback monospaced font is in use.
        return text == null ? 0 : text.length() * size;
    }

    /** @return the canvas, for subclasses that install their own input handlers */
    protected Canvas canvas() {
        return canvas;
    }

    /** @return the font used for canvas text at body size */
    protected static Font bodyFont() {
        return Fonts.pixel(TEXT_SIZE);
    }

    /**
     * Sizes the canvas to whatever room the panel gives it.
     *
     * <p>A {@code Canvas} does not resize itself with its parent, which is the usual surprise
     * when one is dropped into a layout.
     */
    private final class CanvasHolder extends Pane {
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
    }
}
