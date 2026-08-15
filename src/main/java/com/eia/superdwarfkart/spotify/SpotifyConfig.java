package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.app.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Writes the daemon's configuration, and makes the pipe its audio comes out of.
 *
 * <p><strong>The configuration is generated on every launch and is never user-edited.</strong> It
 * lives in a private {@code -config_dir} beside the credentials, so there is exactly one place the
 * daemon's behaviour is decided and it is this file. A setting the user could change by hand is a
 * setting that can silently contradict what the application assumes.
 *
 * <h2>Four values here are correctness requirements, not preferences</h2>
 *
 * <ul>
 *   <li><strong>{@code zeroconf_enabled: false}</strong> and
 *       <strong>{@code disable_autoplay: true}</strong>. Get either wrong and Spotify decides the
 *       running order instead of the active {@code PlaybackMode} - and the failure is silent:
 *       playback carries on sounding perfectly normal while the circular list, the queue and the
 *       binary search tree quietly become decorations. Those three structures are the graded core
 *       of this project; nothing may be allowed to take the running order away from them.</li>
 *   <li><strong>{@code crossfade_duration: 0}</strong>. A crossfade blends the end of one track
 *       into the start of the next, which destroys beat alignment exactly at the boundary and
 *       feeds the analyser a passage that is two songs at once.</li>
 *   <li><strong>{@code audio_backend: pipe}</strong>. With the pipe backend the daemon has no audio
 *       device at all: it makes no sound of its own and writes {@code s16le} to a FIFO. This
 *       application reads that FIFO, writes it to the sound card and taps the same buffer for the
 *       meters and the beat analyser - byte for byte the arrangement
 *       {@code LocalFileAudioSource} already has, with a different source of bytes.</li>
 * </ul>
 *
 * <p><strong>{@code external_volume: true} is the fourth, and it is subtler.</strong> Left false,
 * the pipe driver multiplies every sample by the square of its own volume setting before writing
 * it - so the meters would read the daemon's volume rather than the track's, the beat analyser
 * would measure a quieter signal than the one on the record, and difficulty would change with a
 * slider. Volume belongs to the {@code SourceDataLine} in this application, in exactly one place,
 * as it does for a local file.
 *
 * <p><strong>Normalisation is deliberately left on.</strong> Spotify's -14 LUFS target is what
 * keeps the meter range and therefore the generated courses consistent between tracks; disabling
 * it makes a quiet song generate a course with nothing in it.
 *
 * <p>The format the pipe produces - {@code s16le} at 44.1 kHz stereo - <em>is</em>
 * {@code PcmFormat.PLAYBACK_FORMAT}. That is the whole reason this integration needs no decoding
 * on the Java side.
 */
public final class SpotifyConfig {

    private static final Logger LOG = Logger.getLogger(SpotifyConfig.class.getName());

    /** Filename the daemon expects inside its config directory. */
    private static final String CONFIG_FILE = "config.yml";

    /** Name of the audio pipe, inside the same private folder. */
    private static final String FIFO_FILE = "pcm.fifo";

    /** How long {@code mkfifo} is given before it is treated as broken. */
    private static final long MKFIFO_TIMEOUT_SECONDS = 10;

    private SpotifyConfig() {
        throw new AssertionError("SpotifyConfig is a utility holder and must not be instantiated");
    }

    /** @return the private configuration directory passed to the daemon as {@code -config_dir} */
    public static Path configDir() {
        return AppConfig.spotifyDir();
    }

    /** @return the generated configuration file */
    public static Path configFile() {
        return configDir().resolve(CONFIG_FILE);
    }

    /** @return the named pipe the daemon writes PCM to */
    public static Path fifoPath() {
        return configDir().resolve(FIFO_FILE);
    }

    /**
     * @return the file holding the Spotify session credentials, written by the daemon after a
     *         successful login and read back on every later launch
     */
    public static Path stateFile() {
        return configDir().resolve("state.json");
    }

