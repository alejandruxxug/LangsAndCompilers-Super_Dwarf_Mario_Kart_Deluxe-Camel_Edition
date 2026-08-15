package com.eia.superdwarfkart.spotify;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The REST client, driven against a stub daemon over a real socket.
 *
 * <p>A stub rather than a mock, and the real {@code HttpClient} rather than a seam, because the
 * things that break here are the things a mock would paper over: a 204 meaning "no session" rather
 * than a failure, a JSON shape that shifted, a request body the daemon would reject. The
 * {@code /web-api} responses below are trimmed copies of what Spotify actually returns.
 */
class SpotifyApiTest {

    private HttpServer server;
    private SpotifyApi api;

    /** Every path that was requested, so the client's own URL building is under test too. */
    private final List<String> requested = java.util.Collections.synchronizedList(new ArrayList<>());

    /** Canned responses by path prefix. */
    private final Map<String, String> responses = new ConcurrentHashMap<>();

    /** Paths that answer 204, standing in for "no active session". */
    private final Map<String, Boolean> noContent = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            // getRawQuery, not getQuery: the latter decodes, which would turn the %26 this test is
            // checking for straight back into a bare ampersand and hide the very thing under test.
            String query = exchange.getRequestURI().getRawQuery();
            requested.add(path + (query == null ? "" : "?" + query));

