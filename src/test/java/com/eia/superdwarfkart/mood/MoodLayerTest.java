package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The overlay layers: what they promise about themselves, and the two promises the renderer's whole
 * performance case rests on.
 *
 * <p>A layer draws nothing here - {@code ui/MoodOverlayRenderer} rasterises these definitions - which
 * is exactly what makes them testable with no window, no toolkit and no mood installed.
 */
@DisplayName("Mood layers")
class MoodLayerTest {

    private static final Palette PALETTE = Palette.defaultPalette();

    @Nested
    @DisplayName("the shared style")
    class Style {

        /**
         * The cap is applied on construction rather than by the customizer's slider, and this is
         * the case that proves why: a mood file somebody else exported is not a slider, and it is
         * exactly the file that arrives on the day of a defence.
         */
        @Test
        @DisplayName("caps an above-content layer at 0.35 however it was constructed")
        void aboveContentIsCapped() {
            LayerStyle style = new LayerStyle(ZBand.ABOVE_CONTENT, 1.0, LayerBlend.ADD, 0, 0, true);

            assertEquals(ZBand.ABOVE_CONTENT.maxOpacity(), style.opacity(), 1e-9,
                    "a layer over the content could otherwise bury the runner and the tree");
        }

        @Test
        @DisplayName("re-caps when a wallpaper is moved up in front of the content")
        void movingUpReappliesTheCap() {
            LayerStyle wallpaper = LayerStyle.behind();
            assertEquals(1.0, wallpaper.opacity(), 1e-9);

            assertEquals(ZBand.ABOVE_CONTENT.maxOpacity(),
                    wallpaper.withBand(ZBand.ABOVE_CONTENT).opacity(), 1e-9);
        }

        @Test
        @DisplayName("leaves a wallpaper alone: behind the content nothing can be buried")
        void behindContentIsNotCapped() {
            assertEquals(1.0, LayerStyle.behind().withOpacity(1).opacity(), 1e-9);
        }

        /**
         * The claim the renderer turns into "no frame loop at all". A layer that answered this
         * wrongly would silently turn a free mood into one that blits the whole canvas sixty times
         * a second, on a machine with no GPU.
         */
        @Test
        @DisplayName("calls itself static only when neither axis drifts")
        void staticMeansStill() {
            assertTrue(LayerStyle.behind().isStatic());
            assertFalse(LayerStyle.behind().withScroll(-8, 0).isStatic());
            assertFalse(LayerStyle.behind().withScroll(0, 3).isStatic());
        }

        @Test
        @DisplayName("clamps a drift rate rather than letting a hand-edited file fling a layer")
        void driftIsClamped() {
            LayerStyle style = LayerStyle.behind().withScroll(99999, -99999);

            assertEquals(LayerStyle.MAX_SCROLL, style.scrollX(), 1e-9);
            assertEquals(-LayerStyle.MAX_SCROLL, style.scrollY(), 1e-9);
        }

        @Test
        @DisplayName("an invisible layer is never live, however fast it would have drifted")
        void invisibleIsNeverLive() {
            MoodLayer layer = GradientLayer
                    .between(PaletteRole.BACKGROUND, PaletteRole.SURFACE, 90)
                    .withStyle(LayerStyle.behind().withScroll(60, 0).withVisible(false));

            assertFalse(layer.isLive(), "a hidden layer must not keep the frame loop alive");
        }
    }

    @Nested
    @DisplayName("gradients")
    class Gradients {

        @Test
        @DisplayName("refuse fewer than two stops and more than four")
        void stopCountIsBounded() {
            assertThrows(IllegalArgumentException.class, () -> new GradientLayer(
                    LayerStyle.behind(), GradientLayer.Kind.LINEAR, 90, 0.5, 0.5, 1,
                    List.of(GradientStop.of(0, PaletteRole.PRIMARY)),
                    GradientLayer.DEFAULT_BANDS, true));

            List<GradientStop> five = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                five.add(GradientStop.of(i / 4d, PaletteRole.PRIMARY));
            }
            assertThrows(IllegalArgumentException.class, () -> new GradientLayer(
                    LayerStyle.behind(), GradientLayer.Kind.LINEAR, 90, 0.5, 0.5, 1, five,
                    GradientLayer.DEFAULT_BANDS, true));
        }

