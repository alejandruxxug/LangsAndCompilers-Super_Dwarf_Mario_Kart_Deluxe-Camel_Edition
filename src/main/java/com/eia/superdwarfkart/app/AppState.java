package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.game.SpeedClass;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Racer;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.playback.PlaybackListener;
import com.eia.superdwarfkart.playback.PlaybackMode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The state both windows share.
 *
 * <p>One observable object rather than state duplicated per window: changing the racer in the
 * fullscreen window has to change the sprite riding the disk in the mini player at the same
 * instant, and switching mode has to restyle both. Everything binds here, and nothing owns a
 * private copy.
 *
 * <p>This is also the bridge across the presentation boundary. {@code playback/} may not depend
 * on JavaFX, so {@link com.eia.superdwarfkart.playback.Player} publishes plain callbacks and
 * this class - which lives in {@code app/} and may - republishes them as properties.
 *
 * <p>The active mood joins these properties at its milestone; the binding path is the same for all
 * of them.
 */
public class AppState implements PlaybackListener {

    private final ObjectProperty<Song> currentSong = new SimpleObjectProperty<>(this, "currentSong");
    private final ObjectProperty<ModeId> modeId = new SimpleObjectProperty<>(this, "modeId");
    private final ObjectProperty<Racer> racer =
            new SimpleObjectProperty<>(this, "racer", Racer.defaultRacer());
    private final ObjectProperty<SpeedClass> speedClass =
            new SimpleObjectProperty<>(this, "speedClass", SpeedClass.defaultClass());
    private final ObjectProperty<Mood> mood =
            new SimpleObjectProperty<>(this, "mood", Moods.defaultMood());
    private final BooleanProperty reduceMotion =
            new SimpleBooleanProperty(this, "reduceMotion", false);

    /** @return the song currently playing, or {@code null} */
    public ReadOnlyObjectProperty<Song> currentSongProperty() {
        return currentSong;
    }

    /** @return the song currently playing, or {@code null} */
    public Song getCurrentSong() {
        return currentSong.get();
    }

    /** @return which playback mode is active */
    public ReadOnlyObjectProperty<ModeId> modeIdProperty() {
        return modeId;
    }

    /** @return which playback mode is active */
    public ModeId getModeId() {
        return modeId.get();
    }

    /** @return the racer the user has selected */
    public ObjectProperty<Racer> racerProperty() {
        return racer;
    }

    /** @return the racer the user has selected */
    public Racer getRacer() {
        return racer.get();
    }

    /**
     * Sets the selected racer.
     *
     * @param selected the racer to use; must not be {@code null}
     */
    public void setRacer(Racer selected) {
        racer.set(selected == null ? Racer.defaultRacer() : selected);
    }

    /**
     * The speed class the runner is driven at.
     *
     * <p>Shared rather than owned by the runner, for the same reason the racer is: the class
     * changes which course a song generates and therefore which high score applies to it, so the
     * library's rank badge and the game have to be reading the same value.
     *
     * @return the selected speed class
     */
    public ObjectProperty<SpeedClass> speedClassProperty() {
        return speedClass;
    }

    /** @return the selected speed class */
    public SpeedClass getSpeedClass() {
        return speedClass.get();
    }

    /**
     * Sets the speed class the runner is driven at.
     *
     * @param selected the class to use; {@code null} restores the default
     */
    public void setSpeedClass(SpeedClass selected) {
        speedClass.set(selected == null ? SpeedClass.defaultClass() : selected);
    }

    /**
     * The look the whole application draws with.
     *
     * <p>Shared for the same reason the racer is, and more so: a mood is not a property of a
     * window. Both windows are bound here, so switching mood restyles the companion strip and the
     * fullscreen stage in the same instant rather than leaving whichever one is hidden to catch up
     * when it is next shown.
     *
     * @return the active mood
     */
    public ObjectProperty<Mood> moodProperty() {
        return mood;
    }

    /** @return the active mood */
    public Mood getMood() {
        return mood.get();
    }

    /**
     * Sets the active mood.
     *
     * @param selected the mood to use; {@code null} restores the default
     */
    public void setMood(Mood selected) {
        mood.set(selected == null ? Moods.defaultMood() : selected);
    }

    /**
     * Whether every kind of motion the look produces is suppressed.
     *
     * <p><strong>Not a style setting.</strong> A fullscreen overlay flashing in a darkened classroom
     * is a genuine problem, and this application has several things that flash: the mood layers
     * scroll, a reactive mood modulates with the music, and the runner washes the whole screen on
     * every strong beat and pulses it three times on a bump. On a 120 BPM track the beat effects
     * fire at 2 Hz, which is inside the mood system's 3 Hz cap only because the cap happened to be
     * respected rather than because anything checked.
     *
     * <p>So the switch reaches all of it: {@code MoodOverlayRenderer} for the layers and the
     * reactivity, and {@code RunnerView} for the beat zoom, the washes and the event flashes. It is
     * shared here rather than owned by either, because it is a property of the person watching
     * rather than of a window.
     *
     * @return whether motion is suppressed
     */
    public BooleanProperty reduceMotionProperty() {
        return reduceMotion;
    }

    /** @return whether motion is suppressed */
    public boolean isReduceMotion() {
        return reduceMotion.get();
    }

    /**
     * Suppresses or restores motion.
     *
     * @param on whether to suppress it
     */
    public void setReduceMotion(boolean on) {
        reduceMotion.set(on);
    }

    /**
     * Republishes a change from the player as property changes.
     *
     * @param mode    the active mode
     * @param current the song now current, or {@code null}
     */
    @Override
    public void playbackChanged(PlaybackMode mode, Song current) {
        modeId.set(mode.id());
        currentSong.set(current);
    }
}
