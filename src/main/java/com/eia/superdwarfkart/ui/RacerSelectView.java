package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.RacerFrame;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.game.SpeedClass;
import com.eia.superdwarfkart.model.Racer;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;

/**
 * Who is driving, and how fast.
 *
 * <p><strong>This screen needed no artwork at all.</strong> A racer's sheet holds four frames and
 * only the last of them is a portrait: frames 0 and 1 are the driving cycle, frame 2 is the kart
 * from behind, and frame 3 is the static menu icon. Drawing {@link RacerFrame#ICON} is exactly what
 * that frame was drawn for, and it is the reason it is the one frame that must never be animated.
 * All five racers have had one since the art arrived.
 *
 * <p>The speed class sits here rather than in settings because it belongs to the same decision. It
 * changes which course a song generates and therefore which stored high score applies to it, so it
 * is not a preference tucked away - it is the difficulty, chosen next to the driver.
 */
public class RacerSelectView extends ScrollPane {

    /** Portraits are drawn at a whole multiple of the frame, never a fraction (ground rule 8). */
    private static final int PORTRAIT_SCALE = 3;

    private final AppState state;
    private final AssetRegistry assets;
    private final Map<Racer, Canvas> portraits = new EnumMap<>(Racer.class);

    /**
     * Builds the select screen.
     *
     * @param state  the shared state this view changes; must not be {@code null}
     * @param assets where the racer sheets come from; must not be {@code null}
     */
    public RacerSelectView(AppState state, AssetRegistry assets) {
        this.state = state;
        this.assets = assets;

        VBox content = new VBox(16);
        content.setPadding(new Insets(18));
        content.getStyleClass().add("racer-content");

        Label heading = new Label("RACER SELECT");
        heading.getStyleClass().add("section-heading");

        FlowPane grid = new FlowPane(14, 14);
        ToggleGroup group = new ToggleGroup();
        for (Racer racer : Racer.values()) {
            grid.getChildren().add(buildCard(racer, group));
        }

        content.getChildren().addAll(heading, grid, buildSpeedRow());
        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("racer-view");
    }

    private VBox buildCard(Racer racer, ToggleGroup group) {
        SpriteSheet sheet = assets.racer(racer);
        Rectangle2D source = sheet.viewport(RacerFrame.ICON.index());

        Canvas canvas = new Canvas(source.getWidth() * PORTRAIT_SCALE,
                source.getHeight() * PORTRAIT_SCALE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Pixel art is never smoothed, and a portrait is the most obvious place it would show.
        gc.setImageSmoothing(false);
        gc.drawImage(sheet.image(),
                source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                0, 0, canvas.getWidth(), canvas.getHeight());
        portraits.put(racer, canvas);

        ToggleButton button = new ToggleButton(racer.displayName().toUpperCase());
        button.getStyleClass().add("racer-name");
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        // Space is play/pause; a focused toggle here would answer it instead.
        button.setFocusTraversable(false);
        button.setSelected(racer == state.getRacer());
        button.setOnAction(event -> {
            state.setRacer(racer);
            button.setSelected(true);
        });

        state.racerProperty().addListener(
                (observable, was, now) -> button.setSelected(now == racer));

        VBox card = new VBox(8, canvas, button);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("racer-card");
        card.setPadding(new Insets(10));
        return card;
    }

    private VBox buildSpeedRow() {
        Label heading = new Label("SPEED CLASS");
        heading.getStyleClass().add("panel-heading");

        Label caption = new Label("Faster classes spend more of the track's onsets, so the\n"
                + "course itself is denser - and the coins are worth more.");
        caption.getStyleClass().add("panel-caption");

        FlowPane row = new FlowPane(10, 10);
        ToggleGroup group = new ToggleGroup();
        for (SpeedClass speed : SpeedClass.values()) {
            ToggleButton button = new ToggleButton(speed.displayName());
            button.getStyleClass().add("class-button");
            button.setToggleGroup(group);
            button.setFocusTraversable(false);
            button.setSelected(speed == state.getSpeedClass());
            button.setOnAction(event -> {
                state.setSpeedClass(speed);
                button.setSelected(true);
            });
            state.speedClassProperty().addListener(
                    (observable, was, now) -> button.setSelected(now == speed));
            row.getChildren().add(button);
        }

        return new VBox(8, heading, caption, row);
    }
}