        /**
         * A mood file is hand-editable by design, so its stops arrive in whatever order somebody
         * typed them. Sorted on construction, a ramp that folds back on itself is impossible to
         * express; trusted, it draws a gradient that runs backwards half way along and looks like a
         * rendering fault.
         */
        @Test
        @DisplayName("sort their stops, so a hand-edited file cannot fold the ramp back")
        void stopsAreSorted() {
            GradientLayer layer = new GradientLayer(LayerStyle.behind(),
                    GradientLayer.Kind.LINEAR, 90, 0.5, 0.5, 1,
                    List.of(GradientStop.of(1, PaletteRole.NEGATIVE),
                            GradientStop.of(0, PaletteRole.POSITIVE)),
                    0, false);

            assertEquals(PALETTE.color(PaletteRole.POSITIVE), layer.sample(0, PALETTE));
            assertEquals(PALETTE.color(PaletteRole.NEGATIVE), layer.sample(1, PALETTE));
        }

        @Test
        @DisplayName("run a linear ramp from exactly 0 at one corner to exactly 1 at the other")
        void linearSpansTheCanvas() {
            GradientLayer down = GradientLayer.between(
                    PaletteRole.BACKGROUND, PaletteRole.SURFACE, 90);

            assertEquals(0, down.positionAt(0, 0, 100, 100), 1e-9);
            assertEquals(1, down.positionAt(0, 100, 100, 100), 1e-9);
        }

        /**
         * A ramp that stopped short of the far corner would leave a flat band along one edge at
         * 45 degrees and nowhere else - the kind of fault that only appears at one angle.
         */
        @Test
        @DisplayName("still span the canvas at an angle, and in both directions")
        void diagonalAndReversedAlsoSpan() {
            for (double degrees : new double[] {0, 45, 90, 135, 180, 270, -45}) {
                GradientLayer layer = GradientLayer.between(
                        PaletteRole.BACKGROUND, PaletteRole.SURFACE, degrees);

                double lowest = Double.MAX_VALUE;
                double highest = -Double.MAX_VALUE;
                for (int x = 0; x <= 100; x += 10) {
                    for (int y = 0; y <= 100; y += 10) {
                        double position = layer.positionAt(x, y, 100, 100);
                        lowest = Math.min(lowest, position);
                        highest = Math.max(highest, position);
                        assertTrue(position >= 0 && position <= 1,
                                degrees + " degrees put a pixel at " + position);
                    }
                }
                assertEquals(0, lowest, 1e-9, "the ramp never reaches its first stop at " + degrees);
                assertEquals(1, highest, 1e-9, "the ramp never reaches its last stop at " + degrees);
            }
        }

        @Test
        @DisplayName("a radial ramp is 0 at its centre and 1 at its edge")
        void radialRunsOutwards() {
            GradientLayer layer = new GradientLayer(LayerStyle.behind(),
                    GradientLayer.Kind.RADIAL, 0, 0.5, 0.5, 1,
                    List.of(GradientStop.of(0, PaletteRole.PRIMARY),
                            GradientStop.of(1, PaletteRole.SHADOW)),
                    0, false);

            assertEquals(0, layer.positionAt(50, 50, 100, 100), 1e-9);
            assertEquals(1, layer.positionAt(0, 0, 100, 100), 1e-9);
        }

        /**
         * The whole reason bands exist: a hard-stepped ramp is the authentic look, and a smooth one
         * instantly reads as a modern toolkit whatever the palette is.
         */
        @Test
        @DisplayName("produce exactly as many colours as they have bands")
        void bandingIsWhatItSays() {
            GradientLayer layer = GradientLayer
                    .between(PaletteRole.SHADOW, PaletteRole.TEXT_PRIMARY, 90)
                    .withBands(8)
                    .withDither(false);

            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (int step = 0; step <= 400; step++) {
                seen.add(GbaColor.toHex(layer.colorAt(step / 400d, PALETTE, 0, 0)));
            }
            assertEquals(8, seen.size(), "a gradient cut into eight steps holds eight colours");
        }

