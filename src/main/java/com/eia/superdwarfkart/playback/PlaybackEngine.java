package com.eia.superdwarfkart.playback;

import com.eia.superdwarfkart.audio.AudioException;
import com.eia.superdwarfkart.audio.AudioSource;
import com.eia.superdwarfkart.model.Song;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Keeps the speakers agreeing with the structure.
 *
 * <p>{@link Player} answers "which song is current" by walking a ring, a queue or a tree, and it
 * stays exactly that: navigation over a hand-written structure, with no idea that sound exists. An
 * {@link AudioSource} turns one file into sound and has no idea a running order exists. This class
 * is the one place the two meet - it listens to the player, loads whatever became current, and when
 * a track plays out it asks the player for the next one.
 *
 * <p>Keeping it separate is what lets the graded core be tested without a sound card, and what lets
 * the sound card be tested without a library.
 *
 * <p><strong>Playing is a state of this class, not of a button.</strong> {@code playWhenReady} is
 * what the user last asked for, and it survives a song change: pressing next while paused loads the
 * next song and stays paused, and a track ending while playing rolls straight into the next one.
 * Without it, "playing" would have to be re-derived from the audio source at every transition,
 * where it is momentarily false for reasons that have nothing to do with intent.
 *
 * <p>No {@code javafx} import appears here. End-of-track arrives on the playback thread, so the
 * hop back onto the interface thread is done by a {@link Consumer} of {@link Runnable} supplied by
 * the caller - {@code Platform::runLater} in the application, a direct call in the tests.
 */
