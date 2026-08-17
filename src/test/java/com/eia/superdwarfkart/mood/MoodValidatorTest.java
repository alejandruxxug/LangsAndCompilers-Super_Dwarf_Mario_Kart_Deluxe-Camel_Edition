package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check that stands between a user-built mood and a demonstration that quietly stops working.
 *
 * <p>Every failure this catches is silent. A mood that brings coins and obstacles together throws
 * nothing, logs nothing and photographs perfectly; it fails live, at speed, on the runner - and it
 * looks like the player is simply bad at the game. The same is true of a traversal highlight
 * flattened into the ordinary outline, on the one view that is watched from the back of a room.
 *
 * <p>So the tests below are mostly about the <em>repair</em> rather than the detection. Detecting a
 * bad palette and then rendering it anyway would be worse than not detecting it, because the warning
 * would train the user to ignore warnings.
 */
@DisplayName("Mood validator")
class MoodValidatorTest {

    static List<Palette> palettes() {
        return Moods.builtIns().stream().map(Mood::palette).toList();
    }

    /** A palette with one role replaced, for making a specific fault. */
    private static Palette broken(PaletteRole role, Color color) {
        return Palette.defaultPalette().withColor(role, color);
    }

    @Nested
    @DisplayName("what it catches")
    class Detection {

        @ParameterizedTest(name = "{0} passes as it ships")
        @MethodSource("com.eia.superdwarfkart.mood.MoodValidatorTest#palettes")
        void everyBuiltInIsValid(Palette palette) {
            assertTrue(MoodValidator.isValid(palette),
                    palette.name() + " ships broken: " + MoodValidator.validate(palette));
        }

        @Test
        @DisplayName("body text that cannot be read against the background")
        void unreadableTextIsCaught() {
            Palette palette = broken(PaletteRole.TEXT_PRIMARY,
                    Palette.defaultPalette().color(PaletteRole.BACKGROUND));

            List<MoodIssue> issues = MoodValidator.validate(palette);

            assertTrue(issues.stream().anyMatch(issue -> issue.first() == PaletteRole.TEXT_PRIMARY),
                    "text the same colour as its ground was not reported");
        }

        /**
         * The one that costs the most. Coins are drawn in POSITIVE and obstacles in NEGATIVE, and a
         * mood that brings them together does not break the game - it makes it unreadable at speed,
         * which is worse, because there is nothing to see that is wrong.
         */
        @Test
        @DisplayName("coins and obstacles that look alike")
        void coinsAndObstaclesAreCaught() {
            Palette palette = broken(PaletteRole.NEGATIVE,
                    Palette.defaultPalette().color(PaletteRole.POSITIVE));

            List<MoodIssue> issues = MoodValidator.validate(palette);

            assertTrue(issues.stream().anyMatch(issue ->
                            issue.first() == PaletteRole.POSITIVE
                                    && issue.second() == PaletteRole.NEGATIVE),
                    "identical coins and obstacles were not reported");
        }

        @Test
        @DisplayName("a traversal highlight flattened into the outline")
        void flattenedHighlightIsCaught() {
            Palette palette = broken(PaletteRole.HIGHLIGHT,
                    Palette.defaultPalette().color(PaletteRole.OUTLINE));

            assertFalse(MoodValidator.isValid(palette));
            assertTrue(MoodValidator.validate(palette).stream().anyMatch(issue ->
                    issue.first() == PaletteRole.HIGHLIGHT && issue.second() == PaletteRole.OUTLINE));
        }

        /**
         * Hue alone is not a distinction. A saturated red and a saturated green at the same
         * lightness are twenty-five apart in Lab and indistinguishable to a colourblind viewer and
         * to a projector with bad gamma - and this project is demonstrated through both at once.
         */
        @Test
        @DisplayName("coins and obstacles separated only by hue")
        void hueOnlySeparationIsCaught() {
            Palette palette = Palette.defaultPalette()
                    .withColor(PaletteRole.POSITIVE, GbaColor.web("#00b000"))
                    .withColor(PaletteRole.NEGATIVE, GbaColor.web("#b00000"));

            double positive = palette.color(PaletteRole.POSITIVE).getBrightness();
            double negative = palette.color(PaletteRole.NEGATIVE).getBrightness();
            assertEquals(positive, negative, 0.02,
                    "this test is pointless unless the two are the same brightness");

            assertTrue(MoodValidator.validate(palette).stream()
                            .anyMatch(issue -> issue.measured() < MoodValidator.MIN_BRIGHTNESS_GAP),
                    "a hue-only separation was not reported");
        }

        @Test
        @DisplayName("says what stops working, not which threshold was missed")
        void messagesAreAboutConsequences() {
            Palette palette = broken(PaletteRole.HIGHLIGHT,
                    Palette.defaultPalette().color(PaletteRole.OUTLINE));

            MoodIssue issue = MoodValidator.validate(palette).get(0);

            assertTrue(issue.message().toLowerCase().contains("traversal"),
                    "the message has to name what breaks: " + issue.message());
            assertTrue(issue.detail().contains(String.valueOf(MoodValidator.MIN_DELTA_E)),
                    "the measurement belongs in the detail, not in the headline");
        }
    }

    @Nested
    @DisplayName("what it does about it")
    class Repair {

        @Test
        @DisplayName("leaves a sound palette exactly as it is, by identity")
        void soundPalettesAreUntouched() {
            Palette palette = Palette.defaultPalette();

            assertSame(palette, MoodValidator.repair(palette),
                    "repairing a good palette must not even copy it");
        }

