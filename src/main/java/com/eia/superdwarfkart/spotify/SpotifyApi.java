package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.app.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The daemon's REST API, and through it the Spotify Web API.
 *
 * <p>Two quite different things reached over one socket, and the second is the reason the search
 * in this application needed no OAuth client of its own:
 *
 * <ul>
 *   <li><strong>{@code /player/*} and {@code /status}</strong> drive the daemon - play this URI,
 *       pause, seek, what is loaded.</li>
 *   <li><strong>{@code /web-api/...} is proxied verbatim to {@code api.spotify.com} with the
 *       session's bearer token attached.</strong> So {@code v1/search} and {@code v1/me/tracks}
 *       are available the moment the user is logged in, with no client id, no redirect URI and no
 *       secret anywhere in this repository. The daemon already holds a Spotify session; asking the
 *       user to authorise a second application against the same account would be asking twice for
 *       something already granted.</li>
 * </ul>
 *
 * <p>Every call is synchronous and every one of them can fail - the daemon may not be running, the
 * session may have lapsed, the network may be down. <strong>None of them throws.</strong> Playback
 * commands return a boolean and queries return {@code null} or an empty list, because every caller
 * here is either an interface handler or the audio thread and neither has anything useful to do
 * with an exception. What went wrong is logged once.
 *
 * <p>No {@code javafx} import: this is called from background threads and from the audio path.
 */
public final class SpotifyApi {

    private static final Logger LOG = Logger.getLogger(SpotifyApi.class.getName());

    /** How long a player command is given. Short: these are local and must not stall a keypress. */
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    /** How long a Web API call is given, which goes out to the internet and back. */
    private static final Duration WEB_API_TIMEOUT = Duration.ofSeconds(15);

    /** How long the readiness probe is given. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Creates a client against the daemon's configured loopback address. */
    public SpotifyApi() {
        this("http://" + AppConfig.SPOTIFY_API_HOST + ":" + AppConfig.SPOTIFY_API_PORT);
    }

    /**
     * Creates a client against an explicit base address, for tests against a stub server.
     *
     * @param baseUrl the daemon's address, with no trailing slash; must not be {@code null}
     */
    public SpotifyApi(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    // ------------------------------------------------------------------
    // Reachability
    // ------------------------------------------------------------------

    /**
     * Asks whether the daemon is up and finished starting.
     *
     * <p>{@code GET /} answers {@code playback_ready} once the daemon has bootstrapped, which is
     * the earliest moment {@code /player/play} will be accepted. Polling this is how the session
     * waits for a daemon it has just launched, rather than sleeping for a guessed interval.
     *
     * @return whether the daemon is ready to accept playback commands
     */
    public boolean isReady() {
        JsonNode root = getJson("/", PROBE_TIMEOUT);
        return root != null && root.path("playback_ready").asBoolean(false);
    }

    /** @return whether the daemon answers at all, ready or not */
    public boolean isReachable() {
        return getJson("/", PROBE_TIMEOUT) != null;
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    /**
     * Reads the player status.
     *
     * @return the status document, or {@code null} when there is no active session or the daemon
     *         is not answering - the API returns 204 for the former
     */
    public JsonNode status() {
        return getJson("/status", COMMAND_TIMEOUT);
    }

    /**
     * @return the username of the logged-in account, or {@code null} when nobody is logged in
     */
    public String username() {
        JsonNode status = status();
        if (status == null) {
            return null;
        }
        String username = status.path("username").asText("");
        return username.isBlank() ? null : username;
    }

    /**
     * Fetches a Spotify access token for the active session.
     *
     * <p>Not needed for {@link #webApiGet} - the proxy attaches its own - but it is what tells the
     * interface that the session is genuinely authorised rather than merely running.
     *
     * @return the access token, or {@code null} when there is no active session
     */
    public String token() {
        JsonNode node = postJson("/token", null, COMMAND_TIMEOUT);
        if (node == null) {
            return null;
        }
        String token = node.path("token").asText("");
        return token.isBlank() ? null : token;
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    /**
     * Starts playing one track.
     *
     * <p><strong>A track URI, never a context.</strong> Handing the daemon an album or playlist URI
     * would start a whole Spotify context playing and the running order would be Spotify's rather
     * than the active mode's - so {@code Song} refuses to hold anything but a track URI in the
     * first place, and this method is only ever reached through that.
     *
     * @param trackUri   the track to play, {@code spotify:track:...}
     * @param positionMs where in the track to start, in milliseconds
     * @param paused     whether to load it without starting
     * @return whether the daemon accepted the command
     */
    public boolean play(String trackUri, long positionMs, boolean paused) {
        String body = "{\"uri\":" + quote(trackUri)
                + ",\"position\":" + Math.max(0, positionMs)
                + ",\"paused\":" + paused + "}";
        return post("/player/play", body);
    }

    /** @return whether the daemon accepted the command */
    public boolean resume() {
        return post("/player/resume", null);
    }

    /** @return whether the daemon accepted the command */
    public boolean pause() {
        return post("/player/pause", null);
    }

    /** @return whether the daemon accepted the command */
    public boolean stop() {
        return post("/player/stop", null);
    }

    /**
     * Jumps within the current track.
     *
     * @param positionMs absolute position in milliseconds
     * @return whether the daemon accepted the command
     */
    public boolean seek(long positionMs) {
        return post("/player/seek",
                "{\"position\":" + Math.max(0, positionMs) + ",\"relative\":false}");
    }

    // ------------------------------------------------------------------
    // The Spotify Web API, proxied
    // ------------------------------------------------------------------

    /**
     * Calls the Spotify Web API through the daemon's proxy.
     *
     * @param path  the Web API path and query, for example {@code v1/me/tracks?limit=50}
     * @return the parsed response, or {@code null} if the call failed
     */
    public JsonNode webApiGet(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        String full = "/web-api/" + trimmed;
        lastWebApiStatus = statusOf(full);
        if (lastWebApiStatus != 200) {
            LOG.fine("Spotify Web API answered HTTP " + lastWebApiStatus + " for " + trimmed);
            return null;
        }
        return getJson(full, WEB_API_TIMEOUT);
    }

    /**
     * The status code the last Web API call came back with.
     *
     * <p>Kept because every one of these arrives at the interface as an empty list otherwise, and
     * "Spotify matched nothing" is a very different thing from "Spotify is rate-limiting you" or
     * "your session has lapsed". A search box that answers nothing however it is used reads as
     * broken software; one that says why does not.
     *
     * @return the HTTP status, {@code -1} when the daemon could not be reached, or {@code 0} before
     *         any call has been made
     */
    public int lastWebApiStatus() {
        return lastWebApiStatus;
    }

    /**
     * Explains the last Web API status in a sentence, or {@code null} when it went fine.
     *
     * @return what to show the user, or {@code null}
     */
    public String lastWebApiProblem() {
        return switch (lastWebApiStatus) {
            case 0, 200 -> null;
            // Measured against a live account: Spotify returns this with an empty body and no
            // Retry-After, and the same token sent straight to api.spotify.com returns it too - so
            // it is the account being limited, not anything the daemon or this application did.
            case 429 -> "Spotify is rate-limiting this account. Wait a minute and try again.";
            case 401, 403 -> "Spotify refused the session. Disconnect and connect again.";
            case 404 -> "This go-librespot build has no Web API proxy.";
            case -1 -> "go-librespot is not answering.";
            default -> "Spotify answered HTTP " + lastWebApiStatus + ".";
        };
    }

    private volatile int lastWebApiStatus;

    /**
     * Whether this daemon build actually has the Web API proxy.
     *
     * <p><strong>It is not in any released version.</strong> {@code /token} and
     * {@code /web-api/{path}} exist on the project's {@code master} branch and appear in <em>no</em>
     * tagged release up to and including v0.8.0 - checked against v0.8.0's own {@code api-spec.yml},
     * which contains neither string. Since v0.8.0 is what Homebrew installs and what the GitHub
     * releases carry, <strong>search does not work on a stock install today</strong>: playback does,
     * because every {@code /player/*} endpoint has been there for releases.
     *
     * <p>Probed rather than inferred from a version number, because the moment a release does carry
     * it this starts working with no change here - and a hardcoded "not before 0.9" would then be
     * wrong in the other direction.
     *
     * <p>Without this the failure is silent and looks like a bug in this application: every search
     * returns an empty list, because a 404 from the daemon and "Spotify found nothing" are the same
     * {@code null} to every caller above.
     *
     * <p><strong>Only a 404 means the endpoint is absent</strong>, and getting that wrong disables a
     * feature that works. Measured against a real master build with a live account: the proxy
     * answered <em>429</em>, because Spotify was rate-limiting - and an earlier version of this
     * method, which required a 200, therefore reported "no Web API proxy" on a daemon that had one
     * and was behaving correctly. Every status except 404 says the route is registered; whether
     * Spotify then liked the call is a different question, answered by
     * {@link #lastWebApiProblem()}.
     *
     * @return whether a Web API call was answered, so search can be offered
     */
    public boolean hasWebApiProxy() {
        // v1/me is the cheapest authenticated call there is, and every session can make it.
        int status = statusOf("/web-api/v1/me");
        lastWebApiStatus = status;
        return status > 0 && status != 404;
    }

    /**
     * Sends a bare GET and reports only the status code.
     *
     * @param path the path to call
     * @return the HTTP status, or {@code -1} when the daemon could not be reached
     */
    private int statusOf(String path) {
        try {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .timeout(WEB_API_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /**
     * Searches Spotify's catalogue for tracks.
     *
     * @param query what the user typed; blank returns nothing
     * @param limit how many results to ask for, 1-50
     * @return the tracks found, newest API shape, or an empty list
     */
    public List<SpotifyTrack> searchTracks(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        JsonNode response = webApiGet(
                "v1/search?type=track&limit=" + Math.clamp(limit, 1, 50) + "&q=" + encoded);
        if (response == null) {
            return List.of();
        }
        return readTracks(response.path("tracks").path("items"));
    }

    /**
     * Reads the signed-in user's saved tracks - the "Liked Songs" library.
     *
     * @param limit  how many to ask for, 1-50
     * @param offset where to start, for paging
     * @return the saved tracks, or an empty list
     */
    public List<SpotifyTrack> savedTracks(int limit, int offset) {
        JsonNode response = webApiGet("v1/me/tracks?limit=" + Math.clamp(limit, 1, 50)
                + "&offset=" + Math.max(0, offset));
        if (response == null) {
            return List.of();
        }
        // A saved-tracks page wraps each track in a { added_at, track } envelope.
        List<SpotifyTrack> tracks = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            SpotifyTrack track = readTrack(item.path("track"));
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    /**
     * Reads the tracks of one playlist.
     *
     * @param playlistId the playlist identifier
     * @param limit      how many to ask for, 1-50
     * @param offset     where to start, for paging
     * @return the tracks, or an empty list
     */
    public List<SpotifyTrack> playlistTracks(String playlistId, int limit, int offset) {
        JsonNode response = webApiGet("v1/playlists/" + playlistId + "/tracks?limit="
                + Math.clamp(limit, 1, 50) + "&offset=" + Math.max(0, offset));
        if (response == null) {
            return List.of();
        }
        List<SpotifyTrack> tracks = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            SpotifyTrack track = readTrack(item.path("track"));
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    /**
     * Reads the signed-in user's playlists.
     *
     * @param limit how many to ask for, 1-50
     * @return each playlist's identifier and name, in order
     */
    public List<Playlist> playlists(int limit) {
        JsonNode response = webApiGet("v1/me/playlists?limit=" + Math.clamp(limit, 1, 50));
        if (response == null) {
            return List.of();
        }
        List<Playlist> found = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            String id = item.path("id").asText("");
            String name = item.path("name").asText("");
            if (!id.isBlank() && !name.isBlank()) {
                found.add(new Playlist(id, name, item.path("tracks").path("total").asInt(0)));
            }
        }
        return found;
    }

    /**
     * One of the user's playlists.
     *
     * @param id    the playlist identifier
     * @param name  its name
     * @param total how many tracks it holds
     */
    public record Playlist(String id, String name, int total) {
        @Override
        public String toString() {
            return name;
        }
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Reads the tracks out of a page whose items are bare track objects.
     *
     * <p>Delegates to {@link SpotifyTrack#listFrom}: a track object is shaped identically however it
     * was fetched, so the proxy and the direct catalogue client share one parser rather than each
     * keeping a copy free to drift.
     *
     * @param items the array of track objects
     * @return the tracks
     */
    private static List<SpotifyTrack> readTracks(JsonNode items) {
        return SpotifyTrack.listFrom(items);
    }

    /**
     * Reads one track object.
     *
     * @param node the track object
     * @return the track, or {@code null} when it is not a usable Spotify track
     */
    private static SpotifyTrack readTrack(JsonNode node) {
        return SpotifyTrack.fromJson(node);
    }

    // ------------------------------------------------------------------
    // Transport plumbing
    // ------------------------------------------------------------------

    private JsonNode getJson(String path, Duration timeout) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), timeout);
    }

    private JsonNode postJson(String path, String body, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return send(builder, timeout);
    }

    private boolean post(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = client.send(
                    builder.timeout(COMMAND_TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                LOG.warning("go-librespot refused " + path + ": HTTP " + response.statusCode()
                        + " " + response.body());
                return false;
            }
            return true;
        } catch (IOException e) {
            LOG.log(Level.FINE, "go-librespot is not answering " + path, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Sends a request and parses the body as JSON.
     *
     * @param builder the request, without its timeout set
     * @param timeout how long to allow
     * @return the parsed body, or {@code null} for a failure, a 204, or an unparseable body
     */
    private JsonNode send(HttpRequest.Builder builder, Duration timeout) {
        try {
            HttpResponse<String> response = client.send(
                    builder.timeout(timeout).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                // No active session. An ordinary state, not a failure.
                return null;
            }
            if (response.statusCode() >= 300) {
                LOG.fine("go-librespot answered HTTP " + response.statusCode()
                        + " for " + builder.build().uri().getPath());
                return null;
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }
            return mapper.readTree(body);
        } catch (IOException e) {
            LOG.log(Level.FINE, "go-librespot request failed", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * @param value a string to embed in a hand-built JSON body
     * @return the value as a quoted JSON string
     */
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
