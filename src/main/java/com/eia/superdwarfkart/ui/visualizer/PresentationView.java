package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.app.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * The whole stage given over to the structure visualizer, for answering a question live.
 *
 * <p>One keypress from the running application, no separate slides. Everything else - the
 * library, the transport, the game - gets out of the way, and what is left is the structure being
 * asked about, its step-through controls and the scatter plot enlarged enough to read from the
 * back of the room. A question about the cost of a binary search tree deletion is answered by
 * doing it, in front of the person asking.
 *
 * <p>The visualizer itself is moved here rather than duplicated, so the tree keeps its pan, its
 * zoom and the walk it was in the middle of. Entering and leaving presentation mode is not a
 * reset.
 */
public class PresentationView extends BorderPane {

    /** Width of the measurement column beside the visualizer. */
    private static final double PLOT_WIDTH = 380;

    private final BorderPane stage = new BorderPane();
    private final Label lastMeasurement = new Label();

    /**
     * Builds the presentation shell.
     *
     * @param counter supplies the measurements shown beside the visualizer; must not be {@code null}
     */
    public PresentationView(OperationCounter counter) {
        Objects.requireNonNull(counter, "counter must not be null");

        getStyleClass().add("presentation-view");
        setTop(buildHeader());
        setCenter(stage);
        setRight(buildPlotColumn(counter));

        counter.addListener(measurement ->
                lastMeasurement.setText(measurement == null ? "-" : describe(measurement)));
    }

    /**
     * Renders a measurement for the readout beside the visualizer.
     *
     * @param measurement what the last operation cost
     * @return a one-line description
     */
    private static String describe(Measurement measurement) {
        return measurement.operation() + ": " + plural(measurement.steps(), "step")
                + "  (" + plural(measurement.comparisons(), "comparison")
                + ", " + plural(measurement.pointerHops(), "hop") + ")"
                + "  n = " + measurement.n();
    }

    private static String plural(int count, String noun) {
        // "1 hops" beside a number that is the whole point of the panel reads as carelessness.
        return count + " " + noun + (count == 1 ? "" : noun.endsWith("s") ? "es" : "s");
    }

    private HBox buildHeader() {
        Label mode = new Label("PRESENTATION MODE");
        mode.getStyleClass().add("presentation-title");

        Label name = new Label(AppConfig.APP_NAME);
        name.getStyleClass().add("presentation-app-name");

        Label hint = new Label("F5 or ESC to return");
        hint.getStyleClass().add("presentation-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, mode, name, spacer, hint);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 18, 14, 18));
        header.getStyleClass().add("presentation-header");
        return header;
    }

    private VBox buildPlotColumn(OperationCounter counter) {
        Label caption = new Label("MEASURED STEPS vs n");
        caption.getStyleClass().add("panel-caption");

        ComplexityScatter scatter = new ComplexityScatter(counter);
        VBox.setVgrow(scatter, Priority.ALWAYS);

        Label lastCaption = new Label("LAST OPERATION");
        lastCaption.getStyleClass().add("panel-caption");

        lastMeasurement.getStyleClass().add("complexity-measured");
        lastMeasurement.setWrapText(true);
        lastMeasurement.setText("-");

        VBox column = new VBox(10, caption, scatter, lastCaption, lastMeasurement);
        column.setPadding(new Insets(14));
        column.setMinWidth(PLOT_WIDTH);
        column.setPrefWidth(PLOT_WIDTH);
        column.setMaxWidth(PLOT_WIDTH);
        column.getStyleClass().add("presentation-plot-column");
        return column;
    }

    /**
     * Takes the visualizer over for the duration of presentation mode.
     *
     * @param visualizer the node to show full stage; must not be {@code null}
     */
    public void attach(StructureVisualizer visualizer) {
        stage.setCenter(Objects.requireNonNull(visualizer, "visualizer must not be null"));
    }

    /**
     * Gives the visualizer back, so it can be returned to the main window.
     *
     * <p>A node cannot be in two scene graphs at once, so this has to happen before the main
     * layout takes it again.
     */
    public void detach() {
        stage.setCenter(null);
    }
}
