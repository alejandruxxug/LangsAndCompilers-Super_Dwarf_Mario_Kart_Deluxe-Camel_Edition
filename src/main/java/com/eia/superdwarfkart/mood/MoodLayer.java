package com.eia.superdwarfkart.mood;

/**
 * One entry in a mood's overlay stack.
 *
 * <p>Sealed, and the three permitted kinds are the whole set: something generated from the palette
 * ({@link GradientLayer}), something drawn or imported ({@link ImageLayer}), and something computed
 * from a formula ({@link ProceduralLayer}). Sealing it is what lets the renderer and the customizer
 * switch over the kinds exhaustively - a fourth kind added later fails to compile in both places
 * rather than silently drawing nothing in one of them.
 *
 * <p>Every layer is immutable. Editing one in the customizer produces a new layer, and installing a
 * mood replaces the whole list at once, so the render thread never reads a half-applied edit.
 *
 * <p><strong>A layer describes itself; it does not draw itself.</strong> Nothing in this package
 * takes a {@code GraphicsContext} or knows a canvas exists - {@code ui/MoodOverlayRenderer} reads
 * these definitions and rasterises them. That is what keeps a mood loadable, comparable and
 * testable with no window open, which is how {@code MoodRepository} is tested at all.
 */
public sealed interface MoodLayer permits GradientLayer, ImageLayer, ProceduralLayer {

    /** Most layers a mood may hold. */
    int MAX_LAYERS = 6;

    /** @return the properties every layer has: band, opacity, blend, scroll and visibility */
    LayerStyle style();

    /**
     * Returns this layer with different shared properties.
     *
     * @param style the properties to use; must not be {@code null}
     * @return a new layer of the same kind
     */
    MoodLayer withStyle(LayerStyle style);

    /**
     * A one-line summary for the customizer's layer list.
     *
     * @return what this layer is, in a few words
     */
    String describe();

    /**
     * Whether this layer's own picture changes over time, ignoring any scrolling.
     *
     * <p>Separate from {@link LayerStyle#isStatic()} because the two have different causes: a layer
     * scrolls because the user asked it to, and an animated GIF moves because of what was imported.
     * Both start the render timer; only the second stops the picture being cacheable.
     *
     * @return {@code true} when the layer has to be re-rasterised per frame
     */
    default boolean isAnimated() {
        return false;
    }

    /**
     * Whether this layer needs redrawing every frame for any reason at all.
     *
     * @return {@code true} when it drifts or animates
     */
    default boolean isLive() {
        return style().visible() && (!style().isStatic() || isAnimated());
    }
}
