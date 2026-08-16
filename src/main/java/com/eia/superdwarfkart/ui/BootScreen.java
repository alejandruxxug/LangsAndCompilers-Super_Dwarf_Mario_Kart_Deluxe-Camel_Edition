package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen the application opens on: a dark room, a cartridge, and a slot to put it in.
 *
 * <p>The name is a ROM filename, the interface is 8-bit, and the companion window is literally a
 * game cartridge standing on a record. So the launch is the ritual the whole thing is dressed as
 * rather than a progress bar: the user drags the cartridge down, the machine coughs, and the
 * application comes up. It replaces a modal dialog that asked whether to start playing - inserting
 * the cartridge <em>is</em> that answer, and a second question straight afterwards is one too many.
 *
 * <p><strong>The application's name is on the cartridge, not on the screen.</strong> That is what a
 * cartridge is: the label is where the title goes, and printing it over the top as well would be
 * the one place in this application where the name appeared twice at once. It is measured onto the
 * artwork's own dark panel by {@link SpriteSheet#darkRegion}, wrapped on the separators it already
 * has, so replacement art with a differently-placed label needs no change here.
 *
 * <p>Four phases, in one timer:
 *
 * <ol>
 *   <li><strong>Insert</strong> - black, the cartridge, and the prompt. Nothing moves on its own.</li>
 *   <li><strong>Glitch</strong> - a flash and about half a second of tearing, which is what a
 *       console did when a cartridge went into a live slot. It is deliberately brief.</li>
 *   <li><strong>Loading</strong> - a bar, because a machine that has just been handed a cartridge
 *       has something to read off it.</li>
 *   <li><strong>Done</strong> - the window is handed over.</li>
 * </ol>
 *
 * <p><strong>Everything about the geometry and the glitch is a static pure function</strong> - see
 * {@link #seatTravel}, {@link #insertionProgress}, {@link #isInserted}, {@link #wrapName} and
 * {@link #tearOffset}. That is what makes any of it testable: there is no toolkit in the test run,
 * and neither a drag nor a glitch can be photographed - a still of either is a picture of something
 * standing still. Same reasoning as {@code MiniPlayerLayoutTest} and {@code RunnerProjectionTest}.
 */
public class BootScreen extends Pane {

    /** Where the boot has got to. */
    private enum Phase {
        /** Waiting for the user to put the cartridge in. */
        INSERT,
        /** The machine noticing. */
        GLITCH,
        /** The machine reading. */
        LOADING,
        /** Handed over. */
        DONE
    }

    /**
     * How far down the travel the cartridge has to be let go of for it to seat rather than spring
     * back.
     *
     * <p>Past halfway, because the gesture is deliberate and being made to repeat it reads as the
     * drag not having worked. Short of 1 for the same reason - insisting on the last pixel would
     * make the bottom of the slot a target rather than a direction.
     */
    public static final double INSERT_THRESHOLD = 0.6;

    /**
     * What share of the cartridge's own height disappears into the slot when it is seated.
     *
     * <p>A fraction of the artwork rather than a count of pixels, exactly as
     * {@code MiniPlayerView.CARTRIDGE_SEAT} is, so replacement art of a different size seats to the
     * same <em>place</em> rather than to the same number.
     */
    static final double SEAT_SHARE = 0.55;

    /** Travel used when the artwork could not be measured at all, so the drag still works. */
    static final double FALLBACK_TRAVEL = 120;

    /** How long the white flash at the moment of contact lasts, in seconds. */
    static final double FLASH_SECONDS = 0.14;

    /** How long the picture tears for, in seconds. */
    static final double GLITCH_SECONDS = 0.55;

    /** How long the loading bar runs for, in seconds. */
    static final double LOADING_SECONDS = 1.8;

    /** How many horizontal bands the picture is torn into. */
    static final int TEAR_BANDS = 22;

    /** How far a band can be thrown sideways, as a share of the screen's width. */
    static final double TEAR_AMPLITUDE = 0.16;

    /** Where the slot's mouth sits, as a fraction down the screen. */
    private static final double SLOT_Y_SHARE = 0.76;

    /** How tall the cartridge is drawn, as a fraction of the screen's height. */
    private static final double CARTRIDGE_HEIGHT_SHARE = 0.52;

    /** How far the cartridge hovers above the mouth before it is touched, in pixels. */
    private static final double HOVER_GAP = 24;

    /** How tall the mouth of the slot is, in pixels. */
    private static final double MOUTH_HEIGHT = 22;

    /** Size of the INSERT CARTRIDGE prompt, in pixels. */
    private static final double PROMPT_SIZE = 14;

    /** Size of the line explaining the gesture, in pixels. */
    private static final double HINT_SIZE = 8;

    /** Largest size the name is printed on the label at, in pixels. */
    private static final double LABEL_SIZE_MAX = 13;

    /** Smallest size the name is printed on the label at, in pixels. */
    private static final double LABEL_SIZE_MIN = 5;

    /** Width of the drawn rims, in pixels. */
    private static final double RIM = 3;

    /** How long the cartridge takes to seat itself, or to spring back. */
    private static final Duration SETTLE = Duration.millis(200);

    /**
     * How much the cartridge is darkened, from 0 for the artwork as drawn to -1 for black.
     *
     * <p>Far lighter than the companion window's, and for the opposite reason: there the cartridge
     * is background for a song's details, here it is the only thing on the screen.
     *
     * <p>A negative brightness on {@link ColorAdjust} is a <em>multiplier</em> rather than a
     * subtraction - see {@code MiniPlayerView.CARTRIDGE_SHADE}, where that was measured.
     */
    private static final double CARTRIDGE_SHADE = -0.10;

    private final SpriteSheet sheet;
    private final ImageView cartridge = new ImageView();

    /** Behind the cartridge: the dark inside of the slot. */
    private final Canvas back = new Canvas();

    /** In front of the cartridge: the name printed on its label. */
    private final Canvas plate = new Canvas();

    /**
     * In front of everything: the slot's near lip during the drag, and the whole picture during the
     * glitch and the loading bar.
     *
     * <p>One canvas rather than three, which is what keeps the phases from having to hide each
     * other's nodes: once this fills the screen there is nothing else to think about.
     */
    private final Canvas front = new Canvas();

    /** How far in the cartridge is, from 0 for untouched to 1 for seated. */
    private final DoubleProperty insertion = new SimpleDoubleProperty(0);

    private Runnable onInserted;

    /** Run when the loading bar starts, so the caller can begin whatever the bar is standing for. */
    private Runnable onLoading;

    /**
     * The line under the bar, naming what is being loaded.
     *
     * <p>Volatile because it is set from whatever thread finished the work being reported - the
     * Spotify session answers on its own worker and marshals to the interface thread, but a caption
     * is not worth depending on that.
     */
    private volatile String status = "";

    private Timeline seatAnimation;
    private AnimationTimer sequence;
    private Phase phase = Phase.INSERT;

    /** When the glitch began, in seconds on the wall clock. */
    private double phaseStarted;

    /** How far through the glitch and the loading bar the sequence is, 0 to 1 each. */
    private double glitchProgress;
    private double loadingProgress;

    /** How far the cartridge has to travel to seat, in pixels. Recomputed on every layout pass. */
    private double travel = FALLBACK_TRAVEL;

    /**
     * Where the pointer was when the drag began, in screen coordinates.
     *
     * <p>Screen rather than scene, for the same reason {@code PixelDialog.dragBy} uses them: they
     * are the one frame of reference nothing in the scene graph can move underneath the gesture -
     * and the cartridge is moving while it is being dragged.
     */
    private double pressScreenY;

    private double pressInsertion;

    /**
     * Builds the boot screen.
     *
     * @param assets where the cartridge artwork comes from; must not be {@code null}
     */
    public BootScreen(AssetRegistry assets) {
        this.sheet = assets.sheet(AssetKind.CARTRIDGE);

        buildCartridge();

        // A Canvas is picked on its whole rectangle whatever it has drawn in it, and all three of
        // these span the window. Left pickable, the ones in front would silently swallow every press
        // meant for the cartridge - the fault that ate the companion window's transport clicks,
        // which nothing throws and no screenshot shows.
        back.setMouseTransparent(true);
        plate.setMouseTransparent(true);
        front.setMouseTransparent(true);

        getChildren().addAll(back, cartridge, plate, front);

        insertion.addListener((observable, was, now) -> requestLayout());
    }

    /**
     * What to run once the machine has finished starting up.
     *
     * @param action the callback; may be {@code null}
     */
    public void setOnInserted(Runnable action) {
        this.onInserted = action;
    }

    /**
     * Sets what to start when the loading bar starts.
     *
     * <p>The bar used to stand for nothing - the library is already loaded by the time it runs, and
     * it was honest about being a beat rather than a measurement. It now also fires the one thing
     * that genuinely does take a moment and that the user would otherwise have to ask for by hand,
     * which is the daemon coming up. <strong>The bar does not wait for it.</strong> Booting must
     * never depend on a subprocess, a network or a login (ground rule 5); what the caption reports
     * is how far that got by the time the machine had finished reading the cartridge.
     *
     * @param action run once, as the bar begins; {@code null} to do nothing
     */
    public void setOnLoading(Runnable action) {
        this.onLoading = action;
    }

    /**
     * Sets the line printed under the bar.
     *
     * @param text what is being loaded, or {@code null} for nothing; shown as typed, so it should
     *             already be short enough for the screen
     */
    public void setStatus(String text) {
        this.status = text == null ? "" : text;
    }

    // ------------------------------------------------------------------
    // Geometry and effects - pure, static, and the only part a test can reach
    // ------------------------------------------------------------------

    /**
     * How far the cartridge travels between hovering and seated.
     *
     * @param cartridgeHeight the drawn height of the cartridge, in pixels
     * @return the travel in pixels; never zero, so the drag works even with no artwork at all
     */
    static double seatTravel(double cartridgeHeight) {
        if (!(cartridgeHeight > 0)) {
            return FALLBACK_TRAVEL;
        }
        return HOVER_GAP + cartridgeHeight * SEAT_SHARE;
    }

    /**
     * Turns pointer travel into how far in the cartridge is.
     *
     * @param draggedPixels how far the pointer has moved down from where it was pressed
     * @param travel        the full travel, from {@link #seatTravel}
     * @return a fraction from 0 to 1
     */
    static double insertionProgress(double draggedPixels, double travel) {
        if (!(travel > 0)) {
            return 0;
        }
        return Math.clamp(draggedPixels / travel, 0, 1);
    }

    /**
     * @param progress how far in the cartridge is
     * @return whether letting go here seats it rather than springing it back
     */
    static boolean isInserted(double progress) {
        return progress >= INSERT_THRESHOLD;
    }

    /**
     * How far sideways one band of the picture is thrown at one instant of the glitch.
     *
     * <p><strong>Seeded rather than random</strong>, from the band and from the frame, so the same
     * boot tears the same way twice. That is the same decision the course generator makes and for a
     * related reason: an effect nobody can reproduce is an effect nobody can check, and the smoke
     * test drives this with no pulses at all.
     *
     * <p>The envelope is what makes it read as a machine settling rather than as a fault: the
     * displacement decays across the glitch, and bands towards the middle of the screen are thrown
     * furthest, so the tear collapses inwards and stops.
     *
     * @param band      which horizontal band, from 0
     * @param frame     which frame of the glitch, from 0 - the second seed
     * @param progress  how far through the glitch, 0 to 1
     * @param width     the screen's width, in pixels
     * @return the offset in pixels, positive or negative
     */
    static double tearOffset(int band, int frame, double progress, double width) {
        if (progress >= 1 || progress < 0) {
            return 0;
        }
        // The SplitMix64 finalizer over the two seeds. Written out here rather than taken from
        // Random for the same reason Course defines its own hash: the effect has to come out
        // identical on every run and on every future runtime, and a library generator is free to
        // change. FNV-1a was tried first and was measurably wrong for this - it avalanches poorly in
        // its high bits over two-word inputs, and every band cleared the threshold below, which is
        // static rather than tearing.
        long z = band * 0x9E3779B97F4A7C15L + frame * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        double unit = ((z >>> 11) / (double) (1L << 53)) * 2 - 1;

        // Only some bands move at all. A picture where every row is displaced reads as noise; one
        // where a few rows are thrown a long way reads as a signal breaking up.
        if (Math.abs(unit) < 0.45) {
            return 0;
        }
        double decay = (1 - progress) * (1 - progress);
        return unit * TEAR_AMPLITUDE * width * decay;
    }

    /**
     * Breaks the application's name into lines that fit the cartridge's label.
     *
     * <p>Broken <strong>after its own separators</strong> - the underscores and the hyphen - which
     * is why the name reads as a ROM filename on a label rather than as a sentence chopped in the
     * middle. A run with no separator in it at all is cut to length rather than allowed to overhang,
     * because a label is a fixed width and there is nothing else to do with it.
     *
     * @param name       the name to break up
     * @param perLine    how many characters fit on one line
     * @return the lines, in order; never empty
     */
    static List<String> wrapName(String name, int perLine) {
        List<String> lines = new ArrayList<>();
        if (name == null || name.isEmpty() || perLine <= 0) {
            return List.of(name == null ? "" : name);
        }

        StringBuilder line = new StringBuilder();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            word.append(c);
            // The separator belongs to the end of the run it follows, the way it does in the name.
            boolean breakable = c == '_' || c == '-';
            if (!breakable && i < name.length() - 1) {
                continue;
            }
            if (line.length() + word.length() > perLine && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // A single run longer than the label has nowhere to go but across several lines.
            while (word.length() > perLine) {
                lines.add(word.substring(0, perLine));
                word.delete(0, perLine);
            }
            line.append(word);
            word.setLength(0);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of(name) : lines;
    }

    /**
     * Picks the size the name is printed at so that it fills its label without overflowing it.
     *
     * <p>The font advances one em per glyph, so a line of n characters is about n x the size across
     * and a block of m lines is about m x the size down. Both have to fit, and the label is much
     * taller than it is wide, so it is nearly always the width that decides.
     *
     * @param labelWidth  the label's width, in pixels
     * @param labelHeight the label's height, in pixels
     * @param name        the name to print
     * @return the size, in whole pixels
     */
    static double labelFontSize(double labelWidth, double labelHeight, String name) {
        if (!(labelWidth > 0) || !(labelHeight > 0) || name == null || name.isEmpty()) {
            return LABEL_SIZE_MIN;
        }
        for (double size = LABEL_SIZE_MAX; size > LABEL_SIZE_MIN; size -= 1) {
            int perLine = (int) Math.floor(labelWidth / size);
            if (perLine < 1) {
                continue;
            }
            List<String> lines = wrapName(name, perLine);
            if (lines.size() * size * 1.4 <= labelHeight) {
                return size;
            }
        }
        return LABEL_SIZE_MIN;
    }

    // ------------------------------------------------------------------
    // State a caller can ask about
    // ------------------------------------------------------------------

    /** @return how far in the cartridge is, from 0 to 1 */
    public double insertion() {
        return insertion.get();
    }

    /** @return how far the cartridge has to travel to seat, in pixels */
    public double travelPixels() {
        return travel;
    }

    /** @return whether the cartridge has seated, whatever the machine is still doing about it */
    public boolean isSeated() {
        return phase != Phase.INSERT;
    }

    /** @return whether the whole sequence has run and the window has been handed over */
    public boolean isBooted() {
        return phase == Phase.DONE;
    }

    /** @return the node the cartridge is drawn as, which is what a drag has to be aimed at */
    public Node cartridgeHandle() {
        return cartridge;
    }

    /** @return the artwork this screen is built on, for reporting what was found */
    public SpriteSheet cartridgeSheet() {
        return sheet;
    }

    /**
     * Runs the whole sequence to its end at once, with no animation.
     *
     * <p><strong>Required rather than a convenience.</strong> The smoke test runs synchronously
     * inside {@code start()}, so no pulse ever arrives while it is running: the seat timeline would
     * never advance and neither would the glitch or the loading bar, and the screen would sit at
     * whatever fraction the last synthesised drag left it at, forever. Same reason
     * {@code StructureView.settle()} exists for the screenshots.
     */
    public void settle() {
        stopSeatAnimation();
        stopSequence();
        insertion.set(1);
        applyCss();
        layout();
        phase = Phase.DONE;
        handOver();
    }

    /**
     * Puts the sequence at a chosen moment and draws it, without a running clock.
     *
     * <p>The glitch is unphotographable for the same reason a spinning disk is: the frame loop
     * cannot run while the smoke test holds the interface thread, so the only way to get a picture
     * of it is to ask for one at a stated instant. Mirrors {@code MiniPlayerView.previewAt}.
     *
     * @param glitch how far through the tearing, 0 to 1
     * @param loading how far through the loading bar, 0 to 1
     */
    public void previewAt(double glitch, double loading) {
        this.phase = loading > 0 ? Phase.LOADING : Phase.GLITCH;
        this.glitchProgress = Math.clamp(glitch, 0, 1);
        this.loadingProgress = Math.clamp(loading, 0, 1);
        insertion.set(1);
        // Asked for explicitly, because a Parent only lays out when something marked it dirty and
        // none of the fields above are observable. Without this a second preview silently redraws
        // the first one - which looks like the phase never changed rather than like the canvas was
        // never asked to.
        requestLayout();
        applyCss();
        layout();
    }

    // ------------------------------------------------------------------
    // The gesture
    // ------------------------------------------------------------------

    /**
     * Wires the drag.
     *
     * <p>Vertical only. Sideways movement is ignored, because the cartridge has one place it can go
     * and letting it wander off the slot would only ever be a way to get the gesture wrong.
     *
     * <p>Not built on {@code PixelDialog.dragBy} and not a second copy of it: that moves a window to
     * follow the pointer exactly, where this is axis-constrained, clamped at both ends, and has a
     * threshold and a spring-back. They share only the idea of taking the offset on press.
     */
    private void installDrag() {
        cartridge.setOnMousePressed(event -> {
            if (phase != Phase.INSERT) {
                return;
            }
            stopSeatAnimation();
            pressScreenY = event.getScreenY();
            pressInsertion = insertion.get();
            event.consume();
        });

        cartridge.setOnMouseDragged(event -> {
            if (phase != Phase.INSERT) {
                return;
            }
            double dragged = (event.getScreenY() - pressScreenY) + pressInsertion * travel;
            insertion.set(insertionProgress(dragged, travel));
            event.consume();
        });

        cartridge.setOnMouseReleased(event -> {
            if (phase != Phase.INSERT) {
                return;
            }
            if (isInserted(insertion.get())) {
                seatTo(1, this::startSequence);
            } else {
                seatTo(0, null);
            }
            event.consume();
        });
    }

    /**
     * Runs the cartridge to a resting value.
     *
     * @param target   where to end up, 0 or 1
     * @param finished what to run on arrival; may be {@code null}
     */
    private void seatTo(double target, Runnable finished) {
        stopSeatAnimation();
        seatAnimation = new Timeline(new KeyFrame(SETTLE,
                new KeyValue(insertion, target, Interpolator.EASE_BOTH)));
        if (finished != null) {
            seatAnimation.setOnFinished(event -> finished.run());
        }
        seatAnimation.play();
    }

    private void stopSeatAnimation() {
        if (seatAnimation != null) {
            seatAnimation.stop();
            seatAnimation = null;
        }
    }

    private void stopSequence() {
        if (sequence != null) {
            sequence.stop();
            sequence = null;
        }
    }

    // ------------------------------------------------------------------
    // The start-up sequence
    // ------------------------------------------------------------------

    /**
     * Starts the glitch, then the loading bar, then hands the window over.
     *
     * <p>One timer for both phases rather than a chain of transitions, so there is a single place
     * that knows how far through the sequence the machine is and a single place that can be stopped.
     * It runs on wall time: nothing here is synchronised to audio, because nothing is playing yet.
     */
    private void startSequence() {
        if (phase != Phase.INSERT) {
            return;
        }
        phase = Phase.GLITCH;
        phaseStarted = now();
        stopSequence();
        sequence = new AnimationTimer() {
            @Override
            public void handle(long nanos) {
                advance(now());
            }
        };
        sequence.start();
    }

    /**
     * Moves the sequence on to wherever the clock has got to.
     *
     * @param seconds the wall clock, in seconds
     */
    private void advance(double seconds) {
        double elapsed = seconds - phaseStarted;
        if (phase == Phase.GLITCH) {
            glitchProgress = Math.clamp(elapsed / GLITCH_SECONDS, 0, 1);
            if (glitchProgress >= 1) {
                phase = Phase.LOADING;
                phaseStarted = seconds;
                loadingProgress = 0;
                // The one moment anything real is started. Fired here rather than on insertion so
                // the work begins as the bar does, and the caption has something true to say.
                Runnable started = onLoading;
                if (started != null) {
                    started.run();
                }
            }
        } else if (phase == Phase.LOADING) {
            loadingProgress = Math.clamp(elapsed / LOADING_SECONDS, 0, 1);
            if (loadingProgress >= 1) {
                stopSequence();
                phase = Phase.DONE;
                handOver();
                return;
            }
        }
        requestLayout();
    }

    /**
     * Lets the application through, exactly once.
     *
     * <p>There is no way back out of the slot. A boot that can be un-booted is a state nobody asked
     * for and every view behind this one would have to know about.
     */
    private void handOver() {
        Runnable action = onInserted;
        onInserted = null;
        if (action != null) {
            action.run();
        }
    }

    /** @return the wall clock in seconds; the sequence is not synchronised to anything */
    private static double now() {
        return System.nanoTime() / 1_000_000_000d;
    }

    // ------------------------------------------------------------------
    // Layout and drawing
    // ------------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        for (Canvas canvas : new Canvas[] {back, plate, front}) {
            canvas.setWidth(width);
            canvas.setHeight(height);
        }

        double mouthY = Math.round(height * SLOT_Y_SHARE);
        double cartridgeHeight = Math.round(height * CARTRIDGE_HEIGHT_SHARE);
        double cartridgeWidth = Math.round(cartridgeHeight * aspect());
        travel = seatTravel(cartridgeHeight);

        // An ImageView is not a resizable node: resizeRelocate would position it and leave it at the
        // artwork's own 500 pixels.
        cartridge.setFitWidth(cartridgeWidth);
        cartridge.setFitHeight(cartridgeHeight);
        double cartridgeX = Math.round((width - cartridgeWidth) / 2);
        double cartridgeY = Math.round(mouthY - HOVER_GAP - cartridgeHeight
                + insertion.get() * travel);
        cartridge.relocate(cartridgeX, cartridgeY);

        double mouthWidth = Math.round(cartridgeWidth * inletShare());
        drawBack(width, height, mouthY, mouthWidth);
        drawPlate(width, height, cartridgeX, cartridgeY, cartridgeWidth, cartridgeHeight);
        drawFront(width, height, mouthY, mouthWidth, cartridgeX, cartridgeY,
                cartridgeWidth, cartridgeHeight);
    }

    /**
     * Draws the screen's ground and the dark inside of the slot.
     *
     * <p>The ground is {@code SHADOW} - the darkest role there is, which is what a screen with
     * nothing on it should be. It follows the mood like everything else, so a light palette gets a
     * light room rather than a black rectangle somebody forgot to theme.
     */
    private void drawBack(double width, double height, double mouthY, double mouthWidth) {
        Palette palette = Palette.active();
        GraphicsContext gc = back.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, 0, width, height);

        // The hole itself, a shade darker than the room so it reads as a recess even on black.
        double mouthX = Math.round((width - mouthWidth) / 2);
        gc.setFill(palette.shaded(PaletteRole.SHADOW, 0.5));
        gc.fillRect(mouthX, mouthY, mouthWidth, MOUTH_HEIGHT);
    }

    /**
     * Prints the application's name on the cartridge's own label.
     *
     * <p>Placed by measuring the artwork rather than by four numbers copied out of an image editor:
     * {@link SpriteSheet#darkRegion} finds the dark panel and refuses when what it finds is not
     * panel-shaped, which is exactly what stops the name being laid over a magenta placeholder.
     */
    private void drawPlate(double width, double height, double cartridgeX, double cartridgeY,
            double cartridgeWidth, double cartridgeHeight) {
        GraphicsContext gc = plate.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.clearRect(0, 0, width, height);

        Rectangle2D label = sheet.isPlaceholder() ? null : sheet.darkRegion(0).orElse(null);
        if (label == null) {
            return;
        }
        double scaleX = cartridgeWidth / sheet.frameWidth();
        double scaleY = cartridgeHeight / sheet.frameHeight();
        double x = cartridgeX + label.getMinX() * scaleX;
        double y = cartridgeY + label.getMinY() * scaleY;
        double labelWidth = label.getWidth() * scaleX;
        double labelHeight = label.getHeight() * scaleY;

        double inset = Math.max(4, labelWidth * 0.06);
        double textWidth = labelWidth - 2 * inset;
        double size = labelFontSize(textWidth, labelHeight - 2 * inset, AppConfig.APP_NAME);
        List<String> lines = wrapName(AppConfig.APP_NAME, (int) Math.floor(textWidth / size));

        Palette palette = Palette.active();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Fonts.pixel(size));
        gc.setFill(palette.color(PaletteRole.PRIMARY));

        double lineHeight = size * 1.4;
        double blockHeight = lines.size() * lineHeight;
        double centreX = x + labelWidth / 2;
        // Centred in the label rather than sitting at the top of it: the label is much taller than
        // the name needs, and text piled against the top edge reads as a panel somebody forgot to
        // finish filling.
        double first = y + (labelHeight - blockHeight) / 2 + size;
        for (int i = 0; i < lines.size(); i++) {
            gc.fillText(lines.get(i), Math.round(centreX), Math.round(first + i * lineHeight));
        }

        gc.setFont(Fonts.pixel(Math.max(LABEL_SIZE_MIN, size - 3)));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM));
        gc.fillText("v" + AppConfig.APP_VERSION, Math.round(centreX),
                Math.round(y + labelHeight - inset));
    }

    /**
     * Draws whatever is in front: the slot's lip while the cartridge is going in, and the whole
     * picture once it is.
     */
    private void drawFront(double width, double height, double mouthY, double mouthWidth,
            double cartridgeX, double cartridgeY, double cartridgeWidth, double cartridgeHeight) {
        GraphicsContext gc = front.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.clearRect(0, 0, width, height);

        if (phase == Phase.GLITCH) {
            drawGlitch(gc, width, height, cartridgeX, cartridgeY, cartridgeWidth, cartridgeHeight);
            return;
        }
        if (phase == Phase.LOADING) {
            drawLoading(gc, width, height);
            return;
        }
        if (phase == Phase.DONE) {
            return;
        }
        drawSlot(gc, width, height, mouthY, mouthWidth);
    }

    /** The slot's near lip, which is what swallows the cartridge's foot as it descends. */
    private void drawSlot(GraphicsContext gc, double width, double height,
            double mouthY, double mouthWidth) {
        Palette palette = Palette.active();
        double mouthX = Math.round((width - mouthWidth) / 2);
        double lipY = mouthY + MOUTH_HEIGHT;

        // Everything below the mouth is the room again, drawn in front so the cartridge disappears
        // into it. On a dark screen this is invisible as a shape, which is the whole effect.
        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, mouthY, mouthX, height - mouthY);
        gc.fillRect(mouthX + mouthWidth, mouthY, width - mouthX - mouthWidth, height - mouthY);
        gc.fillRect(0, lipY, width, height - lipY);

        // The rim warms towards HIGHLIGHT as the cartridge goes in, so the screen answers the drag
        // continuously rather than flashing once at the end - a flash is over before the eye that is
        // watching the cartridge gets to it.
        double lit = Math.clamp(insertion.get(), 0, 1);
        gc.setStroke(palette.color(PaletteRole.OUTLINE)
                .interpolate(palette.color(PaletteRole.HIGHLIGHT), lit));
        gc.setLineWidth(RIM);
        gc.strokeRect(mouthX - RIM / 2, mouthY - RIM / 2, mouthWidth + RIM, MOUTH_HEIGHT + RIM);

        drawPrompt(gc, width, height, lit);
    }

    /** The instruction, which fades out as soon as the gesture has been started. */
    private void drawPrompt(GraphicsContext gc, double width, double height, double lit) {
        Palette palette = Palette.active();
        gc.setTextAlign(TextAlignment.CENTER);
        double centreX = Math.round(width / 2);

        gc.setFill(palette.color(PaletteRole.PRIMARY, Math.clamp(1 - lit * 1.6, 0, 1)));
        gc.setFont(Fonts.pixel(PROMPT_SIZE));
        gc.fillText("INSERT CARTRIDGE", centreX, Math.round(height * 0.10));

        gc.setFill(palette.color(PaletteRole.TEXT_DIM, Math.clamp(1 - lit * 2.4, 0, 1)));
        gc.setFont(Fonts.pixel(HINT_SIZE));
        gc.fillText("DRAG IT DOWN INTO THE SLOT", centreX, Math.round(height * 0.10 + 28));
    }

    /**
     * The moment of contact: a flash, then the picture tearing itself apart and settling.
     *
     * <p>What a console did when a cartridge went into a live slot. It is over in half a second on
     * purpose - it has to read as the machine noticing rather than as the machine being broken, and
     * a full-screen effect that outstays that is the sort of thing the "reduce motion" switch exists
     * for.
     */
    private void drawGlitch(GraphicsContext gc, double width, double height,
            double cartridgeX, double cartridgeY, double cartridgeWidth, double cartridgeHeight) {
        Palette palette = Palette.active();

        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, 0, width, height);

        // The picture, torn into bands and thrown sideways. Drawn from the artwork rather than from
        // a snapshot of the scene, which cannot be taken while the interface thread is busy.
        Rectangle2D frame = sheet.viewport(0);
        int frameIndex = (int) Math.round(glitchProgress * GLITCH_SECONDS * 60);
        double bandHeight = cartridgeHeight / TEAR_BANDS;
        double sourceBand = frame.getHeight() / TEAR_BANDS;
        for (int band = 0; band < TEAR_BANDS; band++) {
            double offset = tearOffset(band, frameIndex, glitchProgress, width);
            gc.drawImage(sheet.image(),
                    frame.getMinX(), frame.getMinY() + band * sourceBand,
                    frame.getWidth(), sourceBand,
                    Math.round(cartridgeX + offset), Math.round(cartridgeY + band * bandHeight),
                    Math.round(cartridgeWidth), Math.ceil(bandHeight));
        }

        // A few rows of the signal breaking up entirely. ACCENT and NEGATIVE because they are the
        // two roles furthest from the room's own colour, so the bands read as interference whatever
        // the mood is - and never as part of the artwork.
        for (int band = 0; band < TEAR_BANDS; band++) {
            double offset = tearOffset(band, frameIndex + 977, glitchProgress, width);
            if (offset == 0) {
                continue;
            }
            PaletteRole role = (band % 2 == 0) ? PaletteRole.ACCENT : PaletteRole.NEGATIVE;
            gc.setFill(palette.color(role, 0.35 * (1 - glitchProgress)));
            double y = Math.round(height * band / (double) TEAR_BANDS);
            gc.fillRect(Math.round(offset * 2), y, width, Math.ceil(height / TEAR_BANDS / 2));
        }

        // The flash itself, at the very start and gone almost immediately. TEXT_PRIMARY rather than
        // a literal white: it is the palette's brightest role by definition, and it is protected, so
        // no mood can turn this into a flash nobody sees.
        double flash = 1 - Math.clamp(glitchProgress * GLITCH_SECONDS / FLASH_SECONDS, 0, 1);
        if (flash > 0) {
            gc.setFill(palette.color(PaletteRole.TEXT_PRIMARY, flash * flash));
            gc.fillRect(0, 0, width, height);
        }
    }

    /** The machine reading the cartridge: a caption and a bar. */
    private void drawLoading(GraphicsContext gc, double width, double height) {
        Palette palette = Palette.active();

        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, 0, width, height);

        double barWidth = Math.round(Math.min(width * 0.42, 520));
        double barHeight = 22;
        double barX = Math.round((width - barWidth) / 2);
        double barY = Math.round(height * 0.54);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Fonts.pixel(PROMPT_SIZE));
        gc.setFill(palette.color(PaletteRole.PRIMARY));
        gc.fillText("LOADING", Math.round(width / 2), Math.round(barY - 30));

        gc.setFill(palette.shaded(PaletteRole.SHADOW, 0.5));
        gc.fillRect(barX, barY, barWidth, barHeight);

        // Filled in whole blocks rather than as a smooth sweep, which is what makes it read as 8-bit
        // rather than as a web page. The bar is still a beat rather than a measurement - the library
        // was loaded long before it ran - but it is no longer standing for nothing: the caption
        // beneath it reports the daemon actually coming up, which is the one thing at startup that
        // genuinely takes a moment.
        double block = 10;
        int blocks = (int) Math.floor(barWidth / block);
        int lit = (int) Math.round(blocks * loadingProgress);
        gc.setFill(palette.color(PaletteRole.PRIMARY));
        for (int i = 0; i < lit; i++) {
            gc.fillRect(barX + i * block + 1, barY + 3, block - 2, barHeight - 6);
        }

        gc.setStroke(palette.color(PaletteRole.OUTLINE));
        gc.setLineWidth(RIM);
        gc.strokeRect(barX - RIM / 2, barY - RIM / 2, barWidth + RIM, barHeight + RIM);

        // What is actually being loaded, under the bar. ACCENT rather than TEXT_DIM: this is the
        // only line on the screen that changes while the bar runs, and a reader has about a second
        // and a half to notice it.
        String reporting = status;
        if (!reporting.isEmpty()) {
            gc.setFont(Fonts.pixel(HINT_SIZE));
            gc.setFill(palette.color(PaletteRole.ACCENT));
            gc.fillText(reporting, Math.round(width / 2), Math.round(barY + barHeight + 24));
        }

        gc.setFont(Fonts.pixel(HINT_SIZE));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM));
        gc.fillText(AppConfig.APP_NAME_SHORT, Math.round(width / 2),
                Math.round(barY + barHeight + 48));
    }

    /** @return the cartridge's width over its height, or a sane shape when there is no artwork */
    private double aspect() {
        double frameHeight = sheet.frameHeight();
        return frameHeight > 0 ? sheet.frameWidth() / frameHeight : 0.87;
    }

    /**
     * How wide the mouth is against the cartridge.
     *
     * <p>Measured off the artwork rather than written down: the cartridge steps in towards its foot,
     * and a mouth matched to its <em>widest</em> part would be visibly wider than the part that
     * actually goes into it. {@link SpriteSheet#footprint} reads the lowest row with anything in it,
     * which is literally where it touches down.
     */
    private double inletShare() {
        return sheet.footprint(0)
                .map(foot -> foot.getWidth() / sheet.frameWidth())
                .orElse(1d);
    }

    /**
     * Sets the cartridge up.
     *
     * <p>Scaled <em>smoothly</em>, which is not the exception ground rule 8 forbids: the cartridge is
     * a 500x575 illustration with 1830 colours and per-pixel grain, in the same category as album
     * art. The magenta stand-in, on the other hand, is 32 pixels of flat blocks and is drawn hard.
     */
    private void buildCartridge() {
        cartridge.setImage(sheet.image());
        cartridge.setViewport(sheet.viewport(0));
        cartridge.setPreserveRatio(false);
        cartridge.setSmooth(!sheet.isPlaceholder());
        cartridge.setPickOnBounds(false);

        // Rasterised once rather than recomposited per frame: the image never changes, and the
        // per-frame cost is the whole of what the warning against node effects is about.
        cartridge.setEffect(new ColorAdjust(0, 0, CARTRIDGE_SHADE, 0));
        cartridge.setCache(true);
        cartridge.setCacheHint(CacheHint.SPEED);
        cartridge.setCursor(Cursor.OPEN_HAND);

        installDrag();
    }
}
