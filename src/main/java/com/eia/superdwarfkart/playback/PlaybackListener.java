package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.model.Song;

/**
 * Notified when the {@link Player}'s mode or current song changes.
 *
 * <p>Deliberately a plain callback rather than a JavaFX property: {@code playback/} must not
 * depend on the presentation layer. {@code AppState} implements this and republishes it as
 * observable properties for the windows to bind to.
 */
@FunctionalInterface
public interface PlaybackListener {

    /**
     * Called after the player's state has changed.
     *
     * @param mode    the active mode
     * @param current the song now current, or {@code null} if there is none
     */
    void playbackChanged(PlaybackMode mode, Song current);
}
