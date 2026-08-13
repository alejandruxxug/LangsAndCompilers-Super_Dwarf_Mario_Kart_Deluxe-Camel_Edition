package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.Library;
import com.eia.superdwarfkart.model.LibraryStatistics;
import com.eia.superdwarfkart.model.PlayHistory;
import com.eia.superdwarfkart.model.Song;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What has been played, and what the library adds up to.
 *
 * <p>Two things that belong on one page: the session's running order, newest first, and the totals
 * over the whole library. The history says what just happened and the statistics say what has been
 * happening, and neither is worth a destination of its own.
 *
 * <p>Recomputed when the page is shown rather than kept in step with every change. Both halves are
 * derived values over a library that is edited by hand, so a listener per song would be a great
 * deal of machinery to keep a page current that nobody is looking at.
 */
public class HistoryView extends ScrollPane {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final Library library;
    private final PlayHistory history;
    private final VBox content = new VBox(18);

    private Consumer<Song> onSongActivated = song -> { };

    /**
     * Builds the page.
     *
     * @param library the library to summarise; must not be {@code null}
     * @param history the session's plays; must not be {@code null}
     */
    public HistoryView(Library library, PlayHistory history) {
        this.library = library;
        this.history = history;

        content.setPadding(new Insets(18));
        content.getStyleClass().add("history-content");
        setContent(content);
        setFitToWidth(true);
        getStyleClass().add("history-view");
        refresh();
    }

    /**
     * Sets what happens when a song in the history is clicked.
     *
     * @param action given the song to play; must not be {@code null}
     */
    public void setOnSongActivated(Consumer<Song> action) {
        this.onSongActivated = action == null ? song -> { } : action;
    }

    /** Recomputes both halves of the page. Called whenever it is shown. */
    public final void refresh() {
        // One pass over the library for the whole page, rather than one per section.
        LibraryStatistics stats = LibraryStatistics.of(library.all());
        content.getChildren().setAll(
                heading("HISTORY"),
                section("RECENTLY PLAYED", recentBody()),
                section("LIBRARY", statisticsBody(stats)),
                section("MOST PLAYED", topPlayedBody(stats)),
                section("TOP RATED", topRatedBody(stats)));
    }

    private Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-heading");
        return label;
    }

    private VBox section(String title, javafx.scene.Node body) {
        Label label = new Label(title);
        label.getStyleClass().add("panel-heading");
        VBox box = new VBox(8, label, body);
        box.getStyleClass().add("history-section");
        box.setPadding(new Insets(12));
        return box;
    }

    private javafx.scene.Node recentBody() {
        List<PlayHistory.Entry> recent = history.recent();
        if (recent.isEmpty()) {
            return caption("Nothing yet this session - press play.");
        }

        VBox rows = new VBox(2);
        for (PlayHistory.Entry entry : recent) {
            Song song = entry.song();

            Label time = new Label(CLOCK.format(entry.playedAt()));
            time.getStyleClass().add("history-time");
            time.setMinWidth(52);

            Button title = new Button(song.getTitle() + "  -  " + song.getArtist());
            title.getStyleClass().add("history-entry");
            title.setFocusTraversable(false);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTooltip(new Tooltip(song.getTitle() + "\n" + song.getArtist()));
            title.setOnAction(event -> onSongActivated.accept(song));
            HBox.setHgrow(title, Priority.ALWAYS);

            rows.getChildren().add(new HBox(10, time, title));
        }
        return rows;
    }

    private javafx.scene.Node statisticsBody(LibraryStatistics stats) {
        GridPane grid = grid();
        int row = 0;
        row = put(grid, row, "Songs", String.valueOf(stats.songCount()));
        row = put(grid, row, "Favorites", String.valueOf(stats.favoriteCount()));
        row = put(grid, row, "Played at least once",
                stats.playedCount() + " of " + stats.songCount());
        row = put(grid, row, "Total plays", String.valueOf(stats.totalPlays()));
        row = put(grid, row, "Library length", format(stats.totalDuration()));
        row = put(grid, row, "Time listened", format(stats.listenedTime()));
        row = put(grid, row, "Rated", stats.ratedCount() + " of " + stats.songCount());
        put(grid, row, "Average rating",
                stats.ratedCount() == 0 ? "-" : Math.round(stats.averageRating()) + " / 100");

        VBox box = new VBox(10, grid);
        Map<String, Integer> byArtist = stats.playsByArtist();
        if (!byArtist.isEmpty()) {
            GridPane artists = grid();
            int artistRow = 0;
            for (Map.Entry<String, Integer> entry : byArtist.entrySet()) {
                artistRow = put(artists, artistRow, entry.getKey(),
                        entry.getValue() + (entry.getValue() == 1 ? " play" : " plays"));
            }
            box.getChildren().addAll(caption("MOST PLAYED ARTISTS"), artists);
        }
        return box;
    }

    private javafx.scene.Node topPlayedBody(LibraryStatistics stats) {
        if (stats.topPlayed().isEmpty()) {
            return caption("No song has been played yet.");
        }
        GridPane grid = grid();
        int row = 0;
        for (Song song : stats.topPlayed()) {
            row = put(grid, row, song.getTitle(),
                    song.getPlayCount() + (song.getPlayCount() == 1 ? " play" : " plays"));
        }
        return grid;
    }

    private javafx.scene.Node topRatedBody(LibraryStatistics stats) {
        if (stats.topRated().isEmpty()) {
            return caption("No song has been rated yet.");
        }
        GridPane grid = grid();
        int row = 0;
        for (Song song : stats.topRated()) {
            row = put(grid, row, song.getTitle(), song.getRating() + " / 100");
        }
        return grid;
    }

    private int put(GridPane grid, int row, String name, String value) {
        Label caption = new Label(name);
        caption.getStyleClass().add("settings-caption");
        caption.setTooltip(new Tooltip(name));

        Label readout = new Label(value);
        readout.getStyleClass().add("settings-value");

        grid.add(caption, 0, row);
        grid.add(readout, 1, row);
        return row + 1;
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(7);
        ColumnConstraints names = new ColumnConstraints();
        names.setMinWidth(300);
        names.setPrefWidth(300);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(names, values);
        return grid;
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-caption");
        return label;
    }

    /** Formats a duration as hours and minutes, which is the scale these totals live at. */
    private static String format(Duration duration) {
        if (duration == null || duration.isZero()) {
            return "0m";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