        @Test
        @DisplayName("a smooth gradient is not banded")
        void zeroBandsIsSmooth() {
            GradientLayer layer = GradientLayer
                    .between(PaletteRole.SHADOW, PaletteRole.TEXT_PRIMARY, 90)
                    .withBands(0);

            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (int step = 0; step <= 400; step++) {
                seen.add(GbaColor.toHex(layer.colorAt(step / 400d, PALETTE, 0, 0)));
            }
            assertTrue(seen.size() > 8, "a smooth ramp should hold far more than eight colours");
        }

        /**
         * The dither has to move a pixel to be a dither, and it has to move it <em>both</em> ways:
         * a matrix applied without centring pushes every pixel later and shifts the whole ramp.
         */
        @Test
        @DisplayName("dither scatters a boundary in both directions rather than shifting it")
        void ditherMovesPixelsBothWays() {
            GradientLayer layer = GradientLayer
                    .between(PaletteRole.SHADOW, PaletteRole.TEXT_PRIMARY, 90)
                    .withBands(4)
                    .withDither(true);

            // Exactly on the boundary between the first band and the second, which is the one
            // position where the matrix decides which way each pixel falls. Half a band either
            // side of it every pixel agrees, which is what keeps flat areas flat.
            double onTheBoundary = 1.0 / 4;
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (int y = 0; y < Bayer.SIZE; y++) {
                for (int x = 0; x < Bayer.SIZE; x++) {
                    seen.add(GbaColor.toHex(layer.colorAt(onTheBoundary, PALETTE, x, y)));
                }
            }
            assertEquals(2, seen.size(),
                    "a dithered boundary is a scatter of the two neighbouring colours");

            // And the middle of a band is not scattered: a dither that reached this far would be
            // grain over the whole gradient rather than a softened edge.
            java.util.Set<String> middle = new java.util.LinkedHashSet<>();
            for (int y = 0; y < Bayer.SIZE; y++) {
                for (int x = 0; x < Bayer.SIZE; x++) {
                    middle.add(GbaColor.toHex(layer.colorAt(0.5 / 4, PALETTE, x, y)));
                }
            }
            assertEquals(1, middle.size(), "the middle of a band must stay one flat colour");
        }

        @Test
        @DisplayName("a stop fixed to a colour ignores the palette, and one on a role follows it")
        void stopsResolveTheirOwnWay() {
            GradientStop fixed = GradientStop.of(0, Color.web("#ff0000"));
            GradientStop role = GradientStop.of(1, PaletteRole.PRIMARY);

            assertEquals(fixed.color(PALETTE), fixed.color(Moods.LIGHT.palette()),
                    "a fixed stop must not change with the mood");
            assertNotEquals(role.color(PALETTE), role.color(Moods.LIGHT.palette()),
                    "a stop on a role has to follow the mood - that is what it is for");
        }

        @Test
        @DisplayName("a fixed stop is snapped to the hardware grid like everything else")
        void fixedStopsAreSnapped() {
            Color awkward = Color.rgb(3, 250, 130);
            GradientStop stop = GradientStop.of(0.5, awkward);

            assertEquals(GbaColor.snap(awkward), stop.fixed());
        }

