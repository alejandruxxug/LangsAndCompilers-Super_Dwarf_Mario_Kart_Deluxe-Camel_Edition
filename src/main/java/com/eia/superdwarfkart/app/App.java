package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.assets.AssetKind;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.persistence.LibraryRepository;
import com.eia.superdwarfkart.persistence.PersistenceException;
import com.eia.superdwarfkart.persistence.Repository;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import com.eia.superdwarfkart.ui.ComplexityPanel;
import com.eia.superdwarfkart.ui.Fonts;
import com.eia.superdwarfkart.ui.LibraryView;
import com.eia.superdwarfkart.ui.PixelDialog;
import com.eia.superdwarfkart.ui.PlaybackBar;
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
import javafx.scene.control.Label;
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

    /** Function key that hands the whole stage to the visualizer. */
    private static final KeyCode PRESENTATION_KEY = KeyCode.F5;

    private Library library;
    private Repository<Song> libraryRepository;
    private AssetRegistry assets;
    private Player player;
    private AppState state;
    private OperationCounter counter;

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

        LibraryView libraryView = new LibraryView(library, libraryRepository);
        libraryView.setOnSongActivated(song -> player.select(song));

        playbackBar = new PlaybackBar(player, counter);
        ComplexityPanel complexityPanel = new ComplexityPanel(player, counter);

        visualizer = new StructureVisualizer(player, state, assets);
        presentation = new PresentationView(counter);

        complexityPanel.setPrefHeight(COMPLEXITY_HEIGHT);
        complexityPanel.setMinHeight(COMPLEXITY_HEIGHT);
        sideColumn = new VBox(visualizer, complexityPanel);
        sideColumn.setMinWidth(SIDE_COLUMN_WIDTH);
        sideColumn.setPrefWidth(SIDE_COLUMN_WIDTH);
        sideColumn.setMaxWidth(SIDE_COLUMN_WIDTH);
        VBox.setVgrow(visualizer, Priority.ALWAYS);

        root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(new VBox(buildHeader(), playbackBar));
        root.setLeft(sideColumn);
        root.setCenter(libraryView);

        Scene scene = new Scene(root, AppConfig.MAIN_WIDTH, AppConfig.MAIN_HEIGHT);
        Theme.apply(scene);
        installShortcuts(scene);

        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        stage.show();

        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            runSmokeTest(stage, scene, pixelFont);
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
     *       the search box to move the caret, and the tree view to step through a traversal; all
     *       three consume the event first, so the transport never steals a key out from under a
     *       control that was using it.</li>
     * </ul>
     *
     * @param scene the scene to listen on
     */
    private void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == PRESENTATION_KEY) {
                togglePresentation(scene);
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
                default -> {
                    // Not a transport key; leave it alone.
                }
            }
        });
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

        HBox header = new HBox(14, name, spacer, version);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 14, 16));
        header.getStyleClass().add("app-header");
        return header;
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
            writeScreenshotIfRequested(scene);
            captureEveryView(scene);
            Platform.exit();
        });
        close.play();
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

        java.nio.file.Path base = java.nio.file.Path.of(basePath);
        String name = base.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String derived = dot < 0
                ? name + "-" + suffix
                : name.substring(0, dot) + "-" + suffix + name.substring(dot);
        writeScreenshot(scene, base.resolveSibling(derived));
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
     * Entry point.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
