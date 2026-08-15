package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.spotify.SpotifyApi;
import com.eia.superdwarfkart.spotify.SpotifyConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plays a Spotify track by reading the daemon's audio pipe, tapping every block on the way out.
 *
 * <p>Deliberately the same shape as {@link LocalFileAudioSource}: one daemon thread reads a block,
 * hands it to the taps and writes it to the line; the clock is the card's own frame counter; the
 * output format is {@link PcmFormat#PLAYBACK_FORMAT}. What changes is where the bytes start.
 * <strong>Nothing is decoded on the Java side at all</strong> - go-librespot is configured to emit
 * {@code s16le} at 44.1 kHz stereo, which <em>is</em> the playback format, so the bytes arriving
 * here are already the bytes the meters and the beat analyser are written against.
 *
 * <h2>Three things about a pipe that a file does not do</h2>
 *
 * <p><strong>The pipe is opened once and kept open, at both ends.</strong> A FIFO's reader blocks
 * until a writer appears and reports end of file the moment the last writer leaves - so a reader
 * that only held the read end would see EOF at every track boundary and would have to guess whether
 * that meant "track over" or "daemon restarting". This class opens the write end too and never
 * writes a byte to it, which keeps the pipe alive across every gap. Measured: with our own writer
 * held, a read after the daemon closes its end blocks rather than returning {@code -1}. It is also
 * what makes the daemon's own open succeed - it opens {@code O_WRONLY|O_NONBLOCK} and fails outright
 * if no reader is there, so a reader that came and went would produce an error inside go-librespot
 * at the exact moment the user pressed play.
 *
 * <p><strong>The pipe is never left unread, and that is a correctness requirement rather than a
 * performance one.</strong> See {@link #startReader()}: the one reader thread runs for the life of
 * this source and consumes the pipe in every state, because a full pipe does not merely stall the
 * audio - it wedges the daemon's entire HTTP API, permanently.
 *
 * <p><strong>While audio is actually playing, the sound card is still the clock.</strong>
 * go-librespot's pipe driver has no sleep, no timer and no rate limiter: it transforms and writes in
 * a tight loop and blocks only on the pipe itself. {@link SourceDataLine#write} blocks once the
 * card's buffer is full, the pipe backs up behind it, and the daemon stops decoding until there is
 * room. The backpressure is the design, not a side effect. It only applies while playing; when
 * playback is stopped the daemon has been told to pause, so there is nothing left for it to race
 * through.
 *
 * <p><strong>A pipe holds stale audio, and a file does not.</strong> After a seek or a track change
 * the pipe still contains up to a bufferful of the previous moment, and playing it would put a
 * fraction of a second of the wrong music at the front of every track. The reader drops it instead -
 * see {@link #stale} - and {@link #settle()} is how a caller waits for that to have happened.
 *
 * <h2>The end of a track cannot be detected here</h2>
 *
 * <p>When a track finishes, the daemon's pipe output goes quiet - it does not close the pipe. A
 * quiet pipe is indistinguishable from a quiet passage, so end-of-track arrives out of band, from
 * the daemon's event socket, through {@link #trackEnded()}. Without that the running order would
 * stop dead after one song.
 */
public class SpotifyAudioSource implements AudioSource {

    private static final Logger LOG = Logger.getLogger(SpotifyAudioSource.class.getName());

    /** Bytes read from the pipe at a time: 1024 stereo frames, about 23 ms. */
    private static final int BLOCK_BYTES = 4096;

    /** Blocks the sound card buffers ahead. Small, so the taps stay level with the sound. */
    private static final int LINE_BUFFER_BLOCKS = 4;

    /** How long {@link #close()} waits for the reader thread to notice and stop. */
    private static final long THREAD_JOIN_MILLIS = 500;

    /** How long to wait for the daemon to actually open its end after a play command. */
    private static final long PIPE_OPEN_TIMEOUT_MILLIS = 10_000;

    /** How long {@link #settle()} waits at most for the pipe to go quiet. */
    private static final long SETTLE_TIMEOUT_MILLIS = 500;

    /** How long the pipe must read empty before {@link #settle()} calls it quiet. */
    private static final long SETTLE_QUIET_MILLIS = 20;

    private static final AudioFormat PLAYBACK_FORMAT = PcmFormat.PLAYBACK_FORMAT;

    private final SpotifyApi api;
    private final Object readerLock = new Object();
    private final Object clockLock = new Object();
    private final List<PcmListener> listeners = new CopyOnWriteArrayList<>();

    /** The read end of the pipe, opened once and held for the life of this source. */
    private InputStream pipe;

    /**
     * Our own write end, opened once and never written to.
     *
     * <p>Held solely so the pipe never reaches zero writers; see the class note.
     */
    private OutputStream pipeKeepAlive;

    private SourceDataLine line;

    /** The one thread that reads the pipe. Started on demand, stopped only by {@link #close()}. */
    private Thread reader;

    private Runnable onEndOfMedia;
    private double volume = 1.0;

    private String trackUri;
    private Duration duration = Duration.ZERO;

    private volatile boolean playing;
    private volatile boolean closed;

    /**
     * Set while whatever is in the pipe belongs to where the daemon used to be.
     *
     * <p>Raised before the daemon is repositioned and lowered once {@link #settle()} has seen the
     * pipe go quiet. While it is up the reader still consumes every block - it must, or the daemon
     * blocks - but drops them without reaching the taps or the sound card. Letting them through
     * would put a fraction of a second of the previous track at the front of the new one, and would
     * put the same fragment at the front of the novelty curve a streamed beatmap is built from.
     */
    private volatile boolean stale;

    /**
     * Set when the daemon says the track ended, cleared when the reader has played out the rest.
     *
     * <p>The end of a track is announced by the reader thread rather than by the event socket, so
     * that the audio still sitting in the pipe is heard first. See {@link #trackEnded()}.
     */
    private volatile boolean finishing;

    /** Frames already played when the track was last loaded or sought. */
    private long baseFrames;

    /** The line's own counter at that moment, so only the delta since then counts. */
    private long lineFramesAtBase;

    /**
     * Creates a source driving the daemon through its REST API.
     *
     * @param api the daemon's API; must not be {@code null}
     */
    public SpotifyAudioSource(SpotifyApi api) {
        this.api = Objects.requireNonNull(api, "api must not be null");
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Opens a Spotify track and holds it at its start, without playing it.
     *
     * @param locator the track URI, {@code spotify:track:...}
     * @throws AudioException if the locator is not a track URI or the daemon refuses it
     */
    @Override
    public void load(String locator) {
        Objects.requireNonNull(locator, "locator must not be null");
        if (closed) {
            throw new AudioException("The audio output has been closed");
        }
        if (!locator.startsWith("spotify:track:")) {
            throw new AudioException("Not a Spotify track URI: " + locator);
        }

        stale = true;
        finishing = false;
        playing = false;
        openPipe();
        // Started before the daemon is commanded, and left running afterwards. Commanding a daemon
        // whose pipe nobody is reading is what deadlocked this class - see startReader().
        startReader();

        this.trackUri = locator;
        this.duration = Duration.ZERO;

        // Loaded paused, so that selecting a song in the library while paused leaves the
        // application paused - the same contract load() has for a local file.
        if (!api.play(locator, 0, true)) {
            throw new AudioException("go-librespot would not load " + locator
                    + " - is Spotify connected?");
        }
        settle();
        stale = false;
        rebase(0);
        publishSilence();
    }

    /**
     * Opens the pipe's two ends, if they are not open already.
     *
     * <p>The handshake needs both: opening the read end blocks until some writer exists, and
     * opening a write end blocks until some reader exists. Opening our own writer on another thread
     * lets the two satisfy each other, after which the daemon can come and go freely.
     *
     * @throws AudioException if the pipe cannot be created or opened
     */
    private synchronized void openPipe() {
        if (pipe != null) {
            return;
        }
        Path fifo;
        try {
            fifo = SpotifyConfig.createFifo();
        } catch (IOException e) {
            throw new AudioException("Could not create the Spotify audio pipe: " + e.getMessage(), e);
        }

        // Opened on another thread because this open blocks until the reader below arrives.
        final OutputStream[] writer = new OutputStream[1];
        final IOException[] failure = new IOException[1];
        Thread opener = new Thread(() -> {
            try {
                writer[0] = new FileOutputStream(fifo.toFile());
            } catch (IOException e) {
                failure[0] = e;
            }
        }, "sdmk-spotify-pipe-open");
        opener.setDaemon(true);
        opener.start();

        try {
            InputStream stream = new FileInputStream(fifo.toFile());
            opener.join(PIPE_OPEN_TIMEOUT_MILLIS);
            if (writer[0] == null) {
                closeQuietly(stream);
                throw new AudioException("Could not hold the Spotify audio pipe open"
                        + (failure[0] == null ? "" : ": " + failure[0].getMessage()));
            }
            this.pipe = stream;
            this.pipeKeepAlive = writer[0];
            LOG.fine("Opened the Spotify audio pipe at " + fifo);
        } catch (IOException e) {
            throw new AudioException("Could not open the Spotify audio pipe: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioException("Interrupted while opening the Spotify audio pipe", e);
        }
    }

    /**
     * Waits for the pipe to go quiet after the daemon has been repositioned.
     *
     * <p><strong>Nothing here reads a byte.</strong> The reader thread is what empties the pipe;
     * this only watches {@link InputStream#available()}, which on a pipe reports exactly what is
     * buffered. A second reader on one FIFO would take blocks at random out of the stream the
     * reader is meant to see whole, which is precisely the hole a streamed beatmap cannot survive.
     *
     * <p>Capped, because the daemon may simply be producing nothing - a track loaded paused emits
     * almost no audio at all, so the common case returns after a couple of polls.
     */
    private void settle() {
        InputStream open = pipe;
        if (open == null) {
            return;
        }
        long deadline = System.nanoTime() + SETTLE_TIMEOUT_MILLIS * 1_000_000L;
        long quietSince = 0;
        while (System.nanoTime() < deadline) {
            if (available(open) > 0) {
                quietSince = 0;
            } else if (quietSince == 0) {
                quietSince = System.nanoTime();
            } else if ((System.nanoTime() - quietSince) / 1_000_000L >= SETTLE_QUIET_MILLIS) {
                return;
            }
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LOG.fine("The Spotify audio pipe did not go quiet within "
                + SETTLE_TIMEOUT_MILLIS + " ms");
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @Override
    public void play() {
        if (closed || trackUri == null || playing) {
            return;
        }
        openLine();
        // The reader is running before the daemon is asked to resume, and goes on running whatever
        // happens next. The old arrangement started it afterwards, which could not work: see
        // startReader().
        startReader();
        if (!api.resume()) {
            throw new AudioException("go-librespot would not resume playback");
        }
        line.start();
        playing = true;
    }

    @Override
    public void pause() {
        // Only the writing to the sound card stops. The reader goes on draining the pipe, or the
        // daemon blocks inside write() and its whole API stops answering - including the
        // /player/resume that would have undone this. See startReader().
        playing = false;
        api.pause();
        SourceDataLine open = line;
        if (open != null) {
            open.stop();
        }
        // Settled before the meters are cleared, so the last blocks the daemon emitted before it
        // noticed cannot light them back up after the silence has been published.
        settle();
        publishSilence();
    }

    @Override
    public void stop() {
        pause();
        seek(Duration.ZERO);
    }

    @Override
    public void seek(Duration position) {
        if (trackUri == null || closed) {
            return;
        }
        boolean wasPlaying = playing;

        stale = true;
        playing = false;
        SourceDataLine open = line;
        if (open != null && open.isOpen()) {
            open.stop();
            open.flush();
        }

        long targetMillis = clampToTrack(position);
        if (!api.seek(targetMillis)) {
            LOG.warning("go-librespot refused a seek to " + targetMillis + " ms");
        }
        // Order matters: the daemon has moved, so what is still in the pipe is from where it was.
        settle();
        stale = false;
        rebase(Math.round(targetMillis / 1000d * AppConfig.SAMPLE_RATE));
        publishSilence();

        if (wasPlaying) {
            play();
        }
    }

    /**
     * @param position the requested position, possibly {@code null} or out of range
     * @return the equivalent position in milliseconds, inside the track
     */
    private long clampToTrack(Duration position) {
        if (position == null || position.isNegative()) {
            return 0;
        }
        long millis = position.toMillis();
        if (!duration.isZero()) {
            millis = Math.min(millis, Math.max(0, duration.toMillis() - 1));
        }
        return Math.max(0, millis);
    }

    // ------------------------------------------------------------------
    // The playback thread
    // ------------------------------------------------------------------

    /**
     * Starts the one thread that reads the pipe, if it is not already running.
     *
     * <p><strong>There is exactly one reader and it never parks. That is the fix for a deadlock
     * that took this whole feature out, not a tidying-up.</strong>
     *
     * <p>go-librespot's pipe driver has no pacing of its own: it decodes and writes in a tight loop
     * and blocks only on the pipe. A FIFO's kernel buffer is 4 KB on macOS - one block, about 23 ms
     * - so the instant this side stops reading, the daemon's output goroutine blocks inside
     * {@code write}.
     *
     * <p>That would merely be a stall if the daemon answered requests concurrently. It does not:
     * v0.8.0 serves <em>every</em> HTTP request from a single goroutine - {@code AppPlayer.Run}'s
     * select loop, with {@code handleApiRequest} called inline - and its API server hands the
     * request over on an <em>unbuffered</em> channel with no timeout:
     *
     * <pre>{@code
     * s.requests = make(chan ApiRequest)   // unbuffered
     * s.requests <- req                    // blocks until the Run loop receives
     * resp := <-req.resp                   // blocks until it replies
     * }</pre>
     *
     * <p>So an output goroutine wedged on the pipe wedges the whole API: {@code /status},
     * {@code /player/pause} and {@code /player/resume} alike, for good.
     *
     * <p><strong>The old arrangement parked this thread whenever playback stopped and only restarted
     * it after {@code /player/resume} had come back.</strong> The daemon could not answer until the
     * pipe drained and the pipe could not drain until the daemon answered, so the two waited on each
     * other permanently and every later press of play threw {@code "go-librespot would not resume
     * playback"} - which is a five second timeout being reported as a refusal. Measured on a wedged
     * daemon: {@code GET /player/resume} answered 405 instantly, because that is decided by the
     * router, while {@code /status} did not answer in 25 seconds; draining the pipe by hand returned
     * it to 200 immediately and pulled 25 MB in 6 s, some 24x realtime, which is what a blocked
     * writer does the moment a reader appears.
     *
     * <p>Reading unconditionally breaks the cycle: the daemon can never block on the pipe, so it can
     * always answer. Every transport method in this class relies on that - each one commands the
     * daemon while this thread is running.
     */
    private void startReader() {
        synchronized (readerLock) {
            if (reader != null || pipe == null || closed) {
                return;
            }
            Thread thread = new Thread(this::readerLoop, "sdmk-spotify-playback");
            thread.setDaemon(true);
            reader = thread;
            thread.start();
        }
    }

    /**
     * Reads the pipe for the life of this source, forwarding only what is currently wanted.
     *
     * <p>Three states, and <em>all three consume the pipe</em>:
     *
     * <ul>
     *   <li><strong>Stale</strong> - the daemon has just been repositioned, so what is in the pipe
     *       belongs to where it used to be. Dropped without reaching the taps.</li>
     *   <li><strong>Paused</strong> - published to the taps but not written to the sound card. The
     *       taps are what {@code StreamBeatmapBuilder} folds into its novelty curve, and that curve
     *       is a position in the track measured in samples: dropping the blocks the daemon emits
     *       before it notices the pause would leave a hole in it, and a hole is worse than no curve
     *       because nothing downstream can tell. The frames are added to the clock's base instead,
     *       so {@link #position()} still reports where the track really is.</li>
     *   <li><strong>Playing</strong> - published and written, and {@code write} is what paces the
     *       daemon.</li>
     * </ul>
     */
    private void readerLoop() {
        byte[] block = new byte[BLOCK_BYTES];

        while (!closed) {
            InputStream source = pipe;
            if (source == null) {
                return;
            }

            // The daemon runs ahead of the speakers by however much the pipe holds, so its
            // "stopped" arrives while the last of the track is still in flight. Playing that out
            // before announcing the end is the difference between a clean transition and every
            // streamed track being clipped - up to a pipe buffer plus a card buffer, which is
            // getting on for half a second and is plainly audible.
            if (finishing && available(source) <= 0) {
                finish();
                continue;
            }

            int read;
            try {
                read = source.read(block, 0, block.length);
            } catch (IOException e) {
                if (!closed) {
                    LOG.log(Level.WARNING,
                            "Playback stopped: could not read the Spotify audio pipe", e);
                }
                return;
            }
            if (read < 0) {
                // Only reachable once our own keep-alive writer is gone, which is close().
                return;
            }
            if (read == 0) {
                continue;
            }

            if (stale) {
                continue;
            }

            // Tap first, then forward. The tap must never consume the block.
            publish(block, read);

            SourceDataLine sink = line;
            if (playing && sink != null) {
                writeFully(sink, block, read);
            } else {
                // Analysed but never heard, so the card's frame counter cannot account for it.
                // Banked, or the clock falls behind the track by whatever each pause discarded.
                advanceBase(read / AppConfig.BYTES_PER_FRAME);
            }
        }
    }

    /**
     * @param source the pipe
     * @return how many bytes are buffered, or zero if it cannot be asked
     */
    private static int available(InputStream source) {
        try {
            return source.available();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Writes one block, giving up on the remainder if playback stops part-way through.
     *
     * <p>What is left is dropped rather than held onto: this thread's first duty is to get back to
     * reading the pipe, and a block half-written when the user hit pause is a block they are not
     * going to hear anyway.
     *
     * @param sink   the open line
     * @param block  the bytes to write
     * @param length how many of them
     */
    private void writeFully(SourceDataLine sink, byte[] block, int length) {
        int written = 0;
        while (written < length && playing && !closed) {
            int count = sink.write(block, written, length - written);
            if (count <= 0) {
                break;
            }
            written += count;
        }
        advanceBase((length - written) / AppConfig.BYTES_PER_FRAME);
    }

    /**
     * Lets the card finish what it is holding, then reports the track as over.
     *
     * <p>Runs on the reader thread, once the pipe has been played out.
     */
    private void finish() {
        finishing = false;
        SourceDataLine sink = line;
        if (sink != null) {
            // Drain rather than stop: the last blocks are still buffered in the card, and cutting
            // them off would clip every track and fire the advance early.
            sink.drain();
        }
        playing = false;
        publishSilence();
        Runnable action = onEndOfMedia;
        if (action != null) {
            try {
                action.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "End-of-track handler failed", e);
            }
        }
    }

    private void publish(byte[] block, int length) {
        for (PcmListener listener : listeners) {
            try {
                listener.pcm(block, 0, length);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "PCM listener failed and was skipped for this block", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Told from outside
    // ------------------------------------------------------------------

    /**
     * Reports that the daemon says the track finished.
     *
     * <p>Called from the event socket, because a pipe cannot say this for itself - see the class
     * note.
     *
     * <p><strong>This does not stop anything.</strong> It raises a flag and returns, and the reader
     * thread plays out what is left in the pipe before announcing the end. The daemon is ahead of
     * the speakers by whatever the pipe holds, so acting on this event where it arrives would cut
     * the last fraction of a second off every streamed track and start the next one early. Keeping
     * all of the pipe reading on the reader thread also means this event handler never races it.
     */
    public void trackEnded() {
        if (closed || !playing) {
            return;
        }
        finishing = true;
    }

    /**
     * Records the track's real playing time, as the daemon's metadata event reports it.
     *
     * @param trackDuration the playing time; {@code null} or negative is ignored
     */
    public void setTrackDuration(Duration trackDuration) {
        if (trackDuration != null && !trackDuration.isNegative() && !trackDuration.isZero()) {
            this.duration = trackDuration;
        }
    }

    /** @return the track URI currently loaded, or {@code null} */
    public String trackUri() {
        return trackUri;
    }

    // ------------------------------------------------------------------
    // The sound card
    // ------------------------------------------------------------------

    private void openLine() {
        SourceDataLine open = line;
        if (open != null && open.isOpen()) {
            return;
        }
        closeLine();
        try {
            SourceDataLine fresh = AudioSystem.getSourceDataLine(PLAYBACK_FORMAT);
            fresh.open(PLAYBACK_FORMAT, LINE_BUFFER_BLOCKS * BLOCK_BYTES);
            line = fresh;
            applyVolume();
            resnapClock();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new AudioException("No sound output available for "
                    + PcmFormat.describe(PLAYBACK_FORMAT) + ": " + e.getMessage(), e);
        }
    }

    private void applyVolume() {
        SourceDataLine open = line;
        if (open == null || !open.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) open.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volume <= 0
                ? gain.getMinimum()
                : (float) Math.clamp(20 * Math.log10(volume), gain.getMinimum(), gain.getMaximum());
        gain.setValue(decibels);
    }

    @Override
    public void setVolume(double newVolume) {
        this.volume = Math.clamp(newVolume, 0d, 1d);
        applyVolume();
    }

    @Override
    public double volume() {
        return volume;
    }

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    /**
     * Reports how much of the track has actually been heard.
     *
     * <p>Read from this application's own sound card, not from the daemon's {@code /status}. The
     * daemon assumes its output consumes audio at the speed it is heard, which a pipe does not -
     * so its idea of the position runs ahead of the music by however far the pipe is buffered, and
     * would drift further under any hesitation. The card's frame counter is what was rendered,
     * plus whatever {@link #advanceBase} banked for audio that was decoded but deliberately not.
     */
    @Override
    public Duration position() {
        synchronized (clockLock) {
            long played = Math.max(0, currentLineFrames() - lineFramesAtBase);
            return framesToDuration(baseFrames + played);
        }
    }

    @Override
    public Duration duration() {
        return duration;
    }

    private void rebase(long frames) {
        synchronized (clockLock) {
            baseFrames = Math.max(0, frames);
            lineFramesAtBase = currentLineFrames();
        }
    }

    /**
     * Banks frames the daemon produced that the sound card never played.
     *
     * <p>Whatever the reader drops while paused is time the track genuinely advanced by - the
     * daemon resumes from after it - so leaving it out would put {@link #position()} behind the
     * music by that much at every pause, and the runner's whole lookahead reads off that clock.
     *
     * @param frames how many frames were dropped; zero or fewer is ignored
     */
    private void advanceBase(long frames) {
        if (frames <= 0) {
            return;
        }
        synchronized (clockLock) {
            baseFrames += frames;
        }
    }

    private void resnapClock() {
        synchronized (clockLock) {
            lineFramesAtBase = currentLineFrames();
        }
    }

    private long currentLineFrames() {
        SourceDataLine open = line;
        return open != null && open.isOpen() ? open.getLongFramePosition() : 0;
    }

    private static Duration framesToDuration(long frames) {
        return Duration.ofNanos(Math.round(frames / (double) AppConfig.SAMPLE_RATE * 1_000_000_000d));
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public boolean isLoaded() {
        return trackUri != null;
    }

    @Override
    public void addPcmListener(PcmListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void removePcmListener(PcmListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void setOnEndOfMedia(Runnable action) {
        this.onEndOfMedia = action;
    }

    /** Sends one block of silence to the taps, so the meters fall when the output stops. */
    private void publishSilence() {
        byte[] silence = new byte[AppConfig.BYTES_PER_FRAME];
        publish(silence, silence.length);
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    private void closeLine() {
        SourceDataLine open = line;
        line = null;
        if (open != null) {
            open.stop();
            open.flush();
            open.close();
        }
    }

    private static void closeQuietly(java.io.Closeable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
            LOG.finer("Could not close a Spotify pipe end: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        playing = false;

        Thread running;
        synchronized (readerLock) {
            running = reader;
            reader = null;
        }

        closeLine();
        // The keep-alive writer goes first: with it gone the reader sees end of file rather than
        // blocking forever, which is what lets a thread still inside read() come back.
        closeQuietly(pipeKeepAlive);
        pipeKeepAlive = null;
        closeQuietly(pipe);
        pipe = null;

        if (running != null && running != Thread.currentThread()) {
            try {
                running.join(THREAD_JOIN_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        listeners.clear();
        trackUri = null;
    }
}
