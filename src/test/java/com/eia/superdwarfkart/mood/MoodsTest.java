package com.eia.superdwarfkart.mood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javafx.scene.paint.Color;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-in moods, and the separations a mood must not destroy.
 *
 * <p>Four roles carry meaning rather than decoration. A mood that makes coins and obstacles look
 * alike, or that flattens the traversal highlight into an ordinary outline, throws nothing and
 * looks perfectly fine in a screenshot - it fails live, in front of the room, on the two things
 * this project is being graded on. These are the thresholds the mood validator will enforce on
 * user-built moods when it arrives; the moods that ship have to meet them now.
 */
@DisplayName("Built-in moods")
class MoodsTest {

    /** WCAG AA for body text. */
    private static final double MIN_TEXT_CONTRAST = 4.5;

    /** CIE76 separation below which two colours read as the same one across a room. */
    private static final double MIN_DELTA_E = 25;

    static List<Mood> moods() {
        return Moods.builtIns();
    }

    @Nested
    @DisplayName("the set")
    class TheSet {

        @Test
        @DisplayName("ships the dark and light moods the bonus is delivered as")
        void shipsBoth() {
            assertEquals(List.of("dark", "light"), Moods.builtIns().stream().map(Mood::id).toList());
        }

        @Test
        @DisplayName("resolves a stored id, and treats an unknown one as absent rather than fatal")
        void resolvesById() {
            assertSame(Moods.LIGHT, Moods.byId("light").orElseThrow());
            assertTrue(Moods.byId("sunset_wilds").isEmpty(), "an unknown mood must not resolve");
            assertTrue(Moods.byId(null).isEmpty(), "a missing stored choice must not throw");
        }

        @Test
        @DisplayName("falls back to a mood that exists")
        void defaultIsBuiltIn() {
            assertTrue(Moods.builtIns().contains(Moods.defaultMood()));
        }

        @Test
        @DisplayName("gives every mood a distinct id")
        void idsAreUnique() {
            Set<String> seen = new HashSet<>();
            for (Mood mood : Moods.builtIns()) {
                assertTrue(seen.add(mood.id()), "duplicate mood id: " + mood.id());
            }
        }
    }

    @Nested
    @DisplayName("every mood")
    class EveryMood {

        @ParameterizedTest(name = "{0} keeps body text readable")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void textIsReadable(Mood mood) {
            assertContrast(mood, PaletteRole.TEXT_PRIMARY, PaletteRole.BACKGROUND);
            assertContrast(mood, PaletteRole.TEXT_PRIMARY, PaletteRole.SURFACE);
        }

        /**
         * Coins are drawn in {@code POSITIVE} and obstacles in {@code NEGATIVE}. A mood that brings
         * them together does not break the game - it makes it unreadable at speed, which is worse,
         * because it looks like the player is simply bad at it.
         */
        @ParameterizedTest(name = "{0} keeps coins and obstacles apart")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void coinsAndObstaclesDiffer(Mood mood) {
            assertSeparation(mood, PaletteRole.POSITIVE, PaletteRole.NEGATIVE);
        }

        /**
         * The animating successor edge is the thing the tree view exists to show. Flattened into
         * the ordinary outline it is invisible from the back of a classroom, which is the only
         * place it is ever watched from.
         */
        @ParameterizedTest(name = "{0} keeps the traversal highlight off the outline")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void highlightStandsOutFromOutline(Mood mood) {
            assertSeparation(mood, PaletteRole.HIGHLIGHT, PaletteRole.OUTLINE);
        }

        /**
         * Hue alone is not a distinction: it fails for a colourblind viewer and for a projector
         * with bad gamma, and this project is demonstrated through both.
         */
        @ParameterizedTest(name = "{0} separates coins and obstacles by brightness, not only hue")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void coinsAndObstaclesDifferInBrightness(Mood mood) {
            double positive = mood.color(PaletteRole.POSITIVE).getBrightness();
            double negative = mood.color(PaletteRole.NEGATIVE).getBrightness();

            assertTrue(Math.abs(positive - negative) > 0.08,
                    mood + " separates coins from obstacles by hue alone (brightness " + positive
                            + " vs " + negative + "), which a colourblind viewer cannot use");
        }

