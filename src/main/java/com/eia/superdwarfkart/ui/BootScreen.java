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
import javafx.scene.paint.Color;
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
 * <p><strong>The application's name is on the cartridge while the cartridge is on screen, and on the
 * screen once it is not.</strong> That is what a cartridge is: the label is where the title goes, so
 * during the drag it is measured onto the artwork's own dark panel by {@link SpriteSheet#darkRegion},
 * wrapped on the separators the name already has, and replacement art with a differently-placed
 * label needs no change here. Once the cartridge is <em>in</em> the machine there is no label left to
 * read, and the loading screen prints the name across itself as a splash instead - which is where a
 * console has always put a title. The two can never appear at once, which was the whole reason the
 * splash did not exist before.
 *
 * <p><strong>The colours do not come from the active mood.</strong> They come from
 * {@link Palette#hardware()} - black ground, white light, nothing else - because at this point the
 * system has not started: a mood is something the software chose, and a boot screen in somebody's
 * colour scheme is the console admitting it was running all along. The flash at the moment of contact
 * is white for the same reason, rather than nearly white in whichever direction the palette leans.
 *
 * <p>Four phases, in one timer:
 *
 * <ol>
 *   <li><strong>Insert</strong> - black, the cartridge, and the prompt. Nothing moves on its own.</li>
 *   <li><strong>Glitch</strong> - a white flash and about half a second of tearing, which is what a
 *       console did when a cartridge went into a live slot. It is deliberately brief.</li>
 *   <li><strong>Loading</strong> - the title and a bar, because a machine that has just been handed a
 *       cartridge has something to read off it.</li>
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
        /** The machine noticing: the flash and the tear. */
        GLITCH,
        /**
         * The machine introducing itself, for as long as the fanfare runs.
         *
         * <p>One phase rather than five, with a single normalised progress through it and every
         * element's opacity, size and position a pure function of that number - see
         * {@link #presentsAlpha}, {@link #titleAlpha}, {@link #titleScale}, {@link #seamWidth},
         * {@link #rayAlpha}, {@link #barAlpha}, {@link #barProgress} and {@link #blackout}. The
         * alternative is a chain of phases each with its own clock, which is five places for the
         * sequence to get out of step with itself and nowhere to ask "what does it look like at
         * eleven seconds".
         */
        SHOW,
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

    /**
     * How long the whole start-up sequence runs for, in seconds, when nothing tells it otherwise.
     *
     * <p><strong>The number the fanfare actually measures, not a number somebody liked.</strong>
     * {@code App} calls {@link #setSequenceSeconds} with the decoded length of
     * {@code AppConfig.SOUND_BOOT}, so the animation is exactly as long as the sound it is set to and
     * stays that way if the sound is replaced. This constant is what it falls back to when the audio is
     * missing or will not decode - the sequence is the feature and the sound accompanies it, so it runs
     * to the same length either way rather than becoming a different, shorter thing in silence.
     */
    static final double SEQUENCE_SECONDS = 15.0;

    /**
     * The shortest sequence that still has room for all of it.
     *
     * <p>Every stage below is a <em>fraction</em> of the show, so a very short sound would compress the
     * publisher line, the swell, the hold and the bar into something that flickers past - and at that
     * point the fades stop reading as fades. Clamped rather than scaled indefinitely.
     */
    static final double MIN_SEQUENCE_SECONDS = 4.0;

    // ------------------------------------------------------------------
    // Where each movement of the sequence sits, as a fraction of the show
    // ------------------------------------------------------------------
    //
    // Fractions rather than seconds, so the whole thing follows the length of the fanfare instead of
    // being cut short by it or leaving it playing over a finished screen. The numbers come off the
    // audio's own measured envelope (quarter-second RMS blocks over the decoded fanfare): a quiet
    // opening chime to 2.75s, the big hit at 3.00-4.25s, a sustained passage to 9.0s, a decay to
    // 14.0s, then silence. Divided by the 14.45s show that follows the tear, those land where the
    // constants below put them - which is why the title arrives *on* the swell rather than near it.

    /** When the publisher line starts to appear. */
    static final double PRESENTS_IN = 0.02;

    /** When it is fully up. */
    static final double PRESENTS_FULL = 0.05;

    /** When it starts to leave. */
    static final double PRESENTS_OUT = 0.13;

    /** When it is gone - before the title starts, so the two are never on screen together. */
    static final double PRESENTS_GONE = 0.165;

    /** When the title starts to fade up. This is where the fanfare's big hit lands. */
    static final double TITLE_IN = 0.17;

    /** When the title is fully up and has settled to its own size. */
    static final double TITLE_FULL = 0.30;

    /** When the loading bar fades in under it, as the fanfare begins to decay. */
    static final double LOADING_IN = 0.58;

    /** When everything begins to fade to black, as the fanfare dies. */
    static final double FADE_OUT = 0.93;

    /** How much larger than its resting size the title arrives at, so it settles rather than appears. */
    static final double TITLE_OVERSHOOT = 0.14;

    /** How many rays sweep out of the centre as the title lands. */
    static final int RAYS = 16;

    /** How far through the show the rays have finished. */
    static final double RAYS_DONE = 0.36;

    /**
     * How many times a second the held title breathes.
     *
     * <p><strong>Well under the 3 Hz cap</strong> §8b puts on anything full-screen and rhythmic, and
     * deliberately not near it: this is a slow swell to keep a five-second hold from reading as a frozen
     * frame, not a pulse. Nothing else in the sequence repeats at all.
     */
    static final double BREATH_HZ = 0.4;

    /** How much of the title's brightness the breath takes, at most. */
    private static final double BREATH_DEPTH = 0.12;

    /**
     * The roles the start-up title cycles, in the order it cycles them.
     *
     * <p>The same six the runner's star walks, in the same order, read out of
     * {@link Palette#bootRainbow()} rather than out of the console's own palette - which is monochrome,
     * so cycling roles there would give six greys. Naming roles rather than colours is what keeps the
     * hexadecimal in {@code mood/} where ground rule 7 requires it.
     */
    private static final PaletteRole[] TITLE_RAINBOW = {
            PaletteRole.PRIMARY, PaletteRole.POSITIVE, PaletteRole.METER_LOW,
            PaletteRole.ACCENT, PaletteRole.HIGHLIGHT, PaletteRole.NEGATIVE,
    };

    /**
     * How many times a second the title walks the whole rainbow.
     *
     * <p>One full sweep every two and a half seconds, which is six colour changes in that time and
     * nowhere near §8b's 3 Hz cap on anything full-screen and rhythmic. It is also deliberately slower
     * than the star's {@code RAINBOW_HZ}: the star is a power-up going off during a race and this is a
     * title being read, so the colour has to move slowly enough that the name stays the thing being
     * looked at. Interpolated rather than stepped, so nothing here ever flashes.
     */
    static final double TITLE_RAINBOW_HZ = 0.4;

    /**
     * How far through the show the title starts settling from the rainbow to white.
     *
     * <p>It reaches white exactly at {@link #FADE_OUT}, so the colour finishes at the instant the
     * picture begins to go and the fanfare dies - the cycle resolves rather than being cut off
     * mid-sweep. Starting it at {@link #LOADING_IN} means the settling and the loading bar are the
     * same movement: the machine has finished showing off and is getting on with it.
     */
    static final double TITLE_WHITE_IN = LOADING_IN;

    /** How many stars drift behind the held title. */
    private static final int STARS = 90;

    /** How bright the stars get, at most. One pixel each, and they must not compete with the title. */
    private static final double STAR_ALPHA = 0.38;

    /** How far down the screen a star travels over the whole show, as a multiple of its height. */
    private static final double STAR_DRIFT = 0.55;

    /** How bright a ray is at its strongest. */
    private static final double RAY_ALPHA = 0.16;

    /**
     * How far out a ray starts, as a share of its own length.
     *
     * <p>Wedges that converge on a single point put a solid grey disc behind the title - see
     * {@link #drawRays}. This leaves the middle clear.
     */
    private static final double RAY_INNER = 0.30;

    /** How tall the seam of light grows, in pixels, before it thins away under the title. */
    private static final double SEAM_HEIGHT = 6;

    /** How visible the skip hint is. It is a way out, not an instruction. */
    private static final double SKIP_HINT_ALPHA = 0.55;

    /** Size of the line before the title, in pixels. */
    private static final double PRESENTS_SIZE = 11;

    /**
     * The line the machine shows before the title.
     *
     * <p>The structural equivalent of a console's own first screen, which names whoever made the
     * hardware before the game names itself. This is a Data Structures project for Universidad EIA, so
     * that is what it says - and it is deliberately small, dim and gone before the title arrives.
     */
    private static final String PRESENTS_LINE = "UNIVERSIDAD EIA";

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

    /** Largest size the name is splashed across the loading screen at, in pixels. */
    private static final double SPLASH_SIZE_MAX = 44;

    /** Smallest size the splash is allowed to shrink to before it stops being a splash. */
    private static final double SPLASH_SIZE_MIN = 10;

    /** How much of the screen's width the splash may take. */
    private static final double SPLASH_WIDTH_SHARE = 0.8;

    /**
     * How many lines the splash may run to.
     *
     * <p>Three, because a title is a title and not a paragraph. The name breaks on its own
     * underscores into seven runs, so given enough lines it would happily come out as a narrow column
     * of them at the largest size on offer - which is the one arrangement that reads as a wrapping
     * accident rather than as a logo.
     */
    private static final int SPLASH_MAX_LINES = 3;

    /**
     * Clear space under the splash before the LOADING caption, in pixels.
     *
     * <p>Measured off the picture rather than reasoned about. Without it the caption's own baseline sat
     * fourteen pixels under the splash's, which is inside the descender space of a 44px glyph: nothing
     * technically overlapped, and the caption still read as having been tucked into the bottom of the
     * title rather than placed under it.
     */
    private static final double SPLASH_GAP = 28;

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

    /** Run when the show starts, so the caller can begin whatever the bar is standing for. */
    private Runnable onLoading;

    /**
     * Run at the instant the cartridge seats, which is where the fanfare belongs.
     *
     * <p>A callback rather than the sound itself: playing audio is {@code audio/}'s business, and a
     * view that opened an output line would be the one place in this application where a screen made
     * a noise. It is also what keeps {@link #previewAt} silent - a screenshot must not play a sound.
     */
    private Runnable onGlitch;

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

    /** When the current phase began, in seconds on the wall clock. */
    private double phaseStarted;

    /** How far through the tear the sequence is, 0 to 1. */
    private double glitchProgress;

    /** How far through the show the sequence is, 0 to 1. Everything after the tear is a function of it. */
    private double showProgress;

    /**
     * How long the show runs for, in seconds.
     *
     * <p>Set from the fanfare's own decoded length so the picture and the sound end together. See
     * {@link #SEQUENCE_SECONDS} for what it falls back to.
     */
    private double sequenceSeconds = SEQUENCE_SECONDS;

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

        // A Pane does not clip its children, and the cartridge is deliberately taller than the
        // travel it makes - so near full insertion its foot hangs below this pane's own bottom edge,
        // where the three canvases end and nothing can paint over it. That left a sliver of cartridge
        // visible under the loading screen: everything else went black and the one thing that had
        // just gone into the machine was still on screen. Clipped to the pane, the slot swallows it.
        javafx.scene.shape.Rectangle bounds = new javafx.scene.shape.Rectangle();
        bounds.widthProperty().bind(widthProperty());
        bounds.heightProperty().bind(heightProperty());
        setClip(bounds);

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
     * Sets what to fire at the instant the cartridge seats and the picture tears.
     *
     * <p>This is where the boot fanfare goes. It runs once, from {@link #startSequence()}, so
     * {@link #settle()} and {@link #previewAt} - the two ways the smoke test drives this screen -
     * never trigger it: a screenshot must not make a noise, and a check that ran the whole sequence
     * silently is worth more than one that plays fifteen seconds of audio into a build log.
     *
     * @param action run once, as the glitch begins; {@code null} to do nothing
     */
    public void setOnGlitch(Runnable action) {
        this.onGlitch = action;
    }

    /**
     * Sets how long the whole start-up sequence runs for.
     *
     * <p>Called with the fanfare's own decoded length, so <strong>the animation is exactly as long as
     * the sound</strong> rather than a number written down beside it. Every stage of the show is a
     * fraction of this, so replacing the audio with something longer or shorter re-times the sequence
     * and nothing else has to change - and the picture can never finish while the sound is still
     * playing, which is the one way a fanfare reads as having been cut off.
     *
     * @param seconds the total length including the tear, clamped to at least
     *                {@link #MIN_SEQUENCE_SECONDS}; anything not positive restores
     *                {@link #SEQUENCE_SECONDS}
     */
    public void setSequenceSeconds(double seconds) {
        this.sequenceSeconds = seconds > 0
                ? Math.max(MIN_SEQUENCE_SECONDS, seconds)
                : SEQUENCE_SECONDS;
    }

    /** @return how long the whole sequence runs for, in seconds */
    public double sequenceSeconds() {
        return sequenceSeconds;
    }

    /** @return how long the show after the tear runs for, in seconds */
    public double showSeconds() {
        return Math.max(0.1, sequenceSeconds - GLITCH_SECONDS);
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

    // ------------------------------------------------------------------
    // The show's envelopes - every one a pure function of how far through it is
    // ------------------------------------------------------------------
    //
    // Static and side-effect free for the same reason the tear is: none of this can be photographed
    // and none of it can be waited for. A still of a fade is a still of something at one opacity, and
    // the smoke test holds the interface thread so the sequence's own timer never ticks. These are
    // what a test can actually reach, and what previewShow() asks for at a stated instant.

    /**
     * Ramps from 0 to 1 across a window, and is flat outside it.
     *
     * <p><strong>Smoothstep rather than linear</strong>, which is most of why the fades read as
     * dramatic rather than as opacity being animated: a linear fade starts and stops abruptly at both
     * ends, and the eye catches the corner. This eases in and out of every transition.
     *
     * @param value where we are
     * @param from  where the ramp starts
     * @param to    where it ends; equal to or below {@code from} makes this a step
     * @return 0 before, 1 after, eased between
     */
    static double ramp(double value, double from, double to) {
        if (!(to > from)) {
            return value >= to ? 1 : 0;
        }
        double t = Math.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * How visible the publisher line is.
     *
     * <p>Up, held, and away again <strong>before {@link #TITLE_IN}</strong>, so it and the title are
     * never on screen at once. That ordering is the whole of what makes this read as two movements
     * rather than as one crowded screen, and {@code BootScreenGeometryTest} asserts it rather than
     * leaving it to whoever next edits the constants.
     *
     * @param show how far through the show, 0 to 1
     * @return opacity, 0 to 1
     */
    static double presentsAlpha(double show) {
        return ramp(show, PRESENTS_IN, PRESENTS_FULL)
                * (1 - ramp(show, PRESENTS_OUT, PRESENTS_GONE));
    }

    /**
     * How visible the title is.
     *
     * <p>Fades up on the fanfare's big hit, holds for the whole sustained passage, and goes out with
     * everything else at the end. This is the fade the sequence is built around.
     *
     * @param show how far through the show, 0 to 1
     * @return opacity, 0 to 1
     */
    static double titleAlpha(double show) {
        return ramp(show, TITLE_IN, TITLE_FULL) * (1 - blackout(show));
    }

    /**
     * How large the title is drawn, as a multiple of its resting size.
     *
     * <p>It arrives {@link #TITLE_OVERSHOOT} too big and settles, which is what makes it <em>land</em>
     * instead of appearing. Only ever at or above 1, so it never shrinks below the size
     * {@link #splashFontSize} measured to fit - a title that faded in already overflowing the screen
     * and then shrank into it would be a fade in the wrong direction.
     *
     * @param show how far through the show, 0 to 1
     * @return the scale, 1 or more
     */
    static double titleScale(double show) {
        return 1 + TITLE_OVERSHOOT * (1 - ramp(show, TITLE_IN, TITLE_FULL));
    }

    /**
     * The colour the title is drawn in: a rainbow that settles to white as the fanfare dies.
     *
     * <p><strong>The one thing on this screen that has a hue, and it is the machine rather than the
     * software.</strong> Everything else in the sequence asks {@link Palette#hardware()} and comes out
     * black and white, because at this point no software has chosen a look; a console running a colour
     * test across its own name is the opposite statement, and the two sit in the same frame without
     * contradicting each other. Ground rule 7 is untouched: this names a {@link PaletteRole} and mixes
     * two of them out of {@link Palette#bootRainbow()}, so the colours live in {@code mood/} exactly as
     * the console's own do.
     *
     * <p>Mixed rather than stepped, and {@link Palette#mix} snaps the result back onto the 5-bit grid,
     * so the sweep stays inside the colours a GBA could have shown and never flashes - which is what
     * keeps a full-screen cycling title inside §8b's cap on rhythmic effects rather than merely near it.
     *
     * <p><strong>It resolves to white instead of stopping.</strong> From {@link #TITLE_WHITE_IN} it
     * eases to the console's own {@code TEXT_PRIMARY} - a true white, the same value the insertion flash
     * uses - and arrives there exactly at {@link #FADE_OUT}, so the colour finishes at the instant the
     * blackout starts and the sound runs out. Cutting a rainbow off mid-sweep at the fade would read as
     * the effect having been interrupted rather than having ended.
     *
     * <p>Static and taking the length rather than reading {@link #showSeconds()}, like every other
     * element of the show: the sequence re-times itself to whatever the fanfare measures, and a
     * screenshot or a test has to be able to ask what this looks like at a stated instant.
     *
     * @param show    how far through the show, 0 to 1
     * @param seconds how long the whole show lasts, so the cycle keeps real time
     * @return the colour the name is drawn in, fully opaque
     */
    static Color titleColor(double show, double seconds) {
        double cycle = show * seconds * TITLE_RAINBOW_HZ * TITLE_RAINBOW.length;
        int from = (int) Math.floorMod((long) Math.floor(cycle), TITLE_RAINBOW.length);
        int to = (from + 1) % TITLE_RAINBOW.length;
        Color hue = Palette.bootRainbow()
                .mix(TITLE_RAINBOW[from], TITLE_RAINBOW[to], cycle - Math.floor(cycle));
        return hue.interpolate(Palette.hardware().color(PaletteRole.TEXT_PRIMARY),
                ramp(show, TITLE_WHITE_IN, FADE_OUT));
    }

    /**
     * How wide the seam of light behind the title is, as a fraction of the screen.
     *
     * <p>A line at the centre that opens out sideways, which is the cheapest dramatic reveal there is
     * and the one that suits a hard-edged interface: no blur, no gradient, one rectangle.
     *
     * @param show how far through the show, 0 to 1
     * @return 0 to 1
     */
    static double seamWidth(double show) {
        return ramp(show, TITLE_IN, TITLE_IN + (TITLE_FULL - TITLE_IN) * 0.45);
    }

    /**
     * How visible the rays sweeping out of the centre are.
     *
     * <p>Brightest as the title lands and gone by {@link #RAYS_DONE}, so they are the arrival rather
     * than a permanent decoration. Deliberately not a repeating sweep - see {@link #BREATH_HZ} for the
     * only thing here that repeats at all.
     *
     * @param show how far through the show, 0 to 1
     * @return opacity, 0 to 1
     */
    static double rayAlpha(double show) {
        double in = ramp(show, TITLE_IN, TITLE_IN + 0.03);
        return in * (1 - ramp(show, TITLE_IN + 0.03, RAYS_DONE));
    }

    /**
     * How far out the rays have swept, as a multiple of the screen's half-diagonal.
     *
     * @param show how far through the show, 0 to 1
     * @return 0 to 1
     */
    static double raySweep(double show) {
        return ramp(show, TITLE_IN, RAYS_DONE);
    }

    /**
     * How visible the loading bar is.
     *
     * @param show how far through the show, 0 to 1
     * @return opacity, 0 to 1
     */
    static double barAlpha(double show) {
        return ramp(show, LOADING_IN, LOADING_IN + 0.05) * (1 - blackout(show));
    }

    /**
     * How full the loading bar is.
     *
     * <p>It reaches full exactly at {@link #FADE_OUT}, so the machine finishes reading as the picture
     * starts to go - rather than filling early and sitting there, which would make the rest of the
     * sequence look like a wait rather than a flourish.
     *
     * @param show how far through the show, 0 to 1
     * @return 0 to 1
     */
    static double barProgress(double show) {
        return Math.clamp((show - LOADING_IN) / (FADE_OUT - LOADING_IN), 0, 1);
    }

    /**
     * How much of the screen has gone back to black at the end.
     *
     * @param show how far through the show, 0 to 1
     * @return 0 for none, 1 for entirely black
     */
    static double blackout(double show) {
        return ramp(show, FADE_OUT, 1);
    }

    /**
     * The movements of the show, and the instant in each one worth photographing.
     *
     * <p>Exists so a caller can walk the whole sequence without the timing constants having to become
     * public for its benefit, and so the list of moments lives beside the timings rather than being
     * copied into the smoke test where the two could drift.
     *
     * <p>Each instant is the <strong>middle</strong> of its movement rather than either edge. Every one
     * of these is a fade, and a still of the edge of a fade is a still of an empty screen - which looks
     * exactly like a screen that failed to draw, and is the one picture that would be believed.
     */
    public enum Movement {

        /** The publisher line, up and gone before the title. */
        PRESENTS("boot-presents", midpoint(PRESENTS_FULL, PRESENTS_OUT)),

        /** The title fading up on the fanfare's big hit. */
        TITLE("boot-title", midpoint(TITLE_IN, TITLE_FULL)),

        /** The long hold, with the title full and the stars drifting. */
        HOLD("boot-hold", midpoint(TITLE_FULL, LOADING_IN)),

        /** The bar filling under it as the fanfare decays. */
        LOADING("boot-loading", midpoint(LOADING_IN, FADE_OUT)),

        /** Everything going back to black. */
        FADE("boot-fade", midpoint(FADE_OUT, 1));

        private final String label;
        private final double instant;

        Movement(String label, double instant) {
            this.label = label;
            this.instant = instant;
        }

        /** @return a name for a screenshot file */
        public String label() {
            return label;
        }

        /** @return how far through the show this movement is best seen, 0 to 1 */
        public double instant() {
            return instant;
        }
    }

    /**
     * @param from the start of a movement, as a fraction of the show
     * @param to   its end
     * @return the instant half way between them
     */
    private static double midpoint(double from, double to) {
        return (from + to) / 2;
    }

    /**
     * A reproducible number in {@code [0, 1)} from two small seeds.
     *
     * <p>The **SplitMix64 finalizer**, the same mixer {@link #tearOffset} uses and for the same reason:
     * the starfield has to come out identical on every run and on every future runtime, so a library
     * generator - free to change its algorithm - is the wrong tool. FNV-1a was measured to be wrong for
     * this shape of input; see {@link #tearOffset}.
     *
     * @param a the first seed
     * @param b the second seed
     * @return a value in {@code [0, 1)}
     */
    static double seeded(int a, int b) {
        long z = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) / (double) (1L << 53);
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

    /**
     * Picks the size the name is splashed across the loading screen at.
     *
     * <p>The same arithmetic as {@link #labelFontSize} against a different constraint: there the
     * limit was a label much taller than it was wide, and here it is the screen's width against a
     * ceiling on how many lines a title may be. On this window the name comes out as two lines at the
     * largest size on offer, which is a logo; give it more lines and it would come out as seven short
     * ones at the same size, which is a wrapping accident.
     *
     * @param width the screen's width, in pixels
     * @param name  the name to splash
     * @return the size, in whole pixels
     */
    static double splashFontSize(double width, String name) {
        if (!(width > 0) || name == null || name.isEmpty()) {
            return SPLASH_SIZE_MIN;
        }
        double room = width * SPLASH_WIDTH_SHARE;
        for (double size = SPLASH_SIZE_MAX; size > SPLASH_SIZE_MIN; size -= 1) {
            int perLine = (int) Math.floor(room / size);
            if (perLine < 1) {
                continue;
            }
            if (wrapName(name, perLine).size() <= SPLASH_MAX_LINES) {
                return size;
            }
        }
        return SPLASH_SIZE_MIN;
    }

    /**
     * The splash as it will actually be drawn at a given width.
     *
     * <p>Exists so the smoke test can measure the splash without {@link #splashFontSize} and
     * {@link #wrapName} having to become public for its benefit. A splash that silently fell back to
     * {@link #SPLASH_SIZE_MIN} still draws perfectly well - it just stops being a splash - and the
     * only way to notice that is to read the numbers.
     *
     * @param size        the size it is drawn at, in pixels
     * @param lines       how many lines it wraps to
     * @param widestChars the longest line's length in characters
     */
    public record Splash(double size, int lines, int widestChars) {

        /** @return how wide the splash comes out, in pixels; the font advances one em per glyph */
        public double widthPixels() {
            return widestChars * size;
        }

        /**
         * @param width the screen's width, in pixels
         * @return whether the splash fits and is still large enough to be one
         */
        public boolean fits(double width) {
            return size > SPLASH_SIZE_MIN && widthPixels() <= width;
        }
    }

    /**
     * Works out how the name will be splashed at a given width.
     *
     * @param width the screen's width, in pixels
     * @return the measurements, never {@code null}
     */
    public static Splash splashAt(double width) {
        double size = splashFontSize(width, AppConfig.APP_NAME);
        List<String> lines = wrapName(AppConfig.APP_NAME,
                (int) Math.floor(width * SPLASH_WIDTH_SHARE / size));
        int widest = lines.stream().mapToInt(String::length).max().orElse(0);
        return new Splash(size, lines.size(), widest);
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
     * never advance and neither would the tear or the show, and the screen would sit at whatever
     * fraction the last synthesised drag left it at, forever. Same reason
     * {@code StructureView.settle()} exists for the screenshots.
     *
     * <p>It is also what {@link #skip()} runs on, so a user in a hurry and a smoke test take exactly
     * the same path out.
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
     * Cuts the sequence short and hands the window over.
     *
     * <p><strong>A fifteen-second boot needs a way past it, and that is not a compromise on the
     * sequence.</strong> The whole point of running for the length of the fanfare is that the first
     * launch is an event; the third launch of an afternoon is a wait, and somebody demonstrating the
     * application will start it many times. Every console this is dressed as let you press a button
     * through its own logo.
     *
     * <p>Refused while the cartridge is still outside the machine: there is nothing to skip yet, and a
     * key that booted the application without the gesture would make the gesture optional.
     *
     * @return whether there was anything to skip
     */
    public boolean skip() {
        if (phase == Phase.INSERT || phase == Phase.DONE) {
            return false;
        }
        settle();
        return true;
    }

    /**
     * Puts the tear at a chosen moment and draws it, without a running clock.
     *
     * <p>The glitch is unphotographable for the same reason a spinning disk is: the frame loop cannot
     * run while the smoke test holds the interface thread, so the only way to get a picture of it is to
     * ask for one at a stated instant. Mirrors {@code MiniPlayerView.previewAt}.
     *
     * @param glitch how far through the tearing, 0 to 1
     */
    public void previewGlitch(double glitch) {
        this.phase = Phase.GLITCH;
        this.glitchProgress = Math.clamp(glitch, 0, 1);
        this.showProgress = 0;
        redrawPreview();
    }

    /**
     * Puts the show at a chosen moment and draws it, without a running clock.
     *
     * <p>Every movement of the show is a fade, and <strong>a fade is the single most unphotographable
     * thing there is</strong>: a still of one is a still of something at an opacity, and a still of the
     * wrong instant is a still of an empty screen that looks exactly like a screen that failed to draw.
     * So the moments worth having are asked for by number - which is also the only way to get more than
     * one of them out of a run that holds the interface thread from beginning to end.
     *
     * @param show how far through the show, 0 to 1
     */
    public void previewShow(double show) {
        this.phase = Phase.SHOW;
        this.glitchProgress = 1;
        this.showProgress = Math.clamp(show, 0, 1);
        redrawPreview();
    }

    /** Draws a previewed moment. */
    private void redrawPreview() {
        insertion.set(1);
        // Asked for explicitly, because a Parent only lays out when something marked it dirty and none
        // of the fields above are observable. Without this a second preview silently redraws the first
        // one - which looks like the phase never changed rather than like the canvas was never asked to.
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
     * Starts the tear, then the show, then hands the window over.
     *
     * <p>One timer for both phases rather than a chain of transitions, so there is a single place
     * that knows how far through the sequence the machine is and a single place that can be stopped.
     *
     * <p><strong>It runs on wall time, and that is not the same as being synchronised to the
     * fanfare.</strong> The two are set to the same <em>length</em> - {@link #setSequenceSeconds} takes
     * the sound's own decoded duration - so they finish together, which is all the agreement this needs.
     * Actually driving the picture off the audio's position would mean reading a clock from a line this
     * screen does not own and cannot see, for a sound that is deliberately allowed to outlive the boot.
     */
    private void startSequence() {
        if (phase != Phase.INSERT) {
            return;
        }
        phase = Phase.GLITCH;
        phaseStarted = now();
        // Before the first frame of the tear rather than after it: the flash and the sound are the
        // same event, and a fanfare that started a frame late would read as a sound effect rather
        // than as the machine coming on.
        Runnable fanfare = onGlitch;
        onGlitch = null;
        if (fanfare != null) {
            fanfare.run();
        }
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
                phase = Phase.SHOW;
                phaseStarted = seconds;
                showProgress = 0;
                // The one moment anything real is started. Fired as the show begins rather than on
                // insertion, so the work is under way while the machine is introducing itself and the
                // caption under the bar has something true to say by the time the bar appears.
                Runnable started = onLoading;
                if (started != null) {
                    started.run();
                }
            }
        } else if (phase == Phase.SHOW) {
            showProgress = Math.clamp(elapsed / showSeconds(), 0, 1);
            if (showProgress >= 1) {
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

        // The cartridge is only on screen while it is still outside the machine. Past that it is
        // *inside* - the glitch draws its own torn copy of the artwork, which is the picture breaking
        // up rather than the object still sitting there, and the loading screen has no cartridge on it
        // at all. Decided here rather than at each phase transition because there are four ways into a
        // phase (the drag, settle(), previewAt() and the sequence's own timer) and this is the one
        // place all four pass through.
        boolean stillOutside = phase == Phase.INSERT;
        cartridge.setVisible(stillOutside);
        plate.setVisible(stillOutside);

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
        Palette palette = Palette.hardware();
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

        Palette palette = Palette.hardware();
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
        if (phase == Phase.SHOW) {
            drawShow(gc, width, height);
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
        Palette palette = Palette.hardware();
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
        Palette palette = Palette.hardware();
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
        Palette palette = Palette.hardware();

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

    /**
     * The machine introducing itself: the whole start-up sequence after the tear.
     *
     * <p>Five movements over one clock, in the order a console does them - a publisher line, the title
     * arriving on the fanfare's big hit, a hold, the bar, and a fade to black. Every element's opacity,
     * size and position is a pure function of {@link #showProgress}, so this method only decides
     * <em>where</em> things go and never <em>when</em>.
     *
     * <p>Drawn back to front over a black ground each frame: stars, the seam, the rays, the title, the
     * bar, then the blackout over the lot. Nothing here is a gradient and nothing is anti-aliased - the
     * drama is all in hard-edged blocks and in the easing.
     */
    private void drawShow(GraphicsContext gc, double width, double height) {
        Palette palette = Palette.hardware();
        double show = showProgress;

        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, 0, width, height);

        gc.setTextAlign(TextAlignment.CENTER);
        double centreX = Math.round(width / 2);
        double centreY = Math.round(height * 0.42);

        drawStars(gc, palette, width, height, show);
        drawRays(gc, palette, width, height, centreX, centreY, show);
        drawSeam(gc, palette, width, centreY, show);
        drawTitle(gc, palette, width, centreY, show);
        drawBar(gc, palette, width, height, show);

        // Last, over everything, which is what makes it a fade to black rather than five things each
        // fading on their own and arriving at slightly different times.
        double gone = blackout(show);
        if (gone > 0) {
            gc.setFill(palette.color(PaletteRole.SHADOW, gone));
            gc.fillRect(0, 0, width, height);
        }
    }

    /**
     * The publisher line, up and away again before the title arrives.
     *
     * <p>Small and dim, exactly as a console's first screen is: it is not the title and must not compete
     * with the one that follows it. {@link #presentsAlpha} guarantees they never share the screen.
     */
    private void drawPresents(GraphicsContext gc, Palette palette, double width, double centreY,
            double show) {
        double alpha = presentsAlpha(show);
        if (alpha <= 0) {
            return;
        }
        gc.setFont(Fonts.pixel(PRESENTS_SIZE));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM, alpha));
        gc.fillText(PRESENTS_LINE, Math.round(width / 2), Math.round(centreY));
    }

    /**
     * A drifting field of single pixels behind the held title.
     *
     * <p><strong>Seeded, not random</strong>, by the same SplitMix64 finalizer the tear uses and for the
     * same reason: an effect nobody can reproduce is an effect nobody can check, and every frame of this
     * screen is drawn by {@code previewShow} rather than by a running clock during a smoke test.
     *
     * <p>They exist because a five-second hold on a static frame reads as the application having frozen
     * at exactly the moment it is meant to look most alive - the same problem the companion window's
     * spinning record solves. They drift slowly and they are one pixel each, so they cannot compete with
     * the title.
     */
    private void drawStars(GraphicsContext gc, Palette palette, double width, double height,
            double show) {
        double alpha = Math.min(titleAlpha(show), STAR_ALPHA);
        if (alpha <= 0) {
            return;
        }
        gc.setFill(palette.color(PaletteRole.TEXT_DIM, alpha));
        for (int star = 0; star < STARS; star++) {
            double x = seeded(star, 1) * width;
            // Downwards, at a speed of its own, wrapping - so the field never runs out.
            double drift = (seeded(star, 2) * 0.5 + 0.5) * STAR_DRIFT;
            double y = ((seeded(star, 3) + show * drift) % 1) * height;
            gc.fillRect(Math.round(x), Math.round(y), 1, 1);
        }
    }

    /**
     * Wedges sweeping out of the centre as the title lands.
     *
     * <p>Hard-edged triangles rather than a soft glow, because everything else on this screen is hard
     * edged and a blurred one would be the odd one out. They are gone by {@link #RAYS_DONE}: this is the
     * moment of arrival, not a permanent decoration, and a spinning starburst behind a title is where
     * this sort of thing stops being dramatic and starts being a screensaver.
     */
    private void drawRays(GraphicsContext gc, Palette palette, double width, double height,
            double centreX, double centreY, double show) {
        double alpha = rayAlpha(show);
        if (alpha <= 0) {
            return;
        }
        double sweep = raySweep(show);
        double half = Math.hypot(width, height) / 2;
        // **They start at a radius rather than at the point, and that is not a detail.** Sixteen wedges
        // all converging on one pixel fill the middle of the screen with solid grey - which is a blob
        // with spikes on it rather than light coming from behind the title, and it is precisely where
        // the title has to be readable. Starting them out at RAY_INNER leaves the centre clear.
        double inner = half * RAY_INNER * sweep;
        double outer = half * sweep;
        double spread = Math.PI / RAYS * 0.45;

        gc.setFill(palette.color(PaletteRole.TEXT_PRIMARY, alpha * RAY_ALPHA));
        for (int ray = 0; ray < RAYS; ray++) {
            // Turning very slowly, so the burst is arriving rather than stamped on. Slow enough that it
            // never reads as a spin - a rotating starburst is where this stops being an arrival.
            double angle = ray * 2 * Math.PI / RAYS + show * 0.35;
            gc.fillPolygon(
                    new double[] {
                        centreX + Math.cos(angle - spread) * inner,
                        centreX + Math.cos(angle - spread) * outer,
                        centreX + Math.cos(angle + spread) * outer,
                        centreX + Math.cos(angle + spread) * inner},
                    new double[] {
                        centreY + Math.sin(angle - spread) * inner,
                        centreY + Math.sin(angle - spread) * outer,
                        centreY + Math.sin(angle + spread) * outer,
                        centreY + Math.sin(angle + spread) * inner},
                    4);
        }
    }

    /**
     * The seam of light the title is revealed out of: a line at the centre that opens sideways.
     *
     * <p>The cheapest dramatic reveal there is and the one that suits this interface - one rectangle, no
     * blur, no gradient. It thickens as it opens and then thins away again under the title, so what is
     * left behind is the title rather than a band it is sitting in.
     */
    private void drawSeam(GraphicsContext gc, Palette palette, double width, double centreY,
            double show) {
        double open = seamWidth(show);
        if (open <= 0) {
            return;
        }
        // **Gone before the title is legible, and that is a correction rather than a preference.** It
        // sits at the same height as the middle of the title block, so while both are half-way up the
        // line runs straight through the words and reads as a strikethrough. Fading it out over the
        // first part of the title's own fade-in means the two hand over rather than overlap: the seam
        // opens, the title comes up out of it, the seam is gone. Caught by looking at the picture.
        double alpha = (1 - ramp(show, TITLE_IN + 0.02, midpoint(TITLE_IN, TITLE_FULL)))
                * (1 - blackout(show));
        if (alpha <= 0) {
            return;
        }
        double seamW = Math.round(width * open);
        double seamH = Math.max(1, Math.round(SEAM_HEIGHT * open));
        gc.setFill(palette.color(PaletteRole.TEXT_PRIMARY, alpha));
        gc.fillRect(Math.round((width - seamW) / 2), Math.round(centreY - seamH / 2), seamW, seamH);
    }

    /**
     * The title, fading up and settling to its size.
     *
     * <p><strong>This is the only place the name is shown at a size worth its length.</strong> The
     * cartridge's label is 48% of a cartridge, the title bar shares a strip with three toggles and the
     * companion window is measured to have room for none of it - so the one screen with nothing else on
     * it is where a 43-character joke gets to be the point. It is safe here precisely because the
     * cartridge has gone into the machine by now: while the label was on screen this would have been the
     * name printed twice at once.
     *
     * <p>Broken on the name's own separators by {@link #wrapName} and sized by {@link #splashFontSize},
     * so it is the same object as the cartridge's label rather than a second idea of how to lay the name
     * out. <strong>The wrap is computed at the resting size and never at the scaled one</strong>: wrapping
     * against the size it is momentarily drawn at would re-break the title mid-fade, so the line count
     * would change while it was arriving and the block would jump.
     */
    private void drawTitle(GraphicsContext gc, Palette palette, double width, double centreY,
            double show) {
        double alpha = titleAlpha(show);
        drawPresents(gc, palette, width, centreY, show);
        if (alpha <= 0) {
            return;
        }

        double resting = splashFontSize(width, AppConfig.APP_NAME);
        List<String> lines = wrapName(AppConfig.APP_NAME,
                (int) Math.floor(width * SPLASH_WIDTH_SHARE / resting));
        double size = resting * titleScale(show);
        double lineHeight = size * 1.35;
        double block = lines.size() * lineHeight;

        // A slow swell while it is held, so the long middle of the sequence is not a frozen frame. Well
        // under the 3 Hz cap on anything full-screen and rhythmic - see BREATH_HZ.
        double held = ramp(show, TITLE_FULL, TITLE_FULL + 0.04);
        double breath = 1 - held * BREATH_DEPTH
                * (0.5 - 0.5 * Math.cos(show * showSeconds() * BREATH_HZ * 2 * Math.PI));

        gc.setFont(Fonts.pixel(size));
        // The rainbow, settling to white as the fanfare dies - see titleColor. The breath and the fade
        // still ride on top of it as opacity, so the colour decides the hue and nothing else.
        gc.setFill(titleColor(show, showSeconds())
                .deriveColor(0, 1, 1, Math.clamp(alpha * breath, 0d, 1d)));
        double first = centreY - block / 2 + size;
        for (int i = 0; i < lines.size(); i++) {
            gc.fillText(lines.get(i), Math.round(width / 2),
                    Math.round(first + i * lineHeight));
        }

        // The version under it, arriving a beat later so the two do not appear as one block.
        double versionAlpha = ramp(show, TITLE_FULL - 0.04, TITLE_FULL + 0.06) * (1 - blackout(show));
        if (versionAlpha > 0) {
            gc.setFont(Fonts.pixel(HINT_SIZE));
            gc.setFill(palette.color(PaletteRole.TEXT_DIM, versionAlpha));
            gc.fillText("v" + AppConfig.APP_VERSION, Math.round(width / 2),
                    Math.round(centreY + block / 2 + SPLASH_GAP));
        }
    }

    /**
     * The loading bar and the caption naming what is being loaded.
     *
     * <p>Filled in whole blocks rather than as a smooth sweep, which is what makes it read as 8-bit
     * rather than as a web page. It reaches full at {@link #FADE_OUT}, so the machine finishes reading as
     * the picture starts to go; the caption under it reports the daemon actually coming up, which is the
     * one thing at startup that genuinely takes a moment.
     */
    private void drawBar(GraphicsContext gc, Palette palette, double width, double height,
            double show) {
        double alpha = barAlpha(show);
        if (alpha <= 0) {
            return;
        }

        double barWidth = Math.round(Math.min(width * 0.42, 520));
        double barHeight = 22;
        double barX = Math.round((width - barWidth) / 2);
        double barY = Math.round(height * 0.76);

        gc.setFill(palette.color(PaletteRole.SHADOW, alpha).interpolate(
                palette.shaded(PaletteRole.SHADOW, 0.5), alpha));
        gc.fillRect(barX, barY, barWidth, barHeight);

        double block = 10;
        int blocks = (int) Math.floor(barWidth / block);
        int lit = (int) Math.round(blocks * barProgress(show));
        gc.setFill(palette.color(PaletteRole.PRIMARY, alpha));
        for (int i = 0; i < lit; i++) {
            gc.fillRect(barX + i * block + 1, barY + 3, block - 2, barHeight - 6);
        }

        gc.setStroke(palette.color(PaletteRole.OUTLINE, alpha));
        gc.setLineWidth(RIM);
        gc.strokeRect(barX - RIM / 2, barY - RIM / 2, barWidth + RIM, barHeight + RIM);

        // ACCENT rather than TEXT_DIM: this is the only line on the screen that changes while the bar
        // runs, and it is the whole evidence that anything is actually happening.
        String reporting = status;
        if (!reporting.isEmpty()) {
            gc.setFont(Fonts.pixel(HINT_SIZE));
            gc.setFill(palette.color(PaletteRole.ACCENT, alpha));
            gc.fillText(reporting, Math.round(width / 2), Math.round(barY + barHeight + 24));
        }

        // How to get past all this, said once the bar is up rather than over the title. A fifteen second
        // ritual is an event the first time and a wait the tenth, and somebody demonstrating this will
        // start it many times - see skip().
        gc.setFont(Fonts.pixel(HINT_SIZE));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM, alpha * SKIP_HINT_ALPHA));
        gc.fillText("PRESS ANY KEY TO SKIP", Math.round(width / 2),
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
