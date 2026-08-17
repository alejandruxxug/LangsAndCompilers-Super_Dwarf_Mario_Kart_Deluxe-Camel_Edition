package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a handful of chosen colours into sixteen roles that every check in this project passes.
 *
 * <p><strong>This class is the reason twenty moods can ship instead of three.</strong> Picking
 * sixteen colours by eye takes an hour and usually comes out muddy - and worse, it comes out
 * <em>plausible</em>: a palette that looks lovely in a swatch strip and renders its section
 * headings at 2:1 against the one band they are actually drawn on. That happened to the light mood
 * on its first pass and it is invisible until somebody opens that mood and tries to read it.
 *
 * <p>So a preset names the colours that make it recognisable - the room it is set in, and the two
 * brand colours - and everything else is derived and then <em>forced</em> to clear the same bars
 * {@code MoodsTest} holds the built-ins to:
 *
 * <ul>
 *   <li>body text readable on the background and on the panels;</li>
 *   <li>{@link PaletteRole#PRIMARY} and {@link PaletteRole#ACCENT} readable <em>as text</em>,
 *       including on the recessed band, which is where an accent runs out of contrast first;</li>
 *   <li>the two protected pairs separated in Lab, and coins and obstacles separated in brightness
 *       as well as in hue;</li>
 *   <li>the bevel's lit edge above its face and its shadowed edge below it, the selected row
 *       distinguishable from an unselected one, and a pressed control darker than a hovered one -
 *       the three derivations in {@link PaletteCss} that a badly-chosen palette silently flattens;</li>
 *   <li>no colour used for two roles, which would make two roles indistinguishable by
 *       construction.</li>
 * </ul>
 *
 * <p>The corrections are lightness moves, never hue moves. Hue is what a mood <em>is</em> - the
 * whole difference between Sunset Wilds and Boo Lake - and a fixer that recoloured one into the
 * other would be an intrusion rather than a repair.
 *
 * <p>None of this is a substitute for {@link MoodValidator}: that runs on what the user builds,
 * where this runs on what the application ships. They enforce the same thresholds because a preset
 * that could not pass the validator would be the application holding itself to a lower standard
 * than its users.
 */
public final class PaletteBuilder {

    /**
     * Brightest a control face may be, leaving the bevel's lit edge somewhere to go.
     *
     * <p>{@code Palette.shaded} scales brightness, and a face already at 1.0 does not move - so the
     * lit edge lands on the face's own colour and every button in the application reads as flat.
     * Nothing reports it; the interface simply stops looking like an interface.
     */
    private static final double MAX_FACE_BRIGHTNESS = 0.92;

    /** Darkest a control face may be, leaving the shadowed edge somewhere to go. */
    private static final double MIN_FACE_BRIGHTNESS = 0.10;

    /** How different a selected row has to be from an unselected one to read as selected. */
    private static final double MIN_SELECTION_DISTANCE = 0.16;

    /** How much darker a pressed control has to be than a hovered one. */
    private static final double MIN_PRESS_GAP = 0.06;

    /** Contrast body text needs. The same figure the validator enforces. */
    private static final double TEXT_CONTRAST = MoodValidator.MIN_TEXT_CONTRAST;

    /** Contrast a secondary label needs: readable, but visibly quieter than body text. */
    private static final double DIM_CONTRAST = 3.0;

    private PaletteBuilder() {
        throw new AssertionError("PaletteBuilder is a utility holder and must not be instantiated");
    }

    /**
     * Builds a complete, checked palette from however many roles a recipe names.
     *
     * <p>Roles left out are derived from the ones present, so a preset can be four lines long. Roles
     * that are named are honoured unless they break one of the rules above, in which case they are
     * moved in lightness and no further.
     *
     * @param name the palette's name
     * @param seed the roles the recipe names; may be partial, must name at least
     *             {@link PaletteRole#BACKGROUND}
     * @return a palette that passes every check
     */
    public static Palette build(String name, Map<PaletteRole, Color> seed) {
        Map<PaletteRole, Color> colors = fillGaps(seed);
        harmonize(colors);
        Palette palette = MoodValidator.repair(new Palette(name, colors));
        // Uniqueness last, because the repair above may have moved a role onto another one; and
        // then repaired again, because a nudge for uniqueness is small and the second pass is a
        // no-op whenever the first one left nothing to do.
        return MoodValidator.repair(deduplicate(palette));
    }

    /**
     * Fills in whatever the recipe did not name.
     *
     * <p>Everything here is derived from the room rather than invented: the alternating row is the
     * background nudged, the panel is the background lifted, the shadow is the background driven
     * down. A recipe that names only a background therefore still produces a coherent palette, and
     * a recipe that names all sixteen is passed straight through.
     */
    private static Map<PaletteRole, Color> fillGaps(Map<PaletteRole, Color> seed) {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        colors.putAll(seed);

        Color background = colors.computeIfAbsent(PaletteRole.BACKGROUND,
                role -> GbaColor.web("#12121c"));
        boolean light = !ColorMath.isDark(background);
        // Which way "up" is. Everything below moves away from the background, and in a light room
        // that means downwards.
        double lift = light ? -1 : 1;

        colors.computeIfAbsent(PaletteRole.SHADOW,
                role -> ColorMath.shiftLightness(background, light ? -0.72 : -0.55));
        colors.computeIfAbsent(PaletteRole.BACKGROUND_ALT,
                role -> ColorMath.shiftLightness(background, lift * 0.14));
        colors.computeIfAbsent(PaletteRole.SURFACE,
                role -> ColorMath.shiftLightness(background, lift * 0.07));
        colors.computeIfAbsent(PaletteRole.SURFACE_RAISED,
                role -> ColorMath.shiftLightness(background, lift * 0.22));
        colors.computeIfAbsent(PaletteRole.OUTLINE,
                role -> ColorMath.shiftLightness(background, lift * 0.42));

        colors.computeIfAbsent(PaletteRole.TEXT_PRIMARY,
                role -> light ? GbaColor.web("#141414") : GbaColor.web("#f0f0f0"));
        colors.computeIfAbsent(PaletteRole.TEXT_DIM, role -> ColorMath.shiftLightness(
                colors.get(PaletteRole.TEXT_PRIMARY), light ? 0.42 : -0.38));

        colors.computeIfAbsent(PaletteRole.PRIMARY, role -> GbaColor.web("#ffd23f"));
        colors.computeIfAbsent(PaletteRole.PRIMARY_DIM, role -> ColorMath.shiftLightness(
                colors.get(PaletteRole.PRIMARY), light ? 0.32 : -0.42));
        colors.computeIfAbsent(PaletteRole.ACCENT, role -> GbaColor.web("#6ee7ff"));

        // The meter runs from the accent up to the primary, so a bar peaking reads as it filling
        // with the colour the progress bar is already drawn in. The bottom is the accent moved a
        // step rather than the accent itself: two roles the same colour is what `deduplicate` has
        // to undo, and it is cheaper not to create the collision.
        colors.computeIfAbsent(PaletteRole.METER_LOW, role ->
                ColorMath.shiftLightness(colors.get(PaletteRole.ACCENT), light ? 0.18 : -0.18));
        colors.computeIfAbsent(PaletteRole.METER_HIGH, role -> colors.get(PaletteRole.PRIMARY));

        // Coins and obstacles start from a green and a red that read on this room, rather than from
        // the same two whatever the mood. In a light room both have to be dark enough to be seen on
        // paper; in a dark one both have to be bright enough to be seen at all.
        colors.computeIfAbsent(PaletteRole.POSITIVE,
                role -> light ? GbaColor.web("#187818") : GbaColor.web("#5ce65c"));
        colors.computeIfAbsent(PaletteRole.NEGATIVE,
                role -> light ? GbaColor.web("#b81818") : GbaColor.web("#ff4d5a"));
        colors.computeIfAbsent(PaletteRole.HIGHLIGHT,
                role -> light ? GbaColor.web("#b000b0") : GbaColor.web("#ff5edb"));

        return colors;
    }

    /**
     * Moves whatever has to move, in lightness only.
     *
     * <p>Order is load-bearing. The face is clamped first because the bevel derives from it; the
     * shadow is settled next because the recessed band and the pressed state both derive from it;
     * and only then is anything checked <em>against</em> those grounds. Checking first and then
     * moving the ground underneath is how a palette passes its own harmonisation and fails the
     * test.
     */
    private static void harmonize(Map<PaletteRole, Color> colors) {
        // 1. The control face needs headroom in both directions or the bevel disappears.
        colors.put(PaletteRole.SURFACE_RAISED,
                clampBrightness(colors.get(PaletteRole.SURFACE_RAISED)));

        // 2. The shadow has to be dark enough that a pressed control is visibly darker than a
        //    hovered one. In a dark mood hover lightens the face and the two separate on their own;
        //    in a light one hover darkens it too, and they land on top of each other.
        colors.put(PaletteRole.SHADOW, darkEnoughForPress(colors));

        // 3. The outline has to be far enough from the panel that a selected row reads as selected.
        colors.put(PaletteRole.OUTLINE, awayFromSurface(colors));

        Color background = colors.get(PaletteRole.BACKGROUND);
        Color surface = colors.get(PaletteRole.SURFACE);
        Color recessed = recessed(colors);

        // 4. Body text, against every ground it is set on.
        colors.put(PaletteRole.TEXT_PRIMARY, ColorMath.forContrast(
                colors.get(PaletteRole.TEXT_PRIMARY),
                List.of(background, surface, colors.get(PaletteRole.SURFACE_RAISED)),
                TEXT_CONTRAST));

        // 5. The two brand colours, which are read as often as they are looked at: the application
        //    name, the table headings, the now-playing line, every section heading. The recessed
        //    band is in the list because it is where they run out of contrast first.
        for (PaletteRole role : List.of(PaletteRole.PRIMARY, PaletteRole.ACCENT)) {
            colors.put(role, ColorMath.forContrast(colors.get(role),
                    List.of(background, surface, recessed), TEXT_CONTRAST));
        }

        // 6. Secondary labels: readable, and then pushed back towards the surface if the fix
        //    overshot into being as loud as body text.
        colors.put(PaletteRole.TEXT_DIM, ColorMath.forContrast(
                colors.get(PaletteRole.TEXT_DIM), List.of(surface), DIM_CONTRAST));

        // 7. The disabled variant has to stay visibly quieter than the live one.
        colors.put(PaletteRole.PRIMARY_DIM,
                quieterThan(colors.get(PaletteRole.PRIMARY_DIM), colors.get(PaletteRole.PRIMARY),
                        surface));
    }

    /** Keeps a face away from both ends, so its lit and shadowed edges both have somewhere to go. */
    private static Color clampBrightness(Color face) {
        double brightness = face.getBrightness();
        if (brightness > MAX_FACE_BRIGHTNESS) {
            return ColorMath.shiftLightness(face, -(brightness - MAX_FACE_BRIGHTNESS) - 0.04);
        }
        if (brightness < MIN_FACE_BRIGHTNESS) {
            return ColorMath.shiftLightness(face, MIN_FACE_BRIGHTNESS - brightness + 0.04);
        }
        return face;
    }

    /** Drives the shadow down until {@code -ui-face-pressed} is clearly darker than the hover. */
    private static Color darkEnoughForPress(Map<PaletteRole, Color> colors) {
        Color shadow = colors.get(PaletteRole.SHADOW);
        for (int attempt = 0; attempt < 40; attempt++) {
            double hover = mix(colors.get(PaletteRole.SURFACE_RAISED),
                    colors.get(PaletteRole.OUTLINE), 0.55).getBrightness();
            double pressed = mix(colors.get(PaletteRole.SURFACE_RAISED), shadow, 0.42)
                    .getBrightness();
            if (pressed < hover - MIN_PRESS_GAP) {
                return shadow;
            }
            shadow = ColorMath.shiftLightness(shadow, -0.06);
        }
        return shadow;
    }

    /** Moves the outline until a selected row is a visibly different colour from the panel. */
    private static Color awayFromSurface(Map<PaletteRole, Color> colors) {
        Color surface = colors.get(PaletteRole.SURFACE);
        Color outline = colors.get(PaletteRole.OUTLINE);
        // Away from the panel: an outline darker than the panel has room below and a lighter one
        // has room above. The selection is the outline lightened by 30%, so the comparison is made
        // against that rather than against the outline itself.
        double direction = outline.getBrightness() >= surface.getBrightness() ? 1 : -1;
        for (int attempt = 0; attempt < 40; attempt++) {
            Color selected = GbaColor.snap(outline.deriveColor(0, 1, 1.3, 1));
            if (ColorMath.distance(selected, surface) > MIN_SELECTION_DISTANCE) {
                return outline;
            }
            outline = ColorMath.shiftLightness(outline, direction * 0.05);
        }
        return outline;
    }

    /** Pushes a dim variant until it is readable but still clearly quieter than its live twin. */
    private static Color quieterThan(Color dim, Color live, Color ground) {
        double liveContrast = ColorMath.contrast(live, ground);
        Color result = dim;
        for (int attempt = 0; attempt < 30; attempt++) {
            if (ColorMath.contrast(result, ground) < liveContrast - 0.5) {
                return result;
            }
            // Towards the ground it sits on, which is what "inactive" looks like whatever the mood.
            result = GbaColor.snap(result.interpolate(ground, 0.12));
        }
        return result;
    }

    /**
     * Nudges any role that shares a colour with an earlier one.
     *
     * <p>Two roles the same colour is not a cosmetic problem: it makes them indistinguishable by
     * construction, and the pair it happened to in this project was {@code BACKGROUND_ALT} and
     * {@code SURFACE} - which are the two halves of the library table's alternating rows. They
     * differed by less than one 5-bit level, snapped together, and the rows silently stopped
     * alternating.
     *
     * <p>{@link PaletteRole#METER_HIGH} is exempt: a meter peaking into the colour of the progress
     * fill is the intent rather than a collision.
     */
    private static Palette deduplicate(Palette palette) {
        Map<PaletteRole, Color> colors = palette.asMap();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (PaletteRole role : PaletteRole.values()) {
            if (role == PaletteRole.METER_HIGH) {
                continue;
            }
            Color color = colors.get(role);
            int attempts = 0;
            while (!seen.add(GbaColor.toHex(color)) && attempts < 20) {
                // One 5-bit level at a time, alternating direction, so a nudge for uniqueness can
                // never walk a role far enough to undo a separation the repair just made.
                double step = (attempts % 2 == 0 ? 1 : -1) * 0.035 * (attempts / 2 + 1);
                color = ColorMath.shiftLightness(colors.get(role), step);
                attempts++;
            }
            colors.put(role, color);
        }
        return new Palette(palette.name(), colors);
    }

    private static Color recessed(Map<PaletteRole, Color> colors) {
        return GbaColor.snap(colors.get(PaletteRole.BACKGROUND)
                .interpolate(colors.get(PaletteRole.SHADOW), 0.18));
    }

    private static Color mix(Color from, Color to, double t) {
        return GbaColor.snap(from.interpolate(to, t));
    }
}
