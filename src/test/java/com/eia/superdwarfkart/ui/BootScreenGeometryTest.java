package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the arithmetic the boot screen's drag rests on.
 *
 * <p>Building the screen needs a JavaFX toolkit and there is none in this run, but that is not why
 * these are static: a drag is <em>unphotographable</em>. A still picture of one is a picture of a
 * stationary cartridge, so a screenshot cannot establish that the gesture goes anywhere, that it
 * stops at both ends, or that letting go early refuses. The smoke test drives the real thing with
 * real mouse events; these are the rules it is driven against.
 */
@DisplayName("Boot screen geometry")
class BootScreenGeometryTest {

    @Test
    @DisplayName("the travel is a share of the cartridge's own height, so replacement art seats the same")
    void travelFollowsTheArtwork() {
        double small = BootScreen.seatTravel(200);
        double large = BootScreen.seatTravel(400);

        assertTrue(large > small, "a taller cartridge has further to go");
        assertEquals(BootScreen.SEAT_SHARE, (large - small) / 200, 1e-9,
                "the difference is exactly the seat share, so it is a fraction of the art rather "
                        + "than a pixel count somebody wrote down");
    }

    @Test
    @DisplayName("there is always somewhere to drag to, even with no artwork at all")
    void travelSurvivesMissingArtwork() {
        assertEquals(BootScreen.FALLBACK_TRAVEL, BootScreen.seatTravel(0), 1e-9);
        assertEquals(BootScreen.FALLBACK_TRAVEL, BootScreen.seatTravel(-40), 1e-9);
        assertTrue(BootScreen.seatTravel(0) > 0,
                "a travel of zero would make the application unopenable with the art missing, "
                        + "which ground rule 5 does not allow");
    }

    @Test
    @DisplayName("progress runs from nothing to seated and stops at both ends")
    void progressIsClamped() {
        double travel = 200;

        assertEquals(0, BootScreen.insertionProgress(0, travel), 1e-9);
        assertEquals(0.5, BootScreen.insertionProgress(100, travel), 1e-9);
        assertEquals(1, BootScreen.insertionProgress(travel, travel), 1e-9);
        assertEquals(1, BootScreen.insertionProgress(travel * 4, travel), 1e-9,
                "dragging past the slot must not push the cartridge through the console");
        assertEquals(0, BootScreen.insertionProgress(-500, travel), 1e-9,
                "dragging upwards must not pull it out through the top of the screen");
    }

    @Test
    @DisplayName("progress only ever increases with the drag")
    void progressIsMonotonic() {
        double travel = 260;
        double previous = -1;
        for (double dragged = -100; dragged <= travel * 2; dragged += 7) {
            double progress = BootScreen.insertionProgress(dragged, travel);
            assertTrue(progress >= previous,
                    "the cartridge went backwards at " + dragged + "px");
            previous = progress;
        }
    }

    @Test
    @DisplayName("a travel of nothing cannot divide by zero")
    void noTravelIsNotADivisionByZero() {
        assertEquals(0, BootScreen.insertionProgress(50, 0), 1e-9);
        assertEquals(0, BootScreen.insertionProgress(50, -10), 1e-9);
    }

    @Test
    @DisplayName("the threshold is past halfway and short of the bottom")
    void theThresholdIsAThreshold() {
        assertFalse(BootScreen.isInserted(0), "an untouched cartridge has not been inserted");
        assertFalse(BootScreen.isInserted(BootScreen.INSERT_THRESHOLD - 0.01),
                "letting go early has to spring back, or the threshold is not one");
        assertTrue(BootScreen.isInserted(BootScreen.INSERT_THRESHOLD));
        assertTrue(BootScreen.isInserted(1));

        assertTrue(BootScreen.INSERT_THRESHOLD > 0.5,
                "past halfway: the gesture is deliberate and being made to repeat it reads as the "
                        + "drag not having worked");
        assertTrue(BootScreen.INSERT_THRESHOLD < 1,
                "short of the bottom: insisting on the last pixel makes the slot a target rather "
                        + "than a direction");
    }

    // ------------------------------------------------------------------
    // The name on the label
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the name is broken on its own separators, so it reads as a ROM label")
    void theNameBreaksWhereItAlreadyDoes() {
        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME, 14);

