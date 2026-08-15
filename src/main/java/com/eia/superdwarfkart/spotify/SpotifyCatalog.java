package com.eia.superdwarfkart.spotify;

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
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catalogue search, against Spotify directly and on this application's own quota.
 *
 * <p><strong>This exists because librespot's quota is not ours and never was.</strong>
 * {@code client_id.go} hardcodes Spotify's own desktop client id,
 * {@code 65b708073fc0480ea92a077233ca87bd}, and every librespot instance in the world authenticates
 * as that one application. Spotify rate-limits per client id, so the daemon's {@code /web-api}
 * proxy draws on a bucket shared with every other user of the project. Measured against a live
 * account: {@code retry-after: 31}, then {@code 33} after forty seconds of complete idleness - a
 * rolling window nobody is touching drains to zero, and this one went <em>up</em>. No amount of
 * waiting fixes that, because the usage being limited is not ours.
 *
 * <p>A registered application of our own gets a private quota, and the Client Credentials flow is
 * the cheap half of it: an application token, no user, no browser, no redirect URI, no refresh.
 * That is enough for {@code v1/search}, which is the whole of "find a track and add it".
 *
 * <p><strong>What this deliberately cannot do is anything user-specific.</strong> An application
 * token identifies the application and not a person, so {@code v1/me/tracks} and
 * {@code v1/me/playlists} answer 401 through it however the request is shaped. Saved tracks and
 * playlists need the Authorization Code flow, which needs a browser and a redirect URI; those stay
 * on the daemon's proxy, where they already worked.
 *
 * <p><strong>Searching does not need the daemon at all</strong> - it is an ordinary HTTPS call to
 * {@code api.spotify.com}. Playing does, through {@code /player/play}, which has been in every
 * release since long before v0.8.0. So the two halves of this feature reach Spotify by entirely
 * separate routes, and only the search half was ever affected by the shared quota.
 *
 * <p>Every call is synchronous and none of them throws, matching {@link SpotifyApi}: a failure
 * returns an empty list and leaves a sentence in {@link #lastProblem()}. No {@code javafx} import.
 */
public final class SpotifyCatalog {

    private static final Logger LOG = Logger.getLogger(SpotifyCatalog.class.getName());

    /** Where an application token is minted. */
    static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";

    /** The catalogue search endpoint. */
    static final String SEARCH_ENDPOINT = "https://api.spotify.com/v1/search";

    /**
     * The most results {@code v1/search} will accept in one page.
     *
     * <p><strong>Ten, measured — and Spotify's own documentation says fifty.</strong> The reference
     * states "Minimum: 1. Maximum: 50" for the search endpoint's {@code limit}, and asking for
     * anything above ten answers {@code 400 {"error":{"status":400,"message":"Invalid limit"}}}.
     * Probed against the live service to find the edge exactly: 10 returns ten items, 11, 12, 15,
     * 18, 19, 20, 25, 30, 40 and 50 all refuse.
     *
     * <p>This is ground rule 6 with the documentation as the thing that could not be trusted. It
     * cost a long session: every diagnostic here was written with a small limit and passed, while
     * the interface asked for fifty and failed, so the class looked correct everywhere it was
     * tested and was broken everywhere it was used. <strong>Clamped here rather than at the call
     * site</strong>, so no caller can reintroduce it.
     */
    public static final int MAX_SEARCH_LIMIT = 10;

    /** How long a call to Spotify is given, network included. */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /**
     * How long before a token's stated expiry it is treated as spent.
     *
     * <p>A token that expires between the check and the call would produce one spurious 401 per
     * hour, which is exactly the kind of intermittent failure nobody can reproduce on demand.
     */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final String tokenEndpoint;
    private final String searchEndpoint;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String clientId;
    private volatile String clientSecret;

    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    private volatile int lastStatus;
    private volatile String lastDetail;
    private volatile int retryAfterSeconds;

    /**
     * Creates a catalogue client against the real Spotify endpoints.
     *
     * @param clientId     the registered application's id, or {@code null} when unconfigured
     * @param clientSecret the registered application's secret, or {@code null}
     */
    public SpotifyCatalog(String clientId, String clientSecret) {
        this(clientId, clientSecret, TOKEN_ENDPOINT, SEARCH_ENDPOINT);
    }

    /**
     * Creates a catalogue client against explicit endpoints, for tests against a stub server.
     *
     * @param clientId       the application id, or {@code null}
     * @param clientSecret   the application secret, or {@code null}
     * @param tokenEndpoint  where to mint tokens
     * @param searchEndpoint where to search
     */
    public SpotifyCatalog(String clientId, String clientSecret,
                          String tokenEndpoint, String searchEndpoint) {
        this.clientId = blankToNull(clientId);
        this.clientSecret = blankToNull(clientSecret);
        this.tokenEndpoint = tokenEndpoint;
        this.searchEndpoint = searchEndpoint;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ------------------------------------------------------------------
    // Credentials
    // ------------------------------------------------------------------

    /**
     * Replaces the credentials and discards any cached token.
     *
     * <p>The token is dropped rather than kept: it was minted for the old application, and a token
     * that outlived the credentials it came from would keep working until it expired and then fail
     * in a way that pointed at the wrong thing.
     *
     * @param clientId     the application id, or {@code null} to unconfigure
     * @param clientSecret the application secret, or {@code null}
     */
    public synchronized void setCredentials(String clientId, String clientSecret) {
        this.clientId = blankToNull(clientId);
        this.clientSecret = blankToNull(clientSecret);
        this.token = null;
        this.tokenExpiry = Instant.EPOCH;
        this.lastStatus = 0;
        this.lastDetail = null;
        this.retryAfterSeconds = 0;
    }

    /**
     * Tries a new credential pair, keeping the old one if it does not work.
     *
     * <p><strong>A failed attempt must not destroy a working configuration.</strong> The first
     * version of this replaced the credentials and then cleared them on failure, so pressing "save"
     * with a typo in the field turned off a search that had been working a second earlier - and the
     * message it showed named the typo rather than the far more alarming thing it had just done.
     * Restoring what worked is the whole difference between an attempt that failed and a setting
     * that was lost.
     *
     * @param clientId     the application id to try
     * @param clientSecret the application secret to try
     * @return whether the new pair works and is now in force; on {@code false} the previous pair is
     *         back and {@link #lastProblem()} explains the refusal
     */
    public boolean applyCredentials(String clientId, String clientSecret) {
        String previousId;
        String previousSecret;
        synchronized (this) {
            previousId = this.clientId;
            previousSecret = this.clientSecret;
        }

        setCredentials(clientId, clientSecret);
        if (verifyCredentials()) {
            return true;
        }

        // Captured before the rollback, because setCredentials clears the reporting fields - and
        // the refusal is the one thing the user actually needs to read.
        String refusal = lastProblem();
        int refusedStatus = lastStatus;
        setCredentials(previousId, previousSecret);
        lastStatus = refusedStatus;
        lastDetail = previousId == null ? refusal
                : refusal + " The previous credentials are still in use.";
        return false;
    }

    /** @return whether both halves of a credential pair are present */
    public boolean isConfigured() {
        return clientId != null && clientSecret != null;
    }

    /** @return the configured application id, or {@code null} */
    public String clientId() {
        return clientId;
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /**
     * Explains the last failure in a sentence, or {@code null} when the last call went fine.
     *
     * <p>Every failure here reaches the interface as an empty list otherwise, and "nothing matched"
     * is a very different thing from "your client secret is wrong". A search box that answers
     * nothing however it is used reads as broken software.
     *
     * @return what to show the user, or {@code null}
     */
    public String lastProblem() {
        if (lastDetail != null) {
            return lastDetail;
        }
        return switch (lastStatus) {
            case 0, 200 -> null;
            // Ours this time, unlike the daemon's shared bucket - and unlike that one, Spotify
            // sends Retry-After here, so the wait can be stated rather than guessed at.
            case 429 -> retryAfterSeconds > 0
                    ? "Spotify is rate-limiting: try again in " + retryAfterSeconds + " seconds."
                    : "Spotify is rate-limiting this application. Try again shortly.";
            case 400, 401 -> "Spotify rejected the client id or secret. Check both and save again.";
            case 403 -> "Spotify refused the request for this application.";
            case -1 -> "Could not reach Spotify. Check the network.";
            default -> "Spotify answered HTTP " + lastStatus + ".";
        };
    }

    /** @return the HTTP status of the last call, {@code -1} for unreachable, {@code 0} for none */
    public int lastStatus() {
        return lastStatus;
    }

    /**
     * @return how many seconds Spotify asked us to wait, or {@code 0} when it did not say - read
     *         from the {@code Retry-After} header, which the daemon's proxy strips and a direct
     *         call keeps
     */
    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    /**
     * Searches Spotify's catalogue for tracks.
     *
     * @param query what the user typed; blank returns nothing
     * @param limit how many results to ask for, clamped to {@link #MAX_SEARCH_LIMIT} - asking for
     *              more is refused outright by Spotify rather than trimmed
     * @return the tracks found, or an empty list - never {@code null}, and never an exception
     */
    public List<SpotifyTrack> searchTracks(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (!isConfigured()) {
            lastStatus = 0;
            lastDetail = "No Spotify application is configured, so catalogue search is off.";
            return List.of();
        }

        String bearer = accessToken();
        if (bearer == null) {
            return List.of();
        }

        String url = searchEndpoint
                + "?type=track&limit=" + Math.clamp(limit, 1, MAX_SEARCH_LIMIT)
                + "&q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

        JsonNode response = get(url, bearer);
        if (response == null) {
            return List.of();
        }
        return SpotifyTrack.listFrom(response.path("tracks").path("items"));
    }

    /**
     * Checks that the configured credentials actually mint a token.
     *
     * <p>Worth its own method because the alternative is discovering a typo in the secret as an
     * empty search result, which looks like Spotify having nothing rather than like a setting being
     * wrong.
     *
     * @return whether a token was issued; {@link #lastProblem()} says why not
     */
    public boolean verifyCredentials() {
        if (!isConfigured()) {
            lastStatus = 0;
            lastDetail = "Enter both a client id and a client secret.";
            return false;
        }
        synchronized (this) {
            token = null;
            tokenExpiry = Instant.EPOCH;
        }
        return accessToken() != null;
    }

    // ------------------------------------------------------------------
    // Tokens
    // ------------------------------------------------------------------

    /**
     * Returns a usable application token, minting one if the cached one is spent.
     *
     * <p>Cached, because a token lasts an hour and minting one per keystroke would spend the very
     * quota this class exists to protect.
     *
     * @return the bearer token, or {@code null} when one could not be obtained
     */
    private synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        token = null;

        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=client_credentials", StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatus = response.statusCode();
            lastDetail = null;
            readRetryAfter(response);

            if (lastStatus != 200) {
                // Spotify explains a bad credential in the body; that is far more useful than the
                // status alone, which is 400 for a wrong secret and a wrong grant type alike.
                String explained = describeTokenFailure(response.body());
                lastDetail = explained == null ? credentialHint() : explained + " " + credentialHint();
                LOG.warning("Spotify refused the client credentials: HTTP " + lastStatus);
                return null;
            }

            JsonNode node = mapper.readTree(response.body());
            String issued = node.path("access_token").asText("");
            if (issued.isBlank()) {
                lastDetail = "Spotify issued no access token.";
                return null;
            }
            long expiresIn = Math.max(0, node.path("expires_in").asLong(3600));
            token = issued;
            tokenExpiry = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
            return token;

        } catch (IOException e) {
            lastStatus = -1;
            lastDetail = null;
            LOG.log(Level.FINE, "Could not reach Spotify for a token", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastStatus = -1;
            lastDetail = null;
            return null;
        }
    }

    /**
     * Describes what was actually sent, when Spotify refuses it.
     *
     * <p>"Invalid client" is true and useless: it is the same answer for a mistyped secret, for the
     * client id pasted into both fields, and for a secret that arrived with half of itself missing.
     * The lengths tell those apart at a glance, and they can be reported without ever showing the
     * values - <strong>the credentials themselves are never logged or displayed</strong>, only how
     * long they are. Spotify issues both as 32-character hex strings, so anything else is the fault
     * on its own.
     *
     * @return a sentence naming the observed lengths
     */
    private String credentialHint() {
        int idLength = clientId == null ? 0 : clientId.length();
        int secretLength = clientSecret == null ? 0 : clientSecret.length();
        String hint = "Sent an id of " + idLength + " characters and a secret of "
                + secretLength + ". Spotify issues both as 32.";
        if (idLength == secretLength && idLength > 0 && clientId.equals(clientSecret)) {
            return hint + " These two are identical, so the client id has been pasted into both.";
        }
        return hint;
    }

    /**
     * Turns a token-endpoint error body into something worth showing.
     *
     * @param body the response body, possibly empty
     * @return a sentence, or {@code null} to fall back to the status code
     */
    private String describeTokenFailure(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(body);
            String description = node.path("error_description").asText("");
            if (description.isBlank()) {
                description = node.path("error").asText("");
            }
            return description.isBlank() ? null : "Spotify: " + description;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    /**
     * Sends an authorised GET and parses the body.
     *
     * @param url    the full address, query included
     * @param bearer the access token
     * @return the parsed body, or {@code null} on any failure
     */
    private JsonNode get(String url, String bearer) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatus = response.statusCode();
            lastDetail = null;
            readRetryAfter(response);

            if (lastStatus == 401) {
                // The cached token has been invalidated early - dropped so the next call mints a
                // fresh one rather than repeating a request that cannot now succeed.
                synchronized (this) {
                    token = null;
                    tokenExpiry = Instant.EPOCH;
                }
            }
            if (lastStatus != 200) {
                // Named apart from the same status arriving from the token endpoint. The two mean
                // completely different things - one is "your credentials are wrong", the other is
                // "your credentials were accepted and this particular call was refused" - and they
                // shared a message once, which sent a whole afternoon into re-checking credentials
                // that had already been proven good.
                if (lastStatus == 400 || lastStatus == 401 || lastStatus == 403) {
                    lastDetail = "The credentials were accepted but Spotify refused the search "
                            + "itself (HTTP " + lastStatus + ").";
                }
                LOG.warning("Spotify answered HTTP " + lastStatus + " for a catalogue call to "
                        + URI.create(url).getPath());
                return null;
            }
            String body = response.body();
            return body == null || body.isBlank() ? null : mapper.readTree(body);

        } catch (IOException e) {
            lastStatus = -1;
            LOG.log(Level.FINE, "A Spotify catalogue call failed", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastStatus = -1;
            return null;
        }
    }

    /**
     * Records {@code Retry-After} when Spotify sends one.
     *
     * <p>This is the header the daemon's proxy drops - measured, a proxied 429 carries only
     * {@code Vary}, {@code Date} and {@code Content-Length}, where the same call made directly
     * carries {@code retry-after: 31}. Reading it here is what lets the interface say how long to
     * wait instead of "try again later".
     *
     * @param response the response to read
     */
    private void readRetryAfter(HttpResponse<?> response) {
        Optional<String> header = response.headers().firstValue("retry-after");
        if (header.isEmpty()) {
            retryAfterSeconds = 0;
            return;
        }
        try {
            retryAfterSeconds = Math.max(0, Integer.parseInt(header.get().trim()));
        } catch (NumberFormatException e) {
            // The header may also carry an HTTP date. Rare from Spotify, and a wrong number here
            // would be worse than none, so it simply goes unreported.
            retryAfterSeconds = 0;
        }
    }

    /**
     * Normalises a credential as typed by a person.
     *
     * <p><strong>Trimmed, and that is not tidiness.</strong> These arrive by copy and paste from a
     * web page, which routinely carries a trailing newline or a leading space, and the pair is sent
     * as {@code id:secret} inside a Base64 blob where whitespace is invisible in every direction:
     * the field looks right, the request looks well-formed, and Spotify answers
     * {@code invalid_client} exactly as it would for a genuinely wrong secret. There is nothing
     * anywhere to suggest the difference, so the only place it can be fixed is before it is stored.
     *
     * @param value the value as typed
     * @return the value with surrounding whitespace removed, or {@code null} when there is nothing
     *         left
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
