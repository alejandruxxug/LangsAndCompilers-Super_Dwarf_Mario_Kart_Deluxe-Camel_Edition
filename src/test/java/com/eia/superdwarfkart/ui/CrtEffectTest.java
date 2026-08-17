package com.eia.superdwarfkart.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shapes the boot and shutdown screens are seen through.
 *
 * <p>Static for the reason every other geometry test here is: <strong>none of this can be
 * photographed.</strong> A scanline is one pixel row, so a screenshot of the grille is a screenshot of
 * a picture with a faint texture on it that looks the same whether the period is two, three or wrong;
 * a vignette on a screen whose ground is already the darkest role there is is invisible everywhere
 * except under something drawn on top of it; the curvature's corners are black on a black screen and
 * are legible only through a rim that has to be there for the shape to exist at all; and the sync roll
 * is the one thing here that moves, which puts it out of reach of a still by definition. The numbers
 * are what can actually be checked.
 */
@DisplayName("CRT effect")
class CrtEffectTest {

    @Test
    @DisplayName("the grille is a grille: one dark row per cycle, and most of the screen untouched")
    void theGrilleLeavesThePictureBright() {
        int dark = 0;
        for (int row = 0; row < 300; row++) {
            if (CrtEffect.scanlineShade(row) >= CrtEffect.SCANLINE_SHADE) {
                dark++;
            }
        }
        assertEquals(100, dark, "one row in three should carry the full shade");

        // The whole point of a three-row period rather than a two-row one. At 50% duty the effect
        // stops being a grille and becomes the picture being dimmed, which is a different thing that
        // no screenshot would distinguish from this one. It is also the ceiling on how far the shade
        // may be deepened for drama: the title is read through this.
        double covered = 0;
        for (int row = 0; row < 300; row++) {
            covered += CrtEffect.scanlineShade(row);
        }
        assertTrue(covered / 300 < 0.2,
                "the grille should darken the screen by well under a fifth on average, was "
                        + covered / 300);
    }

    @Test
    @DisplayName("the grille repeats, so it cannot drift down a tall window")
    void theGrilleRepeats() {
        for (int row = 0; row < 200; row++) {
            assertEquals(CrtEffect.scanlineShade(row),
                    CrtEffect.scanlineShade(row + CrtEffect.SCANLINE_PERIOD),
                    "row " + row + " should match the one a period later");
        }
    }

    // ------------------------------------------------------------------
    // The tube
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the middle of the window is dead centre of the tube, and untouched by the curve")
    void theCurveLeavesTheMiddleAlone() {
        assertEquals(0, CrtEffect.curveX(0, 0), 1e-12);
        assertEquals(0, CrtEffect.curveY(0, 0), 1e-12);
        // Along either axis through the centre there is nothing to be pushed out by: a point half way
        // across the middle row is exactly half way across the tube. That is what keeps the title,
        // which sits there, from having to be measured against the curvature at all.
        assertEquals(0.5, CrtEffect.curveX(0.5, 0), 1e-12);
        assertEquals(0.5, CrtEffect.curveY(0, 0.5), 1e-12);
    }

    @Test
    @DisplayName("the corners are off the tube and the middle of every edge is on it")
    void theCornersAreOffTheGlass() {
        double width = 1440;
        double height = 800;
        for (double x : new double[] {0, width}) {
            for (double y : new double[] {0, height}) {
                assertFalse(CrtEffect.insideTube(x, y, width, height),
                        "the corner (" + x + "," + y + ") must be case rather than screen");
            }
        }
        // ... while the middle of every edge is still screen. That is the whole difference between a
        // television and a lens, and it is what the first attempt got wrong: taking the silhouette
        // from the raster's own warp gave a shape that met the window at exactly four points and fell
        // away from all of them, so the sides bowed in over their entire height.
        assertTrue(CrtEffect.insideTube(0, height / 2, width, height), "the left edge is glass");
        assertTrue(CrtEffect.insideTube(width, height / 2, width, height), "the right edge is glass");
        assertTrue(CrtEffect.insideTube(width / 2, 0, width, height), "the top edge is glass");
        assertTrue(CrtEffect.insideTube(width / 2, height, width, height), "the bottom edge is glass");
    }

