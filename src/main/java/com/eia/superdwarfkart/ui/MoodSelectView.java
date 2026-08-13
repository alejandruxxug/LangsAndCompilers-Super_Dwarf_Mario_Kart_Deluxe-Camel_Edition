package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
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
 * is not decoration: it is the palette, and the strip is what the customizer will edit when the
 * mood system proper arrives.
 *
 * <p>This is where the assignment's dark-mode bonus lives. It ships as two named moods rather than
 * as a switch, so the eight presets and the user's own moods have somewhere to land later without
 * anything here changing shape.
 */
public class MoodSelectView extends ScrollPane {

    private static final double SWATCH = 18;
    private static final double CARD_SPACING = 14;

    private final AppState state;
    private final Map<String, VBox> cards = new LinkedHashMap<>();

    /**
     * Builds the switcher.
     *
     * @param state the shared state whose mood this view changes; must not be {@code null}
     */
    public MoodSelectView(AppState state) {
        this.state = state;

        VBox content = new VBox(CARD_SPACING);
        content.getStyleClass().add("mood-content");
        content.setPadding(new Insets(18));

        Label heading = new Label("MOODS");
        heading.getStyleClass().add("section-heading");
        content.getChildren().add(heading);

        Label caption = new Label("A mood is sixteen colours. Picking one restyles\n"
                + "every window at once - there is nothing to confirm.");
        caption.getStyleClass().add("panel-caption");
        content.getChildren().add(caption);

        for (Mood mood : Moods.builtIns()) {
            VBox card = buildCard(mood);
            cards.put(mood.id(), card);
            content.getChildren().add(card);
        }

        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("mood-view");

        markActive(state.getMood());
        state.moodProperty().addListener((observable, was, now) -> markActive(now));
    }

    private VBox buildCard(Mood mood) {
        Label name = new Label(mood.displayName().toUpperCase());
        name.getStyleClass().add("mood-name");

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

        VBox card = new VBox(8, name, swatches, apply);
        card.getStyleClass().add("mood-card");
        card.setPadding(new Insets(12));
        VBox.setVgrow(card, Priority.NEVER);
        return card;
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