    /** @return whether a Spotify session has been established on this machine before */
    public static boolean hasStoredCredentials() {
        Path state = stateFile();
        try {
            return Files.isRegularFile(state) && Files.size(state) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Writing the configuration
    // ------------------------------------------------------------------

    /**
     * Regenerates the configuration file.
     *
     * <p>Overwrites unconditionally: this file belongs to the application, and the whole point of
     * rewriting it every launch is that its contents cannot drift from what the code assumes.
     * {@code state.json} in the same folder is the daemon's and is never touched.
     *
     * @return the file that was written
     * @throws IOException if the folder or the file cannot be written
     */
    public static Path write() throws IOException {
        Files.createDirectories(configDir());
        String yaml = render();
        Path file = configFile();
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        LOG.fine("Wrote go-librespot configuration to " + file);
        return file;
    }

    /**
     * Builds the configuration document.
     *
     * <p>Written by hand rather than through a YAML library. It is a flat document of literals with
     * one nested block, the project has no YAML dependency, and adding one to emit twenty lines
     * would be the largest thing in the build for the smallest reason.
     *
     * <p>Public so the smoke test can read the document back and check the four values that decide
     * whether this application or Spotify owns the running order - each of which fails silently.
     *
     * @return the complete file contents
     */
    public static String render() {
        StringBuilder yaml = new StringBuilder(768);
        yaml.append("# Generated by ").append(AppConfig.APP_NAME).append(" on every launch.\n")
            .append("# Do not edit: this file is overwritten each time the application starts.\n")
            .append("# See spotify/SpotifyConfig.java for why each value is what it is.\n\n");

        yaml.append("device_name: ").append(quote(AppConfig.SPOTIFY_DEVICE_NAME)).append('\n');
        yaml.append("device_type: computer\n\n");

        yaml.append("# The daemon owns no audio device. It writes PCM to this pipe and makes no sound.\n");
        yaml.append("audio_backend: pipe\n");
        yaml.append("audio_output_pipe: ").append(quote(fifoPath().toString())).append('\n');
        yaml.append("audio_output_pipe_format: s16le\n");
        yaml.append("bitrate: ").append(AppConfig.SPOTIFY_BITRATE).append('\n');
        yaml.append("# Volume is applied once, by this application's own output line.\n");
        yaml.append("external_volume: true\n\n");

        yaml.append("# A crossfade blends adjacent tracks and destroys beat alignment at the boundary.\n");
        yaml.append("crossfade_duration: 0\n");
        yaml.append("# Correctness, not preference: the active PlaybackMode chooses what plays next.\n");
        yaml.append("disable_autoplay: true\n");
        yaml.append("zeroconf_enabled: false\n\n");

        yaml.append("credentials:\n");
        yaml.append("  type: interactive\n\n");

        yaml.append("cache:\n");
        yaml.append("  enabled: false\n\n");

        yaml.append("server:\n");
        yaml.append("  enabled: true\n");
        yaml.append("  address: ").append(quote(AppConfig.SPOTIFY_API_HOST)).append('\n');
        yaml.append("  port: ").append(AppConfig.SPOTIFY_API_PORT).append('\n');

        return yaml.toString();
    }

    /**
     * Quotes a scalar for YAML.
     *
     * <p>Single quotes, with any internal single quote doubled - which is the whole of YAML's
     * single-quoted escaping. It matters because these are filesystem paths: the application home
     * can be relocated to anywhere, including somewhere with a space or a colon in it, and an
     * unquoted colon turns a path into a mapping.
     *
     * @param value the scalar to quote
     * @return the quoted scalar
     */
    static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    // ------------------------------------------------------------------
    // The pipe
    // ------------------------------------------------------------------

    /**
     * Creates the named pipe, if it is not already there.
     *
     * <p>Java cannot make a FIFO - there is no API for it anywhere in the platform - so
     * {@code mkfifo} is run as a process. That is what makes the Spotify path POSIX-only while
     * local files stay portable.
     *
     * <p>An existing pipe is left alone rather than recreated. Recreating it would break a daemon
     * from a previous run that still holds the old one open, and the file is inert between runs.
     *
     * @return the pipe
     * @throws IOException if the pipe does not exist and cannot be created
     */
    public static Path createFifo() throws IOException {
        Files.createDirectories(configDir());
        Path fifo = fifoPath();
        if (Files.exists(fifo)) {
            return fifo;
        }
        try {
            Process process = new ProcessBuilder("mkfifo", fifo.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (!process.waitFor(MKFIFO_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("mkfifo did not finish");
            }
            // Tolerate the race where something else created it between the check and the call.
            if (process.exitValue() != 0 && !Files.exists(fifo)) {
                throw new IOException("mkfifo failed: "
                        + (output.isEmpty() ? "exit " + process.exitValue() : output));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while creating the audio pipe", e);
        }
        LOG.fine("Created the audio pipe at " + fifo);
        return fifo;
    }
}
