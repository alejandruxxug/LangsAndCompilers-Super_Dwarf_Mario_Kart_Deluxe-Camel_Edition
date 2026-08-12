package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.ui.visualizer.ComplexityScatter;
import com.eia.superdwarfkart.ui.visualizer.Measurement;
import com.eia.superdwarfkart.ui.visualizer.OperationCounter;
import com.eia.superdwarfkart.ui.visualizer.StructureComparison;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the active mode's operations cost in theory, what they just cost in practice, and the two
 * plotted against each other.
 *
 * <p>The point of the whole project stated on screen: the mode selector is not a preference, it
 * picks a data structure, and the costs listed here change with it. Switching from alphabetical
 * to shuffle turns an {@code O(log n)} search into {@code O(n)} in front of the room - and the
 * measured column turns that from an assertion into an observation.
 *
 * <p>The rows are rendered straight from {@link PlaybackMode#complexities()}, so this class knows
 * nothing about the three modes and gains nothing to maintain when a fourth appears. A measured
 * value appears on any row whose operation name the player brackets; the structure-specific rows
 * simply show a dash.
 */
public class ComplexityPanel extends VBox {

    /**
     * Narrowest the panel goes. Sized for the longest complexity string in the 7px pixel font;
     * it shares a column with the visualizer and takes whatever that column is.
     */
    private static final double MIN_WIDTH = 240;

    /**
     * Height given to the scatter plot here. Enough to separate the two curves and no more:
     * presentation mode is where it gets the room to be read across a lecture theatre.
     */
    private static final double SCATTER_HEIGHT = 112;

    private final Player player;
    private final OperationCounter counter;

    private final Label structureLabel = new Label();
    private final Label sizeLabel = new Label();
    private final VBox entries = new VBox(7);
    private final Button compareButton = new Button("COMPARE STRUCTURES");

    /**
     * Builds the panel and starts following the player.
     *
     * @param player  the player whose active mode is described; must not be {@code null}
     * @param counter supplies the measured step counts; must not be {@code null}
     */
    public ComplexityPanel(Player player, OperationCounter counter) {
        super(6);
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.counter = Objects.requireNonNull(counter, "counter must not be null");

        getStyleClass().add("complexity-panel");
        setPadding(new Insets(12, 12, 12, 12));
        setMinWidth(MIN_WIDTH);

        Label heading = new Label("COMPLEXITY");
        heading.getStyleClass().add("panel-heading");

        structureLabel.getStyleClass().add("complexity-structure");
        structureLabel.setWrapText(true);
        sizeLabel.getStyleClass().add("complexity-size");

        // The rows outgrow a short window in alphabetical mode, which has the most operations.
        // Scrolling them keeps the plot below always visible - it is the part worth seeing.
        ScrollPane scroller = new ScrollPane(entries);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("complexity-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        compareButton.getStyleClass().add("compare-button");
        compareButton.setMaxWidth(Double.MAX_VALUE);
        compareButton.setTooltip(new Tooltip(
                "Search all three structures for the same song, over the same library,\n"
                        + "and report what each one cost."));
        compareButton.setOnAction(e -> runComparison());

        // No caption above the plot: its own axes already say "steps" and "n", and every line
        // spent on chrome here is a line taken off the rows above it.
        ComplexityScatter scatter = new ComplexityScatter(counter);
        scatter.setPrefHeight(SCATTER_HEIGHT);
        scatter.setMinHeight(SCATTER_HEIGHT);

        getChildren().addAll(heading, structureLabel, sizeLabel, scroller, compareButton, scatter);

        player.addListener((mode, song) -> refresh());
        refresh();
    }

    /**
     * Redraws the rows from the active mode.
     *
     * <p>Called on every mode change and whenever the player's state moves, so {@code n} follows
     * a queue as it drains and the measured column follows the operation just performed.
     */
    public final void refresh() {
        PlaybackMode mode = player.mode();

        structureLabel.setText(mode.structureName());
        sizeLabel.setText("n = " + mode.size());
        compareButton.setDisable(player.library().isEmpty());

        entries.getChildren().clear();
        for (Map.Entry<String, String> cost : mode.complexities().entrySet()) {
            entries.getChildren().add(
                    entry(cost.getKey(), cost.getValue(), measuredHere(mode, cost.getKey())));
        }
    }

    /**
     * Returns the last measurement of an operation <strong>on the structure now active</strong>.
     *
     * <p>The counter is shared across every mode, so it still holds what {@code previous()} cost
     * over the circular list after the user has switched to the queue. Showing that against the
     * queue's row would put a measured cost beside "not supported", which is worse than showing
     * nothing: it would look like the queue had just walked backwards.
     *
     * @param mode      the active mode
     * @param operation the operation's name
     * @return the measurement, or {@code null} if this structure has not performed it
     */
    private Measurement measuredHere(PlaybackMode mode, String operation) {
        Measurement measurement = counter.latest(operation);
        return measurement != null && measurement.structure().equals(mode.structureName())
                ? measurement
                : null;
    }

    /**
     * Builds one operation row.
     *
     * <p>The name and the theoretical cost share a line - at 400 pixels the column has room for
     * about fifty-five glyphs, and the longest pair here is under forty - and the measured count
     * goes underneath. A row that has never run shows no second line at all, which keeps the
     * panel short and puts the eye straight on the operations that actually happened.
     *
     * @param operation  the operation's name
     * @param complexity its theoretical cost
     * @param measured   the last measurement of it, or {@code null} if it has not run
     * @return the row node
     */
    private static VBox entry(String operation, String complexity, Measurement measured) {
        Label name = new Label(operation);
        name.getStyleClass().add("complexity-operation");

        Label cost = new Label(complexity);
        cost.getStyleClass().add("complexity-cost");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox headline = new HBox(8, name, gap, cost);
        headline.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(1, headline);
        box.getStyleClass().add("complexity-entry");

        if (measured != null) {
            Label actual = new Label("measured " + measured.steps()
                    + (measured.steps() == 1 ? " step" : " steps") + " @ n=" + measured.n());
            actual.getStyleClass().add("complexity-measured");
            box.getChildren().add(actual);
        }
        return box;
    }

    /**
     * Runs the same search across all three structures and reports the three counts side by side.
     *
     * <p>Each structure is built fresh for the comparison, so running one mid-song cannot disturb
     * the ordering that is actually playing.
     */
    private void runComparison() {
        Song target = player.current();
        if (target == null) {
            List<Song> all = player.library().all();
            if (all.isEmpty()) {
                return;
            }
            target = all.get(all.size() / 2);
        }

        List<StructureComparison.Result> results =
                StructureComparison.run(player.library().all(), target);
        for (StructureComparison.Result result : results) {
            // Filed on the shared counter so the three land on the scatter plot as well.
            counter.record(result.measurement());
        }

        PixelDialog dialog = new PixelDialog(getScene() == null ? null : getScene().getWindow(),
                "COMPARE STRUCTURES");
        dialog.setContent(comparisonContent(target, results));
        dialog.setAcknowledgeOnly();
        dialog.setAcceptText("OK");
        dialog.showAndWait();
    }

    private VBox comparisonContent(Song target, List<StructureComparison.Result> results) {
        Label question = new Label("Searching all three for:");
        question.getStyleClass().add("panel-caption");

        Label song = new Label(LibraryView.ellipsize(target.getTitle(), 40));
        song.getStyleClass().add("complexity-structure");

        VBox box = new VBox(8, question, song);
        for (StructureComparison.Result result : results) {
            Label name = new Label(result.structure());
            name.getStyleClass().add("complexity-operation");

            Label steps = new Label(result.steps() + " steps"
                    + "   (" + result.measurement().comparisons() + " comparisons, "
                    + result.measurement().pointerHops() + " hops)");
            steps.getStyleClass().add("complexity-measured");

            box.getChildren().add(new VBox(2, name, steps));
        }

        Label footer = new Label("n = " + results.get(0).measurement().n()
                + ". Same library, same song, same instant.");
        footer.getStyleClass().add("complexity-cost");
        footer.setWrapText(true);
        footer.setMaxWidth(360);
        box.getChildren().add(footer);
        return box;
    }
}
