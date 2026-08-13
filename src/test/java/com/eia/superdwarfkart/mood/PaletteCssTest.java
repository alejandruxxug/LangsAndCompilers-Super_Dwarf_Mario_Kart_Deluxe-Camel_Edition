package com.eia.superdwarfkart.mood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stylesheet's half of ground rule 7.
 *
 * <p>These are the checks that a mood switch actually reaches the controls. Nothing here throws at
 * runtime when it is wrong: a colour {@code app.css} names but no palette defines simply fails to
 * resolve, and a hexadecimal literal left in the stylesheet simply ignores the mood. Both look
 * fine in a screenshot of the default mood, which is the only mood anybody tests by eye.
 */
@DisplayName("Palette stylesheet")
class PaletteCssTest {

    /** Colours in {@code app.css} are named, never written - except the fault marker. */
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}");

    /** Any {@code -role-*} or {@code -ui-*} name used in the stylesheet. */
    private static final Pattern TOKEN_USE = Pattern.compile("(-role-|-ui-)[a-z-]+");

    /** The one deliberate exception: a diagnostic, not a theme colour. */
    private static final Pattern DIAGNOSTIC_DECLARATION =
            Pattern.compile("-diag-[a-z-]+\\s*:\\s*#[0-9a-fA-F]{3,8}\\s*;");

    private static String appCss() {
        try (InputStream in = PaletteCss.class.getResourceAsStream("/css/app.css")) {
            if (in == null) {
                throw new IllegalStateException("app.css is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every palette a mood switch could install. */
    static List<Palette> palettes() {
        return Moods.builtIns().stream().map(Mood::palette).toList();
    }

    @Nested
    @DisplayName("the generated stylesheet")
    class Generated {

        @Test
        @DisplayName("defines every one of the sixteen roles")
        void definesEveryRole() {
            Map<String, Color> tokens = PaletteCss.tokens(Palette.defaultPalette());
            for (PaletteRole role : PaletteRole.values()) {
                assertTrue(tokens.containsKey(PaletteCss.variableName(role)),
                        role + " has no colour in the generated stylesheet");
            }
        }

        @Test
        @DisplayName("names roles after the enum constant, so a new role cannot be forgotten")
        void namesFollowTheConstant() {
            assertEquals("-role-background", PaletteCss.variableName(PaletteRole.BACKGROUND));
            assertEquals("-role-background-alt",
                    PaletteCss.variableName(PaletteRole.BACKGROUND_ALT));
            assertEquals("-role-text-primary", PaletteCss.variableName(PaletteRole.TEXT_PRIMARY));
        }

        @Test
        @DisplayName("is a data URL carrying the whole stylesheet, so a new palette is a new URL")
        void urlCarriesTheStylesheet() {
            Palette palette = Palette.defaultPalette();
            String url = PaletteCss.stylesheetUrl(palette);

            assertTrue(url.startsWith("data:text/css;base64,"), "not a CSS data URL: " + url);
            String decoded = new String(
                    Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)),
                    StandardCharsets.UTF_8);
            assertEquals(PaletteCss.stylesheet(palette), decoded);
        }

        @Test
        @DisplayName("writes every colour as a plain six-digit hex value CSS can parse")
        void everyValueIsPlainHex() {
            String css = PaletteCss.stylesheet(Palette.defaultPalette());
            for (String line : css.lines().toList()) {
                if (line.contains(":") && !line.startsWith("/*")) {
                    assertTrue(line.trim().matches("^-[a-z-]+: #[0-9a-f]{6};$"),
                            "not a usable declaration: " + line);
                }
            }
        }
    }

    @Nested
    @DisplayName("the derived surfaces")
    class Derived {

        /**
         * The check a light palette is going to break, and the reason these are derived in Java
         * rather than with the CSS {@code derive()} function: mixing a face towards white to
         * lighten it works perfectly in a dark mood and produces a bevel with no lit edge at all
         * in a light one, where the face is already near white. Nothing reports that. The button
         * just stops looking like a button.
         */
        @ParameterizedTest(name = "the bevel stays a bevel in {0}")
        @MethodSource("com.eia.superdwarfkart.mood.PaletteCssTest#palettes")
        void bevelKeepsItsDirection(Palette palette) {
            Map<String, Color> tokens = PaletteCss.tokens(palette);
            double face = brightness(tokens.get("-role-surface-raised"));
            double light = brightness(tokens.get("-ui-bevel-light"));
            double dark = brightness(tokens.get("-ui-bevel-dark"));

            assertTrue(light > face,
                    "the lit edge is not brighter than the face it lights (" + light + " <= "
                            + face + ") in " + palette.name());
            assertTrue(dark < face,
                    "the shadowed edge is not darker than the face (" + dark + " >= " + face
                            + ") in " + palette.name());
        }

        /**
         * A selected row has to be visibly a different colour from an unselected one. Deriving the
         * selection by lightening the face does that in a dark mood and fails silently in a light
         * one, where the face has nowhere brighter to go and selection lands on top of the row
         * colour - so the table stops showing which song is playing.
         */
        @ParameterizedTest(name = "the selected row is distinguishable in {0}")
        @MethodSource("com.eia.superdwarfkart.mood.PaletteCssTest#palettes")
        void selectionStandsOutFromTheRow(Palette palette) {
            Map<String, Color> tokens = PaletteCss.tokens(palette);
            double gap = distance(tokens.get("-ui-selected"), tokens.get("-role-surface"));

            assertTrue(gap > 0.12,
                    "the selected row is the same colour as an unselected one (distance " + gap
                            + ") in " + palette.name());
        }

        @ParameterizedTest(name = "pressed stays darker than hover in {0}")
        @MethodSource("com.eia.superdwarfkart.mood.PaletteCssTest#palettes")
        void pressedIsDarkerThanHover(Palette palette) {
            Map<String, Color> tokens = PaletteCss.tokens(palette);
            double hover = brightness(tokens.get("-ui-face-hover"));
            double pressed = brightness(tokens.get("-ui-face-pressed"));

            assertTrue(pressed < hover - 0.04,
                    "a pressed control is not visibly darker than a hovered one (" + pressed
                            + " vs " + hover + ") in " + palette.name());
        }

        @ParameterizedTest(name = "disabled text stays quieter than body text in {0}")
        @MethodSource("com.eia.superdwarfkart.mood.PaletteCssTest#palettes")
        void disabledTextRecedes(Palette palette) {
            Map<String, Color> tokens = PaletteCss.tokens(palette);
            double body = contrast(tokens.get("-role-text-primary"), tokens.get("-role-surface"));
            double disabled = contrast(tokens.get("-ui-text-disabled"), tokens.get("-role-surface"));

            assertTrue(disabled < body,
                    "disabled text stands out as much as body text in " + palette.name());
        }

        @Test
        @DisplayName("a highlight on the primary is visible even though it is already at full brightness")
        void primaryHighlightIsVisible() {
            Palette palette = Palette.defaultPalette();
            Color primary = palette.color(PaletteRole.PRIMARY);
            Color lit = palette.tinted(PaletteRole.PRIMARY, 0.55);

            assertEquals(1.0, primary.getBrightness(), 0.02,
                    "this test is pointless unless PRIMARY is at full brightness");
            assertTrue(lit.getSaturation() < primary.getSaturation() - 0.15,
                    "tinting did not move the primary towards white, so the lit edge of the "
                            + "rating meter and the slider thumb is invisible");
        }
    }

    @Nested
    @DisplayName("app.css")
    class Stylesheet {

        @Test
        @DisplayName("writes no colour of its own, apart from the fault marker")
        void carriesNoLiterals() {
            String css = DIAGNOSTIC_DECLARATION.matcher(appCss()).replaceAll("");

            Matcher leftovers = HEX.matcher(css);
            assertFalse(leftovers.find(),
                    "app.css names a colour instead of a role: " + (leftovers.reset().results()
                            .map(MatchResultText::of).collect(Collectors.joining(", ")))
                            + ". Every colour resolves through the active mood by role name - a "
                            + "literal here is a control the mood switcher cannot reach.");
        }

        @Test
        @DisplayName("names only colours the generated stylesheet actually defines")
        void everyTokenResolves() {
            Set<String> defined = PaletteCss.tokens(Palette.defaultPalette()).keySet();

            Set<String> used = new LinkedHashSet<>();
            Matcher matcher = TOKEN_USE.matcher(appCss());
            while (matcher.find()) {
                used.add(matcher.group());
            }

            assertFalse(used.isEmpty(), "app.css resolves no roles at all - did it get rewritten?");

            Set<String> undefined = used.stream()
                    .filter(token -> !defined.contains(token))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            assertTrue(undefined.isEmpty(),
                    "app.css uses " + undefined + ", which no palette defines. A looked-up colour "
                            + "that resolves to nothing does not throw - the control is simply "
                            + "drawn in the wrong colour, or in none.");
        }
    }

    private static double brightness(Color color) {
        return color.getBrightness();
    }

    /** Plain RGB distance, normalised to 0..1. Enough to tell "different colour" from "not". */
    private static double distance(Color a, Color b) {
        double dr = a.getRed() - b.getRed();
        double dg = a.getGreen() - b.getGreen();
        double db = a.getBlue() - b.getBlue();
        return Math.sqrt((dr * dr + dg * dg + db * db) / 3);
    }

    /** WCAG relative-luminance contrast, the measure the mood validator will use. */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen())
                + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Renders a regex match for a failure message. */
    private interface MatchResultText {
        static String of(java.util.regex.MatchResult result) {
            return result.group();
        }
    }
}
