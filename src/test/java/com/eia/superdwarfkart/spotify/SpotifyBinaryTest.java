package com.eia.superdwarfkart.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding the daemon.
 *
 * <p>Nothing here touches the network: fetching a 6 MB asset from GitHub in a unit test would make
 * the build depend on a third party being up. What is asserted instead is everything that decides
 * <em>whether</em> a fetch is even attempted, which is where the platform assumptions live.
 */
class SpotifyBinaryTest {

    @Test
    @DisplayName("a configured path wins, and is used exactly as given")
    void aConfiguredPathWins(@TempDir Path temp) throws IOException {
        Path fake = executableAt(temp.resolve("my-build"));

        SpotifyBinary.Resolution resolved = SpotifyBinary.resolve(fake.toString());

        assertEquals(fake, resolved.path());
        assertEquals(SpotifyBinary.Origin.CONFIGURED, resolved.origin());
        assertTrue(resolved.isFound());
    }

    @Test
    @DisplayName("a configured path that is not executable is ignored rather than obeyed")
    void anUnusableConfiguredPathIsIgnored(@TempDir Path temp) throws IOException {
        // A path left over from an uninstall must fall through to the rest of the search, not
        // disable Spotify with a file-not-found the user cannot act on.
        Path missing = temp.resolve("gone");
        Files.writeString(missing, "not executable");
        missing.toFile().setExecutable(false);

        SpotifyBinary.Resolution resolved = SpotifyBinary.resolve(missing.toString());

        assertFalse(resolved.origin() == SpotifyBinary.Origin.CONFIGURED);
    }

    @Test
    @DisplayName("a blank or null configured path simply searches the usual places")
    void aBlankConfiguredPathSearchesNormally() {
        // Whether it finds anything depends on the machine, so the assertion is that it answers
        // rather than what it answers.
        assertNotNull(SpotifyBinary.resolve(null));
        assertNotNull(SpotifyBinary.resolve("   "));
        assertNotNull(SpotifyBinary.resolve(null).origin());
    }

    /**
     * The published assets, which are Linux-only.
     *
     * <p>Asserted because the whole install story turns on it: there has never been a darwin
     * build, so on a Mac {@code fetch} would 404 and the honest route is Homebrew. If upstream ever
     * publishes one this test is where the assumption is written down.
     */
    @Test
    @DisplayName("an asset name is offered for Linux and for nothing else")
    void assetNamesAreLinuxOnly() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String asset = SpotifyBinary.assetName();

