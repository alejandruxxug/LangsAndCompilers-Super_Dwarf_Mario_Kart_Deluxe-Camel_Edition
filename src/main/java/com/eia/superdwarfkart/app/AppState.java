package com.eia.superdwarfkart.app;

import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Racer;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.playback.PlaybackListener;
import com.eia.superdwarfkart.playback.PlaybackMode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
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
 * <p>Speed class and active mood join these properties at their milestones; the binding path is
 * the same for all of them.
 */
public class AppState implements PlaybackListener {

    private final ObjectProperty<Song> currentSong = new SimpleObjectProperty<>(this, "currentSong");
    private final ObjectProperty<ModeId> modeId = new SimpleObjectProperty<>(this, "modeId");
    private final ObjectProperty<Racer> racer =
            new SimpleObjectProperty<>(this, "racer", Racer.defaultRacer());

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
