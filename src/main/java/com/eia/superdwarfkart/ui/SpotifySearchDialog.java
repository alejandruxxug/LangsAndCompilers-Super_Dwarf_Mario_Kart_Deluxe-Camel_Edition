package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.Genre;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.spotify.SpotifyCatalog;
import com.eia.superdwarfkart.spotify.SpotifySession;
import com.eia.superdwarfkart.spotify.SpotifyTrack;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Search Spotify from the library screen, and file what you find properly.
 *
 * <p><strong>Why this exists when the Spotify page already has a search box.</strong> That page is a
 * <em>connection</em> page - install, log in, register an application - and searching is the last panel
 * on it. Adding a song is a library action, so it belongs where the library is; and its ADD button
 * adds a track exactly as Spotify describes it, which leaves the two fields Spotify cannot tell us
 * blank forever. This dialog is the same search with the missing half attached: pick a track, check
 * what came back, choose a genre, set a rating, add it once.
 *
 * <p>Three things arrive with the track and one does not:
 *
 * <ul>
 *   <li><strong>Album, year and artwork</strong> are on the track object and were always parsed - see
 *       {@link SpotifyTrack#fromJson}. They are shown here rather than merely stored, because a field
 *       that is populated and invisible is indistinguishable from one that is empty.</li>
 *   <li><strong>Genre is not.</strong> Spotify files genres against the <em>artist</em>, so it takes a
 *       second request; that request is made here, for one track, at the moment it is opened - see
 *       {@link SpotifySession#artistGenres}. The tags are its own free text and
 *       {@link Genre#fromTags} maps them onto ours. It is a <em>default</em>, not a verdict: the box is
 *       a dropdown and the user is looking straight at it.</li>
 *   <li><strong>Rating is the user's</strong>, and always was. The 0 to 100 scale is the assignment's,
 *       and setting it while adding is one less trip through the edit dialog for the field most likely
 *       to be wanted immediately.</li>
 * </ul>
 *
 * <p><strong>Every network call runs off the interface thread</strong> and lands back through
 * {@code Platform.runLater}, exactly as {@code SpotifyView} does. A search is a round trip to Spotify,
 * and on a bad connection running it here would freeze the window, the meters and the runner for as
 * long as it took - inside a modal dialog, where there is nothing else to look at.
 *
 * <p>It adds no colour of its own: every style class here is one the library, history and settings
 * pages already define, so the palette reaches it through the same {@code -role-*} tokens as
 * everything else (ground rule 7).
 */
public final class SpotifySearchDialog {

    private static final Logger LOG = Logger.getLogger(SpotifySearchDialog.class.getName());

    /**
     * How many results to ask for.
     *
     * <p>{@link SpotifyCatalog#MAX_SEARCH_LIMIT} rather than a number of our own, because Spotify
     * refuses anything above ten outright with {@code 400 Invalid limit} - and its own documentation
     * says the maximum is fifty. That trap cost a whole session once; naming the constant is how it
     * stays fixed.
     */
    private static final int PAGE_SIZE = SpotifyCatalog.MAX_SEARCH_LIMIT;

    /**
     * Characters of a result row shown before it is cut.
     *
     * <p>The dialog is sized to its content and the rows are the widest thing in it, so this is what
     * decides how wide the window comes out. At roughly one em per glyph, 58 characters is about 460
     * pixels at 8px - which is the width {@code PixelDialog}'s own message label is already set to.
     */
    private static final int ROW_CHARS = 58;

    /** How many characters of the album line are shown; the rest is in the tooltip. */
    private static final int ALBUM_CHARS = 34;

    private final Library library;
    private final SpotifySession session;
    private final Window owner;

    /**
     * One thread for every request this dialog makes.
     *
     * <p>Single, so a search typed over the top of a previous one cannot have the two results land on
     * the page out of order - and so a genre lookup queues behind the search that produced it.
     */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sdmk-spotify-add");
        thread.setDaemon(true);
        return thread;
    });

    private final TextField queryField = new TextField();
    private final Label status = caption("");
    private final VBox results = new VBox(2);

    /** The form for the selected track. Hidden until there is one, because it would be all blanks. */
    private final VBox form = new VBox(10);
    private final Label chosenTitle = new Label();
    private final Label chosenArtist = new Label();
    private final Label chosenAlbum = new Label();
    private final ComboBox<Genre> genreBox = new ComboBox<>();
    private final Label genreSource = caption("");
    private final Slider ratingSlider = new Slider(Song.MIN_RATING, Song.MAX_RATING, 0);
    private final Label ratingValue = new Label("0");
    private final RatingDisplay ratingMeter = new RatingDisplay(9, 15, 20, 0);
    private final CheckBox favorite = new CheckBox("FAVORITE");

    private final PixelDialog dialog;

    /** The track the form is showing, or {@code null} when nothing has been picked. */
    private SpotifyTrack chosen;

    /**
     * The rows, so the picked one can be marked.
     *
     * <p>Buttons rather than a {@code ListView}: a bare list view is one of the few controls this
     * project has not restyled, so it would arrive in Modena's own look inside an 8-bit dialog
     * (section 3b). The rows reuse {@code .history-entry}, which the history page already defines.
     */
    private final List<Button> rows = new java.util.ArrayList<>();

    /**
     * Opens the dialog and blocks until it is closed.
     *
     * @param owner   the window to centre on; may be {@code null}
     * @param library where an added track goes; must not be {@code null}
     * @param session the Spotify connection; must not be {@code null}
     * @return the song that was added, or empty if the user cancelled or added nothing
     */
    public static Optional<Song> show(Window owner, Library library, SpotifySession session) {
        return new SpotifySearchDialog(owner, library, session).run();
    }

    /**
     * Builds the dialog with a track already picked, photographs it, and closes it again.
     *
     * <p><strong>The only way to check this dialog's layout.</strong> It is a form in a fixed-width
     * pixel font where every caption has to fit the width it is given, and a caption that does not
     * still draws, still throws nothing and still looks deliberate - section 3b's rule is that this
     * kind of overflow is checked by taking a picture. A picture of the dialog as it opens would not do
     * either: the form is hidden until something is picked, so the interesting half of it would not be
     * in the shot. So a result is supplied and chosen here, exactly as a click would.
     *
     * <p>The genre lookup is deliberately not made - the track handed in carries no artist id, so
     * nothing goes to the network and the box shows the state a track with nothing to look up leaves
     * it in.
     *
     * @param owner       the window to centre on
     * @param library     the library, for the "already added" marker
     * @param session     the Spotify connection, only read for whether searching is on
     * @param results     the rows to show, as though a search had returned them
     * @param photographer given the laid-out scene while the dialog is on screen
     */
    public static void capture(Window owner, Library library, SpotifySession session,
            List<SpotifyTrack> results,
            java.util.function.Consumer<javafx.scene.Scene> photographer) {
        SpotifySearchDialog page = new SpotifySearchDialog(owner, library, session);
        page.dialog.setContent(page.buildContent());
        page.dialog.setAcceptText("ADD");
        javafx.scene.Scene scene = page.dialog.showForCapture();
        page.showTracks("\"crystal castles\"", results, null);
        if (!page.rows.isEmpty()) {
            page.choose(results.get(0), page.rows.get(0));
        }
        page.dialog.resizeToContent();
        photographer.accept(scene);
        page.dialog.close();
        page.worker.shutdownNow();
    }

    private SpotifySearchDialog(Window owner, Library library, SpotifySession session) {
        this.owner = owner;
        this.library = library;
        this.session = session;
        this.dialog = new PixelDialog(owner, "ADD FROM SPOTIFY");
    }

    /**
     * Builds the dialog, shows it, and adds the chosen track if it was accepted.
     *
     * @return the song added, or empty
     */
    private Optional<Song> run() {
        dialog.setContent(buildContent());
        dialog.setAcceptText("ADD");
        // Nothing to add until a track has been picked. Bound to a property rather than set in the
        // row handlers, so the button cannot get out of step with the selection.
        dialog.acceptDisabledProperty().set(true);

        // Searching without a configured application returns nothing however it is used, which reads
        // as Spotify having no results rather than as a setting that has not been filled in. Said out
        // loud, with the one place it is filled in named.
        if (!session.isSearchAvailable()) {
            status.setText("Catalogue search is off. Open Spotify in the side rail and register a "
                    + "free application there, then come back.");
            queryField.setDisable(true);
        }

        boolean accepted;
        try {
            accepted = dialog.showAndWait();
        } finally {
            // Whatever happens, the thread goes. A dialog opened and cancelled a dozen times must not
            // leave a dozen threads parked behind it.
            worker.shutdownNow();
        }

        if (!accepted || chosen == null) {
            return Optional.empty();
        }
        return Optional.of(addChosen());
    }

    /**
     * Puts the chosen track into the library with the fields from the form.
     *
     * <p>The membership check is repeated here as well as on the row, because the two are separated by
     * however long the user spent choosing a genre and the library is shared with everything else in
     * the application - a track added from the Spotify page in the meantime would otherwise arrive
     * twice.
     *
     * @return the song, whether it was added now or already there
     */
    private Song addChosen() {
        Song existing = library.all().stream()
                .filter(song -> chosen.uri().equals(song.getSpotifyUri()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        Song song = chosen.toSong();
        song.setGenre(genreBox.getValue() == null ? Genre.UNKNOWN : genreBox.getValue());
        song.setRating((int) Math.round(ratingSlider.getValue()));
        song.setFavorite(favorite.isSelected());
        library.add(song);
        return song;
    }

    // ------------------------------------------------------------------
    // The page
    // ------------------------------------------------------------------

    private Node buildContent() {
        queryField.setPromptText("Search Spotify");
        queryField.setPrefColumnCount(34);
        queryField.setOnAction(event -> runSearch());
        HBox.setHgrow(queryField, Priority.ALWAYS);

        Button search = new Button("SEARCH");
        search.setFocusTraversable(false);
        search.setTooltip(new Tooltip("Search Spotify's catalogue"));
        search.setOnAction(event -> runSearch());
        search.disableProperty().bind(queryField.disabledProperty());

        HBox searchRow = new HBox(8, queryField, search);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        buildForm();

        VBox body = new VBox(12, searchRow, status, results, form);
        body.setPadding(new Insets(4));
        // Wide enough for the rows at their full length, so the window does not resize itself every
        // time a search comes back with longer titles than the last one.
        body.setPrefWidth(ROW_CHARS * 8 + 40);
        return body;
    }

    /** The fields for the picked track: what Spotify said, and the two things it cannot say. */
    private void buildForm() {
        chosenTitle.getStyleClass().add("detail-title");
        chosenTitle.setWrapText(true);
        chosenArtist.getStyleClass().add("detail-artist");
        chosenArtist.setWrapText(true);
        chosenAlbum.getStyleClass().add("detail-meta");
        chosenAlbum.setWrapText(true);

        genreBox.getItems().setAll(Genre.values());
        genreBox.setValue(Genre.UNKNOWN);
        genreBox.setPrefWidth(160);
        genreBox.setTooltip(new Tooltip(
                "Spotify files genres against the artist, so this is a guess from its tags"));

        ratingSlider.setBlockIncrement(5);
        ratingSlider.setPrefWidth(200);
        // Only the number and the meter follow the thumb. There is nothing expensive to commit here -
        // the song does not exist yet - but the meter rewrites a style class per block, so it is
        // pointless to repaint it when the picture would be identical.
        ratingSlider.valueProperty().addListener((observable, was, now) -> {
            ratingValue.setText(String.valueOf(now.intValue()));
            ratingMeter.setRating(now.intValue());
        });
        ratingValue.getStyleClass().add("rating-value");
        favorite.setFocusTraversable(false);

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        // The label column is sized to its widest caption, and this is not decoration: a GridPane
        // column is one width across every row, so left to itself it settles on whatever the *shortest*
        // label needs and quietly ellipsizes the rest. Measured on the first screenshot of this dialog:
        // ALBUM and GENRE fitted at five characters and RATING came out as "R...", which reads as a
        // control with no name on it. Nothing throws and nothing else reports it.
        javafx.scene.layout.ColumnConstraints captions = new javafx.scene.layout.ColumnConstraints();
        captions.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        fields.getColumnConstraints().add(captions);

        // The meter, its number and the slider that edits them, as one block: the meter reads the
        // value and the slider sets it, and separating them across grid rows let the slider stretch to
        // the full width of a column it shares with a caption.
        HBox meterRow = new HBox(10, ratingMeter, ratingValue);
        meterRow.setAlignment(Pos.CENTER_LEFT);
        VBox ratingBlock = new VBox(8, meterRow, ratingSlider);
        ratingBlock.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        int row = 0;
        fields.addRow(row++, fieldLabel("ALBUM"), chosenAlbum);
        fields.addRow(row++, fieldLabel("GENRE"), new HBox(8, genreBox));
        fields.addRow(row++, fieldLabel(""), genreSource);
        fields.addRow(row++, fieldLabel("RATING"), ratingBlock);
        fields.addRow(row, fieldLabel(""), favorite);

        form.getChildren().setAll(new javafx.scene.control.Separator(),
                chosenTitle, chosenArtist, fields);
        form.getStyleClass().add("song-form");
        // Hidden *and* unmanaged: a form of empty fields taking up its own height would make the
        // dialog open at the size it will be once something is picked, with a blank half.
        form.setVisible(false);
        form.setManaged(false);
    }

    // ------------------------------------------------------------------
    // Searching
    // ------------------------------------------------------------------

    private void runSearch() {
        String query = queryField.getText();
        if (query == null || query.isBlank()) {
            return;
        }
        status.setText("Searching for \"" + query.trim() + "\"...");
        results.getChildren().clear();
        rows.clear();
        worker.submit(() -> {
            List<SpotifyTrack> found;
            try {
                found = session.searchTracks(query, PAGE_SIZE);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A Spotify search failed", e);
                found = List.of();
            }
            String problem = session.searchProblem();
            List<SpotifyTrack> tracks = found;
            Platform.runLater(() -> showTracks(query.trim(), tracks, problem));
        });
    }

    /**
     * Puts the results on the page.
     *
     * @param query   what was searched for, for the caption
     * @param tracks  what came back
     * @param problem what went wrong, or {@code null} when the search simply matched nothing
     */
    private void showTracks(String query, List<SpotifyTrack> tracks, String problem) {
        if (tracks.isEmpty()) {
            // An empty list has several quite different causes and they need different actions from
            // the user. "Nothing found" for a rate limit or a wrong secret is the kind of wrong
            // message that gets a working feature reported as broken.
            status.setText(problem == null ? "Nothing on Spotify matched \"" + query + "\"." : problem);
        } else {
            status.setText(tracks.size() + " results - pick one");
        }
        results.getChildren().clear();
        rows.clear();
        for (SpotifyTrack track : tracks) {
            Button row = trackRow(track);
            rows.add(row);
            results.getChildren().add(row);
        }
        // The window grew or shrank by however many rows came back.
        dialog.resizeToContent();
    }

    /**
     * One result, as a row that picks it.
     *
     * @param track the track
     * @return the row
     */
    private Button trackRow(SpotifyTrack track) {
        boolean already = library.containsSpotifyUri(track.uri());
        String label = formatDuration(track.duration()) + "  " + track.title() + " - " + track.artist();

        Button row = new Button(ellipsize(already ? label + "  [IN LIBRARY]" : label, ROW_CHARS));
        row.getStyleClass().addAll("history-entry", "result-row");
        row.setFocusTraversable(false);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setTooltip(new Tooltip(track.title() + "\n" + track.artist()
                + (track.album().isBlank() ? "" : "\n" + track.album())
                + (already ? "\n\nAlready in the library" : "")));
        // A track already in the library is still selectable, deliberately: choosing it and pressing
        // ADD returns the copy that is there rather than a duplicate, and the alternative - a dead row
        // - leaves the user wondering whether the search found their song or not.
        row.setOnAction(event -> choose(track, row));
        return row;
    }

    /**
     * Picks a track: marks its row, fills the form, and goes to look up its genre.
     *
     * @param track the track chosen
     * @param row   its row, so it can be marked
     */
    private void choose(SpotifyTrack track, Button row) {
        chosen = track;
        for (Button other : rows) {
            other.pseudoClassStateChanged(SELECTED, other == row);
        }

        chosenTitle.setText(track.title());
        chosenArtist.setText(track.artist());
        chosenAlbum.setText(albumLine(track));
        chosenAlbum.setTooltip(track.album().isBlank() ? null : new Tooltip(track.album()));

        // The library's own answer goes in immediately, because it needs no network and is very often
        // the right one - see Library.genreForArtist, and see lookUpGenre for why Spotify's is now the
        // fallback rather than the source.
        Genre known = library.genreForArtist(track.artist());
        genreBox.setValue(known);
        genreSource.setText(known == Genre.UNKNOWN
                ? "Nothing in the library to go on for " + track.artist() + " yet."
                : "From your library: other " + track.artist() + " songs are filed under "
                        + known.displayName() + ".");

        ratingSlider.setValue(0);
        ratingMeter.setRating(0);
        ratingValue.setText("0");
        favorite.setSelected(false);

        form.setVisible(true);
        form.setManaged(true);
        dialog.acceptDisabledProperty().set(false);
        dialog.resizeToContent();

        lookUpGenre(track);
    }

    /**
     * Asks Spotify what it files the track's artist under, and takes the answer if there is one.
     *
     * <p><strong>Spotify has stopped answering this, and the code stays anyway.</strong> Measured
     * against the live service on 2026-08-17 with an application token: {@code v1/artists/{id}} returns
     * HTTP 200 with <em>no {@code genres} key at all</em> - not an empty array, absent - for The
     * Beatles, Ariana Grande and Don Diablo alike, while the reference still documents the field. The
     * track's own album object carries none either, so on a Client Credentials token there is currently
     * no route from Spotify to a genre. This is ground rule 6 again, with the documentation as the
     * thing that could not be trusted.
     *
     * <p>So the library's own knowledge of the artist is the <em>first</em> answer and this is the
     * upgrade: it costs one request on a track the user has deliberately opened, it is correct the
     * moment Spotify sends the field again, and until then it changes nothing and says nothing. What it
     * must never do is <strong>overwrite a better answer with an empty one</strong> - which is why an
     * empty result is discarded here rather than applied.
     *
     * <p>The answer is applied only if the user has not moved on to a different track in the meantime.
     * On a slow connection that is entirely possible, and a genre arriving late over the one they had
     * just chosen by hand would be the worst thing this field could do.
     *
     * @param track the track whose artist to look up
     */
    private void lookUpGenre(SpotifyTrack track) {
        if (track.artistId() == null) {
            return;
        }
        worker.submit(() -> {
            List<String> tags;
            try {
                tags = session.artistGenres(track.artistId());
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "Could not read the artist's genres", e);
                tags = List.of();
            }
            List<String> found = tags;
            Platform.runLater(() -> applyGenre(track, found));
        });
    }

    /**
     * Takes Spotify's tags if they said anything, and leaves the box alone if they did not.
     *
     * <p>Both guards matter. A different track being chosen in the meantime means this answer is about
     * something else; an empty answer - which is what Spotify now always gives, see
     * {@link #lookUpGenre} - would replace the library's own guess with nothing, which is a field
     * getting worse the longer the user waits.
     *
     * @param track the track the tags belong to
     * @param tags  Spotify's own free-text genres, possibly empty
     */
    private void applyGenre(SpotifyTrack track, List<String> tags) {
        if (chosen == null || !chosen.uri().equals(track.uri()) || tags.isEmpty()) {
            return;
        }
        Genre guess = Genre.fromTags(tags);
        if (guess != Genre.UNKNOWN) {
            genreBox.setValue(guess);
        }
        genreSource.setText("Spotify: " + ellipsize(String.join(", ", tags), ROW_CHARS)
                + (guess == Genre.UNKNOWN ? " - none of ours matched" : ""));
        genreSource.setTooltip(new Tooltip(String.join("\n", tags)));
    }

    // ------------------------------------------------------------------
    // Small shared pieces
    // ------------------------------------------------------------------

    /** Marks the picked row. Styled by {@code .result-row:selected} in {@code app.css}. */
    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /**
     * @param track the track
     * @return its album and year, or a line saying there is none
     */
    private static String albumLine(SpotifyTrack track) {
        String album = track.album().isBlank()
                ? "NO ALBUM"
                : ellipsize(track.album(), ALBUM_CHARS);
        return track.releaseYear() == Song.UNKNOWN_YEAR
                ? album
                : album + "  -  " + track.releaseYear();
    }

    /**
     * A caption in the form's left column, which is never allowed to shorten itself.
     *
     * <p>{@code USE_PREF_SIZE} as the minimum, so the label reports its full width as the least it will
     * accept and the column has to be at least that wide. Without it a caption squeezed by the layout
     * ellipsizes silently - and an ellipsized six-letter word is a control with no name on it.
     *
     * @param text the caption, or an empty string for a continuation row
     * @return the label
     */
    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-caption");
        label.setWrapText(true);
        return label;
    }

    private static String ellipsize(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static String formatDuration(Duration duration) {
        if (duration == null || duration.isZero()) {
            return " -:--";
        }
        long totalSeconds = duration.toSeconds();
        return String.format("%2d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