        if (os.contains("linux")) {
            assertNotNull(asset);
            assertTrue(asset.startsWith("go-librespot_linux_"), asset);
            assertTrue(asset.endsWith(".tar.gz"), asset);
        } else {
            assertNull(asset, "go-librespot publishes no build for " + os);
            assertFalse(SpotifyBinary.isDownloadable());
        }
    }

    @Test
    @DisplayName("macOS is offered Homebrew, because that is the only build there is")
    void macIsOfferedHomebrew() {
        if (SpotifyBinary.isMac()) {
            assertEquals("brew install go-librespot", SpotifyBinary.installCommand());
            assertTrue(SpotifyBinary.isSupportedPlatform(), "a Mac has a named pipe");
        } else {
            assertNull(SpotifyBinary.installCommand());
        }
    }

    @Test
    @DisplayName("not finding it is reported with what to do about it, not just a false")
    void notFoundSaysWhatToDo() {
        SpotifyBinary.Resolution resolved =
                SpotifyBinary.resolve("/definitely/not/here/go-librespot");

        if (!resolved.isFound()) {
            assertFalse(resolved.detail().isBlank(),
                    "a dead feature with no explanation is the failure mode this avoids");
        }
    }

    @Test
    @DisplayName("the install folder sits under the application's own home")
    void theInstallFolderIsPrivate() {
        assertTrue(SpotifyBinary.installedPath().toString().endsWith("go-librespot"));
        assertTrue(SpotifyBinary.installedPath().startsWith(
                com.eia.superdwarfkart.app.AppConfig.appHome()));
    }

    /**
     * The version comes out of the log, because there is no flag that would answer it.
     *
     * <p>Measured against the real binary: {@code --help} lists only {@code --conf} and
     * {@code --config_dir}, and anything else exits with
     * {@code error="unknown flag: --version"}. An earlier version of this class ran
     * {@code --version} and returned <em>that message</em> as the version - worse than not knowing,
     * because it would have been shown to the user as the answer.
     */
    @Test
    @DisplayName("the version is read from the daemon's own startup line")
    void theVersionComesFromTheLog() {
        assertEquals("0.8.0", SpotifyBinary.versionFrom(
                "time=\"2026-08-13T21:19:04-05:00\" level=info msg=\"running go-librespot 0.8.0\""));
        assertNull(SpotifyBinary.versionFrom("level=info msg=\"api server listening on 127.0.0.1:3678\""));
        assertNull(SpotifyBinary.versionFrom(null));
        assertNull(SpotifyBinary.versionFrom(
                "level=fatal msg=\"failed loading config\" error=\"unknown flag: --version\""));
    }

    /**
     * The name mismatch that would have made the build button useless.
     *
     * <p>Go names a main package's binary after its directory, and the package is
     * {@code cmd/daemon} - so {@code go install} produces {@code daemon}, not
     * {@code go-librespot}. A build that left it under that name would put a perfectly good daemon
     * somewhere {@link SpotifyBinary#resolve} never looks, and the button would appear to do
     * nothing at all. The build renames it, and {@code GOBIN} puts it in the folder resolution
     * already searches.
     */
    @Test
    @DisplayName("the source package and the name go install gives its output are both pinned")
    void theSourceBuildNamesArePinned() {
        assertEquals("daemon", SpotifyBinary.SOURCE_EXECUTABLE,
                "go names the binary after the package directory, which is cmd/daemon");
        assertNotEquals(SpotifyBinary.SOURCE_EXECUTABLE, SpotifyBinary.EXECUTABLE,
                "if these were ever the same, the rename after the build would be dead code");
        assertTrue(SpotifyBinary.SOURCE_PACKAGE.endsWith("@master"),
                "a released version is exactly what a source build is for getting away from: the "
                        + "web-api proxy search needs is master-only");
        assertEquals("go install " + SpotifyBinary.SOURCE_PACKAGE, SpotifyBinary.buildCommand());
    }

    /**
     * The prerequisite check, which exists because the build's own failure is unhelpful.
     *
     * <p>Reported from a real attempt: with no pkg-config installed, {@code go install} compiles for
     * a while and then prints
     * {@code github.com/devgianlu/go-librespot/mp3: exec: "pkg-config": executable file not found}
     * once per cgo package - three near-identical lines naming what Go could not run rather than
     * what to install. And installing only pkg-config leads straight into the next wall, because
     * {@code ogg}, {@code vorbis} and {@code flac} arrive as Homebrew dependencies of go-librespot
     * while {@code mpg123} does not.
     */
    @Test
    @DisplayName("every missing build prerequisite is named at once, with one command to fix them")
    void prerequisitesAreCheckedTogether() {
        SpotifyBinary.BuildPrerequisites prerequisites = SpotifyBinary.checkBuildPrerequisites();

        assertNotNull(prerequisites.missing());
        if (prerequisites.isSatisfied()) {
            assertNull(prerequisites.installCommand(), "nothing missing means nothing to install");
            assertTrue(prerequisites.describe().isEmpty());
            return;
        }

        // Whatever is missing, the user is told how to get it - all of it, in one command.
        assertNotNull(prerequisites.installCommand());
        for (String missing : prerequisites.missing()) {
            assertFalse(missing.isBlank());
        }
        if (SpotifyBinary.isMac()) {
            assertTrue(prerequisites.installCommand().startsWith("brew install "),
                    prerequisites.installCommand());
        }
        // And the build refuses up front rather than discovering it several packages in.
        String detail = SpotifyBinary.buildFromSource(null).detail();
        assertTrue(detail.startsWith("Missing: "), detail);
        assertTrue(detail.contains(prerequisites.installCommand()), detail);
    }

    /**
     * pkg-config module names, which are not the same as package names.
     *
     * <p>Reported from the machine this was built on: mpg123 was installed and working, and the
     * app went on insisting it was missing. The check was asking pkg-config for a module called
     * {@code mpg123}; the module is called {@code libmpg123} - {@code mpg123} is only the Homebrew
     * <em>formula</em>. The two names differ for exactly this one library, which is why swapping
     * them looked right everywhere else.
     *
     * <p>The authority is go-librespot's own cgo directive, {@code // #cgo pkg-config: libmpg123}
     * in {@code mp3/decoder.go}, because that is literally the question the build asks.
     */
    @Test
    @DisplayName("the libraries are checked by pkg-config module name, not by package name")
    void librariesUsePkgConfigModuleNames() {
        assertTrue(SpotifyBinary.REQUIRED_LIBRARIES.contains("libmpg123"),
                "mp3/decoder.go declares: #cgo pkg-config: libmpg123");
        assertFalse(SpotifyBinary.REQUIRED_LIBRARIES.contains("mpg123"),
                "mpg123 is the Homebrew formula; no such pkg-config module exists, so checking for "
                        + "it reports a library that is installed as missing");
        assertEquals("mpg123", SpotifyBinary.BREW_FORMULAS.get("libmpg123"),
                "the module is libmpg123 and the formula that provides it is mpg123");

        // Every module the check asks about must map to something installable, or a missing
        // library would produce a command that cannot fix it.
        for (String module : SpotifyBinary.REQUIRED_LIBRARIES) {
            assertNotNull(SpotifyBinary.BREW_FORMULAS.get(module),
                    "no Homebrew formula is recorded for the pkg-config module " + module);
        }
    }

    @Test
    @DisplayName("a build with no Go toolchain says so instead of failing obscurely")
    void aBuildWithoutGoIsReportedClearly() {
        if (SpotifyBinary.findGo() != null) {
            // Go is installed on this machine, so the no-toolchain path cannot be exercised here.
            return;
        }
        SpotifyBinary.Resolution built = SpotifyBinary.buildFromSource(null);

        assertFalse(built.isFound());
        // Go is listed among the missing prerequisites rather than handled as a special case.
        assertTrue(built.detail().contains("go"), built.detail());
    }

    private static Path executableAt(Path path) throws IOException {
        Files.writeString(path, "#!/bin/sh\necho stub\n");
        path.toFile().setExecutable(true);
        return path;
    }
}
