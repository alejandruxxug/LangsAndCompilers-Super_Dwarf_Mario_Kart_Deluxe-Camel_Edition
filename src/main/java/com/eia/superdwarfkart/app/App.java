package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.analysis.Beatmap;
import com.eia.superdwarfkart.analysis.BeatmapIndex;
import com.eia.superdwarfkart.analysis.BeatmapService;
import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.audio.AudioSource;
import com.eia.superdwarfkart.audio.LevelAnalyzer;
import com.eia.superdwarfkart.audio.Levels;
import com.eia.superdwarfkart.audio.LocalFileAudioSource;
import com.eia.superdwarfkart.audio.PcmFormat;
import com.eia.superdwarfkart.audio.RoutingAudioSource;
import com.eia.superdwarfkart.game.Course;
import com.eia.superdwarfkart.game.Entity;
import com.eia.superdwarfkart.game.Lane;
import com.eia.superdwarfkart.game.Obstacle;
import com.eia.superdwarfkart.game.RunnerGame;
import com.eia.superdwarfkart.game.ScoreKeeper;
import com.eia.superdwarfkart.game.ScriptedDriver;
import com.eia.superdwarfkart.game.SpeedClass;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.PlayHistory;
import com.eia.superdwarfkart.model.Racer;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.persistence.LibraryRepository;
import com.eia.superdwarfkart.persistence.PersistenceException;
import com.eia.superdwarfkart.persistence.Repository;
import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.MoodRepository;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteImporter;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.mood.PixelTile;
import com.eia.superdwarfkart.mood.ProceduralLayer;
import com.eia.superdwarfkart.persistence.ScoreRepository;
import com.eia.superdwarfkart.persistence.SettingsRepository;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import com.eia.superdwarfkart.spotify.SpotifyBinary;
import com.eia.superdwarfkart.spotify.SpotifyCatalog;
import com.eia.superdwarfkart.spotify.SpotifyConfig;
import com.eia.superdwarfkart.spotify.SpotifySession;
import com.eia.superdwarfkart.playback.PlaybackListener;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import com.eia.superdwarfkart.ui.BeatmapTimeline;
import com.eia.superdwarfkart.ui.BootScreen;
import com.eia.superdwarfkart.ui.ComplexityPanel;
import com.eia.superdwarfkart.ui.CoverArt;
import com.eia.superdwarfkart.ui.SpotifyView;
import com.eia.superdwarfkart.ui.Destination;
import com.eia.superdwarfkart.ui.Fonts;
import com.eia.superdwarfkart.ui.HistoryView;
import com.eia.superdwarfkart.ui.LevelMeterView;
import com.eia.superdwarfkart.ui.LibraryView;
import com.eia.superdwarfkart.ui.MiniPlayerView;
import com.eia.superdwarfkart.ui.MoodSelectView;
import com.eia.superdwarfkart.ui.PixelDialog;
import com.eia.superdwarfkart.ui.PlaybackBar;
import com.eia.superdwarfkart.ui.RacerSelectView;
import com.eia.superdwarfkart.ui.RunnerView;
import com.eia.superdwarfkart.ui.MoodCustomizerView;
import com.eia.superdwarfkart.ui.MoodOverlayRenderer;
import com.eia.superdwarfkart.ui.SettingsView;
import com.eia.superdwarfkart.ui.ShutdownScreen;
import com.eia.superdwarfkart.ui.SideRail;
import com.eia.superdwarfkart.ui.Theme;
import com.eia.superdwarfkart.ui.visualizer.OperationCounter;
import com.eia.superdwarfkart.ui.visualizer.PresentationView;
import com.eia.superdwarfkart.ui.visualizer.StructureVisualizer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * JavaFX bootstrap.
 *
 * <p>Builds the shared model - the library and its storage - and hands it to the views. The
 * application must start and stay usable whatever the state of the stored data or the artwork
 * on disk, so a failure to load the library reports itself and continues with an empty one
 * rather than refusing to open.
 */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    /**
     * When set to {@code true}, the application starts, reports what it verified on stdout and
     * closes itself, so that a launch can be checked without leaving a window on screen.
     */
    private static final String SMOKE_TEST_PROPERTY = "sdmk.smokeTest";

    /**
     * When set to a file path, the smoke test writes a snapshot of the window there before
     * closing. Used to check layout without a person watching the screen, and to capture
     * screenshots for the project documentation.
     */
    private static final String SCREENSHOT_PROPERTY = "sdmk.screenshot";

    /**
     * Width of the left column: the structure visualizer above, the complexity panel below.
     *
     * <p>This column is the queue view: what is coming up is shown as the structure holding it,
     * not as a flat list.
     *
     * <p>Stacked rather than side by side because the two of them beside the library left the
     * table about 470 pixels, and at one em per glyph that truncates every song title to three
     * characters. Sharing one column costs the visualizer some height - which presentation mode
     * gives back in full - and returns the table enough width to be read.
     */
    private static final double SIDE_COLUMN_WIDTH = 400;

    /** Height reserved for the complexity panel at the foot of the left column. */
    private static final double COMPLEXITY_HEIGHT = 350;

    /**
     * How much bigger than its restore size the smoke test grows the window before shrinking it
     * back, in pixels.
     *
     * <p>Any amount would do - the fault is a minimum that ratchets, so one pixel of growth pins it
     * as well as a screenful. This is roughly the difference between the restored window and a
     * maximised one on this machine, which makes the printed numbers the ones the restore button
     * actually produces.
     */
    private static final double SHRINK_PROBE_GROWTH = 360;

    /**
     * Width of the meter column on the right, in pixels.
     *
     * <p>This is where the three-lane runner goes when it is built; the meters already sit where
     * they will flank it, so the game slots between them rather than displacing them.
     */
    private static final double METER_COLUMN_WIDTH = 130;

    /**
     * Function key that folds the structure column away, and brings it back.
     *
     * <p>The column is 400 of the window's 1440 pixels and it is worth every one of them while the
     * structures are being shown - but it is not always what the user is doing. Folding it hands
     * that width to whatever is in the middle: fifty more characters of song title in the table, or
     * a road wide enough to read a lookahead off from across a room.
     *
     * <p>It sits beside {@link #PRESENTATION_KEY} on the keyboard because the two are the same
     * decision at opposite ends - F4 gives the visualizer none of the window and F5 gives it all of
     * it.
     */
    private static final KeyCode STRUCTURE_KEY = KeyCode.F4;

    /** Function key that hands the whole stage to the visualizer. */
    private static final KeyCode PRESENTATION_KEY = KeyCode.F5;

    /**
     * Function key that swaps the library for the rhythm game.
     *
     * <p>The two share the middle of the window rather than splitting it. At one em per glyph the
     * table needs every pixel it has, and the runner needs a road long enough to read a lookahead
     * off - neither survives being given half. It is also the shape the side rail will formalise:
     * Library is one destination among several, not a permanent fixture.
     */
    private static final KeyCode RACE_KEY = KeyCode.F6;

    /**
     * Function key that swaps the whole window for the companion strip, and back.
     *
     * <p>The same key in both directions, in both windows: it is one toggle, and a mode you leave
     * with a different key from the one that put you in it is a mode people get stuck in.
     */
    private static final KeyCode MINI_KEY = KeyCode.F7;

    /**
     * Function key that puts the companion window's artwork away, leaving the song and the transport.
     *
     * <p>Only meaningful while that window is on screen, so it is wired to its scene rather than to
     * the main one's.
     */
    private static final KeyCode COMPACT_KEY = KeyCode.F8;

    /**
     * Function key that gives the runner the whole screen, and nothing else on it.
     *
     * <p><strong>Only the runner.</strong> Presentation mode already hands the whole <em>window</em>
     * to the visualizer, and this is the same idea for the game taken one step further: no title bar,
     * no side rail, no meters, no playback bar, no window frame, and no desktop either. The road is
     * the only thing on the display.
     *
     * <p>F11 because that is what every other application on the machine uses for it, and because
     * every other function key here is already spoken for. It <em>starts</em> the race if one is not
     * running, since a fullscreen kart player with no kart in it would be a blank screen: the key
     * names what the user wants to see rather than a state they have to reach first.
     *
     * <p>Escape leaves, which is what the user asked for and what every other frameless thing in this
     * application already means by it. The toolkit's own fullscreen exit key is switched off in favour
     * of handling it here - see {@link #enterFullscreenRace()} for why that is not the same key twice.
     */
    private static final KeyCode FULLSCREEN_KEY = KeyCode.F11;

    /**
     * What the window's fullscreen button says when pressing it would give up the display.
     *
     * <p>Both captions are three characters, like the three buttons beside them: in a fixed-width font
     * a caption that changes width shoves its neighbours along as it is pressed, and those are exactly
     * where the pointer already is. Arrows rather than words because there is no room for a word, and
     * they point the way the window is about to go.
     */
    private static final String FULLSCREEN_ON_CAPTION = "> <";

    /** What it says when pressing it would take the whole display. See {@link #FULLSCREEN_ON_CAPTION}. */
    private static final String FULLSCREEN_OFF_CAPTION = "< >";

    /**
     * Where into the track the runner is drawn for its screenshot, in seconds.
     *
     * <p>Far enough in that a track which opens quietly has got going.
     */
    private static final double SCREENSHOT_COURSE_SECONDS = 45;

    /**
     * How long the window takes to come up from nothing at launch.
     *
     * <p><strong>Long enough to be an arrival and short enough not to be a wait.</strong> Under about
     * a third of a second a fade is a flicker and reads as the window having failed to draw for a
     * frame; much over a second and it is the first thing between the user and an application they
     * have already asked for, before the fifteen-second sequence they are also about to sit through.
     * It is also deliberately shorter than the boot fanfare's own opening chime, so the picture is
     * fully up before there is anything to listen to.
     */
    private static final Duration LAUNCH_FADE = Duration.millis(650);

    /**
     * How long the application takes to come up out of the black the boot screen leaves behind, and
     * how long the fanfare takes to fade out under it.
     *
     * <p><strong>One length for both, which is the whole point of the constant.</strong> The picture
     * and the sound are two halves of a single handover - the machine has finished reading and the
     * software arrives - and two numbers that are meant to describe one moment are two numbers free
     * to drift. Passed to {@code SoundEffect.stop(double)} rather than letting the sound take its own
     * default quarter-second, which under a two-thirds-of-a-second fade reads as the fanfare having
     * been cut off early.
     *
     * <p>The same 650 ms as {@link #LAUNCH_FADE}, deliberately: the console fades up out of the dark
     * at exactly the rate the application later fades up out of it, so the launch opens and closes on
     * the same gesture.
     */
    private static final Duration HANDOVER_FADE = Duration.millis(650);

    /**
     * How long the launch waits before asking for the display at all.
     *
     * <p><strong>macOS drops a fullscreen request made during another transition, silently.</strong>
     * {@code Stage.setFullScreen} reaches AppKit's {@code toggleFullScreen:}, which is a <em>request</em>
     * rather than a setter: the window server builds a Space and animates into it, and if the
     * application is already mid-transition the request is discarded with no error, no exception and no
     * property change. Two transitions are in flight exactly here - the zoom {@code setMaximized(true)}
     * starts at {@code show()}, and, on a launch that follows a quit closely, the <em>previous</em>
     * instance's fullscreen Space still being torn down. A single {@code Platform.runLater} is one pulse,
     * about 8 ms, which is nowhere near either.
     *
     * <p>This is the "sometimes it does not launch fullscreen, and if I wait it is fine" report, and the
     * wait is the whole diagnosis. Nobody sees the delay: the boot sequence that follows runs for fifteen
     * seconds.
     */
    private static final Duration LAUNCH_FULLSCREEN_SETTLE = Duration.millis(400);

    /**
     * How long after a refused launch request the application asks once more.
     *
     * <p><strong>Once, and bounded, rather than a loop.</strong> {@code setFullScreen} is the call this
     * project has measured wedging the interface thread in {@code MacApplication._enterNestedEventLoopImpl}
     * - see {@code fadeWindowIn} - so retrying it is not free, and a loop that kept asking would turn an
     * intermittent freeze into a reliable one. One more attempt covers the case this exists for, which is
     * a Space that was still closing when the first one went out.
     */
    private static final Duration LAUNCH_FULLSCREEN_RETRY = Duration.millis(700);

    private Library library;
    private Repository<Song> libraryRepository;
    private AssetRegistry assets;
    private Player player;
    private AppState state;
    private OperationCounter counter;

    private AudioSource audio;
    private Levels levels;
    private PlaybackEngine engine;
    private LevelMeterView meters;
    private BeatmapService beatmaps;
    private BeatmapTimeline beatmapTimeline;

    private ScoreRepository scores;
    private SettingsRepository settings;
    private BeatmapIndex courseIndex;
    private RunnerView runner;
    private LibraryView libraryView;
    private SideRail sideRail;
    private HistoryView historyView;
    private RacerSelectView racerSelect;
    private MoodSelectView moodSelect;
    private MoodCustomizerView moodCustomizer;
    private MoodRepository moods;

    /**
     * The two canvases the mood's overlay layers are drawn on, with the interface between them.
     *
     * <p>It holds whatever the window is showing, so the layers reach the library, the runner, the
     * structure visualiser and presentation mode without any of them knowing it exists. Everything
     * that used to call {@code shell.setCenter} now sets this pane's content instead - swapping the
     * shell's centre would take the layers away with the view.
     */
    private MoodOverlayRenderer overlay;
    private SettingsView settingsView;
    private SpotifyView spotifyView;
    private SpotifySession spotify;
    private PlayHistory history;
    private Button viewToggle;
    private boolean racing;

    /** Whether the runner currently has the whole display to itself. See {@link #FULLSCREEN_KEY}. */
    private boolean fullscreenRace;

    /**
     * Whether the <em>window</em> currently has the whole display, which is a different thing from
     * {@link #fullscreenRace}.
     *
     * <p>This one is the application filling the screen with its whole interface - the side rail, the
     * library, the meters and the title bar all still there. {@link #fullscreenRace} is the road and
     * nothing else. They are tracked apart because they nest: pressing {@code F11} out of a fullscreen
     * window and leaving the race again has to put the interface back <em>and stay fullscreen</em>,
     * where a single flag would drop the window out of it as a side effect of leaving the game.
     *
     * @see #setWindowFullscreen(boolean)
     */
    private boolean windowFullscreen;

    /** The header button that toggles {@link #windowFullscreen}; its caption follows the state. */
    private Button fullscreenToggle;

    /**
     * The fanfare the machine makes as the cartridge goes in.
     *
     * <p>Constructed here and decoded on first play, which costs nothing until the cartridge lands.
     */
    private final com.eia.superdwarfkart.audio.SoundEffect bootFanfare =
            new com.eia.superdwarfkart.audio.SoundEffect(AppConfig.SOUND_BOOT);

    /**
     * The mechanical clunk of the cartridge going into the slot, fired as the drag commits.
     *
     * <p><strong>A second effect rather than a second call on the first one, because one
     * {@code SoundEffect} is one line and asking it for another sound stops what it is playing.</strong>
     * These two are meant to overlap: the fanfare's own measured envelope opens with nearly three
     * seconds of quiet chime, so a two-second clunk over the top of it has nothing to fight with.
     *
     * <p><strong>It starts before the fanfare rather than with it</strong>, by the two tenths of a
     * second the cartridge takes to travel the last of the slot - see
     * {@link BootScreen#setOnSeating}. That gap is the whole difference between a sound of something
     * moving and a sound played over a picture of something that has already stopped.
     */
    private final com.eia.superdwarfkart.audio.SoundEffect cartridgeIn =
            new com.eia.superdwarfkart.audio.SoundEffect(AppConfig.SOUND_CARTRIDGE_IN);

    /**
     * Whether the drag ever reached the callback the clunk hangs off.
     *
     * <p>Only the smoke test reads it, and it is the one thing about that sound a run can check: the
     * effect is deliberately not played during a screenshot, so a callback that had quietly stopped
     * being wired would sound exactly like one that was working and being kept quiet.
     */
    private boolean cartridgeSeatedFired;

    /**
     * The noise of the cartridge coming back out, played over the shutdown screen.
     *
     * <p>Started as that screen goes up rather than through a callback the way the boot fanfare is:
     * there the sound fires part way through a sequence the screen owns, and here the sound and the
     * screen begin at the same instant in the same method. It is faded from
     * {@link ShutdownScreen#setOnFading} - see there for why that is not the same moment as the end.
     */
    private final com.eia.superdwarfkart.audio.SoundEffect cartridgeOut =
            new com.eia.superdwarfkart.audio.SoundEffect(AppConfig.SOUND_CARTRIDGE_OUT);

    /** The screen shown while the daemon is being stopped, or {@code null} before the user quits. */
    private ShutdownScreen shutdownScreen;

    /** Set once a quit has been asked for, so a second press cannot start a second teardown. */
    private boolean shuttingDown;

    /** Guards {@link #stopDrawing()}, which the quit path and {@code stop()} both reach. */
    private boolean stoppedDrawing;

    /** Guards {@link #releaseResources}, for the same reason. */
    private boolean releasedResources;

    /** Set when the user chose the library by hand, so pressing play does not overrule them. */
    private boolean libraryPinned;

    /**
     * The window itself: the title bar, the border, and whatever is currently being shown.
     *
     * <p>Separate from {@link #root} because presentation mode swaps what is inside the window and
     * must not swap the window. Before this existed the header was inside {@code root} and F5
     * replaced the lot, which was harmless while the operating system drew the chrome and takes the
     * title bar away with it now that the header <em>is</em> the chrome - leaving a window that
     * cannot be moved or closed until F5 is pressed again.
     */
    private BorderPane shell;

    private BorderPane root;
    private BootScreen bootScreen;

    /** The title bar, kept so the smoke test can measure whether its captions still fit. */
    private HBox header;

    /** Set while the boot screen is up, so no shortcut reaches a window nobody has opened yet. */
    private boolean booting = true;

    private PlaybackBar playbackBar;
    private VBox sideColumn;
    private Button structureToggle;
    private boolean structureFolded;
    private StructureVisualizer visualizer;
    private PresentationView presentation;
    private boolean presenting;

    private Stage mainStage;
    private Stage miniStage;
    private MiniPlayerView miniPlayer;
    private Button miniToggle;

    /** Whether the companion window has been given a position yet; it keeps its own afterwards. */
    private boolean miniPlaced;

    /** Whether the runner's frame loop was running when the main window was put away. */
    private boolean runnerWasRunning;

    @Override
    public void start(Stage stage) {
        this.mainStage = stage;
        boolean pixelFont = Fonts.load();

        // Scanned here rather than on first sprite lookup, so that the summary and any warning
        // about missing artwork appear at the top of the log, and so that the manifest template
        // is written before anything asks for a sprite. Scanning reads filenames only.
        assets = AssetRegistry.shared();

        libraryRepository = new LibraryRepository();
        library = loadLibrary(libraryRepository);

        // One counter instruments every structure the application builds, so the scatter plot
        // accumulates across mode changes instead of starting again with each one.
        counter = new OperationCounter();

        // Shuffle is the mode the application opens in. The player is handed a concrete mode and
        // never learns which one it is; the bar swaps the object when the user picks another.
        player = new Player(library, new ShuffleMode(new Random(), counter), counter);
        state = new AppState();
        player.addListener(state);
        state.playbackChanged(player.mode(), player.current());

        // Read and applied before a single view is built, so the first frame the user sees is
        // already in their mood rather than flashing the default one and correcting itself.
        // Before the settings are read, because the stored mood id is resolved against this: a
        // mood the user built has to be restorable exactly as readily as a preset.
        moods = new MoodRepository(AppConfig.moodsDir());
        settings = new SettingsRepository();
        restoreSettings();

        // The tap is composed in here rather than owned by the audio source, so the same meters
        // work unchanged over any implementation of AudioSource.
        levels = new Levels();

        // Spotify is built but not started. Constructing the session looks at the filesystem for
        // the daemon and nothing else - no process, no socket, no network - so a user who never
        // opens that page never learns the feature is here.
        spotify = new SpotifySession(Platform::runLater);
        spotify.setConfiguredPath(settings.spotifyBinaryPath());
        maybeFetchSpotifyBinary();

        // One output that opens files and Spotify tracks alike, choosing by locator. Everything
        // above this line - meters, analyser, game, transport - is written against AudioSource and
        // cannot tell which one answered.
        audio = new RoutingAudioSource(new LocalFileAudioSource(), spotify::audioSource);
        audio.addPcmListener(new LevelAnalyzer(levels));
        // End of track arrives on the playback thread; runLater is the hop back, and it happens
        // once per song rather than once per audio block.
        engine = new PlaybackEngine(player, audio, Platform::runLater);

        // Recorded from the engine rather than from a song change, so a song scrolled past in the
        // library does not appear in the history as though it had been listened to.
        history = new PlayHistory();
        engine.setOnPlayCounted(song -> {
            // A backstop, and honestly a no-op on every path there is today: handOverFromTheDark
            // fades the fanfare out as the library comes up, so by the time any song can be played
            // it has already let go. It used to be the *only* place the fanfare was stopped, back
            // when it deliberately rang on over the library the way a console's does over a game's
            // first screen. It is kept because it costs nothing and covers the one thing that must
            // never happen - two sounds at once, the older one not giving way - and because this is
            // the exact hook for it: the application boots paused and no song has ever played, so a
            // first play is always a counted one and can never be a resume that slips past.
            bootFanfare.stop();
            history.record(song);
        });
        // A streamed song that cannot open is nearly always the daemon not being up yet. Handled
        // rather than logged: before this, playing one with Spotify unconnected did nothing at all
        // and said nothing anywhere.
        engine.setOnFailure(this::songWouldNotOpen);

        // Analysis follows the running order rather than the play button: whatever becomes current
        // gets a beatmap prepared for it on a background thread, so by the time the rhythm game
        // wants a course it is already there. Reading the file for analysis is a separate decode
        // and never touches the one being played.
        //
        // A streamed song has no file to open, so the only copy of its audio is the one going past
        // on the way to the sound card. The tap below is what turns that into a course: the track
        // is listened to as it plays, the beatmap lands in the cache when it finishes, and every
        // play after the first has full entities. Registered on the routing source, so it reaches
        // the Spotify source whenever that gets built.
        beatmaps = new BeatmapService();
        audio.addPcmListener(beatmaps.streamTap());
        // Everything collected so far is at a known offset into the song; everything after a seek
        // is not. Given up rather than believed - a beatmap built across a seek is wrong, cached,
        // and indistinguishable from a right one.
        engine.setOnSeek(beatmaps::abandonStream);
        // A track that played out has been heard in full, which is the only condition under which
        // what was collected is a beatmap rather than a fragment. Fired before the running order
        // moves, so the result is filed against the track it came from.
        engine.setOnTrackEnded(beatmaps::finishStream);
        player.addListener((mode, current) -> beatmaps.request(locatorOf(current), lengthOf(current)));
        beatmaps.request(locatorOf(player.current()), lengthOf(player.current()));

        // Read before the library view is built: the table shows a rank badge per song, and a board
        // that failed to load has to leave the column empty rather than stop the window opening.
        scores = new ScoreRepository();

        // Which songs already have a course, hashed on a background thread so the table can ask
        // per row per repaint without reading a byte.
        courseIndex = new BeatmapIndex(beatmaps.cache());

        libraryView = new LibraryView(library, libraryRepository, scores, courseIndex);
        libraryView.setOnSongActivated(song -> player.select(song));
        libraryView.setOnSpotifySearch(this::openSpotifySearch);
        courseIndex.setOnUpdated(Platform::runLater, libraryView::refreshBadges);
        // Moving off a song is the moment its analysis has either finished or been abandoned, so
        // that is when the index is asked to look at it again. Cheaper and far more reliable than
        // polling the analysis service for a transition nobody else needs to hear about.
        player.addListener(new PlaybackListener() {
            private Song previous = player.current();

            @Override
            public void playbackChanged(PlaybackMode mode, Song current) {
                if (previous != null && !previous.equals(current)) {
                    courseIndex.recheck(previous.locator());
                }
                previous = current;
            }
        });

        playbackBar = new PlaybackBar(player, engine, counter);
        ComplexityPanel complexityPanel = new ComplexityPanel(player, counter);

        beatmapTimeline = new BeatmapTimeline(beatmaps, engine);
        beatmapTimeline.start();

        meters = new LevelMeterView(levels);
        meters.setMinWidth(METER_COLUMN_WIDTH);
        meters.setPrefWidth(METER_COLUMN_WIDTH);
        meters.setMaxWidth(METER_COLUMN_WIDTH);
        meters.start();

        visualizer = new StructureVisualizer(player, state, assets);
        presentation = new PresentationView(counter);

        runner = new RunnerView(state, assets, beatmaps, engine, levels, scores);
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            // Not during a smoke test: that plays a few seconds of audio to measure the meters, and
            // an automatic hop to the road would replace the library in every screenshot taken
            // afterwards - including the ones taken to check the library.
            runner.setOnRaceStarted(this::showRaceOnPlay);
        }
        runner.start();

        complexityPanel.setPrefHeight(COMPLEXITY_HEIGHT);
        complexityPanel.setMinHeight(COMPLEXITY_HEIGHT);
        sideColumn = new VBox(visualizer, complexityPanel);
        sideColumn.setMinWidth(SIDE_COLUMN_WIDTH);
        sideColumn.setPrefWidth(SIDE_COLUMN_WIDTH);
        sideColumn.setMaxWidth(SIDE_COLUMN_WIDTH);
        VBox.setVgrow(visualizer, Priority.ALWAYS);

        historyView = new HistoryView(library, history);
        historyView.setOnSongActivated(song -> player.select(song));
        racerSelect = new RacerSelectView(state, assets);
        moodSelect = new MoodSelectView(state, moods);
        moodCustomizer = new MoodCustomizerView(state, moods);
        moodCustomizer.setOnMoodsChanged(() -> moodSelect.refresh());
        moodSelect.setOnCustomize(this::showCustomizer);
        settingsView = new SettingsView(state);

        spotifyView = new SpotifyView(library, spotify);
        // A track added from Spotify joins the library like anything else, so the running order
        // rebuilds around it - the ring, the queue and the tree do not care where it came from.
        spotifyView.setOnSongAdded(song -> libraryView.refreshBadges());
        spotifyView.setOnCredentialsSaved(
                credentials -> settings.setSpotifyCredentials(credentials[0], credentials[1]));
        // Added rather than set: the view claimed the single slot in its own constructor, and a
        // second set() would have stopped the page redrawing with nothing to say so.
        spotify.addOnChanged(this::spotifyStateChanged);
        // Restored rather than verified: checking them is a network round trip, and this runs for
        // every launch including the ones where nobody opens the Spotify page at all.
        spotifyView.restoreCredentials(
                settings.spotifyClientId(), settings.spotifyClientSecret());

        sideRail = new SideRail();
        sideRail.destinationProperty().addListener((observable, was, now) -> {
            // Both halves of this page are derived from a library that is edited by hand, so they
            // are recomputed on arrival rather than kept live by a listener per song.
            if (now == Destination.HISTORY) {
                historyView.refresh();
            }
            if (now == Destination.SPOTIFY) {
                // Looks at the filesystem again on arrival, which is what picks up a daemon the
                // user installed by hand while the application was already running.
                spotify.refreshBinary();
                spotifyView.refresh();
            }
            showDestination(now);
        });

        root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(new VBox(playbackBar, beatmapTimeline));
        root.setLeft(new HBox(sideRail, sideColumn));
        root.setCenter(libraryView);
        root.setRight(meters);

        // The window: its own title bar, its own border, and one slot in the middle for whatever is
        // being shown. Everything the operating system used to draw is drawn here instead, so the
        // window looks the same on macOS and on Windows and neither breaks the theme.
        shell = new BorderPane();
        shell.getStyleClass().add("pixel-window");
        // No frame while the boot screen is up. It draws no title bar for the same reason - a console
        // with the power just switched on is not a window yet - and an amber border around a black
        // screen is the software's colour scheme arriving before the software does. Taken off in
        // finishBooting, along with everything else that is the application rather than the machine.
        shell.getStyleClass().add("no-frame");
        // Built now - it wires the window's drag and its buttons - but not attached yet. The boot
        // screen gets the whole window: it is a console with the power just switched on, and a
        // title bar across the top of that is the application admitting it was a window all along.
        buildHeader();

        // The layers' pane goes in the shell's centre once, and everything that changes what is on
        // screen sets its content. It draws the ground the root pane used to paint for itself:
        // .root-pane is transparent now, because a layer behind an opaque pane is a layer nobody
        // can see.
        overlay = new MoodOverlayRenderer();
        overlay.setMusicFeed(new MoodOverlayRenderer.MusicFeed() {
            @Override
            public double seconds() {
                return engine.positionSeconds();
            }

            @Override
            public double level() {
                return Math.max(levels.leftRms(), levels.rightRms());
            }

            @Override
            public double beat() {
                return runner == null ? 0 : runner.beatPulseNow();
            }
        });

        bootScreen = new BootScreen(assets);
        // **In the dark before the scene exists, so the console fades up rather than appearing.** The
        // application used to arrive with this screen already fully drawn on it, which is what "it just
        // pops out" describes, and nothing can fade in from a state it was never in. Here rather than
        // beside show(): the screen has never been laid out at this point, so the first frame the window
        // paints is already the faded one and no extra layout pass lands anywhere near the launch. Only
        // the cartridge and the prompt fade - see BootScreen.sleep, and App.fadeWindowIn for what is
        // known and not known about the platform fault this launch sits next to.
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            bootScreen.sleep();
        }
        bootScreen.setOnInserted(this::finishBooting);
        bootScreen.setOnLoading(this::bootLoadSpotify);
        // The noise a console makes when a cartridge goes into a live slot: the clunk of the thing
        // seating, and the machine's own fanfare underneath it. Two effects and therefore two output
        // lines, because one SoundEffect plays one sound and asking it for a second stops the first.
        // Fired from the boot screen rather than played by it: opening an output line is audio/'s
        // business, and this is also what keeps a screenshot silent - the previews run none of the
        // sequence's side effects.
        //
        // **They are two moments, not one.** The clunk goes on the release, the instant the cartridge
        // commits and starts travelling; the fanfare goes on the tear at the end of that travel, where
        // the flash is. Fired together they both began after the seat animation had finished, so the
        // slide was silent and the thing landed with a noise once it had already stopped.
        //
        // The clunk is guarded and the fanfare is not, and that asymmetry is real rather than an
        // oversight: onGlitch is unreachable during a smoke test (the seat Timeline never gets a pulse,
        // so startSequence never runs), while onSeating hangs off the gesture itself - which the smoke
        // test genuinely performs. Without the guard, taking a screenshot would play two seconds of
        // audio into a build log.
        bootScreen.setOnSeating(() -> {
            cartridgeSeatedFired = true;
            if (!smokeTest()) {
                cartridgeIn.play();
            }
        });
        bootScreen.setOnGlitch(bootFanfare::play);
        // The animation is exactly as long as the sound, measured rather than written down - so the
        // picture and the fanfare end together and neither is cut off by the other. Decoding here is a
        // few hundred kilobytes of MPEG on the way to a screen that has not been drawn yet, and it is
        // what the smoke test's `boot fanfare` line reads too. A missing sound leaves the boot screen on
        // its own fallback length rather than turning the sequence into a different, shorter thing.
        bootScreen.setSequenceSeconds(bootFanfare.lengthSeconds());
        // There is no title bar to drag by while this is up, so the black itself is the handle. The
        // cartridge consumes its own presses, so dragging it never moves the window.
        PixelDialog.dragBy(bootScreen, stage);
        shell.setCenter(overlay);
        // The boot screen gets the window with no layers over it: a console with the power just
        // switched on is not the moment for a starfield, and an ABOVE_CONTENT layer would draw over
        // the cartridge. The mood is installed in finishBooting.
        overlay.setContent(bootScreen);
        // Nothing behind the boot screen may draw. Five canvases started their timers as they were
        // constructed above, and an AnimationTimer does not stop because the node it paints cannot
        // be seen - the same fault the companion window and the F4 fold each had to fix.
        suspendMainViews();

        Scene scene = new Scene(shell, AppConfig.MAIN_WIDTH, AppConfig.MAIN_HEIGHT);
        // The stage is transparent, so the window's visible edge is the border the shell draws.
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        installShortcuts(scene);
        installPaletteDrop(scene);

        // Before show(), which is the only time a stage will accept it. The title stays even though
        // nothing draws it any more: it is what the dock and the task switcher show.
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        // Anything asking the window to close goes through the shutdown screen rather than straight
        // out. There is no system close button on an undecorated stage, but the platform can still ask
        // - Cmd-Q on macOS does - and that path used to freeze for as long as the daemon took to die.
        stage.setOnCloseRequest(event -> {
            event.consume();
            requestQuit();
        });
        // The toolkit's own fullscreen exit key is switched off for the whole session, and Escape is
        // handled in installShortcuts instead. They are the same keystroke and not the same thing:
        // the toolkit's takes the stage out of fullscreen and leaves every flag and style class here
        // exactly as they were - no border, a road parented to the overlay pane, a button whose
        // caption now says the opposite of what it does. Both fullscreen modes rely on this.
        stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        // Filling the screen from the start, because the boot screen is a console powering up and a
        // console does not power up in a window. MAIN_WIDTH and MAIN_HEIGHT stay as the size the
        // window restores to, and the layout has to be right at both - which is what the maximise
        // button in the title bar is for.
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            // Not during a smoke test: it measures the middle of the window against SIDE_COLUMN_WIDTH
            // and photographs every view, and both of those want the size the constants describe
            // rather than whatever display the run happens to be on.
            stage.setMaximized(true);
        }
        stage.show();
        // True fullscreen, and NOT from inside start(). Stage.setFullScreen enters a nested event loop
        // on macOS and does not return until the platform's transition has finished - and that
        // transition can never finish while start() is still on the stack, because the launcher has not
        // handed the thread back to normal event dispatch yet. Measured, before this was deferred: the
        // FX thread sat RUNNABLE in MacApplication._enterNestedEventLoopImpl for as long as the
        // application was left open, entered from MacView._enterFullscreen. The window drew perfectly -
        // the boot screen was painted before the call - and then processed no input at all, so the
        // cartridge could not be dragged and the application could only be killed. A rendered, frozen
        // window is the worst shape this fault could take: nothing throws and nothing looks wrong.
        //
        // Platform.runLater puts it on a later pulse, once start() has returned and the outer event
        // loop is running, which is what lets the nested one complete. This is the same trap the smoke
        // test documents - see smokeTest() - reached by a different road.
        //
        // Skipped whole during a smoke test rather than just its setFullScreen call, and that is a
        // stronger exemption than the one smokeTest() describes: the frame comes off in this mode, so
        // launching into it would take the border out of every screenshot the run photographs and
        // leave the checks measuring a window that is not the one a user opens. The mode is exercised
        // on its own instead - see reportWindowFullscreen.
        //
        // **The fade starts after it returns, and that ordering is worth a sentence.** setFullScreen
        // blocks here for the length of the platform's own transition, so a fade started before it
        // would spend most of its length behind a window being flung onto a display of its own - and
        // arrive already over. Started after, the console comes up on the screen it is going to stay
        // on. The call itself is left exactly as it was; see fadeWindowIn for what is and is not known
        // about the intermittent freeze that lives in it.
        // The stage is the authority on whether this window has the display, and until this listener
        // existed nothing here ever asked it - see syncFullscreenFromStage. The platform can change it
        // without being told to by this application, and it can decline to change it when it is.
        stage.fullScreenProperty().addListener((observable, was, is) -> syncFullscreenFromStage());

        // A PauseTransition rather than a Platform.runLater, and it is doing two jobs at once. The first
        // is the original one: this must not run from inside start(), because setFullScreen enters a
        // nested event loop that cannot finish while the launcher still holds the thread - measured, the
        // FX thread sat RUNNABLE in MacApplication._enterNestedEventLoopImpl for as long as the
        // application was left open, and the window drew perfectly and then accepted no input at all.
        // The second is LAUNCH_FULLSCREEN_SETTLE's: one pulse is not enough time for the platform's own
        // transitions to be out of the way, and a request made during one is discarded in silence.
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            PauseTransition settle = new PauseTransition(LAUNCH_FULLSCREEN_SETTLE);
            settle.setOnFinished(event -> {
                setWindowFullscreen(true);
                // The fade starts after the first attempt returns, not after the retry: setFullScreen
                // blocks for the length of the platform's transition, so a fade started before it would
                // spend most of its length behind a window being flung onto a display of its own and
                // arrive already over. Waiting for a retry that usually never happens would delay the
                // picture on every launch to fix the one that stumbled.
                fadeWindowIn();
                if (!mainStage.isFullScreen()) {
                    // Refused. syncFullscreenFromStage has already put the flag, the frame and the
                    // button's caption back to the truth, so this is an ordinary first attempt again.
                    PauseTransition retry = new PauseTransition(LAUNCH_FULLSCREEN_RETRY);
                    retry.setOnFinished(again -> setWindowFullscreen(true));
                    retry.play();
                }
            });
            settle.play();
        }

        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            runSmokeTest(stage, scene, pixelFont);
        }
    }

    /**
     * Brings the console's picture up out of the dark, once the window has settled where it is going.
     *
     * <p>It fades what the boot screen <em>draws</em> and never the window itself - see
     * {@code BootScreen.sleep}. That began as a workaround and survives on its own merits; the story of
     * how it got there is worth keeping, because most of it is a lesson about measuring rather than
     * about fading.
     *
     * <p><strong>The launch has an intermittent platform fault next to it, and it is not this
     * one.</strong> {@code Stage.setFullScreen} enters a nested event loop on macOS and does not return
     * until the platform's own transition has finished - the trap this class already documents twice -
     * and on this machine that transition <em>sometimes does not finish</em>, leaving the interface
     * thread {@code RUNNABLE} in {@code MacApplication._enterNestedEventLoopImpl} and the application
     * drawn and completely deaf. Deferring the call out of {@code start()} made it rare rather than
     * certain.
     *
     * <p><strong>Four different ways of fading were each measured against it and each looked like the
     * cause, and none of them was.</strong> Dimming the stage before {@code show()}, dimming it after,
     * fading the scene's root, and a full-screen fill over the boot screen all showed failure rates far
     * above an untouched launch - about 10 runs in 16 against 2 in 12. The bisection was careful and the
     * conclusion was wrong: <strong>a control run at the end of the session wedged 6 times out of
     * 6.</strong> The environment had been degrading throughout - roughly forty fullscreen windows had
     * been killed outright by then, each leaving its own macOS Space behind - so every comparison made
     * across that session was against a moving baseline, and the effect attributed to the fade was drift.
     *
     * <p>This is the project's own §7 lesson arriving a second time, in a different room: before treating
     * something as caused by the code, ask what state the <em>machine</em> is in, and interleave the
     * control rather than running it once at the start. A rate measured against an environment that is
     * changing under the experiment is not a rate.
     *
     * <p>What that leaves is honest and worth saying plainly: <strong>the fullscreen launch can freeze on
     * this machine, it could before any of this was written, and it is not understood.</strong> A logout
     * or a restart is the thing to try first, since it is what clears the Spaces those killed windows
     * left behind. The fade neither causes it nor is known to make it likelier.
     */
    private void fadeWindowIn() {
        bootScreen.wakeUp(LAUNCH_FADE);
    }

    /**
     * Accepts a palette file dropped anywhere on the window as "make a mood from this".
     *
     * <p>The shortest path there is from a Lospec page to a mood, and the whole argument for
     * {@link PaletteImporter} rests on that path being short: choosing sixteen colours by eye takes
     * an hour, and this takes as long as a drag. It is deliberately not restricted to the mood
     * screen - somebody who has just downloaded a palette is looking at their downloads folder, not
     * at this application's side rail.
     *
     * <p>The drop is refused unless the file is one this can read, so dragging an MP3 in still does
     * what it always did.
     */
    private void installPaletteDrop(Scene scene) {
        scene.setOnDragOver(event -> {
            if (!booting && event.getDragboard().hasFiles()
                    && event.getDragboard().getFiles().stream()
                            .anyMatch(file -> PaletteImporter.canRead(file.toPath()))) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            boolean handled = false;
            for (java.io.File file : event.getDragboard().getFiles()) {
                if (PaletteImporter.canRead(file.toPath())) {
                    // Through the customizer rather than through the repository directly: it is
                    // what knows to name the mood, store it, apply it and say so, and a second copy
                    // of that sequence here would be free to drift from the button's.
                    moodCustomizer.importPalette(file.toPath());
                    handled = true;
                    break;
                }
            }
            if (handled) {
                sideRail.select(Destination.MOODS);
                showCustomizer();
            }
            event.setDropCompleted(handled);
            event.consume();
        });
    }

    /**
     * Hands the window over to the application, once the cartridge is in.
     *
     * <p>Lands on the library, paused. Inserting the cartridge is the start ritual and asking a
     * second question straight afterwards would be one too many - but it is a statement about
     * <em>starting up</em>, not about playback: pressing play still brings the road up on its own,
     * exactly as it did before.
     *
     * <p>The swap itself is instant and always was; what follows it is {@link #handOverFromTheDark()},
     * which is what makes the arrival read as a handover rather than as a cut.
     */
    private void finishBooting() {
        if (!booting) {
            return;
        }
        booting = false;
        // The title bar and the window frame arrive with the application, not before it - though the
        // frame only if nothing else still wants it off, which is why this asks rather than removes.
        // Launching fullscreen is exactly that case: the boot screen ends and the border must not
        // come back around an application that is filling the display.
        shell.setTop(header);
        updateWindowFrame();
        overlay.setContent(root);
        // Now the layers arrive, with the application.
        applyMood(state.getMood());
        resumeMainViews();
        handOverFromTheDark();
    }

    /**
     * Brings the application up out of the black the boot screen ended on, picture and sound together.
     *
     * <p>The show finishes on a full blackout - {@code BootScreen.blackout} ramps to 1 over its last
     * few percent - and until this existed the library then <em>appeared</em>, whole, on the very next
     * pulse. Two things were wrong with that and they are the same thing twice: the machine spent
     * fifteen seconds fading everything it drew and then handed over with a cut, and on a
     * {@code skip()} the fanfare carried on ringing at full strength over a library that was already
     * up. The end of the loading bar is one moment, however it is reached, so it gets one gesture.
     *
     * <p><strong>The whole window fades, not the centre.</strong> {@code finishBooting} has just
     * attached the header and the frame, and those are as much a part of the application arriving as
     * the library is - a title bar snapping in at full strength over a view that is still coming up
     * would be the cut moved rather than removed. So what is faded is {@code shell}, the scene's root,
     * and what is behind it for the length of the fade is the <em>scene fill</em>, set to the console's
     * own black.
     *
     * <p><strong>The fill is not optional and the black has to be that black.</strong> The stage is
     * {@code TRANSPARENT}, so a half-faded root over the default fill is a half-transparent application
     * over the user's desktop - which is not a fade from black, it is a window that failed to draw.
     * {@code Palette.hardware()} is where the value comes from because that is the palette the boot
     * screen faded <em>to</em>: the first frame of this fade is then exactly the colour of the last
     * frame of that one, and ground rule 7 is untouched - a role named, a palette asked.
     *
     * <p><strong>Skipped whole during a smoke test</strong>, for the reason that run photographs
     * anything at all: the screenshots are taken on the interface thread immediately after
     * {@code bootScreen.skip()}, no pulse arrives to advance a timeline, and every shot in the run
     * would come out of a window frozen at opacity zero over a black fill. A picture of nothing is
     * indistinguishable from a view that failed to lay out, which is the one photograph that would be
     * believed. The fanfare is still told to stop, because that costs no frames and needs none.
     */
    private void handOverFromTheDark() {
        // Whether the fanfare ran its full length or the user pressed a key half way through it, this
        // is where it lets go - it is the machine's noise, and the machine has just finished. Over the
        // same span as the picture, so the two describe one event; a natural end has already retired
        // the player thread and this is a no-op.
        bootFanfare.stop(HANDOVER_FADE.toSeconds());

        if (smokeTest()) {
            return;
        }
        Scene scene = shell.getScene();
        if (scene == null) {
            return;
        }
        scene.setFill(Palette.hardware().color(PaletteRole.SHADOW));
        shell.setOpacity(0);
        // Eased for the reason every other fade here is - BootScreen.wakeUp says it at length: a
        // linear ramp stops abruptly at the top and the eye catches the corner.
        Timeline fade = new Timeline(new KeyFrame(HANDOVER_FADE,
                new KeyValue(shell.opacityProperty(), 1, Interpolator.EASE_OUT)));
        // Whatever happens to the timeline, the application ends up visible and the window ends up
        // transparent again. An application left permanently behind a black veil, in a window whose
        // corners have quietly stopped being see-through, is a far worse fault than anything that
        // could have interrupted the fade - and it has no symptom to search for.
        fade.setOnFinished(event -> {
            shell.setOpacity(1);
            scene.setFill(Color.TRANSPARENT);
        });
        fade.play();
    }

    /**
     * Fetches the go-librespot binary in the background, where this platform has a published one.
     *
     * <p>The daemon is bundled in the sense that matters: the user installs nothing and configures
     * nothing, and by the time they open the Spotify page the executable is already in
     * {@code ~/.superdwarfkart/spotify/bin}. It is a download into this application's own folder
     * on a background thread - <strong>not</strong> a process, a socket or a login, all of which
     * still wait for the user to ask.
     *
     * <p>Does nothing at all when the daemon is already present, when the user pointed the
     * application at their own build, or on a platform with no published asset - which is every
     * Mac, where {@code brew install go-librespot} is offered on the page instead. Failure is
     * silent by design: a music player must open and play local files whatever the network is
     * doing (ground rule 5).
     */
    private void maybeFetchSpotifyBinary() {
        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            // A smoke test must not depend on the network, and must not leave a 6 MB download
            // behind in a scratch profile.
            return;
        }
        if (!settings.spotifyAutoFetch() || spotify.binary().isFound()) {
            return;
        }
        spotify.prefetchBinary();
    }

    /**
     * Applies the choices stored from the last session, and arranges for changes to be written
     * back.
     *
     * <p>An unrecognised stored value is not an error - a mood may have been deleted, or the
     * profile may have been written by a later build. Every one of these falls back to the default
     * rather than refusing to start (ground rule 5).
     */
    private void restoreSettings() {
        // Through the repository rather than through Moods, so a mood the user built is restored
        // as readily as a preset. An id this build has never heard of resolves to nothing and falls
        // back, rather than refusing to start.
        moods.byId(settings.moodId()).ifPresent(state::setMood);
        Racer.byName(settings.racerId()).ifPresent(state::setRacer);
        SpeedClass.byName(settings.speedClass()).ifPresent(state::setSpeedClass);
        state.setReduceMotion(settings.reduceMotion());

        applyMood(state.getMood());
        applyReduceMotion(state.isReduceMotion());
        state.moodProperty().addListener((observable, was, now) -> {
            applyMood(now);
            settings.setMoodId(now.id());
        });
        state.reduceMotionProperty().addListener((observable, was, now) -> {
            applyReduceMotion(now);
            settings.setReduceMotion(now);
        });
        state.racerProperty().addListener(
                (observable, was, now) -> settings.setRacerId(now.name()));
        state.speedClassProperty().addListener(
                (observable, was, now) -> settings.setSpeedClass(now.name()));
    }

    /**
     * Installs a mood: restyles every control and repaints every canvas.
     *
     * <p>The controls restyle themselves, because the palette reaches them as a stylesheet. The
     * canvases do not: they read the active palette when they next paint, and the ones that only
     * paint when their picture changes would otherwise sit in the previous mood indefinitely. The
     * beatmap strip is exactly that - it repaints about six times a second while a long track plays
     * and not at all while one is paused - so the repaints below are not belt and braces.
     *
     * <p>Safe to call before the views exist: this runs once during startup, before the scene is
     * built, so that the first frame is already in the right colours.
     *
     * @param mood the mood to install; must not be {@code null}
     */
    private void applyMood(Mood mood) {
        Theme.setPalette(mood.palette());

        if (overlay != null) {
            // Nothing while the boot screen is up: it is a console with the power just switched on,
            // and an ABOVE_CONTENT layer would draw over the cartridge. finishBooting calls this
            // again, which is when the layers arrive.
            overlay.setMood(booting ? null : mood, moods.folderOf(mood.id()));
        }
        if (moodCustomizer != null) {
            moodCustomizer.refresh();
        }

        if (meters != null) {
            meters.redraw();
        }
        if (beatmapTimeline != null) {
            beatmapTimeline.redraw();
        }
        if (runner != null) {
            runner.redraw();
        }
        if (visualizer != null && visualizer.view() != null) {
            visualizer.view().redraw();
        }
        if (miniPlayer != null) {
            miniPlayer.refresh();
        }
    }

    /**
     * Wires the keyboard shortcuts.
     *
     * <p>They are split across the two phases of event delivery on purpose, because the two
     * groups want opposite things:
     *
     * <ul>
     *   <li><strong>A filter</strong> for the window shortcuts, which run first and win wherever
     *       the focus happens to be. Tab is excused inside a text field, where it belongs to the
     *       field.</li>
     *   <li><strong>A handler</strong> for the transport keys, which run <em>last</em> - only if
     *       nothing else wanted them. The library table uses the arrows to move its selection,
     *       the search box to move the caret, the tree view to step through a traversal, and the
     *       runner to steer; all four consume the event first, so the transport never steals a key
     *       out from under a control that was using it. Space belongs to the same group for the
     *       same reason: it steps the tree when the tree has focus, jumps the kart when the road
     *       has it, presses whichever button does, and only reaches playback when nothing else
     *       claimed it.</li>
     * </ul>
     *
     * @param scene the scene to listen on
     */
    private void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Nothing on the window is reachable until the cartridge is in. Without this, the very
            // first key of the session could collapse the application to a companion strip over a
            // boot screen, or swap in a road that is not on screen.
            if (booting) {
                // Except the way out. The boot screen draws no title bar, so it draws no close
                // button either, and a window with no visible way to shut it needs an invisible
                // one - Escape, which is what every other frameless window here uses to say no.
                if (event.getCode() == KeyCode.ESCAPE) {
                    requestQuit();
                    return;
                }
                // Anything else skips the rest of the sequence, once the cartridge is in. Fifteen
                // seconds of fanfare is an event on the first launch and a wait on the tenth, and
                // somebody demonstrating this will start it many times - every console this is dressed
                // as let you press through its own logo. Escape keeps meaning "no" rather than "hurry
                // up", which is why it is handled first and returns.
                if (bootScreen.skip()) {
                    event.consume();
                }
                return;
            }
            // Fullscreen is the runner and nothing else, so while it is up the keys that would swap
            // in something else are dead. Not because they would throw - because they would work:
            // F6 would put the library into a pane that is not on screen and leave the road showing,
            // which reads as a key that did nothing while quietly having done something. Same shape
            // of early return as the boot screen's above, and for the same reason. The driving keys
            // are untouched: the runner installs those as its own scene filter.
            if (fullscreenRace) {
                if (event.getCode() == KeyCode.ESCAPE || event.getCode() == FULLSCREEN_KEY) {
                    exitFullscreenRace();
                    event.consume();
                }
                return;
            }
            if (event.getCode() == FULLSCREEN_KEY) {
                enterFullscreenRace();
                event.consume();
            } else if (event.getCode() == STRUCTURE_KEY) {
                toggleStructureColumn();
                event.consume();
            } else if (event.getCode() == PRESENTATION_KEY) {
                togglePresentation();
                event.consume();
            } else if (event.getCode() == RACE_KEY) {
                toggleRace();
                event.consume();
            } else if (event.getCode() == MINI_KEY) {
                collapseToCompanion();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && presenting) {
                togglePresentation();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && windowFullscreen) {
                // Last of the three Escapes, deliberately: presentation mode is a state inside the
                // window, so leaving it has to come first or one keystroke would give up the display
                // and leave the visualizer still holding the stage. This is the toolkit's own exit key
                // arriving at the one place that undoes every part of the mode - see setWindowFullscreen.
                setWindowFullscreen(false);
                event.consume();
            } else if (event.getCode() == KeyCode.TAB && !typing(scene)) {
                playbackBar.cycleMode();
                event.consume();
            }
        });

        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (booting) {
                return;
            }
            switch (event.getCode()) {
                case LEFT, TRACK_PREV -> {
                    player.previous();
                    event.consume();
                }
                case RIGHT, TRACK_NEXT -> {
                    player.next();
                    event.consume();
                }
                case SPACE, PLAY, PAUSE -> {
                    engine.toggle();
                    playbackBar.refresh();
                    event.consume();
                }
                default -> {
                    // Not a transport key; leave it alone.
                }
            }
        });
    }

    /**
     * @param song a song, or {@code null}
     * @return what it plays from - a file path or a Spotify URI - or {@code null}
     */
    private static String locatorOf(Song song) {
        return song == null ? null : song.locator();
    }

    /**
     * @param song a song, or {@code null}
     * @return how long it runs in seconds, or 0 when that is not recorded
     */
    private static double lengthOf(Song song) {
        java.time.Duration length = song == null ? null : song.getDuration();
        return length == null ? 0 : length.toNanos() / 1e9;
    }

    /**
     * @param scene the scene to inspect
     * @return whether the focus is in something the user is typing into
     */
    private static boolean typing(Scene scene) {
        return scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl;
    }

    /**
     * Folds the structure column away, and brings it back.
     *
     * <p><strong>Invisible is not enough - it has to be unmanaged too.</strong> A node that is
     * merely invisible still takes its 400 pixels in the layout, so the column would disappear and
     * hand its width to nothing at all: the table would be exactly as narrow as before, beside a
     * blank strip. Giving that width away is the entire feature.
     *
     * <p>The visualizer stops drawing while it is folded. An {@code AnimationTimer} is driven by the
     * toolkit's pulse and knows nothing about whether the node it paints can be seen, so a road that
     * kept scrolling behind a fold would cost a repaint per frame for the rest of the session and
     * report it nowhere - the same fault, in the same shape, as the canvases left running behind the
     * companion window.
     *
     * <p>Nothing about the running order changes. This is a fold in the window, not a change of
     * mode: the structure underneath goes on holding the queue, and unfolding shows it exactly where
     * it got to.
     */
    private void toggleStructureColumn() {
        structureFolded = !structureFolded;
        sideColumn.setVisible(!structureFolded);
        sideColumn.setManaged(!structureFolded);
        updateStructureToggle();
        updateVisualizerDrawing();
    }

    /**
     * Brings the header's fold caption in step with what the column is doing.
     *
     * <p>Both captions are the same length on purpose. In a fixed-width font a toggle that changes
     * width shoves everything beside it along as it is pressed, and the two keys beside this one are
     * exactly where the pointer already is.
     */
    private void updateStructureToggle() {
        if (structureToggle != null) {
            structureToggle.setText(structureFolded ? "F4 SHOW DSA" : "F4 HIDE DSA");
        }
    }

    /**
     * Starts or stops the visualizer's frame timer according to whether it can currently be seen.
     *
     * <p>Three separate things hide it - the fold above, the companion window, and presentation mode
     * showing it in the opposite direction - and any one of them can be in force while another
     * changes. Deciding from the state rather than at each call site is what stops the combinations
     * from disagreeing: folding the column during a presentation must not stop the view that is
     * filling the stage, and leaving a presentation into a folded column must not start one.
     */
    private void updateVisualizerDrawing() {
        if (presenting || !structureFolded) {
            visualizer.start();
        } else {
            visualizer.stop();
        }
    }

    /**
     * Moves the visualizer between the main layout and the full stage.
     *
     * <p>The same node travels in both directions, so the tree keeps its pan, its zoom and any
     * walk in progress: entering presentation mode mid-answer must not reset what is being shown.
     *
     * <p><strong>It swaps the shell's centre, not the scene's root.</strong> The header is the
     * window's title bar now, so replacing the root would take the drag handle and the close button
     * away with it and leave a window that could not be moved or closed until F5 was pressed a
     * second time. The visualizer gets the stage less the title bar, which is the right trade.
     */
    private void togglePresentation() {
        if (presenting) {
            presentation.detach();
            // Back above the complexity panel, where it came from - even when the column is folded
            // away, so that unfolding finds it there rather than empty.
            sideColumn.getChildren().add(0, visualizer);
            overlay.setContent(root);
            presenting = false;
            updateVisualizerDrawing();
            return;
        }
        // Detach from the main layout first: a node cannot sit in two places at once.
        sideColumn.getChildren().remove(visualizer);
        presentation.attach(visualizer);
        overlay.setContent(presentation);
        presenting = true;
        // F5 is how somebody folded out of the way reaches the visualizer for a question, so this
        // has to pick the timer back up rather than assume it was left running.
        updateVisualizerDrawing();
    }

    /**
     * Builds the title bar strip.
     *
     * <p>This is the main window, so it carries the full application name. The mini player must
     * use {@link AppConfig#APP_NAME_SHORT} instead: at 44 characters in the 8-bit font the full
     * name is several times the width of that window.
     *
     * <p><strong>It is now the window's title bar as well as its header</strong>, because there is
     * no system one any more. Rather than stack a second full-width strip above it - which would
     * cost 40 pixels of an 800 pixel window to draw the name a second time - it gains what the
     * chrome supplied: the three window buttons, and dragging by the strip itself.
     *
     * <p>Built early and attached late: it belongs to the application rather than to the boot
     * screen, which gets the window to itself. See {@link #finishBooting()}.
     */
    private void buildHeader() {
        Label name = new Label(AppConfig.APP_NAME);
        name.getStyleClass().add("app-name");

        Label version = new Label("v" + AppConfig.APP_VERSION);
        version.getStyleClass().add("app-version");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        structureToggle = new Button();
        structureToggle.getStyleClass().add("view-toggle");
        structureToggle.setTooltip(new Tooltip(
                "Fold the structure column away and give its width to the middle\nF4"));
        structureToggle.setOnAction(event -> toggleStructureColumn());
        updateStructureToggle();

        viewToggle = new Button();
        viewToggle.getStyleClass().add("view-toggle");
        viewToggle.setTooltip(new Tooltip("Swap the library for the rhythm game\nF6"));
        viewToggle.setOnAction(event -> toggleRace());
        updateViewToggle();

        miniToggle = new Button("F7 MINI");
        miniToggle.getStyleClass().add("view-toggle");
        miniToggle.setTooltip(new Tooltip(
                "Put this window away and keep the music on a companion strip\nF7"));
        miniToggle.setOnAction(event -> collapseToCompanion());

        // None of these takes keyboard focus. They are shortcuts to the function keys printed on
        // them, so focus buys them nothing - and whichever of them held it would be the node that
        // answered the first space bar of the session, which is the same fault that left the
        // runner's jump dead: a control quietly eating the key play/pause is meant to get. Being
        // first in the header, that would be the one folding the structure column away.
        structureToggle.setFocusTraversable(false);
        miniToggle.setFocusTraversable(false);
        viewToggle.setFocusTraversable(false);

        header = new HBox(14, name, spacer, structureToggle, miniToggle, viewToggle, version,
                buildWindowButtons());
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 14, 16));
        header.getStyleClass().add("app-header");

        // The one sanctioned implementation - there must never be a second copy of those eight
        // lines, because a window that cannot be moved is the single thing the missing chrome would
        // actually be missed for.
        PixelDialog.dragBy(header, mainStage);
        // A maximised window has nowhere to be dragged to, and moving one by its own coordinates
        // leaves it maximised at the wrong place. Consumed in a filter, which runs before the drag
        // handler above; the target check is what keeps the buttons on the strip working, since a
        // press on one of them is targeted at the button rather than at the strip.
        header.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (mainStage.isMaximized() && event.getTarget() == header) {
                event.consume();
            }
        });
        header.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (mainStage.isMaximized() && event.getTarget() == header) {
                event.consume();
            }
        });
    }

    /**
     * Builds the minimise, maximise and close buttons.
     *
     * <p><strong>The close button is the load-bearing one.</strong> Before this the main window had
     * no way to quit at all - the only {@code Platform.exit()} a user could reach was the companion
     * window's, so taking the system chrome away without adding this would have left the application
     * closable only by collapsing to the companion strip first.
     *
     * <p>Maximise exists because an undecorated stage loses the native zoom along with everything
     * else. Its caption changes rather than its icon, since a caption that changes width would shove
     * the buttons beside it along as it is pressed - which is exactly where the pointer already is.
     *
     * @return the three buttons in a row, furthest right where system chrome puts them
     */
    private HBox buildWindowButtons() {
        // Fullscreen, and it is the way back as much as the way there: the application launches with
        // the whole display, so on the first run this button is the only visible thing that hands the
        // window back. F11 is not it - that is the runner and nothing else.
        fullscreenToggle = new Button(FULLSCREEN_OFF_CAPTION);
        fullscreenToggle.getStyleClass().add("window-button");
        fullscreenToggle.setTooltip(new Tooltip(
                "Fill the whole display, or go back to a window\nEsc leaves"));
        fullscreenToggle.setOnAction(event -> setWindowFullscreen(!windowFullscreen));

        Button minimise = new Button("_");
        minimise.getStyleClass().add("window-button");
        minimise.setTooltip(new Tooltip("Minimise"));
        minimise.setOnAction(event -> mainStage.setIconified(true));

        Button maximise = new Button("[ ]");
        maximise.getStyleClass().add("window-button");
        maximise.setTooltip(new Tooltip("Maximise or restore"));
        maximise.setOnAction(event -> mainStage.setMaximized(!mainStage.isMaximized()));
        // Driven by the stage rather than set in the handler, because the application maximises
        // itself at start-up and a caption written only where the button is pressed would come up
        // saying the opposite of what the window is doing. Both captions are three characters, so
        // the two buttons beside it do not shift as it changes.
        maximise.setText(mainStage.isMaximized() ? "[-]" : "[ ]");
        mainStage.maximizedProperty().addListener(
                (observable, was, now) -> maximise.setText(now ? "[-]" : "[ ]"));

        Button quit = new Button("X");
        quit.getStyleClass().addAll("window-button", "close-button");
        quit.setTooltip(new Tooltip("Close"));
        // Not Platform.exit(). Shutting down takes real time - the go-librespot child gets a five
        // second grace period before it is killed - and doing that with the window already gone is
        // what made closing the application look like a hang. See requestQuit().
        quit.setOnAction(event -> requestQuit());

        // Same rule as the three view toggles beside them: a focusable button in the header answers
        // the first space bar of the session instead of play/pause, and these three sit at the end
        // of the strip where the traversal would reach them.
        fullscreenToggle.setFocusTraversable(false);
        minimise.setFocusTraversable(false);
        maximise.setFocusTraversable(false);
        quit.setFocusTraversable(false);

        // Fullscreen first, so the three that were already here keep their positions relative to the
        // corner - close stays the last thing on the strip, where every window manager puts it.
        HBox buttons = new HBox(6, fullscreenToggle, minimise, maximise, quit);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        return buttons;
    }

    /**
     * Swaps the middle of the window between the library and the runner.
     *
     * <p>Focus goes to the road on the way in, because the arrows and space belong to the kart
     * while it has focus and to the transport otherwise - the whole arrangement rests on which node
     * the keys reach first, so leaving focus behind in the search box would make the controls look
     * broken.
     *
     * <p>The runner's frame loop keeps running either way. It is the thing that notices a song
     * change and files the score for the run that just ended, and stopping it would mean a run
     * abandoned by switching to the library was silently lost.
     */
    private void toggleRace() {
        racing = !racing;
        if (racing) {
            root.setCenter(runner);
            runner.requestFocus();
        } else {
            // Back to whichever destination the rail is on, not unconditionally to the library:
            // leaving a race the user started from the mood screen should return them there.
            showDestination(sideRail.getDestination());
            // A run that ended while the road was on screen may have changed the board.
            libraryView.refreshBadges();
        }
        // Leaving the road by hand is a decision, and pressing play afterwards must not overrule
        // it. Coming back to the road withdraws it.
        libraryPinned = !racing;
        updateViewToggle();
    }

    /**
     * Puts a destination in the middle of the window.
     *
     * <p>The views are built once and kept, rather than rebuilt per visit: the library holds the
     * user's search, sort and selection, and throwing that away every time they glanced at the
     * settings would be its own bug. They are cheap to keep and none of them runs a timer.
     *
     * <p>Choosing a destination leaves the race, because the two occupy the same space and a rail
     * button that appeared to do nothing while the road was up would read as broken.
     *
     * @param target where to go; must not be {@code null}
     */
    private void showDestination(Destination target) {
        // Favourites are the library with its filter on - see LibraryView.setFavoritesOnly.
        if (target == Destination.LIBRARY || target == Destination.FAVORITES) {
            libraryView.setFavoritesOnly(target == Destination.FAVORITES);
        }

        Node view = switch (target) {
            case LIBRARY, FAVORITES -> libraryView;
            case HISTORY -> historyView;
            case RACERS -> racerSelect;
            case SPOTIFY -> spotifyView;
            case MOODS -> moodSelect;
            case SETTINGS -> settingsView;
        };

        if (racing) {
            racing = false;
            libraryPinned = true;
            updateViewToggle();
        }
        root.setCenter(view);
    }

    /**
     * Opens the Spotify search modal over the library, and selects whatever it added.
     *
     * <p>Called by the library's own SPOTIFY button through a callback, so that view never learns a
     * streaming service exists. What happens afterwards is what makes this an addition to the
     * <em>library</em> rather than to a page of its own: the song is selected in the table, so the
     * details panel is showing it and the rating slider is on it, and the course badge is looked at
     * again because a streamed track earns one as soon as it has been played once.
     */
    private void openSpotifySearch() {
        java.util.Optional<Song> added = com.eia.superdwarfkart.ui.SpotifySearchDialog.show(
                mainStage, library, spotify);
        added.ifPresent(song -> {
            libraryView.showInDetails(song);
            libraryView.refreshBadges();
        });
    }

    /**
     * Gives the whole display to the application, or hands the window back.
     *
     * <p><strong>This is the mode the application launches in</strong>, and it is not the same thing as
     * {@link #enterFullscreenRace()}: everything stays where it is - the title bar, the side rail, the
     * library, the meters - and the window simply stops being a window. The road is one destination
     * inside it rather than the whole of it.
     *
     * <p>The window frame comes off, for the reason it comes off in a fullscreen race: a border is what
     * tells you where a window ends, and on a display with no window on it three pixels of amber is the
     * only thing that is not the application. It goes through {@link #updateWindowFrame()} rather than
     * being toggled here, because three separate states want it off and the last one to finish would
     * otherwise put it back.
     *
     * <p><strong>The toolkit's own fullscreen exit key stays switched off here too.</strong> It would
     * take the stage out of fullscreen and leave this flag set and the frame off - a window that no
     * longer fills the screen, has no border, and whose button now says the opposite of what it does.
     * That is the same desync {@link #enterFullscreenRace()} documents, so the same answer: one way in,
     * one way out, and {@code Escape} handled in the filter.
     *
     * @param on whether the window should have the whole display
     */
    private void setWindowFullscreen(boolean on) {
        if (mainStage == null || windowFullscreen == on) {
            return;
        }
        windowFullscreen = on;
        updateWindowFrame();
        if (fullscreenToggle != null) {
            fullscreenToggle.setText(on ? FULLSCREEN_ON_CAPTION : FULLSCREEN_OFF_CAPTION);
        }
        if (!smokeTest()) {
            // Skipped during a smoke test for the reason smokeTest() gives: on macOS this enters a
            // nested event loop that can never finish while a synchronous run holds the thread.
            mainStage.setFullScreen(on);
            // And then believe the window rather than this method. Everything above ran on the
            // assumption that asking is the same as getting, which on macOS it is not.
            syncFullscreenFromStage();
        }
    }

    /**
     * Puts this class's idea of the mode back in step with the window's.
     *
     * <p><strong>Everything above was write-only until this existed, and that is the bug it fixes.</strong>
     * {@link #setWindowFullscreen(boolean)} sets the flag, strips the frame and flips the button's caption
     * and <em>then</em> asks the platform - so a request macOS declined left the application in a state no
     * user could make sense of: a merely maximised window, with no border, and a {@code < >} button reading
     * as though it were already filling the display. Pressing it then appeared to do nothing, because its
     * {@code setFullScreen(false)} was a no-op on a window that was never fullscreen, and only the
     * <em>second</em> press worked. Nothing threw and nothing was logged, which is why it read as the
     * launch being unreliable rather than as one boolean being out of step.
     *
     * <p>It is also how the platform's own changes arrive. A fullscreen transition can complete or be
     * undone without this application asking - the listener in {@code start()} is what carries that back.
     *
     * <p><strong>A fullscreen race is not a fullscreen window, and this must never confuse the two.</strong>
     * {@link #enterFullscreenRace()} sets its own flag and then puts the <em>stage</em> fullscreen, so
     * without the guard the property change would arrive here and be recorded as the user having asked for
     * a fullscreen window. {@link #exitFullscreenRace()} would then hand the display straight back to
     * itself - {@code setFullScreen(windowFullscreen)} - and F11 would have become a one-way door. The two
     * modes nest, which is exactly why they are two flags.
     *
     * <p>Skipped during a smoke test, where {@code setFullScreen} is never called at all: the stage's
     * answer is a flat {@code false} there, so reconciling against it would immediately undo the very
     * state {@code reportWindowFullscreen} sets up in order to assert it.
     */
    private void syncFullscreenFromStage() {
        if (mainStage == null || smokeTest() || fullscreenRace
                || windowFullscreen == mainStage.isFullScreen()) {
            return;
        }
        windowFullscreen = mainStage.isFullScreen();
        updateWindowFrame();
        if (fullscreenToggle != null) {
            fullscreenToggle.setText(windowFullscreen ? FULLSCREEN_ON_CAPTION : FULLSCREEN_OFF_CAPTION);
        }
    }

    /**
     * Decides whether the shell draws its border, from the state rather than from the call site.
     *
     * <p>Four separate things want the frame off - the boot screen, a fullscreen race, a fullscreen
     * window, and the shutdown screen - and any one of them can end while another is still in force.
     * Toggling the style class at each of the places that change those states is how it ends up wrong:
     * {@code finishBooting} removing it would put a border back around an application that is filling
     * the display, and leaving a race would do the same. Computing it once from all of them cannot drift.
     *
     * <p><strong>The shutdown screen is on this list for the boot screen's own reason, run
     * backwards.</strong> Three pixels of amber around a black screen is the software's look outliving
     * the software - and unlike every other state here there is no title bar left to drag by either, so
     * the border is not even marking an edge that can be used. The two ends of the application match.
     */
    private void updateWindowFrame() {
        boolean framed = !(booting || fullscreenRace || windowFullscreen || shuttingDown);
        shell.getStyleClass().remove("no-frame");
        if (!framed) {
            shell.getStyleClass().add("no-frame");
        }
    }

    /**
     * Gives the runner the whole display: no window, no interface, just the road.
     *
     * <p><strong>It starts the race if one is not running.</strong> F11 says what the user wants to
     * look at rather than naming a state they have to reach first, and a fullscreen kart player with
     * no kart in it is a black screen with a rank of D in the corner.
     *
     * <p>The runner is moved into the overlay pane's slot rather than left in {@link #root}, which is
     * the same move {@link #togglePresentation()} makes and for the same reason: the mood's layers
     * live in that pane, so the road keeps its wallpaper and its scanlines instead of losing them at
     * the moment the game is filling the screen. The header comes off because in true fullscreen a
     * title bar is the one thing on the display that is not the game, and the shell drops its border
     * because a window frame around a whole screen is the edge of a window that is not there.
     *
     * <p><strong>The toolkit's own fullscreen exit key is switched off, and Escape is handled here
     * instead.</strong> They are the same keystroke and they are not the same thing: the toolkit's
     * would take the stage out of fullscreen and leave every one of the changes above in place - no
     * title bar, no border, a runner parented to the wrong pane and a window that cannot be moved or
     * closed. That is the {@code scene.setRoot} trap in a different costume, so there is exactly one
     * way out of this mode and {@link #exitFullscreenRace()} is it. The hint the toolkit would print
     * over the top goes with it; the road already prints its own controls line.
     */
    private void enterFullscreenRace() {
        if (fullscreenRace || mainStage == null) {
            return;
        }
        // A node cannot sit in two places at once, and the visualizer is holding the slot.
        if (presenting) {
            togglePresentation();
        }
        if (!racing) {
            toggleRace();
        }

        root.setCenter(null);
        overlay.setContent(runner);
        shell.setTop(null);
        fullscreenRace = true;
        updateWindowFrame();

        if (!smokeTest()) {
            mainStage.setFullScreen(true);
        }

        runner.requestFocus();
    }

    /**
     * @return whether this launch is a smoke test
     *
     *         <p><strong>{@link Stage#setFullScreen} is skipped during one, and this is why.</strong>
     *         On macOS it enters a <em>nested event loop</em> and does not return until the platform's
     *         own fullscreen transition has finished - and the smoke test runs synchronously on the
     *         interface thread inside a synthesised key event, so that transition can never complete.
     *         Measured: the run deadlocked inside {@code MacApplication._enterNestedEventLoopImpl} and
     *         printed not one line after {@code window shrink}. A nested loop also pumps the event
     *         queue, which would re-enter the check that is running.
     *
     *         <p>So the one call that belongs to the window system is left out and everything this
     *         application actually decides - which pane holds the road, whether the title bar comes
     *         off, whether the frame goes, whether the other shortcuts are dead, and whether all four
     *         come back - is still driven and still asserted. Same shape of exemption as
     *         {@code setMaximized} and the go-librespot download, and reported out loud rather than
     *         quietly skipped.
     */
    private static boolean smokeTest() {
        return Boolean.getBoolean(SMOKE_TEST_PROPERTY);
    }

    /**
     * Puts the window back exactly as it was.
     *
     * <p>Every one of the four things {@link #enterFullscreenRace()} changed is undone here, which is
     * why that is the only way in and this is the only way out. The race itself carries on - leaving
     * fullscreen is not leaving the road, and a run that ended because somebody pressed Escape to get
     * their window back would be a run lost to a keystroke about window management.
     */
    private void exitFullscreenRace() {
        if (!fullscreenRace || mainStage == null) {
            return;
        }
        fullscreenRace = false;
        if (!smokeTest()) {
            // Back to whatever the window itself was doing, not flatly out of fullscreen. Leaving the
            // race from a fullscreen window has to give the interface back and keep the display, or
            // F11 and Escape would quietly be a way of resizing the window - see windowFullscreen.
            mainStage.setFullScreen(windowFullscreen);
        }

        overlay.setContent(root);
        root.setCenter(runner);
        shell.setTop(header);
        updateWindowFrame();

        runner.requestFocus();
    }

    /** @return whether the runner currently has the whole display */
    private boolean isFullscreenRace() {
        return fullscreenRace;
    }

    /**
     * Brings the road up when the music starts.
     *
     * <p>The game is the point of the application, so it appears because a race began rather than
     * because somebody found a function key. It does not fight the user for the window, though:
     * once they have chosen the library, playing and pausing leaves them there until they choose
     * the road again.
     */
    private void showRaceOnPlay() {
        if (!racing && !libraryPinned) {
            toggleRace();
        }
    }

    /** Brings the header's toggle caption in step with what is on screen. */
    private void updateViewToggle() {
        if (viewToggle != null) {
            viewToggle.setText(racing ? "F6 LIBRARY" : "F6 RACE");
        }
    }

    // ------------------------------------------------------------------
    // Companion mode
    // ------------------------------------------------------------------

    /**
     * Puts the main window away and leaves the companion strip in its place.
     *
     * <p>The music does not stop, pause or restart: the engine has no idea either window exists.
     * That is the whole point of the mode, and it is what makes the two windows worth calling one
     * application rather than two - they are both looking at the same {@link AppState} and the same
     * {@link PlaybackEngine}.
     *
     * <p><strong>The companion is shown before the main window is hidden, and the order is not
     * cosmetic.</strong> JavaFX exits when the last window is hidden, so hiding this one first with
     * nothing else on screen would close the application instead of collapsing it.
     */
    private void collapseToCompanion() {
        if (miniStage == null) {
            buildCompanion();
        }
        if (miniStage.isShowing()) {
            return;
        }
        // The display goes back before the window does. A 224px strip is the opposite of a fullscreen
        // application, and on macOS a fullscreen window lives in a Space of its own - hiding it from
        // inside one leaves that Space on screen, empty, with the companion stranded on the desktop
        // behind it. Leaving the race first because a fullscreen race is a fullscreen window too, and
        // collapsing is leaving the race in any case: there is no longer a road to look at.
        if (fullscreenRace) {
            exitFullscreenRace();
        }
        setWindowFullscreen(false);
        if (!miniPlaced) {
            placeCompanion();
            miniPlaced = true;
        }
        miniStage.show();
        miniPlayer.start();
        suspendMainViews();
        mainStage.hide();
    }

    /**
     * Brings the main window back and puts the companion strip away.
     *
     * <p>The same ordering rule as above, in reverse.
     */
    private void expandFromCompanion() {
        if (miniStage == null || !miniStage.isShowing()) {
            return;
        }
        mainStage.show();
        mainStage.toFront();
        resumeMainViews();
        miniPlayer.stop();
        miniStage.hide();
    }

    /**
     * Builds the companion window, once, on the first time it is asked for.
     *
     * <p>Transparent and undecorated, like every other window here, and <strong>deliberately not
     * owned by the main window</strong>: an owned window is hidden along with its owner, which is
     * precisely the moment this one has to stay on screen.
     *
     * <p>It is kept on top because that is what a companion is for - it exists to be visible while
     * the user is doing something else, and one that disappears behind a browser is a window they
     * have to go and find.
     */
    private void buildCompanion() {
        miniStage = new Stage(StageStyle.TRANSPARENT);
        // Never AppConfig.APP_NAME: at 44 characters it is wider than this whole window.
        miniStage.setTitle(AppConfig.APP_NAME_SHORT);
        miniStage.setResizable(false);
        miniStage.setAlwaysOnTop(true);

        miniPlayer = new MiniPlayerView(state, player, engine, assets, beatmaps, miniStage);
        miniPlayer.setOnExpand(this::expandFromCompanion);
        // Through the same path as the header's close button, so quitting from either window shows the
        // shutdown screen. This strip is 224 pixels wide and has nowhere to draw one, so requestQuit
        // brings the main window back for it.
        miniPlayer.setOnQuit(this::requestQuit);

        Scene scene = new Scene(miniPlayer);
        // The stage is transparent, so the window's visible edge is the border the strip draws.
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        installCompanionShortcuts(scene);

        miniStage.setScene(scene);
        // Sized to its content, so the card and the record can never be clipped by a fixed number
        // that stopped matching. The pass has to come first: before CSS is applied the labels are
        // measured in the wrong font, and the window would be built around those measurements.
        miniPlayer.applyCss();
        miniPlayer.layout();
        miniStage.sizeToScene();
    }

    /**
     * Wires the companion window's keys.
     *
     * <p>Split across the two phases exactly as the main window's are, and for the same reason. The
     * expand key runs in a filter so it works wherever the pointer left the focus; the transport
     * keys run as a handler, so anything that wanted them first still gets them.
     *
     * @param scene the companion scene
     */
    private void installCompanionShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == MINI_KEY || event.getCode() == KeyCode.ESCAPE) {
                expandFromCompanion();
                event.consume();
            } else if (event.getCode() == COMPACT_KEY) {
                miniPlayer.setCompact(!miniPlayer.isCompact());
                event.consume();
            }
        });
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case LEFT, TRACK_PREV -> {
                    player.previous();
                    event.consume();
                }
                case RIGHT, TRACK_NEXT -> {
                    player.next();
                    event.consume();
                }
                case SPACE, PLAY, PAUSE -> {
                    engine.toggle();
                    miniPlayer.refresh();
                    event.consume();
                }
                default -> {
                    // Not a transport key; leave it alone.
                }
            }
        });
    }

    /**
     * Puts the companion strip over the middle of the window it is replacing, the first time only.
     *
     * <p>Where the user was already looking, rather than a corner they have to hunt for. Afterwards
     * the window keeps whatever position they dragged it to - a hidden stage remembers its own - so
     * this runs once and never moves it again.
     */
    private void placeCompanion() {
        if (mainStage == null || !mainStage.isShowing()) {
            miniStage.centerOnScreen();
            return;
        }
        // The window's own measured size, not the nominal constants: it is sized to its content.
        miniStage.setX(mainStage.getX() + (mainStage.getWidth() - miniStage.getWidth()) / 2);
        miniStage.setY(mainStage.getY() + (mainStage.getHeight() - miniStage.getHeight()) / 2);
    }

    /**
     * Stops everything the main window was drawing.
     *
     * <p>An {@code AnimationTimer} does not stop because the window it draws into was hidden - it is
     * driven by the toolkit's pulse, and the companion strip keeps that running. Left alone, four
     * canvases would carry on recording draw commands for a window nobody can see, on the one
     * arrangement where the application is expected to sit in the background for a whole album.
     *
     * <p>Stopping the runner also <em>files</em> whatever the run had achieved, which is the same
     * thing closing the window does and is the right answer to the same question: collapsing to the
     * companion is leaving the race, because there is no longer a road to look at.
     */
    /**
     * Applies the accessibility switch everywhere it reaches.
     *
     * <p>Two places, and both of them matter: the mood's layers stop scrolling and a reactive mood
     * stops following the music, and the runner stops zooming, washing and flashing on the beat.
     * The mood system's own notes are explicit that this must reach {@code RunnerView} and not only
     * {@code mood/} - on a 120 BPM track the runner's beat effects fire at 2 Hz, which is inside
     * the 3 Hz cap only by luck.
     *
     * @param on whether motion is suppressed
     */
    private void applyReduceMotion(boolean on) {
        if (overlay != null) {
            overlay.setReduceMotion(on);
        }
        if (runner != null) {
            runner.setReduceMotion(on);
        }
    }

    /**
     * Swaps the mood gallery for the customizer, without leaving the Moods destination.
     *
     * <p>The rail stays on MOODS: the customizer is a page of that destination rather than a
     * destination of its own, and adding an eighth rail button for something reached once a session
     * would cost every other button width it needs more.
     */
    private void showCustomizer() {
        if (racing) {
            toggleRace();
        }
        root.setCenter(moodCustomizer);
        moodCustomizer.refresh();
    }

    private void suspendMainViews() {
        meters.stop();
        beatmapTimeline.stop();
        playbackBar.stopClock();
        visualizer.stop();
        if (overlay != null) {
            overlay.stop();
        }
        runnerWasRunning = runner.isRunning();
        if (runnerWasRunning) {
            runner.stop();
        }
    }

    /** Picks all of it back up, and brings the bar in step with whatever happened while it was away. */
    private void resumeMainViews() {
        meters.start();
        beatmapTimeline.start();
        if (overlay != null) {
            overlay.start();
        }
        playbackBar.startClock();
        playbackBar.refresh();
        // Not an unconditional start: the column may have been folded away before the window was
        // put behind the companion, and coming back must not undo that.
        updateVisualizerDrawing();
        if (runnerWasRunning) {
            runner.start();
        }
        libraryView.refreshBadges();
    }

    /**
     * Loads the stored library, degrading to an empty one if the file cannot be read.
     *
     * @param repository where the library is stored
     * @return the loaded library, never {@code null}
     */
    private Library loadLibrary(Repository<Song> repository) {
        try {
            List<Song> stored = repository.loadAll();
            LOG.info("Loaded " + stored.size() + " songs from " + repository.storageLocation());
            return new Library(stored);
        } catch (PersistenceException e) {
            LOG.warning("Could not load the library: " + e.getMessage());
            Platform.runLater(() -> PixelDialog.error(null, "COULD NOT LOAD LIBRARY",
                    e.getMessage()
                            + "\n\nThe application has started with an empty library. "
                            + "The existing file has not been modified."));
            return new Library();
        }
    }

    /**
     * Prints what the launch actually proved and closes the window shortly afterwards.
     *
     * @param stage     the shown stage
     * @param scene     the scene under test
     * @param pixelFont whether the bundled font loaded
     */
    private void runSmokeTest(Stage stage, Scene scene, boolean pixelFont) {
        // First, because the boot screen is what is on the window until a cartridge goes into it and
        // every check below this line looks for the library.
        boolean bootOk = reportBoot(scene);

        boolean libraryViewPresent = scene.getRoot().lookup(".library-view") != null;
        boolean tablePresent = scene.getRoot().lookup(".table-view") != null;

        System.out.println("[smoke] window shown      : " + stage.isShowing());
        System.out.println("[smoke] window undecorated: " + (stage.getStyle() == StageStyle.TRANSPARENT));
        System.out.println("[smoke] window title      : " + stage.getTitle());
        System.out.println("[smoke] title matches     : " + AppConfig.APP_NAME.equals(stage.getTitle()));
        System.out.println("[smoke] stylesheets       : " + scene.getStylesheets().size());
        System.out.println("[smoke] 8-bit font loaded : " + pixelFont);
        System.out.println("[smoke] javafx runtime    : " + System.getProperty("javafx.runtime.version", "unknown"));
        System.out.println("[smoke] library view      : " + libraryViewPresent);
        System.out.println("[smoke] library table     : " + tablePresent);
        System.out.println("[smoke] songs loaded      : " + library.size());
        System.out.println("[smoke] library file      : " + libraryRepository.storageLocation());
        System.out.println("[smoke] playback mode     : " + player.mode().id().displayName()
                + " over " + player.mode().structureName());
        System.out.println("[smoke] mode holds        : " + player.mode().size() + " songs");
        System.out.println("[smoke] previous enabled  : " + player.canGoPrevious());
        System.out.println("[smoke] state mode        : " + state.getModeId());
        System.out.println("[smoke] visualizer view   : "
                + (visualizer.view() == null ? "- none -" : visualizer.view().getClass().getSimpleName()));
        System.out.println("[smoke] visualizer mode   : "
                + (visualizer.view() == null ? "-" : visualizer.view().modeId()));
        // Drives one operation of each kind so the measured column and the scatter plot are
        // exercised on a launch nobody is watching. An empty library makes these no-ops, which is
        // itself worth proving: the visualizer must come up with nothing loaded.
        player.next();
        player.previous();
        System.out.println("[smoke] measured next()   : " + describe(counter.latest("next()")));
        System.out.println("[smoke] measured build    : " + describe(counter.latest("build")));
        System.out.println("[smoke] samples plotted   : " + counter.sampleCount());

        // The shortcuts are wired across two phases of event delivery, which is exactly the kind
        // of arrangement that looks right and does nothing. Firing the keys proves it.
        Song beforeArrow = player.current();
        fireKey(scene, KeyCode.RIGHT);
        boolean arrowWorks = library.isEmpty() || player.current() != beforeArrow;
        System.out.println("[smoke] right arrow       : "
                + (arrowWorks ? "advances the song" : "DID NOTHING"));

        ModeId beforeTab = player.mode().id();
        fireKey(scene, KeyCode.TAB);
        boolean tabWorks = player.mode().id() != beforeTab;
        System.out.println("[smoke] tab               : " + beforeTab + " -> " + player.mode().id());

        boolean headerOk = reportHeader(scene);
        boolean foldOk = reportStructureFold(scene);
        boolean shrinkOk = reportWindowShrink(scene);
        boolean fullscreenOk = reportFullscreenRace(scene);
        boolean windowFullscreenOk = reportWindowFullscreen(scene);

        reportAudio();
        reportBeatmap();
        reportStreamedBeatmap();
        reportCourse();
        reportRunner(scene);
        boolean companionOk = reportCompanion();
        System.out.println("[smoke] assets found      : " + assets.size());
        System.out.println("[smoke] asset manifest    : " + assets.manifestFile());
        // Decodes each sheet, which normal startup does not do. Worth it here: how a sheet gets
        // sliced is inferred from its dimensions, and this is the only place that inference can
        // be checked against the real artwork - a unit test would need a graphics toolkit.
        for (AssetKind kind : AssetKind.values()) {
            if (kind == AssetKind.UNKNOWN) {
                continue;
            }
            String found = assets.firstEntry(kind)
                    .map(entry -> entry.relativePath()
                            + " (" + assets.sheet(kind).frameCount() + " frames)")
                    .orElse("- missing, placeholder -");
            System.out.printf("[smoke]   %-11s     : %s%n", kind.name().toLowerCase(), found);
        }

        boolean spotifyOk = reportSpotify();
        boolean moodsOk = reportMoods(scene);

        // Named rather than summed. A single FAIL over sixty lines of output is a red light
        // nobody can act on - the whole point of this run is that it says what is wrong, and a
        // verdict that makes the reader scroll back and re-derive it is half a check.
        List<String> failures = new ArrayList<>();
        if (!stage.isShowing()) {
            failures.add("window not shown");
        }
        if (stage.getStyle() != StageStyle.TRANSPARENT) {
            failures.add("window is decorated");
        }
        if (!AppConfig.APP_NAME.equals(stage.getTitle())) {
            failures.add("window title");
        }
        if (scene.getStylesheets().isEmpty()) {
            failures.add("no stylesheet");
        }
        if (!pixelFont) {
            failures.add("8-bit font");
        }
        if (!bootOk) {
            failures.add("boot screen");
        }
        if (!libraryViewPresent || !tablePresent) {
            failures.add("library view");
        }
        if (visualizer.view() == null || visualizer.view().modeId() != player.mode().id()) {
            failures.add("visualizer");
        }
        if (!arrowWorks) {
            failures.add("arrow key");
        }
        if (!tabWorks) {
            failures.add("tab key");
        }
        if (!headerOk) {
            failures.add("header fit");
        }
        if (!shrinkOk) {
            failures.add("window shrink");
        }
        if (!windowFullscreenOk) {
            failures.add("window fullscreen");
        }
        if (!fullscreenOk) {
            failures.add("race fullscreen");
        }
        if (!foldOk) {
            failures.add("dsa fold");
        }
        if (!companionOk) {
            failures.add("companion window");
        }
        if (!spotifyOk) {
            failures.add("spotify config");
        }
        if (!moodsOk) {
            failures.add("moods");
        }
        boolean ok = failures.isEmpty();
        System.out.println("[smoke] RESULT            : "
                + (ok ? "PASS" : "FAIL - " + String.join(", ", failures)));

        PauseTransition close = new PauseTransition(Duration.seconds(2));
        close.setOnFinished(e -> {
            // The base shot first, while audio is still flowing, so the meters in it are real.
            writeScreenshotIfRequested(scene);
            engine.pause();
            captureEveryView(scene);
            // Out through the application's own quit path rather than a bare Platform.exit(), which is
            // the only way to exercise it: the teardown now runs on a thread of its own behind a
            // shutdown screen, and the failure mode of getting that wrong is a process that never
            // exits. A smoke test that closed itself by a different route than the close button uses
            // would report nothing about the route the user takes - and if this deadlocks, the run
            // hangs here and says so, which is exactly what happened when Stage.setFullScreen was
            // called from inside this method.
            // It now waits for the eject animation as well as for the teardown, so the run takes
            // ShutdownScreen.EJECT_SECONDS longer than it used to and that is the feature rather than
            // a stall. If this line is the last one printed, the wait is not the animation - nothing
            // in it can block - and the exit is genuinely wedged.
            System.out.printf("[smoke] shutdown          : going out through the quit path, "
                    + "as the close button does (waiting %.1f s for the eject)%n",
                    ShutdownScreen.EJECT_SECONDS);
            requestQuit();
        });
        close.play();
    }

    /**
     * Drags the cartridge into the slot with real mouse events, and reports what happened.
     *
     * <p>Three things here can only be checked by doing it. Whether the press even reaches the
     * cartridge is a routing question - a canvas in front of it would swallow every one of them
     * while still hovering correctly, which is the fault that ate the companion window's transport
     * clicks and which neither a screenshot nor a unit test can see. Whether the threshold is a
     * threshold cannot be established by crossing it, so a short drag is made first and is expected
     * to be refused. And whether the boot actually hands the window over is the whole feature.
     *
     * @param scene the scene the boot screen is in
     * @return whether a short drag was refused and a long one booted
     */
    private boolean reportBoot(Scene scene) {
        if (bootScreen == null) {
            System.out.println("[smoke] boot screen       : ABSENT");
            return false;
        }
        layoutNow(scene);

        SpriteSheet sheet = bootScreen.cartridgeSheet();
        String art = sheet.isPlaceholder()
                ? "- missing, placeholder -"
                : assets.firstEntry(AssetKind.CARTRIDGE)
                        .map(entry -> entry.relativePath())
                        .orElse("- unnamed -");
        double inlet = sheet.footprint(0).map(foot -> foot.getWidth()).orElse(0d);
        System.out.println("[smoke] boot cartridge    : " + art
                + " (" + (int) sheet.frameWidth() + "x" + (int) sheet.frameHeight()
                + ", inlet " + (int) inlet + ")");
        System.out.println("[smoke] boot travel       : " + Math.round(bootScreen.travelPixels())
                + " px to seat");

        // **The one line that would catch every picture below coming out black.** The launch fades the
        // tube up out of the dark and this run deliberately never puts it to sleep - but a veil left
        // down photographs as a perfectly black rectangle, which is what a screen that failed to draw
        // looks like and is the one picture that would be believed. Nothing else in this run would
        // notice, because a black screenshot is still a screenshot.
        double warmedUp = bootScreen.wakeProgress();
        System.out.printf("[smoke] boot wake         : %s%n", warmedUp >= 1
                ? "picture at full strength, so every shot below is of something"
                : String.format("VEILED at %.2f - the shots below are of a dark screen", warmedUp));

        // The name goes on the cartridge's own label and nowhere else on this screen, so a label
        // that could not be measured means the name is not on screen at all - which no assertion
        // elsewhere would notice and which reads, in a picture, as artwork with nothing printed on
        // it rather than as a measurement that was refused.
        boolean labelled = sheet.darkRegion(0).isPresent();
        System.out.println("[smoke] boot label        : "
                + sheet.darkRegion(0)
                        .map(rect -> (int) rect.getWidth() + "x" + (int) rect.getHeight()
                                + " at (" + (int) rect.getMinX() + "," + (int) rect.getMinY()
                                + "), name printed on it")
                        .orElse("NO PANEL FOUND - the name has nowhere to go"));

        Node handle = bootScreen.cartridgeHandle();
        double travel = bootScreen.travelPixels();

        // The one moment this screen exists in. Everything photographed after this point is the
        // application behind it.
        captureIfRequested(scene, "boot");

        // Short of the threshold. It has to spring back, and nothing may start up.
        fireDrag(handle, travel * (BootScreen.INSERT_THRESHOLD / 2));
        boolean refused = !bootScreen.isSeated();
        System.out.println("[smoke] boot short drag   : "
                + (refused ? "refused, as it should be" : "SEATED BELOW THE THRESHOLD"));
        // The spring-back is a Timeline and no pulse will arrive, so the cartridge is left exactly
        // where the drag put it - which is the half-inserted picture worth having.
        layoutNow(scene);
        captureIfRequested(scene, "boot-partway");

        // Then the real thing, past the threshold - and it has to happen **here**, while the screen is
        // still waiting for a cartridge. It used to sit at the end, after the previews below, where it
        // was a no-op that read as a check: previewGlitch and previewShow put the screen into a phase
        // that correctly refuses the gesture, so every press, drag and release was dropped on the floor
        // and the line after it said nothing about the drag at all. Nothing would ever have noticed,
        // because the skip that follows works from that phase regardless.
        //
        // The seat itself is a Timeline whose onFinished starts the sequence, and no pulse will arrive
        // to run it, so this exercises the drag's routing and the release's own side effects; the skip
        // at the end is what actually gets to the finish.
        fireDrag(handle, travel);
        boolean accepted = bootScreen.insertion() >= BootScreen.INSERT_THRESHOLD;
        System.out.println("[smoke] boot full drag    : "
                + (accepted ? "past the threshold, committed to the slot"
                        : "THE DRAG NEVER REACHED THE CARTRIDGE"));

        // **And this is the only way to see when the clunk fires.** The whole point of moving it off
        // the tear is that it happens on the release, while the cartridge is still travelling - and the
        // tear is two tenths of a second later, behind a Timeline that never runs here, so a callback
        // that had slipped back onto the glitch would simply never fire and no picture would differ.
        // The sound itself is suppressed (see the wiring in start()): a screenshot must not make a
        // noise, so what is read is the callback rather than the audio.
        System.out.println("[smoke] cartridge clunk   : "
                + (cartridgeSeatedFired
                        ? "fires on the release, while the cartridge is still travelling"
                        : "NEVER FIRED - the clunk has slipped back behind the seat animation"));

        // The glitch and the loading bar cannot be photographed by waiting for them: this method
        // holds the interface thread, so the sequence's own timer never ticks. Both are asked for at
        // a stated instant instead, the way the companion window's spinning record is.
        // The white flash first, and it needs its own shot: it lasts FLASH_SECONDS of a GLITCH_SECONDS
        // tear, so a picture taken a third of the way through the glitch is taken after the flash is
        // already over - which is a photograph of the tear claiming to be a photograph of the flash.
        bootScreen.previewGlitch(0.05);
        layoutNow(scene);
        captureIfRequested(scene, "boot-flash");

        bootScreen.previewGlitch(0.35);
        layoutNow(scene);
        captureIfRequested(scene, "boot-glitch");

        // **The cartridge has to be gone by the time the tear starts, and this is where that is
        // checked rather than at the end.** The glitch used to blit the artwork back in twenty-two
        // torn bands, which looked in a still exactly like what it was meant to look like - a picture
        // breaking up - and in motion like the thing that had just been pushed into the machine still
        // hanging about on screen. It is off every canvas now, and the tear throws the scanlines
        // sideways instead. Read off the node, because the ImageView's foot hangs below the pane's own
        // bottom edge and is therefore off the shot on a tall window.
        boolean goneByTheGlitch = !bootScreen.cartridgeHandle().isVisible();
        System.out.println("[smoke] boot glitch       : "
                + (goneByTheGlitch ? "cartridge already off screen; the tear breaks up the display"
                        : "CARTRIDGE STILL DRAWN DURING THE TEAR"));

        // Then the show, one movement at a time. **Every one of these is a fade**, which is the single
        // most unphotographable thing there is: a still of the wrong instant is a still of an empty
        // screen and looks exactly like a screen that failed to draw. The instants are chosen to be the
        // middle of each movement rather than its edges, so what is in the shot is the movement rather
        // than the gap between two of them. previewShow runs none of the sequence's side effects, so
        // none of this starts a daemon or plays fifteen seconds of audio into a build log - which is
        // also why the caption the real boot sets is put here by hand.
        bootScreen.setStatus("GO-LIBRESPOT READY " + WAITING_FOR_THE_SOUND);
        for (BootScreen.Movement movement : BootScreen.Movement.values()) {
            bootScreen.previewShow(movement.instant());
            layoutNow(scene);
            captureIfRequested(scene, movement.label());
        }

        // The cartridge has to be *gone* by now, and this is the one thing on this screen a screenshot
        // cannot settle: the sliver that used to be left over hung below the pane's own bottom edge,
        // which is off the shot on a tall window and visible on a short one. Read off the node instead.
        boolean cartridgeGone = !bootScreen.cartridgeHandle().isVisible();

        // The sequence's length, which is the whole of what was asked for: the animation runs for as long
        // as the fanfare rather than for a number written down beside it. Reported against the sound's
        // own measured duration, so the two drifting apart is visible rather than assumed.
        double fanfareSeconds = bootFanfare.lengthSeconds();
        boolean lengthsAgree = fanfareSeconds <= 0
                || Math.abs(bootScreen.sequenceSeconds() - fanfareSeconds) < 0.05;
        System.out.printf("[smoke] boot sequence     : %.1f s of animation against %.1f s of fanfare%s%n",
                bootScreen.sequenceSeconds(), fanfareSeconds,
                lengthsAgree ? "" : "  THE PICTURE AND THE SOUND WILL NOT END TOGETHER");

        System.out.println("[smoke] boot cartridge in : "
                + (cartridgeGone ? "gone from the screen once it is in the machine"
                        : "STILL ON SCREEN BEHIND THE LOADING BAR"));

        // The name is the joke and this is the one screen with room for it at a size worth reading.
        // Measured rather than looked at, because a splash that silently fell back to its minimum
        // still draws perfectly well - it just stops being a splash.
        BootScreen.Splash splash = BootScreen.splashAt(scene.getWidth());
        boolean splashFits = splash.fits(scene.getWidth());
        System.out.printf("[smoke] boot splash       : %.0fpx over %d lines, widest %d chars = %.0f px "
                        + "in %.0f%s%n",
                splash.size(), splash.lines(), splash.widestChars(), splash.widthPixels(),
                scene.getWidth(), splashFits ? "" : "  DOES NOT FIT");

        // The fanfare is the one thing here that fails completely silently: a missing or undecodable
        // resource sounds exactly like a sound that was never triggered. Decoded without playing it,
        // because a build log is not a place to play fifteen seconds of audio.
        boolean fanfareOk = bootFanfare.isReady();
        System.out.printf("[smoke] boot fanfare      : %s%n", fanfareOk
                ? String.format("%s decodes, %.1f s", AppConfig.SOUND_BOOT,
                        bootFanfare.lengthSeconds())
                : AppConfig.SOUND_BOOT + " WILL NOT DECODE - the boot will be silent");

        // The two cartridge noises, decoded for the same reason and with one extra worth knowing:
        // **both are mono where the fanfare is stereo**, so they are the only things in the jar that
        // take PcmFormat's two-stage conversion - decode, then resample and mix up to stereo. That
        // path is documented in §6 and is exactly the one that "almost every real file" skips, so
        // nothing else that ships here would ever notice it breaking. A length of zero on either of
        // these lines is that conversion failing, and it sounds identical to a sound nobody triggered.
        boolean cartridgeSoundsOk = cartridgeIn.isReady() && cartridgeOut.isReady();
        System.out.printf("[smoke] cartridge sounds  : %s%n", cartridgeSoundsOk
                ? String.format("in %.2f s, out %.2f s - both mono, so both took the two-stage decode",
                        cartridgeIn.lengthSeconds(), cartridgeOut.lengthSeconds())
                : "ONE OF THE CARTRIDGE SOUNDS WILL NOT DECODE - "
                        + AppConfig.SOUND_CARTRIDGE_IN + " / " + AppConfig.SOUND_CARTRIDGE_OUT);

        // The check for the skip, and it has to be done from inside a running sequence - the
        // previews above left the screen part way through the show, which is exactly where a user in a
        // hurry presses a key. **A skip that did nothing would be invisible**: the sequence would simply
        // run its own length, which is what it does anyway, so nothing would look wrong for fifteen
        // seconds. It is also how this method finishes, so a broken skip fails the whole boot check
        // rather than being reported beside it.
        boolean skipped = bootScreen.skip();
        System.out.println("[smoke] boot skip         : "
                + (skipped ? "any key cuts the sequence short and hands the window over"
                        : "SKIP DID NOTHING - a 15 s boot with no way past it"));

        layoutNow(scene);
        boolean booted = bootScreen.isBooted() && !booting;
        boolean libraryUp = scene.getRoot().lookup(".library-view") != null;
        System.out.println("[smoke] boot insert       : "
                + (booted && libraryUp ? "seated, machine started, library shown"
                        : booted ? "started but THE LIBRARY DID NOT APPEAR" : "DID NOTHING"));

        // handOverFromTheDark fades the whole window up out of the console's black, and it is skipped
        // whole in this mode because no pulse arrives to advance a timeline. This is the check that it
        // really was skipped, and it is the same argument as `boot wake` one screen earlier: left at
        // opacity zero over an opaque black fill, **every screenshot in this run would come out
        // black** - and a black picture is exactly what a view that failed to lay out looks like,
        // which is the one photograph that would be believed. Nothing else here would notice.
        boolean handedOver = shell.getOpacity() == 1 && Color.TRANSPARENT.equals(scene.getFill());
        System.out.println("[smoke] boot handover     : "
                + (handedOver ? "window fully up and the fill transparent again"
                        : "STILL BEHIND THE HANDOVER FADE - opacity " + shell.getOpacity()
                                + ", fill " + scene.getFill() + "; every shot after this is black"));

        return refused && accepted && cartridgeSeatedFired && booted && libraryUp && labelled
                && cartridgeGone && goneByTheGlitch && splashFits && fanfareOk && cartridgeSoundsOk
                && lengthsAgree && skipped && handedOver;
    }

    /**
     * Measures the title bar against the window it has to fit in.
     *
     * <p>The strip now carries the application name, three view toggles, the version and three
     * window buttons, and in a font whose glyphs are one em wide that is a great deal of text on one
     * row. What it does when it runs out of room is <em>not</em> throw: the spacer collapses and
     * then the captions ellipsize, so the name reads {@code Super_Dwarf_Mario...} and the toggles
     * lose the very key they are printed to advertise. Nothing reports it and it is easy to miss in
     * a screenshot, because a truncated caption still looks deliberate.
     *
     * <p>The natural width is what is measured, not the laid-out width - the laid-out width always
     * fits, which is exactly the problem.
     *
     * @param scene the scene the header is in
     * @return whether the header's contents fit without being squeezed
     */
    private boolean reportHeader(Scene scene) {
        if (header == null) {
            System.out.println("[smoke] header fits       : ABSENT");
            return false;
        }
        layoutNow(scene);
        double wanted = header.prefWidth(-1);
        double available = scene.getWidth();
        boolean fits = wanted <= available;
        System.out.println("[smoke] header fits       : " + Math.round(wanted) + " of "
                + Math.round(available) + " px"
                + (fits ? " (" + Math.round(available - wanted) + " px spare)"
                        : " - OVERFLOWS BY " + Math.round(wanted - available) + " px"));
        return fits;
    }

    /**
     * Photographs the scene beside the given screenshot, when one was asked for.
     *
     * <p>For views that exist only for a moment. {@code captureEveryView} runs two seconds later
     * from a timeline, by which time the boot screen has been gone since before the first check.
     *
     * @param scene  the scene to capture
     * @param suffix what to call it, beside the base path
     */
    private void captureIfRequested(Scene scene, String suffix) {
        String destination = System.getProperty(SCREENSHOT_PROPERTY);
        if (destination == null || destination.isBlank()) {
            return;
        }
        writeScreenshot(scene, derivedPath(destination, suffix));
    }

    /**
     * Presses a node, drags it down by a distance and lets go.
     *
     * <p>Aimed at the node rather than at the scene, which is the opposite of {@link #fireKey}: the
     * shortcuts are wired on the scene and are meant to run wherever the focus is, where a drag
     * belongs to the thing being dragged and has to be delivered to it.
     *
     * @param target   the node to drag
     * @param distance how far down to drag it, in pixels
     */
    private static void fireDrag(Node target, double distance) {
        double startX = target.getLayoutBounds().getWidth() / 2;
        double startY = target.getLayoutBounds().getHeight() / 2;
        javafx.event.Event.fireEvent(target, mouse(MouseEvent.MOUSE_PRESSED, target, startX, startY));
        javafx.event.Event.fireEvent(target,
                mouse(MouseEvent.MOUSE_DRAGGED, target, startX, startY + distance));
        javafx.event.Event.fireEvent(target,
                mouse(MouseEvent.MOUSE_RELEASED, target, startX, startY + distance));
    }

    /**
     * Builds one mouse event in the node's own coordinates.
     *
     * @param type   which event
     * @param target the node it is aimed at
     * @param x      where in the node, across
     * @param y      where in the node, down
     * @return the event
     */
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, Node target,
            double x, double y) {
        javafx.geometry.Point2D onScreen = target.localToScreen(x, y);
        double screenX = onScreen == null ? x : onScreen.getX();
        double screenY = onScreen == null ? y : onScreen.getY();
        return new MouseEvent(type, x, y, screenX, screenY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, true, null);
    }

    /**
     * Folds the structure column away with the real key and reports what the middle of the window
     * got for it.
     *
     * <p>Three claims here are invisible to everything else. A screenshot of a folded column is a
     * picture of a window without one and says nothing about where its 400 pixels went - a node left
     * <em>managed</em> hides the column and hands the width to nobody, which photographs as a wide
     * blank margin that reads like a rendering fault rather than a layout one. Whether the
     * visualizer stopped drawing is not visible in any picture at all, and a timer left running is
     * silent forever. And the fold has to come back to the width it started at, which is the check
     * that neither pass leaked a pixel.
     *
     * @param scene the scene to fire the key at
     * @return whether the column folded, gave its width away and came back
     */
    private boolean reportStructureFold(Scene scene) {
        boolean drawingBefore = visualizer.view() != null && visualizer.view().isRunning();
        double before = centreWidth();

        fireKey(scene, STRUCTURE_KEY);
        layoutNow(scene);
        double folded = centreWidth();
        boolean stopped = visualizer.view() == null || !visualizer.view().isRunning();
        // The whole column, less nothing: the middle takes every pixel it gave up.
        boolean widthGiven = folded >= before + SIDE_COLUMN_WIDTH - 1;

        fireKey(scene, STRUCTURE_KEY);
        layoutNow(scene);
        double restored = centreWidth();
        boolean drawingAgain = (visualizer.view() != null && visualizer.view().isRunning())
                == drawingBefore;
        boolean cameBack = Math.abs(restored - before) < 1;

        System.out.printf("[smoke] dsa fold          : %.0f -> %.0f -> %.0f px%s%s%s%n",
                before, folded, restored,
                widthGiven ? "" : "  COLUMN WIDTH WENT NOWHERE",
                stopped ? "  (view stopped)" : "  VIEW STILL DRAWING WHILE HIDDEN",
                cameBack && drawingAgain ? "" : "  DID NOT COME BACK");
        return widthGiven && stopped && cameBack && drawingAgain;
    }

    /**
     * Grows the window and shrinks it back, and reports whether the interface followed it down.
     *
     * <p><strong>A window can only grow if anything inside it has a minimum size larger than the
     * window is.</strong> The application comes up maximised and restores to
     * {@link AppConfig#MAIN_WIDTH}, so that is not a corner case - it is the restore button, and it
     * is what this catches. The fault it was written for is that
     * {@link com.eia.superdwarfkart.ui.MoodOverlayRenderer} is a {@code StackPane} holding two
     * canvases sized <em>to itself</em>: a {@code Canvas} is not resizable and reports its own width
     * as its minimum, so the pane holding the whole middle of the window could never go under
     * whatever it last was. Restoring left every view still laid out at the maximised size with the
     * window's edge cutting through it.
     *
     * <p>Nothing else can see this. A screenshot is taken at one size, so it photographs a perfectly
     * good interface; the crop is only ever visible in the second size. And a unit test cannot reach
     * it either - the quantity is a minimum computed by a live scene graph.
     *
     * <p>The <em>root</em> is resized rather than the stage, because a stage resize on this platform
     * comes back through the window system on a later pulse and the smoke test is holding the
     * interface thread. Resizing the root is what the scene itself does when the stage changes size,
     * and it is synchronous. It is put back to the pixel afterwards.
     *
     * @param scene the scene, laid out between each step
     * @return whether the middle of the window grew and came back
     */
    private boolean reportWindowShrink(Scene scene) {
        layoutNow(scene);
        double startWidth = shell.getWidth();
        double startHeight = shell.getHeight();
        double before = centreWidth();

        shell.resize(startWidth + SHRINK_PROBE_GROWTH, startHeight + SHRINK_PROBE_GROWTH);
        layoutNow(scene);
        double grown = centreWidth();

        shell.resize(startWidth, startHeight);
        layoutNow(scene);
        double back = centreWidth();

        // Stated against the window rather than against the number it started at: what the restore
        // button asks for is a middle that fits inside the window, and a middle wider than the
        // window it is in is precisely the crop being looked for.
        boolean fitsAgain = back <= startWidth + 1 && Math.abs(back - before) < 1;
        boolean grewWithIt = grown > before + 1;

        System.out.printf("[smoke] window shrink     : centre %.0f -> %.0f -> %.0f px "
                        + "in a window of %.0f%s%s%n",
                before, grown, back, startWidth,
                grewWithIt ? "" : "  DID NOT GROW",
                fitsAgain ? "" : "  STUCK WIDE - THE WINDOW WILL CROP IT");
        return grewWithIt && fitsAgain;
    }

    /**
     * Drives F11 and Escape at the real scene, and reports what the window actually did.
     *
     * <p><strong>Nothing else can see any of this.</strong> A screenshot of a fullscreen race and a
     * screenshot of a windowed one differ only by the size of the image, and the smoke test does not
     * even maximise; the interesting quantities are all state - whether the stage went fullscreen at
     * all, whether the road ended up in the pane the layers are drawn on, whether the title bar came
     * off, and whether every one of those came back on the way out. The last of those is the one worth
     * the most: an exit that restored the stage and forgot the header would leave a window with no way
     * to move or close it, which is the {@code scene.setRoot} trap this application has already been
     * caught by once.
     *
     * <p>It is driven with a real key event rather than by calling the methods, because a shortcut
     * wired into the wrong phase of event delivery is a routing fault that looks perfect from the
     * inside - and this one has to survive the early return the mode installs for every other key.
     *
     * <p><strong>The one thing not exercised is {@code Stage.setFullScreen} itself</strong>, which is
     * skipped during a smoke test - it enters a nested event loop on macOS and deadlocks a run that is
     * holding the interface thread. See {@link #smokeTest()}, and read the printed line: it says so
     * rather than implying the platform was asked and agreed.
     *
     * @param scene the scene to fire keys at
     * @return whether the mode was entered, left, and left cleanly
     */
    private boolean reportFullscreenRace(Scene scene) {
        boolean wasRacing = racing;
        Node centreBefore = overlay.getContent();

        fireKey(scene, FULLSCREEN_KEY);
        layoutNow(scene);
        boolean entered = fullscreenRace;
        boolean roadHasTheWindow = overlay.getContent() == runner;
        boolean barGone = shell.getTop() == null;
        boolean frameGone = shell.getStyleClass().contains("no-frame");
        boolean startedRace = racing;

        // Every other shortcut has to be dead while this is up: F6 would swap the library into a pane
        // that is not on screen and leave the road showing, which is the shape of fault that reads as
        // a key doing nothing while quietly having done something.
        fireKey(scene, RACE_KEY);
        boolean othersIgnored = fullscreenRace && overlay.getContent() == runner;

        fireKey(scene, KeyCode.ESCAPE);
        layoutNow(scene);
        boolean left = !fullscreenRace && !mainStage.isFullScreen();
        boolean restored = shell.getTop() == header && overlay.getContent() == centreBefore
                && !shell.getStyleClass().contains("no-frame");

        // Back to whatever the window was doing before, since this is the middle of a longer run.
        if (racing != wasRacing) {
            toggleRace();
        }
        layoutNow(scene);

        System.out.println("[smoke] race fullscreen   : F11 "
                + (entered ? "gave the road the display" : "DID NOTHING")
                + (startedRace ? " (started the race)" : "")
                + ", " + (barGone && frameGone ? "title bar and frame off"
                        : barGone ? "bar off but THE FRAME STAYED" : "THE TITLE BAR STAYED")
                + ", " + (othersIgnored ? "F6 correctly ignored" : "F6 CHANGED THE VIEW UNDER IT")
                + ", ESC " + (left ? "left" : "DID NOT LEAVE")
                + (restored ? " and put both back" : " AND LEFT THE WINDOW WITHOUT ITS BAR")
                + "  (the stage's own setFullScreen is skipped here - it deadlocks a synchronous run)");
        return entered && roadHasTheWindow && barGone && frameGone
                && othersIgnored && left && restored;
    }

    /**
     * Drives the window's own fullscreen mode - the one the application launches in - and reports it.
     *
     * <p><strong>This is the mode a user meets first and the one nothing else here photographs.</strong>
     * The run deliberately does not launch into it: the frame comes off, so every screenshot afterwards
     * would be of a borderless window rather than of the one that opens on a desktop. So it is entered
     * and left on purpose, in the middle of the run, and the quantities are all state - a flag, a style
     * class, a caption and the interface still being there.
     *
     * <p><strong>The caption is checked because it is the only way out.</strong> On a display with no
     * chrome the button is the visible affordance, and one that went on saying "fill the display" while
     * already filling it reads as a control that did nothing - which is exactly what a user would
     * conclude before pressing it again and going nowhere.
     *
     * <p>And the title bar has to <em>stay</em>, which is the difference from a fullscreen race worth
     * asserting rather than assuming: this mode gives the display to the whole application, so taking
     * the header off would leave the window with no way back and no way to close.
     *
     * @param scene the scene to fire Escape at
     * @return whether the mode was entered, reported itself, and left cleanly
     */
    private boolean reportWindowFullscreen(Scene scene) {
        boolean framedBefore = !shell.getStyleClass().contains("no-frame");

        setWindowFullscreen(true);
        layoutNow(scene);
        boolean entered = windowFullscreen;
        boolean frameGone = shell.getStyleClass().contains("no-frame");
        boolean barStayed = shell.getTop() == header;
        boolean saysSo = FULLSCREEN_ON_CAPTION.equals(fullscreenToggle.getText());

        // Escape rather than the button, because that is the path with somewhere to go wrong: the
        // toolkit's own exit key is switched off for the session and this is the handler that replaced
        // it, sitting behind two other Escapes that must win first.
        fireKey(scene, KeyCode.ESCAPE);
        layoutNow(scene);
        boolean left = !windowFullscreen;
        boolean framedAgain = !shell.getStyleClass().contains("no-frame");
        boolean captionBack = FULLSCREEN_OFF_CAPTION.equals(fullscreenToggle.getText());

        System.out.println("[smoke] window fullscreen : "
                + (entered ? "took the display" : "DID NOTHING")
                + ", " + (frameGone ? "frame off" : "THE FRAME STAYED")
                + ", " + (barStayed ? "title bar kept" : "THE TITLE BAR WENT - NO WAY BACK")
                + ", button " + (saysSo ? "reads \"" + FULLSCREEN_ON_CAPTION + "\""
                        : "STILL SAYS \"" + FULLSCREEN_OFF_CAPTION + "\"")
                + ", ESC " + (left ? "left" : "DID NOT LEAVE")
                + (framedAgain && captionBack ? " and put the frame and the caption back"
                        : " BUT LEFT THE WINDOW INCONSISTENT")
                + "  (the stage's own setFullScreen is skipped here - it deadlocks a synchronous run)");
        return framedBefore && entered && frameGone && barStayed && saysSo
                && left && framedAgain && captionBack;
    }

    /**
     * Installs every mood in turn and reports what each one actually costs to draw.
     *
     * <p><strong>Three claims here are invisible to everything else.</strong>
     *
     * <ul>
     *   <li><em>A mood whose layers are all static runs no frame loop at all.</em> That is the whole
     *       performance argument of the overlay system, and it is a property of the renderer at
     *       runtime rather than of any definition - a layer that scrolls by accident, or a blend
     *       mode that stops a run being flattenable, silently turns a free mood into one that blits
     *       the whole canvas sixty times a second. No screenshot shows it and no unit test reaches
     *       it, because a stopped {@code AnimationTimer} and a running one look identical.</li>
     *   <li><em>Every built-in rasterises without throwing.</em> A layer definition that reads
     *       perfectly and produces a zero-sized image, or names a tile that is not in the mood, only
     *       fails when something asks it to draw.</li>
     *   <li><em>"Reduce motion" actually stops the loop.</em> A switch that set a flag nobody read
     *       would look exactly like this one, and the accessibility case is the one where a
     *       half-working switch is worse than none.</li>
     * </ul>
     *
     * <p>The per-frame figure carries the same caveat as the runner's, and for the same reason: a
     * {@code Canvas} call records a command rather than painting a pixel, so this measures the cost
     * of writing the frame down. It is the right number for comparing moods against each other,
     * which is what it is for. The honest end-to-end figure is the frame interval under
     * {@code -Dsdmk.diag}.
     *
     * @param scene the scene, so the pane can be laid out before it is measured
     * @return whether every mood installed, rasterised and behaved as its layers describe
     */
    private boolean reportMoods(Scene scene) {
        layoutNow(scene);
        Mood before = state.getMood();
        List<Mood> all = moods.all();
        System.out.println("[smoke] moods             : " + Moods.builtIns().size()
                + " built in, " + moods.userMoods().size() + " of the user's own");
        System.out.println("[smoke] moods folder      : " + moods.storageLocation());

        boolean ok = true;
        String heaviest = "-";
        double worstFrame = -1;
        long worstRebuild = 0;

        for (Mood mood : all) {
            long start = System.nanoTime();
            state.setMood(mood);
            layoutNow(scene);
            long rebuildMillis = (System.nanoTime() - start) / 1_000_000;
            worstRebuild = Math.max(worstRebuild, rebuildMillis);

            int live = overlay.liveLayerCount();
            boolean looping = overlay.isRunning();
            // The claim, stated as an equality rather than as a hope: a loop runs exactly when
            // something needs redrawing, and never otherwise.
            boolean loopMatchesLayers = looping == (live > 0 || mood.reactive());
            ok &= loopMatchesLayers;

            if (live > worstFrame) {
                worstFrame = live;
                heaviest = mood.id();
            }

            boolean valid = com.eia.superdwarfkart.mood.MoodValidator.isValid(mood.palette());
            ok &= valid;

            System.out.printf("[smoke]   %-15s : %d layers, %s%s%s%s%n",
                    mood.id(),
                    mood.layers().size(),
                    live == 0 ? "all flattened, no frame loop" : live + " redrawn per frame",
                    mood.reactive() ? ", reactive" : "",
                    valid ? "" : "  PALETTE FAILS THE VALIDATOR",
                    loopMatchesLayers ? "" : "  FRAME LOOP DISAGREES WITH THE LAYERS");
        }

        long still = all.stream().filter(mood -> !mood.needsAnimation()).count();
        System.out.println("[smoke] moods that cost 0 : " + still + " of " + all.size()
                + " are flattened to a still picture and never redrawn");
        System.out.println("[smoke] mood rebuild      : worst " + worstRebuild
                + " ms, paid on a mood change or a resize and never per frame");

        // The number that has no blind spot in it, and the reason it is measured this way. A Canvas
        // call records a command rather than painting a pixel, and on this machine the two are
        // three orders of magnitude apart: Prism falls back to its software pipeline here, so a
        // full-canvas fill is about ten milliseconds and fill rate is the whole budget.
        // Scene.snapshot rasterises the window synchronously through that same pipeline, so the
        // difference between two of them is what a mood genuinely costs a frame.
        //
        // Measured this way, the mood system's own notes ask for 58 fps with the heaviest mood and
        // the game running - and that figure is unreachable here for a reason that predates this
        // milestone by two: a frame with no layers at all already takes thirty milliseconds.
        double plain = timeRasterisedFrames(scene, Moods.DARK);
        final String heaviestId = heaviest;
        Mood worst = all.stream().filter(mood -> mood.id().equals(heaviestId)).findFirst()
                .orElse(Moods.BOWSER_CASTLE);
        double withLayers = timeRasterisedFrames(scene, worst);
        System.out.printf("[smoke] mood frame cost   : %.1f ms with no layers, %.1f ms on %s"
                        + "  - drifting layers add %.1f ms, still ones add nothing%n",
                plain, withLayers, worst.id(), withLayers - plain);

        state.setMood(before);

        // Reduce motion, on the heaviest mood there is, so there is something to stop.
        Mood moving = all.stream().filter(mood -> mood.needsAnimation()).findFirst().orElse(null);
        if (moving == null) {
            System.out.println("[smoke] reduce motion     : no mood moves, so there is nothing to stop");
        } else {
            state.setMood(moving);
            layoutNow(scene);
            boolean loopedBefore = overlay.isRunning();
            state.setReduceMotion(true);
            boolean stopped = !overlay.isRunning() && runner.isReduceMotion();
            state.setReduceMotion(false);
            boolean restarted = overlay.isRunning();
            ok &= loopedBefore && stopped && restarted;
            System.out.println("[smoke] reduce motion     : " + moving.id() + " "
                    + (loopedBefore ? "loops" : "DOES NOT LOOP") + " -> "
                    + (stopped ? "stopped, and the runner's beat effects with it"
                            : "STILL LOOPING") + " -> "
                    + (restarted ? "loops again" : "DID NOT RESTART"));
        }

        ok &= reportMoodPersistence();

        layoutNow(scene);
        return ok;
    }

    /**
     * Builds a mood the way the customizer does, writes it, reopens the folder and reads it back.
     *
     * <p>The unit tests cover this shape thoroughly over a temporary directory. What they cannot
     * cover is <strong>this</strong> directory: whether the folder the running application actually
     * points at exists, is writable, and gives the mood back. A mood that applied perfectly and was
     * gone at the next launch would look like a mood that was never saved, and nothing in the
     * session it was built in would say so.
     *
     * <p>Cleans up after itself, because a smoke test must not leave a mood in somebody's switcher.
     *
     * @return whether the round trip worked
     */
    private boolean reportMoodPersistence() {
        String id = moods.uniqueId("smoke-check");
        PaletteRole role = PaletteRole.ACCENT;
        Color wanted = GbaColor.web("#39ce9c");

        Mood built = Moods.PEACH_CIRCUIT.copyAs(id, "Smoke Check")
                .withPalette(Moods.PEACH_CIRCUIT.palette().withColor(role, wanted))
                .withTile("smoke", PixelTile.blank(8))
                .withLayerAdded(ProceduralLayer.of(ProceduralLayer.Pattern.LCD_GRID, 0.2));

        String outcome;
        boolean ok = false;
        try {
            moods.save(built);
            // Reopened rather than asked of the instance that just wrote it: the question is what
            // is on disk, and an in-memory cache would answer for the copy in front of it.
            Mood back = new MoodRepository(moods.storageLocation()).byId(id).orElse(null);
            if (back == null) {
                outcome = "SAVED BUT DID NOT COME BACK";
            } else {
                boolean colourKept = GbaColor.toHex(back.color(role))
                        .equals(GbaColor.toHex(wanted));
                boolean layerKept = back.layers().size() == built.layers().size();
                boolean tileKept = back.tile("smoke") != null;
                ok = colourKept && layerKept && tileKept;
                outcome = ok
                        ? "palette, " + back.layers().size() + " layer and its tile all came back"
                        : "LOST" + (colourKept ? "" : " the edited colour")
                                + (layerKept ? "" : " a layer") + (tileKept ? "" : " the tile");
            }
            moods.delete(id);
        } catch (java.io.IOException | RuntimeException e) {
            outcome = "FAILED - " + e.getMessage();
        }

        System.out.println("[smoke] mood round trip   : " + outcome);
        return ok;
    }

    /**
     * How long a frame takes to actually rasterise with a mood installed.
     *
     * <p>{@code Scene.snapshot} runs the whole window through Prism synchronously, which is the same
     * pipeline the pulse uses - so this is the cost of the pixels rather than the cost of writing
     * down what to draw. That distinction is worth more here than anywhere else in the application:
     * this machine has <em>no working GPU</em> (see the project's own notes on
     * {@code -Dprism.verbose}), so a full-canvas alpha fill costs about ten milliseconds and a
     * measurement that only counted commands would report a layer stack as free while the window
     * crawled.
     *
     * <p>It is not the frame <em>interval</em> - there is no vsync here, no layout pass and no
     * competing timer - so it understates a real frame. What it measures honestly is the
     * <em>difference</em> between two moods, which is the quantity the mood system is answerable
     * for.
     *
     * @param scene the scene to rasterise
     * @param mood  the mood to install first
     * @return the mean milliseconds per rasterised frame
     */
    private double timeRasterisedFrames(Scene scene, Mood mood) {
        state.setMood(mood);
        layoutNow(scene);
        // One outside the timing, so the first snapshot's allocation is not charged to the mood.
        scene.snapshot(null);

        int frames = 8;
        long began = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            overlay.repaint();
            scene.snapshot(null);
        }
        return (System.nanoTime() - began) / 1_000_000d / frames;
    }

    /**
     * Reports what the Spotify integration found, without connecting to anything.
     *
     * <p>Deliberately passive. Starting the daemon during a smoke test would launch a subprocess,
     * bind a port and - on a machine that has never logged in - sit waiting for somebody to finish
     * an OAuth flow in a browser, on a run whose whole purpose is to close itself after two
     * seconds. What is checked here is everything that can be checked without a Spotify account:
     * that the platform is understood, that the binary lookup answers, and that the generated
     * configuration still carries the four values which decide whether this application or Spotify
     * owns the running order.
     *
     * <p>Those four are the reason this prints at all. Every one of them fails <em>silently</em>:
     * playback goes on sounding perfectly normal while the hand-written structures stop being
     * consulted, and no exception, log line or screenshot would ever show it.
     *
     * @return whether the configuration is safe to hand to the daemon
     */
    private boolean reportSpotify() {
        var binary = spotify.binary();
        System.out.println("[smoke] spotify platform  : "
                + (SpotifyBinary.isSupportedPlatform()
                        ? "supported (POSIX named pipe)"
                        : "UNSUPPORTED - no named pipe on this platform"));
        System.out.println("[smoke] spotify daemon    : " + binary.origin().label()
                + (binary.path() == null ? "" : " - " + binary.path()));
        if (!binary.isFound()) {
            // Not a failure. A machine with no daemon runs everything else exactly as before,
            // which is the whole point of the feature disabling itself rather than breaking.
            System.out.println("[smoke] spotify install   : " + binary.detail());
        }

        String yaml = SpotifyConfig.render();
        boolean autoplayOff = yaml.contains("disable_autoplay: true");
        boolean zeroconfOff = yaml.contains("zeroconf_enabled: false");
        boolean noCrossfade = yaml.contains("crossfade_duration: 0");
        boolean pipeBackend = yaml.contains("audio_backend: pipe")
                && yaml.contains("audio_output_pipe_format: s16le");
        boolean configOk = autoplayOff && zeroconfOff && noCrossfade && pipeBackend;

        System.out.println("[smoke] spotify config    : "
                + (configOk
                        ? "running order stays with the PlaybackMode"
                        : "BROKEN"
                                + (autoplayOff ? "" : "  AUTOPLAY ON")
                                + (zeroconfOff ? "" : "  ZEROCONF ON")
                                + (noCrossfade ? "" : "  CROSSFADE ON")
                                + (pipeBackend ? "" : "  NOT THE PIPE BACKEND")));
        String detail = spotify.detail();
        System.out.println("[smoke] spotify state     : " + spotify.state().label()
                + (detail.isBlank() || detail.equals(spotify.binary().detail())
                        ? "" : " - " + detail));
        reportCatalogueSearch();
        reportCovers();
        return configOk;
    }

    /**
     * Starts go-librespot while the loading bar runs, and reports how far it got.
     *
     * <p>The cartridge going in is the start ritual, and a console reading a cartridge is exactly
     * the right cover for the one thing at startup that genuinely takes a moment. It is also the
     * answer to the question the Spotify page kept raising: connecting was a step whose necessity
     * nothing on screen explained, and the natural place to do it is the screen that already looks
     * like something loading.
     *
     * <p><strong>The boot never waits for it.</strong> The bar runs its own length and hands over
     * regardless — a daemon that is slow, absent, or waiting on a browser login must not be able to
     * hold the application shut (ground rule 5). What the caption shows is how far it had got by
     * the time the machine finished reading, and the Spotify page carries on from there.
     */
    private void bootLoadSpotify() {
        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            // A smoke test starts no subprocess and takes no port; the build must not depend on it.
            bootScreen.setStatus("SKIPPING GO-LIBRESPOT");
            return;
        }
        if (!spotify.isAvailable()) {
            bootScreen.setStatus((SpotifyBinary.isSupportedPlatform()
                    ? "GO-LIBRESPOT NOT INSTALLED "
                    : "GO-LIBRESPOT UNSUPPORTED HERE ") + WAITING_FOR_THE_SOUND);
            return;
        }
        if (spotify.isConnected()) {
            bootScreen.setStatus("GO-LIBRESPOT READY " + WAITING_FOR_THE_SOUND);
            return;
        }
        bootScreen.setStatus("LOADING GO-LIBRESPOT...");
        spotify.connect();
    }

    /**
     * Keeps the boot caption in step with the daemon.
     *
     * <p>Only while the boot screen is up: afterwards the Spotify page is the thing that reports
     * this, and writing to a screen nobody can see would be a listener quietly kept alive for the
     * session.
     */
    private void updateBootStatus() {
        if (!booting) {
            return;
        }
        bootScreen.setStatus(switch (spotify.state()) {
            // Every one of these is a *finished* state, and the bar is still running - so the caption
            // says what is actually being waited for rather than leaving a stale "READY" up while the
            // screen appears to be doing something. It is honest about it: the work is done and the
            // fanfare is the only reason the screen is still there, and the line under this one says
            // any key gets past it.
            case CONNECTED -> "GO-LIBRESPOT READY " + WAITING_FOR_THE_SOUND;
            case AWAITING_LOGIN -> "GO-LIBRESPOT NEEDS A LOGIN " + WAITING_FOR_THE_SOUND;
            case FAILED -> "GO-LIBRESPOT DID NOT START " + WAITING_FOR_THE_SOUND;
            case UNAVAILABLE -> "GO-LIBRESPOT NOT INSTALLED " + WAITING_FOR_THE_SOUND;
            // The one state that genuinely is still working.
            case STARTING, READY_TO_CONNECT -> "LOADING GO-LIBRESPOT...";
        });
    }

    /**
     * What the boot caption says once there is nothing left to load.
     *
     * <p>The bar has always been a beat rather than a measurement, and it now runs for the length of the
     * fanfare - so once the daemon has resolved, one way or the other, the only thing still holding the
     * screen up is the music. Saying so is better than a "READY" that sits there while the bar carries
     * on filling, which reads as a machine that has lost track of what it was doing.
     */
    private static final String WAITING_FOR_THE_SOUND = "- WAITING FOR THE EPIC SOUND TO FINISH";

    /**
     * The streamed song that is waiting for the daemon to finish coming up, or {@code null}.
     *
     * <p>Held rather than retried in a loop, because connecting is asynchronous and may include a
     * person completing a login in a browser.
     */
    private Song awaitingSpotify;

    /**
     * Whether the user has already been told the daemon is not installed.
     *
     * <p>Once per session. Skipping through a running order of streamed songs would otherwise raise
     * one dialog per song, which is the failure the playback engine already avoids for missing
     * files.
     */
    private boolean toldSpotifyMissing;

    /**
     * A song would not open.
     *
     * <p>For a streamed song this is nearly always "the daemon is not running", which is an
     * ordinary state - nothing starts it until it is needed, and until now that meant pressing play
     * on a Spotify track did nothing whatsoever and said nothing anywhere. Two outcomes are
     * possible and they need opposite responses: <strong>if the daemon is installed, connect and
     * carry on</strong>, because the user has expressed exactly the intent that would have made
     * them press CONNECT; <strong>if it is not, say so</strong>, because no amount of waiting will
     * fix it and the search that put the track in the library gave no hint that playing it needs
     * anything else.
     *
     * @param song   the song that would not open
     * @param reason what the audio layer said
     */
    private void songWouldNotOpen(Song song, String reason) {
        if (song == null || !song.isSpotify() || spotify.isConnected()) {
            // A local file that has moved. The engine has logged it and the bar shows the failure;
            // a dialog per song while skipping through a stale library would be worse than useless.
            return;
        }

        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            // A smoke test must not launch a subprocess, take a network port or wait on a login.
            // The whole point of it is that a build never depends on any of those.
            System.out.println("[smoke] spotify autoconnect: suppressed for \""
                    + song.getTitle() + "\"");
            return;
        }

        if (!spotify.isAvailable()) {
            tellSpotifyIsNotInstalled(song);
            return;
        }

        // Already on its way up: the next state change will pick this song up.
        awaitingSpotify = song;
        if (spotify.state() == SpotifySession.State.STARTING
                || spotify.state() == SpotifySession.State.AWAITING_LOGIN) {
            return;
        }
        LOG.info("Connecting to Spotify to play \"" + song.getTitle() + "\"");
        spotify.connect();
    }

    /**
     * Explains that a streamed song needs go-librespot, and how to get it.
     *
     * @param song the song that could not be played
     */
    private void tellSpotifyIsNotInstalled(Song song) {
        awaitingSpotify = null;
        if (toldSpotifyMissing) {
            return;
        }
        toldSpotifyMissing = true;

        String command = SpotifyBinary.installCommand();
        String how;
        if (!SpotifyBinary.isSupportedPlatform()) {
            how = "This platform has no named pipe, which Spotify playback needs.";
        } else if (command != null) {
            how = "Install it with:\n" + command + "\n\nThen open SPOTIFY on the side rail.";
        } else if (SpotifyBinary.isDownloadable()) {
            how = "Open SPOTIFY on the side rail and press DOWNLOAD.";
        } else {
            how = "No go-librespot build is published for this platform.";
        }

        PixelDialog.warn(mainStage, "SPOTIFY NOT INSTALLED",
                song.getTitle() + "\nby " + song.getArtist()
                        + "\n\nThis track streams from Spotify, which needs go-librespot."
                        + "\n\n" + how
                        + "\n\nSearching still works; only playing needs the daemon.");
    }

    /**
     * Picks up a song that was waiting for the daemon.
     *
     * <p>Runs on every state change, and does nothing unless a song is actually waiting - which is
     * why it is cheap enough to hang off the session's own notification rather than polling.
     */
    private void spotifyStateChanged() {
        updateBootStatus();

        Song waiting = awaitingSpotify;
        if (waiting == null) {
            return;
        }
        switch (spotify.state()) {
            case CONNECTED -> {
                awaitingSpotify = null;
                // Still the song the user asked for? They may have moved on while the daemon came
                // up, and starting a track they have navigated away from would be worse than doing
                // nothing at all.
                if (waiting.equals(player.current())) {
                    LOG.info("Spotify is connected - starting \"" + waiting.getTitle() + "\"");
                    engine.play();
                    playbackBar.refresh();
                }
            }
            case FAILED, UNAVAILABLE -> {
                awaitingSpotify = null;
                PixelDialog.warn(mainStage, "SPOTIFY DID NOT CONNECT",
                        waiting.getTitle()
                                + "\n\n" + spotify.detail()
                                + "\n\nOpen SPOTIFY on the side rail to try again.");
            }
            default -> {
                // STARTING or AWAITING_LOGIN: still working, keep waiting.
            }
        }
    }

    /**
     * Reports whether the library's artwork can actually be resolved and decoded.
     *
     * <p><strong>A cover that fails to load leaves a placeholder, which is exactly what a song with
     * no artwork looks like.</strong> Nothing throws and nothing is logged at a level anybody
     * reads, so the two are indistinguishable on screen — and a streamed song carries its artwork
     * as a URL rather than a file, which is a whole second way for it to be missing that no
     * screenshot of a local library would ever exercise. This counts both kinds and waits for the
     * remote ones, because "it resolved to an address" is not the same claim as "it decoded to
     * pixels".
     */
    private void reportCovers() {
        int local = 0;
        int remote = 0;
        int none = 0;
        List<javafx.scene.image.Image> pending = new java.util.ArrayList<>();

        for (Song song : library.all()) {
            javafx.scene.image.Image image = CoverArt.of(song, 256);
            if (image == null) {
                none++;
            } else if (song.getCoverPath() != null) {
                local++;
            } else {
                remote++;
                pending.add(image);
            }
        }

        // A remote cover cannot be waited for here, and the reason is worth writing down: JavaFX
        // decodes it on a background thread but publishes progress and completion *on the interface
        // thread*, which the smoke test is holding. Sleeping in a loop until it finishes therefore
        // guarantees it never does - measured, five seconds of waiting reported neither decoded nor
        // failed. The running application has the thread free and the listener in CoverArt fires
        // normally; what is checked here instead is that the artwork is real, by decoding one
        // synchronously. That proves the address and the bytes, and the screenshot below proves the
        // rest.
        int decoded = 0;
        int failed = 0;
        String first = "";
        for (Song song : library.all()) {
            String url = song.getCoverUrl();
            if (url == null || song.getCoverPath() != null) {
                continue;
            }
            javafx.scene.image.Image check =
                    new javafx.scene.image.Image(url, 256, 256, true, true);
            if (check.isError() || check.getWidth() <= 0) {
                failed++;
            } else {
                decoded++;
                if (first.isEmpty()) {
                    first = "  first is " + (int) check.getWidth() + "x" + (int) check.getHeight();
                }
            }
        }

        System.out.println("[smoke] covers            : " + local + " from disk, "
                + remote + " remote (" + decoded + " decode, " + failed + " failed), "
                + none + " with none" + first);

        // Put a song with remote artwork in the details panel, so the screenshots taken later show
        // it. By then the interface thread has been free for two seconds and the background load
        // has landed - which is the only way this is ever visible.
        library.all().stream()
                .filter(song -> song.getCoverUrl() != null && song.getCoverPath() == null)
                .findFirst()
                .ifPresent(song -> libraryView.showInDetails(song));
        pending.clear();
    }

    /**
     * Runs one real catalogue search, when an application has been configured.
     *
     * <p><strong>This is the check that no screenshot and no unit test can stand in for.</strong>
     * A search failing inside the running application looks identical to one failing in a test
     * harness that passes - the credentials are the same, the class is the same, and the difference
     * is somewhere in how the application wired them together. Reported here, in the application's
     * own process, against the real service.
     *
     * <p>Costs exactly one request, on the user's own quota, and only when they have configured an
     * application at all. Nothing is printed that could identify the credentials.
     */
    private void reportCatalogueSearch() {
        if (!spotify.isCatalogSearch()) {
            System.out.println("[smoke] spotify search    : no application configured "
                    + "(catalogue search off)");
            return;
        }
        // The limit the interface actually asks for. Spotify refuses anything above ten outright while
        // its own documentation says fifty, and a diagnostic that asked for a different number than the
        // interface is not testing the interface - that is what hid that fault for a whole session.
        List<com.eia.superdwarfkart.spotify.SpotifyTrack> found =
                spotify.searchTracks("the beatles", SpotifyCatalog.MAX_SEARCH_LIMIT);
        String problem = spotify.searchProblem();
        System.out.println("[smoke] spotify search    : " + found.size() + " results"
                + (found.isEmpty() ? "  PROBLEM: " + problem : "  e.g. " + found.get(0)));
        if (found.isEmpty()) {
            return;
        }

        // The genre lookup, and this line is worth more for what it says when it comes back empty than
        // for what it says when it works. Measured on 2026-08-17: v1/artists/{id} on an application
        // token answers 200 with *no genres key at all* - not an empty array, absent - so on a Client
        // Credentials token there is currently no route from Spotify to a genre, and the reference still
        // documents the field. The add dialog's real answer is the library's own knowledge of the
        // artist; this request is the upgrade for the day Spotify sends the field again. Printed rather
        // than asserted, because an empty answer is now the correct one and failing the build on it
        // would be a red light nobody can act on.
        com.eia.superdwarfkart.spotify.SpotifyTrack first = found.get(0);
        List<String> tags = spotify.artistGenres(first.artistId());
        System.out.println("[smoke] spotify genre     : " + first.artist()
                + (first.artistId() == null ? " carries no artist id" : "")
                + " -> " + (tags.isEmpty()
                        ? "Spotify sends no genres field any more (measured); "
                                + "the dialog falls back to the library"
                        : String.join(", ", tags) + "  = "
                                + com.eia.superdwarfkart.model.Genre.fromTags(tags).displayName()));
        System.out.println("[smoke] library genre     : "
                + first.artist() + " -> " + library.genreForArtist(first.artist()).displayName()
                + "  (what the add dialog actually pre-fills with)");
    }

    /**
     * @return the measured width of whatever is in the middle of the window
     */
    private double centreWidth() {
        Node centre = root.getCenter();
        return centre == null ? 0 : centre.getBoundsInParent().getWidth();
    }

    /**
     * Forces the layout pass a measurement needs.
     *
     * <p>Nothing has a size until the scene has laid it out, and the smoke test holds the interface
     * thread, so no pulse arrives to do it on its own.
     *
     * @param scene the scene to lay out
     */
    private static void layoutNow(Scene scene) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    /**
     * Plays the current song briefly and reports what came out.
     *
     * <p>Everything about the audio path is invisible to both a unit test and a screenshot. A test
     * cannot open a sound card, and a picture of a level meter proves only that a rectangle was
     * drawn. Actually playing a second of the current song and printing the four levels is the one
     * check that the file opened, the decoder produced the expected format, the tap fired and left
     * and right were measured separately - and it is also the check for the mistake that matters
     * most here, which is the two channels reading identically because they were never
     * deinterleaved.
     */
    private void reportAudio() {
        Song song = player.current();
        System.out.println("[smoke] audio file        : "
                + (song == null ? "- no song -" : song.getFilePath()));
        if (song == null) {
            return;
        }

        engine.play();
        System.out.println("[smoke] audio loaded      : " + engine.isLoaded()
                + (engine.failure() == null ? "" : " (" + engine.failure() + ")"));
        if (!engine.isLoaded()) {
            return;
        }

        // Blocking the interface thread is normally forbidden; during a smoke test nobody is
        // waiting on it, and there is no other way to let real audio arrive before measuring it.
        sleepQuietly(1000);

        System.out.println("[smoke] audio playing     : " + engine.isPlaying());
        System.out.printf("[smoke] track length      : %.1fs%n", engine.durationSeconds());
        System.out.println("[smoke] position after 1s : " + engine.position().toMillis() + "ms");
        System.out.printf("[smoke] level L           : rms %.4f  peak %.4f%n",
                levels.leftRms(), levels.leftPeak());
        System.out.printf("[smoke] level R           : rms %.4f  peak %.4f%n",
                levels.rightRms(), levels.rightPeak());
        System.out.println("[smoke] channels differ   : "
                + (levels.leftRms() != levels.rightRms()
                        ? "yes - deinterleaved per channel"
                        : "no - mono, silent, or NOT deinterleaved"));
        // Deliberately left playing. The screenshot is taken a couple of seconds from here, and a
        // picture of two meters that have already fallen to silence says nothing about either.
    }

    /**
     * Waits for the current song's beatmap and reports what was found.
     *
     * <p>A beat analysis is the other half of this project that no unit test and no screenshot can
     * check against real music. A test can prove the detector finds clicks it was handed; only a
     * real track proves the whole chain - decode, novelty, threshold, tempo histogram, phase - holds
     * together on audio nobody synthesised.
     *
     * <p><strong>The deviation line is the one that matters.</strong> A tempo is always a plausible
     * number; a grid the beats actually sit on is not. A few milliseconds means the detected beat is
     * the one in the music, and anything approaching a quarter of the beat means the histogram
     * picked a tempo the track does not have.
     */
    private void reportBeatmap() {
        Song song = player.current();
        if (song == null) {
            System.out.println("[smoke] beatmap           : - no song -");
            return;
        }

        // Blocking the interface thread is normally forbidden. During a smoke test nobody is
        // waiting on it, and the alternative is reporting on an analysis that has not happened yet.
        boolean finished = beatmaps.await(java.time.Duration.ofSeconds(30));
        BeatmapService.Status status = beatmaps.status();
        Beatmap beatmap = status.beatmap();

        System.out.println("[smoke] beatmap state     : " + status.stage()
                + (status.isReady() ? (status.fromCache() ? " (from cache)" : " (analysed now)") : "")
                + (finished ? "" : " - TIMED OUT"));
        if (status.failure() != null) {
            System.out.println("[smoke] beatmap failure   : " + status.failure());
            return;
        }
        System.out.printf("[smoke] beatmap tempo     : %.1f BPM (beat every %.3fs)%n",
                beatmap.bpm(), beatmap.beatPeriod());
        System.out.println("[smoke] beatmap onsets    : " + beatmap.onsetCount()
                + " (" + beatmap.strongBeatCount() + " on the beat)");
        System.out.printf("[smoke] beatmap length    : %.1fs%n", beatmap.durationSeconds());
        double deviation = beatmap.gridDeviationSeconds();
        System.out.printf("[smoke] grid deviation    : %s%n", deviation < 0
                ? "- too few beats to judge -"
                : String.format("%.1f ms  %s", deviation * 1000,
                        deviation < beatmap.beatPeriod() / 10
                                ? "- beats sit on the grid"
                                : "- SCATTERED, tempo is probably wrong"));
        System.out.println("[smoke] beatmap cache     : " + beatmaps.cache().directory());
        if (!beatmap.sourceHash().isEmpty()) {
            System.out.println("[smoke] beatmap file      : "
                    + beatmaps.cache().fileFor(beatmap.sourceHash()).getFileName());
        }
    }

    /**
     * Builds a beatmap the way a streamed track has to, and checks it against the file's own.
     *
     * <p><strong>This is how the Spotify course path is verified without a Spotify account.</strong>
     * A streamed track has no file, so its beatmap is built from the audio going past the playback
     * tap - and the only property that matters is that the result is the <em>same</em> beatmap the
     * file analyser would have produced. If the two disagree, a score earned on a streamed track
     * means nothing on a local copy of the same recording, and neither a screenshot nor a synthetic
     * click track would show it: {@code StreamBeatmapBuilderTest} pins the agreement on audio this
     * project generated, and this line pins it on real music.
     *
     * <p>The blocks handed over are read through {@link PcmFormat}, which is the same decode
     * playback uses, so these are the bytes the tap would genuinely see.
     */
    private void reportStreamedBeatmap() {
        Song song = player.current();
        java.nio.file.Path file = song == null ? null : song.getFilePath();
        if (file == null) {
            System.out.println("[smoke] stream beatmap    : - no local track to compare against -");
            return;
        }

        Beatmap read = beatmaps.status().isReady() ? beatmaps.beatmap() : Beatmap.EMPTY;
        if (read.isEmpty()) {
            System.out.println("[smoke] stream beatmap    : - the file's own analysis is not ready -");
            return;
        }

        long startedAt = System.nanoTime();
        Beatmap heard;
        long frames = 0;
        try (com.eia.superdwarfkart.analysis.StreamBeatmapBuilder builder =
                     new com.eia.superdwarfkart.analysis.StreamBeatmapBuilder()) {
            builder.arm(read.sourceHash(), read.durationSeconds());
            try (javax.sound.sampled.AudioInputStream in = PcmFormat.open(file)) {
                // AudioInputStream reads whole frames, which is what a PcmListener is promised.
                byte[] block = new byte[4096];
                for (int read0 = in.read(block); read0 > 0; read0 = in.read(block)) {
                    builder.pcm(block, 0, read0);
                    frames += read0 / AppConfig.BYTES_PER_FRAME;
                }
            }
            heard = builder.finishAndWait(java.time.Duration.ofSeconds(30));
        } catch (Exception e) {
            System.out.println("[smoke] stream beatmap    : FAILED - " + e.getMessage());
            return;
        }

        double seconds = (System.nanoTime() - startedAt) / 1e9;
        if (heard == null) {
            System.out.println("[smoke] stream beatmap    : REFUSED after "
                    + frames + " frames - the run was not usable");
            return;
        }

        boolean sameTempo = Math.abs(heard.bpm() - read.bpm()) < 0.001;
        boolean sameOnsets = java.util.Arrays.equals(heard.onsets(), read.onsets());
        boolean sameBeats = java.util.Arrays.equals(heard.strongBeats(), read.strongBeats());
        System.out.printf("[smoke] stream beatmap    : %.1f BPM, %d onsets, %d on the beat "
                        + "(%.1fs of listening at %.0fx realtime)%n",
                heard.bpm(), heard.onsetCount(), heard.strongBeatCount(), seconds,
                heard.durationSeconds() / Math.max(seconds, 1e-6));
        System.out.println("[smoke] stream vs file    : "
                + (sameTempo && sameOnsets && sameBeats
                        ? "identical - a streamed track generates the same course as a local copy"
                        : "DIFFERENT - tempo " + (sameTempo ? "ok" : "differs")
                                + ", onsets " + (sameOnsets ? "ok" : "differ")
                                + ", beats " + (sameBeats ? "ok" : "differ")));
    }

    /**
     * Generates the current song's course at every speed class and reports what came out.
     *
     * <p>Three things here cannot be checked any other way. The <strong>entity counts per
     * class</strong> are the claim that difficulty comes from the music rather than from a timer,
     * and they are only meaningful against a real track's onsets. The <strong>determinism</strong>
     * line regenerates each course and compares it, which is what the stored high scores rest on.
     * And the <strong>scripted lap</strong> drives the whole course through the collision rules at
     * the frame rate the game actually runs at - a rank of S out of that says the lookahead, the
     * resolution window and the scoring all agree with each other over four minutes of real
     * beatmap, which no screenshot could show and no unit test could reach with this much data.
     */
    private void reportCourse() {
        Song song = player.current();
        if (song == null) {
            System.out.println("[smoke] course            : - no song -");
            return;
        }
        Beatmap beatmap = beatmaps.beatmap();
        for (SpeedClass speedClass : SpeedClass.values()) {
            Course course = Course.generate(song.getId(), beatmap, speedClass);
            Course again = Course.generate(song.getId(), beatmap, speedClass);
            boolean deterministic = course.entities().equals(again.entities());
            System.out.printf("[smoke]   %-6s course   : %d coins, %d bumps, %d walls, %d stars, "
                            + "%.2fs lookahead  %s  lap %s%n",
                    speedClass.displayName(), course.coinsAvailable(), course.obstacleCount(),
                    course.wallCount(), course.starCount(), course.travelTimeSeconds(),
                    deterministic ? "- reproducible" : "- NOT REPRODUCIBLE",
                    driveScriptedLap(course));
        }
    }

    /**
     * Proves the driving controls reach the game, and measures what a frame of it costs.
     *
     * <p>Both of these were guessed at once and both guesses were wrong. The controls used to wait
     * for keyboard focus, which nothing on screen indicated and no key could reliably give them, so
     * jumping silently paused the music instead - a fault a screenshot cannot show and a unit test
     * cannot reach, because it lives entirely in how the scene routes an event. Firing the real key
     * at the real scene is the only check that means anything.
     *
     * <p>The frame timing answers the other one. "It looks laggy" has several possible causes and
     * they need different fixes: drawing every entity on the course rather than the ones on screen
     * would show up here as a large and song-length-dependent number, where a projection that makes
     * things crawl and then rush shows up as a fast frame that still looks wrong.
     *
     * @param scene the scene to deliver keys to
     */
    private void reportRunner(Scene scene) {
        toggleRace();
        layoutNow(scene);
        runner.previewAt(screenshotMoment());

        RunnerGame game = runner.game();
        System.out.println("[smoke] runner course     : " + game.course());

        Lane before = game.lane();
        // Away from whichever edge the racer is on. It is the middle lane here, because previewAt
        // jumps the clock rather than driving - but a key pressed into the wall is a key that
        // correctly does nothing, and this line would report that as a control that never arrived.
        KeyCode towards = before == Lane.LEFT ? KeyCode.RIGHT : KeyCode.LEFT;
        fireKey(scene, towards);
        System.out.println("[smoke] steering          : " + before + " -" + towards + "-> "
                + game.lane() + (game.lane() == before ? "  DID NOTHING" : "  ok"));

        fireKey(scene, KeyCode.SPACE);
        boolean jumped = game.isJumping();
        // Half a jump on, so the reported height is the top of the arc rather than the instant of
        // take-off, where it is legitimately zero and looks like a failure.
        game.update(game.now() + RunnerGame.JUMP_SECONDS / 2);
        System.out.println("[smoke] jump              : " + (jumped
                ? String.format("airborne, %.0f%% of the way up at the apex", game.jumpHeight() * 100)
                : "DID NOTHING"));

        // What is actually on screen, against what the course holds. The gap between the two is the
        // whole of the lookahead's job.
        int onScreen = game.lastVisible() - game.firstVisible() + 1;
        System.out.println("[smoke] entities drawn    : " + Math.max(0, onScreen)
                + " of " + game.course().size() + " on the course");

        long startedAt = System.nanoTime();
        int frames = 120;
        for (int frame = 0; frame < frames; frame++) {
            runner.redraw();
        }
        double perFrame = (System.nanoTime() - startedAt) / 1e6 / frames;
        System.out.printf("[smoke] frame cost        : %.2f ms  (%.0f fps headroom)  %s%n",
                perFrame, 1000 / perFrame,
                perFrame < 16.6 ? "- comfortably inside a 60 fps frame" : "- TOO SLOW FOR 60 FPS");

        toggleRace();
    }

    /**
     * Chooses the instant the runner is drawn at for its screenshot.
     *
     * <p>Aimed at a <strong>wall three quarters of the way down the road</strong>, because that one
     * frame carries more of this milestone than any other: the lookahead as a picture, a row of
     * obstacles the accents put there, and the jump prompt. Falling back to a fixed moment, and
     * clamped into the track - a short sample has no 45th second, and drawing past the end shows
     * an empty road.
     *
     * @return where in the track to draw, in seconds
     */
    private double screenshotMoment() {
        double length = beatmaps.beatmap().durationSeconds();
        double fallback = length > 0
                ? Math.min(SCREENSHOT_COURSE_SECONDS, length * 0.6)
                : SCREENSHOT_COURSE_SECONDS;

        Song song = player.current();
        if (song == null) {
            return fallback;
        }
        Course course = Course.generate(song.getId(), beatmaps.beatmap(), state.getSpeedClass());
        // The wall nearest the preferred moment, in either direction. Searching only forwards found
        // nothing on the eight-second sample in the library, whose one wall is before it.
        double chosen = -1;
        for (Entity entity : course.entities()) {
            if (entity instanceof Obstacle obstacle && obstacle.isWall()
                    && (chosen < 0 || Math.abs(obstacle.beatTime() - fallback)
                            < Math.abs(chosen - fallback))) {
                chosen = obstacle.beatTime();
            }
        }
        // Three quarters of the way down the road: close enough to read as a wall, far enough that
        // the lookahead behind it is still in shot.
        return chosen < 0 ? fallback : chosen - course.travelTimeSeconds() * 0.25;
    }

    /**
     * Opens the companion window, checks what no picture of it could, and collapses it again.
     *
     * <p>Four things here are invisible to both a unit test and a screenshot.
     *
     * <ul>
     *   <li><strong>The name.</strong> {@link AppConfig#APP_NAME} is 44 characters, and in a font
     *       whose glyphs are one em wide that is most of a 420 pixel window. Leaking it into this
     *       strip is the documented trap, and it is invisible until the window is on screen - so
     *       every label in it is read back and checked, rather than trusted.</li>
     *   <li><strong>Overflow.</strong> The whole layout is built from widths that have to add up to
     *       the window, and nothing throws when they do not: the text simply runs out of the side.
     *       The measured size is printed against the two constants, and every label is checked
     *       against the window's edge.</li>
     *   <li><strong>The record turning.</strong> A picture of a spinning disk is a static disk. Two
     *       moments are asked for and compared, which is also the check that it <em>stops</em>: the
     *       frame is a function of the playback position, so a position that stops advancing is a
     *       record that stops.</li>
     *   <li><strong>The shared state.</strong> Changing the racer has to change the sprite riding
     *       the disk immediately. That is one binding, and a binding that was never made looks
     *       exactly like one that was.</li>
     * </ul>
     *
     * @return whether everything checked here held
     */
    private boolean reportCompanion() {
        collapseToCompanion();
        Scene scene = miniStage.getScene();
        layoutNow(scene);

        System.out.println("[smoke] companion shown   : " + miniStage.isShowing()
                + ", main window hidden: " + !mainStage.isShowing());
        System.out.printf("[smoke] companion size    : %.0f x %.0f  (nominal %.0f x %.0f)  %s%n",
                miniStage.getWidth(), miniStage.getHeight(),
                AppConfig.MINI_WIDTH, AppConfig.MINI_HEIGHT,
                miniStage.getWidth() <= AppConfig.MINI_WIDTH + 1
                        && miniStage.getHeight() <= AppConfig.MINI_HEIGHT + 1
                        ? "- fits" : "- OVERFLOWS ITS CONSTANTS");

        String longName = null;
        String widest = "";
        double overrun = 0;
        for (javafx.scene.Node node : scene.getRoot().lookupAll(".label")) {
            if (!(node instanceof Label label)) {
                continue;
            }
            String text = label.getText();
            if (text != null && text.contains(AppConfig.APP_NAME)) {
                longName = text;
            }
            double right = label.localToScene(label.getBoundsInLocal()).getMaxX();
            if (right - scene.getWidth() > overrun) {
                overrun = right - scene.getWidth();
                widest = text;
            }
        }
        boolean nameOk = longName == null;
        System.out.println("[smoke] companion name    : "
                + (nameOk ? "short name only - APP_NAME kept out" : "APP_NAME LEAKED: " + longName));
        System.out.println("[smoke] companion labels  : " + (overrun <= 0
                ? "all inside the window"
                : String.format("\"%s\" RUNS %.0f px PAST THE EDGE", widest, overrun)));

        // Two moments of the record, and then the same moment twice.
        int first = miniPlayer.previewAt(0);
        int later = miniPlayer.previewAt(0.5);
        int again = miniPlayer.previewAt(0.5);
        boolean spins = first != later && later == again;
        System.out.println("[smoke] companion disk    : frame " + first + " -> " + later
                + (spins ? ", and still at " + again + " when the position does not move"
                        : "  DOES NOT FOLLOW THE POSITION"));

        // The sprite riding the disk comes from the shared state, not from a copy of it.
        Racer wasRacer = state.getRacer();
        var beforeSheet = miniPlayer.racerSheet();
        Racer other = wasRacer == Racer.MARIO ? Racer.YOSHI : Racer.MARIO;
        state.setRacer(other);
        boolean racerBound = miniPlayer.racerSheet() != beforeSheet;
        System.out.println("[smoke] companion racer   : " + wasRacer + " -> " + other
                + (racerBound ? "  sprite followed the shared state" : "  SPRITE DID NOT FOLLOW"));
        state.setRacer(wasRacer);

        // The song sits on the cartridge's label, whose size comes from the artwork rather than from
        // this code. Contents too tall for it are not clipped - they carry on down over the grey
        // body, still inside the window, so every other check here passes while it looks broken.
        var panel = miniPlayer.labelBounds();
        double overflow = miniPlayer.labelOverflow();
        boolean labelOk = overflow <= 0 && miniPlayer.labelShortfall() <= 0
                && Math.abs(miniPlayer.inletMisalignment()) <= 2;
        System.out.printf("[smoke] companion label   : %.0f x %.0f measured off the artwork%s%n",
                panel.getWidth(), panel.getHeight(),
                overflow > 0
                        ? String.format("  - CONTENT OVERFLOWS IT BY %.0f px", overflow)
                        : miniPlayer.labelShortfall() > 0
                                ? String.format("  - NARROWER THAN THE CAPTIONS ASSUME BY %.0f px",
                                        miniPlayer.labelShortfall())
                                : "  - the song fits on it");

        double titleSize = MiniPlayerView.titleSize();
        double advance = com.eia.superdwarfkart.ui.Fonts.advance(titleSize);
        System.out.printf("[smoke] companion glyph   : %.2f px per character at %.0fpx "
                        + "(one em would be %.0f), scrolling title shows %d of a nominal %d%n",
                advance, titleSize, titleSize,
                miniPlayer.measuredMarqueeLimit(), MiniPlayerView.nominalMarqueeLimit());

        double inlet = miniPlayer.inletMisalignment();
        System.out.printf("[smoke] companion inlet   : %s%n", Math.abs(inlet) <= 2
                ? "the cartridge's foot lines up with the record"
                : String.format("OUT BY %.0f px - the artwork's step is not %s",
                        inlet, "what the width was derived from"));

        // The record overlaps the card's foot and the kart stands higher still, so whether the
        // transport can actually be clicked is a real question - and the failure is mute. The
        // buttons draw, they highlight on hover, and they do nothing.
        var blocker = miniPlayer.blockingTheTransport();
        boolean clickable = blocker == null;
        System.out.println("[smoke] companion clicks  : " + (clickable
                ? "transport is reachable by mouse"
                : "BLOCKED BY " + blocker.getClass().getSimpleName()
                        + " - the keys will draw and do nothing"));

        // Space is the key this window is most likely to get wrong, and it is unconditional: a
        // focused button swallows it before the transport ever sees it, which is exactly how the
        // road's jump was found dead. Nothing here is focus-traversable for that reason.
        boolean wasPlaying = engine.isPlaying();
        fireKey(scene, KeyCode.SPACE);
        boolean spaceReaches = engine.isPlaying() != wasPlaying;
        System.out.println("[smoke] companion space   : " + (spaceReaches
                ? (wasPlaying ? "playing -> paused" : "paused -> playing")
                : "DID NOTHING - a control took it first"));
        if (engine.isPlaying() != wasPlaying) {
            // Left as it was found: the base screenshot is taken while audio is still flowing, and
            // a picture of two meters that have fallen silent says nothing about either.
            engine.toggle();
        }

        // The arrow only has somewhere to go if the running order does. By this point the smoke
        // test has stepped a two-song queue to its end, and a disabled next control doing nothing
        // is the queue behaving correctly rather than the key failing to arrive.
        boolean canAdvance = player.canGoNext();
        Song beforeKey = player.current();
        fireKey(scene, KeyCode.RIGHT);
        boolean arrowReaches = !canAdvance || player.current() != beforeKey;
        System.out.println("[smoke] companion arrow   : " + (!canAdvance
                ? "nothing to advance to - " + player.mode().structureName() + " is exhausted"
                : arrowReaches ? "advances the song" : "DID NOTHING"));
        boolean keysReach = spaceReaches && arrowReaches;

        // Collapsing hides most of the window's contents, and the failure mode is a strip still the
        // size of the whole cartridge with a hole where the artwork was - the toolkit does not shrink
        // a window because its contents no longer fill it.
        double fullWidth = miniStage.getWidth();
        double fullHeight = miniStage.getHeight();
        miniPlayer.setCompact(true);
        miniStage.sizeToScene();
        boolean shrank = miniStage.getWidth() < fullWidth && miniStage.getHeight() < fullHeight * 0.6;
        boolean transportSurvived = miniPlayer.blockingTheTransport() == null;
        System.out.printf("[smoke] companion compact : %.0f x %.0f -> %.0f x %.0f%s%s%n",
                fullWidth, fullHeight, miniStage.getWidth(), miniStage.getHeight(),
                shrank ? "" : "  - DID NOT SHRINK",
                transportSurvived ? "" : "  - TRANSPORT UNREACHABLE");
        miniPlayer.setCompact(false);
        miniStage.sizeToScene();
        boolean sizeRestored = Math.abs(miniStage.getWidth() - fullWidth) < 1
                && Math.abs(miniStage.getHeight() - fullHeight) < 1;
        boolean compactOk = shrank && transportSurvived && sizeRestored;
        if (!sizeRestored) {
            System.out.println("[smoke] companion compact : DID NOT COME BACK TO ITS FULL SIZE");
        }

        // Hide is the one control here whose behaviour belongs to the platform rather than to this
        // application, and an undecorated transparent window is exactly the case where a window
        // manager may decline to minimise. Reported rather than asserted for that reason - a dead
        // button on one platform is worth knowing about and is not a reason to fail a build.
        miniStage.setIconified(true);
        boolean minimises = miniStage.isIconified();
        miniStage.setIconified(false);
        System.out.println("[smoke] companion hide    : "
                + (minimises ? "minimises to the dock" : "REFUSED BY THE WINDOW MANAGER"));

        expandFromCompanion();
        boolean restored = mainStage.isShowing() && !miniStage.isShowing();
        System.out.println("[smoke] companion expand  : "
                + (restored ? "main window back, strip away" : "DID NOT SWAP BACK"));

        return nameOk && overrun <= 0 && labelOk && spins && racerBound && clickable
                && compactOk && keysReach && restored;
    }

    /**
     * Drives a course from start to finish with a scripted driver, at sixty frames a second.
     *
     * <p>The policy lives in {@link ScriptedDriver}, which the runner's own screenshot uses too.
     * A course that a competent driver <em>cannot</em> get a good rank on is a generated course the
     * rules cannot survive: two bumps too close together to dodge, or an entity placed where the
     * resolution window can never reach it. That is what this catches, over four minutes of real
     * beatmap, on every launch.
     *
     * <p><strong>It also reports the best combo the driver reached</strong>, which is the only check
     * there is that the multiplier is reachable on real music rather than only in a unit test - a
     * meter that never leaves {@code x1} on a real four-minute beatmap is decoration, and that is a
     * property of the generator against a particular track rather than of the scoring rules, so
     * nothing but driving one can establish it.
     *
     * <p>Measured on {@code Crimewave}, all four classes reach {@code x10}, and what separates them
     * is how long they hold it: the driver takes no bumps at all at 50cc and 100cc and sits at the
     * top of the meter for about ninety percent of the run, against a third of it at 150cc and
     * 200cc where it takes twenty and thirty-five. That spread is what {@code COMBO_TINT_ALPHA} was
     * tuned against - a clean run at the easy classes is what the screen looks like most of the
     * time, so the standing tint has to be something worth living with rather than a reward.
     *
     * @param course the course to drive
     * @return the rank the scripted driver earned
     */
    private static String driveScriptedLap(Course course) {
        ScoreKeeper lap = ScriptedDriver.driveLap(course);
        if (lap == null) {
            return "- empty -";
        }
        return lap.rank() + " " + lap.coinsCollected() + "/" + course.coinsAvailable()
                + " combo x" + lap.bestCombo();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Snapshots each structure view and presentation mode in turn.
     *
     * <p>There are four layouts here that no unit test can check. Overflow in a fixed-width pixel
     * font is invisible to assertions and obvious in a picture, and three of these views only
     * appear once a mode has been selected, so a single screenshot of the opening state proves
     * almost nothing about them.
     *
     * <p>Runs only when a screenshot destination was given, and writes beside it: a base path of
     * {@code shot.png} produces {@code shot-arrival.png} and so on.
     *
     * @param scene the scene to capture
     */
    private void captureEveryView(Scene scene) {
        String destination = System.getProperty(SCREENSHOT_PROPERTY);
        if (destination == null || destination.isBlank()) {
            return;
        }

        // Shuffle explicitly rather than relying on the opening state: the keyboard check above
        // presses Tab, so by the time this runs the application is no longer in the mode it
        // started in and the base screenshot is of whatever Tab landed on.
        player.setMode(new ShuffleMode(new Random(), counter));
        player.next();
        layoutAndCapture(scene, destination, "shuffle");

        player.setMode(new ArrivalOrderMode(counter));
        player.next();
        layoutAndCapture(scene, destination, "arrival");

        player.setMode(new AlphabeticalMode(counter));
        // Stepping forward leaves a successor walk lit, which is the part of this view worth
        // looking at - a screenshot of a tree with nothing traversed shows only the layout.
        player.next();
        player.next();
        layoutAndCapture(scene, destination, "alphabetical");

        togglePresentation();
        layoutAndCapture(scene, destination, "presentation");
        togglePresentation();

        // Each rail destination in turn. All four are new views that only exist once their button
        // has been pressed, so the base shot says nothing whatsoever about any of them.
        for (Destination target : new Destination[] {
                Destination.HISTORY, Destination.RACERS, Destination.SPOTIFY, Destination.SETTINGS}) {
            sideRail.select(target);
            layoutAndCapture(scene, destination, target.name().toLowerCase());
        }

        // The light mood, photographed on the mood screen itself so the picture carries both the
        // switcher and what it did. This is the shot that matters most in this milestone: the
        // palette reaches the controls through a generated stylesheet, and a light palette is
        // exactly where a bevel drawn the wrong way round or a caption that stopped contrasting
        // with its ground becomes visible. Neither shows up in any assertion.
        sideRail.select(Destination.MOODS);
        layoutAndCapture(scene, destination, "moods");
        state.setMood(Moods.LIGHT);
        layoutAndCapture(scene, destination, "moods-light");
        sideRail.select(Destination.LIBRARY);
        layoutAndCapture(scene, destination, "library-light");

        // Two presets whose overlay layers are the whole point of them, photographed over the
        // library rather than over the mood screen: a layer is a property of the window, and a
        // picture of it beside its own switcher would look like a swatch rather than like a look.
        // Sunset Wilds is the banded, dithered gradient - a smooth version of that ramp is the
        // fastest way to make this application look like it was built this decade - and Bowser
        // Castle is scanlines drawn ABOVE the interface, which is the band the 0.35 cap exists for.
        state.setMood(Moods.SUNSET_WILDS);
        layoutAndCapture(scene, destination, "mood-sunset");
        state.setMood(Moods.BOWSER_CASTLE);
        layoutAndCapture(scene, destination, "mood-bowser");
        // And the one whose artwork is a hand-drawn tile stored as palette indices, drifting.
        state.setMood(Moods.SKY_GARDEN);
        layoutAndCapture(scene, destination, "mood-sky");

        // The customizer's two panels. Each exists only once its button has been pressed, so the
        // gallery shot above says nothing whatsoever about either.
        state.setMood(Moods.DARK);
        sideRail.select(Destination.MOODS);
        showCustomizer();
        moodCustomizer.showPalette();
        layoutAndCapture(scene, destination, "mood-customizer");
        // The layer list, on a preset that has layers to list - the panel is otherwise a caption
        // saying there are none, which is a picture of the empty case rather than of the feature.
        state.setMood(Moods.SKY_GARDEN);
        moodCustomizer.showLayers();
        layoutAndCapture(scene, destination, "mood-layers");
        state.setMood(Moods.DARK);
        // Seeded with the Sky Garden clouds, because a picture of an empty grid is a picture of a
        // grid. This is also the check that a built-in's tile is a perfectly ordinary tile.
        moodCustomizer.showEditor(Moods.SKY_GARDEN.tile("clouds"));
        layoutAndCapture(scene, destination, "pixel-editor");

        state.setMood(Moods.DARK);
        sideRail.select(Destination.LIBRARY);

        // The library with the structure column folded away. This is the one picture that shows
        // what the fold is for - the table with the whole window, where a title that was ellipsized
        // in every other shot is written out - and it is the picture that catches the column
        // vanishing without handing its width on.
        toggleStructureColumn();
        layoutAndCapture(scene, destination, "dsa-folded");
        toggleStructureColumn();

        // The runner last, because it is the one view that only exists behind a key press: a shot
        // of the opening state proves nothing at all about it.
        toggleRace();
        // 150cc, and part-way into the track. Every course opens with a lead-in of its own travel
        // time, so a picture taken at the three seconds this test has actually played is a picture
        // of an empty road - and at 50cc the entities are two seconds apart by design, which is
        // correct and photographs as almost nothing.
        state.setSpeedClass(SpeedClass.CC150);
        layoutNow(scene);
        // Driven up to the moment rather than jumped to it, so the head-up display in the corner is
        // a real run's - coins, a combo part way up its meter, a rank - instead of the zeroed one a
        // seek leaves behind. See RunnerView.previewDrivenTo.
        runner.previewDrivenTo(screenshotMoment());
        writeScreenshot(scene, derivedPath(destination, "race"));
        state.setSpeedClass(SpeedClass.defaultClass());
        toggleRace();

        // The companion window last, and from its own scene: it is a separate window, so the shot
        // of the main one contains nothing of it. Drawn part way round the record rather than at
        // frame zero, so the picture shows the disk mid-turn with the kart on it.
        collapseToCompanion();
        // Part way into the track, because a progress line at zero is a picture of an empty line
        // and says nothing about whether it fills.
        engine.seek(engine.duration().dividedBy(3));
        miniPlayer.refresh();
        Scene companion = miniStage.getScene();
        layoutNow(companion);
        miniPlayer.previewAt(0.5);
        writeScreenshot(companion, derivedPath(destination, "mini"));

        // And the compact strip, which is a different view of the same window rather than a smaller
        // one - there is nothing of it in the shot above.
        miniPlayer.setCompact(true);
        miniStage.sizeToScene();
        layoutNow(companion);
        writeScreenshot(companion, derivedPath(destination, "mini-compact"));
        miniPlayer.setCompact(false);
        miniStage.sizeToScene();

        expandFromCompanion();

        // The Spotify add dialog, with a track picked so the form is in the shot. It is the largest new
        // surface in this change and it is a form in a fixed-width font, which is exactly where a
        // caption runs off the side while nothing anywhere reports it.
        sideRail.select(Destination.LIBRARY);
        layoutNow(scene);
        captureSpotifyDialog(destination);

        // Last of all, because it is the screen the application ends on. Photographed rather than
        // waited for, and with nothing torn down: the real one goes up when the user quits and the
        // window is gone a second or two later, so this is the only way to look at it - and running
        // the actual teardown here would close the sound card out from under every check above.
        captureShutdown(scene, destination);
    }

    /**
     * Photographs the library's Spotify add dialog with a track picked.
     *
     * <p>The results are made up here rather than searched for, and deliberately so: this is a layout
     * check, and a layout check that depended on a network round trip would be a layout check that
     * silently stopped running whenever the machine was offline or the credentials were unset. The
     * titles are long on purpose - the widest thing in this dialog is a result row, and it is the row
     * that decides how wide the window comes out.
     *
     * <p>The track carries no artist id, so no genre lookup is made and nothing here touches Spotify.
     *
     * @param destination the screenshot path the run was given
     */
    private void captureSpotifyDialog(String destination) {
        List<com.eia.superdwarfkart.spotify.SpotifyTrack> results = List.of(
                new com.eia.superdwarfkart.spotify.SpotifyTrack(
                        "spotify:track:1", "Crimewave", "Crystal Castles", null,
                        "Crystal Castles", java.time.Duration.ofSeconds(258), null, 2008),
                new com.eia.superdwarfkart.spotify.SpotifyTrack(
                        "spotify:track:2", "Crimewave (Crystal Castles vs. HEALTH)",
                        "Crystal Castles, HEALTH", null, "Crystal Castles",
                        java.time.Duration.ofSeconds(213), null, 2008),
                new com.eia.superdwarfkart.spotify.SpotifyTrack(
                        "spotify:track:3", "Untrust Us", "Crystal Castles", null,
                        "Crystal Castles", java.time.Duration.ofSeconds(191), null, 2008));

        com.eia.superdwarfkart.ui.SpotifySearchDialog.capture(mainStage, library, spotify, results,
                dialogScene -> writeScreenshot(dialogScene, derivedPath(destination, "spotify-add")));
    }

    /**
     * Photographs the shutdown screen without shutting anything down.
     *
     * <p>The sweep is asked for at a stated instant, exactly as the boot screen's glitch is: this
     * method holds the interface thread, so the screen's own frame loop never ticks and a live one
     * would be photographed at whatever the first frame happened to be. The caption is set by hand for
     * the same reason it is on the boot screen - the real one is written by the teardown thread as each
     * step starts, and none of that runs here.
     *
     * <p>The window is put back afterwards. A screenshot must not leave the application in a state it
     * cannot leave, and the checks above have not finished with it.
     *
     * @param scene       the scene to capture
     * @param destination the screenshot path the run was given
     */
    private void captureShutdown(Scene scene, String destination) {
        Node was = overlay.getContent();
        ShutdownScreen screen = new ShutdownScreen(assets);
        screen.setStatus("STOPPING GO-LIBRESPOT");
        shell.setTop(null);
        // The border comes off here too, or the photograph is of a screen nobody ever sees: the real
        // quit path drops it through updateWindowFrame, and three pixels of amber round a black screen
        // is exactly the thing this end of the application is not meant to have.
        shell.getStyleClass().add("no-frame");
        overlay.setContent(screen);
        layoutNow(scene);

        // One shot per moment, because no two of the three things on this screen are ever there at
        // once: the tear is over before the cartridge moves, and the name is on the screen or on the
        // cartridge's own label and never both - the boot screen's rule, run backwards. A single
        // picture would look like whichever two it missed had failed to draw. The instants come off
        // ShutdownScreen.Moment rather than being written down here, so they cannot drift from the
        // timings they are meant to be photographing.
        for (ShutdownScreen.Moment moment : ShutdownScreen.Moment.values()) {
            screen.previewAt(moment.instant());
            layoutNow(scene);
            writeScreenshot(scene, derivedPath(destination, moment.label()));
        }

        overlay.setContent(was);
        shell.setTop(header);
        // updateWindowFrame rather than removing the class by hand: the run may be inside a fullscreen
        // check when this is called, and putting the border back unconditionally would frame a window
        // that is filling the display.
        updateWindowFrame();
        layoutNow(scene);
    }

    /**
     * Forces a layout pass, then writes a snapshot next to the given base path.
     *
     * <p>The pulse matters: a view that was just swapped in has no size until the scene lays it
     * out, and snapshotting first would capture an empty canvas.
     *
     * @param scene       the scene to capture
     * @param basePath    the screenshot path the run was given
     * @param suffix      appended to the base file name
     */
    private void layoutAndCapture(Scene scene, String basePath, String suffix) {
        layoutNow(scene);
        // Jump any move to its end state: the settled view - kart parked, traversal fully lit -
        // is the one worth a picture, and catching one a few milliseconds in shows nothing.
        if (visualizer.view() != null) {
            visualizer.view().settle();
        }

        writeScreenshot(scene, derivedPath(basePath, suffix));
    }

    /**
     * Names a screenshot beside the one the run was given: {@code shot.png} plus {@code race}
     * becomes {@code shot-race.png}.
     *
     * @param basePath the screenshot path the run was given
     * @param suffix   appended to the base file name
     * @return where to write it
     */
    private static java.nio.file.Path derivedPath(String basePath, String suffix) {
        java.nio.file.Path base = java.nio.file.Path.of(basePath);
        String name = base.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String derived = dot < 0
                ? name + "-" + suffix
                : name.substring(0, dot) + "-" + suffix + name.substring(dot);
        return base.resolveSibling(derived);
    }

    /**
     * Delivers a synthetic key press to the scene, for the smoke test.
     *
     * @param scene the scene to deliver to
     * @param code  the key to press
     */
    private static void fireKey(Scene scene, KeyCode code) {
        javafx.event.Event.fireEvent(scene, new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /**
     * Renders a measurement for the smoke test log.
     *
     * @param measurement the measurement, or {@code null} if the operation never ran
     * @return a one-line description
     */
    private static String describe(com.eia.superdwarfkart.ui.visualizer.Measurement measurement) {
        return measurement == null
                ? "- not run (empty library) -"
                : measurement.steps() + " steps over " + measurement.structure()
                        + " (n = " + measurement.n() + ")";
    }

    /**
     * Writes a snapshot of the scene when {@value #SCREENSHOT_PROPERTY} names a destination.
     *
     * <p>Failures here are reported but never propagated: a screenshot is a diagnostic, and
     * failing to take one must not change how the application behaves.
     *
     * @param scene the scene to capture
     */
    private void writeScreenshotIfRequested(Scene scene) {
        String destination = System.getProperty(SCREENSHOT_PROPERTY);
        if (destination == null || destination.isBlank()) {
            return;
        }
        writeScreenshot(scene, java.nio.file.Path.of(destination));
    }

    /**
     * Writes a snapshot of the scene to a file.
     *
     * @param scene the scene to capture
     * @param file  where to write the PNG
     */
    private void writeScreenshot(Scene scene, java.nio.file.Path file) {
        try {
            var image = scene.snapshot(null);
            if (file.getParent() != null) {
                java.nio.file.Files.createDirectories(file.getParent());
            }
            javax.imageio.ImageIO.write(
                    javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file.toFile());
            System.out.println("[smoke] screenshot        : " + file);
        } catch (Exception e) {
            System.out.println("[smoke] screenshot failed : " + e);
        }
    }

    /**
     * Starts a visible shutdown: puts a screen up, then tears down off the interface thread.
     *
     * <p><strong>This exists because {@code stop()} on its own looked exactly like a crash.</strong>
     * JavaFX runs {@code Application.stop()} on the interface thread <em>after</em> the last window is
     * hidden, and the slowest thing in it is by far the go-librespot child: it is asked to exit
     * politely and then given a five second grace period before it is killed. So closing the
     * application made the window vanish and then left the process sitting in the dock, unresponsive,
     * for up to five seconds - on macOS long enough to earn a spinning cursor. Nothing was wrong and
     * there was no way at all to tell that from outside.
     *
     * <p>So the order is inverted. The window <em>stays up</em>, shows {@link ShutdownScreen}, and the
     * teardown runs on a thread of its own; the interface thread is free the whole time, so the window
     * still paints, still moves and still reports which step it is on. {@link Platform#exit()} is
     * called at the end, which then reaches {@code stop()} and finds both halves already done.
     *
     * <p><strong>What makes the background half safe is ground rule 3, not luck.</strong> Everything
     * {@link #releaseResources} touches lives in {@code playback/}, {@code audio/}, {@code analysis/}
     * and {@code spotify/}, none of which may import {@code javafx} - so none of it can reach the
     * scene graph however it is called. The parts that <em>are</em> the scene graph, which is every
     * {@code AnimationTimer} plus filing the run in progress, stay on the interface thread in
     * {@link #stopDrawing()} and happen before the screen goes up.
     *
     * <p>There is deliberately no way to cancel. By the time this is on screen the audio line is
     * closing, and a shutdown that could be called off would be a state every view behind it would
     * have to know about.
     */
    private void requestQuit() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        // Whatever else happens, neither of the boot's noises is still playing over a shutdown screen.
        bootFanfare.stop();
        cartridgeIn.stop();

        // Something has to be on screen to draw this on. The companion strip may be the only window
        // showing and it is 224 pixels wide, so the main one comes back for it - shown before the
        // companion is hidden, because JavaFX exits when the last window goes and that would close
        // the application at exactly the moment it is trying to say it is closing.
        if (mainStage != null && !mainStage.isShowing()) {
            mainStage.show();
        }
        if (miniStage != null && miniStage.isShowing()) {
            miniPlayer.stop();
            miniStage.hide();
        }
        if (fullscreenRace) {
            exitFullscreenRace();
        }

        stopDrawing();

        // No title bar: there is nothing left to minimise or maximise, and a close button on a
        // screen that is already closing can only be a second press that does harm. No layers
        // either - this screen and the boot screen are the machine rather than the mood, and an
        // ABOVE_CONTENT scanline layer would be the one thing still drawn in somebody's palette.
        shell.setTop(null);
        // And the border with it, exactly as the boot screen drops it. See updateWindowFrame.
        updateWindowFrame();
        if (overlay != null) {
            overlay.setMood(null, null);
        }
        shutdownScreen = new ShutdownScreen(assets);
        overlay.setContent(shutdownScreen);

        // The machine letting go of the cartridge. Started here rather than through a callback the
        // way the boot fanfare is, because there the sound fires part way through a sequence the
        // screen owns and here the two begin at the same instant in the same method.
        cartridgeOut.play();

        long began = System.nanoTime();

        // **Exit waits for both halves, and the animation is usually the slower one.** The teardown
        // was measured at 8 ms with no daemon running, so on the ordinary path it finishes long
        // before the eject does - and exiting on it alone would put a three second animation on
        // screen and then close the window a frame into it, which is the frozen dock this screen
        // replaced with an extra class in front of it. With a daemon running the teardown takes up to
        // five seconds and the animation is free. Both flags are only ever touched on the interface
        // thread, so no synchronisation is needed for them.
        boolean[] done = {false, false};
        Runnable exitWhenBothAreDone = () -> {
            if (!done[0] || !done[1]) {
                return;
            }
            if (shutdownScreen != null) {
                shutdownScreen.stop();
            }
            // The whole justification for this screen, in one number: however long this says, the
            // window was painting and answering the whole time rather than sitting frozen.
            LOG.info(String.format("Shut down in %.0f ms", (System.nanoTime() - began) / 1e6));
            Platform.exit();
        };

        // The eject sound is 7.71 s against a three second animation, so it is always still playing
        // at the end. Faded at the blackout rather than at the exit, which leaves SoundEffect's own
        // quarter-second fade room to finish before the application actually goes - a line cut off
        // mid-block leaves the cone off zero, and that step is an audible tick.
        shutdownScreen.setOnFading(cartridgeOut::stop);
        shutdownScreen.setOnFinished(() -> {
            done[1] = true;
            exitWhenBothAreDone.run();
        });
        shutdownScreen.start();

        Thread teardown = new Thread(() -> {
            releaseResources(step -> Platform.runLater(() -> {
                if (shutdownScreen != null) {
                    shutdownScreen.setStatus(step);
                }
            }));
            Platform.runLater(() -> {
                done[0] = true;
                exitWhenBothAreDone.run();
            });
        }, "sdmk-shutdown");
        // Daemon, so a teardown that somehow wedged could never be the thing keeping the JVM alive -
        // which would turn a five second wait into a process that never exits at all.
        teardown.setDaemon(true);
        teardown.start();
    }

    /**
     * Stops every frame loop and files the run in progress.
     *
     * <p>Interface thread only: an {@code AnimationTimer} may only be stopped from it, and
     * {@code runner.stop()} writes the score board. Guarded, because both {@link #requestQuit()} and
     * {@link #stop()} reach here and on the ordinary path both of them run.
     */
    private void stopDrawing() {
        if (stoppedDrawing) {
            return;
        }
        stoppedDrawing = true;
        if (meters != null) {
            meters.stop();
        }
        // Stops the frame loop and files whatever the run in progress achieved. A run abandoned by
        // closing the window is still a run, and the board only takes it if it beat what was there.
        if (runner != null) {
            runner.stop();
        }
        if (miniPlayer != null) {
            miniPlayer.stop();
        }
        if (beatmapTimeline != null) {
            beatmapTimeline.stop();
        }
        if (playbackBar != null) {
            playbackBar.stopClock();
        }
        if (visualizer != null) {
            visualizer.stop();
        }
        if (overlay != null) {
            overlay.stop();
        }
    }

    /**
     * Hands back everything that outlives the window, reporting each step as it starts.
     *
     * <p>Safe on any thread, and that is a property of the layering rather than of this method: not
     * one of these classes may import {@code javafx} (ground rule 3, enforced by
     * {@code LayeringTest}), so none of them can touch the scene graph whichever thread calls them.
     *
     * <p>The playback thread is a daemon and the frame timers stop with the toolkit, so nothing here
     * keeps the process alive on its own - but the sound card is a shared system resource and is
     * handed back explicitly, and the go-librespot child is a whole separate process.
     *
     * @param report given a short caption for each step, in progress order; it lands on the interface
     *               thread by the caller's arrangement, not this method's
     */
    private void releaseResources(java.util.function.Consumer<String> report) {
        if (releasedResources) {
            return;
        }
        releasedResources = true;

        report.accept("CLOSING THE AUDIO OUTPUT");
        if (engine != null) {
            engine.close();
        }

        report.accept("STOPPING THE ANALYSER");
        if (beatmaps != null) {
            beatmaps.close();
        }
        if (courseIndex != null) {
            courseIndex.close();
        }

        // Last, and by a wide margin the slowest: this kills the go-librespot child, politely and
        // then not. An orphaned daemon holds a Spotify session and keeps the API port bound, so the
        // next launch finds the port taken and Spotify silently does not work with nothing on screen
        // saying why. There is a shutdown hook behind this for the paths that never reach here.
        report.accept("STOPPING GO-LIBRESPOT");
        if (spotifyView != null) {
            spotifyView.shutdown();
        }
        if (spotify != null) {
            spotify.close();
        }

        report.accept("GOODBYE");
    }

    /**
     * Releases everything that outlives the window.
     *
     * <p>Called by JavaFX on every exit path, including the ones that never went through
     * {@link #requestQuit()} - a smoke test's own {@code Platform.exit()}, or the platform closing the
     * application from outside. Both halves are guarded, so on the ordinary path this finds the work
     * already done and returns.
     */
    @Override
    public void stop() {
        stopDrawing();
        releaseResources(step -> { });
    }

    /**
     * Entry point.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
