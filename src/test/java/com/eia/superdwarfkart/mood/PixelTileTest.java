package com.eia.superdwarfkart.mood;

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
 * The tile format, and the one decision everything good about the pixel editor falls out of.
 *
 * <p>A tile stores <strong>palette indices, not colours</strong>. That is why the editor's picker
 * cannot produce something out of palette, why one pixel is one hex digit in the file, and - the
 * part worth the most - why changing a mood's palette recolours every tile in it instantly. The last
 * of those is tested here rather than taken on trust, because it is the property that would be lost
 * silently the moment somebody "simplified" the format to RGB.
 */
@DisplayName("Pixel tiles")
class PixelTileTest {

    /** A 4x4 corner shape written out longhand, at the smallest legal size. */
    private static PixelTile sample() {
        return PixelTile.fromRows(8, 4, List.of(List.of(
                "05500000",
                "05500000",
                "55550000",
                "00000000",
                "00000000",
                "0000aaaa",
                "0000a00a",
                "0000aaaa")));
    }

    @Nested
    @DisplayName("the format")
    class Format {

        @Test
        @DisplayName("round-trips through its stored rows without losing a pixel")
        void rowsRoundTrip() {
            PixelTile tile = sample();

            PixelTile again = PixelTile.fromRows(tile.size(), tile.fps(), tile.rows());

            assertEquals(tile.rows(), again.rows());
        }

        @Test
        @DisplayName("stores one hex digit per pixel, so the file diffs line by line")
        void oneDigitPerPixel() {
            List<String> rows = sample().rows().get(0);

            assertEquals(8, rows.size());
            for (String row : rows) {
                assertEquals(8, row.length(), "a row is one character per pixel: " + row);
            }
        }

        @Test
        @DisplayName("reads every index from 0 to f")
        void everyIndexIsReadable() {
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                row.append(Character.forDigit(i, 16));
            }
            List<String> rows = new ArrayList<>();
            for (int y = 0; y < 16; y++) {
                rows.add(row.toString());
            }

            PixelTile tile = PixelTile.fromRows(16, 4, List.of(rows));

            for (int i = 0; i < 16; i++) {
                assertEquals(i, tile.indexAt(0, i, 0));
            }
        }

