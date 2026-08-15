package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.analysis.Beatmap;
import com.eia.superdwarfkart.analysis.BeatmapIndex;
import com.eia.superdwarfkart.analysis.BeatmapService;
import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
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
import com.eia.superdwarfkart.game.SpeedClass;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.PlayHistory;
import com.eia.superdwarfkart.model.Racer;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.persistence.LibraryRepository;
import com.eia.superdwarfkart.persistence.PersistenceException;
import com.eia.superdwarfkart.persistence.Repository;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.persistence.ScoreRepository;
import com.eia.superdwarfkart.persistence.SettingsRepository;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import com.eia.superdwarfkart.spotify.SpotifyBinary;
import com.eia.superdwarfkart.spotify.SpotifyConfig;
import com.eia.superdwarfkart.spotify.SpotifySession;
import com.eia.superdwarfkart.playback.PlaybackListener;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import com.eia.superdwarfkart.ui.BeatmapTimeline;
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
import com.eia.superdwarfkart.ui.SettingsView;
import com.eia.superdwarfkart.ui.SideRail;
import com.eia.superdwarfkart.ui.Theme;
import com.eia.superdwarfkart.ui.visualizer.OperationCounter;
import com.eia.superdwarfkart.ui.visualizer.PresentationView;
import com.eia.superdwarfkart.ui.visualizer.StructureVisualizer;
import javafx.animation.PauseTransition;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

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
     * Where into the track the runner is drawn for its screenshot, in seconds.
     *
     * <p>Far enough in that a track which opens quietly has got going.
     */
    private static final double SCREENSHOT_COURSE_SECONDS = 45;

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
    private SettingsView settingsView;
    private SpotifyView spotifyView;
    private SpotifySession spotify;
    private PlayHistory history;
    private Button viewToggle;
    private boolean racing;

    /** Set when the user chose the library by hand, so pressing play does not overrule them. */
    private boolean libraryPinned;

    private BorderPane root;
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
        engine.setOnPlayCounted(history::record);
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
        moodSelect = new MoodSelectView(state);
        settingsView = new SettingsView();

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
        root.setTop(new VBox(buildHeader(), playbackBar, beatmapTimeline));
        root.setLeft(new HBox(sideRail, sideColumn));
        root.setCenter(libraryView);
        root.setRight(meters);

        Scene scene = new Scene(root, AppConfig.MAIN_WIDTH, AppConfig.MAIN_HEIGHT);
        Theme.apply(scene);
        installShortcuts(scene);

        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        stage.show();

        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            runSmokeTest(stage, scene, pixelFont);
            return;
        }
        // After the window is on screen, so the dialog has something to centre on and the user can
        // see what they are being asked about.
        Platform.runLater(() -> offerToStart(stage));
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
        Moods.byId(settings.moodId()).ifPresent(state::setMood);
        Racer.byName(settings.racerId()).ifPresent(state::setRacer);
        SpeedClass.byName(settings.speedClass()).ifPresent(state::setSpeedClass);

        applyMood(state.getMood());
        state.moodProperty().addListener((observable, was, now) -> {
            applyMood(now);
            settings.setMoodId(now.id());
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
     * Asks whether to start playing, once, on the first launch of a session.
     *
     * <p>Nothing plays until somebody says so. Opening a music player and having it start making
     * noise at whatever the shuffle picked is the behaviour every one of them is disliked for, and
     * here it would also drop the user straight into a race they had not asked to drive.
     *
     * <p>Accepting starts the music, which brings the road up on its own - see
     * {@link RunnerView#setOnRaceStarted}. Declining leaves the library on screen, paused, with
     * everything still reachable.
     *
     * @param stage the window to centre the question on
     */
    private void offerToStart(Stage stage) {
        Song song = player.current();
        if (song == null) {
            return;
        }
        boolean start = PixelDialog.confirm(stage, "START YOUR ENGINES",
                song.getTitle() + "\nby " + song.getArtist()
                        + "\n\nPlay this and drive its course?"
                        + "\n\nF6 swaps the road for the library at any time.");
        if (start) {
            engine.play();
            playbackBar.refresh();
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
            if (event.getCode() == STRUCTURE_KEY) {
                toggleStructureColumn();
                event.consume();
            } else if (event.getCode() == PRESENTATION_KEY) {
                togglePresentation(scene);
                event.consume();
            } else if (event.getCode() == RACE_KEY) {
                toggleRace();
                event.consume();
            } else if (event.getCode() == MINI_KEY) {
                collapseToCompanion();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && presenting) {
                togglePresentation(scene);
                event.consume();
            } else if (event.getCode() == KeyCode.TAB && !typing(scene)) {
                playbackBar.cycleMode();
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
     * @param scene the scene whose root is swapped
     */
    private void togglePresentation(Scene scene) {
        if (presenting) {
            presentation.detach();
            // Back above the complexity panel, where it came from - even when the column is folded
            // away, so that unfolding finds it there rather than empty.
            sideColumn.getChildren().add(0, visualizer);
            scene.setRoot(root);
            presenting = false;
            updateVisualizerDrawing();
            return;
        }
        // Detach from the main layout first: a node cannot sit in two places at once.
        sideColumn.getChildren().remove(visualizer);
        presentation.attach(visualizer);
        scene.setRoot(presentation);
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
     * @return the header node
     */
    private HBox buildHeader() {
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

        HBox header = new HBox(14, name, spacer, structureToggle, miniToggle, viewToggle, version);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 14, 16));
        header.getStyleClass().add("app-header");
        return header;
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
        miniPlayer.setOnQuit(Platform::exit);

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
    private void suspendMainViews() {
        meters.stop();
        beatmapTimeline.stop();
        playbackBar.stopClock();
        visualizer.stop();
        runnerWasRunning = runner.isRunning();
        if (runnerWasRunning) {
            runner.stop();
        }
    }

    /** Picks all of it back up, and brings the bar in step with whatever happened while it was away. */
    private void resumeMainViews() {
        meters.start();
        beatmapTimeline.start();
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
        boolean libraryViewPresent = scene.getRoot().lookup(".library-view") != null;
        boolean tablePresent = scene.getRoot().lookup(".table-view") != null;

        System.out.println("[smoke] window shown      : " + stage.isShowing());
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

        boolean foldOk = reportStructureFold(scene);

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

        boolean ok = stage.isShowing()
                && AppConfig.APP_NAME.equals(stage.getTitle())
                && !scene.getStylesheets().isEmpty()
                && pixelFont
                && libraryViewPresent
                && tablePresent
                && visualizer.view() != null
                && visualizer.view().modeId() == player.mode().id()
                && arrowWorks
                && tabWorks
                && foldOk
                && companionOk
                && spotifyOk;
        System.out.println("[smoke] RESULT            : " + (ok ? "PASS" : "FAIL"));

        PauseTransition close = new PauseTransition(Duration.seconds(2));
        close.setOnFinished(e -> {
            // The base shot first, while audio is still flowing, so the meters in it are real.
            writeScreenshotIfRequested(scene);
            engine.pause();
            captureEveryView(scene);
            Platform.exit();
        });
        close.play();
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
        List<com.eia.superdwarfkart.spotify.SpotifyTrack> found =
                spotify.searchTracks("the beatles", 50);
        String problem = spotify.searchProblem();
        System.out.println("[smoke] spotify search    : " + found.size() + " results"
                + (found.isEmpty() ? "  PROBLEM: " + problem : "  e.g. " + found.get(0)));
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
        fireKey(scene, KeyCode.LEFT);
        fireKey(scene, KeyCode.LEFT);
        System.out.println("[smoke] steering          : " + before + " -> " + game.lane()
                + (game.lane() == before ? "  DID NOTHING" : "  ok"));

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

    /** How far ahead the scripted driver treats a bump as a reason to be elsewhere, in seconds. */
    private static final double DANGER_HORIZON_SECONDS = 0.2;

    /** How far ahead the scripted driver looks for something to collect, in seconds. */
    private static final double AIM_HORIZON_SECONDS = 0.6;

    /**
     * Drives a course from start to finish with a scripted driver, at sixty frames a second.
     *
     * <p>The driver is greedy and short-sighted: get out of any lane with a bump about to arrive in
     * it, and otherwise sit in the lane of the next thing worth collecting. That is deliberately
     * not an optimal player - it loses coins whenever a bump and a coin want opposite lanes at the
     * same moment - but it is enough to make the line mean something. A course that a competent
     * driver <em>cannot</em> get a good rank on is a generated course the rules cannot survive: two
     * bumps too close together to dodge, or an entity placed where the resolution window can never
     * reach it. That is what this catches, over four minutes of real beatmap, on every launch.
     *
     * @param course the course to drive
     * @return the rank the scripted driver earned
     */
    private static String driveScriptedLap(Course course) {
        if (course.isEmpty()) {
            return "- empty -";
        }
        RunnerGame lap = new RunnerGame(course);
        double step = 1 / 60d;
        double end = course.entityAt(course.size() - 1).beatTime() + 1;

        for (double at = 0; at <= end; at += step) {
            boolean[] dangerous = new boolean[Lane.COUNT];
            int aim = -1;
            for (int index = course.firstEntityAtOrAfter(at);
                    index < course.size()
                            && course.entityAt(index).beatTime() <= at + AIM_HORIZON_SECONDS;
                    index++) {
                Entity entity = course.entityAt(index);
                if (entity instanceof Obstacle) {
                    if (entity.beatTime() <= at + DANGER_HORIZON_SECONDS) {
                        dangerous[entity.lane().index()] = true;
                    }
                } else if (aim < 0) {
                    aim = entity.lane().index();
                }
            }

            if (aim >= 0 && !dangerous[aim]) {
                steerTowards(lap, Lane.ofIndex(aim));
            }
            if (dangerous[lap.lane().index()]) {
                int safe = firstSafeLane(dangerous);
                if (safe >= 0) {
                    steerTowards(lap, Lane.ofIndex(safe));
                } else {
                    // Every lane is blocked. That is what the jump is for.
                    lap.jump();
                }
            }
            lap.update(at);
        }
        return lap.score().rank() + " " + lap.score().coinsCollected() + "/"
                + course.coinsAvailable();
    }

    /**
     * @param dangerous which lanes have a bump arriving
     * @return the index of the first lane that does not, or {@code -1} when they all do
     */
    private static int firstSafeLane(boolean[] dangerous) {
        for (int index = 0; index < dangerous.length; index++) {
            if (!dangerous[index]) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Moves the scripted driver one lane towards a target.
     *
     * @param lap    the run being driven
     * @param target the lane to head for
     */
    private static void steerTowards(RunnerGame lap, Lane target) {
        if (lap.lane().index() < target.index()) {
            lap.moveRight();
        } else if (lap.lane().index() > target.index()) {
            lap.moveLeft();
        }
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

        togglePresentation(scene);
        layoutAndCapture(scene, destination, "presentation");
        togglePresentation(scene);

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
        runner.previewAt(screenshotMoment());
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
     * Releases everything that outlives the window.
     *
     * <p>The playback thread is a daemon and the frame timers stop with the toolkit, so nothing
     * here keeps the process alive on its own - but the sound card is a shared system resource and
     * is handed back explicitly. Called by JavaFX on every ordinary exit path.
     */
    @Override
    public void stop() {
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
        if (beatmaps != null) {
            beatmaps.close();
        }
        if (courseIndex != null) {
            courseIndex.close();
        }
        if (playbackBar != null) {
            playbackBar.stopClock();
        }
        if (visualizer != null) {
            visualizer.stop();
        }
        if (engine != null) {
            engine.close();
        }
        if (spotifyView != null) {
            spotifyView.shutdown();
        }
        // Last, and it matters that it happens at all: this kills the go-librespot child. An
        // orphaned daemon holds a Spotify session and keeps the API port bound, so the next launch
        // finds the port taken and Spotify silently does not work, with nothing on screen saying
        // why. There is a shutdown hook behind this for the paths that never reach here.
        if (spotify != null) {
            spotify.close();
        }
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
