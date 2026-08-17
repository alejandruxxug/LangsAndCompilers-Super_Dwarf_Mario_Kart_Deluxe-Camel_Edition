package com.eia.superdwarfkart.mood;

/**
 * The properties every overlay layer has, whatever it draws.
 *
 * <p>Held as one record rather than as six components repeated across three layer kinds, so the
 * customizer's controls, the persistence and the opacity cap each exist once. A layer changes one
 * of these by producing a new style and a new layer - nothing here is mutable, for the same reason
 * {@link Palette} is not: the render loop reads these from a different thread's edits and must
 * never see half of one.
 *
 * <p><strong>The opacity cap is applied here, on construction, and that is deliberate.</strong>
 * Putting it in the customizer's slider would leave an imported {@code .mood.json} free to carry
 * an {@link ZBand#ABOVE_CONTENT} layer at full strength, which is exactly the file a teammate hands
 * over the day of the defence. A value that cannot be represented is better than a value that has
 * to be checked everywhere it is read.
 *
 * @param zBand   whether the layer is wallpaper or gauze
 * @param opacity how strongly it is drawn, 0 to 1, capped by the band
 * @param blend   how its pixels combine with what is under them
 * @param scrollX horizontal drift, in pixels per second; 0 is static
 * @param scrollY vertical drift, in pixels per second; 0 is static
 * @param visible per-layer switch, so a layer can be silenced without being deleted
 */
public record LayerStyle(ZBand zBand, double opacity, LayerBlend blend,
        double scrollX, double scrollY, boolean visible) {

    /** The fastest a layer may drift, in pixels per second, in either direction. */
    public static final double MAX_SCROLL = 400;

    /**
     * @throws NullPointerException if the band or the blend is missing
     */
    public LayerStyle {
        if (zBand == null) {
            zBand = ZBand.BEHIND_CONTENT;
        }
        if (blend == null) {
            blend = LayerBlend.NORMAL;
        }
        opacity = Math.clamp(opacity, 0d, zBand.maxOpacity());
        scrollX = Math.clamp(scrollX, -MAX_SCROLL, MAX_SCROLL);
        scrollY = Math.clamp(scrollY, -MAX_SCROLL, MAX_SCROLL);
    }

    /**
     * A static, fully opaque wallpaper layer - what a new layer starts as.
     *
     * @return the default style for a layer behind the content
     */
    public static LayerStyle behind() {
        return new LayerStyle(ZBand.BEHIND_CONTENT, 1.0, LayerBlend.NORMAL, 0, 0, true);
    }

    /**
     * A static gauze layer at the band's ceiling.
     *
     * @return the default style for a layer above the content
     */
    public static LayerStyle above() {
        return new LayerStyle(ZBand.ABOVE_CONTENT, ZBand.ABOVE_CONTENT.maxOpacity(),
                LayerBlend.NORMAL, 0, 0, true);
    }

    /**
     * Whether this layer's picture stands still.
     *
     * <p>The whole performance argument rests on this answer. A static layer is rendered once and
     * flattened into the cached backdrop with every other static layer, so a mood made only of
     * them costs <em>nothing per frame</em> - the canvas is painted when the mood changes and never
     * touched again. One drifting layer is what starts a timer.
     *
     * @return {@code true} when neither axis scrolls
     */
    public boolean isStatic() {
        return scrollX == 0 && scrollY == 0;
    }

    /**
     * Returns a copy with a different opacity, re-capped for the band.
     *
     * @param value the wanted opacity
     * @return the new style
     */
    public LayerStyle withOpacity(double value) {
        return new LayerStyle(zBand, value, blend, scrollX, scrollY, visible);
    }

    /**
     * Returns a copy in a different band.
     *
     * <p>Moving a layer up to {@link ZBand#ABOVE_CONTENT} re-applies the cap, so a wallpaper at
     * full strength quietly becomes a gauze at 0.35 rather than burying the interface.
     *
     * @param value the wanted band
     * @return the new style
     */
    public LayerStyle withBand(ZBand value) {
        return new LayerStyle(value, opacity, blend, scrollX, scrollY, visible);
    }

    /**
     * Returns a copy with a different blend mode.
     *
     * @param value the wanted blend
     * @return the new style
     */
    public LayerStyle withBlend(LayerBlend value) {
        return new LayerStyle(zBand, opacity, value, scrollX, scrollY, visible);
    }

    /**
     * Returns a copy drifting at a different rate.
     *
     * @param x horizontal drift, pixels per second
     * @param y vertical drift, pixels per second
     * @return the new style
     */
    public LayerStyle withScroll(double x, double y) {
        return new LayerStyle(zBand, opacity, blend, x, y, visible);
    }

    /**
     * Returns a copy shown or hidden.
     *
     * @param value whether the layer draws
     * @return the new style
     */
    public LayerStyle withVisible(boolean value) {
        return new LayerStyle(zBand, opacity, blend, scrollX, scrollY, value);
    }
}
