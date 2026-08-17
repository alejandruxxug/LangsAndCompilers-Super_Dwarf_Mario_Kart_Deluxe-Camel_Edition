package com.eia.superdwarfkart.mood;

/**
 * How a layer's pixels combine with what is already on the canvas.
 *
 * <p>These map one to one onto {@code javafx.scene.effect.BlendMode}, which is what
 * {@code GraphicsContext.setGlobalBlendMode} takes - but the mapping is made in {@code ui/} rather
 * than here. This package draws no pixels: it holds the <em>definition</em> of a look, and a
 * definition that named a toolkit enum could not be persisted, compared or tested without one.
 * That is the same reason {@code model/} carries {@code java.time.Duration} rather than the JavaFX
 * one.
 */
public enum LayerBlend {

    /** Ordinary source-over. The layer simply covers what is under it, at its own opacity. */
    NORMAL("Normal"),

    /** Darkens: useful for a vignette or a dusk wash that must not lighten anything. */
    MULTIPLY("Multiply"),

    /** Lightens: what a starfield or a haze wants, so the ground beneath it still shows through. */
    SCREEN("Screen"),

    /** Multiplies the darks and screens the lights, so contrast goes up without the hue moving. */
    OVERLAY("Overlay"),

    /** Adds. The strongest of the five, and the one an {@link ZBand#ABOVE_CONTENT} cap is for. */
    ADD("Add");

    private final String displayName;

    LayerBlend(String displayName) {
        this.displayName = displayName;
    }

    /** @return the caption shown in the customizer */
    public String displayName() {
        return displayName;
    }

    /**
     * Reads a stored blend name, tolerating anything a later version might have written.
     *
     * @param name the stored name; {@code null} or unknown yields {@link #NORMAL}
     * @return the blend mode
     */
    public static LayerBlend byName(String name) {
        if (name != null) {
            for (LayerBlend blend : values()) {
                if (blend.name().equalsIgnoreCase(name.strip())) {
                    return blend;
                }
            }
        }
        return NORMAL;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
