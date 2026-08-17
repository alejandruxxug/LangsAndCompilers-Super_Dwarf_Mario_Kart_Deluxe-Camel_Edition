package com.eia.superdwarfkart.mood;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a user's moods live, and what has to survive the round trip.
 *
 * <p>A mood is a folder rather than a file because it carries more than sixteen colours: layer
 * definitions, imported artwork and tiles drawn in the editor, all of which have to travel together
 * the moment somebody zips one up and sends it to a teammate. So the tests below are mostly about
 * things arriving intact, and about the two ways a shared mood could damage the machine it lands on
 * - a folder name taken from a text field, and a mood id that collides with one already there.
 */
@DisplayName("Mood repository")
class MoodRepositoryTest {

    @TempDir
    Path root;

    private MoodRepository repository;

    @BeforeEach
    void openRepository() {
        repository = new MoodRepository(root);
    }

    private static Mood userMood(String id, String name) {
        return new Mood(id, name, Palette.defaultPalette());
    }

    @Nested
    @DisplayName("the round trip")
    class RoundTrip {

        @Test
        @DisplayName("brings a palette back colour for colour")
        void paletteSurvives() throws IOException {
            Mood mood = new Mood("mine", "Mine", Moods.SNOW_LAND.palette());

            repository.save(mood);
            Mood back = new MoodRepository(root).byId("mine").orElseThrow();

            for (PaletteRole role : PaletteRole.values()) {
                assertEquals(GbaColor.toHex(mood.color(role)), GbaColor.toHex(back.color(role)),
                        role + " changed on the way to disk and back");
            }
        }

        @Test
        @DisplayName("brings every kind of layer back with its own properties")
        void layersSurvive() throws IOException {
            List<MoodLayer> layers = List.of(
                    new GradientLayer(LayerStyle.behind().withOpacity(0.4).withScroll(-11, 3),
                            GradientLayer.Kind.RADIAL, 33, 0.25, 0.75, 1.4,
                            List.of(GradientStop.of(0, PaletteRole.PRIMARY),
                                    GradientStop.of(0.5, GbaColor.web("#ff00ff")),
                                    GradientStop.of(1, PaletteRole.SHADOW)),
                            6, false),
                    new ImageLayer(LayerStyle.above().withBlend(LayerBlend.SCREEN),
                            "clouds.png", ImageLayer.Fit.COVER, 3, false),
                    new ProceduralLayer(LayerStyle.behind().withOpacity(0.7),
                            ProceduralLayer.Pattern.STARFIELD, 6, 4242L));
            Mood mood = new Mood("mine", "Mine", Palette.defaultPalette(), layers);

            repository.save(mood);
            Mood back = new MoodRepository(root).byId("mine").orElseThrow();

            assertEquals(layers, back.layers(),
                    "a layer changed on the way to disk and back");
        }

        @Test
        @DisplayName("brings a hand-drawn tile back pixel for pixel")
        void tilesSurvive() throws IOException {
            PixelTile clouds = Moods.SKY_GARDEN.tile("clouds");
            Mood mood = userMood("mine", "Mine").withTile("clouds", clouds);

            repository.save(mood);
            Mood back = new MoodRepository(root).byId("mine").orElseThrow();

            assertEquals(clouds.rows(), back.tile("clouds").rows());
        }

        @Test
        @DisplayName("brings the reactive switch back")
        void reactiveSurvives() throws IOException {
            repository.save(userMood("mine", "Mine").withReactive(true));

            assertTrue(new MoodRepository(root).byId("mine").orElseThrow().reactive());
        }

        @Test
        @DisplayName("writes a file a human can read and edit")
        void theFileIsReadable() throws IOException {
            repository.save(userMood("mine", "Mine")
                    .withLayerAdded(ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.2)));

            String json = Files.readString(root.resolve("mine").resolve(MoodRepository.MOOD_FILE));

