package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.spotify.SpotifyApi;
import com.eia.superdwarfkart.spotify.SpotifyBinary;
import com.eia.superdwarfkart.spotify.SpotifySession;
import com.eia.superdwarfkart.spotify.SpotifyTrack;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connect Spotify, search it, and put what you find into the library.
 *
 * <p>The page has two halves and which one is shown is decided by the session's state, not by a
 * tab: before there is a connection there is nothing worth searching, and after there is one the
 * connection details are a footnote. Everything a user has to do to get from the first to the
 * second is on screen at the moment it is needed - install, connect, authorise - and never before.
 *
 * <p><strong>Every network call happens on this view's own thread</strong> and lands back through
 * {@code Platform.runLater}. A search is a round trip to Spotify and back; run on the interface
 * thread it would freeze the window, the meters and the runner for as long as it took, which on a
 * bad connection is seconds.
 *
 * <p>The styling reuses the classes the history and settings pages already define, so this view
 * added no rule to {@code app.css} and therefore no hex literal - the palette reaches it through
 * exactly the same {@code -role-*} tokens as everything else (ground rule 7).
 */
public class SpotifyView extends ScrollPane {

    private static final Logger LOG = Logger.getLogger(SpotifyView.class.getName());

    /** How many results a search asks for. Fifty is the Web API's own maximum per page. */
    private static final int PAGE_SIZE = 50;

    /**
     * Characters of a title shown before it is cut.
     *
     * <p>The middle column is about 1215 pixels when the structure column is folded and 815 when it
     * is not, at roughly one em per glyph - so this is set for the narrow case and the tooltip
     * carries the whole value, exactly as the library table does.
     */
    private static final int TITLE_CHARS = 44;

    private final Library library;
    private final SpotifySession session;
    private final VBox content = new VBox(18);
    private final TextField searchField = new TextField();

