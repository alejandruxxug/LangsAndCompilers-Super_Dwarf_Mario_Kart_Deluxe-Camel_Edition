package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.analysis.BeatmapService;
import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.RacerFrame;
import com.eia.superdwarfkart.assets.SpriteAnimation;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.audio.SmoothClock;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import com.eia.superdwarfkart.playback.Player;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.CacheHint;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The companion window: a small card standing on the spinning record, and nothing else.
 *
 * <p>The shape is the point. A portrait card carries the cover, the song and the transport; the
 * record is a <strong>pedestal underneath it</strong>, wider than the card and breaking out past
 * both its edges, with the kart driving on top. Everything that is not the card, the record or the
 * three window buttons is <strong>fully transparent</strong> - there is no outer frame, no
 * background panel and no rectangle around the whole thing, so what sits on the desktop is the
 * shape of the artwork rather than a box containing it.
 *
 * <p>That is only possible because the stage is {@code StageStyle.TRANSPARENT} and the root here
 * paints nothing at all. A background on this pane, however faint, would put the box straight back.
 *
 * <p><strong>The spinning disk is the play/pause indicator.</strong> Nothing here reads a flag and
 * decides to freeze: the record and the kart riding it are drawn at whatever frame the
 * <em>playback position</em> has reached, so when the card stops advancing the position the picture
 * stops with it. Play and the two of them set off again. That is the same arrangement the runner
 * uses for its road, and it has the same property - the indicator and the audio cannot disagree,
 * because there is only one of them. The position goes through {@link SmoothClock} for the reason
 * that class exists: a card reports whole buffers, and a record driven straight off it would jerk.
 *
 * <p><strong>Where the sprites go is measured, not guessed.</strong> The record occupies the lower
 * 60% of its frame and the kart's driving frames leave transparent rows above and below it, so both
 * are placed by {@link SpriteSheet#opaqueBounds(int)} - the kart stands on the record's own surface
 * and the card's foot is covered by the record's own top edge. Art with different margins needs no
 * change here.
 *
 * <p><strong>{@link AppConfig#APP_NAME} must never appear here.</strong> At 43 characters in a font
 * whose glyphs are one em wide it is three times the width of this card, and the overflow is
 * invisible until somebody looks at the window. Nothing in this window draws the application name
 * at all - the record and the kart are the identity - and both {@code MiniPlayerLayoutTest} and the
 * smoke test check that it has not crept back in.
 *
 * <p>Everything on screen comes from the shared {@link AppState} and the one
 * {@link PlaybackEngine}, never from a private copy - changing the racer in the fullscreen window
 * has to change the sprite riding the record here at the same instant.
 *
 * <p>There is deliberately <strong>no seek control</strong>. A seek in a compressed stream decodes
 * from the start of the track, which is why the main window's bar commits once on release rather
 * than per pixel; a card this narrow cannot be dragged accurately enough to be worth that, so the
 * progress line here reports and does not accept. Seeking lives in the window with room for it.
 */
public class MiniPlayerView extends Pane {

    private static final Logger LOG = Logger.getLogger(MiniPlayerView.class.getName());

    /**
     * How big the record should be, in pixels. <strong>This is the size knob for the whole window</strong>
     * - the cartridge is derived from it, so raising this scales the pair together.
     *
     * <p>A <em>request</em>, not a promise: the record is pixel art and is snapped to a whole
     * multiple of its 32 pixel frame, so 254 is drawn at 256. Set it to a multiple of 32 and the two
     * agree exactly; set it to anything else and the difference is a pixel or two nobody can see.
     *
     * <p>Declared first because the cartridge is measured against it - the two are one relationship,
     * not two numbers, and a static field cannot be read before it is declared.
     */
    static final double DISK_SIZE = 254;

    /**
     * What share of the cartridge's width its <strong>inlet</strong> takes up - the narrower section
     * at its foot, past the step in its sides.
     *
     * <p>Measured from the artwork: it is full width down to row 466 and then steps in 27 pixels a
     * side, so its foot is 454 of 500. This is the conservative compile-time figure, exactly as
     * {@link #LABEL_WIDTH_SHARE} is, and {@link SpriteSheet#footprint(int)} measures the real one at
     * runtime for the smoke test to check it against.
     */
    private static final double CARTRIDGE_INLET_SHARE = 454d / 500d;

    /**
     * How wide the cartridge is against the record.
     *
     * <p><strong>Not a taste knob any more - the artwork decides it.</strong> It is the inverse of
     * the inlet share, which is what makes the cartridge's foot exactly as wide as the record: the
     * cartridge reads as plugged into it rather than balanced on it. Matching the cartridge's
     * <em>widest</em> part instead would leave the record narrower than the part actually resting on
     * it, which is the version that looks like a mistake.
     */
    static final double CARD_TO_DISK_RATIO = 1 / CARTRIDGE_INLET_SHARE;

    /**
     * Width of the card - which is the cartridge - in pixels.
     *
     * <p>Derived, so the shape of the window follows from the artwork rather than from two numbers
     * that have to be kept in step by hand. It is also what decides <strong>how long a song title
     * can be</strong>, because the label is only about 48% of this and this is the window's width.
     */
    static final double CARD_WIDTH = Math.round(DISK_SIZE * CARD_TO_DISK_RATIO);

    /**
     * How much the cartridge is darkened, from 0 for the artwork as drawn to -1 for black.
     * <strong>This is the shade knob.</strong>
     *
     * <p>It is a brightness adjustment on the one image rather than a repaint of it, so the grain
     * and the moulded shading survive - what changes is only how much light is on it.
     *
     * <p><strong>Measured, because it is not what it looks like:</strong> a negative brightness on
     * {@link ColorAdjust} is a <em>multiplier</em>, not a subtraction. At -0.28 the shell's 113 came
     * out at 81, which is 113 x 0.72 rather than 113 - 71. That matters for choosing a value - the
     * scale is proportional, so it never quite reaches black and the label keeps its share of the
     * distance rather than converging on the shell. At -0.5 the shell reads 56 and the label 5.
     */
    private static final double CARTRIDGE_SHADE = -0.5;

    /** Padding between the label's edge and what is drawn on it. */
    private static final double CARD_PADDING = 5;


    /**
     * How deep the cartridge sits <strong>into</strong> the record, as a fraction of the record's own
     * ink height. <strong>This is the seat knob.</strong>
     *
     * <p>0 rests it on the record's back edge, 1 pushes it through to the front edge, and 0.5 - the
     * default - seats it on the record's top face, which is the same line the kart's wheels stand on
     * and is where the slot in the middle of the record is. The record is drawn <em>in front</em> of
     * the cartridge, so its near half closes over the cartridge's foot and the thing reads as
     * inserted rather than balanced.
     *
     * <p>A fraction of the ink rather than a count of pixels, for the same reason
     * {@link #RECORD_SURFACE} is: it is a position on the record, and it should stay that position
     * if the record is resized or the artwork replaced.
     */
    private static final double CARTRIDGE_SEAT = 0.5;

    /**
     * How much of the card's bottom edge the record covers when there is <em>no</em> cartridge art.
     *
     * <p>Only the fallback layout uses this. With a cartridge the seat is measured off the record -
     * see {@link #CARTRIDGE_SEAT}.
     */
    private static final double CARD_FOOT = 18;

    /**
     * How much of the record's width the kart takes up. <strong>This is the size knob.</strong>
     *
     * <p>Resolved through {@link #integerScale} rather than used directly, so the kart is still
     * magnified by a whole number - this only says which whole number to aim for, and it therefore
     * changes the picture in steps rather than continuously. Against the 64 pixel racer frames on a
     * {@value #DISK_SIZE} pixel record the thresholds are: up to 0.43 draws at 1x, 0.44 to 0.71 at
     * 2x, 0.72 to 1.00 at 3x. Anything in between lands on the same whole number as its neighbours,
     * which is the rule working rather than the value being ignored.
     */
    static final double RACER_SHARE = 0.65;

    /**
     * Where the record's top face is, as a fraction down its own ink.
     * <strong>This is the offset knob:</strong> it is what the kart stands on.
     *
     * <p>0.0 is the record's back edge, 1.0 its front edge, and 0.5 - the default - is the middle,
     * which is where the top face of a disk seen at this angle reads as being. Lower it to sit the
     * kart further back on the record, raise it to bring it forward.
     *
     * <p>A fraction of the <em>ink</em> rather than of the frame, so replacement artwork with
     * different margins puts the kart in the same place on the record rather than the same place in
     * a rectangle. See {@link SpriteSheet#opaqueBounds(int)}.
     */
    private static final double RECORD_SURFACE = 0.5;

    /**
     * How far the kart floats above that surface, as a fraction of the record's ink height.
     *
     * <p>Zero puts its wheels exactly on the line. Positive lifts it; negative sinks it into the
     * record. Kept separate from {@link #RECORD_SURFACE} because the two are different decisions -
     * one is where the record's face is, the other is whether the kart is touching it.
     */
    private static final double RACER_LIFT = -0.3;

    /**
     * How much the kart is darkened, from 0 for the artwork as drawn to -1 for black.
     *
     * <p>The kart is the one bright thing in a window that is otherwise a dark cartridge on a dark
     * record, and at full strength it reads as pasted on rather than as sitting in the scene. Taking
     * a little light off it lets its colours meet the record's instead of shouting over them.
     *
     * <p><strong>Narrow and deliberate.</strong> Moods must never tint sprite art - that rule stands
     * and is what stops a palette from recolouring the racers - but this is not a mood: it is a fixed
     * decision about one window's lighting, made once, in the same breath as
     * {@link #CARTRIDGE_SHADE}. The runner draws the same sprite through a {@code ColorAdjust} for
     * the star, so the technique is already established; what must not happen is a <em>palette</em>
     * reaching for it.
     */
    private static final double RACER_SHADE = -0.22;

    /** Sideways nudge of the kart from the middle of the record, in pixels. Positive is right. */
    private static final double RACER_NUDGE_X = 0;

    /** Gap between the window buttons and the top of the card. */
    private static final double ACTIONS_GAP = 4;

    /** Space left under the record so it is not clipped by the bottom of the stage. */
    private static final double BOTTOM_MARGIN = 4;

    /** Gap between the transport keys. */
    private static final double TRANSPORT_GAP = 5;

    /**
     * What share of the cartridge's width its label takes up.
     *
     * <p>Measured from the artwork by {@link SpriteSheet#darkRegion(int)} at runtime; this is only
     * the <strong>conservative figure the caption limits are derived from</strong>, so that those
     * limits can stay compile-time constants and be checked without a toolkit. The smoke test
     * measures the real label and reports it against {@link #CONTENT_WIDTH}: a cartridge whose label
     * is narrower than this would clip its own captions, and nothing else would say so.
     */
    private static final double LABEL_WIDTH_SHARE = 0.47;

    /** Width the label has for its contents, at {@link #CARD_WIDTH}. */
    static final double CONTENT_WIDTH =
            Math.floor(CARD_WIDTH * LABEL_WIDTH_SHARE) - 2 * CARD_PADDING;

    /**
     * Song title size. Whole pixels: a fractional size makes a pixel font blurry.
     *
     * <p>Small even by this interface's standards, because a cartridge label is a narrow thing and
     * every pixel of it costs two pixels of window. Long titles are shortened and carry the whole
     * value in a tooltip, exactly as they do in the library.
     */
    static final double TITLE_SIZE = 8;

    /** Artist size. */
    static final double ARTIST_SIZE = 7;

    /** Size of the elapsed and total readouts. */
    static final double TIME_SIZE = 7;

    /** Size the application name would be drawn at, if this window drew it. */
    static final double NAME_SIZE = 9;

    /** Characters of the title that fit the card before it has to be shortened. */
    static final int TITLE_LIMIT = charBudget(CONTENT_WIDTH, TITLE_SIZE);

    /** Characters of the artist line that fit the card. */
    static final int ARTIST_LIMIT = charBudget(CONTENT_WIDTH, ARTIST_SIZE);

    /**
     * Width of the compact strip, in pixels.
     *
     * <p>Chosen for the transport plus a title worth reading, and <em>not</em> tied to the
     * cartridge: with the artwork away there is no label to fit inside, so the title gets the whole
     * strip rather than the 48% of a window the label was.
     */
    static final double COMPACT_WIDTH = 210;

    /**
     * Padding inside the compact strip.
     *
     * <p>Set here rather than in the stylesheet on purpose: CSS outranks a value set in code, so a
     * padding there would quietly replace the one the title was measured against.
     */
    static final double COMPACT_PADDING = 8;

    /**
     * Width of the frame the compact strip draws round itself, matching {@code .pixel-window}.
     *
     * <p>Subtracted below because <strong>a border is part of a region's insets</strong>: the strip
     * is 210 wide, but a child laid out inside it gets 210 less the padding <em>and</em> less this,
     * twice. Leaving it out is six pixels the title thinks it has and does not, which is exactly one
     * glyph - and one glyph over is the difference between a title that scrolls and one the label
     * cuts short a second time.
     */
    static final double COMPACT_BORDER = 3;

    /** Width the compact strip has for its contents, inside both its padding and its frame. */
    static final double COMPACT_CONTENT_WIDTH =
            COMPACT_WIDTH - 2 * COMPACT_PADDING - 2 * COMPACT_BORDER;

    /** Characters of the title that fit the compact strip. */
    static final int COMPACT_TITLE_LIMIT = charBudget(COMPACT_CONTENT_WIDTH, TITLE_SIZE);

    /**
     * What separates the end of a scrolling title from its own beginning.
     *
     * <p>Wide enough to read as a gap rather than as part of the words. Without one a title that
     * happens to end and start on similar letters runs into itself and stops being a title.
     */
    private static final String MARQUEE_GAP = "   -   ";

    /**
     * How fast a title too long for the strip slides past, in characters per second.
     *
     * <p>Slow enough to read at a glance, fast enough to get round a long title before the song
     * changes. In a fixed-width font a character is a whole step, so this is literally how many
     * steps a second it takes.
     */
    private static final double MARQUEE_CHARS_PER_SECOND = 3;

    /**
     * The colours the compact title cycles through, as roles.
     *
     * <p>The same walk the runner's star takes, and made of roles for the same reason: reaching for
     * {@code Color.hsb} would be shorter and would put six colours into the interface that no mood
     * could ever reach, which is exactly the debt ground rule 7 exists to prevent. This way it
     * restyles itself when the mood system lands.
     */
    private static final PaletteRole[] RAINBOW = {
            PaletteRole.PRIMARY, PaletteRole.POSITIVE, PaletteRole.METER_LOW,
            PaletteRole.ACCENT, PaletteRole.HIGHLIGHT, PaletteRole.NEGATIVE,
    };

    /**
     * How much of the way towards {@link PaletteRole#TEXT_PRIMARY} the title is lifted on a beat.
     *
     * <p>Deliberately small. The colour walk is <strong>continuous</strong> - it is interpolated
     * between neighbouring roles rather than stepped between them - so what the beat adds is a lift
     * and a fall on top of a smooth cycle, never a flash. At any sane tempo that is 2 to 3 changes a
     * second, which is inside the cap section 8b puts on beat reactivity; a hard switch of colour per
     * beat would not be.
     */
    private static final double BEAT_LIFT = 0.35;

    /** Beats a second the rainbow falls back to when the track has not been analysed yet. */
    private static final double FALLBACK_BPS = 2;

    /**
     * Turns of the record per second.
     *
     * <p>Chosen against the artwork rather than in the abstract: the sheet is thirteen frames of one
     * revolution, so this is a little under a turn a second - fast enough to read as spinning from
     * the corner of an eye, slow enough that consecutive frames do not alias into standing still.
     */
    private static final double DISK_FPS = 9;

    /**
     * How often the position readout and the transport captions are brought up to date.
     *
     * <p>Ten times a second, as in {@link PlaybackBar} and for the same reason: the progress line
     * moves well under a pixel and the clock under a digit between frames, so repainting them sixty
     * times a second is work nobody can see. The record is checked every frame and repainted only
     * when its frame actually changes, which while paused is never.
     */
    private static final long READOUT_INTERVAL_NANOS = 100_000_000L;

    /** Longest side the cover is decoded at; it is only ever drawn at the card's width. */
    private static final double COVER_DECODE_SIZE = 256;

    /** Inset between the cover frame's edge and the artwork, so the image never paints over it. */
    private static final double COVER_INSET = 5;

    /**
     * Smallest the cover is allowed to shrink to when the label runs short of room.
     *
     * <p>Past this it stops being album art and becomes a smudge, at which point the label is better
     * off overflowing visibly - which somebody will notice and fix - than looking deliberate.
     */
    private static final double MIN_COVER_SIZE = 32;

    private final AppState state;
    private final Player player;
    private final PlaybackEngine engine;
    private final AssetRegistry assets;

    /** Where the tempo comes from, so the compact title's rainbow keeps time with the music. */
    private final BeatmapService beatmaps;

    private final VBox card = new VBox(6);
    private final HBox actions;

    /** The window this fills, so the compact toggle can resize it around its new contents. */
    private final Stage stage;

    /** The rows the compact view puts away, kept so they can be hidden and brought back. */
    private StackPane progressRow;
    private HBox timesRow;

    /** The transport, kept so its keys can be re-sized to whichever layout is showing. */
    private HBox transportRow;

    /** The button whose caption flips between collapsing to the strip and coming back. */
    private Button compactToggle;

    /** Whether the cartridge and the record are put away, leaving the song and the transport. */
    private boolean compact;

    /** The current song's full title, before any shortening or scrolling. */
    private String fullTitle = "";

    /** What the scrolling title last read, so it is only rewritten when it would differ. */
    private String marqueeShowing = "";
    private final Canvas disk = new Canvas(DISK_SIZE, DISK_SIZE);

    /**
     * How wide and tall the record is <strong>actually drawn</strong>, which is its frame times a
     * whole-number scale and so is not usually exactly {@link #DISK_SIZE}.
     *
     * <p>The canvas is sized to this rather than to the requested figure. At a requested 254 the
     * nearest whole scale is 8, which draws 256 - and a 256 pixel sprite centred on a 254 pixel
     * canvas loses a pixel off each side. Clipping artwork to honour a number nobody can see is the
     * wrong way round.
     */
    private final double diskSpanX;
    private final double diskSpanY;
    private final SpriteAnimation diskAnimation;

    private final SpriteSheet cartridgeSheet;
    private final ImageView cartridge = new ImageView();

    /**
     * The cartridge's label in the artwork's own coordinates, or {@code null} when there is no
     * cartridge art to put anything on.
     */
    private final Rectangle2D label;

    /** Label height the cover was last sized against, so the fit is not recomputed every pass. */
    private double fittedLabelHeight = -1;

    /** How big the cover is currently drawn, so a placeholder built later matches it. */
    private double coverSide = CONTENT_WIDTH;

    /**
     * An invisible surface over the part of the record that hangs below the card, which drags the
     * window. See the constructor for why the record itself must not do this.
     */
    private final Region recordGrip = new Region();

    private final ImageView coverImage = new ImageView();
    private final StackPane coverHolder = new StackPane();

    private final Label title = new Label();
    private final Label artist = new Label();
    private final Label elapsed = new Label(PlaybackBar.formatTime(Duration.ZERO));
    private final Label total = new Label(PlaybackBar.formatTime(Duration.ZERO));
    private final Region progressFill = new Region();

    private final Button previousButton = new Button("<<");
    private final Button playButton = new Button();
    private final Button nextButton = new Button(">>");
    private final StackPane previousHolder = new StackPane(previousButton);

    /**
     * The kart's shading, held rather than built per repaint.
     *
     * <p>One instance mutated never - a {@code ColorAdjust} allocated inside the draw would be an
     * object seventeen times a second for as long as the window is open, for a value that never
     * changes.
     */
    private final ColorAdjust racerShade = new ColorAdjust(0, 0, RACER_SHADE, 0);

    /** Smoothed playback position: what the record and the kart are drawn from. */
    private final SmoothClock clock = new SmoothClock();

    private SpriteSheet racerSheet;
    private int diskFrame = -1;
    private int racerFrame = -1;

    private AnimationTimer timer;
    private long lastReadoutNanos;

    private Runnable onExpand = () -> { };
    private Runnable onQuit = () -> { };

    /**
     * Builds the companion window.
     *
     * @param state  the state shared with the fullscreen window; must not be {@code null}
     * @param player the running order to step through; must not be {@code null}
     * @param engine the audio the transport controls; must not be {@code null}
     * @param assets where the record and the racer sheets come from; must not be {@code null}
     * @param beatmaps the analysis the compact title's rainbow keeps time with; must not be
     *                 {@code null}
     * @param stage  the undecorated window this fills, which the card drags; must not be
     *               {@code null}
     */
    public MiniPlayerView(AppState state, Player player, PlaybackEngine engine,
                          AssetRegistry assets, BeatmapService beatmaps, Stage stage) {
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.assets = Objects.requireNonNull(assets, "assets must not be null");
        this.beatmaps = Objects.requireNonNull(beatmaps, "beatmaps must not be null");
        Objects.requireNonNull(stage, "stage must not be null");

        this.diskAnimation = SpriteAnimation.looping(assets.sheet(AssetKind.DISK), DISK_FPS);
        int recordScale = integerScale(DISK_SIZE, diskAnimation.sheet().frameHeight());
        this.diskSpanX = diskAnimation.sheet().frameWidth() * recordScale;
        this.diskSpanY = diskAnimation.sheet().frameHeight() * recordScale;
        disk.setWidth(diskSpanX);
        disk.setHeight(diskSpanY);
        this.racerSheet = assets.racer(state.getRacer());
        this.cartridgeSheet = assets.sheet(AssetKind.CARTRIDGE);
        this.label = cartridgeSheet.isPlaceholder()
                ? null
                : cartridgeSheet.darkRegion(0).orElse(null);

        // Nothing at all: no background, no border, no rounded box. Everything outside the card and
        // the record has to be see-through, and a fill here of any kind puts the frame back.
        getStyleClass().add("mini-player");

        this.stage = stage;
        this.actions = buildActions(stage);
        buildCard();
        buildCartridge();
        // The cartridge behind, its label's contents on top of it, then the record over its foot,
        // then the window buttons over everything.
        getChildren().addAll(cartridge, card, disk, recordGrip, actions);

        // ---------------------------------------------------------------
        // Why the record cannot take the mouse, and why there is a grip
        // ---------------------------------------------------------------
        // A Canvas is picked on its whole rectangle whatever it has drawn in it, and this one is
        // 224 square and reaches a long way up over the card - far enough to cover the transport.
        // Left alone it silently swallowed every click on play, previous and next: the buttons
        // highlighted on hover (that is the card underneath) and then did nothing, which is the most
        // confusing form a dead control can take. Nothing throws and no screenshot shows it.
        //
        // So the record never takes the mouse at all, and the window is dragged by the card and by a
        // transparent grip laid over the part of the record that hangs *below* the card - where it
        // cannot be in front of anything.
        disk.setMouseTransparent(true);
        recordGrip.getStyleClass().add("mini-grip");
        PixelDialog.dragBy(card, stage);
        PixelDialog.dragBy(recordGrip, stage);
        // An ImageView picks on the artwork's own alpha, so this drags by the cartridge's shape and
        // lets a click on the transparent corners past it - and it is behind the label's contents,
        // so it can never be in front of a control.
        PixelDialog.dragBy(cartridge, stage);

        // Both windows read one observable rather than keeping a copy each. Changing the racer in
        // the fullscreen window has to change the sprite riding the record here at the same instant.
        state.currentSongProperty().addListener((observable, was, now) -> showSong(now));
        state.racerProperty().addListener((observable, was, now) -> {
            racerSheet = assets.racer(now == null ? state.getRacer() : now);
            redrawDisk();
        });
        player.addListener((mode, song) -> refresh());

        showSong(state.getCurrentSong());
        refresh();
        redrawDisk();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * Builds the three window buttons that sit above the card.
     *
     * <p>There is no system chrome and no title bar to hang them from, so they float on the
     * transparent ground on top of everything else. Collapse puts the artwork away, hide sends the
     * window to the dock, expand brings the fullscreen window back, quit closes the application.
     * Only the last is destructive, so only the last carries the red hover.
     *
     * @param stage the window they act on
     * @return the button row
     */
    private HBox buildActions(Stage stage) {
        compactToggle = windowButton("^", "", () -> setCompact(!compact));
        Button hide = windowButton("_", "Send the player to the dock", () -> stage.setIconified(true));
        Button expand = windowButton("[]", "Back to the full window\nF7", () -> onExpand.run());
        Button quit = windowButton("X", "Quit " + AppConfig.APP_NAME_SHORT, () -> onQuit.run());
        quit.getStyleClass().add("close-button");

        HBox row = new HBox(4, compactToggle, hide, expand, quit);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.getStyleClass().add("mini-actions");
        return row;
    }

    /**
     * Puts the artwork away, or brings it back.
     *
     * <p><strong>The compact view is the whole player minus everything that is not a control.</strong>
     * The cartridge, the record, the kart, the cover, the progress line and the clock all go; what is
     * left is the song's name and the three keys that move through it. That is the state for somebody
     * working with the window parked in a corner, where the artwork is the part costing screen space
     * rather than the part earning it.
     *
     * <p>The strip is <strong>wider than the cartridge's label</strong>, which is the pleasant
     * surprise here: with no cartridge to fit inside, the title has the whole window instead of the
     * 48% of it the label was. Compact is where long titles read best.
     *
     * <p>The window is resized around its new contents rather than left at its old size with a hole
     * in it - it is sized to its content everywhere else too, and its top left stays put so a window
     * somebody has positioned does not walk across the desktop each time this is pressed.
     *
     * @param wanted whether to show the compact strip
     */
    public void setCompact(boolean wanted) {
        compact = wanted;
        for (javafx.scene.Node node : new javafx.scene.Node[]{
                cartridge, disk, recordGrip, coverHolder, artist, progressRow, timesRow}) {
            node.setVisible(!compact);
            // Unmanaged too, or the column still reserves the height of everything it is not drawing.
            node.setManaged(!compact);
        }
        card.getStyleClass().removeAll("mini-compact", "pixel-window");
        card.setPadding(new Insets(compact ? COMPACT_PADDING : CARD_PADDING));
        if (compact) {
            card.getStyleClass().addAll("pixel-window", "mini-compact");
        } else if (label == null) {
            // No cartridge art to be the frame, so the card draws its own - as it always did.
            card.getStyleClass().add("pixel-window");
        }
        // Coloured here as well as on the tick, or entering compact shows a tenth of a second of
        // plain white text before the first frame gets to it - which reads as the effect starting
        // late rather than as it starting.
        paintTitle(compact ? beatColour(engine.positionSeconds()) : null);
        compactToggle.setText(compact ? "v" : "^");
        compactToggle.setTooltip(new Tooltip(compact
                ? "Bring the cartridge back\nF8"
                : "Collapse to the song and the transport\nF8"));

        // Re-size and re-shorten to the width the new layout gives, then lay the window out around it.
        fitRowsTo(compact ? COMPACT_CONTENT_WIDTH : CONTENT_WIDTH);
        showSong(state.getCurrentSong());
        fittedLabelHeight = -1;
        requestLayout();
        applyCss();
        layout();
        if (stage.isShowing()) {
            stage.sizeToScene();
        }
    }

    /** @return whether the artwork is put away */
    public boolean isCompact() {
        return compact;
    }

    /**
     * @param caption the button text
     * @param tooltip what it does, since a one-glyph caption cannot say so
     * @param action  what to run when it is pressed
     * @return a window button
     */
    private static Button windowButton(String caption, String tooltip, Runnable action) {
        Button button = new Button(caption);
        button.getStyleClass().add("window-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        // Nothing on this window is reached by keyboard traversal, and a focused button would take
        // the space bar away from play/pause - which is the one key a companion window must answer.
        button.setFocusTraversable(false);
        return button;
    }

    /**
     * Sets up the cartridge the window is built on.
     *
     * <p><strong>Scaled smoothly, and that is not the exception ground rule 8 forbids.</strong> That
     * rule is about hand-drawn 8-bit sprites, where a fractional scale interpolates between colours
     * that were placed one pixel at a time and the result reads as bad artwork. This is not one:
     * measured, it is 500x575 with <strong>1830 colours, per-pixel grain across the whole body and
     * not a single flat block</strong> - a high-resolution illustration of an object, in the same
     * category as album art, which this application has always scaled to fit. Drawn at a whole
     * number it would be 500 pixels wide in a 224 pixel window; drawn at a fraction with smoothing
     * off, its ridges alias into moire. The record, the kart and every real sprite still go through
     * {@link #integerScale} with smoothing off, and this is the only thing in the window that does
     * not.
     */
    private void buildCartridge() {
        if (label == null) {
            // No cartridge art, or art with nothing on it to put anything in. The card draws its own
            // frame instead and the window is still perfectly usable - ground rule 5.
            card.getStyleClass().add("pixel-window");
            return;
        }
        cartridge.setImage(cartridgeSheet.image());
        cartridge.setViewport(cartridgeSheet.viewport(0));
        cartridge.setPreserveRatio(false);
        cartridge.setSmooth(true);

        // Darkened as a brightness adjustment rather than by editing the artwork, so one number
        // tunes it and the grain survives. This is the one effect on a node in the application, and
        // the caching is what keeps it that way honestly: the image never changes, so it is
        // rasterised once instead of being recomposited on every frame the record draws. The warning
        // against node effects is about exactly that per-frame cost.
        cartridge.setEffect(new ColorAdjust(0, 0, CARTRIDGE_SHADE, 0));
        cartridge.setCache(true);
        cartridge.setCacheHint(CacheHint.SPEED);
    }

    /** Builds the label's contents: cover, song, progress and transport, in a column. */
    private void buildCard() {
        card.getStyleClass().add("mini-card");
        card.setPadding(new Insets(CARD_PADDING));
        // Centred, not top-aligned. The cover can only grow until it is as wide as the label, so on
        // a tall label there is height left over that it cannot take; piled up underneath the
        // transport that reads as a band of label somebody forgot to fill, and split evenly above
        // and below it reads as a margin.
        card.setAlignment(Pos.CENTER);

        progressRow = buildProgress();
        timesRow = buildTimes();
        transportRow = buildTransport();
        card.getChildren().addAll(buildCover(), title, artist, progressRow, timesRow, transportRow);

        title.getStyleClass().add("mini-title");
        artist.getStyleClass().add("mini-artist");
        fitRowsTo(CONTENT_WIDTH);
    }

    /**
     * Sizes everything that spans the column to whichever layout is showing.
     *
     * <p>Without this the compact strip inherits the <em>cartridge label's</em> width, which is a
     * little over half of it: the title is cut off at fourteen characters where twenty-five fit, and
     * the transport huddles in the middle of a strip built to hold it. Nothing overflows and nothing
     * throws - it just quietly wastes most of the window it was given.
     *
     * @param contentWidth the width the column actually has
     */
    private void fitRowsTo(double contentWidth) {
        title.setMaxWidth(contentWidth);
        artist.setMaxWidth(contentWidth);
        if (transportRow != null) {
            transportRow.setMaxWidth(contentWidth);
        }
        double buttonWidth = Math.floor((contentWidth - 2 * TRANSPORT_GAP) / 3);
        for (Button button : new Button[]{previousButton, playButton, nextButton}) {
            button.setMinWidth(buttonWidth);
            button.setPrefWidth(buttonWidth);
            button.setMaxWidth(buttonWidth);
        }
    }

    /**
     * Builds the cover frame. Square, and sized to whatever the label has left - see
     * {@link #fitCoverTo(double, double)}.
     *
     * @return the cover
     */
    private StackPane buildCover() {
        coverHolder.getStyleClass().add("mini-cover");
        coverImage.setPreserveRatio(true);
        sizeCover(CONTENT_WIDTH);
        return coverHolder;
    }

    /**
     * @param side how wide and tall to draw the cover
     */
    private void sizeCover(double side) {
        coverSide = side;
        coverHolder.setMinSize(side, side);
        coverHolder.setPrefSize(side, side);
        coverHolder.setMaxSize(side, side);
        coverImage.setFitWidth(side - COVER_INSET * 2);
        coverImage.setFitHeight(side - COVER_INSET * 2);
        for (javafx.scene.Node child : coverHolder.getChildren()) {
            if (child instanceof Region placeholder) {
                placeholder.setMinSize(side - COVER_INSET * 2, side - COVER_INSET * 2);
                placeholder.setPrefSize(side - COVER_INSET * 2, side - COVER_INSET * 2);
                placeholder.setMaxSize(side - COVER_INSET * 2, side - COVER_INSET * 2);
            }
        }
    }

    /**
     * Gives the cover whatever height the rest of the label's contents did not want.
     *
     * <p>The cover is the only thing here that can be any size, so it is the thing that absorbs the
     * difference. Measuring is the only honest way to work out how much that is: the text and the
     * transport are as tall as the font makes them, and a number written down here would be right
     * for one font and quietly wrong for the fallback.
     *
     * <p>Only recomputed when the label's height actually changes, because setting sizes during
     * layout asks for another layout - and doing that unconditionally never stops.
     *
     * @param width  the label's content width
     * @param height the label's full height; the card's own padding is inside the measurement
     */
    private void fitCoverTo(double width, double height) {
        if (Math.abs(height - fittedLabelHeight) < 0.5) {
            return;
        }
        fittedLabelHeight = height;

        sizeCover(0);
        double everythingElse = card.prefHeight(width);
        // The gap the cover's own row would have added is already in that figure.
        double side = Math.clamp(height - everythingElse, MIN_COVER_SIZE, width);
        sizeCover(side);
    }

    /** @return the progress line */
    private StackPane buildProgress() {
        Region track = new Region();
        track.getStyleClass().add("mini-progress-track");
        track.setMinSize(CONTENT_WIDTH, 6);
        track.setPrefSize(CONTENT_WIDTH, 6);
        track.setMaxSize(CONTENT_WIDTH, 6);

        progressFill.getStyleClass().add("mini-progress-fill");
        progressFill.setMinHeight(6);
        progressFill.setPrefHeight(6);
        progressFill.setMaxHeight(6);
        // Pinned to its preferred width, which the readout rewrites: left to grow, a stack pane
        // would stretch the fill across the whole track and the line would always read as finished.
        progressFill.setMaxWidth(Region.USE_PREF_SIZE);
        progressFill.setPrefWidth(0);

        StackPane progress = new StackPane(track, progressFill);
        progress.setAlignment(Pos.CENTER_LEFT);
        progress.setMaxWidth(CONTENT_WIDTH);
        return progress;
    }

    /** @return the elapsed and total readouts, pushed to opposite ends */
    private HBox buildTimes() {
        elapsed.getStyleClass().add("mini-time");
        total.getStyleClass().add("mini-time");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox times = new HBox(elapsed, spacer, total);
        times.setMinWidth(CONTENT_WIDTH);
        times.setPrefWidth(CONTENT_WIDTH);
        times.setMaxWidth(CONTENT_WIDTH);
        times.setAlignment(Pos.CENTER_LEFT);
        return times;
    }

    /**
     * Builds back, play/pause and forward.
     *
     * <p>Three keys share the card's width in equal thirds, given explicitly rather than left to the
     * layout: the play caption changes between two glyphs and a row that resized itself would shuffle
     * the other two sideways every time the music was paused.
     *
     * <p>Whether back works is read off the mode, exactly as the main bar reads it: a queue cannot go
     * backwards, so the control says so instead of throwing at whoever presses it. The tooltip sits
     * on a holder around the button because a disabled node receives no mouse events, and a dead
     * control with no explanation is worse than none.
     *
     * @return the transport row
     */
    private HBox buildTransport() {
        previousButton.setOnAction(event -> player.previous());
        playButton.setOnAction(event -> {
            engine.toggle();
            refresh();
        });
        nextButton.setOnAction(event -> player.next());
        for (Button button : new Button[]{previousButton, playButton, nextButton}) {
            button.setFocusTraversable(false);
        }
        playButton.setTooltip(new Tooltip("Play or pause\nSpace"));
        nextButton.setTooltip(new Tooltip("Next song\nRight arrow"));

        HBox row = new HBox(TRANSPORT_GAP, previousHolder, playButton, nextButton);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("mini-transport");
        // Widths come from fitRowsTo, which is told which layout is showing.
        return row;
    }

    // ------------------------------------------------------------------
    // Placing the card and the record
    // ------------------------------------------------------------------

    /**
     * Lays the three pieces out by hand.
     *
     * <p>Not a row or a column, because the record deliberately overflows the card on three sides
     * and every layout pane in the toolkit exists to stop exactly that from happening.
     *
     * <p>The record's <em>visible</em> top edge is put {@link #CARD_FOOT} pixels above the bottom of
     * the card, which is a different thing from the top of its frame - see
     * {@link SpriteSheet#opaqueBounds(int)}. Positioning by frame would leave the record floating
     * well below the card with a gap of nothing between them.
     */
    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double actionsHeight = actions.prefHeight(-1);
        if (compact) {
            layoutCompact(width, actionsHeight);
            return;
        }
        double cardHeight = cardHeight();
        double cardX = Math.round((width - CARD_WIDTH) / 2);
        double cardY = actionsHeight + ACTIONS_GAP;

        // An ImageView is not a resizable node, so resizeRelocate would move it and leave it at the
        // artwork's own 500 pixels - which is most of a window three times narrower than that. It is
        // sized by its fit, and only positioned by the layout.
        cartridge.setFitWidth(CARD_WIDTH);
        cartridge.setFitHeight(cardHeight);
        cartridge.relocate(cardX, cardY);

        // The label, brought from the artwork's coordinates into this pane's.
        Rectangle2D panel = labelRect(cardX, cardY, cardHeight);
        fitCoverTo(panel.getWidth() - 2 * CARD_PADDING, panel.getHeight());
        card.resizeRelocate(panel.getMinX(), panel.getMinY(),
                panel.getWidth(), panel.getHeight());

        double actionsWidth = actions.prefWidth(-1);
        // Right-aligned with the cartridge, so the three of them read as belonging to it.
        actions.resizeRelocate(cardX + CARD_WIDTH - actionsWidth, 0, actionsWidth, actionsHeight);

        double diskY = Math.round(cardY + cardHeight - seatDepth() - recordTopInset());
        disk.relocate(Math.round((width - diskSpanX) / 2), diskY);

        // Only the part of the record below the cartridge, so the grip can never be in front of a
        // control. Above that line the cartridge is the drag handle anyway.
        double cardBottom = cardY + cardHeight;
        recordGrip.resizeRelocate(0, cardBottom, width,
                Math.max(0, diskY + recordBottomExtent() - cardBottom));
    }

    /**
     * Lays out the compact strip: the window buttons, and the song under them.
     *
     * @param width         the pane's width
     * @param actionsHeight how tall the window buttons are
     */
    private void layoutCompact(double width, double actionsHeight) {
        double cardX = Math.round((width - COMPACT_WIDTH) / 2);
        double cardY = actionsHeight + ACTIONS_GAP;
        card.resizeRelocate(cardX, cardY, COMPACT_WIDTH, card.prefHeight(COMPACT_WIDTH));

        double actionsWidth = actions.prefWidth(-1);
        actions.resizeRelocate(cardX + COMPACT_WIDTH - actionsWidth, 0, actionsWidth, actionsHeight);
    }

    /**
     * Returns where the song information goes, in this pane's coordinates.
     *
     * <p>The cartridge's own label when there is a cartridge, and the whole card when there is not.
     *
     * @param cardX      left edge of the cartridge
     * @param cardY      top edge of the cartridge
     * @param cardHeight how tall the cartridge is drawn
     * @return the panel to lay the contents into
     */
    Rectangle2D labelRect(double cardX, double cardY, double cardHeight) {
        if (label == null) {
            return new Rectangle2D(cardX, cardY, CARD_WIDTH, cardHeight - CARD_FOOT);
        }
        double scaleX = CARD_WIDTH / cartridgeSheet.frameWidth();
        double scaleY = cardHeight / cartridgeSheet.frameHeight();
        return new Rectangle2D(
                Math.round(cardX + label.getMinX() * scaleX),
                Math.round(cardY + label.getMinY() * scaleY),
                Math.round(label.getWidth() * scaleX),
                Math.round(label.getHeight() * scaleY));
    }

    /**
     * @return how tall the card is drawn - the cartridge at its own aspect, or what the contents
     *         need when there is no cartridge
     */
    private double cardHeight() {
        if (label == null) {
            return card.prefHeight(CARD_WIDTH) + CARD_FOOT;
        }
        // The artwork's own proportions. Stretching a picture of an object to fit a layout is the
        // one thing that would make it stop reading as an object.
        return Math.round(CARD_WIDTH * cartridgeSheet.frameHeight() / cartridgeSheet.frameWidth());
    }

    @Override
    protected double computePrefWidth(double height) {
        if (compact) {
            return COMPACT_WIDTH;
        }
        // Whichever of the two is wider sets the window's width - which of them that is depends on
        // CARD_TO_DISK_RATIO, and both arrangements are legitimate.
        return Math.max(DISK_SIZE, CARD_WIDTH);
    }

    @Override
    protected double computePrefHeight(double width) {
        if (compact) {
            return actions.prefHeight(-1) + ACTIONS_GAP + card.prefHeight(COMPACT_WIDTH);
        }
        double cardBottom = actions.prefHeight(-1) + ACTIONS_GAP + cardHeight();
        // Down to the record's own bottom edge rather than its frame's, which would leave a band of
        // nothing under the window and put the shadow of a rectangle back on the desktop.
        return cardBottom - seatDepth() - recordTopInset() + recordBottomExtent() + BOTTOM_MARGIN;
    }

    /**
     * How far the cartridge's foot descends past the record's top edge, in pixels.
     *
     * <p>Zero would stand it on the back edge of the record - which from this angle is the far side,
     * and reads as the cartridge floating behind it rather than sitting in it.
     *
     * @return the seat depth, or the fixed foot when there is no cartridge to seat
     */
    private double seatDepth() {
        if (label == null) {
            return CARD_FOOT;
        }
        return diskAnimation.sheet().opaqueBounds(0).getHeight() * diskScale() * CARTRIDGE_SEAT;
    }

    /** @return transparent rows above the record inside its frame, at the scale it is drawn */
    private double recordTopInset() {
        return diskAnimation.sheet().opaqueBounds(0).getMinY() * diskScale();
    }

    /** @return how far the record's bottom edge is from the top of its frame, at the drawn scale */
    private double recordBottomExtent() {
        Rectangle2D bounds = diskAnimation.sheet().opaqueBounds(0);
        return (bounds.getMinY() + bounds.getHeight()) * diskScale();
    }

    /** @return the whole-number magnification the record is drawn at */
    private int diskScale() {
        return integerScale(DISK_SIZE, diskAnimation.sheet().frameHeight());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Starts the record turning and the readout ticking.
     *
     * <p>Called when the window is shown. Safe to call more than once.
     */
    public void start() {
        if (timer != null) {
            return;
        }
        // A song may have changed, or the track moved on, while this window was away.
        clock.reset();
        refresh();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now);
            }
        };
        timer.start();
    }

    /**
     * Stops the record and the readout.
     *
     * <p>Called when the window is hidden and on shutdown. A timer left running would keep polling
     * an audio source and repainting a canvas nobody can see.
     */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /** @return whether the frame loop is running */
    public boolean isRunning() {
        return timer != null;
    }

    /**
     * @param action what to run when the expand button or its shortcut is used
     */
    public void setOnExpand(Runnable action) {
        this.onExpand = action == null ? () -> { } : action;
    }

    /**
     * @param action what to run when the quit button is used
     */
    public void setOnQuit(Runnable action) {
        this.onQuit = action == null ? () -> { } : action;
    }

    // ------------------------------------------------------------------
    // Per frame
    // ------------------------------------------------------------------

    /**
     * One frame: advance the record if it has moved on, and refresh the readout ten times a second.
     *
     * <p>The repaint is gated on the frame indices rather than done unconditionally. While the music
     * plays that is about seventeen repaints a second between the two sheets, and while it is paused
     * it is none at all - which matters more here than anywhere else in the application, because
     * this is the window that is left open.
     *
     * @param nowNanos the frame timestamp
     */
    private void tick(long nowNanos) {
        double seconds = clock.advance(engine.positionSeconds(), engine.isPlaying());

        int nextDisk = diskAnimation.frameIndexAt(seconds);
        int nextRacer = RacerFrame.driving(seconds);
        if (nextDisk != diskFrame || nextRacer != racerFrame) {
            diskFrame = nextDisk;
            racerFrame = nextRacer;
            redrawDisk();
        }

        if (nowNanos - lastReadoutNanos >= READOUT_INTERVAL_NANOS) {
            lastReadoutNanos = nowNanos;
            updateReadout();
            if (compact) {
                // Wall time for the scroll and the playback clock for the colour, on purpose. A
                // title that stopped moving when the music paused reads as a stuck window, where a
                // rainbow that stops reads as the beat stopping - which it has.
                scrollTitle(nowNanos / 1_000_000_000d);
                paintTitle(beatColour(seconds));
            }
        }
    }

    /**
     * Slides a title too long for the compact strip past, one character at a time.
     *
     * <p>Only when it does not fit: a title with room to spare sits still, because motion that
     * carries no information is just something moving in the corner of the eye.
     *
     * <p>The window is cut out of the title <em>followed by its own beginning</em>, so it wraps
     * seamlessly instead of scrolling off and starting again from an empty strip. In a fixed-width
     * font a character is a whole step, so this needs no measuring pass and can never land on half a
     * glyph.
     *
     * @param seconds a steadily increasing clock
     */
    private void scrollTitle(double seconds) {
        String window = marqueeWindow(fullTitle, marqueeLimit(), seconds);
        if (window != null && !window.equals(marqueeShowing)) {
            marqueeShowing = window;
            title.setText(window);
        }
    }

    /**
     * How many characters the scrolling title may show, measured against the real font.
     *
     * <p>{@link #COMPACT_TITLE_LIMIT} is the nominal answer and is what the unit tests hold; this is
     * the one the strip actually uses. The two differ because a glyph is not exactly one em, and
     * this is the one caption in the application where being a glyph out is visible: with no
     * ellipsis of its own to fall back on, a slice that does not fit is truncated a second time by
     * the label and the marquee scrolls a string already ending in dots.
     *
     * @return the number of characters that fit, never more than the nominal limit
     */
    private int marqueeLimit() {
        double advance = Fonts.advance(TITLE_SIZE);
        int measured = advance > 0
                ? (int) Math.floor(COMPACT_CONTENT_WIDTH / advance)
                : COMPACT_TITLE_LIMIT;
        return Math.max(1, Math.min(measured, COMPACT_TITLE_LIMIT));
    }

    /**
     * The slice of a scrolling title showing at a moment in time.
     *
     * <p>Static so the wrap can be checked without a toolkit, which is where the mistake would be:
     * the window is cut from the title <em>followed by a copy of itself</em>, and getting the bounds
     * wrong there is an exception at a random moment during a long song rather than anything a
     * screenshot would show.
     *
     * @param text    the full title
     * @param limit   how many characters the strip holds
     * @param seconds a steadily increasing clock
     * @return the characters to show, or {@code null} when the title fits and should not move
     */
    static String marqueeWindow(String text, int limit, double seconds) {
        if (text == null || limit <= 0 || text.length() <= limit) {
            return null;
        }
        String loop = text + MARQUEE_GAP;
        int offset = (int) Math.floorMod(
                (long) (seconds * MARQUEE_CHARS_PER_SECOND), loop.length());
        return (loop + loop).substring(offset, offset + limit);
    }

    /**
     * Colours the title, or hands it back to the stylesheet.
     *
     * <p><strong>An inline style, and it has to be.</strong> {@code setTextFill} looks like the
     * obvious way and does nothing here: JavaFX ranks an author stylesheet <em>above</em> a value
     * set in code, so {@code .mini-title}'s own {@code -fx-text-fill} wins and the rainbow never
     * appears - silently, with the title still perfectly legible in the ordinary colour. Only an
     * inline style outranks the stylesheet.
     *
     * <p>The colour is written out as {@code rgb(...)} rather than as hex, so no part of this reads
     * like a colour literal: what goes in is a role from the active palette, serialised.
     *
     * @param colour what to draw the title in, or {@code null} to go back to the stylesheet
     */
    private void paintTitle(Color colour) {
        if (colour == null) {
            title.setStyle("");
            return;
        }
        title.setStyle("-fx-text-fill: rgb("
                + (int) Math.round(colour.getRed() * 255) + ","
                + (int) Math.round(colour.getGreen() * 255) + ","
                + (int) Math.round(colour.getBlue() * 255) + ");");
    }

    /**
     * The compact title's colour: a rainbow walked in time with the track.
     *
     * <p>One step of the walk per beat, interpolated rather than stepped, with a lift towards
     * {@link PaletteRole#TEXT_PRIMARY} that peaks on the beat and falls away - so the beat arrives as
     * a swell in an already-moving colour rather than as a flash. Squared, so the lift is brief and
     * the fall is most of the beat.
     *
     * <p>Falls back to a fixed rate when the track has not been analysed yet, which is the first few
     * seconds of a song it has never seen. The rainbow simply keeps its own time until the real
     * tempo arrives.
     *
     * @param seconds the playback position
     * @return the colour to draw the title in
     */
    private Color beatColour(double seconds) {
        BeatmapService.Status status = beatmaps.status();
        double beatsPerSecond = status.isReady() && status.beatmap().bpm() > 0
                ? status.beatmap().bpm() / 60
                : FALLBACK_BPS;

        double beats = seconds * beatsPerSecond;
        double intoBeat = beats - Math.floor(beats);
        int from = (int) Math.floorMod((long) Math.floor(beats), RAINBOW.length);
        int to = (from + 1) % RAINBOW.length;

        Palette palette = Palette.active();
        // Mixed rather than stepped, and Palette.mix snaps the result back onto the 5-bit grid.
        Color walked = palette.mix(RAINBOW[from], RAINBOW[to], intoBeat);
        double lift = BEAT_LIFT * (1 - intoBeat) * (1 - intoBeat);
        return walked.interpolate(palette.color(PaletteRole.TEXT_PRIMARY), lift);
    }

    /**
     * Draws the record with the kart standing on it.
     *
     * <p>Both sprites are hand-drawn pixel art, so both are magnified by a whole number with
     * smoothing off. Nothing here is travelling towards a camera, so the narrow exception the runner
     * makes for entities on its road does not apply.
     *
     * <p>The kart is stood on {@link #RECORD_SURFACE} of the record's own ink and lifted by
     * {@link #RACER_LIFT}; those two and {@link #RACER_SHARE} are the three knobs for adjusting it.
     * Its wheels are the bottom of its <em>ink</em> rather than the bottom of its frame, and there
     * are ten transparent rows between the two - enough, uncorrected, to leave it visibly hovering.
     */
    private void redrawDisk() {
        GraphicsContext gc = disk.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.clearRect(0, 0, diskSpanX, diskSpanY);

        SpriteSheet diskSheet = diskAnimation.sheet();
        int frame = Math.max(0, diskFrame);
        int scale = diskScale();
        Rectangle2D record = diskSheet.opaqueBounds(frame);
        drawSprite(gc, diskSheet, frame, scale, 0, 0);

        // The line the kart's wheels sit on: a fraction down the record's own ink, less the lift.
        double surfaceY = (record.getMinY()
                + record.getHeight() * (RECORD_SURFACE - RACER_LIFT)) * scale;
        int racerScale = integerScale(DISK_SIZE * RACER_SHARE, racerSheet.frameHeight());
        Rectangle2D kart = racerSheet.opaqueBounds(RacerFrame.DRIVE_A.index());
        // Only the kart, and put back straight afterwards: an effect left on the context would
        // shade whatever the next repaint drew first, which is the record.
        gc.setEffect(racerShade);
        drawSprite(gc, racerSheet, Math.max(0, racerFrame), racerScale,
                diskSpanX / 2 + RACER_NUDGE_X - (kart.getMinX() + kart.getWidth() / 2) * racerScale,
                surfaceY - (kart.getMinY() + kart.getHeight()) * racerScale);
        gc.setEffect(null);
    }

    /**
     * Draws one frame of a sheet at a whole-number magnification.
     *
     * @param gc    the context to draw into
     * @param sheet the sheet to take a frame from
     * @param frame which frame
     * @param scale whole-number magnification
     * @param x     left edge of the destination frame
     * @param y     top edge of the destination frame
     */
    private static void drawSprite(GraphicsContext gc, SpriteSheet sheet, int frame, int scale,
                                   double x, double y) {
        Rectangle2D source = sheet.viewport(frame);
        gc.drawImage(sheet.image(),
                source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                // Whole pixels, so a nearest-neighbour blow-up never straddles a half pixel.
                Math.round(x), Math.round(y),
                Math.round(source.getWidth() * scale), Math.round(source.getHeight() * scale));
    }

    /** Brings the clock, the progress line and the play caption up to date. */
    private void updateReadout() {
        Duration at = engine.position();
        Duration length = engine.duration();
        elapsed.setText(PlaybackBar.formatTime(at));
        total.setText(PlaybackBar.formatTime(length));
        progressFill.setPrefWidth(progressWidth(CONTENT_WIDTH, at, length));

        playButton.setText(engine.isPlaying() ? "||" : "|>");
        playButton.setDisable(player.current() == null);
    }

    /**
     * How much of the progress line is filled.
     *
     * <p>Held apart from the node it sets so it can be checked without a toolkit. The case worth
     * checking is a track with no known length: a variable-bitrate file with no header cannot be
     * measured without decoding all of it, so a zero here is ordinary rather than exceptional, and
     * dividing by it would fill the line with a not-a-number that quietly draws nothing.
     *
     * @param columnWidth the full width of the line, in pixels
     * @param at          how far into the track playback has reached
     * @param length      the track's playing time, possibly zero
     * @return the filled width in whole pixels, never past either end of the line
     */
    static double progressWidth(double columnWidth, Duration at, Duration length) {
        if (at == null || length == null || length.isZero() || length.isNegative()) {
            return 0;
        }
        double fraction = Math.clamp(at.toMillis() / (double) length.toMillis(), 0d, 1d);
        return Math.round(columnWidth * fraction);
    }

    // ------------------------------------------------------------------
    // The song
    // ------------------------------------------------------------------

    /**
     * Brings the card back in step with the running order.
     *
     * <p>Capability is read off the mode rather than by asking which mode it is, which is the same
     * polymorphism the player itself relies on.
     */
    public final void refresh() {
        boolean canGoBack = player.canGoPrevious();
        previousButton.setDisable(!canGoBack);
        Tooltip.install(previousHolder, new Tooltip(player.mode().supportsPrevious()
                ? "Previous song\nLeft arrow"
                : "FIFO - no going back."));
        nextButton.setDisable(!player.canGoNext());
        updateReadout();
    }

    /**
     * Shows a song, or an empty card when there is none.
     *
     * <p>Long values are shortened rather than wrapped - there is one line for each and no room to
     * grow - and the full value goes into a tooltip, so nothing is lost.
     *
     * @param song the song now playing, or {@code null}
     */
    private void showSong(Song song) {
        if (song == null) {
            fullTitle = "";
            marqueeShowing = "";
            title.setText("-");
            title.setTooltip(null);
            artist.setText("");
            artist.setTooltip(null);
            showCover(null);
            return;
        }
        fullTitle = song.getTitle();
        marqueeShowing = "";
        String scrolled = compact ? marqueeWindow(fullTitle, marqueeLimit(), 0) : null;
        // A title that is going to scroll starts at its own beginning rather than showing an
        // ellipsis for the first tenth of a second and then jumping.
        title.setText(scrolled != null
                ? scrolled
                : LibraryView.ellipsize(fullTitle, compact ? COMPACT_TITLE_LIMIT : TITLE_LIMIT));
        title.setTooltip(new Tooltip(song.getTitle()));
        artist.setText(LibraryView.ellipsize(song.getArtist(), ARTIST_LIMIT));
        artist.setTooltip(new Tooltip(song.getArtist()));
        showCover(song.getCoverPath());
    }

    /**
     * Shows the cover art, centre-cropped, or a labelled placeholder when there is none.
     *
     * <p>Album art is square and this frame is square, so the image is cropped to its middle rather
     * than letterboxed. A cover that cannot be read is never an error - the application runs with no
     * artwork at all - but it is not left as a bare frame either: on a card this small an empty
     * outline is most of what there is to look at, and it reads as something that failed to draw.
     * The same magenta placeholder the library shows says the artwork is missing, on purpose.
     *
     * @param coverPath the cover image, or {@code null}
     */
    private void showCover(Path coverPath) {
        coverHolder.getChildren().clear();
        if (coverPath != null && Files.isReadable(coverPath)) {
            Image image = new Image(coverPath.toUri().toString(),
                    COVER_DECODE_SIZE, COVER_DECODE_SIZE, true, true);
            if (!image.isError()) {
                coverImage.setImage(image);
                coverImage.setViewport(
                        LibraryView.centeredSquare(image.getWidth(), image.getHeight()));
                coverHolder.getChildren().add(coverImage);
                return;
            }
            LOG.warning("Could not read the cover image at " + coverPath
                    + " - the companion window will show a placeholder");
        }

        // The colour is the library's, so the two placeholders cannot drift apart; the second class
        // only makes it small enough for this card.
        Label caption = new Label("NO\nART");
        caption.getStyleClass().addAll("cover-placeholder-label", "mini-cover-label");
        caption.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        StackPane placeholder = new StackPane(caption);
        placeholder.getStyleClass().add("cover-placeholder");
        double side = coverSide - COVER_INSET * 2;
        placeholder.setMinSize(side, side);
        placeholder.setPrefSize(side, side);
        placeholder.setMaxSize(side, side);
        coverHolder.getChildren().add(placeholder);
    }

    // ------------------------------------------------------------------
    // Arithmetic, kept static so it can be checked without a toolkit
    // ------------------------------------------------------------------

    /**
     * How many characters of a fixed-width pixel font fit across a column.
     *
     * <p>Press Start 2P is about one em per glyph, so an n-character string is roughly
     * {@code n * fontSize} pixels wide. Every caption limit in this window is derived from the space
     * it has to fit rather than guessed, because overflow in this font is invisible to a unit test
     * and obvious the moment somebody looks at the window - and this card is the narrowest thing in
     * the application.
     *
     * @param columnWidth space available, in pixels
     * @param fontSize    the size the text is drawn at
     * @return how many glyphs fit, at least one
     */
    static int charBudget(double columnWidth, double fontSize) {
        if (fontSize <= 0) {
            return 1;
        }
        return (int) Math.max(1, Math.floor(columnWidth / fontSize));
    }

    /**
     * Chooses a whole-number magnification that brings a sprite frame closest to a target size.
     *
     * <p>Whole numbers only, per ground rule 8: this artwork is hand-drawn at 8-bit sizes and a
     * fractional scale turns it to mush. Never below 1, so art larger than the space it is given is
     * drawn at its own size rather than shrunk into porridge.
     *
     * @param targetSize how big it should be, in pixels
     * @param frameSize  the size of one frame in the sheet
     * @return the magnification, at least 1
     */
    static int integerScale(double targetSize, double frameSize) {
        if (frameSize <= 0) {
            return 1;
        }
        return (int) Math.max(1, Math.round(targetSize / frameSize));
    }

    // ------------------------------------------------------------------
    // What is on screen, for the checks a picture cannot make
    // ------------------------------------------------------------------

    /**
     * @return how many characters the scrolling title shows, measured against the real font
     */
    public int measuredMarqueeLimit() {
        return marqueeLimit();
    }

    /** @return the size the song title is drawn at, and the nominal width of one of its glyphs */
    public static double titleSize() {
        return TITLE_SIZE;
    }

    /** @return how many characters the scrolling title is allowed by the nominal one-em estimate */
    public static int nominalMarqueeLimit() {
        return COMPACT_TITLE_LIMIT;
    }

    /**
     * @return the sheet the kart riding the record is currently drawn from
     */
    public SpriteSheet racerSheet() {
        return racerSheet;
    }

    /**
     * How far the cartridge's inlet is from lining up with the record, as it is actually drawn.
     *
     * <p>The width is set from {@link #CARTRIDGE_INLET_SHARE}, which is a figure measured off one
     * piece of artwork and frozen at compile time so the caption limits can be constants. This
     * measures the real thing: replacement art that steps in by a different amount would leave the
     * cartridge balanced on a record too wide or too narrow for its foot, and nothing else in the
     * application would mention it.
     *
     * <p>Compared against the record as <em>drawn</em> rather than as requested. Those are not the
     * same number - the record is snapped to a whole scale - and comparing with the request would
     * pass while the two visibly did not meet.
     *
     * @return pixels of difference between the inlet and the record, or zero when there is no
     *         cartridge to measure
     */
    public double inletMisalignment() {
        if (label == null) {
            return 0;
        }
        return cartridgeSheet.footprint(0)
                .map(foot -> foot.getWidth() * (CARD_WIDTH / cartridgeSheet.frameWidth()) - diskSpanX)
                .orElse(0d);
    }

    /**
     * @return the cartridge's label, in this window's coordinates
     */
    public Rectangle2D labelBounds() {
        double cardX = Math.round((prefWidth(-1) - CARD_WIDTH) / 2);
        return labelRect(cardX, actions.prefHeight(-1) + ACTIONS_GAP, cardHeight());
    }

    /**
     * Reports how far the song information overflows the cartridge's label.
     *
     * <p>A {@code VBox} does not clip what it cannot fit, so contents too tall for the label do not
     * disappear - they carry on down over the cartridge's grey body, which reads as a layout that
     * was never looked at. Nothing throws, and the labels are all still inside the window, so the
     * check for that catches none of it.
     *
     * @return pixels of overflow, or zero or less when everything sits on the label
     */
    public double labelOverflow() {
        Rectangle2D panel = labelBounds();
        return card.prefHeight(panel.getWidth()) - panel.getHeight();
    }

    /**
     * Reports how much narrower the artwork's label is than the captions were sized for.
     *
     * <p>{@link #CONTENT_WIDTH} is a compile-time guess at the label's width, because the caption
     * limits have to be constants to be checkable without a toolkit. This is the guess being marked:
     * a cartridge whose label is narrower than assumed clips its own captions, and nothing else in
     * the application would say so.
     *
     * @return pixels of shortfall, or zero or less when the label is wide enough
     */
    public double labelShortfall() {
        return CONTENT_WIDTH - (labelBounds().getWidth() - 2 * CARD_PADDING);
    }

    /**
     * Reports which frame of the record is showing.
     *
     * @return the frame index, or -1 before the first draw
     */
    public int diskFrame() {
        return diskFrame;
    }

    /**
     * Reports anything lying in front of the transport that would swallow a click on it.
     *
     * <p><strong>This window deliberately overlaps its own controls</strong> - the record reaches up
     * over the card's foot and the kart stands higher still - so "is the play button actually
     * clickable" is a real question with a non-obvious answer, and it is one that neither a unit
     * test nor a screenshot can answer. The buttons still draw, still highlight on hover, and simply
     * do nothing; that is the most confusing form a dead control takes, and it is exactly what
     * happened before the record was made mouse-transparent.
     *
     * <p>Checks the middle of the play key against every sibling drawn after the card, which is
     * where anything capable of covering it must be.
     *
     * @return the node in the way, or {@code null} when the transport can be reached
     */
    public javafx.scene.Node blockingTheTransport() {
        // Through the scene rather than up the parents by hand: the button is two panes deep and
        // counting the offsets manually is its own source of wrong answers.
        javafx.geometry.Bounds play = playButton.localToScene(playButton.getBoundsInLocal());
        javafx.geometry.Point2D centre =
                sceneToLocal(play.getCenterX(), play.getCenterY());

        boolean pastCard = false;
        for (javafx.scene.Node node : getChildren()) {
            if (node == card) {
                pastCard = true;
                continue;
            }
            // Only what is drawn after the card can be in front of it.
            if (!pastCard || node.isMouseTransparent() || !node.isVisible()) {
                continue;
            }
            if (node.getBoundsInParent().contains(centre)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Draws the record and the kart as they would look at a moment in the track, without playing it.
     *
     * <p>For the smoke test, and it is the only way that check can be made. A still picture of a
     * spinning record is a static record, so no screenshot can show that this turns; and the frame
     * loop cannot be observed either, because the smoke test holds the interface thread while it
     * measures and no pulse arrives to advance anything. Asking for two different moments and
     * comparing what came out establishes the thing that actually matters: the picture is a function
     * of the <em>playback position</em>, so a position that stops advancing - which is exactly what
     * pausing does - is a record that stops turning.
     *
     * <p>Safe because the frame loop reads the clock afresh on its next tick.
     *
     * @param seconds where in the track to draw
     * @return the record frame that moment lands on
     */
    public int previewAt(double seconds) {
        diskFrame = diskAnimation.frameIndexAt(seconds);
        racerFrame = RacerFrame.driving(seconds);
        redrawDisk();
        return diskFrame;
    }
}
