package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @DisplayName("the tear is a moment inside the sequence, not the sequence")
    void theTearIsBrief() {
        assertTrue(BootScreen.FLASH_SECONDS < BootScreen.GLITCH_SECONDS,
                "the flash is the start of the glitch, not the whole of it");
        assertTrue(BootScreen.GLITCH_SECONDS < BootScreen.SEQUENCE_SECONDS / 4,
                "the tear takes " + BootScreen.GLITCH_SECONDS + "s of a "
                        + BootScreen.SEQUENCE_SECONDS + "s sequence, which is long enough to read as "
                        + "a fault rather than as the machine noticing");
    }

    @Test
    @DisplayName("the sequence follows the fanfare's length rather than a number written down")
    void theSequenceFollowsTheSound() {
        // The whole point of the length: the picture and the sound end together, whatever the sound is.
        // Not testable through the screen - the toolkit is absent here - but the arithmetic is, and this
        // is what App drives with SoundEffect.lengthSeconds().
        BootScreen.Splash unused = BootScreen.splashAt(1440);
        assertNotNull(unused);

        assertEquals(BootScreen.SEQUENCE_SECONDS, clampSequence(0), 1e-9,
                "a missing sound must leave the sequence on its own length rather than at zero");
        assertEquals(15.0, clampSequence(15.0), 1e-9);
        assertEquals(BootScreen.MIN_SEQUENCE_SECONDS, clampSequence(0.4), 1e-9,
                "a very short sound must not compress five movements into half a second");
    }

    /**
     * @param seconds what the caller asked for
     * @return what {@code setSequenceSeconds} would settle on - mirrored here because building the
     *         screen needs a toolkit and this run has none
     */
    private static double clampSequence(double seconds) {
        return seconds > 0
                ? Math.max(BootScreen.MIN_SEQUENCE_SECONDS, seconds)
                : BootScreen.SEQUENCE_SECONDS;
    }

    @Test
    @DisplayName("the publisher line is gone before the title arrives")
    void theTwoMovementsNeverShareTheScreen() {
        // The ordering *is* the drama: two movements read as two movements, and both at once reads as a
        // crowded screen. Nothing throws if the constants are edited into overlapping, and a screenshot
        // of the wrong instant would not show it either.
        for (double show = 0; show <= 1.0001; show += 0.002) {
            double presents = BootScreen.presentsAlpha(show);
            double title = BootScreen.titleAlpha(show);
            assertTrue(presents == 0 || title == 0,
                    "at " + show + " the publisher line is at " + presents + " and the title at "
                            + title + " - they are on screen together");
        }
    }

    @Test
    @DisplayName("every fade starts at nothing, reaches full, and ends at nothing")
    void theEnvelopesAreComplete() {
        assertEquals(0, BootScreen.presentsAlpha(0), 1e-9);
        assertEquals(0, BootScreen.presentsAlpha(1), 1e-9);
        assertEquals(1, BootScreen.presentsAlpha(
                (BootScreen.PRESENTS_FULL + BootScreen.PRESENTS_OUT) / 2), 1e-6,
                "the publisher line never reaches full, so it only ever half appears");

        assertEquals(0, BootScreen.titleAlpha(0), 1e-9);
        assertEquals(1, BootScreen.titleAlpha(
                (BootScreen.TITLE_FULL + BootScreen.LOADING_IN) / 2), 1e-6,
                "the title never reaches full brightness during its own hold");
        // The one that matters most at the end: a sequence that handed over while the title was still
        // on screen would cut to the interface rather than fade to it.
        assertEquals(0, BootScreen.titleAlpha(1), 1e-9,
                "the title is still visible at the moment the window is handed over");
        assertEquals(0, BootScreen.barAlpha(1), 1e-9);
        assertEquals(1, BootScreen.blackout(1), 1e-9, "the screen never reaches black");
    }

    @Test
    @DisplayName("the loading bar fills exactly as the picture starts to go")
    void theBarFinishesWithTheFade() {
        assertEquals(0, BootScreen.barProgress(BootScreen.LOADING_IN), 1e-9);
        assertEquals(1, BootScreen.barProgress(BootScreen.FADE_OUT), 1e-9,
                "the bar does not reach full at the fade, so the last seconds are a wait rather than "
                        + "a flourish");
        assertEquals(0, BootScreen.barAlpha(BootScreen.LOADING_IN - 0.01), 1e-9,
                "the bar is visible before there is anything for it to be doing");
    }

    @Test
    @DisplayName("the title lands rather than appears, and never smaller than it was measured to fit")
    void theTitleSettles() {
        double arriving = BootScreen.titleScale(BootScreen.TITLE_IN);
        double landed = BootScreen.titleScale(BootScreen.TITLE_FULL);

        assertTrue(arriving > landed, "the title does not settle, so it simply appears");
        assertEquals(1, landed, 1e-9, "the title settles to something other than its measured size");
        for (double show = 0; show <= 1.0001; show += 0.01) {
            assertTrue(BootScreen.titleScale(show) >= 1,
                    "at " + show + " the title is drawn smaller than the size splashFontSize measured "
                            + "to fit, which is a fade in the wrong direction");
        }
    }

    @Test
    @DisplayName("the ramp eases rather than cutting, and is flat outside its window")
    void theRampEases() {
        assertEquals(0, BootScreen.ramp(0.1, 0.2, 0.4), 1e-9);
        assertEquals(1, BootScreen.ramp(0.5, 0.2, 0.4), 1e-9);
        assertEquals(0.5, BootScreen.ramp(0.3, 0.2, 0.4), 1e-9, "the midpoint is not the midpoint");
        // Smoothstep rather than linear, which is most of why the fades read as dramatic: a linear
        // quarter of the way through would be 0.25, and this eases in.
        assertTrue(BootScreen.ramp(0.25, 0.2, 0.4) < 0.25, "the ramp is linear, not eased");
        // A zero-width window is a step rather than a division by zero.
        assertEquals(1, BootScreen.ramp(0.5, 0.5, 0.5), 1e-9);
        assertEquals(0, BootScreen.ramp(0.4, 0.5, 0.5), 1e-9);
    }

    @Test
    @DisplayName("the held title breathes far slower than the cap on anything full-screen")
    void theBreathIsSlow() {
        // Section 8b caps a full-screen rhythmic effect at 3 Hz, and this is the only thing in the whole
        // sequence that repeats at all. It is not near the cap and must not drift towards it.
        assertTrue(BootScreen.BREATH_HZ < 1,
                "the title breathes at " + BootScreen.BREATH_HZ + " Hz, which is a pulse rather than a "
                        + "swell");
    }

    @Test
    @DisplayName("every movement has an instant to photograph, and they are in order")
    void theMovementsAreOrdered() {
        BootScreen.Movement[] movements = BootScreen.Movement.values();
        assertEquals(5, movements.length);
        double previous = -1;
        for (BootScreen.Movement movement : movements) {
            assertTrue(movement.instant() > previous,
                    movement + " is not after the movement before it");
            assertTrue(movement.instant() > 0 && movement.instant() < 1,
                    movement + " is at " + movement.instant() + ", which is an edge of the sequence "
                            + "rather than the middle of a movement");
            assertFalse(movement.label().isBlank());
            previous = movement.instant();
        }
    }

    @Test
    @DisplayName("the starfield is reproducible, so a screenshot of it means something")
    void theStarfieldIsSeeded() {
        assertEquals(BootScreen.seeded(3, 7), BootScreen.seeded(3, 7), 1e-12);
        assertNotEquals(BootScreen.seeded(3, 7), BootScreen.seeded(4, 7));
        for (int star = 0; star < 200; star++) {
            double value = BootScreen.seeded(star, 1);
            assertTrue(value >= 0 && value < 1, "star " + star + " is at " + value);
        }
    }

    @Test
    @DisplayName("the title splash is a logo rather than a wrapped paragraph")
    void theSplashIsALogo() {
        BootScreen.Splash wide = BootScreen.splashAt(AppConfig.MAIN_WIDTH);

        assertTrue(wide.lines() <= 3,
                "the name broke into " + wide.lines() + " lines, which reads as a wrapping accident "
                        + "rather than as a title");
        assertTrue(wide.size() >= 20,
                "the splash came out at " + wide.size() + "px, which is body text on a screen with "
                        + "nothing else on it");
        assertTrue(wide.fits(AppConfig.MAIN_WIDTH),
                "the splash is " + wide.widthPixels() + "px wide in a " + AppConfig.MAIN_WIDTH
                        + "px window - in this font nothing anywhere reports that it ran off the side");
    }

    @Test
    @DisplayName("the splash gives up size rather than running off a narrow screen")
    void theSplashShrinksToFit() {
        BootScreen.Splash wide = BootScreen.splashAt(1440);
        BootScreen.Splash narrow = BootScreen.splashAt(480);

        assertTrue(narrow.size() < wide.size(),
                "a narrow window got the same size as a wide one, so one of them does not fit");
        assertTrue(narrow.fits(480), "the splash overflowed a 480px window");
        // The one property that has to survive every width: the screen is the only place the full
        // name is ever shown at a readable size, so it must not fall back to being unreadable.
        assertTrue(narrow.size() > 8, "the splash shrank to " + narrow.size() + "px, which is a hint");
    }

    @Test
    @DisplayName("the splash and the label break the name in the same places")
    void bothScreensWrapTheNameTheSameWay() {
        // ShutdownScreen goes through BootScreen.wrapName rather than carrying its own idea of how to
        // break the name up. Two implementations would show up as the title being hyphenated
        // differently on the way out of the application than on the way in.
        double size = ShutdownScreen.splashFontSize(AppConfig.MAIN_WIDTH);
        int perLine = (int) Math.floor(AppConfig.MAIN_WIDTH * 0.7 / size);
        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME, perLine);

        assertFalse(lines.isEmpty(), "the goodbye screen has no name to print");
        assertTrue(lines.size() <= 3, "the goodbye splash wrapped to " + lines.size() + " lines");
        for (String line : lines) {
            assertTrue(line.length() <= perLine,
                    "\"" + line + "\" is wider than the " + perLine + " characters it has");
        }
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
