package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.audio.AudioMetadata;
import com.eia.superdwarfkart.model.Genre;
import com.eia.superdwarfkart.model.Song;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;
import java.util.Optional;

/**
 * The form used to add a new song or edit an existing one.
 *
 * <p>The rating control here is the 0 to 100 scale the assignment asks for. It cannot produce an
 * invalid value, but {@link Song#setRating(int)} validates anyway: the model defends itself
 * regardless of which user interface is driving it.
 */
public final class SongDialog {

    /** Extensions offered when browsing for audio. */
    private static final FileChooser.ExtensionFilter AUDIO_FILTER =
            new FileChooser.ExtensionFilter("Audio files (*.mp3, *.wav)", "*.mp3", "*.wav", "*.MP3", "*.WAV");

    /** Extensions offered when browsing for cover art. */
    private static final FileChooser.ExtensionFilter IMAGE_FILTER =
            new FileChooser.ExtensionFilter("Images (*.png, *.jpg)", "*.png", "*.jpg", "*.jpeg", "*.gif");

    /**
     * Opens the dialog to create a new song.
     *
     * @param owner the window to centre on
     * @return the new song, or empty if the user cancelled
     */
    public static Optional<Song> showAdd(Window owner) {
        Optional<Path> file = browseForAudio(owner, null);
        if (file.isEmpty()) {
            return Optional.empty();
        }
        Path audio = file.get();

        // Guess a title from the filename so the common case needs no typing.
        String guessedTitle = stripExtension(audio.getFileName().toString());
        Song draft = new Song(guessedTitle.isBlank() ? "Untitled" : guessedTitle, "Unknown Artist", audio);
        draft.setDuration(AudioMetadata.readDuration(audio));

        Form form = new Form(draft);
        return form.show(owner, "ADD SONG") ? Optional.of(draft) : Optional.empty();
    }

    /**
     * Opens the dialog to edit an existing song. The song is modified in place when accepted.
     *
     * @param owner the window to centre on
     * @param song  the song to edit
     * @return {@code true} if the user accepted the changes
     */
    public static boolean showEdit(Window owner, Song song) {
        return new Form(song).show(owner, "EDIT SONG");
    }