        /**
         * Idempotence is not tidiness here. This runs on every load, so a repair that moved a
         * colour a little further each time would walk a mood away from what the user chose over a
         * few sessions - and every step of it would look deliberate.
         */
        @ParameterizedTest(name = "repairing {0} twice changes nothing the second time")
        @MethodSource("com.eia.superdwarfkart.mood.MoodValidatorTest#palettes")
        void repairIsIdempotent(Palette palette) {
            Palette once = MoodValidator.repair(palette);
            Palette twice = MoodValidator.repair(once);

            for (PaletteRole role : PaletteRole.values()) {
                assertEquals(GbaColor.toHex(once.color(role)), GbaColor.toHex(twice.color(role)),
                        role + " drifted on a second repair");
            }
        }

        @Test
        @DisplayName("makes an unreadable palette readable rather than refusing it")
        void repairFixesText() {
            Palette palette = broken(PaletteRole.TEXT_PRIMARY,
                    Palette.defaultPalette().color(PaletteRole.BACKGROUND));

            Palette repaired = MoodValidator.repair(palette);

            assertTrue(MoodValidator.isValid(repaired),
                    "the repair left " + MoodValidator.validate(repaired));
        }

        @Test
        @DisplayName("pulls coins and obstacles back apart, in both directions")
        void repairSeparatesTheProtectedPair() {
            for (String hex : List.of("#5ce65c", "#101010", "#f0f0f0")) {
                Palette palette = broken(PaletteRole.NEGATIVE, GbaColor.web(hex))
                        .withColor(PaletteRole.POSITIVE, GbaColor.web(hex));

                Palette repaired = MoodValidator.repair(palette);

                assertTrue(MoodValidator.isValid(repaired),
                        "starting from " + hex + " the repair left "
                                + MoodValidator.validate(repaired));
            }
        }

        /**
         * The repair moves lightness and never hue, because hue is what a mood <em>is</em> - the
         * whole difference between Sunset Wilds and Boo Lake. A fixer that recoloured one into the
         * other would be an intrusion rather than a repair.
         */
        @Test
        @DisplayName("moves lightness, never hue")
        void repairKeepsTheHue() {
            Palette palette = broken(PaletteRole.HIGHLIGHT,
                    Palette.defaultPalette().color(PaletteRole.OUTLINE));

            Palette repaired = MoodValidator.repair(palette);

            double before = palette.color(PaletteRole.HIGHLIGHT).getHue();
            double after = repaired.color(PaletteRole.HIGHLIGHT).getHue();
            assertEquals(before, after, 12,
                    "the repair changed the colour rather than its lightness");
        }

        @Test
        @DisplayName("touches only the roles that failed")
        void repairIsMinimal() {
            Palette palette = broken(PaletteRole.HIGHLIGHT,
                    Palette.defaultPalette().color(PaletteRole.OUTLINE));

            Palette repaired = MoodValidator.repair(palette);

            for (PaletteRole role : PaletteRole.values()) {
                if (role == PaletteRole.HIGHLIGHT) {
                    continue;
                }
                assertEquals(GbaColor.toHex(palette.color(role)),
                        GbaColor.toHex(repaired.color(role)),
                        "the repair moved " + role + ", which was not the problem");
            }
        }

        /**
         * A palette with a mid-grey ground has nowhere for text to go. It must still return
         * something - the nearest to readable it managed - rather than throwing or handing back the
         * unreadable original.
         */
        @Test
        @DisplayName("returns its best attempt rather than throwing on an impossible palette")
        void impossiblePalettesStillProduceSomething() {
            Color grey = GbaColor.web("#808080");
            Palette palette = Palette.defaultPalette()
                    .withColor(PaletteRole.BACKGROUND, grey)
                    .withColor(PaletteRole.SURFACE, grey)
                    .withColor(PaletteRole.TEXT_PRIMARY, grey);

            Palette repaired = MoodValidator.repair(palette);

            assertTrue(ColorMath.contrast(repaired.color(PaletteRole.TEXT_PRIMARY), grey)
                            > ColorMath.contrast(grey, grey),
                    "the best attempt has to be better than the original");
        }
    }

    @Nested
    @DisplayName("the measurements")
    class Measurements {

        @Test
        @DisplayName("WCAG contrast: 21 for black on white, 1 for a colour on itself")
        void contrastIsWcag() {
            assertEquals(21, ColorMath.contrast(Color.BLACK, Color.WHITE), 0.05);
            assertEquals(1, ColorMath.contrast(Color.RED, Color.RED), 1e-9);
        }

        @Test
        @DisplayName("CIE76: zero for a colour against itself, and symmetric")
        void deltaEIsAMetric() {
            Color red = GbaColor.web("#ff0000");
            Color blue = GbaColor.web("#0000ff");

            assertEquals(0, ColorMath.deltaE(red, red), 1e-9);
            assertEquals(ColorMath.deltaE(red, blue), ColorMath.deltaE(blue, red), 1e-9);
            assertTrue(ColorMath.deltaE(red, blue) > MoodValidator.MIN_DELTA_E);
        }

        @Test
        @DisplayName("a lightness shift stays on the hardware grid")
        void shiftsAreSnapped() {
            Color shifted = ColorMath.shiftLightness(GbaColor.web("#3f7fbf"), 0.37);

            assertEquals(shifted, GbaColor.snap(shifted));
        }
    }
}