    @Test
    @DisplayName("the sides run straight and only the corner arc takes anything off")
    void theSidesAreStraight() {
        double width = 1440;
        double height = 800;
        double radius = CrtEffect.cornerRadius(width, height);
        // Anywhere clear of the arc the row is the whole window. A shape that narrowed all the way up
        // would be an oval, however gently.
        for (double y = radius; y <= height - radius; y += 10) {
            assertEquals(1, CrtEffect.tubeHalfWidth(y, width, height), 1e-12,
                    "the straight part of the side must give up nothing, at " + y);
        }
        double previous = 1;
        for (double y = height - radius; y <= height; y += 2) {
            double half = CrtEffect.tubeHalfWidth(y, width, height);
            assertTrue(half <= previous + 1e-9, "the arc must only ever close, at " + y);
            assertTrue(half >= 0 && half <= 1, "a row is between none and all of the window");
            previous = half;
        }
        assertEquals(CrtEffect.tubeHalfWidth(height * 0.2, width, height),
                CrtEffect.tubeHalfWidth(height * 0.8, width, height), 1e-12,
                "and the tube is not lopsided");
    }

    @Test
    @DisplayName("tubeHalfWidth agrees with insideTube, which is the thing the mask actually draws")
    void theRowWidthMatchesTheMask() {
        // Two implementations of one boundary - one solved, one tested per pixel - and the roll
        // trusts the solved one to know where to stop. They have to be the same curve.
        double width = 1440;
        double height = 800;
        for (double y = 0; y <= height; y += 7) {
            double half = CrtEffect.tubeHalfWidth(y, width, height);
            double edge = width / 2 * half;
            if (half > 0.02) {
                assertTrue(CrtEffect.insideTube(width / 2 - edge + 1, y, width, height),
                        "just inside the reported width should be glass, at " + y);
            }
            if (half < 0.99) {
                assertFalse(CrtEffect.insideTube(width / 2 - edge - 1, y, width, height),
                        "just outside it should be case, at " + y);
            }
        }
    }

    @Test
    @DisplayName("everything the two screens lay out sits comfortably inside the glass")
    void theContentClearsTheCurve() {
        double width = 1440;
        double height = 800;
        // The captions on both screens are centred and the widest of them is the splash, so what the
        // corner arc costs them is width at the top and bottom. Nothing about running into the case
        // would throw - the caption would simply be cut off - so the rows they sit on are pinned here
        // instead. The prompt is at a tenth of the way down and the skip hint at about six sevenths.
        assertTrue(CrtEffect.tubeHalfWidth(height * 0.10, width, height) > 0.9,
                "the INSERT CARTRIDGE / SHUTTING DOWN row keeps almost all of its width");
        assertTrue(CrtEffect.tubeHalfWidth(height * 0.86, width, height) > 0.9,
                "and so does the row the skip hint sits on");
        assertEquals(1, CrtEffect.tubeHalfWidth(height * 0.42, width, height), 1e-12,
                "the title's own row is untouched");
    }

    @Test
    @DisplayName("the edge distance is measured in pixels, so the rim is as thick across as it is down")
    void theRimIsEvenOnAWideWindow() {
        double width = 1440;
        double height = 800;
        // Half way down the left edge and half way along the top edge, both twenty pixels in.
        // Measured in normalised units these would differ by the window's aspect ratio, and the rim
        // would come out nearly twice as wide down the sides as across the top on this window.
        assertEquals(20, CrtEffect.edgeDistance(20, height / 2, width, height), 1e-9,
                "twenty pixels in from the side should measure twenty");
        assertEquals(20, CrtEffect.edgeDistance(width / 2, 20, width, height), 1e-9,
                "and twenty pixels in from the top should too");
    }

    @Test
    @DisplayName("the edge distance changes sign exactly at the glass")
    void theEdgeDistanceIsSigned() {
        double width = 1440;
        double height = 800;
        assertEquals(height / 2, CrtEffect.edgeDistance(width / 2, height / 2, width, height), 1e-9,
                "from the centre the nearest edge is half the shorter side away");
        assertEquals(0, CrtEffect.edgeDistance(0, height / 2, width, height), 1e-9,
                "and the glass itself measures zero");
        assertTrue(CrtEffect.edgeDistance(0, 0, width, height) < 0, "the corner is on the case");
        assertTrue(CrtEffect.edgeDistance(0, 0, width, height)
                        > CrtEffect.edgeDistance(-40, -40, width, height),
                "further out must read as further out");
    }