        /**
         * {@code PRIMARY} and {@code ACCENT} are text as often as they are fill - the application
         * name, the table headings, the now-playing line, the section headings. A palette that
         * picks them for how they look as a block of colour and never checks them as text produces
         * an application whose headings cannot be read, which is what the light mood did on its
         * first pass: a bright amber that is perfect on near-black measured 2.0:1 on its own
         * header. Nothing reports it, and it is invisible until somebody looks at that mood.
         */
        @ParameterizedTest(name = "{0} keeps the primary and the accent readable as text")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void accentsAreReadableAsText(Mood mood) {
            Map<String, Color> tokens = PaletteCss.tokens(mood.palette());
            for (PaletteRole role : List.of(PaletteRole.PRIMARY, PaletteRole.ACCENT)) {
                assertContrast(mood, role, PaletteRole.SURFACE);
                assertContrast(mood, role, PaletteRole.BACKGROUND);

                // The recessed band is the darkest ground in a light mood and the lightest in a
                // dark one, so it is where an accent runs out of contrast first.
                double ratio = contrast(mood.color(role), tokens.get("-ui-recessed"));
                assertTrue(ratio >= MIN_TEXT_CONTRAST,
                        mood + " renders " + role + " on the recessed band at " + round(ratio)
                                + ":1, under the " + MIN_TEXT_CONTRAST + ":1 needed to read it");
            }
        }

        @ParameterizedTest(name = "{0} holds all sixteen roles, snapped to the GBA grid")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void isOnTheHardwareGrid(Mood mood) {
            for (PaletteRole role : PaletteRole.values()) {
                Color color = mood.color(role);
                assertEquals(color, GbaColor.snap(color),
                        mood + " draws " + role + " in a colour the hardware could not display");
            }
        }

        @ParameterizedTest(name = "{0} does not reuse one colour for two roles")
        @MethodSource("com.eia.superdwarfkart.mood.MoodsTest#moods")
        void rolesAreDistinct(Mood mood) {
            Set<String> seen = new HashSet<>();
            for (PaletteRole role : PaletteRole.values()) {
                if (role == PaletteRole.METER_HIGH) {
                    // The meter's top and the primary are legitimately the same amber in the dark
                    // mood: a meter peaking into the colour of the progress fill is the intent.
                    continue;
                }
                String hex = GbaColor.toHex(mood.color(role));
                assertTrue(seen.add(hex),
                        mood + " uses " + hex + " for " + role + " and for another role as well");
            }
        }
    }

    @Test
    @DisplayName("the light mood leaves the button face room to be lit")
    void lightFaceHasHeadroom() {
        double face = Moods.LIGHT.color(PaletteRole.SURFACE_RAISED).getBrightness();

        assertFalse(face > 0.97,
                "the light mood's control face is at full brightness, so the bevel's lit edge has "
                        + "nowhere to go and every button in the application reads as flat");
    }

    private static void assertContrast(Mood mood, PaletteRole text, PaletteRole ground) {
        double ratio = contrast(mood.color(text), mood.color(ground));
        assertTrue(ratio >= MIN_TEXT_CONTRAST,
                mood + " renders " + text + " on " + ground + " at " + round(ratio)
                        + ":1, under the " + MIN_TEXT_CONTRAST + ":1 needed to read it");
    }

    private static void assertSeparation(Mood mood, PaletteRole a, PaletteRole b) {
        double delta = deltaE(mood.color(a), mood.color(b));
        assertTrue(delta >= MIN_DELTA_E,
                mood + " puts " + a + " and " + b + " only " + round(delta)
                        + " apart, under the " + MIN_DELTA_E + " they need to be told apart");
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10d;
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen())
                + 0.0722 * linear(c.getBlue());
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** CIE76: plain Euclidean distance in Lab, which is what the mood system specifies. */
    private static double deltaE(Color a, Color b) {
        double[] first = lab(a);
        double[] second = lab(b);
        double dl = first[0] - second[0];
        double da = first[1] - second[1];
        double db = first[2] - second[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static double[] lab(Color c) {
        double r = linear(c.getRed());
        double g = linear(c.getGreen());
        double b = linear(c.getBlue());

        // sRGB to CIE XYZ, D65.
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;

        double fx = pivot(x);
        double fy = pivot(y);
        double fz = pivot(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double pivot(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (903.3 * t + 16) / 116;
    }
}
