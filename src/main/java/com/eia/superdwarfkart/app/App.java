package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.persistence.LibraryRepository;
import com.eia.superdwarfkart.persistence.PersistenceException;
import com.eia.superdwarfkart.persistence.Repository;
import com.eia.superdwarfkart.ui.Fonts;
import com.eia.superdwarfkart.ui.LibraryView;
import com.eia.superdwarfkart.ui.PixelDialog;
import com.eia.superdwarfkart.ui.Theme;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
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

    private Library library;
    private Repository<Song> libraryRepository;

    @Override
    public void start(Stage stage) {
        boolean pixelFont = Fonts.load();

        libraryRepository = new LibraryRepository();
        library = loadLibrary(libraryRepository);

        LibraryView libraryView = new LibraryView(library, libraryRepository);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(libraryView);

        Scene scene = new Scene(root, AppConfig.MAIN_WIDTH, AppConfig.MAIN_HEIGHT);
        Theme.apply(scene);

        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        stage.show();

        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            runSmokeTest(stage, scene, pixelFont);
        }
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

        boolean ok = stage.isShowing()
                && AppConfig.APP_NAME.equals(stage.getTitle())
                && !scene.getStylesheets().isEmpty()
                && pixelFont
                && libraryViewPresent
                && tablePresent;
        System.out.println("[smoke] RESULT            : " + (ok ? "PASS" : "FAIL"));

        PauseTransition close = new PauseTransition(Duration.seconds(2));
        close.setOnFinished(e -> {
            writeScreenshotIfRequested(scene);
            Platform.exit();
        });
        close.play();
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
        try {
            var image = scene.snapshot(null);
            var file = java.nio.file.Path.of(destination);
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
