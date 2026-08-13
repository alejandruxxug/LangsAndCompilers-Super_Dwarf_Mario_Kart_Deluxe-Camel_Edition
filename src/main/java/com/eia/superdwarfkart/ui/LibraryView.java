package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.analysis.BeatmapIndex;
import com.eia.superdwarfkart.game.ScoreEntry;
import com.eia.superdwarfkart.model.Genre;
import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.LibraryListener;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.persistence.PersistenceException;
import com.eia.superdwarfkart.persistence.Repository;
import com.eia.superdwarfkart.persistence.ScoreRepository;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The library screen: a sortable table of songs with create, edit, delete, search and filters,
 * plus a details panel carrying the 0 to 100 rating control and the cover art.
 *
 * <p>The view owns no state. It reads from the {@link Library}, listens for changes and writes
 * them straight back through the {@link Repository}, so the same library driven from anywhere
 * else stays in step.
 */
public class LibraryView extends BorderPane {

    private static final Logger LOG = Logger.getLogger(LibraryView.class.getName());

    /** Width of the details column, in pixels. */
    /**
     * Width of the details column.
     *
     * <p>This sets the cover's height too, because the cover spans the column and album art is
     * square. Widening it therefore pushes the rating controls below the fold - the column was
     * cut from 340 when the playback bar took its 62 pixels off the top of the window.
     */
    private static final double DETAILS_WIDTH = 280;

    /** Padding inside the details column, in pixels. */
    private static final double DETAILS_PADDING = 16;

    /**
     * Gap between the cover frame's edge and the artwork inside it, in pixels: the 3 pixel
     * border plus a little breathing room, so the frame reads as a frame.
     */
    private static final double COVER_INSET = 7;

    /**
     * Longest side the cover is decoded at. Art on disk can be far larger than it is ever
     * displayed, and decoding a 3000 pixel image to fill a 220 pixel box wastes memory.
     */
    private static final double COVER_DECODE_SIZE = 512;

    /**
     * Shown in the race column for a song whose course is built but never driven.
     *
     * <p>A chevron rather than a letter, so it cannot be mistaken for a rank at a glance across a
     * column of them.
     */
    private static final String COURSE_READY_MARK = ">";

    private static final String ANY_ARTIST = "ALL ARTISTS";
    private static final String ANY_ALBUM = "ALL ALBUMS";
    private static final Genre ANY_GENRE = null;

    private final Library library;
    private final Repository<Song> repository;

    /** The score board the race column reads, or {@code null} when the view was built without one. */
    private final ScoreRepository scores;

    /** Which songs already have a course, or {@code null} when the view was built without one. */
    private final BeatmapIndex courses;

    /** Every song in the library, mirrored for the table. */
    private final ObservableList<Song> masterList = FXCollections.observableArrayList();

    private final FilteredList<Song> filteredList = new FilteredList<>(masterList, song -> true);
    private final TableView<Song> table = new TableView<>();

    private final TextField searchField = new TextField();
    private final ComboBox<String> artistFilter = new ComboBox<>();
    private final ComboBox<Genre> genreFilter = new ComboBox<>();
    private final ComboBox<String> albumFilter = new ComboBox<>();
    private final CheckBox favoritesOnly = new CheckBox("FAVORITES");

    private final Label statusLabel = new Label();

    private final ImageView coverImage = new ImageView();
    private final StackPane coverHolder = new StackPane();
    private final Label detailTitle = new Label();
    private final Label detailArtist = new Label();
    private final Label detailMeta = new Label();
    private final Slider ratingSlider = new Slider(Song.MIN_RATING, Song.MAX_RATING, 0);
    // Ten blocks, the star and the number have to fit the column's inner width: at 14px a block
    // that is 158 + 8 + 28 + 10 + 22, which clears 248. Widening the blocks pushes the number
    // off the edge.
    private final RatingDisplay detailRating = new RatingDisplay(14, 20, 28, 0);
    private final Label ratingValue = new Label("0");
    private final CheckBox favoriteToggle = new CheckBox("FAVORITE");

