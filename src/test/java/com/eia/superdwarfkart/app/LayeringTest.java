package com.eia.superdwarfkart.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ground rule 3, enforced instead of merely written down.
 *
 * <p>The separation of logic from presentation has been held by discipline since M0, and discipline
 * is exactly what erodes at the end of a long milestone: one {@code javafx.scene.paint.Color} in a
 * model class compiles, runs, and quietly makes the domain layer untestable without a toolkit.
 * Nothing fails until somebody tries to run a unit test headlessly, by which point the import has
 * company.
 *
 * <p>This reads the sources rather than the compiled classes, because an unused import is still a
 * dependency the next person will follow.
 */
class LayeringTest {

    /** The packages that must never see the toolkit. */
    private static final List<String> LOGIC_PACKAGES =
            List.of("ds", "model", "playback", "audio", "analysis", "spotify");

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/eia/superdwarfkart");

    @Test
    @DisplayName("no logic package imports javafx, including javafx.util.Duration")
    void logicPackagesAreFreeOfTheToolkit() throws IOException {
        List<String> offences = new ArrayList<>();

        for (String pkg : LOGIC_PACKAGES) {
            Path dir = SOURCE_ROOT.resolve(pkg);
            assertTrue(Files.isDirectory(dir), "expected package directory " + dir);
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    int lineNumber = 0;
                    for (String line : Files.readAllLines(file)) {
                        lineNumber++;
                        String trimmed = line.strip();
                        if (trimmed.startsWith("import ") && trimmed.contains("javafx")) {
                            offences.add(file + ":" + lineNumber + "  " + trimmed);
                        }
                    }
                }
            }
        }

        assertTrue(offences.isEmpty(),
                "these files import the toolkit from a logic package, which ground rule 3 forbids "
                        + "and which makes them untestable without a display:\n  "
                        + String.join("\n  ", offences));
    }

    /**
     * The other half of the same rule, and the one that actually bit this project.
     *
     * <p>{@code java.time.Duration.toSeconds()} returns a {@code long} and throws the fraction
     * away; its JavaFX namesake returns a {@code double}. The two are told apart by nothing but the
     * import, so a model class that pulled in the wrong {@code Duration} would compile, read
     * correctly, and quantise the game clock to one second - which cost a milestone of "the game
     * feels laggy" once already.
     */
    @Test
    @DisplayName("model and audio use java.time.Duration, never the JavaFX one")
    void theRightDurationIsImported() throws IOException {
        for (String pkg : List.of("model", "audio", "spotify")) {
            try (Stream<Path> files = Files.walk(SOURCE_ROOT.resolve(pkg))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("import javafx.util.Duration"),
                            file + " imports the JavaFX Duration, whose toSeconds() returns a "
                                    + "double where java.time's returns a truncated long");
                }
            }
        }
    }

    /**
     * The mood system's own half of ground rule 3.
     *
     * <p>{@code mood/} is the one non-{@code ui/} package allowed to see the toolkit at all, and the
     * allowance is narrow on purpose: colours, paints and raw images. It holds the <em>definition</em>
     * of a look - which has to be loadable, comparable, persistable and testable with no window open,
     * which is how {@code MoodRepositoryTest} runs at all - and {@code ui/MoodOverlayRenderer} is what
     * turns a definition into pixels.
     *
     * <p>{@code javafx.scene.image} is on the allowed list and the mood system's own notes name only
     * {@code paint}. That is a deliberate widening, and it is small: a {@code WritableImage} is a
     * buffer rather than a node, it takes part in no scene graph, and both places that use one -
     * rendering a tile's indices through a palette, and quantising an import onto sixteen colours -
     * are pixel arithmetic that would otherwise have to move to {@code ui/} and take the tile format
     * with it.
     *
     * <p>What stays out is what the rule is actually about: a {@code Node}, a control, a layout, a
     * canvas, an animation or a stage. Any one of those would make a mood something that can only
     * exist while a toolkit is running.
     */
    @Test
    @DisplayName("mood/ sees colours and images, and no node, control, layout, canvas or timer")
    void theMoodPackageStaysOutOfTheSceneGraph() throws IOException {
        List<String> allowed = List.of("javafx.scene.paint.", "javafx.scene.image.");
        List<String> offences = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE_ROOT.resolve("mood"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                int lineNumber = 0;
                for (String line : Files.readAllLines(file)) {
                    lineNumber++;
                    String trimmed = line.strip();
                    if (!trimmed.startsWith("import ") || !trimmed.contains("javafx")) {
                        continue;
                    }
                    String imported = trimmed.substring("import ".length()).replace(";", "");
                    if (allowed.stream().noneMatch(imported::startsWith)) {
                        offences.add(file.getFileName() + ":" + lineNumber + "  " + trimmed);
                    }
                }
            }
        }

        assertTrue(offences.isEmpty(),
                "mood/ holds the definition of a look, not a view of one. These reach into the "
                        + "scene graph, which would make a mood impossible to load, compare or "
                        + "test without a running toolkit:\n  " + String.join("\n  ", offences));
    }

    @Test
    @DisplayName("nothing outside spotify/ and the audio seam knows a subprocess exists")
    void theDaemonStaysBehindItsSeam() throws IOException {
        List<String> offences = new ArrayList<>();

        for (String pkg : List.of("ds", "model", "playback", "analysis", "game", "mood",
                "assets", "persistence")) {
            try (Stream<Path> files = Files.walk(SOURCE_ROOT.resolve(pkg))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        String trimmed = line.strip();
                        if (trimmed.startsWith("import ") && trimmed.contains(".spotify.")) {
                            offences.add(file + "  " + trimmed);
                        }
                    }
                }
            }
        }

        // The running order, the structures, the game and the analyser are written against
        // AudioSource and must stay unable to tell where the bytes came from. Only audio/ (the
        // seam), ui/ (the view) and app/ (the wiring) may name this package.
        assertTrue(offences.isEmpty(),
                "M10 is meant to be strictly additive; these reach past the seam:\n  "
                        + String.join("\n  ", offences));
    }
}