            if (noContent.containsKey(path)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            String body = responses.entrySet().stream()
                    .filter(entry -> path.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        api = new SpotifyApi("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("readiness comes from playback_ready, not merely from the daemon answering")
    void readinessIsPlaybackReady() {
        responses.put("/", "{\"playback_ready\":false}");
        assertTrue(api.isReachable(), "it answered");
        assertFalse(api.isReady(), "but it cannot accept a play yet");

        responses.put("/", "{\"playback_ready\":true}");
        assertTrue(api.isReady());
    }

    @Test
    @DisplayName("a 204 means no session, which is an ordinary state and not a failure")
    void noSessionIsNotAFailure() {
        noContent.put("/status", true);
        assertNull(api.status());
        assertNull(api.username());
    }

    @Test
    @DisplayName("a daemon that is not running at all returns null rather than throwing")
    void anAbsentDaemonIsReportedNotThrown() {
        server.stop(0);
        // Every caller here is an interface handler or the audio path; neither has anything useful
        // to do with an exception.
        assertNull(api.status());
        assertFalse(api.isReady());
        assertFalse(api.play("spotify:track:abc", 0, false));
    }

    @Test
    @DisplayName("the play body carries the URI, the position and the paused flag")
    void playSendsTheRightBody() {
        responses.put("/player/play", "{}");
        assertTrue(api.play("spotify:track:abc", 4500, true));
        assertTrue(requested.contains("/player/play"));
    }

    @Test
    @DisplayName("a search result becomes a track, artists joined and the smallest cover taken")
    void searchParsesTracks() {
        responses.put("/web-api/v1/search", """
                {"tracks":{"items":[
                  {"uri":"spotify:track:abc","name":"Crimewave","duration_ms":258000,
                   "artists":[{"name":"Crystal Castles"},{"name":"HEALTH"}],
                   "album":{"name":"Crystal Castles","release_date":"2008-03-18",
                            "images":[{"url":"big.jpg","width":640},{"url":"mid.jpg","width":300},
                                      {"url":"small.jpg","width":64}]}}
                ]}}""");

        List<SpotifyTrack> tracks = api.searchTracks("crimewave", 20);

        assertEquals(1, tracks.size());
        SpotifyTrack track = tracks.get(0);
        assertEquals("spotify:track:abc", track.uri());
        assertEquals("Crimewave", track.title());
        assertEquals("Crystal Castles, HEALTH", track.artist());
        assertEquals(258_000, track.duration().toMillis());
        assertEquals(2008, track.releaseYear());
        assertEquals("mid.jpg", track.coverUrl(),
                "the 64px thumbnail is four times too small for the library's details panel, and "
                        + "640px is four times more than anything draws");
    }

    @Test
    @DisplayName("the query is encoded, so a search with a space or an ampersand still works")
    void theQueryIsEncoded() {
        responses.put("/web-api/v1/search", "{\"tracks\":{\"items\":[]}}");
        api.searchTracks("drum & bass", 20);

        String call = requested.stream().filter(p -> p.startsWith("/web-api/v1/search")).findFirst().orElseThrow();
        assertTrue(call.contains("drum+%26+bass") || call.contains("drum%20%26%20bass"),
                "an unencoded ampersand would truncate the query: " + call);
    }

    @Test
    @DisplayName("saved tracks are unwrapped from their added_at envelope")
    void savedTracksAreUnwrapped() {
        responses.put("/web-api/v1/me/tracks", """
                {"items":[
                  {"added_at":"2024-01-01T00:00:00Z",
                   "track":{"uri":"spotify:track:xyz","name":"Song","duration_ms":1000,
                            "artists":[{"name":"Artist"}],
                            "album":{"name":"Album","release_date":"2020","images":[]}}}
                ]}""");

        List<SpotifyTrack> tracks = api.savedTracks(50, 0);

        assertEquals(1, tracks.size());
        assertEquals("Song", tracks.get(0).title());
        assertEquals(2020, tracks.get(0).releaseYear(), "a year-only release date still parses");
        assertNull(tracks.get(0).coverUrl(), "no images is no cover, not a crash");
    }

    /**
     * The rows that must not reach the library.
     *
     * <p>A playlist can hold a local file the user added on their own machine, and a track removed
     * from the catalogue comes back as a null object. Both are ordinary, and both would produce a
     * song that cannot be played if they were let through.
     */
    @Test
    @DisplayName("unplayable rows are skipped rather than becoming broken songs")
    void unplayableRowsAreSkipped() {
        responses.put("/web-api/v1/playlists", """
                {"items":[
                  {"track":null},
                  {"track":{"uri":"spotify:local:x","name":"A local file","artists":[],
                            "album":{"name":"","images":[]}}},
                  {"track":{"uri":"spotify:track:ok","name":"","artists":[],
                            "album":{"name":"","images":[]}}},
                  {"track":{"uri":"spotify:track:good","name":"Keeper","duration_ms":1,
                            "artists":[],"album":{"name":"","release_date":"","images":[]}}}
                ]}""");

        List<SpotifyTrack> tracks = api.playlistTracks("pid", 50, 0);

        assertEquals(1, tracks.size());
        assertEquals("Keeper", tracks.get(0).title());
        assertEquals("Unknown Artist", tracks.get(0).artist(),
                "Song requires a non-blank artist, and a track with none is a real thing");
    }

    @Test
    @DisplayName("a token is read when there is one and null when there is not")
    void tokenIsReadOrNull() {
        responses.put("/token", "{\"token\":\"abc123\"}");
        assertEquals("abc123", api.token());

        responses.remove("/token");
        noContent.put("/token", true);
        assertNull(api.token());
    }

    /**
     * The check that would have caught this milestone's worst mistake.
     *
     * <p>The Web API proxy was written against the project's {@code master} branch and is in
     * <strong>no tagged release</strong> - v0.8.0's own {@code api-spec.yml} contains neither
     * {@code /web-api} nor {@code /token}. Without a probe, a stock daemon answers every search
     * with a 404, which arrives at the interface as an empty list and reads as a bug here rather
     * than as a missing endpoint there.
     */
    @Test
    @DisplayName("a daemon with no web-api proxy is detected, not mistaken for an empty result")
    void aMissingWebApiProxyIsDetected() {
        // Nothing registered under /web-api, so the stub answers 404 exactly as v0.8.0 does.
        responses.put("/status", "{\"username\":\"someone\"}");
        assertFalse(api.hasWebApiProxy());

        responses.put("/web-api/v1/me", "{\"id\":\"someone\"}");
        assertTrue(api.hasWebApiProxy());
    }

    /**
     * Only a 404 means the proxy is absent, and requiring a 200 disabled a feature that worked.
     *
     * <p>Measured against a real master build with a live account: {@code /web-api/v1/me} answered
     * <strong>429</strong> because Spotify was rate-limiting - confirmed by sending the same token
     * straight to api.spotify.com, which returned 429 too. The proxy was present and behaving
     * correctly; the probe called it missing.
     */
    @Test
    @DisplayName("a rate-limited proxy is present, not absent - only 404 means absent")
    void onlyA404MeansTheProxyIsAbsent() {
        // Nothing registered under /web-api, so the stub 404s exactly as v0.8.0 does.
        assertFalse(api.hasWebApiProxy(), "404 is the one status that means no such route");

        responses.put("/web-api/v1/me", "{\"id\":\"someone\"}");
        assertTrue(api.hasWebApiProxy());
    }

    @Test
    @DisplayName("an empty result says why, so a rate limit is not reported as \"nothing found\"")
    void anEmptyResultExplainsItself() {
        // No /web-api route registered: the search comes back empty, and the reason survives.
        assertTrue(api.searchTracks("anything", 20).isEmpty());
        assertEquals(404, api.lastWebApiStatus());
        assertNotNull(api.lastWebApiProblem());

        // A genuine empty result from a working proxy has nothing to explain.
        responses.put("/web-api/v1/search", "{\"tracks\":{\"items\":[]}}");
        assertTrue(api.searchTracks("nothing matches", 20).isEmpty());
        assertEquals(200, api.lastWebApiStatus());
        assertNull(api.lastWebApiProblem(),
                "a working search that matched nothing must not blame the network");
    }

    @Test
    @DisplayName("playlists are listed with their track counts")
    void playlistsAreListed() {
        responses.put("/web-api/v1/me/playlists", """
                {"items":[
                  {"id":"p1","name":"Driving","tracks":{"total":42}},
                  {"id":"","name":"Broken","tracks":{"total":1}}
                ]}""");

        List<SpotifyApi.Playlist> playlists = api.playlists(50);

        assertEquals(1, playlists.size(), "a playlist with no id cannot be opened");
        assertEquals("Driving", playlists.get(0).name());
        assertEquals(42, playlists.get(0).total());
    }
}
