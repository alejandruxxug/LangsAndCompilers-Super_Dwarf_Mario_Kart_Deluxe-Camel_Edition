package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.GradientLayer;
import com.eia.superdwarfkart.mood.ImageLayer;
import com.eia.superdwarfkart.mood.LayerBlend;
import com.eia.superdwarfkart.mood.LayerStyle;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.MoodIssue;
import com.eia.superdwarfkart.mood.MoodLayer;
import com.eia.superdwarfkart.mood.MoodRepository;
import com.eia.superdwarfkart.mood.MoodValidator;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.mood.PaletteImporter;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.mood.PixelTile;
import com.eia.superdwarfkart.mood.ProceduralLayer;
import com.eia.superdwarfkart.mood.ZBand;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build a mood: sixteen colours, a stack of overlay layers, and a tile editor.
 *
 * <p><strong>Applied live. There is no OK button and no preview pane, because the whole application
 * is the preview.</strong> Moving a swatch restyles every window, every control and every canvas in
 * the same instant, so a small rectangle showing what it would look like would be strictly less
 * informative than simply doing it.
 *
 * <h2>Never edit a preset in place</h2>
 *
 * <p>The eight track presets and the two plain moods are code, so there is always a known-good mood
 * to fall back to twenty minutes before a defence. Editing one therefore <em>duplicates it first</em>
 * and carries on in the copy, and says so in the status line. That is the "duplicate-then-edit" rule
 * with the friction taken out: making the user press a button before they may change a colour is a
 * rule they will resent, and one they will work around by editing the file by hand.
 *
 * <h2>The validator bar</h2>
 *
 * <p>Green when the palette is sound; otherwise it names the failing pair in plain English and says
 * what stops working. It is not advisory. {@link MoodValidator#repair} substitutes a corrected value
 * for the user's, so what is <em>rendered</em> always meets the thresholds - which is the only
 * arrangement where the warning and the consequence arrive together. An application that accepted an
 * unreadable mood silently would fail live, on stage, on the two views worth the most.
 */
public class MoodCustomizerView extends ScrollPane {

    private static final Logger LOG = Logger.getLogger(MoodCustomizerView.class.getName());

    private static final double SWATCH = 34;

    /** Which of the three panels is showing. */
    private enum Section {
        PALETTE("PALETTE"), LAYERS("LAYERS"), EDITOR("CREATE YOUR OWN");

        private final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private final AppState state;
    private final MoodRepository repository;

    private final Label title = new Label();
    private final Label validatorBar = new Label();
    private final Label status = new Label();
    private final VBox body = new VBox(14);
    private final FlowPane swatchStrip = new FlowPane(6, 6);
    private final VBox layerList = new VBox(8);
    private final PixelEditorView editor = new PixelEditorView();

    private Section section = Section.PALETTE;
    private Runnable onMoodsChanged;

    /**
     * Builds the customizer.
     *
     * @param state      the shared state whose mood this edits; must not be {@code null}
     * @param repository where user moods are stored; must not be {@code null}
     */
    public MoodCustomizerView(AppState state, MoodRepository repository) {
        this.state = state;
        this.repository = repository;

        title.getStyleClass().add("section-heading");
        validatorBar.getStyleClass().add("validator-bar");
        validatorBar.setWrapText(true);
        validatorBar.setMaxWidth(Double.MAX_VALUE);
        status.getStyleClass().add("panel-caption");

        editor.setOnSaveToLayer(this::saveTileToLayer);

        VBox content = new VBox(14, title, validatorBar, sectionTabs(), body, actions(), status);
        content.setPadding(new Insets(18));
        content.getStyleClass().add("mood-content");

        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("mood-view");

        state.moodProperty().addListener((observable, was, now) -> refresh());
        refresh();
    }

    /**
     * Called whenever a mood is created, renamed or deleted, so the gallery can rebuild.
     *
     * @param handler what to run
     */
    public void setOnMoodsChanged(Runnable handler) {
        this.onMoodsChanged = handler;
    }

    /** Rebuilds every panel from the active mood. */
    public final void refresh() {
        Mood mood = state.getMood();
        title.setText("EDITING  " + mood.displayName().toUpperCase());
        editor.setPalette(mood.palette());
        showValidation(mood);
        rebuildSwatches(mood);
        rebuildLayers(mood);
        showSection(section);
    }

    // ------------------------------------------------------------------
    // Chrome
    // ------------------------------------------------------------------

    private HBox sectionTabs() {
        HBox row = new HBox(6);
        ToggleGroup group = new ToggleGroup();
        for (Section value : Section.values()) {
            ToggleButton button = new ToggleButton(value.label);
            button.setToggleGroup(group);
            button.setFocusTraversable(false);
            button.setSelected(value == section);
            button.setOnAction(event -> {
                button.setSelected(true);
                showSection(value);
            });
            row.getChildren().add(button);
        }
        return row;
    }

    /**
     * Shows the palette strip.
     *
     * <p>Exists so the smoke test can photograph it: each of the three panels only exists once its
     * button has been pressed, so one shot of the opening state proves nothing about the other two.
     */
    public void showPalette() {
        showSection(Section.PALETTE);
    }

    /**
     * Shows the layer list.
     *
     * <p>Exists for the same reason {@link #showPalette()} does: the smoke test has to press each
     * of the three buttons to photograph what is behind it.
     */
    public void showLayers() {
        showSection(Section.LAYERS);
    }

    /**
     * Shows the tile editor, optionally with something already drawn in it.
     *
     * @param seed a tile to load first, so the picture is of a drawing rather than of an empty
     *             grid; {@code null} leaves whatever is there
     */
    public void showEditor(PixelTile seed) {
        if (seed != null) {
            editor.setTile(seed);
        }
        showSection(Section.EDITOR);
    }

    private void showSection(Section wanted) {
        this.section = wanted;
        body.getChildren().setAll(switch (wanted) {
            case PALETTE -> paletteSection();
            case LAYERS -> layerSection();
            case EDITOR -> editor;
        });
    }

    private VBox paletteSection() {
        Label caption = new Label("Click a swatch to recolour that role. Every value snaps to the\n"
                + "GBA's 5-bit grid, and the whole application restyles as you go.");
        caption.getStyleClass().add("panel-caption");
        VBox box = new VBox(10, caption, swatchStrip);
        box.getStyleClass().add("mood-card");
        box.setPadding(new Insets(12));
        return box;
    }

    private VBox layerSection() {
        Label caption = new Label("Layers draw on the fullscreen window only - the companion strip\n"
                + "is 224px of transparent card and parallax there is invisible noise.\n"
                + "Above the content, opacity is capped at "
                + ZBand.ABOVE_CONTENT.maxOpacity() + " so a layer can never bury the game.");
        caption.getStyleClass().add("panel-caption");

        FlowPane add = new FlowPane(6, 6);
        add.getChildren().addAll(
                button("+ GRADIENT", () -> addLayer(GradientLayer.between(
                        PaletteRole.BACKGROUND, PaletteRole.SURFACE_RAISED, 90))),
                button("+ SCANLINES", () -> addLayer(
                        ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.25))),
                button("+ LCD GRID", () -> addLayer(
                        ProceduralLayer.of(ProceduralLayer.Pattern.LCD_GRID, 0.18))),
                button("+ VIGNETTE", () -> addLayer(
                        ProceduralLayer.of(ProceduralLayer.Pattern.VIGNETTE, 0.3))),
                button("+ STARFIELD", () -> addLayer(new ProceduralLayer(
                        LayerStyle.behind().withScroll(-8, 0),
                        ProceduralLayer.Pattern.STARFIELD, 4, System.nanoTime()))),
                button("+ IMAGE", this::importImageLayer));

        CheckBox reactive = new CheckBox("REACT TO THE MUSIC");
        reactive.setFocusTraversable(false);
        reactive.setSelected(state.getMood().reactive());
        reactive.setOnAction(event -> apply(state.getMood().withReactive(reactive.isSelected())));
        Tooltip.install(reactive, new Tooltip(
                "Layer opacity and the accent's brightness follow the level and the beat.\n"
                        + "Brightness and alpha only, never hue; capped at "
                        + (int) com.eia.superdwarfkart.mood.MoodReactivity.MAX_UPDATE_HZ
                        + " Hz whatever the tempo; and clamped so the protected roles stay\n"
                        + "distinguishable at every point in the modulation."));

        VBox box = new VBox(12, caption, add, reactive, layerList);
        box.getStyleClass().add("mood-card");
        box.setPadding(new Insets(12));
        return box;
    }

    private FlowPane actions() {
        FlowPane row = new FlowPane(6, 6);
        row.getChildren().addAll(
                button("DUPLICATE", this::duplicate),
                button("RENAME", this::rename),
                button("DELETE", this::delete),
                button("IMPORT PALETTE", this::importPalette),
                button("EXPORT MOOD", this::exportMood),
                button("IMPORT MOOD", this::importMood));
        return row;
    }

    private Button button(String label, Runnable handler) {
        Button button = new Button(label);
        button.setFocusTraversable(false);
        button.setOnAction(event -> handler.run());
        return button;
    }

    // ------------------------------------------------------------------
    // Palette
    // ------------------------------------------------------------------

    private void rebuildSwatches(Mood mood) {
        swatchStrip.getChildren().clear();
        for (PaletteRole role : PaletteRole.values()) {
            Color color = mood.color(role);

            Region block = new Region();
            block.setMinSize(SWATCH, SWATCH);
            block.setPrefSize(SWATCH, SWATCH);
            block.setMaxSize(SWATCH, SWATCH);
            // In code rather than through the stylesheet: this shows a colour of a palette that may
            // not be the active one, so it is one of the few places in the interface that must not
            // follow the mood.
            block.setStyle("-fx-background-color: " + GbaColor.toHex(color) + ";"
                    + "-fx-border-color: -role-outline; -fx-border-width: 2;");

            Label name = new Label(shortName(role));
            name.getStyleClass().add("swatch-name");
            Label hex = new Label(GbaColor.toHex(color));
            hex.getStyleClass().add("swatch-hex");

            VBox cell = new VBox(3, block, name, hex);
            cell.setAlignment(Pos.CENTER);
            cell.getStyleClass().add("swatch-cell");
            if (role.isProtected()) {
                cell.getStyleClass().add("swatch-protected");
            }
            Tooltip.install(cell, new Tooltip(role.displayName() + "\n" + GbaColor.toHex(color)
                    + (role.isProtected()
                            ? "\n\nProtected: this role carries meaning rather than decoration."
                            : "")));
            cell.setOnMouseClicked(event -> recolour(role));
            swatchStrip.getChildren().add(cell);
        }
    }

    /**
     * Opens the picker for one role and applies the result.
     *
     * <p>Modal and committed once, which is deliberate: the picker's own sliders fire per pixel of
     * travel, and applying a palette means regenerating the stylesheet, restyling every scene and
     * re-rasterising every layer. That is exactly the work this project has a rule against doing on
     * continuous input.
     */
    private void recolour(PaletteRole role) {
        Mood mood = state.getMood();
        Color chosen = GbaColorPicker.pick(window(), role, mood.color(role));
        if (chosen == null) {
            return;
        }
        apply(mood.withPalette(mood.palette().withColor(role, chosen)));
    }

    private static String shortName(PaletteRole role) {
        // The strip is sixteen cells across a column that is not wide, and a role's full name in a
        // fixed-width pixel font is several times the width of its swatch.
        return role.name().replace("BACKGROUND", "BG").replace("SURFACE", "SURF")
                .replace("PRIMARY", "PRIM").replace("_", " ");
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

    private void rebuildLayers(Mood mood) {
        layerList.getChildren().clear();
        List<MoodLayer> layers = mood.layers();
        if (layers.isEmpty()) {
            Label empty = new Label("No layers. A palette on its own restyles the interface;\n"
                    + "one scanline pass or one vignette and it looks like a different machine.");
            empty.getStyleClass().add("panel-caption");
            layerList.getChildren().add(empty);
            return;
        }
        for (int index = 0; index < layers.size(); index++) {
            layerList.getChildren().add(layerRow(index, layers.get(index)));
        }
    }

    private VBox layerRow(int index, MoodLayer layer) {
        LayerStyle style = layer.style();

        Label name = new Label((index + 1) + ".  " + layer.describe());
        name.getStyleClass().add("layer-name");

        CheckBox visible = new CheckBox("SHOW");
        visible.setSelected(style.visible());
        visible.setFocusTraversable(false);
        visible.setOnAction(event ->
                replace(index, layer.withStyle(style.withVisible(visible.isSelected()))));

        ComboBox<ZBand> band = new ComboBox<>();
        band.getItems().addAll(ZBand.values());
        band.setValue(style.zBand());
        band.setFocusTraversable(false);
        band.setPrefWidth(96);
        band.setOnAction(event -> replace(index, layer.withStyle(style.withBand(band.getValue()))));

        ComboBox<LayerBlend> blend = new ComboBox<>();
        blend.getItems().addAll(LayerBlend.values());
        blend.setValue(style.blend());
        blend.setFocusTraversable(false);
        blend.setPrefWidth(110);
        blend.setOnAction(event ->
                replace(index, layer.withStyle(style.withBlend(blend.getValue()))));

        Slider opacity = new Slider(0, style.zBand().maxOpacity(), style.opacity());
        opacity.setPrefWidth(150);
        // Committed when the drag ends, not per pixel of travel: a commit here re-rasterises every
        // layer and writes the mood to disk. Same rule as the library's rating slider, which used to
        // rebuild the whole table and write a JSON file per pixel and visibly lagged.
        opacity.valueChangingProperty().addListener((observable, was, changing) -> {
            if (!changing) {
                replace(index, layer.withStyle(style.withOpacity(opacity.getValue())));
            }
        });
        opacity.valueProperty().addListener((observable, was, now) -> {
            if (!opacity.isValueChanging()) {
                replace(index, layer.withStyle(style.withOpacity(now.doubleValue())));
            }
        });

        Slider drift = new Slider(-120, 120, style.scrollX());
        drift.setPrefWidth(150);
        drift.valueChangingProperty().addListener((observable, was, changing) -> {
            if (!changing) {
                replace(index, layer.withStyle(
                        style.withScroll(drift.getValue(), style.scrollY())));
            }
        });

        FlowPane controls = new FlowPane(6, 6, visible, band, blend,
                labelled("OPACITY", opacity), labelled("DRIFT X", drift));

        FlowPane order = new FlowPane(6, 6,
                button("UP", () -> move(index, -1)),
                button("DOWN", () -> move(index, 1)),
                button("DELETE", () -> remove(index)));

        VBox row = new VBox(8, name, controls, order);
        row.getStyleClass().add("layer-row");
        row.setPadding(new Insets(10));
        return row;
    }

    private HBox labelled(String caption, Region control) {
        Label label = new Label(caption);
        label.getStyleClass().add("settings-caption");
        HBox box = new HBox(6, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void addLayer(MoodLayer layer) {
        Mood mood = state.getMood();
        if (mood.layers().size() >= MoodLayer.MAX_LAYERS) {
            // Said out loud rather than silently dropped. Six layers redrawn individually at 60 fps
            // is how this feature quietly costs the framerate the rest of the project shows off.
            say("This mood already holds the maximum of " + MoodLayer.MAX_LAYERS
                    + " layers. Delete one first.");
            return;
        }
        apply(mood.withLayerAdded(layer));
    }

    private void replace(int index, MoodLayer layer) {
        apply(state.getMood().withLayer(index, layer));
    }

    private void move(int index, int delta) {
        apply(state.getMood().withLayerMoved(index, delta));
    }

    private void remove(int index) {
        apply(state.getMood().withLayerRemoved(index));
    }

    private void importImageLayer() {
        Mood mood = editableMood();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add an image layer");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            // Copied into the mood's own folder rather than referenced. A mood has to survive the
            // source being moved or deleted, and it has to survive being zipped up and handed over.
            String name = repository.importImage(mood.id(), file.toPath());
            apply(mood.withLayerAdded(ImageLayer.tiled(name)));
            say("Imported " + name + " into this mood's folder.");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not import " + file, e);
            say("Could not import that image: " + e.getMessage());
        }
    }

    private void saveTileToLayer(PixelTile tile) {
        if (tile.isBlank()) {
            say("That tile is empty - draw something first.");
            return;
        }
        Mood mood = editableMood();
        String name = "tile-" + (mood.tiles().size() + 1);
        Mood updated = mood.withTile(name, tile);
        if (updated.layers().size() < MoodLayer.MAX_LAYERS) {
            updated = updated.withLayerAdded(ImageLayer.tiled(name));
        }
        apply(updated);
        showSection(Section.LAYERS);
        // Straight to the layer list, so the scroll speed can be set immediately: scrolling a
        // hand-drawn 16x16 tile is the cheapest good-looking background in the application.
        say("Saved as \"" + name + "\" and added as a tiled layer. Set its drift below.");
    }

    // ------------------------------------------------------------------
    // Mood actions
    // ------------------------------------------------------------------

    private void duplicate() {
        Mood mood = state.getMood();
        Mood copy = mood.copyAs(repository.uniqueId(mood.id() + "-copy"),
                mood.displayName() + " copy");
        store(copy);
        state.setMood(copy);
        say("Duplicated. Edits now go to \"" + copy.displayName() + "\".");
    }

    private void rename() {
        Mood mood = editableMood();
        TextField field = new TextField(mood.displayName());
        field.setPrefColumnCount(20);
        PixelDialog dialog = new PixelDialog(window(), "RENAME MOOD");
        dialog.setContent(new VBox(8, new Label("NAME"), field));
        if (!dialog.showAndWait() || field.getText().isBlank()) {
            return;
        }
        apply(mood.renamed(field.getText().strip()));
    }

    private void delete() {
        Mood mood = state.getMood();
        if (Moods.isBuiltIn(mood.id())) {
            say("\"" + mood.displayName() + "\" ships with the application and cannot be deleted. "
                    + "There has to be a known-good mood to fall back to.");
            return;
        }
        if (!PixelDialog.confirm(window(), "DELETE MOOD",
                "Delete \"" + mood.displayName() + "\"?\nThis removes its folder and everything "
                        + "in it.")) {
            return;
        }
        try {
            repository.delete(mood.id());
            state.setMood(Moods.defaultMood());
            moodsChanged();
            say("Deleted.");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete the mood " + mood.id(), e);
            say("Could not delete that mood: " + e.getMessage());
        }
    }

    private void importPalette() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import a palette");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Aseprite and Lospec palettes", "*.gpl", "*.hex"));
        File file = chooser.showOpenDialog(window());
        if (file != null) {
            importPalette(file.toPath());
        }
    }

    /**
     * Creates a mood from a palette file, which is also what dropping one on the window does.
     *
     * @param file the {@code .gpl} or {@code .hex} to read; must not be {@code null}
     */
    public void importPalette(Path file) {
        try {
            com.eia.superdwarfkart.mood.Palette palette = PaletteImporter.read(file);
            Mood mood = new Mood(repository.uniqueId(MoodRepository.slug(palette.name())),
                    palette.name(), palette);
            store(mood);
            state.setMood(mood);
            say("Imported " + file.getFileName() + " as a new mood.");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not import the palette " + file, e);
            say("Could not read that palette: " + e.getMessage());
        }
    }

    private void exportMood() {
        Mood mood = state.getMood();
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Export \"" + mood.displayName() + "\"");
        File folder = chooser.showDialog(window());
        if (folder == null) {
            return;
        }
        try {
            Path written = repository.export(mood, folder.toPath());
            say("Exported to " + written + " - palette, layers and every image, so it can be "
                    + "dropped in whole.");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not export the mood " + mood.id(), e);
            say("Could not export: " + e.getMessage());
        }
    }

    private void importMood() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Import a mood folder");
        File folder = chooser.showDialog(window());
        if (folder == null) {
            return;
        }
        try {
            Mood imported = repository.importFrom(folder.toPath());
            moodsChanged();
            state.setMood(imported);
            say("Imported \"" + imported.displayName() + "\".");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not import a mood from " + folder, e);
            say("Could not import that mood: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Applying
    // ------------------------------------------------------------------

    /**
     * Applies an edit, duplicating a preset first if that is what is being edited.
     *
     * @param edited the mood as the user has just changed it
     */
    private void apply(Mood edited) {
        Mood target = edited;
        if (Moods.isBuiltIn(edited.id())) {
            target = edited.copyAs(repository.uniqueId(edited.id() + "-copy"),
                    edited.displayName() + " copy");
            say("\"" + edited.displayName() + "\" ships with the application, so this edit went "
                    + "into a copy called \"" + target.displayName() + "\". The original is "
                    + "untouched.");
        }
        store(target);
        state.setMood(target);
    }

    /**
     * The mood an edit should be written to: the active one, or a fresh copy of a preset.
     *
     * <p>Used by the actions that have to have somewhere on disk to put a file before they can do
     * anything - importing an image, or saving a tile - because those cannot wait for the edit to
     * decide which mood they belong to.
     */
    private Mood editableMood() {
        Mood mood = state.getMood();
        if (!Moods.isBuiltIn(mood.id())) {
            return mood;
        }
        Mood copy = mood.copyAs(repository.uniqueId(mood.id() + "-copy"),
                mood.displayName() + " copy");
        store(copy);
        state.setMood(copy);
        say("\"" + mood.displayName() + "\" ships with the application, so this went into a copy "
                + "called \"" + copy.displayName() + "\".");
        return copy;
    }

    private void store(Mood mood) {
        try {
            repository.save(mood);
            moodsChanged();
        } catch (IOException | RuntimeException e) {
            // A mood that failed to persist is still applied - the user can see it and go on
            // working. Losing it at the next launch is bad; refusing the edit is worse.
            LOG.log(Level.WARNING, "Could not save the mood " + mood.id(), e);
            say("Applied, but could not be saved: " + e.getMessage());
        }
    }

    private void showValidation(Mood mood) {
        List<MoodIssue> issues = MoodValidator.validate(mood.palette());
        validatorBar.getStyleClass().removeAll("validator-ok", "validator-bad");
        if (issues.isEmpty()) {
            validatorBar.getStyleClass().add("validator-ok");
            validatorBar.setText("PALETTE OK - every protected role is still distinguishable.");
            return;
        }
        validatorBar.getStyleClass().add("validator-bad");
        List<String> lines = new ArrayList<>();
        for (MoodIssue issue : issues) {
            lines.add("! " + issue.detail());
        }
        lines.add("Rendered with a corrected substitute until this is fixed.");
        validatorBar.setText(String.join("\n", lines));
    }

    private void moodsChanged() {
        if (onMoodsChanged != null) {
            onMoodsChanged.run();
        }
    }

    private void say(String message) {
        status.setText(message);
    }

    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
