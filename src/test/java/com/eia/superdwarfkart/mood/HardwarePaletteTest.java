package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The palette the boot and shutdown screens draw through.
 *
 * <p>These two screens bracket the application - at one the system has not started and at the other
 * it has stopped - so they are deliberately not in the user's mood. Everything below is a property
 * that <strong>fails silently</strong> when it breaks: a boot screen drawn in someone's colour scheme
 * still draws perfectly, and a white flash that is only nearly white still flashes. Nothing throws
 * and every screenshot looks fine, which is why the properties are asserted rather than looked at.
 */
@DisplayName("Hardware palette")
class HardwarePaletteTest {

    /**
     * How far two channels of one colour may differ and still count as grey.
     *
     * <p>Not zero, because every value is snapped onto the GBA's 5-bit grid on the way in and the
     * three channels do not have to round in the same direction. One level is 8 of 255.
     */
    private static final double GREY_TOLERANCE = 8.5 / 255;

    @Test
    @DisplayName("is the same object every time, because it is asked for on every frame of the glitch")
    void isCached() {
        assertSame(Palette.hardware(), Palette.hardware());
    }

    @Test
    @DisplayName("is monochrome, so no mood's hue can reach a screen the software has not started on")
    void isMonochrome() {
        for (PaletteRole role : PaletteRole.values()) {
            Color color = Palette.hardware().color(role);
            double spread = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()))
                    - Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
            assertTrue(spread <= GREY_TOLERANCE,
                    role + " is " + GbaColor.toHex(color) + ", which has a hue - the boot screen is "
                            + "the machine rather than the mood, and a coloured one is the software's "
                            + "look arriving before the software does");
        }
    }

    @Test
    @DisplayName("the ground is true black, not nearly black")
    void theGroundIsBlack() {
        // SHADOW is what both screens fill themselves with, and the request was a black backdrop.
        // Snapping to the 5-bit grid keeps 0 at 0, so this survives the constructor.
        Color ground = Palette.hardware().color(PaletteRole.SHADOW);
        assertEquals(0, ground.getRed(), 1e-9);
        assertEquals(0, ground.getGreen(), 1e-9);
        assertEquals(0, ground.getBlue(), 1e-9);
        assertEquals(Palette.hardware().color(PaletteRole.BACKGROUND), ground,
                "the two grounds differ, so the screen has a shape on it nobody drew");
    }

    @Test
    @DisplayName("the flash is white, not nearly white")
    void theFlashIsWhite() {
        // The glitch flashes TEXT_PRIMARY at the instant the cartridge lands. Expanding 5 bits back
        // to 8 has to take 31 to 255 rather than to 248 - see GbaColor.expand - so a flash that came
        // out at 248 would mean that shift had regressed.
        Color flash = Palette.hardware().color(PaletteRole.TEXT_PRIMARY);
        assertEquals(1, flash.getRed(), 1e-9);
        assertEquals(1, flash.getGreen(), 1e-9);
        assertEquals(1, flash.getBlue(), 1e-9);
    }

    @Test
    @DisplayName("the flash reads over the text it flashes across")
    void theFlashIsBrighterThanWhatItCovers() {
        Palette palette = Palette.hardware();
        double flash = palette.color(PaletteRole.TEXT_PRIMARY).getBrightness();
        double title = palette.color(PaletteRole.PRIMARY).getBrightness();

        assertTrue(flash > title,
                "the flash is no brighter than the title it goes over, so at the moment of contact "
                        + "the screen does not appear to light up at all");
    }

    @Test
    @DisplayName("the glitch's two interference bands are far apart in lightness")
    void theInterferenceBandsSeparate() {
        // The tear draws alternate bands in ACCENT and NEGATIVE. Everywhere else those are the two
        // roles furthest apart in hue, which is what makes them read as interference; here there is no
        // hue to spend, so the whole separation has to come out of lightness.
        Palette palette = Palette.hardware();
        double accent = palette.color(PaletteRole.ACCENT).getBrightness();
        double negative = palette.color(PaletteRole.NEGATIVE).getBrightness();

        assertTrue(Math.abs(accent - negative) > 0.25,
                "the interference bands are " + Math.abs(accent - negative) + " apart in brightness, "
                        + "which in a palette with no hue in it is one band rather than two");
    }

    @Test
    @DisplayName("the loading bar's lit blocks stand out from its groove")
    void theBarReads() {
        Palette palette = Palette.hardware();
        double lit = palette.color(PaletteRole.PRIMARY).getBrightness();
        double groove = palette.shaded(PaletteRole.SHADOW, 0.5).getBrightness();
        double rim = palette.color(PaletteRole.OUTLINE).getBrightness();

        assertTrue(lit - groove > 0.5, "a filled block is indistinguishable from an empty one");
        assertTrue(rim > groove, "the bar's rim is darker than its groove, so it has no visible edge");
    }

    @Test
    @DisplayName("is not offered as a mood, because nobody chooses the machine's own colours")
    void isNotAMood() {
        assertFalse(Moods.builtIns().stream()
                        .anyMatch(mood -> mood.palette().name().equals(Palette.hardware().name())),
                "the console palette turned up in the switcher; it is deliberately not a look and "
                        + "has never been through MoodValidator");
    }

    @Test
    @DisplayName("the start-up rainbow is cached too, because the title asks for it every frame")
    void theRainbowIsCached() {
        assertSame(Palette.bootRainbow(), Palette.bootRainbow());
    }

    @Test
    @DisplayName("the start-up rainbow's six roles carry hues, which is the whole reason it exists")
    void theRainbowHasHues() {
        // The one property that matters: a rainbow read out of the console's own palette would be six
        // greys, and the title would cycle between them looking exactly like a static white title.
        Palette rainbow = Palette.bootRainbow();
        for (PaletteRole role : List.of(PaletteRole.PRIMARY, PaletteRole.POSITIVE,
                PaletteRole.METER_LOW, PaletteRole.ACCENT, PaletteRole.HIGHLIGHT,
                PaletteRole.NEGATIVE)) {
            Color colour = rainbow.color(role);
            double spread = Math.max(colour.getRed(), Math.max(colour.getGreen(), colour.getBlue()))
                    - Math.min(colour.getRed(), Math.min(colour.getGreen(), colour.getBlue()));
            assertTrue(spread > 0.25, role + " is grey in the start-up rainbow: " + colour);
        }
    }

    @Test
    @DisplayName("its six are distinct, so a sweep reads as a spectrum rather than as one colour")
    void theRainbowsSixAreDistinct() {
        Palette rainbow = Palette.bootRainbow();
        List<PaletteRole> cycle = List.of(PaletteRole.PRIMARY, PaletteRole.POSITIVE,
                PaletteRole.METER_LOW, PaletteRole.ACCENT, PaletteRole.HIGHLIGHT,
                PaletteRole.NEGATIVE);
        for (int i = 0; i < cycle.size(); i++) {
            for (int j = i + 1; j < cycle.size(); j++) {
                Color first = rainbow.color(cycle.get(i));
                Color second = rainbow.color(cycle.get(j));
                double apart = Math.sqrt(Math.pow(first.getRed() - second.getRed(), 2)
                        + Math.pow(first.getGreen() - second.getGreen(), 2)
                        + Math.pow(first.getBlue() - second.getBlue(), 2));
                assertTrue(apart > 0.2, cycle.get(i) + " and " + cycle.get(j)
                        + " are the same colour in the start-up rainbow");
            }
        }
    }

    @Test
    @DisplayName("and it is not a mood either - the machine's colour test is not a look to choose")
    void theRainbowIsNotAMood() {
        assertFalse(Moods.builtIns().stream()
                        .anyMatch(mood -> mood.palette().name().equals(Palette.bootRainbow().name())),
                "the start-up rainbow turned up in the switcher");
    }
}
