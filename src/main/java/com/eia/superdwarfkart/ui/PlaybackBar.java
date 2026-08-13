package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.ds.StepCounter;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import com.eia.superdwarfkart.playback.PlaybackMode;
import com.eia.superdwarfkart.playback.Player;
import com.eia.superdwarfkart.playback.ShuffleMode;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The mode selector and the transport controls.
 *
 * <p>Choosing a mode here replaces the {@link PlaybackMode} object inside the {@link Player} and
 * nothing else - the player does not learn which mode it now holds, and neither does this bar
 * beyond building the right one. Everything that follows from the choice, including whether the
 * previous control works, is read back off the interface.
 *
 * <p><strong>Previous is disabled in arrival order rather than allowed to fail.</strong> A queue
 * cannot go backwards; the control says so, with a tooltip that explains why, instead of
 * throwing at whoever presses it.
 *
 * <p>Everything to do with sound goes through {@link PlaybackEngine}: this bar starts and stops
 * playback and asks where it has reached, and never touches an audio source directly.
 */
public class PlaybackBar extends VBox {

    /** Characters of the now-playing line before it is shortened. */
    private static final int NOW_PLAYING_LIMIT = 46;

    /** Characters of the up-next line before it is shortened. */
    private static final int UP_NEXT_LIMIT = 34;

    /** Characters of the status line before it is shortened. */
    private static final int STATUS_LIMIT = 30;

    /**
     * Fixed width for the play control, in pixels.
     *
     * <p>Its two captions are different lengths, and in a fixed-width font a button that resizes
     * itself shoves the whole transport sideways every time playback is paused.
     */
    private static final double PLAY_BUTTON_WIDTH = 116;

    /**
     * How often the position readout is refreshed, in nanoseconds.
     *
     * <p>Ten times a second. The seek bar and the clock move by less than a pixel and less than a
     * digit between frames, so repainting them sixty times a second is work nobody can see.
     */
    private static final long CLOCK_INTERVAL_NANOS = 100_000_000L;

    private final Player player;
    private final PlaybackEngine engine;

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final Map<ModeId, ToggleButton> modeButtons = new EnumMap<>(ModeId.class);

    private final Button previousButton = new Button("<< PREV");
    private final Button playButton = new Button();
    private final Button nextButton = new Button("NEXT >>");
    private final HBox previousHolder = new HBox(previousButton);

    private final Label nowPlaying = new Label();
    private final Label upNext = new Label();
    private final Label status = new Label();

    private final Slider seekBar = new Slider(0, 1, 0);
    private final Label elapsed = new Label(formatTime(Duration.ZERO));
    private final Label total = new Label(formatTime(Duration.ZERO));
    private final Slider volume = new Slider(0, 1, 1);

    /**
     * Set while the clock is writing to the seek bar, so its own updates are not mistaken for the
     * user dragging it and turned straight back into a seek.
     */
    private boolean syncingSeekBar;

    private AnimationTimer clock;
    private long lastClockNanos;

    /** Builders for the three modes, so selecting one constructs it fresh from the library. */
    private final Map<ModeId, Supplier<PlaybackMode>> factories = new EnumMap<>(ModeId.class);

    /**
     * Builds the bar.
     *
     * @param player  the player to drive; must not be {@code null}
     * @param engine  the audio the transport controls; must not be {@code null}
     * @param counter handed to every mode this bar builds, so a mode selected after startup is
     *                instrumented exactly like the one the application opened with; must not be
     *                {@code null}, pass {@link StepCounter#NO_OP} to disable
     */
    public PlaybackBar(Player player, PlaybackEngine engine, StepCounter counter) {
        super(10);
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(counter, "counter must not be null; use StepCounter.NO_OP");

        factories.put(ModeId.SHUFFLE, () -> new ShuffleMode(new Random(), counter));
        factories.put(ModeId.ARRIVAL_ORDER, () -> new ArrivalOrderMode(counter));
        factories.put(ModeId.ALPHABETICAL, () -> new AlphabeticalMode(counter));

        getStyleClass().add("playback-bar");
        setPadding(new Insets(12, 16, 12, 16));

        getChildren().addAll(buildTopRow(), buildNowPlayingRow(), buildProgressRow());

        player.addListener((mode, song) -> refresh());
        refresh();
        startClock();
    }

