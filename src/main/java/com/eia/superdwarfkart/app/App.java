package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.ui.Fonts;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * JavaFX bootstrap.
 *
 * <p>At milestone M0 this opens the styled shell and proves the build: the bundled 8-bit font
 * loads, the stylesheet applies, and the window carries the full application name. Later
 * milestones replace the placeholder body with the real title screen and views.
 */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    /**
     * When set to {@code true}, the application starts, reports what it verified on stdout and
     * closes itself, so that a launch can be checked without leaving a window on screen.
     */
    private static final String SMOKE_TEST_PROPERTY = "sdmk.smokeTest";

    @Override
    public void start(Stage stage) {
        boolean pixelFont = Fonts.load();

        Scene scene = new Scene(buildRoot(pixelFont), AppConfig.MAIN_WIDTH, AppConfig.MAIN_HEIGHT);
        applyStylesheet(scene);

        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        stage.show();

        if (Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            runSmokeTest(stage, scene, pixelFont);
        }
    }

    /**
     * Builds the M0 placeholder content: the application name in the 8-bit font over a short
     * environment report.
     *
     * @param pixelFont whether the bundled font loaded, which the report echoes back
     * @return the scene root
     */
    private StackPane buildRoot(boolean pixelFont) {
        // The full name is the joke and gets the screen real estate, but it is 44 characters
        // wide, so it is split across lines rather than allowed to overflow.
        Text line1 = new Text("Super_Dwarf_Mario_Kart");
        Text line2 = new Text("_Deluxe-Camel_Edition");
        line1.getStyleClass().add("title-text");
        line2.getStyleClass().add("title-text");
        line1.setTextAlignment(TextAlignment.CENTER);
        line2.setTextAlignment(TextAlignment.CENTER);

        Label subtitle = new Label("MILESTONE M0 - BUILD SKELETON");
        subtitle.getStyleClass().add("subtitle");

        Label report = new Label(String.join("\n",
                "Java        " + System.getProperty("java.version"),
                "JavaFX      " + System.getProperty("javafx.runtime.version", "unknown"),
                "8-bit font  " + (pixelFont ? "loaded" : "MISSING (using fallback)"),
                "App home    " + AppConfig.appHome()));
        report.getStyleClass().add("report");

        VBox box = new VBox(18, line1, line2, subtitle, report);
        box.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(box);
        root.getStyleClass().add("root-pane");
        return root;
    }

    /**
     * Applies the bundled stylesheet, logging a warning and continuing unstyled if it is absent.
     *
     * @param scene the scene to style
     */
    private void applyStylesheet(Scene scene) {
        var url = App.class.getResource(AppConfig.STYLESHEET_RESOURCE);
        if (url == null) {
            LOG.warning("Stylesheet not found at " + AppConfig.STYLESHEET_RESOURCE + " - running unstyled");
            return;
        }
        scene.getStylesheets().add(url.toExternalForm());
    }

    /**
     * Prints what the launch actually proved and closes the window shortly afterwards.
     *
     * @param stage     the shown stage
     * @param scene     the scene under test
     * @param pixelFont whether the bundled font loaded
     */
    private void runSmokeTest(Stage stage, Scene scene, boolean pixelFont) {
        System.out.println("[smoke] window shown      : " + stage.isShowing());
        System.out.println("[smoke] window title      : " + stage.getTitle());
        System.out.println("[smoke] title matches     : " + AppConfig.APP_NAME.equals(stage.getTitle()));
        System.out.println("[smoke] stylesheets       : " + scene.getStylesheets().size());
        System.out.println("[smoke] 8-bit font loaded : " + pixelFont);
        System.out.println("[smoke] javafx runtime    : " + System.getProperty("javafx.runtime.version", "unknown"));

        boolean ok = stage.isShowing()
                && AppConfig.APP_NAME.equals(stage.getTitle())
                && !scene.getStylesheets().isEmpty()
                && pixelFont;
        System.out.println("[smoke] RESULT            : " + (ok ? "PASS" : "FAIL"));

        PauseTransition close = new PauseTransition(Duration.seconds(2));
        close.setOnFinished(e -> Platform.exit());
        close.play();
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
