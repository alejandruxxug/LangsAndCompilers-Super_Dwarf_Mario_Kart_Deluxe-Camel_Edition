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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalogue search on our own credentials, driven against a stub Spotify over a real socket.
 *
 * <p>A stub rather than a mock, for the same reason {@link SpotifyApiTest} uses one: what breaks
 * here is the shape of the wire traffic - the Basic authorisation header, the form-encoded grant,
 * a {@code Retry-After} that arrives as a header rather than in the body - and a mock would agree
 * with whatever the code did. The response bodies are trimmed copies of what Spotify returns.
 */
class SpotifyCatalogTest {

    private HttpServer server;
    private SpotifyCatalog catalog;

    private final List<String> requested = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger tokensIssued = new AtomicInteger();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastTokenBody = new AtomicReference<>();

    /** What the search endpoint answers with next, and how. */
    private volatile int searchStatus = 200;
    private volatile String searchBody = TRACKS_JSON;
    private volatile String searchRetryAfter;

    /** What the token endpoint answers with. */
    private volatile int tokenStatus = 200;
    private volatile String tokenBody =
            "{\"access_token\":\"stub-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

    /** What the artist endpoint answers with; the genre lookup the add dialog makes. */
    private volatile int artistStatus = 200;
    private volatile String artistBody =
            "{\"id\":\"0gxyHStUsqpMadRV0Di1Qt\",\"name\":\"Rick Astley\","
                    + "\"genres\":[\"new romantic\",\"dance pop\"]}";