            assertTrue(json.contains("\"palette\""), json);
            assertTrue(json.contains("#"), "the palette is written as hex, not as numbers");
            assertTrue(json.contains("\"type\" : \"procedural\""), json);
        }
    }

    @Nested
    @DisplayName("the set it offers")
    class TheSet {

        @Test
        @DisplayName("lists the presets first, then the user's own")
        void presetsComeFirst() throws IOException {
            repository.save(userMood("aaa", "Aaa"));

            List<Mood> all = repository.all();

            assertEquals(Moods.builtIns().size() + 1, all.size());
            assertEquals(Moods.DARK.id(), all.get(0).id(),
                    "a list that opened with the user's own would bury the presets");
            assertEquals("aaa", all.get(all.size() - 1).id());
        }

        @Test
        @DisplayName("resolves a preset and a user mood through the same lookup")
        void oneLookupForBoth() throws IOException {
            repository.save(userMood("mine", "Mine"));

            assertEquals("Mine", repository.byId("mine").orElseThrow().displayName());
            assertEquals(Moods.SUNSET_WILDS, repository.byId("sunset_wilds").orElseThrow());
            assertTrue(repository.byId("nothing").isEmpty());
            assertTrue(repository.byId(null).isEmpty());
        }

        @Test
        @DisplayName("opens happily on a folder that does not exist yet")
        void firstRunIsNotAnError() {
            MoodRepository fresh = new MoodRepository(root.resolve("never-created"));

            assertTrue(fresh.userMoods().isEmpty());
            assertEquals(Moods.builtIns().size(), fresh.all().size());
        }
    }

    @Nested
    @DisplayName("protecting the presets")
    class Presets {

        /**
         * There has to be a known-good mood to fall back to when an experiment goes wrong twenty
         * minutes before a defence. The customizer's answer is to duplicate first; this is the
         * backstop that makes it impossible to get round.
         */
        @Test
        @DisplayName("refuses to overwrite a mood that ships with the application")
        void builtInsCannotBeSaved() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> repository.save(Moods.SUNSET_WILDS));

            assertTrue(thrown.getMessage().contains("Duplicate"), thrown.getMessage());
        }

        @Test
        @DisplayName("refuses to delete one")
        void builtInsCannotBeDeleted() {
            assertThrows(IllegalArgumentException.class, () -> repository.delete("dark"));
        }

        @Test
        @DisplayName("hands out an id nothing is already using")
        void idsAreMadeUnique() throws IOException {
            repository.save(userMood("mine", "Mine"));

            assertEquals("mine-2", repository.uniqueId("mine"));
            assertEquals("dark-2", repository.uniqueId("dark"),
                    "a preset's id is taken too, or a copy would shadow the original");
            assertEquals("free", repository.uniqueId("free"));
        }
    }

    @Nested
    @DisplayName("names off a text field")
    class Slugs {

        /**
         * A mood folder is created from a name the user typed, so this is the only thing between a
         * text field and {@code Path.resolve}. Anything that is not a letter, a digit or a dash
         * becomes a dash, which rules out separators, traversal and every reserved character at
         * once.
         */
        @Test
        @DisplayName("cannot produce a separator, a traversal or a reserved character")
        void slugsAreSafe() {
            assertEquals("my-mood", MoodRepository.slug("My Mood"));
            assertEquals("etc-passwd", MoodRepository.slug("../../etc/passwd"));
            assertEquals("con", MoodRepository.slug("  CON  "));
            assertEquals("a-b", MoodRepository.slug("a:b"));
        }

        @Test
        @DisplayName("never produces a blank name")
        void slugsAreNeverBlank() {
            assertEquals("mood", MoodRepository.slug(""));
            assertEquals("mood", MoodRepository.slug("   "));
            assertEquals("mood", MoodRepository.slug("///"));
            assertEquals("mood", MoodRepository.slug(null));
        }

        @Test
        @DisplayName("writes a mood into a folder inside the moods directory and nowhere else")
        void foldersStayInside() throws IOException {
            repository.save(userMood("../escape", "Escape"));

            assertTrue(Files.isDirectory(root.resolve("escape")),
                    "the mood should be in a folder called \"escape\" under the moods directory");
            assertFalse(Files.exists(root.getParent().resolve("escape")),
                    "a mood escaped its own directory");
        }
    }

    @Nested
    @DisplayName("when something is wrong with a file")
    class Damage {

        /**
         * Ground rule 5, applied to the mood system: a mood that fails to load costs the user their
         * colours and nothing else. One unreadable folder must not cost them the other nineteen and
         * must certainly not stop the application opening.
         */
        @Test
        @DisplayName("skips an unreadable mood and keeps the rest")
        void oneBadMoodDoesNotCostTheOthers() throws IOException {
            repository.save(userMood("good", "Good"));
            Files.createDirectories(root.resolve("bad"));
            Files.writeString(root.resolve("bad").resolve(MoodRepository.MOOD_FILE),
                    "{ this is not json");

            MoodRepository reopened = new MoodRepository(root);

            assertEquals(1, reopened.userMoods().size());
            assertEquals("Good", reopened.userMoods().get(0).displayName());
        }

        @Test
        @DisplayName("ignores a folder with no mood file in it")
        void straysAreIgnored() throws IOException {
            Files.createDirectories(root.resolve("just-a-folder"));

            assertTrue(new MoodRepository(root).userMoods().isEmpty());
        }

        @Test
        @DisplayName("drops a layer of a kind it does not understand and keeps the mood")
        void unknownLayersAreDropped() throws IOException {
            repository.save(userMood("mine", "Mine")
                    .withLayerAdded(ProceduralLayer.of(ProceduralLayer.Pattern.VIGNETTE, 0.2)));
            Path file = root.resolve("mine").resolve(MoodRepository.MOOD_FILE);
            Files.writeString(file, Files.readString(file)
                    .replace("\"procedural\"", "\"holographic\""));

            Mood back = new MoodRepository(root).byId("mine").orElseThrow();

            assertEquals("Mine", back.displayName());
            assertTrue(back.layers().isEmpty(), "an unreadable layer must not take the mood with it");
        }

        @Test
        @DisplayName("repairs a hand-edited palette that breaks a protected pair")
        void handEditedPalettesAreValidated() throws IOException {
            repository.save(userMood("mine", "Mine"));
            Path file = root.resolve("mine").resolve(MoodRepository.MOOD_FILE);
            String hex = GbaColor.toHex(Palette.defaultPalette().color(PaletteRole.POSITIVE));
            // Make NEGATIVE the same colour as POSITIVE, which is the edit that makes coins and
            // obstacles indistinguishable and throws nothing anywhere.
            String json = Files.readString(file);
            int positive = json.indexOf(hex);
            int negative = json.indexOf('"', json.indexOf('"', positive + hex.length()) + 1);
            Files.writeString(file, json.substring(0, negative + 1) + hex
                    + json.substring(json.indexOf('"', negative + 2)));

            Mood back = new MoodRepository(root).byId("mine").orElseThrow();

            assertTrue(MoodValidator.isValid(back.palette()),
                    "a hand-edited mood installed a palette the validator rejects: "
                            + MoodValidator.validate(back.palette()));
        }
    }

    @Nested
    @DisplayName("sharing")
    class Sharing {

        @Test
        @DisplayName("exports the definition and every image beside it")
        void exportCarriesTheArtwork(@TempDir Path elsewhere) throws IOException {
            Mood mood = userMood("mine", "Mine");
            repository.save(mood);
            Files.writeString(repository.folderOf("mine").resolve("bg.png"), "not really a png");

            Path written = repository.export(repository.byId("mine").orElseThrow(), elsewhere);

            assertTrue(Files.isRegularFile(written.resolve("mine" + MoodRepository.EXPORT_SUFFIX)),
                    "the definition is named after the mood so a downloads folder stays legible");
            assertTrue(Files.isRegularFile(written.resolve("bg.png")),
                    "a mood that arrived without its artwork would draw magenta rectangles");
        }

        @Test
        @DisplayName("imports an exported folder, artwork and all")
        void importReadsAnExport(@TempDir Path elsewhere) throws IOException {
            Mood mood = new Mood("theirs", "Theirs", Moods.BOO_LAKE.palette(),
                    List.of(ImageLayer.tiled("bg.png")));
            repository.save(mood);
            Files.writeString(repository.folderOf("theirs").resolve("bg.png"), "not really a png");
            Path exported = repository.export(mood, elsewhere);
            repository.delete("theirs");

            Mood imported = repository.importFrom(exported);

            assertEquals("Theirs", imported.displayName());
            assertEquals(1, imported.layers().size());
            assertTrue(Files.isRegularFile(repository.folderOf(imported.id()).resolve("bg.png")));
        }

        /**
         * A mood somebody shared must never overwrite one the user built. The id is made unique and
         * the name says where it came from, so the two are told apart in the switcher rather than
         * one of them quietly disappearing.
         */
        @Test
        @DisplayName("never overwrites a mood the user already has")
        void importsDoNotCollide(@TempDir Path elsewhere) throws IOException {
            Mood mine = userMood("shared", "Shared");
            repository.save(mine);
            Path exported = repository.export(mine, elsewhere);

            Mood imported = repository.importFrom(exported);

            assertNotEquals("shared", imported.id());
            assertTrue(imported.displayName().contains("imported"), imported.displayName());
            assertEquals(2, repository.userMoods().size());
        }

        @Test
        @DisplayName("says so rather than throwing something opaque when there is no mood there")
        void importingNothingIsExplained(@TempDir Path empty) {
            IOException thrown = assertThrows(IOException.class,
                    () -> repository.importFrom(empty));

            assertTrue(thrown.getMessage().contains(MoodRepository.EXPORT_SUFFIX),
                    thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("imported images")
    class Images {

        @Test
        @DisplayName("are copied into the mood's own folder, not referenced where they came from")
        void imagesAreCopiedIn(@TempDir Path elsewhere) throws IOException {
            Path source = elsewhere.resolve("bg.png");
            Files.writeString(source, "not really a png");
            repository.save(userMood("mine", "Mine"));

            String name = repository.importImage("mine", source);

            assertEquals("bg.png", name);
            assertTrue(Files.isRegularFile(repository.folderOf("mine").resolve("bg.png")));
            // Deleting the original must not affect the mood - which is the whole reason for the
            // copy, and the failure that only shows on somebody else's machine.
            Files.delete(source);
            assertTrue(Files.isRegularFile(repository.folderOf("mine").resolve("bg.png")));
        }

        @Test
        @DisplayName("do not silently replace one another when two files share a name")
        void namesAreMadeUnique(@TempDir Path elsewhere) throws IOException {
            repository.save(userMood("mine", "Mine"));
            Path first = elsewhere.resolve("bg.png");
            Files.writeString(first, "one");
            Path second = elsewhere.resolve("other").resolve("bg.png");
            Files.createDirectories(second.getParent());
            Files.writeString(second, "two");

            assertEquals("bg.png", repository.importImage("mine", first));
            assertEquals("bg-1.png", repository.importImage("mine", second),
                    "the second import must not overwrite a file a layer is already using");
        }
    }
}