    // ------------------------------------------------------------------
    // The shades and the lifts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the middle of the screen is left completely clear")
    void theVignetteLeavesTheMiddleAlone() {
        assertEquals(0, CrtEffect.vignetteShade(0, 0), "dead centre must be untouched");

        // Everything the two screens actually draw lives in the middle band, and a vignette that
        // started at the centre would read as a dirty screen rather than as a curved one.
        assertEquals(0, CrtEffect.vignetteShade(0, -0.3));
        assertEquals(0, CrtEffect.vignetteShade(-0.16, 0));
    }

    @Test
    @DisplayName("the corners are the darkest part, and it eases rather than stepping")
    void theVignetteGrowsOutwards() {
        double previous = -1;
        // Straight out along the diagonal, which is the direction that reaches the corner.
        for (double t = 0; t <= 1.0001; t += 0.05) {
            double shade = CrtEffect.vignetteShade(t, t);
            assertTrue(shade >= previous,
                    "the falloff must never come back towards the middle, at " + t);
            previous = shade;
        }
        assertEquals(CrtEffect.VIGNETTE_SHADE, CrtEffect.vignetteShade(1, 1), 1e-9,
                "the far corner of the tube should reach the full shade");
    }

    @Test
    @DisplayName("the vignette is symmetric, so a window is not darker down one side")
    void theVignetteIsSymmetric() {
        for (double x = -1; x <= 1.0001; x += 0.125) {
            for (double y = -1; y <= 1.0001; y += 0.125) {
                assertEquals(CrtEffect.vignetteShade(x, y), CrtEffect.vignetteShade(-x, -y), 1e-9,
                        "opposite sides of (" + x + "," + y + ") should match");
            }
        }
    }

    @Test
    @DisplayName("the glass edge darkens sharply and stops, rather than reaching the picture")
    void theGlassEdgeIsLocal() {
        assertEquals(CrtEffect.EDGE_SHADE, CrtEffect.edgeShade(0), 1e-9,
                "right against the glass it is at full strength");
        assertEquals(0, CrtEffect.edgeShade(CrtEffect.EDGE_PIXELS), 1e-9,
                "and it has to be gone by its own reach");
        assertEquals(0, CrtEffect.edgeShade(400), 1e-9, "well inside the picture it is nothing");

        double previous = Double.MAX_VALUE;
        for (double pixels = 0; pixels <= CrtEffect.EDGE_PIXELS; pixels += 2) {
            double shade = CrtEffect.edgeShade(pixels);
            assertTrue(shade <= previous + 1e-9, "it must only ever ease off, at " + pixels);
            previous = shade;
        }
    }

    @Test
    @DisplayName("the rim is the brightest part of the case, which is the only reason the corners show")
    void theRimIsWhatMakesTheCornersVisible() {
        // **The load-bearing property of this whole effect.** The case is SHADOW and so is the room on
        // both screens, so a corner blacked out is a corner nobody can see - the same trap that ate one
        // attempt at the boot glitch. What the eye actually reads is this: a lit curve where the glass
        // ends. If it ever reaches zero the curvature becomes invisible and nothing throws.
        assertTrue(CrtEffect.RIM_LIFT > 0.1, "the rim has to be visible against a black room");
        assertEquals(CrtEffect.RIM_LIFT, CrtEffect.bezelLift(0), 1e-9,
                "and it is brightest right against the glass");

        double previous = Double.MAX_VALUE;
        for (double pixels = 0; pixels >= -CrtEffect.BEZEL_PIXELS * 2; pixels -= 2) {
            double lift = CrtEffect.bezelLift(pixels);
            assertTrue(lift <= previous + 1e-9, "it must only ever fall away, at " + pixels);
            assertTrue(lift >= CrtEffect.BEZEL_LIFT - 1e-9,
                    "and never below the case's own colour, at " + pixels);
            previous = lift;
        }
        assertEquals(CrtEffect.BEZEL_LIFT, CrtEffect.bezelLift(-CrtEffect.BEZEL_PIXELS * 2), 1e-9,
                "well out into the case it settles to flat plastic");
    }

    @Test
    @DisplayName("the sheen is a band across one corner, not a wash over the whole screen")
    void theGlareIsLocal() {
        double peak = CrtEffect.glareLift(CrtEffect.GLARE_CENTRE, CrtEffect.GLARE_CENTRE);
        assertEquals(CrtEffect.GLARE_LIFT, peak, 1e-9, "it reaches its full strength on its own line");
        assertTrue(peak < 0.12,
                "and a reflection that lifted the room by more than a tenth would be a fog, was " + peak);

        // Gone by the opposite corner. A sheen everywhere is a screen that is not black, which on two
        // screens whose whole ground is SHADOW reads as a fault rather than as glass.
        assertEquals(0, CrtEffect.glareLift(1, 1), 1e-9);
        assertTrue(CrtEffect.glareLift(-1, -1) < peak,
                "the very corner is past the middle of the band, so it is dimmer than the peak");
    }