    private static final String TRACKS_JSON = """
            {"tracks":{"items":[
              {"uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC",
               "name":"Never Gonna Give You Up",
               "duration_ms":213573,
               "artists":[{"name":"Rick Astley","id":"0gxyHStUsqpMadRV0Di1Qt"}],
               "album":{"name":"Whenever You Need Somebody",
                        "release_date":"1987-11-12",
                        "images":[{"url":"https://i.example/640.jpg","width":640,"height":640},
                                  {"url":"https://i.example/300.jpg","width":300,"height":300},
                                  {"url":"https://i.example/64.jpg","width":64,"height":64}]}},
              {"uri":"spotify:local:whatever","name":"A local file"},
              {"uri":"spotify:track:2","name":"","artists":[]}
            ]}}""";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/token", exchange -> {
            requested.add("/token");
            tokensIssued.incrementAndGet();
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastTokenBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, tokenStatus, tokenBody, null);
        });

        server.createContext("/v1/search", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            requested.add("/v1/search" + (query == null ? "" : "?" + query));
            respond(exchange, searchStatus, searchBody, searchRetryAfter);
        });

        // Registered at the path the catalogue derives from the search endpoint rather than at one
        // written out here: if artistUrl stopped putting it beside v1/search, the request would arrive
        // somewhere with no handler and this stub would 404 rather than quietly agreeing.
        server.createContext("/v1/artists/", exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            respond(exchange, artistStatus, artistBody, null);
        });

        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        catalog = new SpotifyCatalog("an-id", "a-secret", base + "/token", base + "/v1/search");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                int status, String body, String retryAfter) throws IOException {
        if (retryAfter != null) {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
        }
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // The happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a search mints a token and returns the playable tracks")
    void aSearchReturnsTracks() {
        List<SpotifyTrack> tracks = catalog.searchTracks("rick astley", 20);

        assertEquals(1, tracks.size(),
                "the local file and the untitled track are not playable and must be dropped");
        SpotifyTrack track = tracks.get(0);
        assertEquals("spotify:track:4uLU6hMCjMI75M1A2tKUQC", track.uri());
        assertEquals("Never Gonna Give You Up", track.title());
        assertEquals("Rick Astley", track.artist());
        assertEquals("Whenever You Need Somebody", track.album());
        assertEquals(1987, track.releaseYear());
        assertEquals(213573, track.duration().toMillis());
        // The details panel is ~250px wide, so the 64px thumbnail arrives visibly blurred and the
        // 640px one is four times more than anything draws. See PREFERRED_COVER_WIDTH.
        assertEquals("https://i.example/300.jpg", track.coverUrl());
        // Carried so the genre can be looked up: a track object has no genre in it, and this is the
        // only thing in it that identifies whose artist page to ask.
        assertEquals("0gxyHStUsqpMadRV0Di1Qt", track.artistId());
        assertNull(catalog.lastProblem());
    }

    // ------------------------------------------------------------------
    // The genre lookup, which is the whole of what the add dialog knows that a track object does not
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an artist's genres are read from v1/artists beside the search endpoint")
    void anArtistsGenresAreRead() {
        List<String> genres = catalog.artistGenres("0gxyHStUsqpMadRV0Di1Qt");

        assertEquals(List.of("new romantic", "dance pop"), genres);
        // The path is derived from the search endpoint rather than written out, so a test pointing this
        // class at a stub gets both redirected for one override. Asserted, because getting it wrong
        // would send a live request from a unit test.
        assertTrue(requested.contains("/v1/artists/0gxyHStUsqpMadRV0Di1Qt"),
                "the artist call did not land beside v1/search: " + requested);
    }

    @Test
    @DisplayName("an artist with no genres recorded is an empty list, not a failure")
    void anArtistWithNoGenres() {
        // Completely ordinary for a small artist, and it must read as "nobody has said" rather than as
        // something having gone wrong - the dialog's genre box simply stays on UNKNOWN.
        artistBody = "{\"id\":\"x\",\"name\":\"Somebody\",\"genres\":[]}";

        assertTrue(catalog.artistGenres("x").isEmpty());
        assertNull(catalog.lastProblem());
    }

    @Test
    @DisplayName("a track with no artist id costs no network call at all")
    void noArtistIdIsNoRequest() {
        assertTrue(catalog.artistGenres(null).isEmpty());
        assertTrue(catalog.artistGenres("  ").isEmpty());
        assertTrue(requested.isEmpty(),
                "a lookup with nothing to look up still went to Spotify: " + requested);
    }

    @Test
    @DisplayName("a refused artist call is an empty list rather than an exception")
    void arefusedArtistCallIsEmpty() {
        artistStatus = 404;
        artistBody = "{\"error\":{\"status\":404,\"message\":\"non existing id\"}}";

        assertTrue(catalog.artistGenres("nope").isEmpty());
    }

    @Test
    @DisplayName("an album with only small artwork keeps the largest it has")
    void asmallOnlyAlbumKeepsItsLargest() {
        searchBody = """
                {"tracks":{"items":[
                  {"uri":"spotify:track:x","name":"Tiny","artists":[{"name":"A"}],
                   "album":{"name":"B","images":[
                     {"url":"https://i.example/64.jpg","width":64,"height":64},
                     {"url":"https://i.example/160.jpg","width":160,"height":160}]}}
                ]}}""";

        assertEquals("https://i.example/160.jpg",
                catalog.searchTracks("anything", 5).get(0).coverUrl());
    }

    @Test
    @DisplayName("an album with no artwork at all is not a parse failure")
    void anAlbumWithNoArtworkStillReadsAsATrack() {
        searchBody = """
                {"tracks":{"items":[
                  {"uri":"spotify:track:x","name":"Bare","artists":[{"name":"A"}],
                   "album":{"name":"B","images":[]}}
                ]}}""";

        List<SpotifyTrack> tracks = catalog.searchTracks("anything", 5);
        assertEquals(1, tracks.size());
        assertNull(tracks.get(0).coverUrl(), "no artwork is ordinary; the view shows a placeholder");
    }

    /**
     * The Client Credentials grant, which is the whole reason this class exists.
     *
     * <p>Spotify wants the pair as HTTP Basic and the grant as a form body; sending the id and
     * secret in the body instead is the shape people reach for first and it is refused.
     */
    @Test
    @DisplayName("the token request is Basic-authenticated with a client_credentials grant")
    void theTokenRequestIsShapedCorrectly() {
        catalog.searchTracks("anything", 5);

        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "an-id:a-secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, lastAuthorization.get());
        assertEquals("grant_type=client_credentials", lastTokenBody.get());
    }

    @Test
    @DisplayName("the query is encoded and the limit is clamped to what Spotify really accepts")
    void theQueryIsEncodedAndTheLimitClamped() {
        catalog.searchTracks("drum & bass", 500);

        String search = requested.stream().filter(path -> path.startsWith("/v1/search"))
                .findFirst().orElseThrow();
        assertTrue(search.contains("q=drum+%26+bass"), search);
        assertTrue(search.contains("limit=10"), search);
        assertTrue(search.contains("type=track"), search);
    }

    /**
     * The limit Spotify actually enforces, which is not the one it documents.
     *
     * <p>The reference says "Minimum: 1. Maximum: 50" for {@code v1/search}. Measured against the
     * live service, anything above <strong>ten</strong> answers
     * {@code 400 "Invalid limit"} — 11, 12, 15, 18, 19, 20, 25, 30, 40 and 50 were all refused and
     * 10 returned ten items.
     *
     * <p>This shipped broken and every diagnostic written to chase it passed, because they all
     * asked for a handful of results while the interface asked for fifty. A caller must not be able
     * to reintroduce it, which is why the clamp is here and not at the call site — this test asserts
     * that a caller asking for the documented maximum still produces a legal request.
     */
    @Test
    @DisplayName("asking for the documented maximum of 50 still sends a request Spotify accepts")
    void theDocumentedMaximumIsNotTheRealOne() {
        assertEquals(10, SpotifyCatalog.MAX_SEARCH_LIMIT,
                "measured against the live API: 11 and above answer 400 Invalid limit");

        catalog.searchTracks("anything", 50);

        String search = requested.stream().filter(path -> path.startsWith("/v1/search"))
                .findFirst().orElseThrow();
        assertFalse(search.contains("limit=50"), "50 is refused outright, not trimmed: " + search);
        assertTrue(search.contains("limit=10"), search);
    }

    /**
     * Token caching, which protects the very quota this class was written to get.
     *
     * <p>A token lasts an hour. Minting one per search would spend the application's request
     * allowance on nothing but tokens, which is precisely the failure being escaped.
     */
    @Test
    @DisplayName("the token is minted once and reused across searches")
    void theTokenIsCached() {
        catalog.searchTracks("one", 5);
        catalog.searchTracks("two", 5);
        catalog.searchTracks("three", 5);

        assertEquals(1, tokensIssued.get(), "three searches must not cost three tokens");
        assertEquals(3, requested.stream().filter(p -> p.startsWith("/v1/search")).count());
    }

    // ------------------------------------------------------------------
    // Failures, which must be explained rather than returned as emptiness
    // ------------------------------------------------------------------

    /**
     * The header the daemon's proxy throws away.
     *
     * <p>Measured against the live service: a 429 through {@code /web-api} carries only
     * {@code Vary}, {@code Date} and {@code Content-Length}, where the identical call made directly
     * carries {@code retry-after: 31}. Reading it is what lets the interface name a number instead
     * of saying "try again later" and hoping.
     */
    @Test
    @DisplayName("a rate limit is reported with the wait Spotify actually asked for")
    void aRateLimitReportsItsRetryAfter() {
        searchStatus = 429;
        searchRetryAfter = "31";
        searchBody = "{\"error\":{\"status\":429,\"message\":\"API rate limit exceeded\"}}";

        assertTrue(catalog.searchTracks("anything", 10).isEmpty());

        assertEquals(429, catalog.lastStatus());
        assertEquals(31, catalog.retryAfterSeconds());
        assertTrue(catalog.lastProblem().contains("31"), catalog.lastProblem());
    }

    @Test
    @DisplayName("a rate limit with no Retry-After still says something usable")
    void aRateLimitWithoutRetryAfterStillExplains() {
        searchStatus = 429;
        searchRetryAfter = null;
        searchBody = "{}";

        catalog.searchTracks("anything", 10);

        assertEquals(0, catalog.retryAfterSeconds());
        assertNotNull(catalog.lastProblem());
        assertFalse(catalog.lastProblem().isBlank());
    }

    /**
     * A mistyped secret, which is the single most likely thing to go wrong here.
     *
     * <p>It must not read as "Spotify found nothing": that sends the user looking for a better
     * search term for a problem that is in a text field on the same page.
     */
    @Test
    @DisplayName("bad credentials are named as bad credentials, not as an empty result")
    void badCredentialsAreExplained() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_client\",\"error_description\":\"Invalid client secret\"}";

        assertTrue(catalog.searchTracks("anything", 10).isEmpty());

        String problem = catalog.lastProblem();
        assertNotNull(problem);
        assertTrue(problem.contains("Invalid client secret"), problem);
        assertEquals(0, requested.stream().filter(p -> p.startsWith("/v1/search")).count(),
                "no point searching without a token");
    }

    /**
     * A token revoked before its stated expiry.
     *
     * <p>The cached one has to be dropped, or every later search repeats a request that cannot now
     * succeed and the feature stays broken until the application is restarted.
     */
    @Test
    @DisplayName("a 401 discards the cached token so the next call mints a fresh one")
    void anUnauthorizedResponseDropsTheCachedToken() {
        catalog.searchTracks("first", 5);
        assertEquals(1, tokensIssued.get());

        searchStatus = 401;
        searchBody = "{\"error\":{\"status\":401,\"message\":\"The access token expired\"}}";
        catalog.searchTracks("second", 5);

        searchStatus = 200;
        searchBody = TRACKS_JSON;
        List<SpotifyTrack> recovered = catalog.searchTracks("third", 5);

        assertEquals(2, tokensIssued.get(), "the dead token must have been replaced");
        assertEquals(1, recovered.size(), "and the search must work again afterwards");
    }

    /**
     * The whitespace a copy and paste brings with it.
     *
     * <p>Reported from a real attempt: correct-looking credentials rejected as
     * {@code invalid_client}. A credential pasted from a web page routinely carries a trailing
     * newline, and it is invisible in every direction - the field looks right, the Base64 blob is
     * well-formed, and Spotify's answer is the same one it gives for a genuinely wrong secret.
     */
    @Test
    @DisplayName("credentials pasted with surrounding whitespace still work")
    void pastedWhitespaceIsStripped() {
        catalog.setCredentials("  an-id\n", "\ta-secret  \n");

        assertTrue(catalog.isConfigured());
        assertEquals(1, catalog.searchTracks("anything", 5).size());

        // The header must carry the trimmed pair, not the one that was typed.
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "an-id:a-secret".getBytes(StandardCharsets.UTF_8)), lastAuthorization.get());
    }

    /**
     * The lengths, which tell apart failures that all answer "Invalid client".
     *
     * <p>Never the values themselves: a secret must not reach a log or a label.
     */
    @Test
    @DisplayName("a refusal reports the lengths sent, and never the credentials")
    void aRefusalReportsLengthsNotSecrets() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_client\"}";
        catalog.setCredentials("0123456789abcdef", "0123456789abcdef");

        catalog.searchTracks("anything", 5);
        String problem = catalog.lastProblem();

        assertNotNull(problem);
        assertTrue(problem.contains("16"), "the observed length is the diagnostic: " + problem);
        assertTrue(problem.contains("identical"),
                "the id pasted into both fields is worth naming outright: " + problem);
        assertFalse(problem.contains("0123456789abcdef"),
                "a credential must never be shown back: " + problem);
    }

    /**
     * The failure that was reported as "it still says the credentials are wrong".
     *
     * <p>Search comes up already configured from stored settings, so the button is pressed against
     * a <em>working</em> configuration. The first version replaced the pair and then cleared it on
     * refusal, which turned a working search off — and the message named the typo rather than the
     * far more alarming thing it had just done. A failed attempt is an attempt that failed, not a
     * setting that was lost.
     */
    @Test
    @DisplayName("a refused attempt keeps the credentials that were already working")
    void aRefusedAttemptDoesNotDestroyWorkingCredentials() {
        // Established and working, as a launch restores from settings.json.
        assertTrue(catalog.applyCredentials("an-id", "a-secret"));
        assertTrue(catalog.isConfigured());

        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_client\"}";
        assertFalse(catalog.applyCredentials("a-typo", "another-typo"));

        // Still on, still the old pair, and the refusal is still readable.
        assertTrue(catalog.isConfigured(), "a typo must not turn searching off");
        assertNotNull(catalog.lastProblem());
        assertTrue(catalog.lastProblem().contains("previous credentials"), catalog.lastProblem());

        tokenStatus = 200;
        tokenBody = "{\"access_token\":\"stub-token\",\"expires_in\":3600}";
        assertEquals(1, catalog.searchTracks("anything", 5).size(),
                "and the configuration that worked before must still work");
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "an-id:a-secret".getBytes(StandardCharsets.UTF_8)), lastAuthorization.get());
    }

    @Test
    @DisplayName("a refused first attempt leaves the catalogue unconfigured, not half-set")
    void aRefusedFirstAttemptLeavesNothingBehind() {
        tokenStatus = 401;
        tokenBody = "{\"error\":\"invalid_client\"}";

        SpotifyCatalog fresh = new SpotifyCatalog(null, null,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/token",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/search");

        assertFalse(fresh.applyCredentials("bad-id", "bad-secret"));
        assertFalse(fresh.isConfigured(), "nothing was working before, so nothing is now");
        assertNotNull(fresh.lastProblem());
        assertFalse(fresh.lastProblem().contains("previous credentials"),
                "there were none to keep: " + fresh.lastProblem());
    }

    @Test
    @DisplayName("an unconfigured catalogue searches nothing and says why")
    void anUnconfiguredCatalogueExplainsItself() {
        SpotifyCatalog empty = new SpotifyCatalog(null, null);

        assertFalse(empty.isConfigured());
        assertTrue(empty.searchTracks("anything", 10).isEmpty());
        assertNotNull(empty.lastProblem());
        assertFalse(empty.verifyCredentials());
    }

    @Test
    @DisplayName("a blank query costs no network call at all")
    void aBlankQueryIsNotSent() {
        assertTrue(catalog.searchTracks("   ", 10).isEmpty());
        assertTrue(catalog.searchTracks(null, 10).isEmpty());
        assertTrue(requested.isEmpty(), "a blank search must not spend a request: " + requested);
    }

    /**
     * Changing the credentials must not leave the old application's token in place.
     *
     * <p>It would go on working until it expired, so a corrected secret would appear to change
     * nothing and a wrong one would appear to work - both of which point the user at the wrong
     * thing.
     */
    @Test
    @DisplayName("replacing the credentials discards the token minted for the old ones")
    void newCredentialsDiscardTheOldToken() {
        catalog.searchTracks("first", 5);
        assertEquals(1, tokensIssued.get());

        catalog.setCredentials("another-id", "another-secret");
        catalog.searchTracks("second", 5);

        assertEquals(2, tokensIssued.get());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                        "another-id:another-secret".getBytes(StandardCharsets.UTF_8)),
                lastAuthorization.get());
    }

    @Test
    @DisplayName("verifying credentials checks them against Spotify rather than merely storing them")
    void verifyingActuallyAsksSpotify() {
        assertTrue(catalog.verifyCredentials());
        assertEquals(1, tokensIssued.get());

        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_client\"}";
        assertFalse(catalog.verifyCredentials(), "a cached token must not mask bad credentials");
    }
}