        @Test
        @DisplayName("a stop that names neither a role nor a colour is refused")
        void aStopMustNameSomething() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GradientStop(0.5, null, null));
        }
    }

    @Nested
    @DisplayName("image layers")
    class Images {

        /**
         * A mood folder is unzipped from something a teammate sent, so the file name in it is
         * untrusted input. This is the only thing between that name and {@code Path.resolve}.
         */
        @Test
        @DisplayName("refuse a file name that could escape the mood's own folder")
        void pathsAreRefused() {
            for (String name : List.of("../secret.png", "a/b.png", "..\\secret.png", "..")) {
                assertThrows(IllegalArgumentException.class,
                        () -> ImageLayer.tiled(name),
                        "\"" + name + "\" is a path, not a file inside the mood");
            }
        }

        @Test
        @DisplayName("refuse a blank name")
        void blankNamesAreRefused() {
            assertThrows(IllegalArgumentException.class, () -> ImageLayer.tiled("  "));
        }

        @Test
        @DisplayName("clamp the magnification to whole steps that exist")
        void pixelScaleIsClamped() {
            assertEquals(1, ImageLayer.tiled("a.png").withPixelScale(0).pixelScale());
            assertEquals(ImageLayer.MAX_PIXEL_SCALE,
                    ImageLayer.tiled("a.png").withPixelScale(99).pixelScale());
        }

        /**
         * An animated GIF can never join the flattened backdrop, whatever its scroll rate says -
         * which is a different reason from scrolling and has to be reported separately.
         */
        @Test
        @DisplayName("a GIF is live even when it stands still")
        void animatedIsAlwaysLive() {
            ImageLayer gif = ImageLayer.tiled("stars.gif");

            assertTrue(gif.animated(), "the extension is what says it moves");
            assertTrue(gif.style().isStatic(), "it is not scrolling");
            assertTrue(gif.isLive(), "but it still has to be redrawn every frame");
        }
    }

    @Nested
    @DisplayName("procedural layers")
    class Procedural {

        /**
         * The reason this uses the SplitMix64 finaliser rather than FNV-1a, which the boot screen's
         * glitch had to learn the hard way: FNV avalanches poorly in its high bits over two small
         * integers, and a field whose every cell cleared the threshold is not a starfield, it is a
         * grid.
         */
        @Test
        @DisplayName("scatter their stars rather than filling or clearing the whole field")
        void starsAreScattered() {
            ProceduralLayer field = new ProceduralLayer(LayerStyle.behind(),
                    ProceduralLayer.Pattern.STARFIELD, 4, 20250816L);

            int stars = 0;
            int cells = 0;
            for (int y = 0; y < 60; y++) {
                for (int x = 0; x < 60; x++) {
                    cells++;
                    if (field.hasStar(x, y, 26)) {
                        stars++;
                    }
                }
            }
            assertTrue(stars > 0, "no stars at all is an empty layer");
            assertTrue(stars < cells / 4, "one cell in four is not a starfield, it is a texture");
        }

        @Test
        @DisplayName("give the same mood the same stars every launch")
        void starsAreSeeded() {
            ProceduralLayer first = new ProceduralLayer(LayerStyle.behind(),
                    ProceduralLayer.Pattern.STARFIELD, 4, 42);
            ProceduralLayer same = new ProceduralLayer(LayerStyle.behind(),
                    ProceduralLayer.Pattern.STARFIELD, 4, 42);
            ProceduralLayer other = first.withSeed(43);

            boolean anyDifference = false;
            for (int x = 0; x < 200; x++) {
                assertEquals(first.hasStar(x, 7, 20), same.hasStar(x, 7, 20),
                        "the same seed has to give the same field");
                anyDifference |= first.hasStar(x, 7, 20) != other.hasStar(x, 7, 20);
            }
            assertTrue(anyDifference, "a different seed has to give a different field");
        }

        @Test
        @DisplayName("vary their stars' brightness, so a field has depth")
        void starsVaryInBrightness() {
            ProceduralLayer field = new ProceduralLayer(LayerStyle.behind(),
                    ProceduralLayer.Pattern.STARFIELD, 4, 1);

            double lowest = 1;
            double highest = 0;
            for (int x = 0; x < 200; x++) {
                double brightness = field.starBrightness(x, 3);
                lowest = Math.min(lowest, brightness);
                highest = Math.max(highest, brightness);
                assertTrue(brightness > 0 && brightness <= 1);
            }
            assertTrue(highest - lowest > 0.4, "every star the same brightness is a flat scatter");
        }

        @Test
        @DisplayName("draw in a palette role, so all four follow the mood")
        void patternsDrawInRoles() {
            for (ProceduralLayer.Pattern pattern : ProceduralLayer.Pattern.values()) {
                ProceduralLayer layer = ProceduralLayer.of(pattern, 0.2);
                assertEquals(PALETTE.color(layer.role(), 0.5), layer.color(PALETTE, 0.5),
                        pattern + " does not resolve through the palette");
            }
        }

        /**
         * All four of these are things seen <em>through</em>. Behind the interface a scanline
         * pattern is a scanline pattern nobody can see - which is also why the band's 0.35 cap
         * matters here more than anywhere else.
         */
        @Test
        @DisplayName("default to the band they are meant to be seen in, and are capped there")
        void proceduralDefaultsAreGauze() {
            ProceduralLayer layer = ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 1.0);

            assertEquals(ZBand.ABOVE_CONTENT, layer.style().zBand());
            assertEquals(ZBand.ABOVE_CONTENT.maxOpacity(), layer.style().opacity(), 1e-9);
        }
    }

    @Nested
    @DisplayName("the stack")
    class Stack {

        @Test
        @DisplayName("holds at most six layers, and says so rather than dropping one")
        void sixIsTheCeiling() {
            Mood mood = Moods.DARK;
            for (int i = 0; i < MoodLayer.MAX_LAYERS; i++) {
                mood = mood.withLayerAdded(
                        ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.1));
            }
            final Mood full = mood;

            assertThrows(IllegalArgumentException.class, () -> full.withLayerAdded(
                    ProceduralLayer.of(ProceduralLayer.Pattern.VIGNETTE, 0.1)));
        }

        @Test
        @DisplayName("reorders without changing any layer, because order is what a stack is")
        void reorderingKeepsTheLayers() {
            MoodLayer first = ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.1);
            MoodLayer second = ProceduralLayer.of(ProceduralLayer.Pattern.VIGNETTE, 0.2);
            Mood mood = Moods.DARK.withLayers(List.of(first, second));

            Mood moved = mood.withLayerMoved(0, 1);

            assertEquals(List.of(second, first), moved.layers());
            assertEquals(mood.layers(), moved.withLayerMoved(1, -1).layers());
        }

        @Test
        @DisplayName("ignores a move that would leave the stack rather than throwing at the user")
        void movesOffTheEndDoNothing() {
            Mood mood = Moods.DARK.withLayerAdded(
                    ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.1));

            assertEquals(mood.layers(), mood.withLayerMoved(0, -1).layers());
            assertEquals(mood.layers(), mood.withLayerMoved(0, 1).layers());
        }

        /**
         * The single question the renderer asks before deciding whether to start a frame loop at
         * all. Every preset made only of static layers has to answer no, or the eight of them stop
         * being free.
         */
        @Test
        @DisplayName("needs an animation only when something actually moves")
        void animationFollowsTheLayers() {
            assertFalse(Moods.DARK.needsAnimation(), "a mood with no layers cannot move");
            assertFalse(Moods.SUNSET_WILDS.needsAnimation(),
                    "a static gradient is flattened once and never redrawn");
            assertTrue(Moods.SKY_GARDEN.needsAnimation(), "its clouds drift");
            assertTrue(Moods.DARK.withReactive(true).needsAnimation(),
                    "a reactive mood follows the music whatever its layers do");
        }

        @Test
        @DisplayName("hides a layer without deleting it")
        void visibilityIsSeparateFromExistence() {
            Mood mood = Moods.DARK.withLayerAdded(
                    ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.2));
            Mood hidden = mood.withLayer(0,
                    mood.layers().get(0).withStyle(mood.layers().get(0).style().withVisible(false)));

            assertEquals(1, hidden.layers().size());
            assertTrue(hidden.visibleLayers().isEmpty());
        }
    }
}
