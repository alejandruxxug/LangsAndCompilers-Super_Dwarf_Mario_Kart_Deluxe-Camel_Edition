package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.animation.AnimationTimer;
import javafx.geometry.Rectangle2D;
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
 * <p><strong>And the cartridge comes back out, which is the whole shape of the thing.</strong>
 * {@link BootScreen} opens on a cartridge being pushed into a slot; this closes by handing it back.
 * The two are one gesture and its reverse, so this screen borrows the boot screen's geometry rather
 * than inventing its own - the same slot, the same hover gap, the same measured inlet - and it borrows
 * its rule about the name as well: <strong>the title is on the screen while the cartridge is not, and
 * on the cartridge's label once it is</strong>. So the splash fades out as the cartridge rises and the
 * name reappears where a cartridge has always carried it. The two are never on screen at once, in
 * either direction.
 *
 * <p><strong>It starts where the boot screen left it: seated, with {@code 1 - SEAT_SHARE} of itself
 * still above the slot.</strong> Beginning from entirely swallowed would have the cartridge climb out
 * of a hole nothing was ever put into - the machine would be handing back something it had eaten - and
 * it costs the first third of the travel to reach the position the last thing the user actually did
 * left it in. So {@link #emergence} runs from the boot screen's own seated y to the hover above it,
 * which makes the two screens one continuous object: it goes in this far, it sits there for the whole
 * session, and it comes back out from exactly there.
 *
 * <p><strong>And the picture tears as it goes, because the contact has been broken.</strong> The boot
 * screen glitches when the cartridge reaches a live slot; this glitches at the instant the application
 * is told to close, which is the same electrical event backwards. It is deliberately the <em>same</em>
 * tear - {@link BootScreen#drawTear} draws both, so the two ends of one session cannot come to look
 * like different machines - less the white flash, which belongs only to the end where power arrives.
 * It is drawn over the picture rather than instead of it: this screen is already up when it happens,
 * and a signal breaking up breaks up whatever was being shown.
 *
 * <p><strong>The length is fixed here and taken from the sound on the boot screen, and that asymmetry
 * is deliberate.</strong> {@code BootScreen.setSequenceSeconds} runs the whole start-up for exactly as
 * long as the fanfare, because a first launch is an event worth standing still for. The eject sound is
 * 7.71 s and this runs for {@link #EJECT_SECONDS}: making somebody wait nearly eight seconds to close
 * an application is precisely the hang this screen was built to stop looking like, so the sound is
 * faded out under the blackout instead - see {@link #setOnFading}.
 *
 * <p>The colours come from {@link Palette#hardware()}, not from the active mood, and this screen and
 * {@link BootScreen} are the only two that do. They bracket the application: at one the system has
 * not started and at the other it has stopped, and a mood is something the software chose. See
 * {@link Palette#hardware()} for why that is still inside ground rule 7. Both wear {@link CrtEffect}
 * for the same reason - they are the machine rather than the software, and a display's own texture is
 * what says so without putting a colour on screen.
 *
 * <p>There is deliberately no way to cancel. A shutdown that can be called off is a state every view
 * behind this one would have to know about, and by the time this is on screen the audio line is
 * already closing.
 */
public class ShutdownScreen extends Pane {

    /**
     * How long the whole eject runs for, in seconds.
     *
     * <p><strong>Long enough to be seen and short enough not to be a wait.</strong> Everything below
     * is a fraction of this, so the one number sets the pace of the whole screen. Three seconds puts
     * the cartridge's own travel at about one and a third, which is roughly how long a mechanism takes
     * to let go of something - fast enough to read as a spring rather than a lift, slow enough that
     * the eye follows it out.
     *
     * <p>It also sets the floor on how long quitting takes, which is the cost of the feature and is
     * stated here rather than discovered: {@code App.requestQuit} waits for this <em>and</em> the
     * teardown, so with a daemon running (up to five seconds) the animation is free, and with none it
     * is the whole of the wait. Against the up-to-five-second frozen dock this replaced, three seconds
     * of a window that is painting and answering is the better trade.
     */
    public static final double EJECT_SECONDS = 3.0;

    /**
     * When the picture has finished tearing itself apart, as a fraction of the eject.
     *
     * <p><strong>Before {@link #EJECT_IN}, and the order is the argument.</strong> The tear is the
     * contact breaking, which happens the moment the application is told to close; the travel is the
     * machine handing the cartridge back, which is what it does about it. Overlapping them would put a
     * cause and its consequence on screen at once and read as one confused event, where in sequence it
     * reads as two: the picture goes, and then the cartridge comes out.
     *
     * <p>At {@link #EJECT_SECONDS} this is 0.45 s, which is within a tenth of a second of the boot
     * screen's own {@code GLITCH_SECONDS}. The two are not tied together deliberately - one is a
     * fraction of a sequence and the other is an absolute - but they are meant to look like the same
     * length, and {@code ShutdownScreenGeometryTest} says so rather than leaving it to whoever next
     * moves either number.
     */
    static final double GLITCH_OUT = 0.15;

    /** When the cartridge starts to come out, as a fraction of the eject. */
    static final double EJECT_IN = 0.18;

    /** When it is fully out and hovering above the slot. */
    static final double EJECT_OUT = 0.62;

    /** How far past {@link #EJECT_IN} the splash has finished getting out of the cartridge's way. */
    static final double SPLASH_HANDOVER = 0.12;

    /**
     * When the picture starts going to black.
     *
     * <p>The sound is faded from here too - see {@link #setOnFading}. The gap to the end is what gives
     * {@code SoundEffect}'s own quarter-second fade room to finish before the application goes, so the
     * eject noise ends rather than being cut off with the process.
     */
    static final double FADE_OUT = 0.80;

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

    /** Width of the drawn rims, in pixels. */
    private static final double RIM = 3;

    /** How wide one block of the bar is, in pixels. */
    private static final double BLOCK = 10;

    /** Where the slot's mouth sits, as a fraction down the screen. Lower than the boot screen's,
     *  because the bar and its caption sit underneath it here rather than the cartridge sitting alone. */
    private static final double SLOT_Y_SHARE = 0.78;

    /** How tall the cartridge is drawn, as a fraction of the screen's height. */
    private static final double CARTRIDGE_HEIGHT_SHARE = 0.44;

    /** How far the cartridge ends up hovering above the mouth, in pixels. */
    private static final double HOVER_GAP = 20;

    /** How tall the mouth of the slot is, in pixels. */
    private static final double MOUTH_HEIGHT = 22;

    /** Where the bar sits, as a fraction down the screen. Below the slot, clear of the cartridge. */
    private static final double BAR_Y_SHARE = 0.86;

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

    /** The cartridge artwork, which is the thing being handed back. */
    private final SpriteSheet sheet;

    /** The glass, exactly as the boot screen wears it. See {@link CrtEffect}. */
    private final CrtEffect crt = new CrtEffect();

    /**
     * What is being stopped, named under the bar.
     *
     * <p>Volatile because it is set from whichever thread finished a teardown step, and those run off
     * the interface thread by design - that being the whole reason this screen can be drawn at all.
     */
    private volatile String status = "";

    private AnimationTimer timer;

    /** When the eject started, in seconds on the wall clock. Nothing here is synced to audio. */
    private double startedAt = now();

    /** How far through the eject, 0 to 1. Every element on the screen is a pure function of it. */
    private double ejectProgress;

    private Runnable onFading;
    private Runnable onFinished;

    /** Set once each callback has been fired, so a frame loop cannot fire either of them twice. */
    private boolean faded;
    private boolean finished;

    /**
     * Builds the screen. Nothing moves until {@link #start()}.
     *
     * @param assets where the cartridge artwork comes from; must not be {@code null}
     */
    public ShutdownScreen(AssetRegistry assets) {
        this.sheet = assets.sheet(AssetKind.CARTRIDGE);
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

    /**
     * Sets what to run as the picture starts going to black.
     *
     * <p><strong>This is where the eject sound is faded, and it is a separate callback from
     * {@link #setOnFinished} for a measurable reason.</strong> The sound is 7.71 s against a three
     * second animation, so it is always still playing at the end; {@code SoundEffect.stop()} fades
     * over a quarter of a second rather than cutting, because stopping a line mid-block leaves the
     * speaker cone off zero and that step is an audible tick. Firing this at {@link #FADE_OUT} leaves
     * six tenths of a second before the application actually goes, which is room for that fade to
     * finish. Doing both at the end would fade into a process that has already exited.
     *
     * @param action the callback; may be {@code null}
     */
    public void setOnFading(Runnable action) {
        this.onFading = action;
    }

    /**
     * Sets what to run once the eject has finished and the screen is black.
     *
     * <p>This is the application's cue to actually go. It fires from the frame loop, so it only
     * happens on a screen that was genuinely drawn - a shutdown that exited before its own animation
     * had run would be the frozen dock this replaced, with an extra class in front of it.
     *
     * @param action the callback; may be {@code null}
     */
    public void setOnFinished(Runnable action) {
        this.onFinished = action;
    }

    /** @return how far through the eject, 0 to 1 */
    public double ejectProgress() {
        return ejectProgress;
    }

    /** Starts the eject. */
    public void start() {
        if (timer != null) {
            return;
        }
        startedAt = now();
        timer = new AnimationTimer() {
            @Override
            public void handle(long nanos) {
                advance(now());
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
     * Moves the eject on to wherever the clock has got to, firing each callback exactly once.
     *
     * @param seconds the wall clock, in seconds
     */
    private void advance(double seconds) {
        ejectProgress = Math.clamp((seconds - startedAt) / EJECT_SECONDS, 0, 1);
        if (!faded && ejectProgress >= FADE_OUT) {
            faded = true;
            Runnable fading = onFading;
            if (fading != null) {
                fading.run();
            }
        }
        if (!finished && ejectProgress >= 1) {
            finished = true;
            Runnable done = onFinished;
            if (done != null) {
                done.run();
            }
        }
        requestLayout();
    }

    /**
     * Draws the screen at a stated point in the eject, without a running clock.
     *
     * <p>Unphotographable otherwise, for the reason {@code BootScreen.previewShow} and
     * {@code MiniPlayerView.previewAt} both exist: the smoke test holds the interface thread, so no
     * pulse arrives and the frame loop above never ticks. <strong>It runs none of the callbacks</strong>
     * - a screenshot must not make a noise and must certainly not exit the application.
     *
     * @param eject how far through the eject, 0 to 1; the bar's sweep is derived from it, so the two
     *              cannot be photographed out of step with each other
     */
    public void previewAt(double eject) {
        ejectProgress = Math.clamp(eject, 0, 1);
        startedAt = now() - ejectProgress * EJECT_SECONDS;
        requestLayout();
        applyCss();
        layout();
    }

    // ------------------------------------------------------------------
    // The envelopes - pure functions of how far through the eject it is
    // ------------------------------------------------------------------

    /**
     * How far out of the slot the cartridge is.
     *
     * <p>Eased through {@code BootScreen.ramp}, so it leaves the slot and arrives at its hover without
     * a corner at either end - the same smoothstep every fade on the boot screen uses.
     *
     * @param eject how far through the eject, 0 to 1
     * @return 0 for seated exactly where the boot screen left it - <em>not</em> swallowed whole, see
     *         the class comment - and 1 for hovering clear of the slot
     */
    static double emergence(double eject) {
        return BootScreen.ramp(eject, EJECT_IN, EJECT_OUT);
    }

    /**
     * How far through the tear the screen is.
     *
     * <p>Linear rather than eased, unlike everything else here, and that is the point of it: a signal
     * breaking up has no easing in it. What decays is the displacement itself, inside
     * {@code BootScreen.tearOffset}.
     *
     * @param eject how far through the eject, 0 to 1
     * @return 0 at the instant the contact breaks, 1 once the picture has settled
     */
    static double tearProgress(double eject) {
        return Math.clamp(eject / GLITCH_OUT, 0, 1);
    }

    /**
     * How visible the name is on the cartridge's own label.
     *
     * <p><strong>Exactly the complement of {@link #splashAlpha}</strong>, which is what keeps this
     * screen's own rule true now that the cartridge is on screen from the first frame: the name is
     * never in two places at once, so as it leaves the middle of the screen it arrives on the label.
     * Written as the complement rather than as a second ramp because two ramps that were meant to add
     * to one are two numbers free to drift, and the way they would drift is the title appearing twice.
     *
     * @param eject how far through the eject, 0 to 1
     * @return opacity, 0 to 1
     */
    static double plateAlpha(double eject) {
        return 1 - splashAlpha(eject);
    }

    /**
     * How visible the splash is.
     *
     * <p>Full until the cartridge starts to move and gone shortly after, which is what keeps the name
     * off the screen at the moment it is back on the label. The boot screen's rule, run backwards.
     *
     * @param eject how far through the eject, 0 to 1
     * @return opacity, 0 to 1
     */
    static double splashAlpha(double eject) {
        return 1 - BootScreen.ramp(eject, EJECT_IN, EJECT_IN + SPLASH_HANDOVER);
    }

    /**
     * How much of the screen has gone to black.
     *
     * @param eject how far through the eject, 0 to 1
     * @return 0 for none, 1 for entirely black
     */
    static double blackout(double eject) {
        return BootScreen.ramp(eject, FADE_OUT, 1);
    }

    /**
     * The moments of the eject, and the instant in each one worth photographing.
     *
     * <p>Exists for the reason {@code BootScreen.Movement} does: the list of moments belongs beside
     * the timings rather than copied into the smoke test where the two are free to drift, and the
     * timing constants stay package-private. Three shots rather than one because there are three
     * things on this screen and <strong>no two of them are ever there together</strong> - the tear is
     * over before the cartridge moves, and the name is on the screen or on the label and never both -
     * so a single picture would look like whichever two it missed had failed to draw.
     *
     * <p>Each instant is the <strong>middle</strong> of its moment. Every one of these is a fade or a
     * travel, and a still of the edge of either is a still of nothing happening.
     */
    public enum Moment {

        /** The contact breaking: the picture torn, the cartridge not yet moving. */
        GLITCH("shutdown-glitch", GLITCH_OUT / 2),

        /** The name across the screen, the picture settled, the cartridge still seated. */
        SPLASH("shutdown", (GLITCH_OUT + EJECT_IN) / 2),

        /** The cartridge visibly on its way out, with the name back on its own label. */
        EJECT("shutdown-eject", (EJECT_IN + EJECT_OUT) / 2);

        private final String label;
        private final double instant;

        Moment(String label, double instant) {
            this.label = label;
            this.instant = instant;
        }

        /** @return a name for a screenshot file */
        public String label() {
            return label;
        }

        /** @return how far through the eject this moment is best seen, 0 to 1 */
        public double instant() {
            return instant;
        }
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

        double mouthY = Math.round(height * SLOT_Y_SHARE);
        double cartridgeHeight = Math.round(height * CARTRIDGE_HEIGHT_SHARE);
        double cartridgeWidth = Math.round(cartridgeHeight * aspect());
        double mouthWidth = Math.round(cartridgeWidth * inletShare());

        // The hole itself, a shade darker than the room so it reads as a recess even on black.
        double mouthX = Math.round((width - mouthWidth) / 2);
        gc.setFill(palette.shaded(PaletteRole.SHADOW, 0.5));
        gc.fillRect(mouthX, mouthY, mouthWidth, MOUTH_HEIGHT);

        drawCartridge(gc, palette, width, mouthY, cartridgeWidth, cartridgeHeight);
        drawLip(gc, palette, width, height, mouthX, mouthY, mouthWidth);

        double barWidth = Math.round(Math.min(width * 0.42, 520));
        double barHeight = 22;
        double barX = Math.round((width - barWidth) / 2);
        double barY = Math.round(height * BAR_Y_SHARE);

        gc.setTextAlign(TextAlignment.CENTER);
        drawSplash(gc, width, height, palette);

        gc.setFont(Fonts.pixel(PROMPT_SIZE));
        gc.setFill(palette.color(PaletteRole.PRIMARY));
        gc.fillText("SHUTTING DOWN", Math.round(width / 2), Math.round(height * 0.10));

        drawSweep(gc, palette, barX, barY, barWidth, barHeight);

        String reporting = status;
        if (!reporting.isEmpty()) {
            // ACCENT, as on the boot screen and for the same reason: this is the one line that
            // changes while the bar runs, and it is the whole evidence that anything is happening.
            gc.setFont(Fonts.pixel(HINT_SIZE));
            gc.setFill(palette.color(PaletteRole.ACCENT));
            gc.fillText(reporting, Math.round(width / 2), Math.round(barY + barHeight + 24));
        }

        // **The contact breaking, over the whole picture and under the glass.** The same tear the boot
        // screen draws - one implementation, see BootScreen.drawTear - because a cartridge arriving in
        // a live slot and a cartridge being taken out of one are the same electrical event twice. What
        // differs is only that this goes *over* a screen that is already drawn rather than replacing
        // it: the signal breaks up whatever was being shown, which here is the name and the bar.
        //
        // Under the glass rather than over it, for the reason drawFront gives on the boot screen: the
        // tube's own shape is a property of the screen and not of what is on it, so a display that
        // squared off its corners for half a second at the moment it was announcing what it is would
        // be changing shape in front of the one person looking at it.
        BootScreen.drawTear(gc, palette, width, height, tearProgress(ejectProgress),
                BootScreen.tearFrame(tearProgress(ejectProgress), GLITCH_OUT * EJECT_SECONDS));

        // The glass over the finished picture, then the blackout over the glass - the same order the
        // boot screen ends on, and for the same reason: a sync roll still drifting down a screen that
        // has finished fading would be the display outliving the fade.
        crt.draw(gc, width, height, now(), palette);

        double gone = blackout(ejectProgress);
        if (gone > 0) {
            gc.setFill(palette.color(PaletteRole.SHADOW, gone));
            gc.fillRect(0, 0, width, height);
        }
    }

    /**
     * The cartridge rising out of the slot, with the name back on its own label.
     *
     * <p><strong>It starts seated rather than swallowed</strong>, at the y the boot screen's own
     * arithmetic leaves it at when {@code insertion} reaches 1 - which is {@code SEAT_SHARE} of it
     * inside the machine and the rest still above the lip. That is where the last thing the user did
     * with this object put it, and it is what makes the two screens one continuous cartridge rather
     * than two animations of one. Beginning below the mouth instead spends the first third of the
     * travel climbing back to a position nobody ever moved it away from.
     *
     * <p>It ends hovering {@link #HOVER_GAP} above the mouth, which is exactly where the boot
     * screen's starts. Scaled <em>smoothly</em>, which is not the exception ground rule 8 forbids: the
     * cartridge is a 500x575 illustration with 1830 colours and per-pixel grain, in the same category
     * as album art. The magenta stand-in is flat blocks and is drawn hard, which is what
     * {@code isPlaceholder} decides.
     */
    private void drawCartridge(GraphicsContext gc, Palette palette, double width, double mouthY,
            double cartridgeWidth, double cartridgeHeight) {
        double out = emergence(ejectProgress);
        gc.setImageSmoothing(!sheet.isPlaceholder());

        // Asked of the boot screen rather than worked out again here, so the eject starts at exactly
        // the pixel the insert finished on and replacement artwork that seats deeper seats deeper at
        // both ends of the session. See BootScreen.seatedY.
        double seated = BootScreen.seatedY(mouthY, cartridgeHeight);
        // The hover is this screen's own, and deliberately: it is where the cartridge is handed back
        // to, not where it was picked up from, and this screen has a bar and a caption under the slot
        // that the boot screen does not.
        double hovering = mouthY - HOVER_GAP - cartridgeHeight;
        double x = Math.round((width - cartridgeWidth) / 2);
        double y = Math.round(seated + (hovering - seated) * out);

        Rectangle2D frame = sheet.viewport(0);
        gc.drawImage(sheet.image(), frame.getMinX(), frame.getMinY(),
                frame.getWidth(), frame.getHeight(), x, y, cartridgeWidth, cartridgeHeight);
        gc.setImageSmoothing(false);

        drawPlate(gc, palette, x, y, cartridgeWidth, cartridgeHeight, plateAlpha(ejectProgress));
    }

    /**
     * Prints the application's name on the cartridge's own label as it comes out.
     *
     * <p>Placed by measuring the artwork rather than by four numbers copied out of an image editor,
     * and sized and wrapped by {@code BootScreen}'s own methods - so the name breaks in exactly the
     * same places on the way out as it did on the way in. A second idea of how to lay it out would
     * show up as the title being hyphenated differently at the two ends of the same session.
     *
     * <p><strong>It fades in as the splash fades out</strong>, rather than being drawn whenever the
     * cartridge is. That is forced by the cartridge now starting on screen: the label is 68% of the
     * artwork's height and most of it is above the lip from the first frame, so drawn unconditionally
     * the name would be on the screen and on the label at the same instant - which is the one thing
     * this screen and the boot screen both promise never happens. See {@link #plateAlpha}.
     *
     * @param alpha how visible the name on the label is, 0 to 1
     */
    private void drawPlate(GraphicsContext gc, Palette palette, double cartridgeX, double cartridgeY,
            double cartridgeWidth, double cartridgeHeight, double alpha) {
        if (alpha <= 0) {
            return;
        }
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
        double size = BootScreen.labelFontSize(textWidth, labelHeight - 2 * inset,
                AppConfig.APP_NAME);
        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME,
                (int) Math.floor(textWidth / size));

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Fonts.pixel(size));
        gc.setFill(palette.color(PaletteRole.PRIMARY, alpha));

        double lineHeight = size * 1.4;
        double blockHeight = lines.size() * lineHeight;
        double centreX = x + labelWidth / 2;
        double first = y + (labelHeight - blockHeight) / 2 + size;
        for (int i = 0; i < lines.size(); i++) {
            gc.fillText(lines.get(i), Math.round(centreX), Math.round(first + i * lineHeight));
        }

        gc.setFont(Fonts.pixel(Math.max(5, size - 3)));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM, alpha));
        gc.fillText("v" + AppConfig.APP_VERSION, Math.round(centreX),
                Math.round(y + labelHeight - inset));
    }

    /**
     * The slot's near lip, which is what the cartridge climbs out of.
     *
     * <p>Everything below the mouth is the room again, drawn in front of the cartridge so it emerges
     * rather than slides across. On a dark screen this is invisible as a shape, which is the effect.
     */
    private void drawLip(GraphicsContext gc, Palette palette, double width, double height,
            double mouthX, double mouthY, double mouthWidth) {
        gc.setFill(palette.color(PaletteRole.SHADOW));
        gc.fillRect(0, mouthY, mouthX, height - mouthY);
        gc.fillRect(mouthX + mouthWidth, mouthY, width - mouthX - mouthWidth, height - mouthY);
        gc.fillRect(0, mouthY + MOUTH_HEIGHT, width, height - mouthY - MOUTH_HEIGHT);

        // The rim cools from HIGHLIGHT back to OUTLINE as the cartridge leaves - the exact reverse of
        // the boot screen's, where it warms as the cartridge goes in. The machine letting go.
        gc.setStroke(palette.color(PaletteRole.HIGHLIGHT)
                .interpolate(palette.color(PaletteRole.OUTLINE), emergence(ejectProgress)));
        gc.setLineWidth(RIM);
        gc.strokeRect(mouthX - RIM / 2, mouthY - RIM / 2, mouthWidth + RIM, MOUTH_HEIGHT + RIM);
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
     * Splashes the name above the slot, matching the boot screen's, and takes it away as the
     * cartridge arrives to carry it instead.
     *
     * <p>Smaller than the boot screen's on purpose. This is a goodbye rather than an arrival, and the
     * two screens sitting at the same weight would make closing the application feel like as much of
     * an event as opening it.
     */
    private void drawSplash(GraphicsContext gc, double width, double height, Palette palette) {
        double alpha = splashAlpha(ejectProgress);
        if (alpha <= 0) {
            return;
        }
        double size = splashFontSize(width);
        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME,
                (int) Math.floor(width * SPLASH_WIDTH_SHARE / size));
        double lineHeight = size * 1.35;
        // Centred as a block between the caption at the top and the slot below, so adding or losing a
        // line grows it evenly in both directions rather than pushing it into one of them.
        double centreY = height * 0.24;
        double first = centreY - lines.size() * lineHeight / 2 + size;

        gc.setFont(Fonts.pixel(size));
        gc.setFill(palette.color(PaletteRole.TEXT_DIM, alpha));
        for (int i = 0; i < lines.size(); i++) {
            gc.fillText(lines.get(i), Math.round(width / 2),
                    Math.round(first + i * lineHeight));
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

    /** @return the cartridge's width over its height, or a sane shape when there is no artwork */
    private double aspect() {
        double frameHeight = sheet.frameHeight();
        return frameHeight > 0 ? sheet.frameWidth() / frameHeight : 0.87;
    }

    /**
     * How wide the mouth is against the cartridge.
     *
     * <p>Measured off the artwork rather than written down, exactly as the boot screen's is: the
     * cartridge steps in towards its foot, and a mouth matched to its <em>widest</em> part would be
     * visibly wider than the part that actually comes out of it.
     */
    private double inletShare() {
        return sheet.footprint(0)
                .map(foot -> foot.getWidth() / sheet.frameWidth())
                .orElse(1d);
    }

    /** @return the wall clock in seconds; nothing here is synchronised to audio */
    private static double now() {
        return System.nanoTime() / 1_000_000_000d;
    }
}