    /**
     * Opens a file chooser for an audio file.
     *
     * @param owner   the window to centre on
     * @param initial the file to start from, or {@code null}
     * @return the chosen file, or empty if cancelled
     */
    static Optional<Path> browseForAudio(Window owner, Path initial) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an audio file");
        chooser.getExtensionFilters().add(AUDIO_FILTER);
        applyInitialDirectory(chooser, initial);
        var chosen = chooser.showOpenDialog(owner);
        return Optional.ofNullable(chosen).map(java.io.File::toPath);
    }

    private static Optional<Path> browseForCover(Window owner, Path initial) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose cover art");
        chooser.getExtensionFilters().add(IMAGE_FILTER);
        applyInitialDirectory(chooser, initial);
        var chosen = chooser.showOpenDialog(owner);
        return Optional.ofNullable(chosen).map(java.io.File::toPath);
    }

    private static void applyInitialDirectory(FileChooser chooser, Path initial) {
        if (initial == null) {
            return;
        }
        Path parent = initial.toAbsolutePath().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            chooser.setInitialDirectory(parent.toFile());
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /** The form itself, kept separate so add and edit share exactly one implementation. */
    private static final class Form {

        private final Song song;

        private final TextField titleField = new TextField();
        private final TextField artistField = new TextField();
        private final TextField albumField = new TextField();
        private final ComboBox<Genre> genreBox = new ComboBox<>();
        private final Spinner<Integer> yearSpinner = new Spinner<>();
        private final Slider ratingSlider = new Slider(Song.MIN_RATING, Song.MAX_RATING, 0);
        private final Label ratingValue = new Label();
        private final CheckBox favoriteBox = new CheckBox("FAVORITE");
        private final TextField fileField = new TextField();
        private final TextField coverField = new TextField();

        private Form(Song song) {
            this.song = song;
        }

        private boolean show(Window owner, String title) {
            PixelDialog dialog = new PixelDialog(owner, title);
            dialog.setContent(buildContent(owner));
            dialog.setAcceptText("SAVE");
            dialog.acceptDisabledProperty().bind(
                    titleField.textProperty().isEmpty()
                            .or(artistField.textProperty().isEmpty())
                            .or(fileField.textProperty().isEmpty()));

            load();

            while (true) {
                if (!dialog.showAndWait()) {
                    return false;
                }
                try {
                    apply();
                    return true;
                } catch (RuntimeException e) {
                    // The model rejected something. Show why and reopen rather than losing the
                    // user's typing.
                    PixelDialog.error(owner, "INVALID SONG",
                            "That song could not be saved:\n\n" + e.getMessage());
                }
            }
        }

        private GridPane buildContent(Window owner) {
            genreBox.getItems().setAll(Genre.values());

            int maxYear = Year.now().getValue() + 1;
            yearSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxYear, 0));
            yearSpinner.setEditable(true);
            yearSpinner.setPrefWidth(120);

            ratingSlider.setShowTickMarks(true);
            ratingSlider.setShowTickLabels(true);
            ratingSlider.setMajorTickUnit(25);
            ratingSlider.setBlockIncrement(5);
            ratingSlider.valueProperty().addListener(
                    (obs, was, now) -> ratingValue.setText(String.valueOf(now.intValue())));
            ratingValue.getStyleClass().add("rating-value");

            fileField.setEditable(false);
            Button browseAudio = new Button("BROWSE");
            browseAudio.setOnAction(e -> browseForAudio(owner, currentPath(fileField))
                    .ifPresent(p -> fileField.setText(p.toString())));
            // A streamed song has no file to browse for. The row still shows its URI, because that
            // is the one field explaining why the rest of the dialog cannot change where it plays
            // from - a disabled button with no reason beside it reads as a broken dialog.
            if (song != null && song.isSpotify()) {
                browseAudio.setDisable(true);
                browseAudio.setTooltip(
                        new javafx.scene.control.Tooltip("Streamed from Spotify - there is no file"));
            }

            coverField.setEditable(false);
            coverField.setPromptText("OPTIONAL");
            Button browseCover = new Button("BROWSE");
            browseCover.setOnAction(e -> browseForCover(owner, currentPath(coverField))
                    .ifPresent(p -> coverField.setText(p.toString())));
            Button clearCover = new Button("CLEAR");
            clearCover.setOnAction(e -> coverField.clear());

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(10);
            grid.setPadding(new Insets(16));

            int row = 0;
            grid.addRow(row++, new Label("TITLE"), titleField);
            grid.addRow(row++, new Label("ARTIST"), artistField);
            grid.addRow(row++, new Label("ALBUM"), albumField);
            grid.addRow(row++, new Label("GENRE"), genreBox);
            grid.addRow(row++, new Label("YEAR"), yearSpinner);

            HBox ratingRow = new HBox(10, ratingSlider, ratingValue);
            grid.addRow(row++, new Label("RATING"), ratingRow);
            grid.addRow(row++, new Label(""), favoriteBox);

            HBox fileRow = new HBox(8, fileField, browseAudio);
            HBox.setHgrow(fileField, Priority.ALWAYS);
            grid.addRow(row++, new Label("FILE"), fileRow);

            HBox coverRow = new HBox(8, coverField, browseCover, clearCover);
            HBox.setHgrow(coverField, Priority.ALWAYS);
            grid.addRow(row, new Label("COVER"), coverRow);

            titleField.setPrefWidth(360);
            grid.getStyleClass().add("song-form");
            return grid;
        }

        private static Path currentPath(TextField field) {
            String text = field.getText();
            return text == null || text.isBlank() ? null : Path.of(text);
        }

        /** Copies the song into the controls. */
        private void load() {
            titleField.setText(song.getTitle());
            artistField.setText(song.getArtist());
            albumField.setText(song.getAlbum());
            genreBox.setValue(song.getGenre());
            yearSpinner.getValueFactory().setValue(song.getYear());
            ratingSlider.setValue(song.getRating());
            ratingValue.setText(String.valueOf(song.getRating()));
            favoriteBox.setSelected(song.isFavorite());
            // The locator rather than the path: a streamed song has no file and would throw here.
            fileField.setText(song.locator());
            coverField.setText(song.getCoverPath() == null ? "" : song.getCoverPath().toString());
        }

        /**
         * Copies the controls back into the song.
         *
         * @throws RuntimeException if the model rejects a value
         */
        private void apply() {
            song.setTitle(titleField.getText());
            song.setArtist(artistField.getText());
            song.setAlbum(albumField.getText());
            song.setGenre(genreBox.getValue());
            song.setYear(yearSpinner.getValue() == null ? 0 : yearSpinner.getValue());
            song.setRating((int) Math.round(ratingSlider.getValue()));
            song.setFavorite(favoriteBox.isSelected());
            song.setCoverPath(coverField.getText().isBlank() ? null : Path.of(coverField.getText()));

            // Where a streamed song plays from is not the user's to edit, and there is no file to
            // read a duration out of - Spotify already told us how long it is.
            if (song.isSpotify()) {
                return;
            }

            Path audio = Path.of(fileField.getText());
            boolean fileChanged = !audio.equals(song.getFilePath());
            song.setFilePath(audio);
            if (fileChanged || song.getDuration().equals(Duration.ZERO)) {
                song.setDuration(AudioMetadata.readDuration(audio));
            }
        }
    }

    private SongDialog() {
        throw new AssertionError("SongDialog is a factory holder and must not be instantiated");
    }
}
