package com.eia.superdwarfkart.mood;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "GBA-ify" pass: any imported picture, made to belong in this application.
 *
 * <p>The property that matters is the second step. Quantising to <em>the mood's</em> sixteen colours
 * rather than to sixteen chosen from the image is what makes an imported photograph and a hand-drawn
 * tile look like they came from the same machine - and it is what makes an import restyle itself
 * when the palette changes.
 */
@DisplayName("Image quantizer")
class ImageQuantizerTest {

    private static final Palette PALETTE = Palette.defaultPalette();

    /** A smooth horizontal ramp: the picture a sixteen-colour bank has the most trouble with. */
    private static Image ramp(int width, int height) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double t = x / (double) (width - 1);
                writer.setColor(x, y, Color.color(t, 1 - t, Math.abs(0.5 - t) * 2));
            }
        }
        return image;
    }

    private static Set<String> coloursIn(Image image) {
        Set<String> seen = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = image.getPixelReader().getColor(x, y);
                if (pixel.getOpacity() > 0) {
                    seen.add(GbaColor.toHex(pixel));
                }
            }
        }
        return seen;
    }

    @Nested
    @DisplayName("quantising")
    class Quantising {

        @Test
        @DisplayName("uses nothing but the mood's own sixteen colours")
        void everythingLandsOnThePalette() {
            Image quantised = ImageQuantizer.quantize(ramp(64, 8), PALETTE, false);

            Set<String> palette = new HashSet<>();
            for (PaletteRole role : PaletteRole.values()) {
                palette.add(GbaColor.toHex(PALETTE.color(role)));
            }
            for (String colour : coloursIn(quantised)) {
                assertTrue(palette.contains(colour),
                        colour + " is not in the palette the picture was quantised onto");
            }
        }

        @Test
        @DisplayName("holds at most sixteen colours, whatever the source held")
        void neverMoreThanSixteen() {
            Image source = ramp(64, 8);
            assertTrue(coloursIn(source).size() > PaletteRole.COUNT,
                    "this test is pointless unless the source has more than sixteen colours");

            assertTrue(coloursIn(ImageQuantizer.quantize(source, PALETTE, false)).size()
                            <= PaletteRole.COUNT);
        }

        /**
         * The same import against two moods has to come out differently, or the quantiser is not
         * using the palette it was handed - which is the one thing it exists to do.
         */
        @Test
        @DisplayName("gives a different answer for a different mood")
        void theResultFollowsThePalette() {
            Image source = ramp(32, 4);

            Set<String> dark = coloursIn(ImageQuantizer.quantize(source, Moods.DARK.palette(), false));
            Set<String> light = coloursIn(
                    ImageQuantizer.quantize(source, Moods.LIGHT.palette(), false));

            assertNotEquals(dark, light);
        }

        /**
         * An imported PNG with a cut-out background has to keep it: index 0 of a GBA bank is the
         * transparency key, and a cut-out filled in with the nearest palette colour is a sprite on
         * a rectangle.
         */
        @Test
        @DisplayName("keeps a transparent pixel transparent")
        void transparencySurvives() {
            WritableImage source = new WritableImage(2, 1);
            source.getPixelWriter().setColor(0, 0, Color.TRANSPARENT);
            source.getPixelWriter().setColor(1, 0, Color.RED);

            Image quantised = ImageQuantizer.quantize(source, PALETTE, false);

            assertEquals(0, quantised.getPixelReader().getColor(0, 0).getOpacity());
            assertEquals(1, quantised.getPixelReader().getColor(1, 0).getOpacity());
        }

        /**
         * Sixteen colours is few enough that a smooth sky comes out as three flat bands. The dither
         * is what turns that back into a gradient - so it has to actually change the picture.
         */
        @Test
        @DisplayName("dithering changes the picture rather than being a switch that does nothing")
        void ditherDoesSomething() {
            Image source = ramp(64, 16);

            Image plain = ImageQuantizer.quantize(source, PALETTE, false);
            Image dithered = ImageQuantizer.quantize(source, PALETTE, true);

            boolean anyDifference = false;
            for (int y = 0; y < 16 && !anyDifference; y++) {
                for (int x = 0; x < 64; x++) {
                    if (!plain.getPixelReader().getColor(x, y)
                            .equals(dithered.getPixelReader().getColor(x, y))) {
                        anyDifference = true;
                        break;
                    }
                }
            }
            assertTrue(anyDifference, "dithering left every pixel exactly where it was");
        }
    }

    @Nested
    @DisplayName("downscaling")
    class Downscaling {

        @Test
        @DisplayName("reduces to the GBA's own screen and keeps the proportions")
        void reducesToTheHardwareScreen() {
            Image reduced = ImageQuantizer.downscale(ramp(960, 640),
                    ImageQuantizer.GBA_WIDTH, ImageQuantizer.GBA_HEIGHT);

            assertEquals(ImageQuantizer.GBA_WIDTH, reduced.getWidth());
            assertEquals(ImageQuantizer.GBA_HEIGHT, reduced.getHeight());
        }

        @Test
        @DisplayName("does not stretch a picture of a different shape to fit")
        void aspectIsPreserved() {
            Image reduced = ImageQuantizer.downscale(ramp(1000, 250), 240, 160);

            assertEquals(240, reduced.getWidth());
            assertEquals(60, reduced.getHeight(), "a 4:1 picture came back a different shape");
        }

        /**
         * Blowing a small picture up here and then magnifying it again at draw time would
         * double-scale it and lose exactly the crisp edges the whole exercise is about.
         */
        @Test
        @DisplayName("leaves a picture already small enough completely alone")
        void smallPicturesAreUntouched() {
            Image small = ramp(32, 32);

            assertSame(small, ImageQuantizer.downscale(small, 240, 160));
        }
    }

    @Nested
    @DisplayName("importing into a tile")
    class TileImport {

        @Test
        @DisplayName("produces one index per pixel, all of them in the palette's range")
        void indicesAreInRange() {
            int[] indices = ImageQuantizer.toTileIndices(ramp(16, 16), 16, PALETTE);

            assertEquals(16 * 16, indices.length);
            for (int index : indices) {
                assertTrue(index >= 0 && index < PaletteRole.COUNT, "out of range: " + index);
            }
        }

        /**
         * Refused rather than downscaled, and the message says why. A 512x512 illustration reduced
         * to 16x16 is not a small version of itself, it is sixteen pixels of mud - and handing that
         * back as though the import had worked is worse than saying no.
         */
        @Test
        @DisplayName("refuses a picture too large to be recognisable as a tile, and says so")
        void oversizedImportsAreRefused() {
            Image big = ramp(128, 128);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> ImageQuantizer.toTileIndices(big, 16, PALETTE));

            assertTrue(thrown.getMessage().contains("128x128"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains(String.valueOf(PixelTile.MAX_IMPORT_SIZE)),
                    thrown.getMessage());
        }

        @Test
        @DisplayName("accepts one right at the limit")
        void theLimitItselfIsAccepted() {
            int[] indices = ImageQuantizer.toTileIndices(
                    ramp(PixelTile.MAX_IMPORT_SIZE, PixelTile.MAX_IMPORT_SIZE), 32, PALETTE);

            assertEquals(32 * 32, indices.length);
        }

        @Test
        @DisplayName("reads a transparent pixel as index 0")
        void transparencyBecomesIndexZero() {
            WritableImage source = new WritableImage(8, 8);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    source.getPixelWriter().setColor(x, y, Color.TRANSPARENT);
                }
            }

            int[] indices = ImageQuantizer.toTileIndices(source, 8, PALETTE);

            for (int index : indices) {
                assertEquals(PixelTile.TRANSPARENT, index);
            }
        }

        @Test
        @DisplayName("maps a colour onto the role nearest it")
        void nearestRoleWins() {
            assertEquals(PaletteRole.POSITIVE.ordinal(),
                    ImageQuantizer.indexOf(PALETTE.color(PaletteRole.POSITIVE), PALETTE));
            assertEquals(PaletteRole.NEGATIVE.ordinal(),
                    ImageQuantizer.indexOf(PALETTE.color(PaletteRole.NEGATIVE), PALETTE));
        }
    }
}
