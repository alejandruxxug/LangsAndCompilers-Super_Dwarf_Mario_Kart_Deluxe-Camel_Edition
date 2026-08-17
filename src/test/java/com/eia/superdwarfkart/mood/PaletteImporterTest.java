package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a palette out of the formats Aseprite and Lospec already export.
 *
 * <p>These are the forty lines that make twenty moods possible instead of two, so the cases below
 * are the ones a real file off the internet actually presents: a header, comments, tab-separated
 * names, a trailing blank line, and a palette that holds eight colours rather than sixteen because
 * nothing outside this application has a notion of sixteen anything.
 */
@DisplayName("Palette import")
class PaletteImporterTest {

    /** A real GIMP palette, header, comment, tabs and all. */
    private static final String GPL = """
            GIMP Palette
            Name: Sunset
            Columns: 4
            # Exported from Aseprite
            255 154  60\tOrange
             34  17  51\tPlum
            110 231 255\tSky
              0   0   0\tBlack
            """;

    /** What Lospec's "HEX" button downloads. */
    private static final String HEX = """
            1a1a2e
            16213e
            0f3460
            e94560
            """;

    @Nested
    @DisplayName("the formats")
    class Formats {

        @Test
        @DisplayName("reads a GIMP palette, skipping its header, comments and metadata")
        void readsGpl() {
            List<Color> colors = PaletteImporter.readColors(GPL);

            assertEquals(4, colors.size(), "read " + colors);
            assertEquals(GbaColor.of(255, 154, 60), colors.get(0));
            assertEquals(GbaColor.of(0, 0, 0), colors.get(3));
        }

        @Test
        @DisplayName("reads a plain hex list")
        void readsHex() {
            List<Color> colors = PaletteImporter.readColors(HEX);

            assertEquals(4, colors.size());
            assertEquals(GbaColor.web("#1a1a2e"), colors.get(0));
            assertEquals(GbaColor.web("#e94560"), colors.get(3));
        }

        /**
         * A leading hash is a comment in {@code .gpl} and a colour in {@code .hex}. The two are told
         * apart by what follows rather than by the extension, because plenty of files carry the
         * hash and plenty do not.
         */
        @Test
        @DisplayName("tells a commented line from a hash-prefixed colour")
        void hashIsBothACommentAndAColour() {
            List<Color> colors = PaletteImporter.readColors("""
                    # a comment about the palette
                    #ff8800
                    #00ff88
                    """);

            assertEquals(List.of(GbaColor.web("#ff8800"), GbaColor.web("#00ff88")), colors);
        }

        @Test
        @DisplayName("skips a line it cannot read rather than refusing the file")
        void junkLinesAreSkipped() {
            List<Color> colors = PaletteImporter.readColors("""
                    GIMP Palette
                    255 154 60 Orange
                    this line is not a colour
                    0 0 0 Black
                    """);

            assertEquals(2, colors.size(), "one bad line must not cost the whole palette");
        }

        @Test
        @DisplayName("refuses a file with no colours in it at all, and says what the formats are")
        void anEmptyFileIsAnError() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> PaletteImporter.fromText("Nothing", "just some prose\nand more of it\n"));

