package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.analysis.Beatmap;
import com.eia.superdwarfkart.analysis.BeatmapService;
import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.audio.Levels;
import com.eia.superdwarfkart.audio.SmoothClock;
import com.eia.superdwarfkart.assets.RacerFrame;
import com.eia.superdwarfkart.game.Course;
import com.eia.superdwarfkart.game.Entity;
import com.eia.superdwarfkart.game.EntityState;
import com.eia.superdwarfkart.game.Lane;
import com.eia.superdwarfkart.game.Obstacle;
import com.eia.superdwarfkart.game.RunnerGame;
import com.eia.superdwarfkart.game.ScoreEntry;
import com.eia.superdwarfkart.game.ScoreKeeper;
import com.eia.superdwarfkart.game.SpeedClass;
import com.eia.superdwarfkart.game.Star;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.persistence.ScoreRepository;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The three-lane runner: the course a song generates, driven in time with it.
 *
 * <p><strong>Seen from behind the kart, with the road running away to a horizon.</strong> That is
 * the Super Circuit shot the whole application is styled after, and it is also the only projection
 * in which the lookahead is legible: an entity due in a second and a half is <em>visibly</em> a
 * second and a half away, up the road, and the player reads the course rather than reacting to
 * things appearing at the edge of the screen.
 *
 * <p>Perspective and pixel art do not naturally get on. A sprite scaled by 1.73 to sit "correctly"
 * in depth is mush, and it is mush in a way that reads as bad artwork rather than as a bad
 * decision. So the depth is real - {@code y = horizon + C/z}, the road narrows as {@code 1/z} and
 * so does the lane spacing - but a <strong>sprite only ever draws at 1x, 2x, 3x or 4x</strong>,
 * snapping between them as it approaches. That is exactly what the hardware this look comes from
 * did, and it is why it looks like a GBA game instead of a 3D engine with the filtering switched
 * off.
 *
 * <p><strong>Every moving thing here is a function of {@code audioSource.position()}.</strong> The
 * entities, the scrolling road, the beat flash and the star countdown all come off the playback
 * clock through {@link RunnerGame#update(double)}; nothing accumulates a frame delta. A dropped
 * frame therefore costs a frame and not a permanent offset, and pausing stops the road because the
 * clock stops rather than because anything was told to stop.
 *
 * <p>The game logic is not in this class. Where the racer is, what hit him and what the score is
 * all live in {@code game/}, which has no window and is tested without one; this file decides
 * where that state is drawn.
 */
public class RunnerView extends BorderPane {

    // ------------------------------------------------------------------
    // Projection
    // ------------------------------------------------------------------

    /** Where the horizon sits, as a fraction of the canvas height. */
    private static final double HORIZON_FRACTION = 0.30;

    /** How many hard steps the sky is posterised into. */
    private static final int SKY_BANDS = 5;

    /**
     * How much the road is foreshortened, as an exponent on time.
     *
     * <p><strong>This is the number that decides whether the game reads as a rhythm game.</strong>
     * Everything on the road - entities and surface bands alike - sits at
     * {@code screenFraction = progress^BIAS}, where progress is purely "how much of the travel time
     * has gone". At 1.0 the picture is Piano Tiles exactly: constant screen speed, and where a thing
     * is <em>is</em> how long until it arrives. At 2.0 it is a true perspective divide.
     *
     * <p>The first version used a real {@code 1/z} divide with the depth linear in time, and it was
     * unplayable. Half the travel was spent inside the top fifth of the road and the last tenth of
     * it covered nearly half the screen, so an entity hung around the horizon and then whooshed -
     * which is exactly the "everything pools at the horizon" it looked like. Nothing about the
     * motion told you when a thing would arrive, which is the only job this projection has.
     *
     * <p>1.25 keeps a visible foreshortening while leaving screen position near enough proportional
     * to time to be read as timing. Raise it towards 2 for more depth and less readability.
     */
    private static final double PERSPECTIVE_BIAS = 1.25;

    /** Room left under the racer for the road to run off the bottom of the canvas. */
    private static final double BOTTOM_MARGIN = 26;

    /** The road's half-width at the racer, as a fraction of the canvas width. */
    private static final double ROAD_HALF_FRACTION = 0.40;

    /** Largest integer magnification a sprite is drawn at, which is what it reaches at the racer. */
    private static final int MAX_SPRITE_SCALE = 4;

    /**
     * Smallest magnification an entity is drawn at, right up against the horizon.
     *
     * <p>Below about a third the artwork stops being readable as a coin or a bump and becomes a
     * smudge, which defeats the point of a lookahead the player is supposed to read.
     */
    private static final double MIN_ENTITY_SCALE = 0.4;

    /**
     * How many surface bands cover the whole lookahead.
     *
     * <p>Spaced in <em>time</em>, not in depth, so a band travels exactly as an entity does and the
     * two can never slide against each other. It also means the road visibly scrolls faster at the
     * quick classes for free, because their lookahead is shorter.
     */
    private static final double BANDS_PER_LOOKAHEAD = 9;

    /** Width of the kerb down each side of the road, at the racer, in pixels. */
    private static final double KERB_WIDTH_NEAR = 22;

    /** Width of a lane line at the racer, in pixels. */
    private static final double LANE_LINE_WIDTH_NEAR = 8;

    /**
     * How far the lit road band is lifted towards the text colour.
     *
     * <p>Not a colour: a distance between two roles. The palette is deliberately dark and every
     * surface role in it sits within a few steps of every other, so a road drawn straight from
     * {@code SURFACE} and {@code SURFACE_RAISED} is a correct shape nobody can see. Expressed this
     * way it stays right in a light mood too, where the same mix darkens rather than lightens.
     */
    private static final double ROAD_LIFT = 0.22;

    // ------------------------------------------------------------------
    // Feel
    // ------------------------------------------------------------------

    /**
     * How long the beat flash takes to fade, in seconds.
     *
     * <p><strong>The flash is the only thing on the beat, and the road never is.</strong> An earlier
     * version swelled the road a few percent on every strong beat and it was a mistake: the whole
     * picture pumped, the kart appeared to lurch, and the motion stopped reading as driving. Speed
     * is a constant, and what the music does is put things <em>on</em> the road - coins, stars, and
     * a wall on the big hits. Do not put the geometry back on the beat.
     */
    private static final double PULSE_SECONDS = 0.18;

    /**
     * How much of one beat the effect occupies, when the tempo is known.
     *
     * <p>Comfortably over half, so the beat is felt rather than glimpsed, and comfortably under
     * one, so the screen is back to normal before the next strike lands.
     */
    private static final double PULSE_BEAT_FRACTION = 0.62;

    /** Shortest the beat effect may be, however fast the track. */
    private static final double PULSE_MIN_SECONDS = 0.14;

    /** Longest the beat effect may be, however slow the track. */
    private static final double PULSE_MAX_SECONDS = 0.38;

    /**
     * How high off the road a jump lifts the sprite, in pixels.
     *
     * <p>Deliberately more than looks physically sensible. The kart is nearly two hundred pixels
     * tall on the full stage, and at the fifty-odd pixels this started at the jump was a bob that
     * the player could not tell from the sprite's own idle - the control worked and read as broken.
     */
    private static final double JUMP_LIFT = 130;

    /** Frames per second the star sheet is animated at. */
    private static final double STAR_FPS = 12;

    /** How fast a caption rises off the road, in pixels per second. */
    private static final double EFFECT_RISE = 90;

    /** Flashes per second while the racer is protected after a bump. */
    private static final double INVULNERABLE_BLINK_HZ = 8;

    /** How long a collected coin's pop lasts, in seconds. */
    private static final double POP_SECONDS = 0.4;

    /** How long a broken obstacle's explosion lasts, in seconds. */
    private static final double BREAK_SECONDS = 0.6;

    /** How long a bump - explosion, then the coins it cost raining down - lasts, in seconds. */
    private static final double HIT_SECONDS = RunnerGame.EFFECT_SECONDS;

    /** How hard a dropped coin is thrown upwards, in pixels per second. */
    private static final double COIN_THROW_SPEED = 340;

    /** How hard a dropped coin is pulled back down, in pixels per second per second. */
    private static final double COIN_GRAVITY = 1100;

    /** How far to either side the dropped coins scatter, in pixels per second. */
    private static final double COIN_SCATTER_SPEED = 210;

    /**
     * How far down the course a wall must have come before the jump prompt appears.
     *
     * <p>As a fraction of the travel time rather than as seconds, so it warns the same distance
     * ahead at every speed class - a fixed number of seconds would be a hint at 50cc and an
     * apology at 200cc.
     */
    private static final double WALL_WARNING_PROGRESS = 0.45;

    /** How long the controls hint stays on screen after the music starts, in seconds. */
    private static final double CONTROLS_SECONDS = 3.5;

    /** How much of that is spent fading out. */
    private static final double CONTROLS_FADE_SECONDS = 1.0;

    /**
     * How much the whole screen darkens on a strong beat, at the instant of it.
     *
     * <p>A wash over the finished picture, never a change to the picture - which is the same rule
     * {@link #PULSE_SECONDS} states for the horizon flash and for the same reason. Darkening reads
     * as the room dipping on the beat; brightening washes out the road and hides the very entities
     * the beat has just placed on it.
     */
    private static final double BEAT_WASH_ALPHA = 0.42;

    /**
     * How far the screen lifts <em>above</em> normal as the beat lets go.
     *
     * <p>The wash used to only darken, which reads as the picture being dimmed rather than as the
     * room reacting - it has a beginning and no end. Following the dip with a brief lift past
     * normal gives the beat a release as well as a strike, and it is the release that makes the
     * whole thing legible out of the corner of an eye. Smaller than the dip: brightening a dark
     * palette washes the road out much faster than darkening it hides anything.
     */
    private static final double BEAT_LIFT_ALPHA = 0.16;

    /**
     * How far the camera punches in on a strong beat, as a fraction.
     *
     * <p>The ceiling is the point where the eye stops reading this as the camera moving and starts
     * reading it as the road changing shape, which lands straight back in the trap described on
     * {@link #PULSE_SECONDS}. That threshold is well above this: what keeps the two apart is that
     * a camera move scales the lane lines, the kerbs and the entities by the same factor, so the
     * picture stays internally consistent however far it is pushed. Around a tenth it starts to
     * feel like a lurch rather than a punch, so this is the practical limit rather than a
     * correctness one.
     */
    private static final double BEAT_ZOOM = 0.055;

    /** How long a pickup lights the screen for, in seconds. */
    private static final double PICKUP_FLASH_SECONDS = 0.28;

    /** How strongly a pickup lights the screen. */
    private static final double PICKUP_FLASH_ALPHA = 0.24;

    /** How long a bump lights the screen for, in seconds. */
    private static final double HIT_FLASH_SECONDS = 0.7;

    /** How strongly a bump lights the screen at its brightest. */
    private static final double HIT_FLASH_ALPHA = 0.38;

    /**
     * How many times a bump's flash pulses before it goes.
     *
     * <p>A single fade reads as a change in lighting; a few beats of it read as an alarm, which is
     * what a bump is. Three is enough to register at {@value #HIT_FLASH_SECONDS} seconds without
     * becoming a strobe - and the whole thing is over well inside the protected spell, so it never
     * outlasts the state it is announcing.
     */
    private static final double HIT_FLASH_PULSES = 3;

    /** How tall a wall's hazard band is at the racer, in pixels. */
    private static final double BARRIER_HEIGHT_NEAR = 34;

    /** How wide one hazard stripe is at the racer, in pixels. */
    private static final double BARRIER_BLOCK_NEAR = 40;

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** Body size on the canvas. Whole pixels: a fractional pixel font is blurry. */
    private static final double TEXT_SIZE = 8;

    /** Size of the head-up readouts. */
    private static final double HUD_SIZE = 10;

    /** Size of the banner shown while there is no course to drive. */
    private static final double BANNER_SIZE = 12;

    /** Size of the speed class plate's text, the largest thing in the head-up display. */
    private static final double CLASS_SIZE = 16;

    /** Height of the speed class plate, text plus its bevel. */
    private static final double CLASS_PLATE_HEIGHT = CLASS_SIZE + 14;

    /** Padding inside the canvas for the head-up display. */
    private static final double HUD_PADDING = 14;

    /**
     * Set this to {@code true} to measure the frame loop from launch.
     *
     * <p>{@code F3} does the same thing from inside a running race, which is the more useful of the
     * two: a stutter that is reported as constant usually turns out to have a cause the user can
     * point at, and switching the readout on while it is happening is how that gets found.
     */
    static final String DIAGNOSTICS_PROPERTY = "sdmk.diag";

    private final AppState state;
    private final AssetRegistry assets;
    private final BeatmapService beatmaps;
    private final PlaybackEngine engine;
    private final Levels levels;
    private final ScoreRepository scores;

    private final RunnerGame game = new RunnerGame();
    private final SmoothClock clock = new SmoothClock();
    private final Canvas canvas = new Canvas();

    /** Whether the music was playing last frame, so starting again is noticed exactly once. */
    private boolean wasPlaying;

    /**
     * Frame pacing, clock behaviour and jump timing, or {@code null} when nothing is being measured.
     *
     * <p>Switched on with {@code -Dsdmk.diag=true} and with {@code F3} while the road is on screen.
     * Null rather than a no-op object, so the ordinary frame runs the code it has always run.
     */
    private RunnerDiagnostics diagnostics =
            Boolean.getBoolean(DIAGNOSTICS_PROPERTY) ? new RunnerDiagnostics() : null;

    /** Whether the readout is drawn as well as printed. */
    private boolean diagnosticsOverlay = Boolean.getBoolean(DIAGNOSTICS_PROPERTY);

    private Runnable onRaceStarted;

    /** Playback position the controls hint stops being drawn at, in seconds. */
    private double controlsShownUntil = Double.NEGATIVE_INFINITY;

    // ------------------------------------------------------------------
    // The screen flash
    // ------------------------------------------------------------------

    /**
     * When the current screen flash began, in playback seconds, or negative infinity for none.
     *
     * <p>Timed from the game clock rather than from wall time, so it stops when the music does and
     * a flash cannot outlive a pause - the same reason everything else here reads that clock.
     */
    private double flashStartedAt = Double.NEGATIVE_INFINITY;

    private PaletteRole flashRole = PaletteRole.PRIMARY;
    private double flashSeconds;
    private double flashAlpha;

    /** How many times the flash pulses on its way out; 0 is a plain fade. */
    private double flashPulses;

    /**
     * Set while the smoke test is drawing a still of a moment that is not being played.
     *
     * <p>Only the paused banner reads it. Everything else on screen is the same whether the music
     * is running or not, but a box saying "paused" over a preview of a running course would be
     * both true and misleading in the one picture that exists to show the course running.
     */
    private boolean previewing;

    private final ToggleGroup classGroup = new ToggleGroup();
    private final Map<SpeedClass, ToggleButton> classButtons = new EnumMap<>(SpeedClass.class);
    private final Label courseSummary = new Label();

    private AnimationTimer timer;

    /** What the course under the racer was built from, so it is rebuilt only when it must be. */
    private String builtForSongId;
    private SpeedClass builtForClass;
    private String builtFromHash;

    /**
     * The analysis the course on screen was built from.
     *
     * <p>Held rather than read back off the service per frame, because the two can disagree: the
     * service follows whatever became current and may already be analysing the <em>next</em> song
     * while this one is still playing. Flashing the road to a beatmap the speakers are not playing
     * is exactly the kind of desynchronisation this milestone exists to avoid.
     */
    private Beatmap activeBeatmap = Beatmap.EMPTY;

    /** The song the current run belongs to, so its score is filed under the right one. */
    private Song running;

    /** The best stored run for the song and class on screen, looked up on a rebuild, not per frame. */
    private ScoreEntry best;

    /**
     * Builds the runner.
     *
     * @param state     the shared state holding the current song, racer and speed class; must not
     *                  be {@code null}
     * @param assets    where sprites come from; must not be {@code null}
     * @param beatmaps  the analysis the course is generated from; must not be {@code null}
     * @param engine    the playback clock everything is driven by; must not be {@code null}
     * @param levels    the per-channel levels the lane edges glow with; must not be {@code null}
     * @param scores    where a finished run is recorded; must not be {@code null}
     */
    public RunnerView(AppState state, AssetRegistry assets, BeatmapService beatmaps,
                      PlaybackEngine engine, Levels levels, ScoreRepository scores) {
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.assets = Objects.requireNonNull(assets, "assets must not be null");
        this.beatmaps = Objects.requireNonNull(beatmaps, "beatmaps must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.levels = Objects.requireNonNull(levels, "levels must not be null");
        this.scores = Objects.requireNonNull(scores, "scores must not be null");

        getStyleClass().add("runner-view");
        canvas.setManaged(false);
        Pane holder = new CanvasHolder();
        holder.getChildren().add(canvas);
        setCenter(holder);
        setTop(buildControls());

        // Always registered, and it asks whether anything is measuring rather than being added and
        // removed with the readout: a listener attached half way through a run would report its
        // first jump against a press it never saw.
        game.addListener(new com.eia.superdwarfkart.game.RunnerListener() {
            @Override
            public void coinCollected(com.eia.superdwarfkart.game.Coin coin, ScoreKeeper score) {
                lightScreen(PaletteRole.PRIMARY, PICKUP_FLASH_SECONDS, PICKUP_FLASH_ALPHA, 0);
            }

            @Override
            public void starCollected(Star star, ScoreKeeper score) {
                lightScreen(PaletteRole.PRIMARY, PICKUP_FLASH_SECONDS * 2, PICKUP_FLASH_ALPHA, 0);
            }

            @Override
            public void obstacleBroken(Obstacle obstacle, ScoreKeeper score) {
                lightScreen(PaletteRole.PRIMARY, PICKUP_FLASH_SECONDS, PICKUP_FLASH_ALPHA, 0);
            }

            @Override
            public void obstacleHit(Obstacle obstacle, ScoreKeeper score) {
                lightScreen(PaletteRole.NEGATIVE, HIT_FLASH_SECONDS, HIT_FLASH_ALPHA,
                        HIT_FLASH_PULSES);
                if (diagnostics != null) {
                    diagnostics.obstacleResolved(obstacle, EntityState.HIT);
                }
            }

            @Override
            public void obstacleCleared(Obstacle obstacle) {
                if (diagnostics != null) {
                    diagnostics.obstacleResolved(obstacle, EntityState.CLEARED);
                }
            }
        });

        installInput();
    }

    /**
     * Turns the frame-pacing readout on and off.
     *
     * <p>Three states rather than two: off, printed, and printed with the overlay drawn over the
     * road. The middle one exists because the overlay is itself several dozen glyphs a frame, and a
     * measurement of the frame rate that costs frames is not one worth reading.
     */
    private void toggleDiagnostics() {
        if (diagnostics == null) {
            diagnostics = new RunnerDiagnostics();
            diagnosticsOverlay = true;
        } else if (diagnosticsOverlay) {
            diagnosticsOverlay = false;
        } else {
            diagnostics = null;
        }
        System.out.println("[diag] " + (diagnostics == null
                ? "off"
                : diagnosticsOverlay ? "on, overlay drawn" : "on, printed only"));
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    /**
     * Builds the strip above the road: the speed class selector, a restart, and what the course
     * holds.
     *
     * @return the control bar
     */
    private HBox buildControls() {
        Label caption = new Label("CLASS");
        caption.getStyleClass().add("bar-caption");

        HBox classes = new HBox(6);
        classes.setAlignment(Pos.CENTER_LEFT);
        for (SpeedClass speedClass : SpeedClass.values()) {
            classes.getChildren().add(classButton(speedClass));
        }
        selectButtonFor(state.getSpeedClass());

        Button restart = new Button("RESTART");
        restart.setTooltip(new Tooltip("Start this course again from the top"));
        restart.setOnAction(event -> {
            game.restart();
            // The buttons take focus when clicked, and the arrows belong to the kart.
            requestFocus();
        });

        courseSummary.getStyleClass().add("runner-summary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, caption, classes, restart, spacer, courseSummary);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.getStyleClass().add("runner-controls");
        return bar;
    }

    /**
     * @param speedClass the class the button selects
     * @return the toggle
     */
    private ToggleButton classButton(SpeedClass speedClass) {
        ToggleButton button = new ToggleButton(speedClass.displayName().toUpperCase());
        button.setToggleGroup(classGroup);
        button.getStyleClass().add("class-button");
        button.setTooltip(new Tooltip(speedClass.displayName()
                + "\nSpeed x" + speedClass.speedMultiplier()
                + "\n" + density(speedClass)
                + "\nCoins worth x" + speedClass.scoreMultiplier()));
        button.setOnAction(event -> {
            if (!button.isSelected()) {
                // Clicking the active class must not deselect it and leave nothing chosen.
                button.setSelected(true);
                return;
            }
            state.setSpeedClass(speedClass);
            requestFocus();
        });
        classButtons.put(speedClass, button);
        return button;
    }

    /**
     * @param speedClass the class to describe
     * @return one line saying what of the music it spends
     */
    private static String density(SpeedClass speedClass) {
        if (speedClass.usesIntermediateOnsets()) {
            return "Every strong beat and every onset between them";
        }
        return speedClass.beatInterval() == 1
                ? "Every strong beat"
                : "Every " + speedClass.beatInterval() + ordinalSuffix(speedClass.beatInterval())
                        + " strong beat";
    }

    private static String ordinalSuffix(int value) {
        return switch (value) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /**
     * @param speedClass the class now active
     */
    private void selectButtonFor(SpeedClass speedClass) {
        ToggleButton button = classButtons.get(speedClass);
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }
    }

    /**
     * Wires the driving controls.
     *
     * <p><strong>A handler on this node, not a filter on the scene</strong>, which is the same
     * split the rest of the application uses and for the same reason. The arrows already move the
     * running order and space already plays and pauses, both wired as scene <em>handlers</em> - and
     * a handler on the focused node runs before the scene's does. So while the road has focus the
     * arrows steer and space jumps, and the moment focus goes anywhere else they go back to being
     * the transport, without either side knowing about the other.
     */
    private void installInput() {
        // Added to the scene when this view is put on screen and taken off when it is removed. The
        // race view and the library swap places in the middle of the window, so being in the scene
        // graph at all is exactly the same question as being the view the user is looking at.
        sceneProperty().addListener((observable, was, now) -> {
            if (was != null) {
                was.removeEventFilter(KeyEvent.KEY_PRESSED, drivingKeys);
            }
            if (now != null) {
                now.addEventFilter(KeyEvent.KEY_PRESSED, drivingKeys);
            }
        });
    }

    /**
     * The driving keys.
     *
     * <p><strong>A scene filter, not a handler on this node, and that is the fix for a jump that
     * did nothing.</strong> The first version waited for the road to have keyboard focus, which is
     * a condition the user cannot see and had no reliable way to satisfy: {@code Tab} is bound to
     * the mode cycle application-wide, so focus could only be taken by clicking exactly the right
     * pixels, and any button pressed since took it away again. Pressing space then fell through to
     * the transport and paused the music instead of jumping - which looks precisely like a broken
     * control.
     *
     * <p>A filter runs before anything else and does not care what has focus, so while the road is
     * on screen the arrows steer and space jumps, full stop. The cost is that the transport
     * shortcuts are unavailable during a race; the buttons at the top of the window still work, and
     * F6 hands the keys back on the way out. A text field is still excused, in case one is ever put
     * on this screen.
     */
    private final javafx.event.EventHandler<KeyEvent> drivingKeys = event -> {
        if (event.getTarget() instanceof javafx.scene.control.TextInputControl) {
            return;
        }
        if (event.getCode() == KeyCode.F3) {
            // Deliberately above the playing check: the readout has to be reachable while the road
            // is stopped, which is when somebody is most likely to be looking for it.
            toggleDiagnostics();
            event.consume();
            return;
        }
        if (!isRunningWithAudio()) {
            // The music is stopped, so the run is stopped. Steering a frozen kart to line up an
            // obstacle that cannot reach it is not a control, it is an exploit - and space has to
            // reach the transport while paused, or the play key stops working exactly when it is
            // the only one the user wants.
            return;
        }
        switch (event.getCode()) {
            case LEFT, A -> {
                game.moveLeft();
                event.consume();
            }
            case RIGHT, D -> {
                game.moveRight();
                event.consume();
            }
            case SPACE, UP, W -> {
                game.jump();
                if (diagnostics != null) {
                    diagnostics.jumpPressed(game.now());
                }
                event.consume();
            }
            default -> {
                // Not a driving key; let it carry on to whatever else wanted it.
            }
        }
    };

    // ------------------------------------------------------------------
    // The frame loop
    // ------------------------------------------------------------------

    /** Begins driving the runner from the playback clock. */
    public void start() {
        if (timer != null) {
            return;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (diagnostics != null) {
                    diagnostics.frameStarted(now);
                }
                tick();
            }
        };
        timer.start();
    }

    /**
     * Stops the frame loop and files whatever the current run achieved.
     *
     * <p>Called on shutdown, and on leaving the runner for another view: a run abandoned by closing
     * the window is still a run, and the board only takes it if it beats what is there.
     */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        recordRun();
    }

    /** @return whether the frame loop is running */
    public boolean isRunning() {
        return timer != null;
    }

    /**
     * One frame: bring the course up to date, advance the run, repaint.
     *
     * <p>The position goes through {@link SmoothClock} rather than straight into the run. The sound
     * card reports whole buffers, so read per frame it stands still and then jumps, and a road
     * drawn off it stutters however fast the machine is - see that class for why smoothing it is
     * not the accumulated frame time this project forbids.
     *
     * <p><strong>Pausing the music pauses the game</strong>, and it does so by itself: the clock
     * stops because the card stops, so the entities stop, the road stops scrolling and the star
     * stops running out. Nothing is told to stop, which is why the two can never disagree.
     */
    private void tick() {
        if (diagnostics == null) {
            syncCourse();
            boolean playing = engine.isPlaying();
            if (playing != wasPlaying) {
                wasPlaying = playing;
                if (playing) {
                    onPlaybackResumed();
                }
            }
            game.update(clock.advance(engine.positionSeconds(), playing));
            redraw();
            return;
        }
        measuredTick();
    }

    /**
     * The same frame, timed in three parts.
     *
     * <p>Held apart from {@link #tick()} rather than folded into it with a null check per stage, so
     * the ordinary path carries no instrumentation at all - a measurement that changes what it
     * measures is worth less than no measurement.
     */
    private void measuredTick() {
        long a = System.nanoTime();
        syncCourse();
        long b = System.nanoTime();

        boolean playing = engine.isPlaying();
        if (playing != wasPlaying) {
            wasPlaying = playing;
            if (playing) {
                onPlaybackResumed();
            }
        }
        double raw = engine.positionSeconds();
        double smoothed = clock.advance(raw, playing);
        game.update(smoothed);
        long c = System.nanoTime();

        redraw();
        long d = System.nanoTime();

        diagnostics.tickCost(b - a, c - b, d - c);
        diagnostics.clock(raw, smoothed, clock.snaps());
        diagnostics.reportIfDue(smoothed);
    }

    /**
     * Asks the engine outright rather than reading what the last frame saw.
     *
     * <p>{@code wasPlaying} exists only to notice the <em>edge</em> when playback starts, and it is
     * updated by the frame loop - so anything else reading it is quietly asserting that a frame has
     * been drawn since the state last changed. That is untrue whenever the interface thread has
     * been busy, and it made the driving controls dead for the first frame after every resume. One
     * boolean off the audio source has no such window.
     *
     * @return whether the music, and therefore the run, is moving
     */
    private boolean isRunningWithAudio() {
        return engine.isPlaying();
    }

    /**
     * Called the moment playback starts again, so anything watching for it can react once.
     *
     * <p>Separate from the frame loop because it happens twice a song at most, and mixing a
     * once-per-song action into something that runs sixty times a second is how it ends up
     * happening sixty times a second.
     */
    private void onPlaybackResumed() {
        // Shown from here rather than from a song change, because this is the moment the player is
        // about to need them - and it is also the moment they are looking at the road.
        controlsShownUntil = game.now() + CONTROLS_SECONDS;
        if (onRaceStarted != null) {
            onRaceStarted.run();
        }
    }

    /**
     * Sets what happens the first time the music starts.
     *
     * <p>The application uses this to bring the road up when the user presses play, so the game
     * appears because a race began rather than because a function key was found.
     *
     * @param action what to run, or {@code null} for nothing
     */
    public void setOnRaceStarted(Runnable action) {
        this.onRaceStarted = action;
    }

    /**
     * Rebuilds the course when the song, the speed class or the available analysis has changed.
     *
     * <p>Checked every frame because all three can change without anything telling this view - the
     * beatmap in particular arrives on a background thread and is polled, exactly as the level
     * meters are polled. The check itself is three comparisons; the rebuild behind it happens once
     * per song.
     */
    private void syncCourse() {
        Song song = state.getCurrentSong();
        String songId = song == null ? null : song.getId();
        SpeedClass speedClass = state.getSpeedClass();
        selectButtonFor(speedClass);

        BeatmapService.Status status = beatmaps.status();
        boolean matches = song != null && sameFile(status.file(), song.getFilePath());
        Beatmap beatmap = matches && status.isReady() ? status.beatmap() : Beatmap.EMPTY;
        String hash = beatmap.isEmpty() ? null : beatmap.sourceHash();

        if (Objects.equals(songId, builtForSongId)
                && speedClass == builtForClass
                && Objects.equals(hash, builtFromHash)) {
            return;
        }

        // The song or the class is changing under a run in progress: file it before it is lost.
        if (!Objects.equals(songId, builtForSongId) || speedClass != builtForClass) {
            recordRun();
            running = song;
            // Easing from the old song's position into the new one's would animate a journey that
            // never happened - four minutes of road in a fifth of a second.
            clock.reset();
        }

        builtForSongId = songId;
        builtForClass = speedClass;
        builtFromHash = hash;
        activeBeatmap = beatmap;

        Course course = songId == null
                ? Course.empty("", speedClass)
                : Course.generate(songId, beatmap, speedClass);
        game.setCourse(course);
        best = songId == null ? null : scores.best(songId, speedClass).orElse(null);
        courseSummary.setText(course.isEmpty()
                ? "NO COURSE"
                : course.coinsAvailable() + " COINS  " + course.obstacleCount() + " BUMPS  "
                        + course.starCount() + " STARS");
    }

    /**
     * @param analysed the file the analysis is about, or {@code null}
     * @param playing  the file the current song plays from
     * @return whether they are the same file
     */
    private static boolean sameFile(Path analysed, Path playing) {
        return analysed != null && analysed.equals(playing);
    }

    /**
     * Files the current run on the score board, if there is one worth filing.
     *
     * <p>A course with no coins on it has nothing to be a fraction of, and a run that collected
     * nothing says only that the song was skipped - neither is a result. Everything else goes to
     * the board, which keeps it only if it beats what is already there.
     */
    private void recordRun() {
        ScoreKeeper score = game.score();
        if (running == null || !score.isRanked() || score.coinsCollected() == 0) {
            return;
        }
        ScoreEntry entry = ScoreEntry.of(running.getId(), score);
        if (scores.record(entry)) {
            best = entry;
        }
    }

    /** @return the run in progress, for the smoke test and for tests */
    public RunnerGame game() {
        return game;
    }

    /**
     * Draws the course as it would look at a moment in the track, without playing it.
     *
     * <p>For the screenshot the smoke test takes. Every course starts with a lead-in of its own
     * travel time - nothing may be due before the road has finished filling - so a picture taken at
     * the two or three seconds the smoke test has actually played is a picture of an empty road,
     * which says nothing about the one thing worth looking at.
     *
     * <p>Safe because the frame loop reads the clock afresh on its next tick: this moves the view
     * for one frame and the next real tick puts it back.
     *
     * @param seconds where in the track to draw
     */
    public void previewAt(double seconds) {
        previewing = true;
        try {
            syncCourse();
            game.update(seconds);
            redraw();
        } finally {
            previewing = false;
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Repaints the whole view. */
    public void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Pixel art must not be interpolated, at any scale. JavaFX interpolates by default.
        gc.setImageSmoothing(false);

        double now = game.now();
        double pulse = beatPulse(now);
        double horizonY = Math.round(height * HORIZON_FRACTION);
        double groundY = height - BOTTOM_MARGIN;
        double depth = groundY - horizonY;
        if (depth < 40) {
            // Too short to be a road. Draw the backdrop and the banner and stop.
            gc.setFill(color(PaletteRole.BACKGROUND));
            gc.fillRect(0, 0, width, height);
            drawBanner(gc, width, height);
            return;
        }

        // A constant. The road's width and its scroll rate are functions of the speed class and of
        // nothing else - see PULSE_SECONDS on why the beat must never reach the geometry.
        double halfNear = width * ROAD_HALF_FRACTION;

        // The camera punches in on the beat. Everything from here to the matching restore is drawn
        // through one uniform transform - see beatZoom for why that is not the thing this file
        // spends a paragraph forbidding.
        double zoom = beatZoom(pulse);
        boolean zooming = zoom != 1;
        if (zooming) {
            gc.save();
            gc.translate(width / 2, height / 2);
            gc.scale(zoom, zoom);
            gc.translate(-width / 2, -height / 2);
        }

        drawSky(gc, width, horizonY, pulse);
        drawRoad(gc, width, height, horizonY, depth, halfNear, now);
        drawEntities(gc, width, horizonY, depth, halfNear, groundY, now);
        drawRacer(gc, width, halfNear, groundY, now);
        // Over the kart: an explosion belongs in the player's face, not peeping out from behind
        // the sprite it just hit.
        drawEffects(gc, width, halfNear, groundY, now);

        if (zooming) {
            gc.restore();
        }

        // Over the whole picture and under the head-up display: the beat and the last thing that
        // happened are things the road is seen *through*, and neither may make a score unreadable.
        // Outside the zoom, so a full-canvas wash stays a full-canvas wash.
        drawBeatWash(gc, width, height, pulse);
        drawEventFlash(gc, width, height, now);

        drawHud(gc, width, height);
        drawControlsHint(gc, width, height, now);
        drawBanner(gc, width, height);

        if (diagnostics != null && diagnosticsOverlay) {
            diagnostics.draw(gc, HUD_PADDING, height - HUD_PADDING - 5 * 13, RunnerView::color);
        }
    }

    /**
     * Shows the controls in the middle of the road for a few seconds when the music starts.
     *
     * <p>The permanent line in the corner is a reminder for somebody who already knows; this is the
     * one moment somebody who does not is both looking at the road and about to need them. It fades
     * out on its own rather than waiting to be dismissed, so it never becomes something to close.
     *
     * @param gc     the context to draw into
     * @param width  canvas width
     * @param height canvas height
     * @param now    the playback position, in seconds
     */
    private void drawControlsHint(GraphicsContext gc, double width, double height, double now) {
        double left = controlsShownUntil - now;
        // Suppressed in a preview for the same reason the paused banner is: the smoke test jumps
        // the clock about, and a hint that is transient in play would be permanent in a still.
        if (left <= 0 || previewing) {
            return;
        }
        // Full strength for most of its life and then a fade, so it does not spend the whole time
        // half-visible over the course the player is trying to read.
        double fade = Math.clamp(left / CONTROLS_FADE_SECONDS, 0d, 1d);

        double keyWidth = 46;
        double keyHeight = 40;
        double gap = 12;
        double spaceWidth = keyWidth * 3 + gap * 2;
        double blockWidth = keyWidth * 2 + gap;
        double centerX = width / 2;
        double top = height / 2 - keyHeight;

        drawKeyCap(gc, centerX - blockWidth / 2, top, keyWidth, keyHeight, "<", fade);
        drawKeyCap(gc, centerX - blockWidth / 2 + keyWidth + gap, top, keyWidth, keyHeight, ">",
                fade);
        drawKeyCap(gc, centerX - spaceWidth / 2, top + keyHeight + gap, spaceWidth, keyHeight,
                "SPACE", fade);

        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(color(PaletteRole.TEXT_DIM, fade));
        String steer = "STEER";
        gc.fillText(steer, Math.round(centerX - textWidth(steer, TEXT_SIZE) / 2),
                Math.round(top - 10));
        String jump = "JUMP";
        gc.fillText(jump, Math.round(centerX - textWidth(jump, TEXT_SIZE) / 2),
                Math.round(top + keyHeight * 2 + gap + 18));
    }

    /**
     * Draws one key of the controls hint as a beveled block, the same physical-key convention the
     * stylesheet uses for buttons.
     *
     * @param gc     the context to draw into
     * @param x      left edge
     * @param y      top edge
     * @param w      width
     * @param h      height
     * @param label  what is written on it
     * @param fade   how opaque it still is
     */
    private static void drawKeyCap(GraphicsContext gc, double x, double y, double w, double h,
                                   String label, double fade) {
        gc.setFill(color(PaletteRole.SHADOW, 0.85 * fade));
        gc.fillRect(x, y, w, h);
        gc.setFill(color(PaletteRole.OUTLINE, fade));
        gc.fillRect(x, y, w, 3);
        gc.fillRect(x, y, 3, h);
        gc.setFill(color(PaletteRole.SHADOW, fade));
        gc.fillRect(x, y + h - 3, w, 3);
        gc.fillRect(x + w - 3, y, 3, h);

        double size = label.length() > 1 ? TEXT_SIZE : HUD_SIZE + 4;
        gc.setFont(Fonts.pixel(size));
        gc.setFill(color(PaletteRole.PRIMARY, fade));
        gc.fillText(label, Math.round(x + (w - textWidth(label, size)) / 2),
                Math.round(y + h / 2 + size / 2));
    }

    /**
     * How much the camera is punched in right now.
     *
     * <p><strong>This is a camera move, and that is what makes it legal where swelling the road was
     * not.</strong> {@link #PULSE_SECONDS} tells the story of the earlier version that grew the
     * road's width a few percent on every beat: the whole picture pumped, the kart appeared to
     * lurch, and the motion stopped reading as driving. The reason it failed is worth being precise
     * about, because it is not "the beat touched the picture" - it is that the road's width is an
     * input to the projection, so changing it moved every entity <em>relative to the lane lines and
     * to each other</em>. Where a thing was stopped meaning when it would arrive, which is the one
     * job the projection has.
     *
     * <p>A uniform scale about the centre of the canvas does none of that. Every pixel of the
     * finished frame moves by the same factor, so nothing moves relative to anything else: the
     * entity is in the same place on the road, the road is the same width in road units, and the
     * lookahead still reads as timing. What changes is only how much of the frame you can see -
     * which is what a camera is. The road's width and scroll rate remain functions of the speed
     * class alone, exactly as the rule says.
     *
     * <p>Squared, so the punch is sharp and gone rather than a slow swell across the whole beat,
     * and it only ever zooms <em>in</em> - zooming out would leave the canvas edges uncovered.
     *
     * @param pulse the beat flash, 0 to 1
     * @return the scale factor to draw the world through, 1 when there is no beat
     */
    private static double beatZoom(double pulse) {
        return pulse <= 0 ? 1 : 1 + pulse * pulse * BEAT_ZOOM;
    }

    /**
     * Dips the whole screen on a strong beat.
     *
     * <p>The horizon flash says where the beat is; this says that the beat happened, and it is
     * readable from across a room and out of the corner of an eye - which is what a player driving
     * the road actually has available. It darkens rather than brightens: a light wash over a dark
     * palette washes out the road and hides the entities the beat has just put on it.
     *
     * <p>Still not the geometry. Nothing moves, nothing changes size, and the road's width and
     * scroll rate remain functions of the speed class alone - see {@link #PULSE_SECONDS}.
     *
     * @param gc     the context to draw into
     * @param width  canvas width
     * @param height canvas height
     * @param pulse  the beat flash, 0 to 1
     */
    private static void drawBeatWash(GraphicsContext gc, double width, double height,
                                     double pulse) {
        if (pulse <= 0) {
            return;
        }
        // Two lobes over one envelope. The dip is squared so it is hardest at the strike and lets
        // go quickly; the lift is a hump peaking halfway through the decay, so the screen goes
        // dark, comes back up through normal, overshoots slightly and settles. Strike and release,
        // rather than a dim that only has a beginning.
        double faded = 1 - pulse;
        double dip = pulse * pulse * BEAT_WASH_ALPHA;
        double lift = 4 * faded * (1 - faded) * BEAT_LIFT_ALPHA;

        if (dip > lift) {
            gc.setFill(color(PaletteRole.SHADOW, dip - lift));
        } else {
            // Lifted with the palette's near-white rather than a literal, so a light mood lifts
            // towards its own brightest colour instead of towards a colour it does not contain.
            gc.setFill(color(PaletteRole.TEXT_PRIMARY, lift - dip));
        }
        gc.fillRect(0, 0, width, height);
    }

    /**
     * Lights the whole screen in a role's colour after something happened.
     *
     * <p>Called from the run's listeners, so it fires exactly once per event rather than being
     * worked out per frame by looking for changes - see {@code RunnerListener} on why that
     * distinction matters the first time a frame is dropped.
     *
     * @param role    the palette role to light the screen with
     * @param seconds how long it lasts
     * @param alpha   how strong it is at its brightest
     * @param pulses  how many times it pulses on the way out; 0 for a plain fade
     */
    private void lightScreen(PaletteRole role, double seconds, double alpha, double pulses) {
        flashRole = role;
        flashSeconds = seconds;
        flashAlpha = alpha;
        flashPulses = pulses;
        flashStartedAt = game.now();
    }

    /**
     * Draws whatever the last event left on the screen.
     *
     * <p>A pickup is yellow and fades; a bump is red and <strong>pulses</strong> before it goes,
     * because a bump is the one event the player may have missed the cause of. A single fade reads
     * as a change in the light, and a few beats of it read as an alarm.
     *
     * @param gc     the context to draw into
     * @param width  canvas width
     * @param height canvas height
     * @param now    the playback position, in seconds
     */
    private void drawEventFlash(GraphicsContext gc, double width, double height, double now) {
        double age = now - flashStartedAt;
        if (!(age >= 0) || age > flashSeconds || flashSeconds <= 0) {
            // Also catches the infinity before the first event, and a seek backwards past one.
            return;
        }
        double fade = 1 - age / flashSeconds;
        double strength = flashPulses > 0
                // Fading envelope times a rectified sine, so it beats and dies rather than
                // strobing at a constant brightness and then vanishing mid-flash.
                ? fade * Math.abs(Math.sin(Math.PI * flashPulses * age / flashSeconds))
                : fade;

        gc.setFill(color(flashRole, strength * flashAlpha));
        gc.fillRect(0, 0, width, height);
    }

    /**
     * How bright the beat flash is right now.
     *
     * <p>Read straight off the beatmap rather than counted, so the flash is on the beat that is
     * audible and not on a beat this view worked out for itself.
     *
     * @param now the playback position, in seconds
     * @return 1 on the instant of a strong beat, falling to 0 over {@value #PULSE_SECONDS} seconds
     */
    private double beatPulse(double now) {
        if (activeBeatmap.isEmpty()) {
            return 0;
        }
        double beat = activeBeatmap.lastStrongBeatAtOrBefore(now);
        if (beat < 0) {
            return 0;
        }
        return Math.max(0, 1 - (now - beat) / pulseSeconds());
    }

    /**
     * How long the beat effect takes to fade, in seconds.
     *
     * <p><strong>A share of the track's own beat rather than a fixed number.</strong> A fixed
     * length has to be chosen for some tempo and is wrong at every other one: at
     * {@value #PULSE_SECONDS} seconds a 90 BPM track barely flickers between beats, while a 175 BPM
     * track never gets back to normal at all - the screen sits permanently part-washed and the
     * effect stops reading as a beat and starts reading as a haze. Taking a fraction of the period
     * means the picture always returns to neutral before the next strike, whatever the tempo, and
     * it is the same reasoning that puts the star's life in beats and the wall's warning in a
     * fraction of the travel time.
     *
     * <p>Capped at both ends: a track whose tempo could not be established falls back to the fixed
     * figure, and a very slow one must not leave the screen washed for most of a second.
     *
     * @return how long one beat's effect lasts
     */
    private double pulseSeconds() {
        double period = game.course().beatPeriodSeconds();
        if (period <= 0) {
            return PULSE_SECONDS;
        }
        return Math.clamp(period * PULSE_BEAT_FRACTION, PULSE_MIN_SECONDS, PULSE_MAX_SECONDS);
    }

    /**
     * Draws the backdrop and the horizon.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param horizonY where the road vanishes
     * @param pulse    the beat flash, 0 to 1
     */
    private void drawSky(GraphicsContext gc, double width, double horizonY, double pulse) {
        gc.setFill(color(PaletteRole.BACKGROUND));
        gc.fillRect(0, 0, width, horizonY);

        // Posterised into whole bands rather than smoothly interpolated. A smooth gradient reads
        // as a modern interface at a glance; a hard-banded one reads as the hardware this whole
        // application is styled after, and it costs four fills instead of a shader.
        double bandHeight = Math.ceil(horizonY / SKY_BANDS);
        for (int band = 0; band < SKY_BANDS; band++) {
            // Brightest against the horizon and falling away upwards, which is the direction the
            // light comes from in every track this look is borrowed from.
            double strength = 1 - band / (double) SKY_BANDS;
            gc.setFill(palette().mix(PaletteRole.BACKGROUND, PaletteRole.SURFACE_RAISED,
                    strength * strength));
            gc.fillRect(0, Math.round(horizonY - (band + 1) * bandHeight), width, bandHeight);
        }

        // The horizon line carries the beat. It is the widest continuous thing on screen, so a
        // flash there reads from the back of a room where a flicker on a sprite does not.
        gc.setFill(pulse > 0
                ? palette().mix(PaletteRole.OUTLINE, PaletteRole.ACCENT, pulse)
                : color(PaletteRole.OUTLINE));
        gc.fillRect(0, horizonY - 1, width, 2 + Math.round(2 * pulse));
    }

    /**
     * Draws the road, its kerbs, its lane lines and the ground either side.
     *
     * <p>The bands are the scroll. A stripe boundary sits wherever {@code (z + phase) * frequency}
     * crosses a whole number, so the whole road is a dozen filled trapezoids rather than a
     * per-scanline loop - and because {@code phase} advances at exactly the rate entities travel,
     * the surface and the things on it move as one. Drive at 200cc and the road is visibly faster,
     * for free.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param height   canvas height
     * @param horizonY where the road vanishes
     * @param depth    pixels between the horizon and the racer's line
     * @param halfNear the road's half-width at the racer
     * @param now      the playback position, in seconds
     */
    private void drawRoad(GraphicsContext gc, double width, double height, double horizonY,
                          double depth, double halfNear, double now) {
        double travel = game.course().travelTimeSeconds();
        // How far the bands have scrolled, counted in bands. Advancing this at exactly the rate an
        // entity's progress advances is what locks the surface to the things standing on it.
        double scroll = bandScroll(now, travel);

        double left = levels.leftRms();
        double right = levels.rightRms();
        double centerX = width / 2;

        // One band beyond each end: a band straddling an edge is clamped rather than skipped, and
        // starting exactly on the edge would leave a seam at the racer's line.
        int firstBand = (int) Math.floor(scroll - BANDS_PER_LOOKAHEAD) - 1;
        int lastBand = (int) Math.ceil(scroll) + 1;

        for (int band = firstBand; band <= lastBand; band++) {
            // Each boundary is a fixed instant, converted to progress and then to the screen by the
            // one shared curve - the same two steps an entity's position takes.
            //
            // The subtraction runs THIS way round, and getting it backwards is what made the road
            // scroll the wrong way. An entity's progress is `1 - (beatTime - now) / travel`, which
            // grows as the clock advances, so it moves down the screen towards the racer. Written
            // as `(band - scroll)` a band's progress *shrinks* as the clock advances, so the
            // surface climbed towards the horizon while everything standing on it came down -
            // which reads as driving backwards under a road going forwards, and is why the picture
            // looked wrong in a way that was hard to name.
            double progressNear = bandProgress(scroll, band);
            double progressFar = bandProgress(scroll, band + 1);
            if (progressNear <= progressFar) {
                continue;
            }

            double uFar = screenFraction(progressFar);
            double uNear = screenFraction(progressNear);
            double yFar = horizonY + depth * uFar;
            double yNear = horizonY + depth * uNear;
            // A straight flat road's edges are straight lines to the vanishing point, so the width
            // is simply proportional to how far down the screen it is. No divide needed.
            double halfFar = halfNear * uFar;
            double halfNearEdge = halfNear * uNear;
            boolean lit = (band & 1) == 0;

            // The ground either side, so the road reads as a road rather than as a shape.
            fillTrapezoid(gc, 0, width, yFar, 0, width, yNear,
                    lit ? color(PaletteRole.BACKGROUND_ALT)
                            : palette().mix(PaletteRole.BACKGROUND, PaletteRole.SHADOW, 0.5));

            // The surface, deliberately lifted well clear of the ground. Both are dark in this
            // palette and the two alternating road bands alone left the road indistinguishable
            // from the verge - the shape was there and nobody could see it was a road.
            fillTrapezoid(gc, centerX - halfFar, centerX + halfFar, yFar,
                    centerX - halfNearEdge, centerX + halfNearEdge, yNear,
                    lit ? palette().mix(PaletteRole.SURFACE_RAISED, PaletteRole.TEXT_DIM, ROAD_LIFT)
                            : color(PaletteRole.SURFACE_RAISED));

            // The kerbs. Their brightness is the channel's level, which is where the meters and
            // the game visibly meet: a hard-panned track lights one side of the road and not the
            // other, and it is the same measurement the bars on the right are drawing.
            drawKerb(gc, centerX - halfFar, centerX - halfNearEdge, yFar, yNear,
                    uFar, uNear, lit, left, true);
            drawKerb(gc, centerX + halfFar, centerX + halfNearEdge, yFar, yNear,
                    uFar, uNear, lit, right, false);

            // The lane lines, dashed by drawing them on alternate bands only. Wider near the
            // camera, because at one pixel throughout they vanish at the far end and look ragged
            // at the near one.
            if (!lit) {
                for (int line = 1; line < Lane.COUNT; line++) {
                    double offset = (line / (double) Lane.COUNT - 0.5) * 2;
                    double halfLineFar = Math.max(0.5, LANE_LINE_WIDTH_NEAR * uFar / 2);
                    double halfLineNear = Math.max(0.5, LANE_LINE_WIDTH_NEAR * uNear / 2);
                    fillTrapezoid(gc,
                            centerX + offset * halfFar - halfLineFar,
                            centerX + offset * halfFar + halfLineFar, yFar,
                            centerX + offset * halfNearEdge - halfLineNear,
                            centerX + offset * halfNearEdge + halfLineNear, yNear,
                            color(PaletteRole.TEXT_DIM, 0.7));
                }
            }
        }

        // Under the road, so the strip below the racer never shows whatever the last band left.
        double bottom = horizonY + depth;
        gc.setFill(color(PaletteRole.SHADOW));
        gc.fillRect(0, bottom, width, height - bottom);
    }

    /**
     * Draws one side's kerb for one band.
     *
     * @param gc         the context to draw into
     * @param xFar       the road edge at the far end of the band
     * @param xNear      the road edge at the near end
     * @param yFar       the far end's screen row
     * @param yNear      the near end's screen row
     * @param uFar       the far end's screen fraction
     * @param uNear      the near end's screen fraction
     * @param lit        whether this is an alternate band
     * @param level      the channel's level, 0 to 1, which the kerb glows with
     * @param leftSide   whether this is the left-hand kerb, which grows outwards the other way
     */
    private void drawKerb(GraphicsContext gc, double xFar, double xNear, double yFar, double yNear,
                          double uFar, double uNear, boolean lit, double level,
                          boolean leftSide) {
        double widthFar = KERB_WIDTH_NEAR * uFar;
        double widthNear = KERB_WIDTH_NEAR * uNear;
        double direction = leftSide ? -1 : 1;
        Color face = lit
                ? palette().mix(PaletteRole.OUTLINE, PaletteRole.METER_HIGH,
                        Math.clamp(Levels.scale((float) level), 0f, 1f))
                : color(PaletteRole.SHADOW);
        fillTrapezoid(gc, xFar, xFar + direction * widthFar, yFar,
                xNear, xNear + direction * widthNear, yNear, face);
    }

    /**
     * Fills the quadrilateral between two horizontal edges.
     *
     * @param gc     the context to draw into
     * @param farLeft   left edge at the far end
     * @param farRight  right edge at the far end
     * @param farY      the far end's screen row
     * @param nearLeft  left edge at the near end
     * @param nearRight right edge at the near end
     * @param nearY     the near end's screen row
     * @param fill      the colour to fill with
     */
    private void fillTrapezoid(GraphicsContext gc, double farLeft, double farRight,
                               double farY, double nearLeft, double nearRight, double nearY,
                               Color fill) {
        polygonX[0] = farLeft;
        polygonX[1] = farRight;
        polygonX[2] = nearRight;
        polygonX[3] = nearLeft;
        polygonY[0] = farY;
        polygonY[1] = farY;
        polygonY[2] = nearY;
        polygonY[3] = nearY;
        gc.setFill(fill);
        gc.fillPolygon(polygonX, polygonY, 4);
    }

    /**
     * Scratch arrays for {@link #fillTrapezoid}.
     *
     * <p>The road is around seventy quadrilaterals a frame - surface, verge, two kerbs and two lane
     * lines per band - and allocating a pair of four-element arrays for each of them is over eight
     * thousand short-lived arrays a second, for a shape that is the same four corners every time.
     * Held rather than allocated, which is safe because drawing only ever happens on one thread.
     */
    private final double[] polygonX = new double[4];
    private final double[] polygonY = new double[4];

    /**
     * Draws everything on the course that is close enough to see.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param horizonY where the road vanishes
     * @param depth    pixels between the horizon and the racer's line
     * @param halfNear the road's half-width at the racer
     * @param groundY  the racer's line
     * @param now      the playback position, in seconds
     */
    private void drawEntities(GraphicsContext gc, double width, double horizonY, double depth,
                              double halfNear, double groundY, double now) {
        Course course = game.course();
        double closestWall = -1;

        // ---- Barriers first, all of them ------------------------------------------------------
        //
        // A wall is three obstacles sharing one beat, and each of them asks for the band. Drawn
        // inline with its own sprite, the second obstacle's band painted over the first
        // obstacle's kart-height sprite and the third painted over both - the bumps looked cut off
        // at the ankles. The band is opaque and identical for all three, so drawing every one of
        // them before any sprite costs nothing and the overdraw becomes invisible, which is what
        // it was always supposed to be.
        for (int index = game.lastVisible(); index >= game.firstVisible(); index--) {
            if (game.stateOf(index).isResolved()) {
                continue;
            }
            double progress = game.progressOf(index);
            if (progress < 0) {
                continue;
            }
            if (course.entityAt(index) instanceof Obstacle obstacle && obstacle.isWall()) {
                closestWall = Math.max(closestWall, progress);
                drawBarrier(gc, width, horizonY, depth, halfNear, screenFraction(progress));
            }
        }

        // ---- Then the entities, far to near ---------------------------------------------------
        //
        // **Descending, and that is the whole of the fix.** The course is in ascending beat order,
        // so a low index is an entity arriving sooner - which is the one closest to the racer and
        // lowest on the screen. Walking up from firstVisible therefore drew the nearest first and
        // let every farther entity paint over it, so a coin at the horizon clipped through the bump
        // about to hit you. Painter's algorithm wants the opposite: farthest down first, nearest
        // last, so nearer things occlude farther ones exactly as they should.
        //
        // Resolved entities are not drawn here at all - see drawEffects, which runs after the kart.
        for (int index = game.lastVisible(); index >= game.firstVisible(); index--) {
            if (game.stateOf(index).isResolved()) {
                continue;
            }
            double progress = game.progressOf(index);
            if (progress < 0) {
                continue;
            }
            drawEntity(gc, width, horizonY, depth, halfNear, course.entityAt(index), progress, now);
        }

        if (closestWall >= WALL_WARNING_PROGRESS) {
            drawJumpPrompt(gc, width, groundY, closestWall, now);
        }
    }

    /**
     * Draws what is left of everything the racer has already met.
     *
     * <p><strong>After the kart, deliberately.</strong> Every one of these happens <em>at</em> the
     * racer - the coin pop, the explosion, the coins a bump scattered - and the kart is nearly two
     * hundred pixels of opaque sprite standing exactly there. Drawn before it, an explosion is a
     * ring of light peeping out from behind the thing it is supposed to have hit, which reads as
     * the effect happening somewhere else. Drawn after, it goes off in the player's face, which is
     * what it is for.
     *
     * <p>Descending, like the pending pass, so a nearer effect covers a farther one.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param halfNear the road's half-width at the racer
     * @param groundY  the racer's line
     * @param now      the playback position, in seconds
     */
    private void drawEffects(GraphicsContext gc, double width, double halfNear, double groundY,
                             double now) {
        Course course = game.course();
        for (int index = game.lastVisible(); index >= game.firstVisible(); index--) {
            EntityState entityState = game.stateOf(index);
            if (!entityState.isResolved()) {
                continue;
            }
            drawEffect(gc, width, halfNear, groundY, course.entityAt(index), entityState,
                    now - game.resolvedAt(index));
        }
    }

    /**
     * Tells the player to jump, as a wall closes.
     *
     * <p>A wall blocks every lane, so unlike everything else on the course there is nothing to read
     * and decide - the answer is always the same and the only question is when. Shouting it is
     * therefore not a crutch: what the player is being asked for is timing, and hiding the cue
     * would only be testing whether they had learned what a row of three bumps looks like at
     * distance.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param groundY  the racer's line
     * @param progress how far down the course the wall has come, 0 to 1
     * @param now      the playback position, in seconds
     */
    private void drawJumpPrompt(GraphicsContext gc, double width, double groundY, double progress,
                                double now) {
        if (game.isJumping()) {
            return;
        }
        // Brighter and larger the closer it gets, so the urgency is in the picture rather than only
        // in the word.
        double urgency = Math.clamp(
                (progress - WALL_WARNING_PROGRESS) / (1 - WALL_WARNING_PROGRESS), 0d, 1d);
        double size = Math.round(BANNER_SIZE + 8 * urgency);
        double flash = 0.55 + 0.45 * Math.abs(Math.sin(now * 12));

        String text = "JUMP!";
        double x = Math.round((width - textWidth(text, size)) / 2);
        double y = Math.round(groundY - 150 - 30 * urgency);

        gc.setFont(Fonts.pixel(size));
        gc.setFill(color(PaletteRole.SHADOW, 0.75));
        gc.fillText(text, x + 3, y + 3);
        gc.setFill(color(PaletteRole.NEGATIVE, flash));
        gc.fillText(text, x, y);
    }

    /**
     * Draws one pending entity at its depth.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param horizonY where the road vanishes
     * @param depth    pixels between the horizon and the racer's line
     * @param halfNear the road's half-width at the racer
     * @param entity   what to draw
     * @param progress how far along the course it has come, 0 to 1
     * @param now      the playback position, in seconds
     */
    private void drawEntity(GraphicsContext gc, double width, double horizonY, double depth,
                            double halfNear, Entity entity, double progress, double now) {
        // The wall's hazard band is not drawn here. It is a band across the whole road that all
        // three of a wall's obstacles ask for, so drawEntities lays every one of them down before
        // any sprite - see the note there.
        double u = screenFraction(progress);

        SpriteSheet sheet = assets.sheet(entity.kind());
        int frame = entity.kind() == AssetKind.STAR
                ? (int) (now * STAR_FPS)
                : 0;
        // Continuous, so the entity swells smoothly as it comes in rather than jumping between
        // four sizes on the way. See entityScale for why this is the one place that does not snap.
        double scale = entityScale(u);
        double x = laneX(width, halfNear, entity.lane().index(), u);
        double y = horizonY + depth * u;
        Rectangle2D viewport = sheet.viewport(frame);
        drawSprite(gc, sheet, frame,
                x - viewport.getWidth() * scale / 2,
                y - viewport.getHeight() * scale,
                scale);
    }

    /**
     * Draws the band that turns a row of three bumps into a wall.
     *
     * <p>Opaque, so the three obstacles that each ask for it overdraw each other invisibly rather
     * than stacking up alpha. Hazard-striped in whole blocks whose width shrinks with depth, which
     * is the same trick the road's own bands use and the reason it reads as lying on the surface
     * rather than floating over it.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param horizonY where the road vanishes
     * @param depth    pixels between the horizon and the racer's line
     * @param halfNear the road's half-width at the racer
     * @param u        the wall's screen fraction from the horizon
     */
    private void drawBarrier(GraphicsContext gc, double width, double horizonY, double depth,
                             double halfNear, double u) {
        double half = halfNear * u;
        double y = horizonY + depth * u;
        double height = Math.max(3, BARRIER_HEIGHT_NEAR * u);
        double left = width / 2 - half;

        double block = Math.max(4, BARRIER_BLOCK_NEAR * u);
        int blocks = (int) Math.ceil(2 * half / block);
        for (int index = 0; index < blocks; index++) {
            gc.setFill(index % 2 == 0
                    ? color(PaletteRole.NEGATIVE)
                    : color(PaletteRole.TEXT_PRIMARY));
            double blockLeft = left + index * block;
            gc.fillRect(blockLeft, y - height, Math.min(block, left + 2 * half - blockLeft), height);
        }
    }

    /**
     * Draws what is left of an entity the racer has already met.
     *
     * <p>Effects are drawn at the racer's line rather than carried on down the road. An entity's
     * position is a function of how long until its beat, and once that beat has passed the function
     * has nothing left to say - so the pop happens where the collision did and fades out of its own
     * accord.
     *
     * @param gc          the context to draw into
     * @param width       canvas width
     * @param halfNear    the road's half-width at the racer
     * @param groundY     the racer's line
     * @param entity      what was met
     * @param entityState what became of it
     * @param age         how long ago, in seconds
     */
    private void drawEffect(GraphicsContext gc, double width, double halfNear, double groundY,
                            Entity entity, EntityState entityState, double age) {
        if (age < 0 || age > RunnerGame.EFFECT_SECONDS) {
            return;
        }
        double fade = 1 - age / RunnerGame.EFFECT_SECONDS;
        double x = laneX(width, halfNear, entity.lane().index(), 1);
        double y = groundY - age * EFFECT_RISE;

        switch (entityState) {
            case COLLECTED -> {
                if (age > POP_SECONDS) {
                    return;
                }
                burst(gc, x, groundY, age, POP_SECONDS, PaletteRole.POSITIVE);
                caption(gc, entity instanceof Star ? "STAR!" : "+1", x, y, PaletteRole.POSITIVE,
                        1 - age / POP_SECONDS);
            }
            case BROKEN -> {
                if (age > BREAK_SECONDS) {
                    return;
                }
                explode(gc, x, groundY, age, BREAK_SECONDS);
                caption(gc, "+" + ScoreKeeper.BREAK_BONUS_COINS, x, y, PaletteRole.PRIMARY,
                        1 - age / BREAK_SECONDS);
            }
            case HIT -> {
                // The bump goes up, and the coins it cost go everywhere. Watching them bounce off
                // and land is the part that makes the penalty land too - a number ticking down in
                // the corner is arithmetic, and coins on the floor are a mistake.
                explode(gc, x, groundY, age, HIT_SECONDS);
                dropCoins(gc, x, groundY, age, entity.beatTime());
                caption(gc, "-" + ScoreKeeper.HIT_PENALTY_COINS, x, y, PaletteRole.NEGATIVE,
                        1 - age / HIT_SECONDS);
            }
            case CLEARED -> {
                if (age <= POP_SECONDS) {
                    caption(gc, "OVER!", x, y, PaletteRole.ACCENT, 1 - age / POP_SECONDS);
                }
            }
            default -> {
                // Passed in another lane. Nothing happened, so nothing is drawn.
            }
        }
    }

    /**
     * Plays the explosion sheet over its whole life.
     *
     * <p>The artwork is two frames and may not be present at all - it draws a magenta placeholder
     * then, loudly and without an exception, which is exactly what ground rule 5 asks for. The
     * flash underneath it is drawn either way, so a bump reads as a bump even with no art at all.
     *
     * @param gc      the context to draw into
     * @param x       where it happened
     * @param groundY the road line it happened on
     * @param age     how long ago, in seconds
     * @param life    how long this effect runs, in seconds
     */
    private void explode(GraphicsContext gc, double x, double groundY, double age, double life) {
        double fade = Math.clamp(1 - age / life, 0d, 1d);
        SpriteSheet sheet = assets.sheet(AssetKind.EXPLOSION);
        // Clamped rather than left to wrap. SpriteSheet.viewport takes a frame index modulo the
        // count, which is right for a looping animation and wrong for one that plays once: at the
        // very last instant of the effect `age / life` reaches 1, the index reaches frameCount, and
        // a two-frame explosion would snap back to its first frame for a single frame as it faded.
        int frame = (int) Math.clamp((long) (age / life * sheet.frameCount()),
                0, sheet.frameCount() - 1L);
        Rectangle2D viewport = sheet.viewport(frame);

        // A ring of light that swells and dies, so the moment is legible before the sprite is read.
        double radius = 30 + 150 * (1 - fade);
        gc.setFill(color(PaletteRole.NEGATIVE, fade * 0.45));
        gc.fillOval(x - radius, groundY - 30 - radius / 2, radius * 2, radius);

        gc.setGlobalAlpha(Math.clamp(fade + 0.25, 0d, 1d));
        drawSprite(gc, sheet, frame,
                x - viewport.getWidth() * MAX_SPRITE_SCALE / 2.0,
                groundY - viewport.getHeight() * MAX_SPRITE_SCALE,
                MAX_SPRITE_SCALE);
        gc.setGlobalAlpha(1);
    }

    /**
     * Throws the coins a bump cost out onto the road.
     *
     * <p>Each coin's arc is a pure function of the obstacle's beat time and its own index - thrown
     * up, pulled down, scattered sideways - so there is no particle list to keep, nothing to
     * allocate per frame, and the same bump scatters its coins the same way every time the course
     * is driven. Deterministic for the same reason the course is.
     *
     * @param gc       the context to draw into
     * @param x        where the bump was hit
     * @param groundY  the road line
     * @param age      how long ago, in seconds
     * @param beatTime the obstacle's beat, which seeds the scatter
     */
    private void dropCoins(GraphicsContext gc, double x, double groundY, double age,
                           double beatTime) {
        SpriteSheet sheet = assets.sheet(AssetKind.COIN);
        Rectangle2D viewport = sheet.viewport(0);
        int scale = Math.max(1, MAX_SPRITE_SCALE / 2);

        for (int coin = 0; coin < ScoreKeeper.HIT_PENALTY_COINS; coin++) {
            // A cheap deterministic spread: no random number generator, no state, same every run.
            double spin = Math.sin((beatTime + 1) * 37.13 + coin * 2.399);
            double lift = 0.7 + 0.3 * Math.cos((beatTime + 1) * 17.77 + coin * 1.618);

            double flightX = x + spin * COIN_SCATTER_SPEED * age;
            double rise = COIN_THROW_SPEED * lift * age - COIN_GRAVITY * age * age / 2;
            double flightY = groundY - 30 - rise;
            if (flightY > groundY) {
                // Landed and rolled off the bottom of the road.
                continue;
            }
            gc.setGlobalAlpha(Math.clamp(1 - age / HIT_SECONDS, 0d, 1d));
            drawSprite(gc, sheet, 0,
                    flightX - viewport.getWidth() * scale / 2.0,
                    flightY - viewport.getHeight() * scale,
                    scale);
            gc.setGlobalAlpha(1);
        }
    }

    /**
     * Draws a ring of pixel squares flying outwards, which is the 8-bit way of saying something
     * happened here.
     *
     * @param gc   the context to draw into
     * @param x    where it happened
     * @param y    the road line it happened on
     * @param age  how long ago, in seconds
     * @param life how long the effect runs, in seconds
     * @param role the colour role for the particles
     */
    private static void burst(GraphicsContext gc, double x, double y, double age, double life,
                              PaletteRole role) {
        double fade = 1 - age / life;
        double spread = age * 220;
        gc.setFill(color(role, Math.clamp(fade, 0d, 1d)));
        for (int particle = 0; particle < 8; particle++) {
            double angle = particle * Math.PI / 4;
            gc.fillRect(Math.round(x + Math.cos(angle) * spread) - 2,
                    Math.round(y - 20 + Math.sin(angle) * spread) - 2, 4, 4);
        }
    }

    /**
     * Draws a rising caption over an effect.
     *
     * @param gc   the context to draw into
     * @param text what it says
     * @param x    its centre
     * @param y    its baseline
     * @param role the colour role
     * @param fade how opaque it still is
     */
    private static void caption(GraphicsContext gc, String text, double x, double y,
                                PaletteRole role, double fade) {
        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(color(role, Math.clamp(fade, 0d, 1d)));
        gc.fillText(text, Math.round(x - textWidth(text, TEXT_SIZE) / 2), Math.round(y));
    }

    /**
     * Draws the kart, seen from behind.
     *
     * <p>Frame {@link RacerFrame#BACK} and never the driving cycle: the cycle is a side view, and
     * looping it here would flicker the kart between facing away and facing right several times a
     * second. What conveys speed in this shot is the road moving underneath, which it does because
     * it is driven by the same clock.
     *
     * @param gc       the context to draw into
     * @param width    canvas width
     * @param halfNear the road's half-width at the racer
     * @param groundY  the racer's line
     * @param now      the playback position, in seconds
     */
    private void drawRacer(GraphicsContext gc, double width, double halfNear, double groundY,
                           double now) {
        if (game.isInvulnerable() && Math.floorMod((int) (now * INVULNERABLE_BLINK_HZ), 2) == 1) {
            // Blinking through the protected spell, which is how every game of this kind says
            // "that one did not count" without a word of text.
            return;
        }

        SpriteSheet sheet = assets.racer(state.getRacer());
        Rectangle2D viewport = sheet.viewport(RacerFrame.BACK.index());
        int scale = racerScale(width);
        double x = laneX(width, halfNear, game.lanePosition(), 1);
        double height = game.jumpHeight();
        double lift = height * JUMP_LIFT;
        double y = groundY - viewport.getHeight() * scale - lift;

        // A shadow that shrinks and pulls away underneath is what turns a sprite moving up the
        // screen into a sprite leaving the ground. Without it a jump reads as the kart drifting
        // upwards, which is why the first version of this looked like nothing was happening.
        double shadow = 1 - 0.55 * height;
        gc.setFill(color(PaletteRole.SHADOW, 0.55 - 0.25 * height));
        gc.fillOval(x - viewport.getWidth() * scale * shadow / 2, groundY - 5,
                viewport.getWidth() * scale * shadow, 10);

        if (height > 0) {
            // A puff of dust left on the road at the take-off point, so the jump has a beginning
            // as well as a middle.
            double dust = 1 - game.jumpProgress();
            gc.setFill(color(PaletteRole.TEXT_DIM, 0.35 * dust * dust));
            double spread = viewport.getWidth() * scale * (0.5 + 0.7 * game.jumpProgress());
            gc.fillOval(x - spread / 2, groundY - 12, spread, 16);
        }

        boolean starred = game.isStarred();
        if (starred) {
            // A halo drawn as blocks rather than as an ellipse. A smooth anti-aliased oval is the
            // one shape on this screen that gives away that it was drawn by a modern toolkit -
            // everything else here is hard-edged, and the eye finds the odd one out immediately.
            double flicker = 0.32 + 0.22 * Math.sin(now * 18);
            fillPixelOval(gc,
                    x, y + viewport.getHeight() * scale / 2,
                    viewport.getWidth() * scale * 0.78,
                    viewport.getHeight() * scale * 0.62 + 10,
                    Math.max(3, scale * 2),
                    rainbow(now, flicker));
        }

        if (starred) {
            // The star cycles the kart through the palette. Moods still never tint the artwork -
            // this is a power-up saying so, not a theme, and it lasts eight beats.
            gc.setEffect(starTint(now));
        }
        drawSprite(gc, sheet, RacerFrame.BACK.index(),
                x - viewport.getWidth() * scale / 2, y, scale);
        if (starred) {
            gc.setEffect(null);
        }
    }

    /**
     * The roles the star cycles through, in the order it cycles them.
     *
     * <p><strong>A rainbow made of the mood's own colours, not of hues.</strong> Reaching for
     * {@code Color.hsb} here would have been shorter and would have put six colours into the runner
     * that no palette can reach - exactly the hex-literal debt ground rule 7 exists to prevent, and
     * the reason a mood would leave the star looking like it belonged to a different game. Cycling
     * roles instead means the star restyles itself when M11 arrives, for free.
     */
    private static final PaletteRole[] RAINBOW = {
            PaletteRole.PRIMARY, PaletteRole.POSITIVE, PaletteRole.METER_LOW,
            PaletteRole.ACCENT, PaletteRole.HIGHLIGHT, PaletteRole.NEGATIVE,
    };

    /** How many times a second the star runs through the whole palette cycle. */
    private static final double RAINBOW_HZ = 1.6;

    /**
     * @param now     the playback position, in seconds
     * @param opacity how strong the colour should be
     * @return the star's colour at this instant, mixed between two palette roles
     */
    private static Color rainbow(double now, double opacity) {
        double cycle = now * RAINBOW_HZ * RAINBOW.length;
        int from = (int) Math.floorMod((long) Math.floor(cycle), RAINBOW.length);
        int to = (from + 1) % RAINBOW.length;
        // Mixed rather than stepped, and Palette.mix snaps the result back onto the 5-bit grid, so
        // the cycle stays inside the colours a GBA could actually have shown.
        return palette().mix(RAINBOW[from], RAINBOW[to], cycle - Math.floor(cycle))
                .deriveColor(0, 1, 1, Math.clamp(opacity, 0d, 1d));
    }

    /**
     * The hue rotation applied to the kart while the star is running.
     *
     * <p>One instance, mutated in place. A {@code ColorAdjust} per frame would be an object per
     * frame for eight beats at a time, and the effect is only ever used from the interface thread.
     *
     * @param now the playback position, in seconds
     * @return the effect to draw the kart through
     */
    private static ColorAdjust starTint(double now) {
        // Hue runs -1..1 in JavaFX, so this walks the whole wheel once per cycle.
        STAR_TINT.setHue(Math.sin(now * RAINBOW_HZ * 2 * Math.PI));
        STAR_TINT.setSaturation(0.45);
        STAR_TINT.setBrightness(0.18);
        return STAR_TINT;
    }

    private static final ColorAdjust STAR_TINT = new ColorAdjust();

    /**
     * Fills an ellipse out of square blocks, the way a tile-based machine would have.
     *
     * <p>Each row of blocks is as wide as the ellipse is at that row, rounded outwards to a whole
     * block, so the edge steps in visible stairs instead of being anti-aliased into a smooth curve.
     * That is the whole point: this sits behind a hand-drawn sprite, and a perfectly smooth oval
     * next to hard pixel art reads as a rendering mistake.
     *
     * @param gc      the context to draw into
     * @param centerX the ellipse's centre
     * @param centerY the ellipse's centre
     * @param radiusX half its width
     * @param radiusY half its height
     * @param block   the size of one block, in pixels
     * @param fill    the colour to fill with
     */
    private static void fillPixelOval(GraphicsContext gc, double centerX, double centerY,
                                      double radiusX, double radiusY, double block, Color fill) {
        gc.setFill(fill);
        int rows = (int) Math.ceil(2 * radiusY / block);
        for (int row = 0; row < rows; row++) {
            double top = centerY - radiusY + row * block;
            // Measured at the row's middle, so a row is as wide as the ellipse actually is across
            // it rather than at whichever edge happened to be sampled.
            double dy = (top + block / 2 - centerY) / radiusY;
            if (Math.abs(dy) >= 1) {
                continue;
            }
            double half = radiusX * Math.sqrt(1 - dy * dy);
            int blocks = (int) Math.round(2 * half / block);
            if (blocks <= 0) {
                continue;
            }
            double left = Math.round((centerX - blocks * block / 2) / block) * block;
            gc.fillRect(left, Math.round(top), blocks * block, block);
        }
    }

    /**
     * Draws the head-up display.
     *
     * @param gc     the context to draw into
     * @param width  canvas width
     * @param height canvas height
     */
    private void drawHud(GraphicsContext gc, double width, double height) {
        ScoreKeeper score = game.score();
        double x = HUD_PADDING;

        // The speed class first and largest. It changes what the course holds, how fast it comes
        // and what the coins are worth, and it is the one thing on screen a viewer needs in order
        // to know why one run looks nothing like the last - so it is read before anything else.
        String speedClass = state.getSpeedClass().displayName().toUpperCase();
        drawClassPlate(gc, x, HUD_PADDING, speedClass);
        double y = HUD_PADDING + CLASS_PLATE_HEIGHT + HUD_SIZE + 10;

        gc.setFont(Fonts.pixel(HUD_SIZE));
        gc.setFill(color(PaletteRole.PRIMARY));
        gc.fillText("COINS " + String.format("%03d", score.coins()), x, y);
        y += HUD_SIZE + 8;
        gc.setFill(color(PaletteRole.TEXT_PRIMARY));
        gc.fillText("SCORE " + String.format("%05d", score.score()), x, y);
        y += HUD_SIZE + 8;

        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(color(PaletteRole.TEXT_DIM));
        gc.fillText(score.isRanked()
                        ? "RANK " + score.rank() + "  " + Math.round(score.completion() * 100) + "%"
                        : "RANK -",
                x, y);
        y += TEXT_SIZE + 6;

        if (best != null) {
            gc.setFill(color(PaletteRole.TEXT_DIM, 0.75));
            gc.fillText("BEST " + best.rank() + "  " + Math.round(best.completion() * 100) + "%",
                    x, y);
        }

        drawNowPlaying(gc, width);

        if (game.isStarred()) {
            drawStarTimer(gc, width, height);
        }

        // The controls, small and out of the way, on the side the kart is not. Always the same
        // words: the keys work whenever the road is on screen, so there is no state to report.
        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(color(PaletteRole.TEXT_DIM, 0.6));
        String hint = "< > STEER   SPACE JUMP";
        gc.fillText(hint, width - HUD_PADDING - textWidth(hint, TEXT_SIZE),
                height - HUD_PADDING);
    }

    /**
     * Draws the speed class as a plate rather than a line of text, so it reads across a room.
     *
     * @param gc    the context to draw into
     * @param x     left edge
     * @param y     top edge
     * @param label the class name
     */
    private static void drawClassPlate(GraphicsContext gc, double x, double y, String label) {
        double plateWidth = textWidth(label, CLASS_SIZE) + 18;

        gc.setFill(color(PaletteRole.PRIMARY));
        gc.fillRect(x, y, plateWidth, CLASS_PLATE_HEIGHT);
        gc.setFill(color(PaletteRole.SHADOW));
        gc.fillRect(x + 3, y + 3, plateWidth - 6, CLASS_PLATE_HEIGHT - 6);

        gc.setFont(Fonts.pixel(CLASS_SIZE));
        gc.setFill(color(PaletteRole.PRIMARY));
        gc.fillText(label, Math.round(x + 9), Math.round(y + CLASS_PLATE_HEIGHT - 10));
    }

    /**
     * Draws what is playing, in the top right corner.
     *
     * <p>The transport at the top of the window says the same thing, and it is the wrong place for
     * it: once the road has the eye, looking away from it to read a title means missing the next
     * bar. Shortened rather than wrapped, like every other long string in this interface.
     *
     * @param gc    the context to draw into
     * @param width canvas width
     */
    private void drawNowPlaying(GraphicsContext gc, double width) {
        Song song = state.getCurrentSong();
        if (song == null) {
            return;
        }
        double available = width / 2.4;
        String title = fit(song.getTitle().toUpperCase(), available, TEXT_SIZE);
        String artist = fit(song.getArtist().toUpperCase(), available, TEXT_SIZE);
        double right = width - HUD_PADDING;

        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(color(PaletteRole.TEXT_DIM, 0.7));
        String caption = isRunningWithAudio() ? "NOW PLAYING" : "PAUSED";
        gc.fillText(caption, right - textWidth(caption, TEXT_SIZE), HUD_PADDING + TEXT_SIZE);

        gc.setFill(color(PaletteRole.PRIMARY));
        gc.fillText(title, right - textWidth(title, TEXT_SIZE), HUD_PADDING + TEXT_SIZE * 2 + 8);
        gc.setFill(color(PaletteRole.TEXT_DIM));
        gc.fillText(artist, right - textWidth(artist, TEXT_SIZE), HUD_PADDING + TEXT_SIZE * 3 + 14);
    }

    /**
     * Shortens a string to fit a pixel width.
     *
     * @param text  the string to fit
     * @param width available width in pixels
     * @param size  the point size it will be drawn at
     * @return the string, shortened with a trailing ellipsis if it did not fit
     */
    private static String fit(String text, double width, double size) {
        if (text == null) {
            return "";
        }
        int characters = (int) Math.max(1, Math.floor(width / size));
        if (text.length() <= characters) {
            return text;
        }
        return characters <= 3
                ? text.substring(0, characters)
                : text.substring(0, characters - 3) + "...";
    }

    /**
     * Draws how much of the star is left.
     *
     * @param gc     the context to draw into
     * @param width  canvas width
     * @param height canvas height
     */
    private void drawStarTimer(GraphicsContext gc, double width, double height) {
        double barWidth = Math.min(240, width - HUD_PADDING * 2);
        double x = (width - barWidth) / 2;
        double y = height - HUD_PADDING - 14;
        double period = game.course().beatPeriodSeconds();
        double total = period > 0
                ? RunnerGame.STAR_BEATS * period
                : RunnerGame.STAR_FALLBACK_SECONDS;
        double left = Math.clamp(game.starRemainingSeconds() / total, 0d, 1d);

        gc.setFill(color(PaletteRole.SHADOW, 0.7));
        gc.fillRect(x, y, barWidth, 10);
        gc.setFill(color(PaletteRole.PRIMARY));
        gc.fillRect(x, y, Math.round(barWidth * left), 10);
        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        gc.strokeRect(x + 0.5, y + 0.5, barWidth - 1, 9);

        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(color(PaletteRole.PRIMARY));
        String text = "STAR";
        gc.fillText(text, x - textWidth(text, TEXT_SIZE) - 8, y + 9);
    }

    /**
     * Draws the message shown when there is no course to drive.
     *
     * <p>An unanalysed track is an ordinary state, not a failure: the road, the meters and the beat
     * flash all work while the analysis runs, and the entities simply arrive when it finishes. So
     * this is a banner over a working view rather than a screen instead of one.
     *
     * @param gc     the context to draw into
     * @param width  canvas width
     * @param height canvas height
     */
    private void drawBanner(GraphicsContext gc, double width, double height) {
        BeatmapService.Status status = beatmaps.status();
        String message;
        double progress = -1;
        if (state.getCurrentSong() == null) {
            message = "NO SONG LOADED";
        } else if (status.stage() == BeatmapService.Stage.FAILED) {
            message = "NO COURSE FOR THIS TRACK";
        } else if (game.course().isEmpty()) {
            message = "GENERATING COURSE";
            progress = status.progress();
        } else if (!isRunningWithAudio() && !previewing) {
            // The run is stopped because the music is. Said out loud, because a frozen road with a
            // kart on it is otherwise indistinguishable from a frozen application.
            message = "PAUSED - PRESS PLAY";
        } else {
            return;
        }

        double boxWidth = Math.min(width - 40, Math.max(300, textWidth(message, BANNER_SIZE) + 48));
        double boxHeight = progress >= 0 ? 78 : 56;
        double x = Math.round((width - boxWidth) / 2);
        double y = Math.round(height / 2 - boxHeight / 2);

        gc.setFill(color(PaletteRole.SHADOW, 0.85));
        gc.fillRect(x, y, boxWidth, boxHeight);
        gc.setStroke(color(PaletteRole.PRIMARY));
        gc.setLineWidth(2);
        gc.strokeRect(x + 1, y + 1, boxWidth - 2, boxHeight - 2);

        gc.setFont(Fonts.pixel(BANNER_SIZE));
        gc.setFill(color(PaletteRole.PRIMARY));
        gc.fillText(message,
                Math.round(x + (boxWidth - textWidth(message, BANNER_SIZE)) / 2),
                Math.round(y + BANNER_SIZE + 20));

        if (progress < 0) {
            return;
        }
        double barWidth = boxWidth - 48;
        double barX = x + 24;
        double barY = y + boxHeight - 26;
        gc.setFill(color(PaletteRole.BACKGROUND));
        gc.fillRect(barX, barY, barWidth, 10);
        gc.setFill(color(PaletteRole.ACCENT));
        gc.fillRect(barX, barY, Math.round(barWidth * Math.clamp(progress, 0d, 1d)), 10);
        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        gc.strokeRect(barX + 0.5, barY + 0.5, barWidth - 1, 9);
    }

    // ------------------------------------------------------------------
    // Projection helpers
    // ------------------------------------------------------------------

    /**
     * The one curve everything on the road is placed by.
     *
     * <p>Takes "how much of the travel time has gone" and returns "how far down the road that is",
     * 0 at the vanishing point and 1 at the racer's line. Entities, surface bands, kerbs and lane
     * lines all go through it, which is what makes them move as one picture instead of sliding
     * against each other - and it is the single place to tune how much the road is foreshortened.
     * See {@link #PERSPECTIVE_BIAS}.
     *
     * @param progress how far along the course, 0 at the far end and 1 at the racer
     * @return the screen fraction between the horizon and the racer's line
     */
    static double screenFraction(double progress) {
        return Math.pow(Math.clamp(progress, 0d, 1d), PERSPECTIVE_BIAS);
    }

    /**
     * How far down the road one surface-band boundary has travelled.
     *
     * <p><strong>This must move the same way an entity does, and once it did not.</strong> A band
     * boundary is a fixed instant on the course, exactly like a coin's beat, so its progress has to
     * grow as the clock advances - which means subtracting the band from the scroll and not the
     * other way round. Written the other way the surface climbed towards the horizon while
     * everything standing on it came down the screen, and the result reads as vertigo rather than
     * as a mistake anybody can point at, which is why it survived a milestone.
     *
     * <p>A still frame of a scrolling road is a static road, so no screenshot can check this and
     * {@code RunnerProjectionTest} does instead.
     *
     * @param scroll how far the surface has scrolled, counted in bands
     * @param band   which band boundary
     * @return its progress along the course, 0 at the horizon and 1 at the racer
     */
    static double bandProgress(double scroll, int band) {
        return Math.clamp((scroll - band) / BANDS_PER_LOOKAHEAD, 0d, 1d);
    }

    /**
     * @param seconds       the playback position
     * @param travelSeconds the course's lookahead
     * @return how far the surface has scrolled, counted in bands
     */
    static double bandScroll(double seconds, double travelSeconds) {
        return travelSeconds <= 0 ? 0 : seconds / travelSeconds * BANDS_PER_LOOKAHEAD;
    }

    /**
     * @param width     canvas width
     * @param halfNear  the road's half-width at the racer
     * @param laneIndex the lane, possibly fractional while the kart slides between two
     * @param u         the screen fraction from the horizon
     * @return the screen column of that lane's centre there
     */
    private static double laneX(double width, double halfNear, double laneIndex, double u) {
        double half = halfNear * u;
        double offset = (laneIndex - (Lane.COUNT - 1) / 2.0) * (2 * half / Lane.COUNT);
        return width / 2 + offset;
    }

    /**
     * Chooses a sprite magnification for a point on the road.
     *
     * <p><strong>A whole number, always.</strong> The road behind it narrows continuously and the
     * sprite snaps between four sizes as it comes in, which is precisely what the hardware this
     * look is borrowed from did. Interpolating instead would be smoother and would turn every
     * hand-drawn sprite to mush everywhere but one distance.
     *
     * <p>Kept for anything that should read as authentically stepped. The entities on the road no
     * longer use it - see {@link #entityScale(double)}.
     *
     * @param u the screen fraction from the horizon
     * @return the magnification, between 1 and {@value #MAX_SPRITE_SCALE}
     */
    private static int spriteScale(double u) {
        return (int) Math.clamp(Math.round(MAX_SPRITE_SCALE * u), 1, MAX_SPRITE_SCALE);
    }

    /**
     * How much an entity is magnified at a point on the road, growing continuously.
     *
     * <p><strong>This is a deliberate, narrow exception to ground rule 8, and it is worth naming.
     * </strong> That rule says integer scale factors only, and it is right about why: JavaFX
     * interpolates by default and a fractionally scaled sprite turns to mush that reads as bad
     * artwork. But the rule was written about sprites sitting <em>still</em> at a scale. On the
     * road a sprite is travelling towards the camera, and snapping between four sizes meant it
     * visibly jumped three times on the way in - which on the thing the player is trying to time
     * is worse than a soft edge, because a sprite that changes size in a step is a sprite that
     * appears to change <em>position</em> in a step.
     *
     * <p>The mush is avoided rather than accepted: {@code setImageSmoothing(false)} is still on, so
     * this is a nearest-neighbour blow-up and never a blurred one, and {@link #drawSprite} rounds
     * the destination rectangle to whole pixels so the sprite never straddles a half pixel. What
     * that costs is uneven pixel widths at fractional scales, which is invisible on something
     * moving and is the trade every game of this kind actually made.
     *
     * <p>Everything not on the road - the racer, the explosion, anything in the interface - still
     * goes through {@link #spriteScale(double)} and still snaps.
     *
     * @param u the screen fraction from the horizon
     * @return the magnification, between {@value #MIN_ENTITY_SCALE} and {@value #MAX_SPRITE_SCALE}
     */
    static double entityScale(double u) {
        return Math.clamp(MAX_SPRITE_SCALE * u, MIN_ENTITY_SCALE, MAX_SPRITE_SCALE);
    }

    /**
     * @param width canvas width
     * @return the magnification the kart is drawn at, so it stays a sensible size on any panel
     */
    private static int racerScale(double width) {
        return (int) Math.clamp(Math.round(width / 320), 2, MAX_SPRITE_SCALE);
    }

    /**
     * Draws one frame of a sprite sheet at an integer scale.
     *
     * @param gc    the context to draw into
     * @param sheet the sheet to take a frame from
     * @param frame which frame
     * @param x     left edge of the destination
     * @param y     top edge of the destination
     * @param scale integer magnification, at least 1
     */
    private static void drawSprite(GraphicsContext gc, SpriteSheet sheet, int frame,
                                   double x, double y, double scale) {
        double factor = Math.max(MIN_ENTITY_SCALE, scale);
        Rectangle2D source = sheet.viewport(frame);
        // The destination is rounded to whole pixels even at a fractional scale, so a sprite never
        // straddles a half pixel and the nearest-neighbour blow-up stays hard-edged. Smoothing is
        // off on this context (ground rule 8), so this is never a blurred scale - only an uneven
        // one, which is what the hardware did too.
        gc.drawImage(sheet.image(),
                source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                Math.round(x), Math.round(y),
                Math.round(source.getWidth() * factor), Math.round(source.getHeight() * factor));
    }

    /** @return the width one string occupies at a size, in pixels */
    private static double textWidth(String text, double size) {
        // Press Start 2P is fixed-width at about one em per glyph, so this needs no measuring pass.
        return text == null ? 0 : text.length() * size;
    }

    /** @return the palette every colour in this view resolves through */
    private static Palette palette() {
        return Palette.active();
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

    /** @return the best stored run for the song and class on screen, if there is one */
    public Optional<ScoreEntry> best() {
        return Optional.ofNullable(best);
    }

    /**
     * Sizes the canvas to whatever room the panel gives it.
     *
     * <p>A {@code Canvas} does not resize with its parent, which is the usual surprise when one is
     * dropped into a layout.
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
