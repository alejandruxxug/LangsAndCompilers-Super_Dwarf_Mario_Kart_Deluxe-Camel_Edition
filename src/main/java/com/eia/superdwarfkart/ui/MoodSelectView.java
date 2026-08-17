package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.MoodLayer;
import com.eia.superdwarfkart.mood.MoodRepository;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mood switcher: pick a look and the whole application takes it immediately.
 *
 * <p>There is no preview pane and no confirm button, because the application <em>is</em> the
 * preview. Selecting a mood restyles both windows, every canvas and every control in the same
 * instant, so a pane showing a small picture of what it would look like would be strictly less
 * informative than simply doing it.
 *
 * <p>Each mood shows its sixteen colours as a strip of swatches, in {@code PaletteRole} order. That
 * is not decoration: it is the palette, and it is what the customizer edits.
 *
 * <p><strong>Ten moods ship, and the count is the feature.</strong> Ten in a switcher reads as a
 * system; two reads as a setting. That is the whole reason {@code PaletteImporter} and
 * {@code PaletteBuilder} were built before a single colour was hand-picked - see {@link Moods}.
 * The user's own moods are listed after them, and are the only ones that can be edited or deleted.
 *
 * <p>This is also where the assignment's dark-mode bonus lives: as two named moods among ten rather
 * than as a switch.
 */
public class MoodSelectView extends ScrollPane {

    private static final double SWATCH = 18;
    private static final double CARD_SPACING = 12;

    private final AppState state;
    private final MoodRepository repository;
    private final FlowPane gallery = new FlowPane(CARD_SPACING, CARD_SPACING);
    private final Map<String, VBox> cards = new LinkedHashMap<>();

    private Runnable onCustomize;

    /**
     * Builds the switcher.
     *
     * @param state      the shared state whose mood this view changes; must not be {@code null}
     * @param repository where the user's own moods live; must not be {@code null}
     */
    public MoodSelectView(AppState state, MoodRepository repository) {
        this.state = state;
        this.repository = repository;

        VBox content = new VBox(CARD_SPACING);
        content.getStyleClass().add("mood-content");
        content.setPadding(new Insets(18));

        Label heading = new Label("MOODS");
        heading.getStyleClass().add("section-heading");

        Label caption = new Label("A mood is sixteen colours and a stack of overlay layers.\n"
                + "Picking one restyles every window at once - there is nothing to confirm.");
        caption.getStyleClass().add("panel-caption");

        Button customize = new Button("CUSTOMIZE / CREATE");
        customize.setFocusTraversable(false);
        customize.setOnAction(event -> {
            if (onCustomize != null) {
                onCustomize.run();
            }
        });
        Tooltip.install(customize, new Tooltip(
                "Edit the palette, build layers, draw a tile, import an Aseprite or Lospec "
                        + "palette.\nEditing a mood that ships with the application copies it "
                        + "first."));

        content.getChildren().addAll(heading, caption, customize, gallery);
        VBox.setVgrow(gallery, Priority.ALWAYS);

        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("mood-view");

        refresh();
        state.moodProperty().addListener((observable, was, now) -> markActive(now));
    }

    /**
     * Called when the user asks to edit or build a mood.
     *
     * @param handler what to run
     */
    public void setOnCustomize(Runnable handler) {
        this.onCustomize = handler;
    }

    /**
     * Rebuilds the gallery from the presets and whatever is on disk.
     *
     * <p>Called after any change to the set - an import, a duplicate, a delete. A mood that did not
     * appear in the switcher until the next launch would read as the operation having failed.
     */
    public final void refresh() {
        gallery.getChildren().clear();
        cards.clear();
        for (Mood mood : repository.all()) {
            VBox card = buildCard(mood);
            cards.put(mood.id(), card);
            gallery.getChildren().add(card);
        }
        markActive(state.getMood());
    }

    private VBox buildCard(Mood mood) {
        Label name = new Label(mood.displayName().toUpperCase());
        name.getStyleClass().add("mood-name");

        Label kind = new Label(describe(mood));
        kind.getStyleClass().add("swatch-hex");

        HBox swatches = new HBox(2);
        for (PaletteRole role : PaletteRole.values()) {
            Region swatch = new Region();
            swatch.setMinSize(SWATCH, SWATCH);
            swatch.setPrefSize(SWATCH, SWATCH);
            swatch.setMaxSize(SWATCH, SWATCH);
            // Set in code rather than through the stylesheet: this swatch shows a colour from a
            // palette that is not the active one, so it is the single place in the interface that
            // must NOT follow the mood.
            swatch.setStyle("-fx-background-color: " + GbaColor.toHex(mood.color(role)) + ";");
            Tooltip.install(swatch,
                    new Tooltip(role.displayName() + "\n" + GbaColor.toHex(mood.color(role))));
            swatches.getChildren().add(swatch);
        }

        Button apply = new Button("USE THIS MOOD");
        apply.getStyleClass().add("mood-apply");
        apply.setFocusTraversable(false);
        apply.setOnAction(event -> state.setMood(mood));

        VBox card = new VBox(6, name, kind, swatches, apply);
        card.getStyleClass().add("mood-card");
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.TOP_LEFT);
        return card;
    }

    /**
     * The line under a mood's name: where it came from, and what it carries beyond a palette.
     */
    private static String describe(Mood mood) {
        StringBuilder text = new StringBuilder(Moods.isBuiltIn(mood.id()) ? "built in" : "yours");
        int layers = mood.layers().size();
        if (layers > 0) {
            text.append(", ").append(layers).append(layers == 1 ? " layer" : " layers");
            if (layers >= MoodLayer.MAX_LAYERS) {
                text.append(" (full)");
            }
        }
        if (mood.reactive()) {
            text.append(", reactive");
        }
        return text.toString();
    }

    /**
     * Marks which card is the active mood.
     *
     * <p>By style class rather than by colour, so the marker itself follows the mood it is marking.
     */
    private void markActive(Mood active) {
        cards.forEach((id, card) -> {
            boolean selected = active != null && active.id().equals(id);
            card.getStyleClass().removeAll("mood-card-active");
            if (selected) {
                card.getStyleClass().add("mood-card-active");
            }
        });
    }
}
