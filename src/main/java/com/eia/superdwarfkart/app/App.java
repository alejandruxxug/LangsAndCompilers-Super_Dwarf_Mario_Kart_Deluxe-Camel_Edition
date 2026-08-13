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
import com.eia.superdwarfkart.game.Course;
import com.eia.superdwarfkart.game.Entity;
import com.eia.superdwarfkart.game.Lane;
import com.eia.superdwarfkart.game.Obstacle;
import com.eia.superdwarfkart.game.RunnerGame;
import com.eia.superdwarfkart.game.SpeedClass;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.persistence.LibraryRepository;
import com.eia.superdwarfkart.persistence.PersistenceException;
import com.eia.superdwarfkart.persistence.Repository;
import com.eia.superdwarfkart.persistence.ScoreRepository;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import com.eia.superdwarfkart.playback.PlaybackListener;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import com.eia.superdwarfkart.ui.BeatmapTimeline;
import com.eia.superdwarfkart.ui.ComplexityPanel;
import com.eia.superdwarfkart.ui.Fonts;
import com.eia.superdwarfkart.ui.LevelMeterView;
import com.eia.superdwarfkart.ui.LibraryView;
import com.eia.superdwarfkart.ui.PixelDialog;
import com.eia.superdwarfkart.ui.PlaybackBar;
import com.eia.superdwarfkart.ui.RunnerView;
import com.eia.superdwarfkart.ui.Theme;
import com.eia.superdwarfkart.ui.visualizer.OperationCounter;
import com.eia.superdwarfkart.ui.visualizer.PresentationView;
import com.eia.superdwarfkart.ui.visualizer.StructureVisualizer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.stage.Stage;
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
    private BeatmapIndex courseIndex;
    private RunnerView runner;
    private LibraryView libraryView;
    private Button viewToggle;
    private boolean racing;

    /** Set when the user chose the library by hand, so pressing play does not overrule them. */
    private boolean libraryPinned;

    private BorderPane root;
    private PlaybackBar playbackBar;
    private VBox sideColumn;
    private StructureVisualizer visualizer;
    private PresentationView presentation;
    private boolean presenting;

    @Override
    public void start(Stage stage) {
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

        // The tap is composed in here rather than owned by the audio source, so the same meters
        // work unchanged over any implementation of AudioSource.
        levels = new Levels();
        audio = new LocalFileAudioSource();
        audio.addPcmListener(new LevelAnalyzer(levels));
        // End of track arrives on the playback thread; runLater is the hop back, and it happens
        // once per song rather than once per audio block.
        engine = new PlaybackEngine(player, audio, Platform::runLater);

        // Analysis follows the running order rather than the play button: whatever becomes current
        // gets a beatmap prepared for it on a background thread, so by the time the rhythm game
        // wants a course it is already there. Reading the file for analysis is a separate decode
        // and never touches the one being played.
        beatmaps = new BeatmapService();
        player.addListener((mode, current) -> beatmaps.request(fileOf(current)));
        beatmaps.request(fileOf(player.current()));

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
                    courseIndex.recheck(previous.getFilePath());
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

        root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(new VBox(buildHeader(), playbackBar, beatmapTimeline));
        root.setLeft(sideColumn);
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
            if (event.getCode() == PRESENTATION_KEY) {
                togglePresentation(scene);
                event.consume();
            } else if (event.getCode() == RACE_KEY) {
                toggleRace();
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
     * @return the file it plays from, or {@code null}
     */
    private static java.nio.file.Path fileOf(Song song) {
        return song == null ? null : song.getFilePath();
    }

    /**
     * @param scene the scene to inspect
     * @return whether the focus is in something the user is typing into
     */
    private static boolean typing(Scene scene) {
        return scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl;
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
            // Back above the complexity panel, where it came from.
            sideColumn.getChildren().add(0, visualizer);
            scene.setRoot(root);
            presenting = false;
            return;
        }
        // Detach from the main layout first: a node cannot sit in two places at once.
        sideColumn.getChildren().remove(visualizer);
        presentation.attach(visualizer);
        scene.setRoot(presentation);
        presenting = true;
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

        viewToggle = new Button();
        viewToggle.getStyleClass().add("view-toggle");
        viewToggle.setTooltip(new Tooltip("Swap the library for the rhythm game\nF6"));
        viewToggle.setOnAction(event -> toggleRace());
        updateViewToggle();

        HBox header = new HBox(14, name, spacer, viewToggle, version);
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
            root.setCenter(libraryView);
            // A run that ended while the road was on screen may have changed the board.
            libraryView.refreshBadges();
        }
        // Leaving the road by hand is a decision, and pressing play afterwards must not overrule
        // it. Coming back to the road withdraws it.
        libraryPinned = !racing;
        updateViewToggle();
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

        reportAudio();
        reportBeatmap();
        reportCourse();
        reportRunner(scene);
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

        boolean ok = stage.isShowing()
                && AppConfig.APP_NAME.equals(stage.getTitle())
                && !scene.getStylesheets().isEmpty()
                && pixelFont
                && libraryViewPresent
                && tablePresent
                && visualizer.view() != null
                && visualizer.view().modeId() == player.mode().id()
                && arrowWorks
                && tabWorks;
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
        scene.getRoot().applyCss();
        scene.getRoot().layout();
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

        // The runner last, because it is the one view that only exists behind a key press: a shot
        // of the opening state proves nothing at all about it.
        toggleRace();
        // 150cc, and part-way into the track. Every course opens with a lead-in of its own travel
        // time, so a picture taken at the three seconds this test has actually played is a picture
        // of an empty road - and at 50cc the entities are two seconds apart by design, which is
        // correct and photographs as almost nothing.
        state.setSpeedClass(SpeedClass.CC150);
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        runner.previewAt(screenshotMoment());
        writeScreenshot(scene, derivedPath(destination, "race"));
        state.setSpeedClass(SpeedClass.defaultClass());
        toggleRace();
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
        scene.getRoot().applyCss();
        scene.getRoot().layout();
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
        if (visualizer != null && visualizer.view() != null) {
            visualizer.view().stop();
        }
        if (engine != null) {
            engine.close();
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