    // ------------------------------------------------------------------
    // The one thing that moves
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the roll only ever travels downwards, and off both ends")
    void theRollOnlyGoesOneWay() {
        double height = 800;
        double band = height * CrtEffect.ROLL_HEIGHT_SHARE;

        // Within one pass it rises monotonically. A band that reversed would read as the picture
        // being scrubbed rather than as a vertical hold that is slightly out.
        double previous = Double.NEGATIVE_INFINITY;
        for (double t = 0; t < CrtEffect.ROLL_SECONDS; t += 0.1) {
            double centre = CrtEffect.rollCentre(t, height);
            assertTrue(centre > previous, "the roll must always move down, at " + t);
            previous = centre;
        }

        // It starts entirely above the screen and ends entirely below it, so it enters and leaves
        // rather than appearing in place - the same rule the shutdown bar's sweep follows.
        assertEquals(-band / 2, CrtEffect.rollCentre(0, height), 1e-9);
        assertTrue(CrtEffect.rollCentre(CrtEffect.ROLL_SECONDS * 0.999, height)
                > height - band / 2 - 1);
    }

    @Test
    @DisplayName("the roll is two orders of magnitude under the cap on rhythmic effects")
    void theRollIsSlow() {
        double hz = 1 / CrtEffect.ROLL_SECONDS;
        // §8b caps anything full-screen and rhythmic at 3 Hz, for a darkened classroom. This is the
        // only thing on either bracket screen that repeats at all, and it is nowhere near it - which
        // is a property worth pinning rather than re-deriving from the constant every time.
        assertTrue(hz < 0.3, "the sync roll should be far under 3 Hz, was " + hz);
    }

    // ------------------------------------------------------------------
    // How the layers combine
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the shades compose rather than adding, so nothing overshoots past SHADOW")
    void theShadesCompose() {
        // The worst case: a full scanline row right at the glass in the corner of the tube, where the
        // vignette is at its strongest too. Added, those would exceed 1 and paint a band of pure black
        // through the part of the picture the falloff is meant to be easing.
        double scan = CrtEffect.scanlineShade(0);
        double vignette = CrtEffect.vignetteShade(1, 1);
        double edge = CrtEffect.edgeShade(0);
        double composed = 1 - (1 - scan) * (1 - vignette) * (1 - edge);
        assertTrue(composed <= 1, "a composed shade can never exceed one, was " + composed);
        assertTrue(composed >= Math.max(scan, Math.max(vignette, edge)),
                "and must be at least as dark as any of them on its own");
    }

    @Test
    @DisplayName("a shade and a lift collapse to one fill exactly, which is why there is one blit")
    void theTwoDirectionsCollapseToOneFill() {
        // The arithmetic the mask is baked with. Source-over of a shade s towards SHADOW followed by a
        // lift t towards TEXT_PRIMARY has to equal one source-over of the combined alpha carrying the
        // two weights - otherwise the single blit is an approximation of the two it replaced, and the
        // error would show up as a rim that is the wrong colour against a light palette rather than as
        // anything that throws.
        for (double shade = 0; shade <= 1.0001; shade += 0.1) {
            for (double lift = 0; lift <= 1.0001; lift += 0.1) {
                double alpha = 1 - (1 - shade) * (1 - lift);
                if (alpha <= 0) {
                    continue;
                }
                double toShadow = shade * (1 - lift) / alpha;
                double toLight = lift / alpha;
                assertEquals(1, toShadow + toLight, 1e-9,
                        "the two weights must be a whole colour at s=" + shade + " t=" + lift);

                // And the composite of an arbitrary content value has to come out the same either way.
                double content = 0.37;
                double sequential = (content * (1 - shade) + 0.0 * shade) * (1 - lift) + 1.0 * lift;
                double single = content * (1 - alpha) + (0.0 * toShadow + 1.0 * toLight) * alpha;
                assertEquals(sequential, single, 1e-9,
                        "one fill must equal two at s=" + shade + " t=" + lift);
            }
        }
    }
}