        @Test
        @DisplayName("refuses a row of the wrong width, naming what it found")
        void badRowsAreRefused() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> PixelTile.fromRows(8, 4, List.of(List.of(
                            "0550", "05500000", "55550000", "00000000",
                            "00000000", "00000000", "00000000", "00000000"))));

            assertTrue(thrown.getMessage().contains("Row 0"), thrown.getMessage());
        }

        @Test
        @DisplayName("refuses a character that is not a palette index")
        void badCharactersAreRefused() {
            assertThrows(IllegalArgumentException.class,
                    () -> PixelTile.fromRows(8, 4, List.of(List.of(
                            "0z500000", "05500000", "55550000", "00000000",
                            "00000000", "00000000", "00000000", "00000000"))));
        }

        @Test
        @DisplayName("offers only the three sizes, because these are tiles rather than paintings")
        void onlyThreeSizes() {
            assertTrue(PixelTile.isValidSize(8));
            assertTrue(PixelTile.isValidSize(16));
            assertTrue(PixelTile.isValidSize(32));
            assertFalse(PixelTile.isValidSize(24));
            assertThrows(IllegalArgumentException.class, () -> PixelTile.blank(64));
        }

        /**
         * A hand-edited mood file with a stray digit should draw the tile it mostly describes, not
         * refuse to open the mood (ground rule 5). An index is clamped where a malformed row is
         * refused, and the difference is that one is recoverable and the other is not.
         */
        @Test
        @DisplayName("clamps an out-of-range index rather than refusing the whole tile")
        void indicesAreClamped() {
            PixelTile tile = new PixelTile(8, 4, List.of(new int[] {
                99, -3, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0}));

            assertEquals(PaletteRole.COUNT - 1, tile.indexAt(0, 0, 0));
            assertEquals(0, tile.indexAt(0, 1, 0));
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("is immutable: writing a pixel yields a new tile and leaves the old one")
        void editsAreCopies() {
            PixelTile before = sample();

            PixelTile after = before.withPixel(0, 0, 0, 9);

            assertEquals(0, before.indexAt(0, 0, 0), "the original was modified");
            assertEquals(9, after.indexAt(0, 0, 0));
        }

        @Test
        @DisplayName("ignores a write outside the tile rather than growing or throwing")
        void writesOutsideAreIgnored() {
            PixelTile tile = sample();

            assertEquals(tile.rows(), tile.withPixel(0, -1, 0, 5).rows());
            assertEquals(tile.rows(), tile.withPixel(0, 99, 0, 5).rows());
        }

        @Test
        @DisplayName("reads outside the tile as transparent, so a stroke near an edge is safe")
        void readsOutsideAreTransparent() {
            assertEquals(PixelTile.TRANSPARENT, sample().indexAt(0, -1, -1));
            assertEquals(PixelTile.TRANSPARENT, sample().indexAt(0, 99, 99));
        }

        @Test
        @DisplayName("holds up to four frames and refuses to go past that or below one")
        void frameCountIsBounded() {
            PixelTile tile = sample();
            for (int i = 1; i < PixelTile.MAX_FRAMES; i++) {
                tile = tile.withFrameAdded();
            }
            assertEquals(PixelTile.MAX_FRAMES, tile.frameCount());

            assertEquals(PixelTile.MAX_FRAMES, tile.withFrameAdded().frameCount(),
                    "a fifth frame must be refused rather than added");

            PixelTile one = sample();
            assertEquals(1, one.withFrameRemoved().frameCount(),
                    "removing the last frame would leave a tile with nothing in it");
        }

        @Test
        @DisplayName("a new frame starts as a copy of the last, which is what an animator wants")
        void newFramesAreCopies() {
            PixelTile tile = sample().withFrameAdded();

            assertEquals(tile.rows().get(0), tile.rows().get(1));
        }

        @Test
        @DisplayName("advances frames on a clock the caller supplies, never one of its own")
        void framesFollowTheCallersClock() {
            PixelTile tile = sample().withFrameAdded().withFps(4);

            assertEquals(0, tile.frameAt(0));
            assertEquals(1, tile.frameAt(0.3));
            assertEquals(0, tile.frameAt(0.6));
            // A caller may hand this a negative time - a seek to before the start of a track does
            // exactly that - so it wraps rather than throwing or reading off the front of the array.
            assertTrue(tile.frameAt(-0.1) >= 0 && tile.frameAt(-0.1) < tile.frameCount(),
                    "a clock that ran backwards must still land on a frame");
        }

        @Test
        @DisplayName("a single-frame tile shows its one frame at every moment")
        void oneFrameNeverAdvances() {
            PixelTile tile = sample();

            assertEquals(0, tile.frameAt(0));
            assertEquals(0, tile.frameAt(9999));
        }

        @Test
        @DisplayName("knows when it is empty, so an empty tile is not saved as a layer")
        void blankIsRecognised() {
            assertTrue(PixelTile.blank(16).isBlank());
            assertFalse(sample().isBlank());
        }
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        /**
         * <strong>The property that makes the whole format worth having.</strong> Import a Lospec
         * palette and a background somebody drew by hand restyles itself, because there is no
         * colour in the tile to be out of date. Store RGB instead and this is gone, silently.
         */
        @Test
        @DisplayName("recolours itself when the palette changes, because it holds no colours")
        void tilesFollowThePalette() {
            PixelTile tile = sample();

            javafx.scene.image.Image dark = tile.toImage(0, Moods.DARK.palette());
            javafx.scene.image.Image light = tile.toImage(0, Moods.LIGHT.palette());

            // (0,1) is index 5, TEXT_PRIMARY: near-white in the dark mood and near-black in the
            // light one, which is as loud a difference as the palette has.
            assertNotEquals(dark.getPixelReader().getColor(1, 0),
                    light.getPixelReader().getColor(1, 0),
                    "a tile that did not follow the palette is storing colours");
            assertEquals(Moods.DARK.color(PaletteRole.TEXT_PRIMARY),
                    dark.getPixelReader().getColor(1, 0));
        }

        /**
         * Index 0 is the transparency key, matching the GBA convention where entry 0 of a bank is
         * the colour that is not drawn. A tile that wants the background colour names it explicitly.
         */
        @Test
        @DisplayName("draws index 0 as nothing at all")
        void indexZeroIsTransparent() {
            javafx.scene.image.Image image = sample().toImage(0, Moods.DARK.palette());

            assertEquals(0, image.getPixelReader().getColor(0, 0).getOpacity(),
                    "index 0 has to be transparent, not the background colour");
            assertEquals(1, image.getPixelReader().getColor(1, 0).getOpacity());
        }

        @Test
        @DisplayName("draws at 1:1, so the caller decides the integer magnification")
        void rendersAtItsOwnSize() {
            javafx.scene.image.Image image = sample().toImage(0, Moods.DARK.palette());

            assertEquals(8, image.getWidth());
            assertEquals(8, image.getHeight());
        }
    }
}
