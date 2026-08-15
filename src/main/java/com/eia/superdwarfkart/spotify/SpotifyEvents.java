package com.eia.superdwarfkart.spotify;

import com.eia.superdwarfkart.app.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The daemon's event stream, which is how this application learns that a track ended.
 *
 * <p><strong>It cannot be deduced from the pipe.</strong> When a track finishes, the daemon's pipe
 * output does not close the FIFO - it moves to a paused state and simply stops writing - so a
 * reader sees no end of file and no error, just a stream that has gone quiet. A silent passage
 * inside a track looks exactly the same. Without this socket the running order would stop dead
 * after the first Spotify song, which is precisely the failure {@code PlaybackEngine} was already
 * built to avoid for local files.
 *
 * <p>Events arrive as {@code {"type": "...", "data": {...}}}. The ones that matter here:
 * {@code playing}, {@code paused}, {@code stopped}, {@code not_playing} and {@code metadata} - the
 * last carrying the track's real duration, which the Web API's own figure is checked against.
 *
 * <p>The socket reconnects on its own while it is meant to be up. A daemon that is restarted, or a
 * connection dropped while the machine slept, must not leave the application permanently unable to
 * advance - and the failure would be invisible, because everything else would go on working.
 */
public final class SpotifyEvents implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SpotifyEvents.class.getName());

    /** How long to wait before dialling again after the socket drops. */
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(2);

    /**
     * One event from the daemon.
     *
     * @param type the event name, for example {@code playing} or {@code stopped}
     * @param data its payload, never {@code null} - a missing node for events that carry none
     */
    public record Event(String type, JsonNode data) {

        /** @return the track URI this event refers to, or {@code null} */
        public String uri() {
            String uri = data.path("uri").asText("");
            return uri.isBlank() ? null : uri;
        }

        /** @return the position carried by a {@code seek} or {@code metadata} event, in millis */
        public long positionMillis() {
            return data.path("position").asLong(0);
        }

        /** @return the duration carried by a {@code metadata} event, in millis, or zero */
        public long durationMillis() {
            return data.path("duration").asLong(0);
        }
    }

    private final URI endpoint;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final StringBuilder partial = new StringBuilder();

    private final ScheduledExecutorService reconnects = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sdmk-spotify-events");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private volatile HttpClient client;

    /** Creates an event stream against the daemon's configured loopback address. */
    public SpotifyEvents() {
        this("ws://" + AppConfig.SPOTIFY_API_HOST + ":" + AppConfig.SPOTIFY_API_PORT + "/events");
    }

    /**
     * Creates an event stream against an explicit address, for tests against a stub server.
     *
     * @param url the websocket address; must not be {@code null}
     */
    public SpotifyEvents(String url) {
        this.endpoint = URI.create(Objects.requireNonNull(url, "url must not be null"));
    }

    /**
     * Registers a listener.
     *
     * <p><strong>Called on the websocket's own thread</strong>, not the interface thread. A
     * listener that touches the scene graph must marshal for itself, exactly as a
     * {@code PcmListener} must.
     *
     * @param listener told about every event; must not be {@code null}
     */
    public void addListener(Consumer<Event> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /** @param listener the listener to remove */
    public void removeListener(Consumer<Event> listener) {
        listeners.remove(listener);
    }

    /** @return whether the stream is meant to be connected */
    public boolean isRunning() {
        return running;
    }

    /** @return whether a socket is open right now */
    public boolean isConnected() {
        WebSocket open = socket.get();
        return open != null && !open.isInputClosed();
    }

    /** Opens the stream, and keeps it open until {@link #close()}. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        client = HttpClient.newHttpClient();
        connect();
    }

    private void connect() {
        if (!running) {
            return;
        }
        HttpClient open = client;
        if (open == null) {
            return;
        }
        open.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(endpoint, new Handler())
                .whenComplete((connected, failure) -> {
                    if (failure != null) {
                        LOG.log(Level.FINE, "Could not open the go-librespot event socket", failure);
                        scheduleReconnect();
                        return;
                    }
                    socket.set(connected);
                    LOG.fine("Connected to the go-librespot event socket");
                });
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        try {
            reconnects.schedule(this::connect, RECONNECT_DELAY.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Shutting down between the drop and the retry. Nothing to do.
            LOG.finer("Reconnect abandoned: the event stream is closing");
        }
    }

    private void deliver(String message) {
        Event event;
        try {
            JsonNode node = mapper.readTree(message);
            String type = node.path("type").asText("");
            if (type.isBlank()) {
                return;
            }
            event = new Event(type, node.path("data"));
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            LOG.fine("Ignoring an unparseable event from go-librespot: " + e.getOriginalMessage());
            return;
        }
        for (Consumer<Event> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // One misbehaving listener must not take the event stream down with it.
                LOG.log(Level.WARNING, "A Spotify event listener failed and was skipped", e);
            }
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        WebSocket open = socket.getAndSet(null);
        if (open != null) {
            open.abort();
        }
        reconnects.shutdownNow();
        HttpClient http = client;
        client = null;
        if (http != null) {
            http.close();
        }
    }

    /**
     * The websocket callbacks.
     *
     * <p>Two details are load-bearing and both are easy to get wrong. A text message may arrive in
     * several fragments, so it is accumulated until {@code last} - a JSON parse of a fragment fails
     * and the event is lost. And overriding {@code onText} means the demand this class asks for is
     * its own responsibility: without {@code request(1)} the socket delivers exactly one message
     * and then goes silent forever, which looks precisely like a daemon that stopped sending.
     */
    private final class Handler implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            partial.setLength(0);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                deliver(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOG.fine("The go-librespot event socket closed: " + statusCode + " " + reason);
            socket.compareAndSet(webSocket, null);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOG.log(Level.FINE, "The go-librespot event socket failed", error);
            socket.compareAndSet(webSocket, null);
            scheduleReconnect();
        }
    }
}