public class PlaybackEngine implements PlaybackListener, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PlaybackEngine.class.getName());

    private final Player player;
    private final AudioSource audio;
    private final Consumer<Runnable> onUiThread;

    /** The song the audio source currently holds, which trails the player's during a load. */
    private Song loaded;

    /** What the user last asked for, as opposed to what the audio source happens to be doing. */
    private boolean playWhenReady;

    /** Whether this song's play has already been counted, so resuming does not count it twice. */
    private boolean counted;

    /** Why the current song will not play, or {@code null} when there is nothing wrong. */
    private String failure;

    private BiConsumer<Song, String> onFailure = (song, message) -> { };

    /**
     * Told when a play is counted, so a history can record it.
     *
     * <p>A callback rather than a history of its own: this class starts and stops audio, and what
     * anybody wants to do with the fact that a song began is not its business.
     */
    private Consumer<Song> onPlayCounted = song -> { };

    /** Told when the playhead is moved by hand. See {@link #setOnSeek}. */
    private Runnable onSeek = () -> { };

    /** Told when a track has played all the way out, before the running order moves. */
    private Runnable onTrackEnded = () -> { };

    /**
     * Wires a player to an audio source, running callbacks on the calling thread.
     *
     * @param player the running order to follow; must not be {@code null}
     * @param audio  the output to drive; must not be {@code null}
     */
    public PlaybackEngine(Player player, AudioSource audio) {
        this(player, audio, Runnable::run);
    }

    /**
     * Wires a player to an audio source.
     *
     * @param player     the running order to follow; must not be {@code null}
     * @param audio      the output to drive; must not be {@code null}
     * @param onUiThread how to get back onto the interface thread from the playback thread; must
     *                   not be {@code null}
     */
    public PlaybackEngine(Player player, AudioSource audio, Consumer<Runnable> onUiThread) {
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.audio = Objects.requireNonNull(audio, "audio must not be null");
        this.onUiThread = Objects.requireNonNull(onUiThread, "onUiThread must not be null");

        audio.setOnEndOfMedia(() -> this.onUiThread.accept(this::advance));
        player.addListener(this);
        // Open whatever is already current, so the first press of play is instant rather than
        // spending a decode on the file it should have had ready.
        syncToPlayer();
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    /** Starts or resumes playback of the current song. */
    public void play() {
        playWhenReady = true;
        Song song = player.current();
        if (song == null) {
            return;
        }
        if (!audio.isLoaded() && !open(song)) {
            return;
        }
        start(song);
    }

    /** Suspends playback, holding the position. */
    public void pause() {
        playWhenReady = false;
        audio.pause();
    }

    /** Plays if paused, pauses if playing. */
    public void toggle() {
        if (audio.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    /** Stops playback and rewinds the current song. */
    public void stop() {
        playWhenReady = false;
        audio.stop();
    }

    /**
     * Jumps to a position within the current song.
     *
     * @param position where to move to
     */
    public void seek(Duration position) {
        if (audio.isLoaded()) {
            audio.seek(position);
            onSeek.run();
        }
    }

    /**
     * Sets what happens when the playhead is moved by hand.
     *
     * <p>One listener with one caller: anything deriving a result from the audio as it goes past
     * has to give that result up when the audio stops corresponding to where it thought it was.
     * The beat analysis of a streamed track is the case - it is the audio, so a seek leaves it with
     * a curve whose timings are wrong and no way to know it.
     *
     * @param action what to run after a seek; must not be {@code null}
     */
    public void setOnSeek(Runnable action) {
        this.onSeek = Objects.requireNonNull(action, "action must not be null");
    }

    /**
     * Sets what happens when a track plays all the way out.
     *
     * <p>Runs on the same thread the running order is advanced on, immediately before it moves, so
     * the song that just finished is still the current one. That is the only moment at which
     * anything built out of the audio can be filed against the right track.
     *
     * <p>Distinct from a song change: skipping is not finishing, and a track heard half way through
     * has produced half a beatmap, which is not a short one but a wrong one.
     *
     * @param action what to run at the end of a track; must not be {@code null}
     */
    public void setOnTrackEnded(Runnable action) {
        this.onTrackEnded = Objects.requireNonNull(action, "action must not be null");
    }

    // ------------------------------------------------------------------
    // Following the running order
    // ------------------------------------------------------------------

    /**
     * Loads whatever the player made current.
     *
     * @param mode    the active mode
     * @param current the song now current, or {@code null}
     */
    @Override
    public void playbackChanged(PlaybackMode mode, Song current) {
        syncToPlayer();
    }

    private void syncToPlayer() {
        Song song = player.current();
        if (Objects.equals(song, loaded)) {
            // A rebuild that kept the same song current, or a mode change carrying it across.
            // Reloading here would restart the track every time the library was touched.
            return;
        }
        loaded = song;
        counted = false;
        failure = null;
        if (song == null) {
            audio.stop();
            return;
        }
        if (open(song) && playWhenReady) {
            start(song);
        }
    }

    /**
     * Advances at the end of a track.
     *
     * <p>Two details here, and both were wrong first time round:
     *
     * <ul>
     *   <li><strong>Exhaustion is checked before moving, not after.</strong> A drained queue leaves
     *       the last song current and answers {@link Player#next()} with {@code null} - but it
     *       still notifies, and reacting to that notification would reload the song that had just
     *       finished and play it again, forever. Asking {@link Player#canGoNext()} first is what
     *       makes the end of a queue actually the end.</li>
     *   <li><strong>{@code loaded} is cleared before moving.</strong> A one-song circular list
     *       answers {@code next()} with the song that just finished, which is correct - past the
     *       tail is the head. Without clearing, the engine would recognise it as already loaded and
     *       leave the room in silence.</li>
     * </ul>
     */
    private void advance() {
        // Announced before the running order moves, and that ordering is the whole point. Anything
        // deriving a result from the audio as it went past has heard all of the track it is ever
        // going to hear, and has to be told so while the song it belongs to is still the current
        // one - a moment later, the beat analysis of a streamed track would be filed against
        // whatever came next.
        onTrackEnded.run();
        if (!player.canGoNext()) {
            playWhenReady = false;
            return;
        }
        loaded = null;
        player.next();
    }

    /**
     * Opens a song's file.
     *
     * @param song the song to open
     * @return whether it can be played
     */
    private boolean open(Song song) {
        try {
            // The locator, not the file path: a streamed song has no file, and this is the one
            // line that would otherwise have to know the difference. See Song.locator().
            audio.load(song.locator());
            failure = null;
            return true;
        } catch (AudioException e) {
            failure = e.getMessage();
            // Reported once per song rather than per attempt: skipping through a library of stale
            // paths must not fill the log, and it must certainly not raise a dialog per song.
            LOG.warning("Cannot play \"" + song.getTitle() + "\": " + failure);
            onFailure.accept(song, failure);
            return false;
        }
    }

    /**
     * Starts the audio and records the play.
     *
     * <p>The count goes up when a song actually starts, not when it is loaded and not when it is
     * resumed, so scrolling past a song in the library leaves its statistics alone.
     *
     * @param song the song being started
     */
    private void start(Song song) {
        try {
            audio.play();
        } catch (AudioException e) {
            failure = e.getMessage();
            LOG.warning("Cannot play \"" + song.getTitle() + "\": " + failure);
            onFailure.accept(song, failure);
            return;
        }
        if (!counted) {
            counted = true;
            song.incrementPlayCount();
            // Announced so the table and the statistics follow; the player deliberately ignores an
            // edit, so this cannot disturb the running order.
            player.library().update(song);
            // The same instant the count goes up is the one the history wants: a song that was
            // loaded and never started did not play, and one that was resumed did not play twice.
            onPlayCounted.accept(song);
        }
    }

    // ------------------------------------------------------------------
    // State, for the transport controls
    // ------------------------------------------------------------------

    /** @return whether audio is being rendered right now */
    public boolean isPlaying() {
        return audio.isPlaying();
    }

    /** @return whether the current song's file was opened successfully */
    public boolean isLoaded() {
        return audio.isLoaded() && failure == null;
    }

    /** @return how far into the current song playback has reached */
    public Duration position() {
        return audio.position();
    }

    /**
     * The clock everything timed against the music must read.
     *
     * <p>Never {@code position().toSeconds()}: on {@code java.time.Duration} that method returns a
     * {@code long} and silently throws the fraction away. See {@link AudioSource#positionSeconds()}
     * for what that cost.
     *
     * @return how far into the current song playback has reached, in fractional seconds
     */
    public double positionSeconds() {
        return audio.positionSeconds();
    }

    /** @return the current song's playing time in fractional seconds */
    public double durationSeconds() {
        return AudioSource.toSeconds(duration());
    }

    /**
     * Returns the current song's playing time.
     *
     * <p>Falls back to what the library recorded when the decoder cannot say - a variable-bitrate
     * file with no header genuinely cannot be measured without decoding all of it, and the
     * progress bar needs a length before the song has finished.
     *
     * @return the playing time, or {@link Duration#ZERO} when nothing is known
     */
    public Duration duration() {
        Duration measured = audio.duration();
        if (!measured.isZero()) {
            return measured;
        }
        Song song = player.current();
        return song == null ? Duration.ZERO : song.getDuration();
    }

    /** @return why the current song cannot be played, or {@code null} when it can */
    public String failure() {
        return failure;
    }

    /** @return the audio output being driven */
    public AudioSource audio() {
        return audio;
    }

    /**
     * Sets what to do when a song's file cannot be opened.
     *
     * <p>Called once per song, on the thread that changed the song. The interface shows this in
     * place rather than in a dialog: skipping through a library whose files have moved would
     * otherwise raise one modal window per song.
     *
     * @param handler receives the song and the reason; must not be {@code null}
     */
    public void setOnFailure(BiConsumer<Song, String> handler) {
        this.onFailure = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * Sets what to do when a play is counted.
     *
     * <p>Fires exactly once per song actually started - not when one is loaded, and not when a
     * paused one is resumed - which is the same moment the song's play count goes up. Called on the
     * thread that started the audio.
     *
     * @param handler receives the song that started; must not be {@code null}
     */
    public void setOnPlayCounted(Consumer<Song> handler) {
        this.onPlayCounted = Objects.requireNonNull(handler, "handler must not be null");
    }

    /** Stops playback and releases the audio output. */
    @Override
    public void close() {
        player.removeListener(this);
        audio.setOnEndOfMedia(null);
        audio.close();
    }
}
