package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Where things are kept, and what every key does.
 *
 * <p>The keyboard reference is the part that earns its place. The shortcuts are wired across both
 * phases of event delivery and several of them are the only way to reach a whole view - {@code F6}
 * for the road, {@code F7} for the companion window - so an application that never says so has
 * features nobody finds. A printed line in a corner is a reminder for somebody who already knows;
 * this is the list.
 *
 * <p>Storage locations are shown rather than made editable. They move with
 * {@code -Dsdmk.home}, which is a testing switch, and a path the user can mistype is a library
 * they can lose.
 */
public class SettingsView extends ScrollPane {

    /** Every shortcut, in the order the reference lists them: what it does, then the key. */
    private static final String[][] SHORTCUTS = {
        {"Previous / next song", "LEFT  RIGHT"},
        {"Play / pause", "SPACE"},
        {"Cycle the playback mode", "TAB"},
        {"Presentation mode", "F5"},
        {"Swap the library for the road", "F6"},
        {"Collapse to the companion window", "F7"},
        {"Put the companion's artwork away", "F8"},
        {"Leave presentation mode", "ESC"},
        {"Step a traversal (tree focused)", "RIGHT  SPACE"},
        {"Change lane (racing)", "LEFT  RIGHT  A  D"},
        {"Jump (racing)", "SPACE  UP  W"},
        {"Frame pacing readout (racing)", "F3"},
    };

    /** Builds the settings page. */
    public SettingsView() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(18));
        content.getStyleClass().add("settings-content");

        Label heading = new Label("SETTINGS");
        heading.getStyleClass().add("section-heading");

        content.getChildren().addAll(
                heading,
                section("KEYBOARD", shortcutTable()),
                section("STORAGE", storageTable()),
                section("ABOUT", aboutTable()));

        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("settings-view");
    }

    private VBox section(String title, GridPane body) {
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-heading");
        VBox box = new VBox(8, heading, body);
        box.getStyleClass().add("settings-section");
        box.setPadding(new Insets(12));
        return box;
    }

    private GridPane shortcutTable() {
        GridPane grid = twoColumnGrid();
        int row = 0;
        for (String[] shortcut : SHORTCUTS) {
            grid.add(caption(shortcut[0]), 0, row);
            grid.add(value(shortcut[1]), 1, row);
            row++;
        }
        return grid;
    }

    private GridPane storageTable() {
        GridPane grid = twoColumnGrid();
        grid.add(caption("Library"), 0, 0);
        grid.add(value(AppConfig.libraryFile().toString()), 1, 0);
        grid.add(caption("High scores"), 0, 1);
        grid.add(value(AppConfig.scoresFile().toString()), 1, 1);
        grid.add(caption("Settings"), 0, 2);
        grid.add(value(AppConfig.settingsFile().toString()), 1, 2);
        grid.add(caption("Beatmaps"), 0, 3);
        grid.add(value(AppConfig.beatmapsDir().toString()), 1, 3);
        grid.add(caption("Artwork"), 0, 4);
        grid.add(value(AppConfig.assetsDir().toString()), 1, 4);
        return grid;
    }

    private GridPane aboutTable() {
        GridPane grid = twoColumnGrid();
        grid.add(caption("Name"), 0, 0);
        grid.add(value(AppConfig.APP_NAME), 1, 0);
        grid.add(caption("Version"), 0, 1);
        grid.add(value(AppConfig.APP_VERSION), 1, 1);
        grid.add(caption("Java"), 0, 2);
        grid.add(value(System.getProperty("java.version")), 1, 2);
        grid.add(caption("JavaFX"), 0, 3);
        grid.add(value(System.getProperty("javafx.runtime.version", "unknown")), 1, 3);
        return grid;
    }

    private GridPane twoColumnGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(7);

        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(260);
        labels.setPrefWidth(260);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        return grid;
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-caption");
        return label;
    }

    /**
     * A value, with the whole of it in a tooltip.
     *
     * <p>A storage path is far longer than the column and this font has no way to shrink, so the
     * label is left to be cut by its column and the full value goes where it can be read - which is
     * what every other long value in this interface does.
     */
    private Label value(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-value");
        label.setTooltip(new Tooltip(text));
        return label;
    }
}