        assertTrue(lines.size() > 1, "43 characters do not fit on one 14-character line");
        for (int i = 0; i < lines.size() - 1; i++) {
            char last = lines.get(i).charAt(lines.get(i).length() - 1);
            assertTrue(last == '_' || last == '-',
                    "line " + i + " is \"" + lines.get(i) + "\", which ends mid-word. The name is "
                            + "punctuated like a filename and that is where it has to break.");
        }
        assertEquals(AppConfig.APP_NAME, String.join("", lines),
                "wrapping must not lose or add a character");
    }

    @Test
    @DisplayName("no line is ever wider than the label it is printed on")
    void noLineOverflowsTheLabel() {
        for (int perLine = 3; perLine <= 60; perLine++) {
            for (String line : BootScreen.wrapName(AppConfig.APP_NAME, perLine)) {
                assertTrue(line.length() <= perLine,
                        "\"" + line + "\" is " + line.length() + " characters on a "
                                + perLine + "-character label");
            }
        }
    }

    @Test
    @DisplayName("a run with no separator in it is cut rather than allowed to overhang")
    void anUnbreakableRunIsCut() {
        List<String> lines = BootScreen.wrapName("ABCDEFGHIJ", 4);

        assertEquals(List.of("ABCD", "EFGH", "IJ"), lines);
    }

    @Test
    @DisplayName("wrapping survives the degenerate cases rather than throwing at start-up")
    void wrappingIsTotal() {
        assertEquals(List.of(""), BootScreen.wrapName("", 10));
        assertEquals(List.of("abc"), BootScreen.wrapName("abc", 0));
        assertEquals(List.of("abc"), BootScreen.wrapName("abc", -5));
        assertFalse(BootScreen.wrapName(null, 10).isEmpty());
    }

    @Test
    @DisplayName("the name is sized so it fills the label without running off it")
    void theLabelSizeFitsBothWays() {
        // The real label, measured from the artwork: 238 x 389 of a 500 x 575 frame, drawn here at
        // the size the boot screen gives it on a 1440 x 900 window.
        double labelWidth = 205;
        double labelHeight = 335;

        double size = BootScreen.labelFontSize(labelWidth, labelHeight, AppConfig.APP_NAME);
        int perLine = (int) Math.floor(labelWidth / size);
        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME, perLine);

        assertTrue(size >= 5, "at " + size + "px the name on the cartridge is unreadable");
        for (String line : lines) {
            assertTrue(line.length() * size <= labelWidth,
                    "\"" + line + "\" is " + (line.length() * size) + "px on a "
                            + labelWidth + "px label");
        }
        assertTrue(lines.size() * size * 1.4 <= labelHeight,
                lines.size() + " lines at " + size + "px do not fit " + labelHeight + "px");
    }

    @Test
    @DisplayName("a label of nothing still yields a usable size rather than throwing")
    void theLabelSizeIsTotal() {
        assertTrue(BootScreen.labelFontSize(0, 0, AppConfig.APP_NAME) > 0);
        assertTrue(BootScreen.labelFontSize(-10, 300, AppConfig.APP_NAME) > 0);
        assertTrue(BootScreen.labelFontSize(200, 300, "") > 0);
        assertTrue(BootScreen.labelFontSize(200, 300, null) > 0);
    }

    // ------------------------------------------------------------------
    // The glitch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the same boot tears the same way twice")
    void theGlitchIsReproducible() {
        for (int band = 0; band < BootScreen.TEAR_BANDS; band++) {
            assertEquals(BootScreen.tearOffset(band, 7, 0.3, 1440),
                    BootScreen.tearOffset(band, 7, 0.3, 1440), 1e-12,
                    "band " + band + " moved somewhere different on an identical call");
        }
    }

    @Test
    @DisplayName("the tear collapses to nothing by the end, so it settles rather than stopping dead")
    void theGlitchDecays() {
        double early = maximumTear(0.05);
        double late = maximumTear(0.9);

        assertTrue(early > 0, "nothing tore at all at the start of the glitch");
        assertTrue(late < early,
                "the tear has to shrink across the effect, or it reads as a fault rather than as "
                        + "the machine settling: " + early + "px early, " + late + "px late");
        assertEquals(0, maximumTear(1), 1e-9, "it must be over when it is over");
    }

    @Test
    @DisplayName("a band is thrown sideways but never off the screen")
    void theTearStaysOnScreen() {
        double width = 1440;
        double cap = BootScreen.TEAR_AMPLITUDE * width;

        for (int frame = 0; frame < 40; frame++) {
            for (int band = 0; band < BootScreen.TEAR_BANDS; band++) {
                double offset = BootScreen.tearOffset(band, frame, frame / 40d, width);
                assertTrue(Math.abs(offset) <= cap + 1e-9,
                        "band " + band + " was thrown " + offset + "px, past the " + cap
                                + "px the amplitude allows");
            }
        }
    }

    @Test
    @DisplayName("only some rows move, so it reads as a signal breaking up rather than as noise")
    void notEveryBandMoves() {
        int moved = 0;
        int total = 0;
        for (int frame = 0; frame < 30; frame++) {
            for (int band = 0; band < BootScreen.TEAR_BANDS; band++) {
                total++;
                if (BootScreen.tearOffset(band, frame, 0.2, 1440) != 0) {
                    moved++;
                }
            }
        }
        assertTrue(moved > 0, "the glitch never displaced anything");
        assertTrue(moved < total * 0.85,
                "nearly every band moved (" + moved + " of " + total + "), which is static rather "
                        + "than tearing");
    }

    @Test
    @DisplayName("the whole sequence is short enough not to be in the way")
    void theSequenceIsBrief() {
        double total = BootScreen.GLITCH_SECONDS + BootScreen.LOADING_SECONDS;

        assertTrue(BootScreen.FLASH_SECONDS < BootScreen.GLITCH_SECONDS,
                "the flash is the start of the glitch, not the whole of it");
        assertTrue(total <= 3,
                "the boot ritual is " + total + "s, which stops being a flourish and starts being "
                        + "a wait");
    }

    /**
     * @param progress how far through the glitch
     * @return the largest displacement any band takes at that moment
     */
    private static double maximumTear(double progress) {
        double largest = 0;
        for (int frame = 0; frame < 30; frame++) {
            for (int band = 0; band < BootScreen.TEAR_BANDS; band++) {
                largest = Math.max(largest,
                        Math.abs(BootScreen.tearOffset(band, frame, progress, 1440)));
            }
        }
        return largest;
    }
}
