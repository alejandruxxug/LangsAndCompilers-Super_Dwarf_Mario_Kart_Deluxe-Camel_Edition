package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The mode selector and transport controls.
 *
 * <p>Choosing a mode here replaces the {@link PlaybackMode} object inside the {@link Player} and
 * nothing else - the player does not learn which mode it now holds, and neither does this bar
 * beyond building the right one. Everything that follows from the choice, including whether the
 * previous control works, is read back off the interface.
 *
 * <p><strong>Previous is disabled in arrival order rather than allowed to fail.</strong> A queue
 * cannot go backwards; the control says so, with a tooltip that explains why, instead of
 * throwing at whoever presses it.
 */
public class PlaybackBar extends VBox {

    /** Characters of the now-playing line before it is shortened. */
    private static final int NOW_PLAYING_LIMIT = 46;

    /** Characters of the up-next line before it is shortened. */
    private static final int UP_NEXT_LIMIT = 34;

    private final Player player;

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final Map<ModeId, ToggleButton> modeButtons = new EnumMap<>(ModeId.class);

    private final Button previousButton = new Button("<< PREV");
    private final Button nextButton = new Button("NEXT >>");
    private final HBox previousHolder = new HBox(previousButton);

    private final Label nowPlaying = new Label();
    private final Label upNext = new Label();

    /** Builders for the three modes, so selecting one constructs it fresh from the library. */
    private final Map<ModeId, Supplier<PlaybackMode>> factories = new EnumMap<>(ModeId.class);

    /**
     * Builds the bar.
     *
     * @param player the player to drive
     */
    public PlaybackBar(Player player) {
        super(10);
        this.player = player;

        factories.put(ModeId.SHUFFLE, ShuffleMode::new);
        factories.put(ModeId.ARRIVAL_ORDER, ArrivalOrderMode::new);
        factories.put(ModeId.ALPHABETICAL, AlphabeticalMode::new);

        getStyleClass().add("playback-bar");
        setPadding(new Insets(12, 16, 12, 16));

        getChildren().addAll(buildTopRow(), buildNowPlayingRow());

        player.addListener((mode, song) -> refresh());
        refresh();
    }

    private HBox buildTopRow() {
        Label modeCaption = new Label("MODE");
        modeCaption.getStyleClass().add("bar-caption");

        HBox modes = new HBox(6);
        modes.setAlignment(Pos.CENTER_LEFT);
        for (ModeId id : ModeId.values()) {
            modes.getChildren().add(modeButton(id));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        previousButton.setOnAction(e -> player.previous());
        nextButton.setOnAction(e -> player.next());
        // A disabled node receives no mouse events, so the tooltip explaining why goes on the
        // holder around it. Without this the control is dead and unexplained.
        previousHolder.setAlignment(Pos.CENTER);

        HBox transport = new HBox(8, previousHolder, nextButton);
        transport.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(14, modeCaption, modes, spacer, transport);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Builds one mode toggle.
     *
     * @param id the mode it selects
     * @return the toggle button
     */
    private ToggleButton modeButton(ModeId id) {
        ToggleButton button = new ToggleButton(id.displayName().toUpperCase());
        button.setToggleGroup(modeGroup);
        button.getStyleClass().add("mode-button");
        button.setTooltip(new Tooltip(id.displayName() + "\nBacked by " + id.structureName()
                + (id.supportsPrevious() ? "" : "\nFIFO - no going back.")));
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                // Clicking the active mode must not deselect it and leave nothing chosen.
                button.setSelected(true);
                return;
            }
            player.setMode(factories.get(id).get());
        });
        modeButtons.put(id, button);
        return button;
    }

    private HBox buildNowPlayingRow() {
        Label nowCaption = new Label("NOW PLAYING");
        nowCaption.getStyleClass().add("bar-caption");
        nowPlaying.getStyleClass().add("now-playing");

        Label nextCaption = new Label("UP NEXT");
        nextCaption.getStyleClass().add("bar-caption");
        upNext.getStyleClass().add("up-next");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, nowCaption, nowPlaying, spacer, nextCaption, upNext);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Brings the bar back in step with the player.
     *
     * <p>Reads capability off the mode rather than checking which mode it is: that is the same
     * polymorphism the player relies on, applied to the controls.
     */
    public final void refresh() {
        PlaybackMode mode = player.mode();

        ToggleButton active = modeButtons.get(mode.id());
        if (active != null && !active.isSelected()) {
            active.setSelected(true);
        }

        boolean canGoBack = player.canGoPrevious();
        previousButton.setDisable(!canGoBack);
        Tooltip.install(previousHolder, new Tooltip(mode.supportsPrevious()
                ? "Step back through " + mode.structureName()
                : "FIFO - no going back."));

        nextButton.setDisable(!player.canGoNext());

        Song current = player.current();
        nowPlaying.setText(current == null
                ? "-"
                : LibraryView.ellipsize(current.getTitle() + " - " + current.getArtist(),
                        NOW_PLAYING_LIMIT));

        Song upcoming = player.peekNext();
        upNext.setText(upcoming == null
                ? "-"
                : LibraryView.ellipsize(upcoming.getTitle(), UP_NEXT_LIMIT));
    }
}
