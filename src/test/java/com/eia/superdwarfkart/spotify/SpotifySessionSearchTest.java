package com.eia.superdwarfkart.spotify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the two routes a search takes.
 *
 * <p>Nothing here starts a daemon or touches the network: what is under test is the decision, which
 * is the part that decides whether a released go-librespot is enough.
 */
class SpotifySessionSearchTest {

    /** Runs the marshalled callback inline, so state changes are observable without a toolkit. */
    private SpotifySession session() {
        return new SpotifySession(Runnable::run);
    }

    /**
     * The point of the whole exercise.
     *
     * <p>Search used to require the daemon's {@code /web-api} proxy, which is on {@code master} and
     * in no tagged release - verified against v0.8.0's own {@code api-spec.yml}, which contains
     * neither {@code web-api} nor {@code token}. Configuring an application of our own turns
     * searching on with that proxy absent, which is what lets a stock Homebrew install do the job.
     */
    @Test
    @DisplayName("credentials turn searching on without the daemon's master-only proxy")
    void credentialsEnableSearchWithoutTheProxy() {
        SpotifySession session = session();

        // No daemon has been connected, so the proxy has certainly not been found.
        assertFalse(session.isUserLibraryAvailable());
        assertFalse(session.isSearchAvailable(), "nothing should be searchable yet");

        session.setCatalogCredentials("an-id", "a-secret");

        assertTrue(session.isSearchAvailable());
        assertTrue(session.isCatalogSearch());
        assertFalse(session.isUserLibraryAvailable(),
                "saved tracks and playlists still need a user behind the token");
    }

    @Test
    @DisplayName("clearing the credentials turns catalogue search back off")
    void clearingCredentialsTurnsItOff() {
        SpotifySession session = session();
        session.setCatalogCredentials("an-id", "a-secret");
        assertTrue(session.isCatalogSearch());

        session.setCatalogCredentials(null, null);

        assertFalse(session.isCatalogSearch());
        assertFalse(session.isSearchAvailable());
    }

    /**
     * Half a credential pair is not a usable state.
     *
     * <p>Accepting it would turn catalogue search on, take the route away from the proxy that might
     * have worked, and then fail every search with an authorisation error.
     */
    @Test
    @DisplayName("half a credential pair does not count as configured")
    void halfAPairIsNotConfigured() {
        SpotifySession session = session();

        session.setCatalogCredentials("an-id", null);
        assertFalse(session.isCatalogSearch());

        session.setCatalogCredentials(null, "a-secret");
        assertFalse(session.isCatalogSearch());

        session.setCatalogCredentials("an-id", "   ");
        assertFalse(session.isCatalogSearch(), "a blank secret is not a secret");
    }

    /**
     * A state change the session's own state machine never sees.
     *
     * <p>Configuring credentials turns searching on while the session sits in exactly the same
     * state, so a view listening only for state transitions would not redraw and the search box
     * would not appear until something else happened to move the session.
     */
    @Test
    @DisplayName("configuring credentials notifies the interface even though the state is unchanged")
    void configuringNotifiesTheInterface() {
        SpotifySession session = session();
        SpotifySession.State before = session.state();

        boolean[] told = {false};
        session.setOnChanged(() -> told[0] = true);
        session.setCatalogCredentials("an-id", "a-secret");

        assertTrue(told[0], "the search panel appears on this change and nothing else announces it");
        assertTrue(session.state() == before, "and it is not a state change");
    }

    /**
     * Two listeners, both of which have to survive.
     *
     * <p>The Spotify page registers in its own constructor and playback registers afterwards, to
     * pick up a streamed song that was waiting for the daemon. With a single slot the second
     * silently displaced the first, and the symptom would have been a page that stopped redrawing -
     * which looks like a frozen interface, not like a lost listener.
     */
    @Test
    @DisplayName("a second listener does not displace the first")
    void listenersAccumulate() {
        SpotifySession session = session();
        boolean[] page = {false};
        boolean[] playback = {false};

        session.setOnChanged(() -> page[0] = true);
        session.addOnChanged(() -> playback[0] = true);
        session.setCatalogCredentials("an-id", "a-secret");

        assertTrue(page[0], "the page must still redraw");
        assertTrue(playback[0], "and playback must still hear about it");
    }

    /**
     * A listener that throws must not take the others down with it.
     *
     * <p>Redrawing the page and resuming a waiting song are independent; losing the second because
     * the first failed would present as a song that simply refused to start.
     */
    @Test
    @DisplayName("one listener throwing does not stop the rest")
    void aThrowingListenerIsContained() {
        SpotifySession session = session();
        boolean[] reached = {false};

        session.setOnChanged(() -> {
            throw new IllegalStateException("deliberate");
        });
        session.addOnChanged(() -> reached[0] = true);
        session.setCatalogCredentials("an-id", "a-secret");

        assertTrue(reached[0]);
    }

    @Test
    @DisplayName("with nothing configured a search asks the daemon rather than throwing")
    void anUnconfiguredSearchFallsBackQuietly() {
        SpotifySession session = session();

        // No daemon is running on the API port during a test, so this exercises the failure path.
        assertTrue(session.searchTracks("anything", 10).isEmpty());
    }
}