    /**
     * One thread for every network call this page makes.
     *
     * <p>Single, so a search typed over the top of a previous one cannot have the two results race
     * each other onto the page out of order.
     */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sdmk-spotify-view");
        thread.setDaemon(true);
        return thread;
    });

    private Consumer<Song> onSongAdded = song -> { };
    private VBox resultsBox = new VBox(2);
    private Label resultsCaption = new Label();

    /**
     * The credential fields, held rather than rebuilt.
     *
     * <p>{@link #refresh()} rebuilds the page on every state change, and a field recreated mid-type
     * loses what was in it - which on a 32-character secret being pasted in halves is exactly when
     * it would happen.
     */
    private final TextField clientIdField = new TextField();
    private final javafx.scene.control.PasswordField clientSecretField =
            new javafx.scene.control.PasswordField();
    private final Label catalogMessage = caption("");

    /** Told the credentials to persist, as {@code {clientId, clientSecret}}; nulls to clear. */
    private Consumer<String[]> onCredentialsSaved = credentials -> { };

    /**
     * Builds the page.
     *
     * @param library where added tracks go; must not be {@code null}
     * @param session the Spotify connection; must not be {@code null}
     */
    public SpotifyView(Library library, SpotifySession session) {
        this.library = library;
        this.session = session;

        content.setPadding(new Insets(18));
        content.getStyleClass().add("history-content");
        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("history-view");

        searchField.setPromptText("Search Spotify");
        searchField.setOnAction(event -> runSearch());

        session.setOnChanged(this::refresh);
        refresh();
    }

    /**
     * Sets what happens when a track is added to the library.
     *
     * @param action given the new song; must not be {@code null}
     */
    public void setOnSongAdded(Consumer<Song> action) {
        this.onSongAdded = action == null ? song -> { } : action;
    }

    /**
     * Sets what stores the catalogue credentials when they have been verified.
     *
     * @param action given {@code {clientId, clientSecret}}, both {@code null} to clear
     */
    public void setOnCredentialsSaved(Consumer<String[]> action) {
        this.onCredentialsSaved = action == null ? credentials -> { } : action;
    }

    /**
     * Fills the credential fields from stored settings and turns searching on if they are there.
     *
     * <p>Not verified here: that is a network round trip, and doing one at startup for a feature
     * nobody may open is exactly what this whole package avoids. A stored credential that has since
     * been revoked reports itself on the first search.
     *
     * @param clientId     the stored client id, or {@code null}
     * @param clientSecret the stored client secret, or {@code null}
     */
    public void restoreCredentials(String clientId, String clientSecret) {
        clientIdField.setText(clientId == null ? "" : clientId);
        clientSecretField.setText(clientSecret == null ? "" : clientSecret);
        session.setCatalogCredentials(clientId, clientSecret);
        refresh();
    }

    // ------------------------------------------------------------------
    // The page
    // ------------------------------------------------------------------

    /** Rebuilds the page from the session's current state. Called on every state change. */
    public final void refresh() {
        content.getChildren().setAll(heading("SPOTIFY"), connectionSection(), catalogSection());
        if (session.isSearchAvailable()) {
            content.getChildren().addAll(searchSection(), resultsSection());
        } else if (session.isConnected()) {
            content.getChildren().add(searchUnavailableSection());
        }
    }

    /**
     * The Spotify application catalogue search runs as.
     *
     * <p><strong>This is the panel that made a released daemon enough.</strong> Search used to go
     * through go-librespot's {@code /web-api} proxy, which borrows librespot's own hardcoded client
     * id - shared by every librespot instance there is, and rate-limited accordingly: measured, a
     * {@code retry-after} that went <em>up</em> over forty seconds of complete idleness, because the
     * usage being limited was never ours. A registered application of the user's own has a quota
     * nobody else can spend.
     *
     * <p>It also removes the dependency that forced a source build: {@code /token} and
     * {@code /web-api} are both master-only, and searching here uses neither.
     *
     * @return the panel
     */
    private Node catalogSection() {
        boolean configured = session.isCatalogSearch();
        VBox body = new VBox(10);

        Label status = new Label(configured ? "CATALOGUE SEARCH: ON" : "CATALOGUE SEARCH: OFF");
        status.getStyleClass().add("settings-value");
        body.getChildren().add(status);

        body.getChildren().add(caption(configured
                ? "Searching as your own Spotify application, on a quota nothing else shares. "
                        + "Playback still goes through go-librespot."
                : "Register a free application at developer.spotify.com and paste its details here "
                        + "to turn searching on. No redirect URI is needed and no second login: "
                        + "this is the Client Credentials flow, which searches the public "
                        + "catalogue and never sees your account."));

        clientIdField.setPromptText("Client ID");
        clientIdField.setPrefColumnCount(34);
        clientSecretField.setPromptText("Client secret");
        clientSecretField.setPrefColumnCount(34);

        body.getChildren().addAll(
                labelled("CLIENT ID", clientIdField),
                labelled("SECRET", clientSecretField),
                new HBox(8,
                        button("SAVE & VERIFY", "Check the credentials and turn searching on",
                                this::saveCredentials),
                        button("CLEAR", "Forget these credentials", this::clearCredentials)));

        if (!catalogMessage.getText().isBlank()) {
            body.getChildren().add(catalogMessage);
        }
        return section("SEARCH APPLICATION", body);
    }

    private Node labelled(String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-caption");
        label.setMinWidth(90);
        HBox row = new HBox(8, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Verifies the credentials before storing them.
     *
     * <p>Verified rather than merely saved, because the failure mode otherwise is a search box that
     * returns nothing - which looks like Spotify having no results rather than like a mistyped
     * secret.
     */
    private void saveCredentials() {
        String id = clientIdField.getText();
        String secret = clientSecretField.getText();
        catalogMessage.setText("Checking...");
        worker.submit(() -> {
            // Rolls itself back on a refusal, so pressing this with a typo cannot turn off a search
            // that was working when the window opened.
            boolean ok = session.applyCatalogCredentials(id, secret);
            String problem = session.searchProblem();
            Platform.runLater(() -> {
                if (ok) {
                    onCredentialsSaved.accept(new String[] {id, secret});
                    catalogMessage.setText("");
                } else {
                    catalogMessage.setText(problem == null ? "Those credentials did not work."
                            : problem);
                }
                refresh();
            });
        });
    }

    private void clearCredentials() {
        clientIdField.clear();
        clientSecretField.clear();
        catalogMessage.setText("");
        session.setCatalogCredentials(null, null);
        onCredentialsSaved.accept(new String[] {null, null});
        refresh();
    }

    /**
     * Shown when the daemon can play but cannot search.
     *
     * <p>Search goes through go-librespot's {@code /web-api} proxy, which is on the project's
     * {@code master} branch and in <strong>no tagged release</strong> - v0.8.0's own API spec
     * contains neither it nor {@code /token}. So a stock Homebrew or GitHub-release install plays
     * perfectly and cannot search, and saying so is the whole point of this panel: the alternative
     * is a search box that returns nothing however it is used, which reads as a bug in this
     * application rather than as a missing endpoint in the daemon.
     *
     * @return the panel
     */
    private Node searchUnavailableSection() {
        VBox body = new VBox(8,
                caption("Fill in the search application above and searching works immediately - "
                        + "that is the short way, it needs no particular daemon build, and it has a "
                        + "rate limit of its own rather than one shared with every librespot user."),
                caption("The rest of this panel is the other route: go-librespot's own Web API "
                        + "proxy, which this build does not have"
                        + (session.daemonVersion() == null
                                ? "." : " - this daemon is " + session.daemonVersion() + ".")
                        + " It is only worth building for your saved tracks and playlists, which "
                        + "need an account behind them and so cannot come from the search "
                        + "application above. Playback works either way."));

        // Checked here rather than at the build, so everything missing is named at once. The build
        // itself fails several packages deep and reports which executable cgo could not run, which
        // names Go's problem rather than the user's - and fixing one missing library only leads
        // into the next.
        SpotifyBinary.BuildPrerequisites prerequisites = session.buildPrerequisites();

        if (prerequisites.isSatisfied()) {
            body.getChildren().addAll(
                    caption("Building one takes a few minutes. It is installed into this "
                            + "application's own folder, leaving the one on PATH alone:"),
                    caption(SpotifyBinary.buildCommand()),
                    actionRow("BUILD & ENABLE SEARCH",
                            "Runs " + SpotifyBinary.buildCommand() + " into "
                                    + SpotifyBinary.installDir(),
                            session::buildFromSource));
        } else {
            String command = prerequisites.installCommand();
            body.getChildren().add(caption("Building one needs these first, which are not "
                    + "installed: " + prerequisites.describe()));
            if (command != null) {
                body.getChildren().add(caption(command));
            }
            if (command != null && SpotifyBinary.isMac()) {
                body.getChildren().add(actionRow("INSTALL BUILD TOOLS", "Runs " + command,
                        session::installBuildPrerequisites));
            } else if (command != null) {
                // Needs root, and this application does not ask for that. Copy it and run it.
                body.getChildren().add(caption("Run that in a terminal, then press RE-CHECK."));
            }
        }

        body.getChildren().add(actionRow("RE-CHECK",
                "Look again, after installing build tools or a newer daemon", this::refresh));
        return section("SEARCH", body);
    }

    /**
     * The connection panel: what state the session is in, and the one action that moves it on.
     *
     * @return the panel
     */
    private Node connectionSection() {
        SpotifySession.State state = session.state();
        VBox body = new VBox(10);

        Label status = new Label(state.label().toUpperCase(java.util.Locale.ROOT));
        status.getStyleClass().add("settings-value");
        body.getChildren().add(status);

        // Not shown for UNAVAILABLE: the install row below says the same thing at more length, and
        // the same sentence twice in a row reads as a page that has lost track of itself.
        if (!session.detail().isBlank() && state != SpotifySession.State.UNAVAILABLE) {
            body.getChildren().add(caption(session.detail()));
        }

        switch (state) {
            case UNAVAILABLE -> body.getChildren().add(installRow());
            case READY_TO_CONNECT, FAILED -> body.getChildren().add(
                    actionRow("CONNECT", "Start go-librespot and log in", session::connect));
            case AWAITING_LOGIN -> body.getChildren().add(loginRow());
            case STARTING -> body.getChildren().add(caption("Working..."));
            case CONNECTED -> body.getChildren().add(
                    actionRow("DISCONNECT", "Stop the daemon and release the account",
                            this::disconnect));
        }

        // Named outright rather than left implicit: when this does not work, which binary was
        // found and where is the first thing anybody needs to know.
        SpotifyBinary.Resolution binary = session.binary();
        body.getChildren().add(caption("Daemon: " + binary.origin().label()
                + (binary.path() == null ? "" : " - " + binary.path())));

        return section("CONNECTION", body);
    }

    /**
     * The row shown when the daemon is not installed.
     *
     * <p>go-librespot publishes Linux binaries only, so on a Mac there is nothing to download and
     * the honest offer is to run Homebrew. The command is printed on the page as well as on the
     * button, because it changes the machine and the user is entitled to see what will run.
     *
     * @return the row
     */
    private Node installRow() {
        if (!SpotifyBinary.isSupportedPlatform()) {
            return caption("Spotify playback needs a named pipe, which this platform does not have.");
        }
        String command = SpotifyBinary.installCommand();
        if (command != null) {
            VBox box = new VBox(8,
                    caption("No macOS build of go-librespot is published. Homebrew has one:"),
                    caption(command),
                    actionRow("INSTALL", "Runs " + command, session::runInstallCommand));
            return box;
        }
        if (SpotifyBinary.isDownloadable()) {
            return actionRow("DOWNLOAD", "Fetch go-librespot for this platform",
                    session::prefetchBinary);
        }
        return caption("No go-librespot build is published for this platform.");
    }

    /**
     * The row shown while the daemon waits for the browser.
     *
     * @return the row
     */
    private Node loginRow() {
        String url = session.authorizationUrl();
        VBox box = new VBox(8, caption("Authorise this device in your browser to finish."));
        if (url == null) {
            return box;
        }

        // The link is shown as well as opened, in a field it can be selected and copied out of.
        // Opening a browser is the one step here that depends on the desktop environment playing
        // along, and when it does not the user is one copy-and-paste from finishing rather than
        // stuck - which is worth far more than the row costs. A Label would not do: it cannot be
        // selected, so the link would be visible and unusable.
        TextField link = new TextField(url);
        link.setEditable(false);
        link.setPrefColumnCount(48);
        link.setTooltip(new Tooltip("Select and copy if the browser does not open"));
        HBox.setHgrow(link, Priority.ALWAYS);

        box.getChildren().addAll(
                actionRow("OPEN BROWSER", "Open the Spotify authorisation page",
                        () -> openBrowser(url)),
                caption("Or copy this link:"),
                link);
        return box;
    }

    private void disconnect() {
        worker.submit(() -> {
            session.close();
            Platform.runLater(this::refresh);
        });
    }

    /**
     * Opens the authorisation link.
     *
     * <p>{@code java.awt.Desktop} rather than {@code HostServices}, because this class is not the
     * {@code Application} and threading a reference to it through five constructors to open one URL
     * would be the tail wagging the dog. Failure is reported rather than thrown: the link is on
     * screen and can be copied.
     *
     * @param url the address to open
     */
    private void openBrowser(String url) {
        worker.submit(() -> {
            java.net.URI target;
            try {
                target = new java.net.URI(url);
            } catch (java.net.URISyntaxException e) {
                // Reported with the offending text, because the link is on screen and the user can
                // still finish by hand - and because the only way this happens is the daemon's log
                // format changing under the pattern that extracts it.
                LOG.log(Level.WARNING, "The Spotify authorisation link is not a usable URL: " + url, e);
                return;
            }
            try {
                java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported()
                        ? java.awt.Desktop.getDesktop() : null;
                if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(target);
                    return;
                }
                // Desktop reports unsupported on plenty of Linux setups. The platform opener works
                // there, and the link is on screen either way.
                openWithPlatformCommand(url);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Could not open a browser for the Spotify login; "
                        + "the link is on screen and can be copied", e);
            }
        });
    }

    /**
     * Falls back to the operating system's own opener.
     *
     * @param url the address to open
     */
    private static void openWithPlatformCommand(String url) throws IOException {
        String command = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("mac") ? "open" : "xdg-open";
        new ProcessBuilder(command, url).start();
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private Node searchSection() {
        Button search = button("SEARCH", "Search Spotify's catalogue", this::runSearch);

        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox row = new HBox(8, searchField, search);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(10, row);

        // Saved tracks and playlists are the user's own, and an application token is not a user -
        // the Client Credentials flow answers 401 for every /v1/me path however the request is
        // shaped. They stay on the daemon's proxy, which is the only route that has a user behind
        // it. Offered only when that route exists, because a button that cannot work is worse than
        // no button.
        if (session.isUserLibraryAvailable()) {
            body.getChildren().add(new HBox(8,
                    button("LIKED SONGS", "Your saved tracks",
                            () -> load("Liked Songs", () -> session.api().savedTracks(PAGE_SIZE, 0))),
                    button("PLAYLISTS", "Your playlists", this::showPlaylists)));
        }

        if (!session.isConnected()) {
            body.getChildren().add(caption("Searching works without a connection. Playing a result "
                    + "needs go-librespot connected above."));
        }
        return section("FIND", body);
    }

    private Node resultsSection() {
        resultsCaption = caption("Search, or open your saved tracks.");
        resultsBox = new VBox(2);
        return section("RESULTS", new VBox(10, resultsCaption, resultsBox));
    }

    private void runSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            return;
        }
        load("\"" + query.trim() + "\"", () -> session.searchTracks(query, PAGE_SIZE));
    }

    private void showPlaylists() {
        resultsCaption.setText("Loading playlists...");
        resultsBox.getChildren().clear();
        worker.submit(() -> {
            List<SpotifyApi.Playlist> playlists = session.api().playlists(PAGE_SIZE);
            Platform.runLater(() -> {
                resultsCaption.setText(playlists.isEmpty()
                        ? "No playlists found."
                        : playlists.size() + " playlists");
                resultsBox.getChildren().clear();
                for (SpotifyApi.Playlist playlist : playlists) {
                    Button open = new Button(ellipsize(playlist.name(), TITLE_CHARS)
                            + "  -  " + playlist.total() + " tracks");
                    open.getStyleClass().add("history-entry");
                    open.setFocusTraversable(false);
                    open.setMaxWidth(Double.MAX_VALUE);
                    open.setTooltip(new Tooltip(playlist.name()));
                    open.setOnAction(event -> load(playlist.name(),
                            () -> session.api().playlistTracks(playlist.id(), PAGE_SIZE, 0)));
                    resultsBox.getChildren().add(open);
                }
            });
        });
    }

    /**
     * Runs a fetch off the interface thread and shows what came back.
     *
     * @param what    what is being loaded, for the caption
     * @param fetcher the call to make; runs on the worker thread
     */
    private void load(String what, Supplier<List<SpotifyTrack>> fetcher) {
        resultsCaption.setText("Loading " + what + "...");
        resultsBox.getChildren().clear();
        worker.submit(() -> {
            List<SpotifyTrack> tracks;
            try {
                tracks = fetcher.get();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A Spotify request failed", e);
                tracks = List.of();
            }
            List<SpotifyTrack> found = tracks;
            Platform.runLater(() -> showTracks(what, found));
        });
    }

    private void showTracks(String what, List<SpotifyTrack> tracks) {
        if (tracks.isEmpty()) {
            // An empty list has several quite different causes and they need different actions
            // from the user. "Nothing found" for a rate limit or a lapsed session is the kind of
            // wrong message that gets a working feature reported as broken.
            String problem = session.searchProblem();
            resultsCaption.setText(problem == null ? "Nothing found for " + what : problem);
        } else {
            resultsCaption.setText(tracks.size() + " tracks for " + what);
        }
        resultsBox.getChildren().clear();
        for (SpotifyTrack track : tracks) {
            resultsBox.getChildren().add(trackRow(track));
        }
    }

    /**
     * One result: what it is, how long it is, and the one thing to do with it.
     *
     * @param track the track
     * @return the row
     */
    private Node trackRow(SpotifyTrack track) {
        boolean already = library.containsSpotifyUri(track.uri());

        Label length = new Label(formatDuration(track.duration()));
        length.getStyleClass().add("history-time");
        length.setMinWidth(52);

        Label title = new Label(ellipsize(track.title() + "  -  " + track.artist(), TITLE_CHARS));
        title.getStyleClass().add("settings-caption");
        title.setTooltip(new Tooltip(track.title() + "\n" + track.artist()
                + (track.album().isBlank() ? "" : "\n" + track.album())));
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        Button add = new Button(already ? "IN LIBRARY" : "ADD");
        add.getStyleClass().add("history-entry");
        add.setFocusTraversable(false);
        add.setMinWidth(96);
        add.setDisable(already);
        add.setTooltip(new Tooltip(already
                ? "This track is already in the library"
                : "Add to the library and the running order"));
        add.setOnAction(event -> {
            Song song = track.toSong();
            if (library.containsSpotifyUri(song.getSpotifyUri())) {
                return;
            }
            library.add(song);
            add.setText("IN LIBRARY");
            add.setDisable(true);
            onSongAdded.accept(song);
        });

        HBox row = new HBox(10, length, title, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ------------------------------------------------------------------
    // Small shared pieces, matching the other pages
    // ------------------------------------------------------------------

    private Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-heading");
        return label;
    }

    private VBox section(String title, Node body) {
        Label label = new Label(title);
        label.getStyleClass().add("panel-heading");
        VBox box = new VBox(8, label, body);
        box.getStyleClass().add("history-section");
        box.setPadding(new Insets(12));
        return box;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-caption");
        label.setWrapText(true);
        return label;
    }

    private Button button(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        // Nothing on this page may take focus: a focused button answers the space bar, and space
        // is play/pause. Same rule as the side rail and the header's toggles.
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    private Node actionRow(String text, String tooltip, Runnable action) {
        HBox row = new HBox(8, button(text, tooltip, action));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * @param text  the text to shorten
     * @param limit how many characters may be shown
     * @return the text, cut with a trailing ellipsis if it was too long
     */
    private static String ellipsize(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static String formatDuration(Duration duration) {
        if (duration == null || duration.isZero()) {
            return "-";
        }
        long totalSeconds = duration.toSeconds();
        return totalSeconds / 60 + ":" + String.format("%02d", totalSeconds % 60);
    }

    /** Stops the page's worker thread. Called when the application shuts down. */
    public void shutdown() {
        worker.shutdownNow();
    }
}
