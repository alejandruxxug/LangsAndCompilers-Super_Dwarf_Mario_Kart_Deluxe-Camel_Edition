package com.eia.superdwarfkart.assets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers scanning, the manifest and the drop-in folder.
 *
 * <p>Nothing here decodes an image, which is exactly the point: scanning must cost a directory
 * listing, so that a folder full of large sheets cannot slow the application's startup down. The
 * files written below are not valid PNGs and never need to be.
 */
@DisplayName("Asset registry")
class AssetRegistryTest {

    /** Stand-in file contents. Scanning must never look at these bytes. */
    private static final byte[] NOT_REALLY_AN_IMAGE = {1, 2, 3, 4};

    private static Path writeFile(Path dir, String relativePath) throws IOException {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent() == null ? dir : file.getParent());
        Files.write(file, NOT_REALLY_AN_IMAGE);
        return file;
    }

    private static Optional<AssetEntry> find(List<AssetEntry> entries, String key) {
        return entries.stream().filter(entry -> entry.key().equals(key)).findFirst();
    }

    @Nested
    @DisplayName("scanning a folder")
    class Scanning {

        @Test
        @DisplayName("finds artwork in nested folders")
        void walksRecursively(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            writeFile(dir, "textures/Sprites/Star.png");
            writeFile(dir, "textures/deep/nested/bump.png");

            List<AssetEntry> entries = AssetRegistry.scanDirectory(dir, true);

            assertEquals(3, entries.size());
            assertTrue(find(entries, "star").isPresent());
            assertTrue(find(entries, "bump").isPresent());
        }

        @Test
        @DisplayName("ignores everything that is not an image")
        void skipsNonImages(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            writeFile(dir, "song.mp3");
            writeFile(dir, "notes.txt");
            writeFile(dir, "assets.json");

            List<AssetEntry> entries = AssetRegistry.scanDirectory(dir, true);

            assertEquals(1, entries.size());
            assertEquals("coin", entries.getFirst().key());
        }

        @Test
        @DisplayName("classifies what it finds and records where it came from")
        void classifiesAndTagsEntries(@TempDir Path dir) throws IOException {
            writeFile(dir, "textures/Star.png");

            AssetEntry star = find(AssetRegistry.scanDirectory(dir, true), "star").orElseThrow();

            assertEquals(AssetKind.STAR, star.kind());
            assertEquals("textures/Star.png", star.relativePath());
            assertTrue(star.userSupplied());
            assertEquals(AssetEntry.INFER_FRAMES, star.declaredFrames(),
                    "frame count is worked out when the image is decoded, not while scanning");
        }

        @Test
        @DisplayName("is ordered, so the first sheet of a kind is always the same one")
        void isDeterministic(@TempDir Path dir) throws IOException {
            writeFile(dir, "b-coin.png");
            writeFile(dir, "a-coin.png");
            writeFile(dir, "c-coin.png");

            List<String> keys = AssetRegistry.scanDirectory(dir, true).stream()
                    .map(AssetEntry::key).toList();

            assertEquals(List.of("a-coin", "b-coin", "c-coin"), keys);
        }

        @Test
        @DisplayName("a folder that does not exist is not an error")
        void missingFolderIsFine(@TempDir Path dir) {
            assertEquals(List.of(), AssetRegistry.scanDirectory(dir.resolve("nope"), true));
        }
    }

    @Nested
    @DisplayName("the drop-in folder")
    class UserFolder {

        @Test
        @DisplayName("replaces bundled artwork of the same name without a rebuild")
        void userArtWins(@TempDir Path dir) throws IOException {
            // Star.png is bundled in the project. Dropping one in the user's folder must take
            // over, which is how new art reaches the application between builds.
            writeFile(dir, "star.png");

            AssetEntry star = AssetRegistry.scan(dir).entry("star").orElseThrow();

            assertTrue(star.userSupplied(), "the user's copy should win over the bundled one");
        }

        @Test
        @DisplayName("the bundled artwork is still found when the folder is empty")
        void bundledArtIsFound(@TempDir Path dir) {
            AssetRegistry registry = AssetRegistry.scan(dir.resolve("does-not-exist"));

            assertFalse(registry.isEmpty(), "the project's own sprites should have been found");
            assertTrue(registry.entry("star").isPresent());
            assertTrue(registry.entry("mario").isPresent());
            assertEquals(AssetKind.DISK, registry.entry("disk-sheet").orElseThrow().kind());
        }
    }

    @Nested
    @DisplayName("the manifest")
    class Manifest {

        @Test
        @DisplayName("overrides what the filename suggested")
        void overridesDetection(@TempDir Path dir) throws IOException {
            writeFile(dir, "untitled-4.png");
            Files.writeString(dir.resolve("assets.json"), """
                    {
                      "version": 1,
                      "assets": [
                        { "key": "untitled-4", "kind": "STAR", "file": "untitled-4.png", "frames": 9 }
                      ]
                    }
                    """);

            AssetEntry entry = AssetRegistry.scan(dir).entry("untitled-4").orElseThrow();

            assertEquals(AssetKind.STAR, entry.kind(), "the manifest overrides the guess");
            assertEquals(9, entry.declaredFrames());
        }

        @Test
        @DisplayName("a template is written on first run, filled in with what was detected")
        void writesTemplateOnFirstRun(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            Path manifest = dir.resolve("assets.json");
            assertFalse(Files.exists(manifest));

            AssetRegistry registry = AssetRegistry.scan(dir);

            assertTrue(Files.exists(manifest), "a first run should leave a manifest to edit");
            assertEquals(manifest, registry.manifestFile());
            String written = Files.readString(manifest);
            assertTrue(written.contains("\"coin\""), written);
            assertTrue(written.contains("COIN"), written);
        }

        @Test
        @DisplayName("an existing manifest is never overwritten")
        void doesNotClobberAnExistingManifest(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            String original = """
                    { "version": 1, "assets": [ { "key": "coin", "kind": "COIN", "frames": 4 } ] }
                    """;
            Files.writeString(dir.resolve("assets.json"), original);

            AssetRegistry.scan(dir);

            assertEquals(original, Files.readString(dir.resolve("assets.json")));
        }

        @Test
        @DisplayName("unreadable JSON is ignored rather than fatal")
        void brokenManifestIsIgnored(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            Files.writeString(dir.resolve("assets.json"), "{ this is not json");

            AssetRegistry registry = AssetRegistry.scan(dir);

            assertEquals(AssetKind.COIN, registry.entry("coin").orElseThrow().kind());
        }

        @Test
        @DisplayName("naming a file that is not there is a warning, not a failure")
        void unknownKeyIsIgnored(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            Files.writeString(dir.resolve("assets.json"), """
                    {
                      "version": 1,
                      "assets": [
                        { "key": "ghost", "kind": "STAR", "file": "ghost.png", "frames": 2 },
                        { "key": "coin", "kind": "COIN", "file": "coin.png", "frames": 1 }
                      ]
                    }
                    """);

            AssetRegistry registry = AssetRegistry.scan(dir);

            assertTrue(registry.entry("ghost").isEmpty());
            assertEquals(1, registry.entry("coin").orElseThrow().declaredFrames());
        }

        @Test
        @DisplayName("a misspelled kind keeps the detected one")
        void unknownKindFallsBack(@TempDir Path dir) throws IOException {
            writeFile(dir, "coin.png");
            Files.writeString(dir.resolve("assets.json"), """
                    { "version": 1, "assets": [ { "key": "coin", "kind": "MONEY", "frames": 0 } ] }
                    """);

            assertEquals(AssetKind.COIN, AssetRegistry.scan(dir).entry("coin").orElseThrow().kind());
        }
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        @DisplayName("finds artwork by kind, in a stable order")
        void byKind(@TempDir Path dir) throws IOException {
            writeFile(dir, "a-fondo.png");
            writeFile(dir, "z-background.png");

            AssetRegistry registry = AssetRegistry.scan(dir);

            assertEquals(2, registry.entries(AssetKind.BACKGROUND).size());
            assertEquals("a-fondo", registry.firstEntry(AssetKind.BACKGROUND).orElseThrow().key());
        }

        @Test
        @DisplayName("a key that was never registered is simply absent")
        void unknownKey(@TempDir Path dir) {
            AssetRegistry registry = AssetRegistry.scan(dir);

            assertTrue(registry.entry("no-such-sprite").isEmpty());
            assertTrue(registry.entry(null).isEmpty());
        }

        @Test
        @DisplayName("lookup is case-insensitive")
        void caseInsensitiveKeys(@TempDir Path dir) throws IOException {
            writeFile(dir, "Coin.png");

            AssetRegistry registry = AssetRegistry.scan(dir);

            assertNotNull(registry.entry("COIN").orElse(null));
            assertNotNull(registry.entry("coin").orElse(null));
        }

        @Test
        @DisplayName("the explosion the project has not drawn yet is reported as missing")
        void missingKindIsReportedNotThrown(@TempDir Path dir) {
            // Ground rule: the application launches and is usable with no artwork at all. The
            // explosion sheet does not exist yet, and asking for it must not be a failure.
            AssetRegistry registry = AssetRegistry.scan(dir);

            assertTrue(registry.firstEntry(AssetKind.EXPLOSION).isEmpty());
        }
    }
}
