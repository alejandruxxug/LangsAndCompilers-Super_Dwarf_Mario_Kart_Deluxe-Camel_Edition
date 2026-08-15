package com.eia.superdwarfkart.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one class in the application that knows there is more than one kind of song.
 *
 * <p>Everything here is about a failure that throws nothing. The meters, the beat analyser and the
 * end-of-track handler attach themselves once at startup, long before anybody has played a Spotify
 * track - so if they are not carried onto the source built later, the first streamed song plays
 * with dead meters and the running order stops when it finishes. Both look like something else
 * broke.
 */
class RoutingAudioSourceTest {

    /** A source that records what it was asked to do, so routing is observable. */
    private static final class RecordingSource implements AudioSource {
        private final List<String> loads = new ArrayList<>();
        private final List<PcmListener> taps = new ArrayList<>();
        private Runnable onEndOfMedia;
        private double volume = 1;
        private boolean playing;
        private boolean closed;

        @Override
        public void load(String locator) {
            loads.add(locator);
        }

        @Override
        public void play() {
            playing = true;
        }

        @Override
        public void pause() {
            playing = false;
        }

        @Override
        public void stop() {
            playing = false;
        }

        @Override
        public void seek(Duration position) {
            // Not needed here.
        }

        @Override
        public Duration position() {
            return Duration.ZERO;
        }

        @Override
        public Duration duration() {
            return Duration.ZERO;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public boolean isLoaded() {
            return !loads.isEmpty();
        }

        @Override
        public void addPcmListener(PcmListener listener) {
            taps.add(listener);
        }

        @Override
        public void removePcmListener(PcmListener listener) {
            taps.remove(listener);
        }

        @Override
        public void setOnEndOfMedia(Runnable action) {
            this.onEndOfMedia = action;
        }

        @Override
        public void setVolume(double newVolume) {
            this.volume = newVolume;
        }

        @Override
        public double volume() {
            return volume;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    @DisplayName("a file path goes to the local source and a track URI to the Spotify one")
    void itRoutesByLocator() {
        RecordingSource local = new RecordingSource();
        RecordingSource streamed = new RecordingSource();
        RoutingAudioSource routing = new RoutingAudioSource(local, () -> streamed);

        routing.load("/music/song.mp3");
        assertEquals(List.of("/music/song.mp3"), local.loads);
        assertTrue(streamed.loads.isEmpty());
        assertFalse(routing.isStreaming());

        routing.load("spotify:track:abc");
        assertEquals(List.of("spotify:track:abc"), streamed.loads);
        assertEquals(1, local.loads.size(), "the local source must not see a URI");
        assertTrue(routing.isStreaming());
        assertSame(streamed, routing.active());
    }

    /**
     * The bug this whole class exists to prevent, and it was nearly shipped.
     *
     * <p>The Spotify source is built the first time a streamed song is loaded, which is long after
     * the meters and the analyser registered. A tap list that is not replayed onto it leaves them
     * reading silence for every streamed song, and nothing anywhere says so.
     */
    @Test
    @DisplayName("taps registered before the Spotify source existed still reach it")
    void tapsReachASourceBuiltLater() {
        RecordingSource local = new RecordingSource();
        RecordingSource streamed = new RecordingSource();
        RoutingAudioSource routing = new RoutingAudioSource(local, () -> streamed);

        PcmListener meter = (buffer, offset, length) -> { };
        routing.addPcmListener(meter);
        assertEquals(1, local.taps.size());
        assertTrue(streamed.taps.isEmpty(), "not built yet");

        routing.load("spotify:track:abc");

        assertEquals(1, streamed.taps.size(),
                "the meters and the beat analyser have to follow onto the source built later");
        assertSame(meter, streamed.taps.get(0));
    }

    @Test
    @DisplayName("the end-of-track handler reaches a source built later, or the queue stops dead")
    void theEndOfTrackHandlerReachesASourceBuiltLater() {
        RecordingSource local = new RecordingSource();
        RecordingSource streamed = new RecordingSource();
        RoutingAudioSource routing = new RoutingAudioSource(local, () -> streamed);

        AtomicInteger advances = new AtomicInteger();
        routing.setOnEndOfMedia(advances::incrementAndGet);
        routing.load("spotify:track:abc");

        streamed.onEndOfMedia.run();
        assertEquals(1, advances.get(),
                "without this the running order stops after the first streamed song");
    }

    @Test
    @DisplayName("volume set before the switch applies after it")
    void volumeCarriesAcross() {
        RecordingSource local = new RecordingSource();
        RecordingSource streamed = new RecordingSource();
        RoutingAudioSource routing = new RoutingAudioSource(local, () -> streamed);

        routing.setVolume(0.25);
        routing.load("spotify:track:abc");

        assertEquals(0.25, streamed.volume, 1e-9);
    }

    @Test
    @DisplayName("handing over pauses the source being left, so two do not play at once")
    void handingOverPausesTheOtherSource() {
        RecordingSource local = new RecordingSource();
        RecordingSource streamed = new RecordingSource();
        RoutingAudioSource routing = new RoutingAudioSource(local, () -> streamed);

        routing.load("/music/song.mp3");
        routing.play();
        assertTrue(local.isPlaying());

        routing.load("spotify:track:abc");
        assertFalse(local.isPlaying(), "the local source would otherwise play under the new one");
    }

    @Test
    @DisplayName("a Spotify track with no session available fails with a message, not a crash")
    void unavailableSpotifyIsReported() {
        RoutingAudioSource routing = new RoutingAudioSource(new RecordingSource(), () -> null);

        AudioException failure = assertThrows(AudioException.class,
                () -> routing.load("spotify:track:abc"));
        assertTrue(failure.getMessage().toLowerCase().contains("spotify"));
    }

    @Test
    @DisplayName("closing closes both, so nothing holds the sound card")
    void closingClosesBoth() {
        RecordingSource local = new RecordingSource();
        RecordingSource streamed = new RecordingSource();
        RoutingAudioSource routing = new RoutingAudioSource(local, () -> streamed);

        routing.load("spotify:track:abc");
        routing.close();

        assertTrue(local.closed);
        assertTrue(streamed.closed);
    }

    @Test
    @DisplayName("only a spotify: locator is treated as one")
    void onlySpotifyLocatorsAreStreamed() {
        assertTrue(RoutingAudioSource.isSpotify("spotify:track:abc"));
        assertFalse(RoutingAudioSource.isSpotify("/music/spotify/song.mp3"));
        assertFalse(RoutingAudioSource.isSpotify(null));
    }
}
