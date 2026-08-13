package com.eia.superdwarfkart.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;

/**
 * The strip down the left edge that chooses what the middle of the window shows.
 *
 * <p>One button per {@link Destination}, in a toggle group so exactly one is always lit. The
 * selection is published as a property rather than through a callback, so the window binds to it
 * the same way it binds to everything else in {@code AppState}.
 *
 * <p><strong>Nothing here takes keyboard focus.</strong> A focused toggle button answers the space
 * bar, and space is play/pause - so a rail that could be focused would have the first space of the
 * session change destination instead of starting the music. That is the same fault the header's two
 * toggles were fixed for, and the rail sits even earlier in the scene. The interface is
 * mouse-driven and {@code Tab} is bound to the playback mode application-wide, so focus buys these
 * buttons nothing at all.
 */
public class SideRail extends VBox {

    /**
     * The rail's width, in pixels.
     *
     * <p>Set by the longest caption rather than chosen: the interface runs in a fixed-width pixel
     * font at about one em per character, so {@code FAVORITES} at 8px is 72 pixels of text, and the
     * rest is the button's padding and the bevel on both sides.
     */
    public static final double RAIL_WIDTH = 104;

    private static final double BUTTON_HEIGHT = 44;

    private final ObjectProperty<Destination> destination =
            new SimpleObjectProperty<>(this, "destination", Destination.defaultDestination());

    private final Map<Destination, ToggleButton> buttons = new EnumMap<>(Destination.class);

    /** Builds the rail with one button per destination. */
    public SideRail() {
        getStyleClass().add("side-rail");
        setMinWidth(RAIL_WIDTH);
        setPrefWidth(RAIL_WIDTH);
        setMaxWidth(RAIL_WIDTH);

        Label heading = new Label("GO TO");
        heading.getStyleClass().add("rail-heading");
        getChildren().add(heading);

        ToggleGroup group = new ToggleGroup();
        for (Destination target : Destination.values()) {
            ToggleButton button = new ToggleButton(target.label());
            button.getStyleClass().add("rail-button");
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(BUTTON_HEIGHT);
            button.setPrefHeight(BUTTON_HEIGHT);
            // See the class comment: a focused toggle would eat the space bar.
            button.setFocusTraversable(false);

            Tooltip tooltip = new Tooltip(target.description());
            tooltip.setShowDelay(Duration.millis(400));
            button.setTooltip(tooltip);

            button.setOnAction(event -> select(target));
            buttons.put(target, button);
            getChildren().add(button);
        }

        select(Destination.defaultDestination());
    }

    /**
     * The destination currently selected.
     *
     * @return the selection, never {@code null}
     */
    public ObjectProperty<Destination> destinationProperty() {
        return destination;
    }

    /** @return the destination currently selected */
    public Destination getDestination() {
        return destination.get();
    }

    /**
     * Selects a destination, lighting its button.
     *
     * <p>A toggle group lets its selected button be clicked again and switched off, which would
     * leave the rail showing nothing selected while the window still showed something. Re-lighting
     * the button unconditionally is what stops that.
     *
     * @param target the destination to show; {@code null} restores the default
     */
    public final void select(Destination target) {
        Destination wanted = target == null ? Destination.defaultDestination() : target;
        buttons.get(wanted).setSelected(true);
        destination.set(wanted);
    }
}