    private HBox buildTopRow() {
        Label modeCaption = new Label("MODE");
        modeCaption.getStyleClass().add("bar-caption");

        HBox modes = new HBox(6);
        modes.setAlignment(Pos.CENTER_LEFT);
        for (ModeId id : ModeId.values()) {
            modes.getChildren().add(modeButton(id));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        previousButton.setOnAction(e -> player.previous());
        nextButton.setOnAction(e -> player.next());
        playButton.setOnAction(e -> engine.toggle());
        playButton.getStyleClass().add("play-button");
        playButton.setMinWidth(PLAY_BUTTON_WIDTH);
        playButton.setPrefWidth(PLAY_BUTTON_WIDTH);
        // Fixed text, so it is built once rather than on every refresh.
        playButton.setTooltip(new Tooltip("Play or pause\nSpace"));
        // A disabled node receives no mouse events, so the tooltip explaining why goes on the
        // holder around it. Without this the control is dead and unexplained.
        previousHolder.setAlignment(Pos.CENTER);

        HBox transport = new HBox(8, previousHolder, playButton, nextButton);
        transport.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(14, modeCaption, modes, spacer, transport, buildVolume());
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Builds the volume control.
     *
     * <p>Committed continuously, unlike the rating slider: setting a gain is one call to the sound
     * card, where committing a rating rebuilds the table and writes the library to disk.
     *
     * @return the volume control and its caption
     */
    private HBox buildVolume() {
        Label caption = new Label("VOL");
        caption.getStyleClass().add("bar-caption");

        volume.setPrefWidth(90);
        volume.setValue(engine.audio().volume());
        // One tooltip whose text is rewritten, not a new one per event: a drag fires per pixel.
        Tooltip readout = new Tooltip();
        volume.setTooltip(readout);
        volume.valueProperty().addListener((observable, was, now) -> {
            engine.audio().setVolume(now.doubleValue());
            readout.setText("Volume " + Math.round(now.doubleValue() * 100) + "%");
        });
        readout.setText("Volume " + Math.round(volume.getValue() * 100) + "%");

        HBox row = new HBox(8, caption, volume);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Switches to the next mode in the list, wrapping round.
     *
     * <p>The keyboard shortcut calls this rather than building a mode of its own: the factories
     * live here, and they are what hand each new mode the step counter. A shortcut that
     * constructed its own would quietly produce an uninstrumented mode whose operations never
     * appeared on the complexity panel.
     */
    public void cycleMode() {
        ModeId[] all = ModeId.values();
        ModeId next = all[(player.mode().id().ordinal() + 1) % all.length];
        player.setMode(factories.get(next).get());
    }

    /**
     * Builds one mode toggle.
     *
     * @param id the mode it selects
     * @return the toggle button
     */
    private ToggleButton modeButton(ModeId id) {
        ToggleButton button = new ToggleButton(id.displayName().toUpperCase());
        button.setToggleGroup(modeGroup);
        button.getStyleClass().add("mode-button");
        button.setTooltip(new Tooltip(id.displayName() + "\nBacked by " + id.structureName()
                + (id.supportsPrevious() ? "" : "\nFIFO - no going back.")
                + "\n\nTab cycles the mode."));
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                // Clicking the active mode must not deselect it and leave nothing chosen.
                button.setSelected(true);
                return;
            }
            player.setMode(factories.get(id).get());
        });
        modeButtons.put(id, button);
        return button;
    }

