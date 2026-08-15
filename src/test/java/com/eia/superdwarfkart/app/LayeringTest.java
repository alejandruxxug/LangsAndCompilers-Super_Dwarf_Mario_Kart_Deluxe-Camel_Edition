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
