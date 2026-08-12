package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Shows what the active mode's operations actually cost.
 *
 * <p>The point of the whole project stated on screen: the mode selector is not a preference, it
 * picks a data structure, and the costs listed here change with it. Switching from alphabetical
 * to shuffle turns {@code O(log n)} search into {@code O(n)} in front of the room.
 *
 * <p>Rendered straight from {@link PlaybackMode#complexities()}, so this class knows nothing
 * about the three modes and gains nothing to maintain when a fourth appears.
 *
 * <p>Measured step counts land beside the theoretical figures with the operation counter, and
 * the entries are laid out two lines deep to leave room for them.
 */
public class ComplexityPanel extends VBox {

    /** Column width in pixels. Sized for the longest complexity string in the 7px pixel font. */
    private static final double WIDTH = 250;

    private final Player player;

    private final Label structureLabel = new Label();
    private final Label sizeLabel = new Label();
    private final VBox entries = new VBox(7);

    /**
     * Builds the panel and starts following the player.
     *
     * @param player the player whose active mode is described
     */
    public ComplexityPanel(Player player) {
        super(10);
        this.player = player;

        getStyleClass().add("complexity-panel");
        setPadding(new Insets(14, 12, 14, 12));
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);

        Label heading = new Label("COMPLEXITY");
        heading.getStyleClass().add("panel-heading");

        structureLabel.getStyleClass().add("complexity-structure");
        structureLabel.setWrapText(true);
        sizeLabel.getStyleClass().add("complexity-size");

        getChildren().addAll(heading, structureLabel, sizeLabel, entries);

        player.addListener((mode, song) -> refresh());
        refresh();
    }

    /**
     * Redraws the panel from the active mode.
     *
     * <p>Called on every mode change and whenever the player's state moves, so {@code n} follows
     * a queue as it drains.
     */
    public final void refresh() {
        PlaybackMode mode = player.mode();

        structureLabel.setText(mode.structureName());
        sizeLabel.setText("n = " + mode.size());

        entries.getChildren().clear();
        for (Map.Entry<String, String> cost : mode.complexities().entrySet()) {
            entries.getChildren().add(entry(cost.getKey(), cost.getValue()));
        }
    }

    /**
     * Builds one operation entry.
     *
     * <p>Two lines rather than two columns: at one em per glyph an operation name and its
     * complexity side by side run past 45 characters, which does not fit any sensible column
     * width. Stacking them keeps the panel narrow and nothing has to be truncated.
     *
     * @param operation  the operation's name
     * @param complexity its theoretical cost
     * @return the entry node
     */
    private static VBox entry(String operation, String complexity) {
        Label name = new Label(operation);
        name.getStyleClass().add("complexity-operation");

        Label cost = new Label(complexity);
        cost.getStyleClass().add("complexity-cost");

        VBox box = new VBox(2, name, cost);
        box.getStyleClass().add("complexity-entry");
        return box;
    }
}
