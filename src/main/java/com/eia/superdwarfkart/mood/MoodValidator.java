package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The check that keeps a user-built mood from quietly breaking the two views this project is
 * demonstrated on.
 *
 * <p><strong>This is the trap the whole mood system has to be built around.</strong> Four of the
 * sixteen roles carry meaning rather than decoration. A mood that brings {@link PaletteRole#POSITIVE}
 * and {@link PaletteRole#NEGATIVE} together makes coins and obstacles indistinguishable at speed;
 * one that flattens {@link PaletteRole#HIGHLIGHT} into {@link PaletteRole#OUTLINE} kills the BST
 * traversal animation from the back of the room. Neither throws. Neither shows up in a screenshot,
 * because the person taking it knows which is which. It fails <em>live</em>, in front of the class,
 * on the runner and on the structure visualiser - the two things a competing team is unlikely to
 * have built and therefore the two things worth the most.
 *
 * <p>So this runs on every load <em>and</em> on every edit in the customizer, and the answer is
 * never "no". A failing palette is reported by name <em>and</em> rendered through
 * {@link #repair(Palette)}, which shifts the offending role's lightness until the threshold is met.
 * Never render an invalid mood; never silently accept one either. The user sees the warning and
 * sees the substitute at the same moment, which is the only arrangement in which they can tell what
 * happened.
 *
 * <p>Brightness is checked as well as colour, and that is not fussiness. Hue coding fails for a
 * colourblind viewer and for a projector with bad gamma, and this project is demonstrated through
 * both at once.
 */
public final class MoodValidator {

    /** WCAG AA for body text. */
    public static final double MIN_TEXT_CONTRAST = 4.5;

    /** CIE76 distance below which two colours read as one across a room. */
    public static final double MIN_DELTA_E = 25;

    /**
     * Brightness separation coins and obstacles need on top of their colour difference.
     *
     * <p>Two colours can be 25 apart in Lab and identical in brightness - a saturated red and a
     * saturated green at the same lightness are exactly that, and they are also the single most
     * common pair of colours a colourblind viewer cannot separate.
     */
    public static final double MIN_BRIGHTNESS_GAP = 0.08;

    /** Largest lightness shift a repair may make before it gives up and takes what it has. */
    private static final double MAX_REPAIR = 0.9;

    /** Step size a repair searches in. Fine enough to stop just past the threshold. */
    private static final double REPAIR_STEP = 0.03;

    private MoodValidator() {
        throw new AssertionError("MoodValidator is a utility holder and must not be instantiated");
    }

    /**
     * Checks a palette against every rule.
     *
     * @param palette the palette to check; must not be {@code null}
     * @return the problems, in the order they are worth fixing; empty when the palette is sound
     */
    public static List<MoodIssue> validate(Palette palette) {
        List<MoodIssue> issues = new ArrayList<>();

        textIssue(palette, PaletteRole.BACKGROUND,
                "Body text is too close to the background to read").ifPresent(issues::add);
        textIssue(palette, PaletteRole.SURFACE,
                "Body text is too close to the panels to read").ifPresent(issues::add);

        separationIssue(palette, PaletteRole.POSITIVE, PaletteRole.NEGATIVE,
                "Coins and obstacles look the same - the runner becomes unreadable at speed")
                .ifPresent(issues::add);
        separationIssue(palette, PaletteRole.HIGHLIGHT, PaletteRole.OUTLINE,
                "HIGHLIGHT is too close to OUTLINE - the BST traversal will be hard to see")
                .ifPresent(issues::add);

        double positive = palette.color(PaletteRole.POSITIVE).getBrightness();
        double negative = palette.color(PaletteRole.NEGATIVE).getBrightness();
        double gap = Math.abs(positive - negative);
        if (gap < MIN_BRIGHTNESS_GAP) {
            issues.add(new MoodIssue(PaletteRole.POSITIVE, PaletteRole.NEGATIVE, gap,
                    MIN_BRIGHTNESS_GAP,
                    "Coins and obstacles differ only in hue, which a colourblind viewer and a "
                            + "projector with bad gamma both lose"));
        }

        return issues;
    }

    /**
     * Whether a palette may be rendered as it stands.
     *
     * @param palette the palette to check; must not be {@code null}
     * @return {@code true} when nothing is wrong with it
     */
    public static boolean isValid(Palette palette) {
        return validate(palette).isEmpty();
    }

    /**
     * Returns a palette that meets every threshold, substituting where the given one does not.
     *
     * <p>Only the roles that failed are moved, and only in lightness. Everything else is the user's
     * palette exactly as they built it - a repair that rewrote a mood wholesale would be
     * indistinguishable from the application refusing to use it.
     *
     * <p>Idempotent by construction: a palette that already passes is returned unchanged, so a mood
     * cannot drift a little further from what the user chose every time it is loaded.
     *
     * @param palette the palette to repair; must not be {@code null}
     * @return the same palette when it is sound, otherwise a corrected copy
     */
    public static Palette repair(Palette palette) {
        if (isValid(palette)) {
            return palette;
        }

        Map<PaletteRole, Color> fixed = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            fixed.put(role, palette.color(role));
        }

        // Text first, and against both of its grounds at once. Fixing it against the background
        // and then against the surface separately would let the second pass undo the first.
        fixed.put(PaletteRole.TEXT_PRIMARY, awayFrom(
                fixed.get(PaletteRole.TEXT_PRIMARY),
                List.of(fixed.get(PaletteRole.BACKGROUND), fixed.get(PaletteRole.SURFACE))));

        // Then the two protected pairs. NEGATIVE is the one moved in each case rather than
        // POSITIVE: coins are the palette's own accent colour and are seen constantly, where an
        // obstacle is a warning and may be any shade of alarming.
        fixed.put(PaletteRole.NEGATIVE,
                apart(fixed.get(PaletteRole.NEGATIVE), fixed.get(PaletteRole.POSITIVE), true));
        fixed.put(PaletteRole.HIGHLIGHT,
                apart(fixed.get(PaletteRole.HIGHLIGHT), fixed.get(PaletteRole.OUTLINE), false));

        return new Palette(palette.name(), fixed);
    }

    /**
     * Pushes a colour until it has enough contrast against every ground it is drawn on.
     *
     * <p>Both directions are tried and the better one wins, rather than assuming which way there is
     * room. On a mid-grey ground - which is exactly what a user lands on halfway through dragging a
     * picker - one direction runs out of headroom and the other does not, and guessing gets it
     * wrong half the time.
     */
    private static Color awayFrom(Color color, List<Color> grounds) {
        return ColorMath.forContrast(color, grounds, MIN_TEXT_CONTRAST);
    }

    /**
     * Pushes one colour away from another until they are far enough apart in Lab, and - when asked
     * - in brightness too.
     */
    private static Color apart(Color color, Color other, boolean alsoBrightness) {
        if (isSeparated(color, other, alsoBrightness)) {
            return color;
        }
        // Away from wherever the other one is: a colour darker than its partner has room below and
        // a lighter one has room above. Pushing towards it would close the gap while appearing to
        // do something about it.
        double direction = color.getBrightness() >= other.getBrightness() ? 1 : -1;
        Color best = color;
        double bestScore = ColorMath.deltaE(color, other);
        for (double amount = REPAIR_STEP; amount <= MAX_REPAIR; amount += REPAIR_STEP) {
            Color candidate = ColorMath.shiftLightness(color, direction * amount);
            if (isSeparated(candidate, other, alsoBrightness)) {
                return candidate;
            }
            double score = ColorMath.deltaE(candidate, other);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        // The chosen direction ran out of room; try the other one before settling.
        for (double amount = REPAIR_STEP; amount <= MAX_REPAIR; amount += REPAIR_STEP) {
            Color candidate = ColorMath.shiftLightness(color, -direction * amount);
            if (isSeparated(candidate, other, alsoBrightness)) {
                return candidate;
            }
        }
        return best;
    }

    private static boolean isSeparated(Color color, Color other, boolean alsoBrightness) {
        if (ColorMath.deltaE(color, other) < MIN_DELTA_E) {
            return false;
        }
        return !alsoBrightness
                || Math.abs(color.getBrightness() - other.getBrightness()) >= MIN_BRIGHTNESS_GAP;
    }

    private static java.util.Optional<MoodIssue> textIssue(Palette palette, PaletteRole ground,
            String message) {
        double ratio = ColorMath.contrast(
                palette.color(PaletteRole.TEXT_PRIMARY), palette.color(ground));
        if (ratio >= MIN_TEXT_CONTRAST) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new MoodIssue(PaletteRole.TEXT_PRIMARY, ground, ratio,
                MIN_TEXT_CONTRAST, message));
    }

    private static java.util.Optional<MoodIssue> separationIssue(Palette palette, PaletteRole first,
            PaletteRole second, String message) {
        double delta = ColorMath.deltaE(palette.color(first), palette.color(second));
        if (delta >= MIN_DELTA_E) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new MoodIssue(first, second, delta, MIN_DELTA_E, message));
    }
}
