package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A saved look: sixteen colours, and an ordered stack of overlay layers.
 *
 * <p>Selecting one restyles the whole application at once - the canvases because they resolve every
 * colour through {@link Palette#active()}, the controls because {@link PaletteCss} publishes the
 * same palette as a stylesheet, and the fullscreen backdrop because
 * {@code ui/MoodOverlayRenderer} draws the layers.
 *
 * <p>The two halves have deliberately different scope. <strong>The palette applies everywhere</strong>
 * - both windows, every table, meter, dialog and canvas. <strong>The layers apply to the fullscreen
 * window only.</strong> The companion strip is a 224-pixel transparent card that is on screen for
 * whole albums at a time; parallax artwork there is invisible noise costing framerate on the one
 * window that is always up.
 *
 * <p>This is also where the assignment's dark-mode bonus lives, and it ships as moods rather than as
 * a boolean on purpose: a switch between two hard-coded themes is one feature, and a named look that
 * the palette is a value of is the same work with room for the other eighteen.
 *
 * @param id          stable identifier, used for persistence and for the folder name
 * @param displayName the name shown in the switcher
 * @param palette     the sixteen colours this mood draws with
 * @param layers      the overlay stack, drawn in order; empty for a mood that is only a palette
 * @param tiles       tiles drawn in the pixel editor, by name, stored as palette indices
 * @param reactive    whether layers respond to the music - see {@link MoodReactivity}
 */
public record Mood(String id, String displayName, Palette palette, List<MoodLayer> layers,
        Map<String, PixelTile> tiles, boolean reactive) {

    /**
     * @throws IllegalArgumentException if the id or name is blank, or there are too many layers
     * @throws NullPointerException     if the palette is missing
     */
    public Mood {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(palette, "palette must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("A mood's id must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("A mood's display name must not be blank");
        }
        layers = layers == null ? List.of() : List.copyOf(layers);
        if (layers.size() > MoodLayer.MAX_LAYERS) {
            throw new IllegalArgumentException("A mood holds at most " + MoodLayer.MAX_LAYERS
                    + " layers, not " + layers.size() + ". Six of them redrawn individually at "
                    + "60 fps is how this feature quietly costs the framerate the rest of the "
                    + "project exists to show off.");
        }
        tiles = tiles == null ? Map.of() : Map.copyOf(tiles);
    }

    /**
     * A mood that is only a palette - what the two built-ins were before the layers existed.
     *
     * @param id          stable identifier
     * @param displayName the name shown in the switcher
     * @param palette     the sixteen colours
     */
    public Mood(String id, String displayName, Palette palette) {
        this(id, displayName, palette, List.of(), Map.of(), false);
    }

    /**
     * A mood with a layer stack and no hand-drawn tiles - what every preset is.
     *
     * @param id          stable identifier
     * @param displayName the name shown in the switcher
     * @param palette     the sixteen colours
     * @param layers      the overlay stack
     */
    public Mood(String id, String displayName, Palette palette, List<MoodLayer> layers) {
        this(id, displayName, palette, layers, Map.of(), false);
    }

    /**
     * Returns the colour playing a role in this mood.
     *
     * @param role the role to resolve; must not be {@code null}
     * @return the colour for that role
     */
    public Color color(PaletteRole role) {
        return palette.color(role);
    }

    /**
     * Returns a copy with a different palette.
     *
     * @param newPalette the palette to use; must not be {@code null}
     * @return the new mood
     */
    public Mood withPalette(Palette newPalette) {
        return new Mood(id, displayName, newPalette, layers, tiles, reactive);
    }

    /**
     * Returns a copy with a different layer stack.
     *
     * @param newLayers the layers, in draw order
     * @return the new mood
     */
    public Mood withLayers(List<MoodLayer> newLayers) {
        return new Mood(id, displayName, palette, newLayers, tiles, reactive);
    }

    /**
     * Returns a copy with one layer replaced.
     *
     * @param index which layer
     * @param layer what to put there
     * @return the new mood, or this one when the index is outside the stack
     */
    public Mood withLayer(int index, MoodLayer layer) {
        if (index < 0 || index >= layers.size()) {
            return this;
        }
        List<MoodLayer> copy = new ArrayList<>(layers);
        copy.set(index, layer);
        return withLayers(copy);
    }

    /**
     * Returns a copy with one more layer on top.
     *
     * @param layer the layer to add
     * @return the new mood
     * @throws IllegalArgumentException if the mood is already full
     */
    public Mood withLayerAdded(MoodLayer layer) {
        List<MoodLayer> copy = new ArrayList<>(layers);
        copy.add(layer);
        return withLayers(copy);
    }

    /**
     * Returns a copy with one layer taken out.
     *
     * @param index which layer
     * @return the new mood, or this one when the index is outside the stack
     */
    public Mood withLayerRemoved(int index) {
        if (index < 0 || index >= layers.size()) {
            return this;
        }
        List<MoodLayer> copy = new ArrayList<>(layers);
        copy.remove(index);
        return withLayers(copy);
    }

    /**
     * Returns a copy with one layer moved up or down the stack.
     *
     * <p>Order is what a stack of layers <em>is</em>, so this is the one edit in the customizer
     * that changes nothing about any layer and everything about the result.
     *
     * @param index which layer
     * @param delta -1 to move it earlier, 1 to move it later
     * @return the new mood, or this one when the move would leave the stack
     */
    public Mood withLayerMoved(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= layers.size() || target < 0 || target >= layers.size()) {
            return this;
        }
        List<MoodLayer> copy = new ArrayList<>(layers);
        copy.add(target, copy.remove(index));
        return withLayers(copy);
    }

    /**
     * Returns a copy holding one more tile, or replacing the one of that name.
     *
     * @param name the tile's name, which is also what an {@link ImageLayer} refers to it by
     * @param tile the tile
     * @return the new mood
     */
    public Mood withTile(String name, PixelTile tile) {
        Map<String, PixelTile> copy = new LinkedHashMap<>(tiles);
        copy.put(name, tile);
        return new Mood(id, displayName, palette, layers, copy, reactive);
    }

    /**
     * Looks up a tile by the name an {@link ImageLayer} refers to it by.
     *
     * @param name the tile's name
     * @return the tile, or {@code null} when this mood has none of that name
     */
    public PixelTile tile(String name) {
        return tiles.get(name);
    }

    /**
     * Returns a copy that does or does not respond to the music.
     *
     * @param on whether layers react
     * @return the new mood
     */
    public Mood withReactive(boolean on) {
        return new Mood(id, displayName, palette, layers, tiles, on);
    }

    /**
     * Returns a copy under a new identity, for duplicate-then-edit.
     *
     * @param newId          the new identifier
     * @param newDisplayName the new name
     * @return the new mood
     */
    public Mood copyAs(String newId, String newDisplayName) {
        return new Mood(newId, newDisplayName, palette, layers, tiles, reactive);
    }

    /**
     * Returns a copy renamed, keeping its identity.
     *
     * @param newDisplayName the new name
     * @return the new mood
     */
    public Mood renamed(String newDisplayName) {
        return new Mood(id, newDisplayName, palette, layers, tiles, reactive);
    }

    /**
     * Whether anything in this mood has to be redrawn per frame.
     *
     * <p>The whole performance case rests on this being {@code false} for most moods: a mood whose
     * layers are all static is rasterised once and never touched again, so it costs nothing at all
     * while the game runs.
     *
     * @return {@code true} when a layer drifts, animates, or the mood reacts to the music
     */
    public boolean needsAnimation() {
        if (reactive) {
            return true;
        }
        return layers.stream().anyMatch(MoodLayer::isLive);
    }

    /** @return the layers actually drawn, in order - visible ones only */
    public List<MoodLayer> visibleLayers() {
        return layers.stream().filter(layer -> layer.style().visible()).toList();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
