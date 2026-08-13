package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders a {@link Palette} as a stylesheet, so the controls follow the mood the canvases already
 * follow.
 *
 * <p>Ground rule 7 says every colour resolves through a role by name, and until this class existed
 * the stylesheet was the one place that did not: sixty hexadecimal literals, which would have left
 * a mood switch restyling the road, the meters and the tree while every button, table and dialog
 * stayed exactly as it was. A half-restyled application is worse than an unstyled one, because it
 * looks like the feature is broken rather than absent.
 *
 * <p><strong>This is a stylesheet, not an inline style, and that is not a detail.</strong> An
 * inline style on the scene's root would be the shorter way to install looked-up colours and it
 * would silently miss every popup: a tooltip and a combo box's drop-down are their own scenes with
 * their own roots, so a definition on the main root never reaches them and every colour in them
 * would fail to resolve. A stylesheet is copied to popup scenes the same way {@code app.css}
 * already is, so the two cannot diverge.
 *
 * <h2>The derived tokens</h2>
 *
 * <p>A palette holds exactly sixteen colours (the GBA's 4bpp tile addresses one bank of sixteen)
 * but an interface of beveled blocks needs more than sixteen <em>surfaces</em>: a face, its lit
 * edge, its shadowed edge, its hover, its pressed and its disabled state. Those are not extra
 * palette entries and must never become any - they are transformations of a role, computed here
 * through {@link Palette#mix}, {@link Palette#shaded} and {@link Palette#tinted}.
 *
 * <p>Deriving them in Java rather than with the CSS {@code derive()} function buys the one thing
 * that matters: they can be tested. {@code PaletteCssTest} asserts that a bevel's lit edge is
 * brighter than its face and its shadowed edge darker <em>in every built-in mood</em>, which is
 * precisely the property a light palette breaks - and a broken bevel is invisible to a unit test
 * that only checks the colours were emitted.
 */
public final class PaletteCss {

    /** Prefix for the sixteen role colours, one per {@link PaletteRole}. */
    public static final String ROLE_PREFIX = "-role-";

    /** Prefix for the surfaces derived from those roles. */
    public static final String DERIVED_PREFIX = "-ui-";

    private PaletteCss() {
        throw new AssertionError("PaletteCss is a utility holder and must not be instantiated");
    }

    /**
     * Returns the looked-up colour name a role is published under.
     *
     * <p>Derived from the constant rather than written down, so a role added to the enum cannot be
     * left out of the stylesheet by omission.
     *
     * @param role the role to name; must not be {@code null}
     * @return the CSS variable name, such as {@code -role-background-alt}
     */
    public static String variableName(PaletteRole role) {
        return ROLE_PREFIX + role.name().toLowerCase().replace('_', '-');
    }

    /**
     * Computes every colour a stylesheet may name: the sixteen roles and the surfaces derived
     * from them.
     *
     * @param palette the palette to resolve against; must not be {@code null}
     * @return variable name to colour, in declaration order
     */
    public static Map<String, Color> tokens(Palette palette) {
        Map<String, Color> tokens = new LinkedHashMap<>();
        for (PaletteRole role : PaletteRole.values()) {
            tokens.put(variableName(role), palette.color(role));
        }

        // Recessed ground: headers, status bars, tooltips, sunken fields and scroll bars. A short
        // step towards the shadow rather than most of the way there, because "recessed" in a light
        // mood means a shade below the paper - not the mid-grey a larger step lands on.
        tokens.put(DERIVED_PREFIX + "recessed",
                palette.mix(PaletteRole.BACKGROUND, PaletteRole.SHADOW, 0.18));
        // The unlit cell of a meter, and any well a value is drawn into.
        tokens.put(DERIVED_PREFIX + "inset",
                palette.mix(PaletteRole.SURFACE, PaletteRole.SURFACE_RAISED, 0.6));

        // A control's face through its states. Pressed goes a long way towards the shadow so that
        // it stays clearly darker than hover: in a dark mood hover lightens the face and the two
        // separate on their own, but in a light mood hover darkens it too and a short step here
        // put pressed and hover within a couple of levels of each other.
        tokens.put(DERIVED_PREFIX + "face-hover",
                palette.mix(PaletteRole.SURFACE_RAISED, PaletteRole.OUTLINE, 0.55));
        tokens.put(DERIVED_PREFIX + "face-pressed",
                palette.mix(PaletteRole.SURFACE_RAISED, PaletteRole.SHADOW, 0.42));
        tokens.put(DERIVED_PREFIX + "face-disabled",
                palette.mix(PaletteRole.SURFACE, PaletteRole.SHADOW, 0.25));
        // Selection is built from the outline rather than from the face. Lightening the face marks
        // the selected row in a dark mood and washes it out in a light one, where the face is
        // already near white and has nowhere brighter to go.
        tokens.put(DERIVED_PREFIX + "selected", palette.shaded(PaletteRole.OUTLINE, 1.3));

        // The bevel: lit on the top-left, shadowed on the bottom-right. Scaled rather than mixed,
        // so the edges stay on the right sides of the face in a light mood as well as a dark one.
        tokens.put(DERIVED_PREFIX + "bevel-light", palette.shaded(PaletteRole.SURFACE_RAISED, 1.9));
        tokens.put(DERIVED_PREFIX + "bevel-dark", palette.shaded(PaletteRole.SURFACE_RAISED, 0.45));
        tokens.put(DERIVED_PREFIX + "groove-light", palette.shaded(PaletteRole.OUTLINE, 1.4));

        // Text quieter than TEXT_DIM, for captions and for disabled controls.
        tokens.put(DERIVED_PREFIX + "text-faint",
                palette.mix(PaletteRole.TEXT_DIM, PaletteRole.SURFACE, 0.45));
        tokens.put(DERIVED_PREFIX + "text-disabled",
                palette.mix(PaletteRole.TEXT_DIM, PaletteRole.SURFACE, 0.72));

        // Lit top edges for the rating meter's three bands and the slider thumb. Tinted rather
        // than shaded because PRIMARY is already at full brightness and would not move.
        tokens.put(DERIVED_PREFIX + "primary-light", palette.tinted(PaletteRole.PRIMARY, 0.55));
        tokens.put(DERIVED_PREFIX + "positive-light", palette.tinted(PaletteRole.POSITIVE, 0.55));
        tokens.put(DERIVED_PREFIX + "negative-light", palette.tinted(PaletteRole.NEGATIVE, 0.55));
        tokens.put(DERIVED_PREFIX + "negative-dark", palette.shaded(PaletteRole.NEGATIVE, 0.72));

        return tokens;
    }

    /**
     * Renders a palette as a complete stylesheet defining every colour {@code app.css} names.
     *
     * @param palette the palette to render; must not be {@code null}
     * @return the stylesheet source
     */
    public static String stylesheet(Palette palette) {
        StringBuilder css = new StringBuilder(1024);
        css.append("/* Generated from the active mood - see mood/PaletteCss. */\n");
        css.append(".root {\n");
        tokens(palette).forEach((name, color) ->
                css.append("    ").append(name).append(": ").append(GbaColor.toHex(color))
                        .append(";\n"));
        css.append("}\n");
        return css.toString();
    }

    /**
     * Renders a palette as a stylesheet URL a scene can be given directly.
     *
     * <p>A {@code data:} URL rather than a file: the palette changes whenever the mood does, and
     * writing a stylesheet to disk on every switch - then relying on the toolkit to notice it had
     * changed - is a cache-invalidation problem this does not need to have. The URL carries the
     * whole stylesheet, so a different palette is a different URL by construction.
     *
     * @param palette the palette to render; must not be {@code null}
     * @return a {@code data:text/css;base64,...} URL
     */
    public static String stylesheetUrl(Palette palette) {
        String encoded = Base64.getEncoder()
                .encodeToString(stylesheet(palette).getBytes(StandardCharsets.UTF_8));
        return "data:text/css;base64," + encoded;
    }
}