    private HBox buildNowPlayingRow() {
        Label nowCaption = new Label("NOW PLAYING");
        nowCaption.getStyleClass().add("bar-caption");
        nowPlaying.getStyleClass().add("now-playing");

        Label nextCaption = new Label("UP NEXT");
        nextCaption.getStyleClass().add("bar-caption");
        upNext.getStyleClass().add("up-next");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, nowCaption, nowPlaying, spacer, nextCaption, upNext);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Builds the position readout.
     *
     * <p>The bar is bound to real playback time, read back from the sound card rather than counted
     * up per frame, so it cannot drift away from what is audible.
     *
     * <p><strong>A drag commits once, at the end.</strong> A seek in a compressed stream decodes
     * from the start of the track to find the instant asked for; doing that per pixel of travel
     * would lock the interface solid for the length of the drag.
     *
     * @return the progress row
     */
    private HBox buildProgressRow() {
        elapsed.getStyleClass().add("time-readout");
        total.getStyleClass().add("time-readout");
        status.getStyleClass().add("playback-status");

        seekBar.getStyleClass().add("seek-bar");
        HBox.setHgrow(seekBar, Priority.ALWAYS);
        seekBar.setMaxWidth(Double.MAX_VALUE);

        seekBar.valueProperty().addListener((observable, was, now) -> {
            if (syncingSeekBar) {
                return;
            }
            // While dragging, only the cheap readout follows the thumb.
            elapsed.setText(formatTime(Duration.ofMillis(Math.round(now.doubleValue() * 1000))));
            if (!seekBar.isValueChanging()) {
                // Not a drag: a click on the track, or an arrow key. Those want an immediate jump.
                commitSeek();
            }
        });
        seekBar.valueChangingProperty().addListener((observable, was, changing) -> {
            if (was && !changing) {
                commitSeek();
            }
        });

        HBox row = new HBox(10, elapsed, seekBar, total, status);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void commitSeek() {
        engine.seek(Duration.ofMillis(Math.round(seekBar.getValue() * 1000)));
    }

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    /**
     * Starts the position readout.
     *
     * <p>Public and idempotent because the bar is stopped whenever the whole window goes away -
     * collapsing to the companion strip hides it - and has to be picked up again when it comes
     * back.
     */
    public final void startClock() {
        if (clock != null) {
            return;
        }
        clock = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastClockNanos < CLOCK_INTERVAL_NANOS) {
                    return;
                }
                lastClockNanos = now;
                updateClock();
            }
        };
        clock.start();
    }

    /**
     * Stops the position readout.
     *
     * <p>Called on shutdown, so nothing is left polling a closed audio source.
     */
    public void stopClock() {
        if (clock != null) {
            clock.stop();
            clock = null;
        }
    }

    /**
     * Brings the position readout, the play control and the status line up to date.
     *
     * <p>Polled rather than pushed. The audio source changes state on its own thread when a track
     * ends, and polling four cheap reads ten times a second is a great deal simpler - and safer -
     * than marshalling a notification across that boundary for something the eye reads as
     * continuous anyway.
     */
    private void updateClock() {
        boolean playing = engine.isPlaying();
        playButton.setText(playing ? "|| PAUSE" : "> PLAY");
        playButton.setDisable(player.current() == null);

        Duration length = engine.duration();
        Duration at = engine.position();
        total.setText(formatTime(length));

        boolean seekable = !length.isZero() && engine.isLoaded();
        seekBar.setDisable(!seekable);
        if (seekable && !seekBar.isValueChanging()) {
            syncingSeekBar = true;
            seekBar.setMax(length.toMillis() / 1000d);
            seekBar.setValue(Math.min(at.toMillis() / 1000d, seekBar.getMax()));
            syncingSeekBar = false;
            elapsed.setText(formatTime(at));
        } else if (!seekable) {
            elapsed.setText(formatTime(at));
        }

        status.setText(statusText(playing));
    }

    /**
     * @param playing whether audio is being rendered
     * @return the short status caption
     */
    private String statusText(boolean playing) {
        String problem = engine.failure();
        if (problem != null) {
            return LibraryView.ellipsize(problem.toUpperCase(), STATUS_LIMIT);
        }
        if (player.current() == null) {
            return "NO SONG";
        }
        return playing ? "PLAYING" : "PAUSED";
    }

    /**
     * @param value the time to render
     * @return the time as {@code m:ss}, or {@code h:mm:ss} for anything over an hour
     */
    static String formatTime(Duration value) {
        if (value == null || value.isNegative()) {
            return "0:00";
        }
        long seconds = value.toSeconds();
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, remainder)
                : String.format("%d:%02d", minutes, remainder);
    }

    // ------------------------------------------------------------------
    // Mode and song
    // ------------------------------------------------------------------

    /**
     * Brings the bar back in step with the player.
     *
     * <p>Reads capability off the mode rather than checking which mode it is: that is the same
     * polymorphism the player relies on, applied to the controls.
     */
    public final void refresh() {
        PlaybackMode mode = player.mode();

        ToggleButton active = modeButtons.get(mode.id());
        if (active != null && !active.isSelected()) {
            active.setSelected(true);
        }

        boolean canGoBack = player.canGoPrevious();
        previousButton.setDisable(!canGoBack);
        Tooltip.install(previousHolder, new Tooltip(mode.supportsPrevious()
                ? "Step back through " + mode.structureName() + "\nLeft arrow"
                : "FIFO - no going back."));

        nextButton.setDisable(!player.canGoNext());
        nextButton.setTooltip(new Tooltip("Step forward through " + mode.structureName()
                + "\nRight arrow"));

        Song current = player.current();
        nowPlaying.setText(current == null
                ? "-"
                : LibraryView.ellipsize(current.getTitle() + " - " + current.getArtist(),
                        NOW_PLAYING_LIMIT));

        Song upcoming = player.peekNext();
        upNext.setText(upcoming == null
                ? "-"
                : LibraryView.ellipsize(upcoming.getTitle(), UP_NEXT_LIMIT));

        updateClock();
    }
}