    /** Suppresses write-back while the details panel is being populated from a selection. */
    /** Called when the user asks for a song to be played, or {@code null} if nothing listens. */
    private java.util.function.Consumer<Song> onSongActivated;

    private boolean populatingDetails;

    /**
     * Incremented for each rating cell the table creates, giving every row's star a different
     * starting frame so the column shimmers rather than spinning in unison.
     */
    private int cellPhase;

    /**
     * Builds the view over a library and its storage, with no race column.
     *
     * <p>For tests and for any caller that has no score board to show.
     *
     * @param library    the library to display and edit; must not be {@code null}
     * @param repository where changes are persisted; must not be {@code null}
     */
    public LibraryView(Library library, Repository<Song> repository) {
        this(library, repository, null, null);
    }

    /**
     * Builds the view over a library and its storage.
     *
     * @param library    the library to display and edit; must not be {@code null}
     * @param repository where changes are persisted; must not be {@code null}
     * @param scores     the score board the race column reads, or {@code null} to omit the column
     * @param courses    which songs already have a course, or {@code null} to omit the marker
     */
    public LibraryView(Library library, Repository<Song> repository, ScoreRepository scores,
                       BeatmapIndex courses) {
        this.library = library;
        this.repository = repository;
        this.scores = scores;
        this.courses = courses;

        getStyleClass().add("library-view");
        setTop(buildHeader());
        setCenter(buildTable());
        setRight(buildDetails());
        setBottom(buildStatusBar());

        library.addListener(this::onLibraryChanged);
        refreshFromLibrary();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private VBox buildHeader() {
        Label heading = new Label("LIBRARY");
        heading.getStyleClass().add("section-heading");

        Button addButton = new Button("ADD");
        addButton.setTooltip(new Tooltip("Import an audio file into the library"));
        addButton.setOnAction(e -> onAdd());

        Button editButton = new Button("EDIT");
        editButton.setTooltip(new Tooltip("Edit the selected song"));
        editButton.setOnAction(e -> onEdit());

        Button deleteButton = new Button("DELETE");
        deleteButton.setTooltip(new Tooltip("Remove the selected song from the library"));
        deleteButton.setOnAction(e -> onDelete());

        // Nothing to edit or delete without a selection.
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox actions = new HBox(8, addButton, editButton, deleteButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(16, heading, spacer, actions);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Press Start 2P is about one em per character, so prompts and control widths are sized
        // in characters rather than guessed: a long prompt simply will not fit.
        searchField.setPromptText("SEARCH...");
        searchField.setPrefWidth(210);
        searchField.textProperty().addListener((obs, was, now) -> applyFilters());

        artistFilter.setPrefWidth(170);
        genreFilter.setPrefWidth(150);
        albumFilter.setPrefWidth(170);
        artistFilter.setOnAction(e -> applyFilters());
        genreFilter.setOnAction(e -> applyFilters());
        albumFilter.setOnAction(e -> applyFilters());
        favoritesOnly.selectedProperty().addListener((obs, was, now) -> applyFilters());

        genreFilter.setButtonCell(new GenreCell());
        genreFilter.setCellFactory(list -> new GenreCell());

        Button clearFilters = new Button("CLEAR");
        clearFilters.setOnAction(e -> {
            searchField.clear();
            artistFilter.getSelectionModel().selectFirst();
            albumFilter.getSelectionModel().selectFirst();
            genreFilter.getSelectionModel().selectFirst();
            favoritesOnly.setSelected(false);
        });

        // A flow pane rather than a row: in the pixel font these controls are wide, and on a
        // narrow window they wrap onto a second line instead of being clipped.
        javafx.scene.layout.FlowPane filterRow = new javafx.scene.layout.FlowPane(10, 10,
                searchField,
                new Label("ARTIST"), artistFilter,
                new Label("GENRE"), genreFilter,
                new Label("ALBUM"), albumFilter,
                favoritesOnly,
                clearFilters);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.setRowValignment(javafx.geometry.VPos.CENTER);
        filterRow.getStyleClass().add("filter-bar");

        VBox header = new VBox(12, topRow, filterRow, new Separator());
        header.setPadding(new Insets(16, 16, 10, 16));
        return header;
    }

    private TableView<Song> buildTable() {
        TableColumn<Song, String> titleColumn = column("TITLE", 200, Song::getTitle);
        TableColumn<Song, String> artistColumn = column("ARTIST", 165, Song::getArtist);
        TableColumn<Song, String> albumColumn = column("ALBUM", 150, Song::getAlbum);
        TableColumn<Song, Genre> genreColumn = new TableColumn<>("GENRE");
        genreColumn.setPrefWidth(105);
        genreColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getGenre()));

        TableColumn<Song, Integer> yearColumn = new TableColumn<>("YEAR");
        yearColumn.setPrefWidth(66);
        yearColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getYear()));
        yearColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Integer year, boolean empty) {
                super.updateItem(year, empty);
                setText(empty || year == null || year == Song.UNKNOWN_YEAR ? "" : String.valueOf(year));
            }
        });

        TableColumn<Song, Duration> durationColumn = new TableColumn<>("LENGTH");
        durationColumn.setPrefWidth(78);
        durationColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getDuration()));
        durationColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Duration duration, boolean empty) {
                super.updateItem(duration, empty);
                setText(empty || duration == null ? "" : formatDuration(duration));
            }
        });

        TableColumn<Song, Integer> ratingColumn = new TableColumn<>("RATING");
        ratingColumn.setPrefWidth(146);
        ratingColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRating()));
        ratingColumn.setCellFactory(c -> new TableCell<>() {
            // One meter per cell, reused as the table recycles cells while scrolling, rather
            // than a fresh node per update.
            private final RatingDisplay display = new RatingDisplay(7, 13, 18, cellPhase++);

            @Override
            protected void updateItem(Integer rating, boolean empty) {
                super.updateItem(rating, empty);
                if (empty || rating == null) {
                    // A recycled cell showing nothing must not keep a sprite animating.
                    display.stop();
                    setGraphic(null);
                    return;
                }
                display.setRating(rating);
                setGraphic(display);
            }
        });

        TableColumn<Song, Boolean> favoriteColumn = new TableColumn<>("FAV");
        favoriteColumn.setPrefWidth(52);
        favoriteColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().isFavorite()));
        favoriteColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean favorite, boolean empty) {
                super.updateItem(favorite, empty);
                setText(empty || favorite == null || !favorite ? "" : "*");
            }
        });

        TableColumn<Song, Integer> playsColumn = new TableColumn<>("PLAYS");
        playsColumn.setPrefWidth(68);
        playsColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getPlayCount()));

        List<TableColumn<Song, ?>> columns = new java.util.ArrayList<>(List.of(
                titleColumn, artistColumn, albumColumn, genreColumn, yearColumn, durationColumn,
                ratingColumn, favoriteColumn, playsColumn));
        if (scores != null || courses != null) {
            columns.add(buildRaceColumn());
        }
        table.getColumns().setAll(columns);

        // FilteredList cannot be sorted directly, so the table sorts a SortedList wrapped around
        // it and the comparator is bound to whichever column the user clicks.
        SortedList<Song> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedList);

        // Share the available width between the columns instead of overflowing into a
        // horizontal scrollbar.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, was, now) -> showDetails(now));
        table.setPlaceholder(new Label("LIBRARY EMPTY\nPRESS ADD TO IMPORT A SONG"));
        table.setRowFactory(t -> {
            var row = new javafx.scene.control.TableRow<Song>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onEdit();
                }
            });
            return row;
        });
        // Enter plays the highlighted song; double-click is already Edit. Browsing the library
        // deliberately does not change what is playing - only an explicit action does.
        table.setOnKeyPressed(event -> {
            Song selected = table.getSelectionModel().getSelectedItem();
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER && selected != null
                    && onSongActivated != null) {
                onSongActivated.accept(selected);
                event.consume();
            }
        });
        return table;
    }

    /**
     * Builds the race column: what this song has been driven to, and whether it can be driven yet.
     *
     * <p>Three states, in order of what they are worth: the best <strong>rank</strong> the song has
     * been driven to at any speed class, a bare marker for a song whose course is built and waiting,
     * and nothing for one that has never been analysed. That ordering matters more than it looks -
     * the column's job is to answer "what should I put on next", and a song already ranked S is a
     * worse answer than one with a fresh course on it.
     *
     * <p>Neither lookup touches the disk. The board is held in memory and the course marker comes
     * from {@link BeatmapIndex}, which does its hashing on a background thread precisely so that a
     * scrolling table never does.
     *
     * @return the column
     */
    private TableColumn<Song, String> buildRaceColumn() {
        TableColumn<Song, String> raceColumn = new TableColumn<>("RACE");
        raceColumn.setPrefWidth(64);
        raceColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(raceBadge(c.getValue())));
        raceColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String badge, boolean empty) {
                super.updateItem(badge, empty);
                getStyleClass().removeAll("race-rank", "race-ready");
                if (empty || badge == null || badge.isEmpty()) {
                    setText("");
                    setTooltip(null);
                    return;
                }
                setText(badge);
                getStyleClass().add(COURSE_READY_MARK.equals(badge) ? "race-ready" : "race-rank");
                setTooltip(new Tooltip(raceTooltip(getTableRow() == null
                        ? null : getTableRow().getItem())));
            }
        });
        return raceColumn;
    }

    /**
     * @param song the song, or {@code null}
     * @return the letter or marker shown in the race column
     */
    private String raceBadge(Song song) {
        if (song == null) {
            return "";
        }
        if (scores != null) {
            var best = scores.bestAnyClass(song.getId());
            if (best.isPresent()) {
                return best.get().rank().name();
            }
        }
        return courses != null && courses.isReady(song.getFilePath()) ? COURSE_READY_MARK : "";
    }

    /**
     * @param song the song, or {@code null}
     * @return what the race column's badge means for it
     */
    private String raceTooltip(Song song) {
        if (song == null) {
            return "";
        }
        if (scores != null) {
            var best = scores.bestAnyClass(song.getId());
            if (best.isPresent()) {
                ScoreEntry entry = best.get();
                return "Best rank " + entry.rank() + " at " + entry.speedClass().displayName()
                        + "\n" + entry.coinsCollected() + " of " + entry.coinsAvailable()
                        + " coins  (" + Math.round(entry.completion() * 100) + "%)"
                        + "\nScore " + entry.score();
            }
        }
        return "Course ready - press F6 and drive it.";
    }

    /**
     * Repaints the race column.
     *
     * <p>Called when a run is filed and when the course index finishes a batch. The table holds
     * live {@link Song} references and the badge is not one of their fields, so nothing about the
     * list has changed and only a forced re-render will show it.
     */
    public void refreshBadges() {
        table.refresh();
    }

    /**
     * Shows only the songs marked as favourites, or all of them.
     *
     * <p>The side rail's Favorites destination is this view with the filter turned on, rather than
     * a second table beside it. Favourites are a <em>filter over the library</em>, not a separate
     * collection: a second view would need its own search, its own sorting, its own rating control
     * and its own rank badges, and every one of them would be a copy that could drift. The user
     * still sees the checkbox move, which is the honest thing - the rail did something they could
     * have done themselves.
     *
     * @param only whether to restrict the table to favourites
     */
    public void setFavoritesOnly(boolean only) {
        favoritesOnly.setSelected(only);
    }

    /** @return whether the table is currently restricted to favourites */
    public boolean isFavoritesOnly() {
        return favoritesOnly.isSelected();
    }

    private static TableColumn<Song, String> column(String title, double width,
                                                    java.util.function.Function<Song, String> value) {
        TableColumn<Song, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        return col;
    }

    private Region buildDetails() {
        coverImage.setPreserveRatio(true);
        coverHolder.getStyleClass().add("cover-holder");

        detailTitle.getStyleClass().add("detail-title");
        detailTitle.setWrapText(true);
        detailArtist.getStyleClass().add("detail-artist");
        detailArtist.setWrapText(true);
        detailMeta.getStyleClass().add("detail-meta");
        detailMeta.setWrapText(true);

        // No tick marks or labels here: the meter above already reads the value out, and the
        // axis underneath cost about thirty pixels of height for information shown twice.
        ratingSlider.setShowTickMarks(false);
        ratingSlider.setShowTickLabels(false);
        ratingSlider.setBlockIncrement(5);
        // Dragging the slider fires continuously. Only the number follows the thumb; the meter,
        // the star and the write to disk wait for the drag to end.
        //
        // Committing on every event meant a full table rebuild and a JSON save per pixel of
        // travel, which is what made dragging feel heavy.
        ratingSlider.valueProperty().addListener((obs, was, now) -> {
            ratingValue.setText(String.valueOf(now.intValue()));
            if (!ratingSlider.isValueChanging()) {
                // Not a drag: a click on the track, or an arrow key. Commit straight away.
                commitRating();
            }
        });
        ratingSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                commitRating();
            }
        });
        ratingValue.getStyleClass().add("rating-value");

        favoriteToggle.selectedProperty().addListener((obs, was, now) -> {
            if (populatingDetails) {
                return;
            }
            Song selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && selected.isFavorite() != now) {
                selected.setFavorite(now);
                library.update(selected);
            }
        });

        // The meter shows the rating; the slider underneath is what edits it.
        HBox meterRow = new HBox(10, detailRating, ratingValue);
        meterRow.setAlignment(Pos.CENTER_LEFT);

        HBox ratingRow = new HBox(10, ratingSlider);
        ratingRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(ratingSlider, Priority.ALWAYS);

        Label ratingCaption = new Label("RATING 0-100");
        ratingCaption.getStyleClass().add("detail-caption");

        // Only the description scrolls. The cover is as tall as the column is wide, so on a short
        // window it pushes whatever follows it off the bottom - and what followed it was the
        // rating controls, which then could not be reached at all without a scrollbar nobody
        // wants. Splitting them fixes that for good: the editable controls are pinned to the
        // bottom of the column and are always on screen whatever the window height.
        VBox description = new VBox(10, coverHolder, detailTitle, detailArtist, detailMeta);
        description.setPadding(new Insets(DETAILS_PADDING, DETAILS_PADDING, 0, DETAILS_PADDING));
        description.getStyleClass().add("details-content");

        ScrollPane scroller = new ScrollPane(description);
        scroller.setFitToWidth(true);
        // No visible scrollbars: a bar along the edge is visual noise on a panel this narrow.
        // The wheel still scrolls, so a very short window can still reach everything.
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.getStyleClass().add("details-scroll");

        VBox editor = new VBox(8, new Separator(), ratingCaption, meterRow, ratingRow,
                favoriteToggle);
        editor.setPadding(new Insets(0, DETAILS_PADDING, DETAILS_PADDING, DETAILS_PADDING));
        editor.getStyleClass().add("details-content");

        BorderPane column = new BorderPane();
        column.setCenter(scroller);
        column.setBottom(editor);
        column.setPrefWidth(DETAILS_WIDTH);
        column.setMinWidth(DETAILS_WIDTH);
        column.setMaxWidth(DETAILS_WIDTH);
        column.getStyleClass().add("details-pane");

        // The cover spans the full width of the column inside its padding, and its height
        // follows, because album art is square. Binding rather than hardcoding keeps the two in
        // step as the column width changes, for instance when the scrollbar appears.
        var side = description.widthProperty().subtract(DETAILS_PADDING * 2);
        coverHolder.minWidthProperty().bind(side);
        coverHolder.prefWidthProperty().bind(side);
        coverHolder.maxWidthProperty().bind(side);
        coverHolder.minHeightProperty().bind(side);
        coverHolder.prefHeightProperty().bind(side);
        coverHolder.maxHeightProperty().bind(side);

        // The image stops short of the frame's edge so the border is visible around it.
        // Previously the image covered the full holder and painted straight over the border.
        var imageSide = side.subtract(COVER_INSET * 2);
        coverImage.fitWidthProperty().bind(imageSide);
        coverImage.fitHeightProperty().bind(imageSide);

        return column;
    }

    /**
     * Registers what happens when the user activates a song with Enter.
     *
     * <p>A callback rather than a reference to the player: the library view shows the library,
     * and knows nothing about how songs are ordered for playback.
     *
     * @param handler receives the activated song, or {@code null} to do nothing
     */
    public void setOnSongActivated(java.util.function.Consumer<Song> handler) {
        this.onSongActivated = handler;
    }

    private HBox buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        HBox bar = new HBox(statusLabel);
        bar.setPadding(new Insets(8, 16, 10, 16));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void onAdd() {
        Optional<Song> created = SongDialog.showAdd(window());
        if (created.isEmpty()) {
            return;
        }
        Song song = created.get();
        if (library.containsFile(song.getFilePath())) {
            PixelDialog.warn(window(), "ALREADY IMPORTED",
                    "That audio file is already in the library:\n\n" + song.getFilePath());
            return;
        }
        library.add(song);
        select(song);
    }

    private void onEdit() {
        Song selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (SongDialog.showEdit(window(), selected)) {
            library.update(selected);
            select(selected);
        }
    }

    private void onDelete() {
        Song selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean confirmed = PixelDialog.confirm(window(), "REMOVE SONG",
                "Remove \"" + selected.getTitle() + "\" from the library?\n\n"
                        + "The audio file on disk is not deleted.");
        if (confirmed) {
            library.remove(selected);
        }
    }

    /**
     * Applies the slider's current value: repaints the meter and star, then writes the change
     * through to the library.
     *
     * <p>Called when a drag finishes rather than while it runs, so the expensive part - the
     * table rebuild and the save to disk - happens once per edit instead of once per pixel.
     */
    private void commitRating() {
        int rating = (int) Math.round(ratingSlider.getValue());
        detailRating.setRating(rating);
        if (!populatingDetails) {
            applyRating(rating);
        }
    }

    private void applyRating(int rating) {
        Song selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getRating() == rating) {
            return;
        }
        try {
            selected.setRating(rating);
            library.update(selected);
        } catch (IllegalArgumentException e) {
            // The slider is bounded to the valid range, so this should be unreachable; the model
            // still guards itself, and if it ever fires we want to know rather than swallow it.
            LOG.warning("Rejected rating " + rating + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Library synchronisation
    // ------------------------------------------------------------------

    /**
     * Mirrors a library change into the table and writes it straight back to disk.
     *
     * <p>Every change is saved, including a wholesale replacement. Loading at startup does not
     * arrive here - the library is constructed from the stored songs before any listener is
     * attached - so there is no initial event to filter out, and treating a replacement as
     * "not a real change" would silently fail to persist a cleared library.
     *
     * @param change what happened
     * @param song   the song involved, or {@code null} for a whole-library change
     */
    private void onLibraryChanged(LibraryListener.LibraryChange change, Song song) {
        refreshFromLibrary();
        save();
    }

    /**
     * Rebuilds the table contents from the library, preserving the selection and the filters.
     */
    private void refreshFromLibrary() {
        Song previouslySelected = table.getSelectionModel().getSelectedItem();

        masterList.setAll(library.all());
        rebuildFilterChoices();
        applyFilters();

        // Look up anything new in the background. Already-known files are skipped inside, so this
        // costs nothing on a refresh that only changed a rating.
        if (courses != null) {
            courses.request(masterList.stream().map(Song::getFilePath).toList());
        }

        if (previouslySelected != null) {
            library.findById(previouslySelected.getId()).ifPresent(this::select);
        } else if (!table.getItems().isEmpty()) {
            // Open on a selected song so the details panel and the rating control are usable
            // straight away rather than showing an empty shell.
            table.getSelectionModel().selectFirst();
        }
        // The table shows live Song references, so an edit changes the object the cells read
        // from without changing the list itself; refresh forces the cells to re-render.
        table.refresh();
        updateStatus();
        showDetails(table.getSelectionModel().getSelectedItem());
    }

    private void rebuildFilterChoices() {
        String selectedArtist = artistFilter.getValue();
        String selectedAlbum = albumFilter.getValue();
        Genre selectedGenre = genreFilter.getValue();

        artistFilter.getItems().setAll(ANY_ARTIST);
        artistFilter.getItems().addAll(library.distinctArtists());
        albumFilter.getItems().setAll(ANY_ALBUM);
        albumFilter.getItems().addAll(library.distinctAlbums());

        genreFilter.getItems().setAll(ANY_GENRE);
        genreFilter.getItems().addAll(library.genresInUse());

        artistFilter.setValue(artistFilter.getItems().contains(selectedArtist) ? selectedArtist : ANY_ARTIST);
        albumFilter.setValue(albumFilter.getItems().contains(selectedAlbum) ? selectedAlbum : ANY_ALBUM);
        genreFilter.setValue(genreFilter.getItems().contains(selectedGenre) ? selectedGenre : ANY_GENRE);
    }

    private void applyFilters() {
        String query = searchField.getText();
        String artist = artistFilter.getValue();
        String album = albumFilter.getValue();
        Genre genre = genreFilter.getValue();
        boolean onlyFavorites = favoritesOnly.isSelected();

        // The library owns the matching rules; the view only combines them.
        List<Song> textMatches = library.search(query);
        List<Song> fieldMatches = library.filter(
                ANY_ARTIST.equals(artist) ? null : artist,
                genre,
                ANY_ALBUM.equals(album) ? null : album);

        filteredList.setPredicate(song -> textMatches.contains(song)
                && fieldMatches.contains(song)
                && (!onlyFavorites || song.isFavorite()));

        updateStatus();
    }

    private void save() {
        try {
            repository.saveAll(library.all());
        } catch (PersistenceException e) {
            LOG.warning("Could not save the library: " + e.getMessage());
            PixelDialog.error(window(), "SAVE FAILED",
                    e.getMessage() + "\n\nChanges are kept in memory but were not written to disk.");
        }
    }

    // ------------------------------------------------------------------
    // Details panel
    // ------------------------------------------------------------------

    private void showDetails(Song song) {
        populatingDetails = true;
        try {
            if (song == null) {
                detailTitle.setText("NO SONG SELECTED");
                detailArtist.setText("");
                detailMeta.setText("");
                ratingSlider.setValue(0);
                detailRating.setRating(0);
                ratingValue.setText("0");
                ratingSlider.setDisable(true);
                favoriteToggle.setSelected(false);
                favoriteToggle.setDisable(true);
                showCover(null);
                return;
            }
            detailTitle.setText(song.getTitle());
            detailArtist.setText(song.getArtist());
            detailMeta.setText(String.join("\n",
                    song.getAlbum().isBlank() ? "NO ALBUM" : song.getAlbum(),
                    song.getGenre().displayName()
                            + (song.getYear() == Song.UNKNOWN_YEAR ? "" : "  -  " + song.getYear()),
                    "LENGTH " + formatDuration(song.getDuration()),
                    "PLAYS  " + song.getPlayCount(),
                    // Downloaded files often carry very long names, which in a fixed-width pixel
                    // font would push the panel to a dozen lines.
                    ellipsize(song.getFilePath().getFileName().toString(), 34)));
            ratingSlider.setDisable(false);
            ratingSlider.setValue(song.getRating());
            detailRating.setRating(song.getRating());
            ratingValue.setText(String.valueOf(song.getRating()));
            favoriteToggle.setDisable(false);
            favoriteToggle.setSelected(song.isFavorite());
            showCover(song.getCoverPath());
        } finally {
            populatingDetails = false;
        }
    }

    /**
     * Shows the cover art, or a labelled placeholder when there is none.
     *
     * <p>Album art is square, so the frame is square and the image is <strong>centre-cropped</strong>
     * to fill it. Fitting the whole image inside instead would letterbox anything that is not
     * already square, leaving bands of empty frame around it.
     *
     * <p>A missing or unreadable cover is never an error: the application must stay usable with
     * no artwork present at all.
     *
     * @param coverPath the cover image, or {@code null}
     */
    private void showCover(Path coverPath) {
        coverHolder.getChildren().clear();

        if (coverPath != null && Files.isReadable(coverPath)) {
            Image image = new Image(coverPath.toUri().toString(),
                    COVER_DECODE_SIZE, COVER_DECODE_SIZE, true, true);
            if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0) {
                coverImage.setImage(image);
                coverImage.setViewport(centeredSquare(image.getWidth(), image.getHeight()));
                coverHolder.getChildren().add(coverImage);
                return;
            }
            LOG.warning("Could not read the cover image at " + coverPath + " - using a placeholder");
        }

        Label placeholder = new Label("NO COVER");
        placeholder.getStyleClass().add("cover-placeholder-label");
        StackPane placeholderPane = new StackPane(placeholder);
        placeholderPane.getStyleClass().add("cover-placeholder");
        // Fills the frame's inner area, leaving the frame border visible around it.
        placeholderPane.maxWidthProperty().bind(coverImage.fitWidthProperty());
        placeholderPane.maxHeightProperty().bind(coverImage.fitHeightProperty());
        placeholderPane.minWidthProperty().bind(coverImage.fitWidthProperty());
        placeholderPane.minHeightProperty().bind(coverImage.fitHeightProperty());
        coverHolder.getChildren().add(placeholderPane);
    }

    /**
     * Returns the largest square that fits inside an image, centred on it.
     *
     * <p>A wide image loses equal slices from the left and right, a tall one from the top and
     * bottom, which keeps the middle of the artwork - where the interesting part almost always
     * is - in view.
     *
     * @param width  image width in pixels
     * @param height image height in pixels
     * @return the crop rectangle
     */
    static Rectangle2D centeredSquare(double width, double height) {
        double side = Math.min(width, height);
        return new Rectangle2D((width - side) / 2, (height - side) / 2, side, side);
    }

    private void updateStatus() {
        int shown = filteredList.size();
        int total = library.size();
        String counts = shown == total
                ? total + (total == 1 ? " SONG" : " SONGS")
                : shown + " OF " + total + " SHOWN";
        // The full storage path is far too wide in a fixed-width pixel font, so the status bar
        // shows the tail of it and the whole path lives in the tooltip.
        String location = repository.storageLocation().toString();
        statusLabel.setText(counts + "   -   " + ellipsizeStart(location, 46));
        statusLabel.setTooltip(new Tooltip(location));
    }

    /**
     * Shortens a string from the end, keeping the beginning.
     *
     * @param text  the text to shorten
     * @param limit the maximum length including the ellipsis
     * @return the text, shortened if it was longer than the limit
     */
    static String ellipsize(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, limit - 3)) + "...";
    }

    /**
     * Shortens a string from the front, keeping the end. Used for file paths, where the last
     * few segments identify the file and the first few rarely matter.
     *
     * @param text  the text to shorten
     * @param limit the maximum length including the ellipsis
     * @return the text, shortened if it was longer than the limit
     */
    static String ellipsizeStart(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        return "..." + text.substring(text.length() - Math.max(0, limit - 3));
    }

    private void select(Song song) {
        table.getSelectionModel().select(song);
        table.scrollTo(song);
    }

    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }

    /**
     * Formats a playing time as minutes and seconds.
     *
     * @param duration the time to format
     * @return {@code "--:--"} when unknown, otherwise {@code "m:ss"}
     */
    static String formatDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "--:--";
        }
        long totalSeconds = duration.toSeconds();
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** Renders the null entry in the genre filter as "ALL GENRES". */
    private static final class GenreCell extends javafx.scene.control.ListCell<Genre> {
        @Override
        protected void updateItem(Genre genre, boolean empty) {
            super.updateItem(genre, empty);
            setText(empty ? "" : (genre == null ? "ALL GENRES" : genre.displayName()));
        }
    }
}