            assertTrue(thrown.getMessage().contains("RRGGBB"),
                    "the message has to say what a readable file looks like");
        }

        @Test
        @DisplayName("recognises both extensions and nothing else")
        void extensionsAreRecognised() {
            assertTrue(PaletteImporter.canRead(Path.of("x", "Sunset.gpl")));
            assertTrue(PaletteImporter.canRead(Path.of("x", "SUNSET.HEX")));
            assertFalse(PaletteImporter.canRead(Path.of("x", "song.mp3")));
            assertFalse(PaletteImporter.canRead(null));
        }

        @Test
        @DisplayName("reads from disk and names the palette after the file")
        void readsFromDisk(@TempDir Path folder) throws IOException {
            Path file = folder.resolve("Sunset Wilds.gpl");
            Files.writeString(file, GPL);

            Palette palette = PaletteImporter.read(file);

            assertEquals("Sunset Wilds", palette.name());
        }
    }

    @Nested
    @DisplayName("assigning to roles")
    class Assignment {

        @Test
        @DisplayName("takes the first sixteen entries in enum order and drops the rest")
        void sixteenInOrder() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                text.append(String.format("%02x0000%n", i * 12));
            }

            Palette palette = PaletteImporter.toPalette("Ramp",
                    PaletteImporter.readColors(text.toString()));

            // Declaration order is the format: the first entry lands on the first role, and the
            // seventeenth has nowhere to go. Reordering PaletteRole would silently rewrite every
            // palette anybody ever imported, which is why the enum says so in its own Javadoc.
            assertEquals(GbaColor.web("#000000"), palette.color(PaletteRole.BACKGROUND));
            assertEquals(GbaColor.web("#b40000"), palette.color(PaletteRole.SHADOW));

            String seventeenth = GbaColor.toHex(GbaColor.web("#c00000"));
            for (PaletteRole role : PaletteRole.values()) {
                assertFalse(GbaColor.toHex(palette.color(role)).equals(seventeenth),
                        "the seventeenth entry reached " + role + ", but there are only sixteen "
                                + "roles for it to land on");
            }
        }

        /**
         * Plenty of good palettes hold eight colours, or five. Refusing those would mean telling
         * somebody their palette is wrong when it is merely short, over a format that has no notion
         * of sixteen anything.
         */
        @Test
        @DisplayName("extends a short palette rather than refusing it")
        void shortPalettesAreExtended() {
            Palette palette = PaletteImporter.fromText("Four", HEX);

            for (PaletteRole role : PaletteRole.values()) {
                assertTrue(palette.color(role) != null, role + " has no colour");
            }
        }

        /**
         * Repeating a four-colour palette four times would give four roles the same colour, which
         * makes them indistinguishable <em>by construction</em> - and two of the sixteen are coins
         * and obstacles.
         */
        @Test
        @DisplayName("keeps every role a distinct colour when it extends one")
        void extensionDoesNotDuplicate() {
            Palette palette = PaletteImporter.fromText("Four", HEX);

            Set<String> seen = new HashSet<>();
            for (PaletteRole role : PaletteRole.values()) {
                if (role == PaletteRole.METER_HIGH) {
                    continue;
                }
                assertTrue(seen.add(GbaColor.toHex(palette.color(role))),
                        role + " repeats a colour already used by another role");
            }
        }

        @Test
        @DisplayName("snaps every imported colour to the hardware grid")
        void everythingIsSnapped() {
            Palette palette = PaletteImporter.fromText("Ramp", """
                    GIMP Palette
                    3 250 130
                    7 11 13
                    """);

            for (PaletteRole role : PaletteRole.values()) {
                Color color = palette.color(role);
                assertEquals(color, GbaColor.snap(color),
                        role + " is a colour the hardware could not display");
            }
        }

        /**
         * An arbitrary palette off the internet has no idea that entries twelve and thirteen become
         * coins and obstacles, and a good few of them put two greens there. Importing without
         * checking would turn "design a mood in ten seconds" into "make the game unreadable in ten
         * seconds".
         */
        @Test
        @DisplayName("repairs a palette whose entries break a protected pair")
        void importedPalettesAreValidated() {
            // Two near-identical greens land on POSITIVE and NEGATIVE by position alone.
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                text.append(i == 12 ? "0 176 0\n" : i == 13 ? "0 180 0\n"
                        : String.format("%d %d %d%n", i * 16, i * 9, 255 - i * 15));
            }

            Palette palette = PaletteImporter.fromText("Awkward", text.toString());

            assertTrue(MoodValidator.isValid(palette),
                    "an imported palette was left broken: " + MoodValidator.validate(palette));
        }

        @Test
        @DisplayName("every palette this can read comes out valid, whatever was in the file")
        void importsAreAlwaysValid() {
            for (String text : List.of(GPL, HEX, "000000\n010101\n", "255 255 255\n")) {
                Palette palette = PaletteImporter.fromText("Test", text);
                assertTrue(MoodValidator.isValid(palette),
                        "importing \"" + text.lines().findFirst().orElse("") + "...\" produced "
                                + MoodValidator.validate(palette));
            }
        }
    }
}
