package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Holds whichever structure view belongs to the active mode, and swaps it when the mode changes.
 *
 * <p>This is the queue view. Upcoming songs are shown <em>structurally</em> - as positions around
 * a circuit, as a starting grid, as a tree - rather than as a flat list, and switching mode
 * switches the picture as well as the ordering. Seeing the same library redrawn three different
 * ways is the argument that the mode selector picks a data structure rather than a preference.
 *
 * <p><strong>This is the one class that names the concrete modes.</strong> Everywhere else the
 * three are interchangeable behind {@link PlaybackMode}, and the player in particular never asks
 * what it is holding. Here the question is unavoidable and legitimate: a view of a race circuit
 * <em>is</em> a view of a circular list, and there is no honest way to draw a starting grid
 * without a queue behind it. An unrecognised mode gets a plain message rather than an exception,
 * so adding a fourth mode leaves the application working while its view is written.
 */
public class StructureVisualizer extends BorderPane {

    private final Player player;
    private final AppState state;
    private final AssetRegistry assets;

    private final StackPane holder = new StackPane();

    /** The mode object the current view was built for. */
    private PlaybackMode shownMode;

    private StructureView view;

    /** Whether the visualizer is on screen and therefore worth drawing. */
    private boolean drawing = true;

    /**
     * Builds the visualizer and starts following the player.
     *
     * @param player supplies the active mode and its structure; must not be {@code null}
     * @param state  shared state, for the racer riding the karts; must not be {@code null}
     * @param assets supplies the artwork; must not be {@code null}
     */
    public StructureVisualizer(Player player, AppState state, AssetRegistry assets) {
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.assets = Objects.requireNonNull(assets, "assets must not be null");

        getStyleClass().add("structure-visualizer");
        holder.setPadding(Insets.EMPTY);
        setCenter(holder);

        // Following the player rather than the mode identifier in AppState, because building a
        // view needs the mode object itself and not only which of the three it is. AppState
        // publishes the same change to everything that needs the identifier alone.
        player.addListener((mode, song) -> onPlaybackChanged(mode));
        onPlaybackChanged(player.mode());
    }

    /**
     * Rebuilds the view when the mode object changed, and otherwise brings it up to date.
     *
     * @param mode the mode now active
     */
    private void onPlaybackChanged(PlaybackMode mode) {
        if (mode != shownMode) {
            swapTo(mode);
            return;
        }
        if (view != null) {
            view.refresh();
        }
    }

    private void swapTo(PlaybackMode mode) {
        if (view != null) {
            // Stop the outgoing view's timer, or it repaints an invisible canvas forever.
            view.stop();
        }
        shownMode = mode;
        view = createView(mode);
        holder.getChildren().setAll(view == null ? unsupported(mode) : view);
        if (!drawing && view != null) {
            // A view starts its own idle timer as it is built, which is right for a view that is
            // about to be looked at and wrong for one built while the column is folded away or the
            // window is behind the companion strip. The mode can be cycled from the keyboard at any
            // time, so this is not a corner case.
            view.stop();
        }
    }

    /**
     * Builds the view that belongs to a mode.
     *
     * @param mode the mode to visualize
     * @return its view, or {@code null} if there is no view for it yet
     */
    private StructureView createView(PlaybackMode mode) {
        return switch (mode) {
            case ShuffleMode shuffle -> new CircuitView(shuffle, state, assets);
            case ArrivalOrderMode arrival -> new StraightView(arrival, state, assets);
            case AlphabeticalMode alphabetical -> new BstView(alphabetical, player, state, assets);
            default -> null;
        };
    }

    private Label unsupported(PlaybackMode mode) {
        Label label = new Label("No visualizer yet for\n" + mode.structureName());
        label.getStyleClass().add("visualizer-placeholder");
        label.setAlignment(Pos.CENTER);
        label.setWrapText(true);
        return label;
    }

    /** @return the view currently on screen, or {@code null} when the mode has none */
    public StructureView view() {
        return view;
    }

    /**
     * Picks the frame timer back up, for a visualizer coming back on screen.
     *
     * <p>The state is remembered rather than only forwarded, because the view underneath is
     * replaced whenever the playback mode changes and the replacement must inherit it - see
     * {@link #swapTo}.
     */
    public void start() {
        drawing = true;
        if (view != null) {
            view.start();
        }
    }

    /**
     * Releases the frame timer, for a visualizer being taken off screen - folded away with the
     * structure column, or hidden behind the companion window.
     */
    public void stop() {
        drawing = false;
        if (view != null) {
            view.stop();
        }
    }
}
