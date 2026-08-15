package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.app.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds the go-librespot daemon, and fetches it when this platform has a published build.
 *
 * <p><strong>The user installs nothing by hand where that can be avoided.</strong> Resolution runs
 * in a fixed order, most specific first, and stops at the first hit:
 *
 * <ol>
 *   <li>the path stored in the settings, if the user pointed the application at their own build;</li>
 *   <li>{@code ~/.superdwarfkart/bin/go-librespot}, which is where {@link #fetch} puts it;</li>
 *   <li>whatever is on {@code PATH}, which is what catches {@code brew install go-librespot};</li>
 *   <li>nothing - and the Spotify features disable themselves rather than failing.</li>
 * </ol>
 *
 * <h2>There is no macOS download, and that is a fact about the project rather than a gap here</h2>
 *
 * <p>go-librespot publishes <strong>Linux binaries only</strong>: every release from v0.5.2 to the
 * current one carries exactly four assets - {@code linux_x86_64}, {@code linux_arm64},
 * {@code linux_armv6} and {@code linux_armv6_rpi} - and no darwin build has ever been published,
 * even though the source has had a macOS AudioToolbox backend for some time. So {@link #fetch}
 * genuinely cannot work on a Mac, and pretending otherwise would produce a download that 404s at
 * the one moment the user is waiting on it.
 *
 * <p>What macOS has instead is a Homebrew formula, which is bottled. {@link #installCommand()}
 * returns it, and the Spotify view offers to run that command rather than running it unasked:
 * installing four packages on somebody's machine is not a thing to do quietly at startup, and it
 * is the one step here that changes the system rather than this application's own folder.
 *
 * <p>Everything in this class is safe to call with no network. A failed fetch is logged and
 * reported; nothing throws at the caller and nothing blocks startup (ground rule 5).
 */
public final class SpotifyBinary {

    private static final Logger LOG = Logger.getLogger(SpotifyBinary.class.getName());

    /** The executable's name, on every platform that has one. */
    public static final String EXECUTABLE = "go-librespot";

    /**
     * Where a published asset is downloaded from.
     *
     * <p>{@code /releases/latest/download/} rather than a pinned tag or a call to the releases API:
     * GitHub redirects it to the newest release's asset of that name, so this needs no API request
     * - which would be rate limited and unauthenticated - and cannot go stale against a version
     * number written down here.
     */
    private static final String DOWNLOAD_BASE =
            "https://github.com/devgianlu/go-librespot/releases/latest/download/";

    /** The Homebrew formula, which is how a Mac gets the daemon. */
    private static final String BREW_FORMULA = "go-librespot";

    /** How long the download is given before it is abandoned. */
    private static final Duration FETCH_TIMEOUT = Duration.ofMinutes(3);

    /**
     * How long a source build is given.
     *
     * <p>Generous because it deserves to be: a cold {@code go install} fetches the whole module
     * graph and compiles it, and killing that halfway leaves the user worse off than waiting.
     */
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(15);

    /** Where the daemon was found. */
    public enum Origin {

        /** A path the user configured explicitly. */
        CONFIGURED("configured path"),

        /** Downloaded by this application into its own folder. */
        DOWNLOADED("downloaded"),

        /** Found on {@code PATH} - typically a Homebrew or package-manager install. */
        ON_PATH("found on PATH"),

        /** Not present anywhere. */
        NOT_FOUND("not installed");

        private final String label;

        Origin(String label) {
            this.label = label;
        }

        /** @return a short phrase naming where the daemon came from */
        public String label() {
            return label;
        }
    }

    /**
     * The outcome of a lookup.
     *
     * @param path   where the executable is, or {@code null} when it was not found
     * @param origin which step of the search answered
     * @param detail a sentence for the interface, naming what happened
     */
    public record Resolution(Path path, Origin origin, String detail) {

        /** @return whether an executable was found */
        public boolean isFound() {
            return path != null;
        }
    }

    private SpotifyBinary() {
        throw new AssertionError("SpotifyBinary is a utility holder and must not be instantiated");
    }

    // ------------------------------------------------------------------
    // Where things live
    // ------------------------------------------------------------------

    /** @return the folder this application keeps its own copy of the daemon in */
    public static Path installDir() {
        return AppConfig.spotifyDir().resolve("bin");
    }

    /** @return where {@link #fetch} puts the executable */
    public static Path installedPath() {
        return installDir().resolve(EXECUTABLE);
    }

    // ------------------------------------------------------------------
    // Platform
    // ------------------------------------------------------------------

    /**
     * Names the release asset for the running platform.
     *
     * <p>Checked against the published assets rather than composed from templates: the project ships
     * {@code linux_x86_64}, {@code linux_arm64} and {@code linux_armv6}, and nothing else.
     *
     * @return the asset filename, or {@code null} when this platform has no published build
     */
    public static String assetName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("linux")) {
            // macOS and Windows both land here. macOS has a Homebrew bottle; Windows has neither a
            // build nor a POSIX FIFO, so the Spotify path is not available there at all.
            return null;
        }
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "go-librespot_linux_arm64.tar.gz";
        }
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "go-librespot_linux_x86_64.tar.gz";
        }
        if (arch.contains("arm")) {
            return "go-librespot_linux_armv6.tar.gz";
        }
        return null;
    }

    /** @return whether {@link #fetch} has anything it could download on this platform */
    public static boolean isDownloadable() {
        return assetName() != null;
    }

    /**
     * Whether the Spotify path can work on this platform at all.
     *
     * <p>The daemon writes audio to a named pipe and Java cannot create one, so {@code mkfifo} is
     * invoked as a process - which makes the whole feature POSIX-only. Local files stay portable;
     * this is the one part of the application that is not.
     *
     * @return whether this is a POSIX platform
     */
    public static boolean isSupportedPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("linux") || os.contains("mac");
    }

    /** @return whether the running platform is macOS, where Homebrew is the install route */
    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * The command that installs the daemon on a platform with no published binary.
     *
     * @return the shell command to run, or {@code null} where a download is available instead
     */
    public static String installCommand() {
        return isMac() ? "brew install " + BREW_FORMULA : null;
    }

    // ------------------------------------------------------------------
    // Building from source, which is the only way to get the search endpoints
    // ------------------------------------------------------------------

    /**
     * The Go package that builds the daemon.
     *
     * <p>Pinned to {@code master} deliberately: the Web API proxy that search needs is on that
     * branch and in no tagged release, so a released version is exactly what this is for getting
     * away from.
     */
    public static final String SOURCE_PACKAGE = "github.com/devgianlu/go-librespot/cmd/daemon@master";

    /**
     * What {@code go install} names the executable it produces.
     *
     * <p><strong>Not {@code go-librespot}.</strong> Go names a main package's binary after its
     * directory, and the package is {@code cmd/daemon} - so a source build produces {@code daemon}
     * and {@link #resolve} would never find it, since that looks for {@value #EXECUTABLE}. It is
     * renamed after the build for exactly this reason.
     */
    static final String SOURCE_EXECUTABLE = "daemon";

    /** @return the command a user would type to build the daemon themselves */
    public static String buildCommand() {
        return "go install " + SOURCE_PACKAGE;
    }

    /** @return the command that installs the Go toolchain, or {@code null} off macOS */
    public static String goInstallCommand() {
        return isMac() ? "brew install go" : null;
    }

    /**
     * The native libraries the daemon links against, as pkg-config names them.
     *
     * <p>Taken from the upstream build instructions and confirmed by what actually failed:
     * {@code go install} reported {@code mp3}, {@code flac} and {@code vorbis} each unable to run
     * pkg-config, which are the cgo packages wrapping mpg123, FLAC and Vorbis. ALSA is Linux only -
     * macOS uses the AudioToolbox backend and has no {@code alsa.pc} to find.
     */
    static final List<String> REQUIRED_LIBRARIES =
            List.of("ogg", "vorbis", "vorbisfile", "flac", "libmpg123");

    /**
     * How each pkg-config module is spelled as a Homebrew formula.
     *
     * <p><strong>The two names are not the same thing and confusing them is how this got written
     * wrong the first time.</strong> A pkg-config module is what the cgo directive asks for -
     * {@code // #cgo pkg-config: libmpg123} in {@code mp3/decoder.go} - and the formula is what
     * installs it. For mpg123 they differ: the formula is {@code mpg123} and the module is
     * {@code libmpg123}, so checking for a module called {@code mpg123} reported it missing on a
     * machine where it was installed and working. The lists above are the <em>module</em> names,
     * because that is the question the build actually asks.
     */
    static final Map<String, String> BREW_FORMULAS = Map.of(
            "ogg", "libogg",
            "vorbis", "libvorbis",
            "vorbisfile", "libvorbis",
            "flac", "flac",
            "libmpg123", "mpg123",
            "alsa", "",
            "pkg-config", "pkg-config",
            "go", "go");

    /** How each is spelled as a Debian package, for the hint shown on Linux. */
    private static final Map<String, String> APT_PACKAGES = Map.of(
            "ogg", "libogg-dev",
            "vorbis", "libvorbis-dev",
            "vorbisfile", "libvorbis-dev",
            "flac", "libflac-dev",
            "libmpg123", "libmpg123-dev",
            "alsa", "libasound2-dev",
            "pkg-config", "pkg-config",
            "go", "golang");

    /**
     * What a source build needs, and what of it is missing.
     *
     * @param missing        the prerequisites that are not present, in the order to install them
     * @param installCommand the command that would install them, or {@code null} when this platform
     *                       has no one-line answer
     */
    public record BuildPrerequisites(List<String> missing, String installCommand) {

        /** @return whether everything needed to build is present */
        public boolean isSatisfied() {
            return missing.isEmpty();
        }

        /** @return the missing prerequisites as a readable list */
        public String describe() {
            return String.join(", ", missing);
        }
    }

    /**
     * Checks everything a source build needs, before running one.
     *
     * <p><strong>This exists because the build fails several packages deep otherwise.</strong> With
     * no pkg-config on the machine, {@code go install} gets as far as compiling and then reports
     * {@code exec: "pkg-config": executable file not found in $PATH} once per cgo package - three
     * near-identical lines that name the thing Go could not run rather than the thing the user has
     * to install, after a wait. And fixing only that leads straight into the next one: on this
     * machine {@code ogg}, {@code vorbis} and {@code flac} arrive as Homebrew dependencies of
     * go-librespot itself, but {@code mpg123} does not, so the second attempt fails too.
     *
     * @return what is missing, and how to get it
     */
    public static BuildPrerequisites checkBuildPrerequisites() {
        List<String> missing = new java.util.ArrayList<>();
        if (findGo() == null) {
            missing.add("go");
        }
        boolean pkgConfig = findOnPath("pkg-config") != null;
        if (!pkgConfig) {
            missing.add("pkg-config");
        }
        for (String library : REQUIRED_LIBRARIES) {
            if (!hasLibrary(library, pkgConfig)) {
                missing.add(library);
            }
        }
        return new BuildPrerequisites(List.copyOf(missing), installCommandFor(missing));
    }

    /**
     * @param missing what is not installed
     * @return the one command that would install all of it, or {@code null} where there is none
     */
    private static String installCommandFor(List<String> missing) {
        if (missing.isEmpty()) {
            return null;
        }
        if (isMac()) {
            String formulas = missing.stream()
                    .map(name -> BREW_FORMULAS.getOrDefault(name, name))
                    .filter(formula -> !formula.isEmpty())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(" "));
            return formulas.isEmpty() ? null : "brew install " + formulas;
        }
        // Not run on Linux: it needs root, and this application must never ask for that. Shown so
        // it can be copied into a terminal.
        String packages = missing.stream()
                .map(name -> APT_PACKAGES.getOrDefault(name, name))
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        return "sudo apt-get install " + packages;
    }

    /**
     * Reports whether a native library is available to link against.
     *
     * @param library    the pkg-config name
     * @param pkgConfig  whether pkg-config itself is installed
     * @return whether the library can be found
     */
    private static boolean hasLibrary(String library, boolean pkgConfig) {
        if (pkgConfig) {
            // Authoritative: this is the same question the cgo build asks.
            return runsSuccessfully("pkg-config", "--exists", library);
        }
        // pkg-config is missing, so the libraries cannot be asked about the usual way. Looking for
        // the metadata file directly is what pkg-config would do, and it means the user is told
        // about every missing prerequisite at once rather than discovering them one build at a time.
        return findPkgConfigFile(library) != null;
    }

    /**
     * @param library the pkg-config name
     * @return the {@code .pc} file describing it, or {@code null}
     */
    private static Path findPkgConfigFile(String library) {
        List<String> directories = new java.util.ArrayList<>();
        String configured = System.getenv("PKG_CONFIG_PATH");
        if (configured != null && !configured.isBlank()) {
            directories.addAll(List.of(configured.split(java.io.File.pathSeparator)));
        }
        directories.addAll(List.of(
                "/opt/homebrew/lib/pkgconfig",
                "/usr/local/lib/pkgconfig",
                "/usr/lib/pkgconfig",
                "/usr/lib64/pkgconfig",
                "/usr/share/pkgconfig",
                "/usr/lib/x86_64-linux-gnu/pkgconfig",
                "/usr/lib/aarch64-linux-gnu/pkgconfig"));
        for (String directory : directories) {
            if (directory.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(directory, library + ".pc");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException e) {
                LOG.finer("Skipping an unusable pkg-config directory: " + directory);
            }
        }
        return null;
    }

    /**
     * @param command the executable and its arguments
     * @return whether it ran and exited zero
     */
    private static boolean runsSuccessfully(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (InputStream stream = process.getInputStream()) {
                stream.readAllBytes();
            }
            return process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
                    && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * @param executable the name to look for
     * @return the first match on {@code PATH}, or {@code null}
     */
    private static Path findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(entry, executable);
                if (isExecutable(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException e) {
                LOG.finer("Skipping an unusable PATH entry: " + entry);
            }
        }
        return null;
    }

    /**
     * Looks for the Go toolchain.
     *
     * @return the {@code go} executable, or {@code null} when it is not installed
     */
    public static Path findGo() {
        return findOnPath("go");
    }

    /**
     * Builds the daemon from source, into this application's own folder.
     *
     * <p>{@code GOBIN} is set to {@link #installDir()} rather than letting Go use its default, so
     * the result lands where {@link #resolve} already looks and no {@code go env} has to be parsed
     * to find out where that was. The binary is then renamed from {@value #SOURCE_EXECUTABLE} to
     * {@value #EXECUTABLE}, after which it resolves exactly as a downloaded one does.
     *
     * <p>Call this off the interface thread: a cold build downloads the module graph and compiles
     * it, which takes minutes.
     *
     * @param progress told what is happening, for a status line; may be {@code null}
     * @return where the daemon ended up, or why it did not
     */
    public static Resolution buildFromSource(Consumer<String> progress) {
        BuildPrerequisites prerequisites = checkBuildPrerequisites();
        if (!prerequisites.isSatisfied()) {
            // Checked before compiling rather than discovered during it. Without this the build
            // runs for a while and then reports which executable cgo could not run, once per
            // package - which names Go's problem rather than the user's.
            String command = prerequisites.installCommand();
            return new Resolution(null, Origin.NOT_FOUND, "Missing: " + prerequisites.describe()
                    + (command == null ? "" : " - run: " + command));
        }
        Path go = findGo();

        try {
            Files.createDirectories(installDir());
            report(progress, "Building go-librespot from source (this takes a few minutes)");

            ProcessBuilder builder = new ProcessBuilder(go.toString(), "install", SOURCE_PACKAGE);
            builder.redirectErrorStream(true);
            // Go writes the binary here instead of $GOPATH/bin, so there is nothing to locate
            // afterwards and nothing of the user's own is overwritten.
            builder.environment().put("GOBIN", installDir().toString());

            Process process = builder.start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes()).trim();
            }
            if (!process.waitFor(BUILD_TIMEOUT.toMinutes(), java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return new Resolution(null, Origin.NOT_FOUND, "The build did not finish in "
                        + BUILD_TIMEOUT.toMinutes() + " minutes");
            }
            if (process.exitValue() != 0) {
                LOG.warning("go install failed:\n" + output);
                return new Resolution(null, Origin.NOT_FOUND, "Build failed: " + lastLine(output));
            }

            Path built = installDir().resolve(SOURCE_EXECUTABLE);
            if (!Files.isRegularFile(built)) {
                return new Resolution(null, Origin.NOT_FOUND,
                        "The build produced no " + SOURCE_EXECUTABLE + " in " + installDir());
            }
            Path renamed = installedPath();
            Files.move(built, renamed, StandardCopyOption.REPLACE_EXISTING);
            makeExecutable(renamed);

            report(progress, "Built " + renamed);
            LOG.info("Built go-librespot from source at " + renamed);
            return new Resolution(renamed, Origin.DOWNLOADED, "Built from source at " + renamed);

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not build go-librespot from source", e);
            return new Resolution(null, Origin.NOT_FOUND, "Build failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resolution(null, Origin.NOT_FOUND, "The build was interrupted");
        }
    }

    /**
     * @param output a command's whole output
     * @return its last non-blank line, which is where a Go build puts the reason it failed
     */
    private static String lastLine(String output) {
        if (output == null || output.isBlank()) {
            return "no output";
        }
        return output.lines()
                .filter(line -> !line.isBlank())
                .reduce((first, second) -> second)
                .orElse("no output");
    }

    // ------------------------------------------------------------------
    // Finding it
    // ------------------------------------------------------------------

    /**
     * Looks for the daemon without touching the network.
     *
     * <p>Cheap enough to call at startup and on every visit to the Spotify view.
     *
     * @param configuredPath a path the user set, or {@code null}
     * @return where the daemon is, and how it was found
     */
    public static Resolution resolve(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath.trim());
            if (isExecutable(configured)) {
                return new Resolution(configured, Origin.CONFIGURED, "Using " + configured);
            }
            LOG.warning("Configured go-librespot path is not executable, ignoring it: " + configured);
        }

        Path installed = installedPath();
        if (isExecutable(installed)) {
            return new Resolution(installed, Origin.DOWNLOADED, "Using the downloaded copy");
        }

        Path onPath = searchPath();
        if (onPath != null) {
            return new Resolution(onPath, Origin.ON_PATH, "Found on PATH at " + onPath);
        }

        return new Resolution(null, Origin.NOT_FOUND, notFoundMessage());
    }

    private static String notFoundMessage() {
        if (!isSupportedPlatform()) {
            return "Spotify needs a named pipe, which this platform does not have";
        }
        if (installCommand() != null) {
            // Kept short: the Spotify view prints the command itself, on its own line and on the
            // button, so repeating it here would put the same sentence on screen twice.
            return "No macOS build is published - Homebrew has one";
        }
        return isDownloadable()
                ? "Not installed - it can be downloaded"
                : "Not installed, and there is no published build for this platform";
    }

    /**
     * Walks {@code PATH} looking for the executable.
     *
     * @return the first executable found, or {@code null}
     */
    private static Path searchPath() {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(entry, EXECUTABLE);
                if (isExecutable(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException e) {
                // An unparseable PATH entry is not worth failing the whole search over.
                LOG.finer("Skipping an unusable PATH entry: " + entry);
            }
        }
        return null;
    }

    private static boolean isExecutable(Path candidate) {
        return candidate != null && Files.isRegularFile(candidate) && Files.isExecutable(candidate);
    }

    // ------------------------------------------------------------------
    // Fetching it
    // ------------------------------------------------------------------

    /**
     * Downloads and unpacks the daemon into this application's own folder.
     *
     * <p><strong>Call this off the interface thread.</strong> It is a 6 MB download followed by an
     * extraction, and it is written to be interruptible and to fail quietly: every failure returns a
     * {@link Origin#NOT_FOUND} resolution carrying the reason, and nothing propagates.
     *
     * <p>The archive is unpacked with {@code tar} rather than by a hand-written reader. The one
     * platform this method can ever run on is Linux, where {@code tar} is guaranteed, and sixty
     * lines of ustar header parsing is a liability with no upside here - unlike the playback
     * structures, nothing about untarring is being defended at a viva.
     *
     * @param progress told what is happening, for a status line; may be {@code null}
     * @return where the daemon ended up, or why it did not
     */
    public static Resolution fetch(Consumer<String> progress) {
        String asset = assetName();
        if (asset == null) {
            return new Resolution(null, Origin.NOT_FOUND, notFoundMessage());
        }

        Path archive = null;
        try {
            report(progress, "Downloading " + asset);
            Files.createDirectories(installDir());
            archive = Files.createTempFile(installDir(), "go-librespot", ".tar.gz");
            download(DOWNLOAD_BASE + asset, archive);

            report(progress, "Unpacking");
            extract(archive, installDir());

            Path binary = installedPath();
            if (!Files.isRegularFile(binary)) {
                return new Resolution(null, Origin.NOT_FOUND,
                        "The archive did not contain " + EXECUTABLE);
            }
            makeExecutable(binary);
            report(progress, "Installed " + binary);
            LOG.info("Fetched go-librespot to " + binary);
            return new Resolution(binary, Origin.DOWNLOADED, "Downloaded to " + binary);

        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not fetch go-librespot", e);
            return new Resolution(null, Origin.NOT_FOUND, "Download failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resolution(null, Origin.NOT_FOUND, "Download was interrupted");
        } finally {
            deleteQuietly(archive);
        }
    }

    /**
     * @param url         where to fetch from
     * @param destination where to write it
     * @throws IOException if the transfer fails or the server answers with anything but 200
     */
    private static void download(String url, Path destination) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(FETCH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
            try (InputStream body = response.body()) {
                Files.copy(body, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * @param archive     the downloaded tarball
     * @param destination where to unpack it
     * @throws IOException if {@code tar} is missing or reports a failure
     */
    private static void extract(Path archive, Path destination) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "tar", "-xzf", archive.toString(), "-C", destination.toString(), EXECUTABLE);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes()).trim();
        }
        if (!process.waitFor(1, java.util.concurrent.TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("tar did not finish");
        }
        if (process.exitValue() != 0) {
            throw new IOException("tar failed: " + (output.isEmpty() ? "exit " + process.exitValue() : output));
        }
    }

    /**
     * Marks the extracted file executable, and clears macOS's quarantine flag if it is set.
     *
     * <p>Gatekeeper refuses to run an unsigned third-party binary that carries
     * {@code com.apple.quarantine}, which it acquires when the project arrives as a browser
     * download rather than a clone. Stripping it is best-effort: {@code xattr} may be absent and
     * the flag may not be there, and neither is worth reporting.
     *
     * @param binary the extracted executable
     */
    private static void makeExecutable(Path binary) {
        try {
            binary.toFile().setExecutable(true, false);
        } catch (SecurityException e) {
            LOG.warning("Could not mark " + binary + " executable: " + e.getMessage());
        }
        if (!isMac()) {
            return;
        }
        try {
            new ProcessBuilder("xattr", "-d", "com.apple.quarantine", binary.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException e) {
            LOG.finer("xattr is not available; leaving any quarantine flag in place");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.finer("Could not delete " + path + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Version
    // ------------------------------------------------------------------

    /**
     * The daemon's version, as it announces itself on startup.
     *
     * <p><strong>There is no {@code --version} flag.</strong> Measured against the real binary:
     * {@code go-librespot --help} lists exactly two options, {@code --conf} and
     * {@code --config_dir}, and anything else exits with
     * {@code level=fatal msg="failed loading config" error="unknown flag: --version"}. An earlier
     * version of this class ran {@code --version} and returned that error message as though it were
     * a version string, which is worse than not knowing - it would have been shown to the user in
     * place of the answer.
     *
     * <p>So the version comes from the log instead, where the daemon prints
     * {@code running go-librespot 0.8.0} as its first line. {@code SpotifyDaemon} watches for it.
     */
    static final Pattern VERSION_LINE = Pattern.compile("running go-librespot ([0-9][^\\s\"]*)");

    /**
     * Reads a version out of one of the daemon's log lines.
     *
     * @param logLine a line the daemon printed
     * @return the version, or {@code null} when this is not the line that carries it
     */
    public static String versionFrom(String logLine) {
        if (logLine == null) {
            return null;
        }
        Matcher matcher = VERSION_LINE.matcher(logLine);
        return matcher.find() ? matcher.group(1) : null;
    }
}
