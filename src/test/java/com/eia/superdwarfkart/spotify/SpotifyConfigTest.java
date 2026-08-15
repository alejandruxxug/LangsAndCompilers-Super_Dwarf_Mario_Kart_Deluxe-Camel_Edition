package com.eia.superdwarfkart.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daemon's generated configuration.
 *
 * <p>Four of these values decide whether this application or Spotify chooses what plays next, and
 * <strong>every one of them fails silently</strong>: get one wrong and playback carries on sounding
 * completely normal while the circular list, the queue and the binary search tree stop being
 * consulted. There is no exception, no log line and nothing on screen. A test is the only thing
 * that notices, which is why these are asserted one by one rather than by comparing the whole file.
 */
class SpotifyConfigTest {

    @Test
    @DisplayName("autoplay and zeroconf are off, so the active PlaybackMode owns the running order")
    void theRunningOrderStaysWithThePlaybackMode() {
        String yaml = SpotifyConfig.render();

        assertTrue(yaml.contains("disable_autoplay: true"),
                "with autoplay on, Spotify queues the next track and the graded structures become "
                        + "decorations - silently");
        assertTrue(yaml.contains("zeroconf_enabled: false"),
                "a discoverable device lets a phone take over the running order mid-song");
    }

    @Test
    @DisplayName("crossfade is off, because it destroys beat alignment at the track boundary")
    void crossfadeIsOff() {
        assertTrue(SpotifyConfig.render().contains("crossfade_duration: 0"));
    }

    @Test
    @DisplayName("the pipe backend is what makes the daemon silent and this application the player")
    void thePipeBackendIsConfigured() {
        String yaml = SpotifyConfig.render();

        assertTrue(yaml.contains("audio_backend: pipe"));
        assertTrue(yaml.contains("audio_output_pipe_format: s16le"),
                "s16le at 44.1 kHz stereo is PcmFormat.PLAYBACK_FORMAT, which is the whole reason "
                        + "nothing is decoded on the Java side");
        assertTrue(yaml.contains(SpotifyConfig.fifoPath().toString()),
                "the daemon has to be told the same pipe this application reads");
    }

    @Test
    @DisplayName("volume is applied once, by this application, so the meters read the track")
    void volumeIsExternal() {
        // Left false, the pipe driver scales every sample by the square of its own volume - so the
        // meters would follow a slider rather than the music, and the generated courses with them.
        assertTrue(SpotifyConfig.render().contains("external_volume: true"));
    }

    @Test
    @DisplayName("normalisation is left on, so difficulty does not change with the track's loudness")
    void normalisationIsLeftAlone() {
        assertFalse(SpotifyConfig.render().contains("normalisation_disabled: true"),
                "Spotify's -14 LUFS target is what keeps the meter range, and therefore the "
                        + "generated course, consistent between tracks");
    }

    @Test
    @DisplayName("the API is bound to loopback, because it can start playback and mint a token")
    void theApiIsBoundToLoopback() {
        String yaml = SpotifyConfig.render();
        assertTrue(yaml.contains("server:"));
        assertTrue(yaml.contains("port: 3678"));
        assertTrue(yaml.contains("'127.0.0.1'"));
    }

    @Test
    @DisplayName("paths are quoted, so an application home with a space or colon still parses")
    void pathsAreQuoted() {
        assertEquals("'/tmp/plain'", SpotifyConfig.quote("/tmp/plain"));
        assertEquals("'/tmp/with space'", SpotifyConfig.quote("/tmp/with space"));
        // An unquoted colon turns a scalar into a mapping and the daemon reads a different file.
        assertEquals("'/tmp/a:b'", SpotifyConfig.quote("/tmp/a:b"));
        // YAML escapes a single quote by doubling it, and that is the whole of the rule.
        assertEquals("'it''s here'", SpotifyConfig.quote("it's here"));
    }

    @Test
    @DisplayName("the file says it is generated, because it is rewritten on every launch")
    void theFileWarnsThatItIsGenerated() {
        assertTrue(SpotifyConfig.render().contains("Do not edit"));
    }
}
