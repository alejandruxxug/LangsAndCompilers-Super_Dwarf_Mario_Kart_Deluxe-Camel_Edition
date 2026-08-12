package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plays a file from disk through {@code javax.sound.sampled}, tapping every block on the way out.
 *
 * <p>MP3 and WAV both arrive here as an {@link AudioInputStream}; the service provider registered by
 * {@code mp3spi} decodes the former. Whatever the file was - mono, 22 kHz, compressed - what comes
 * out is {@link #PLAYBACK_FORMAT}, because everything downstream is written against that one format
 * and nothing else. See {@link #convertToPlaybackFormat} for why reaching it sometimes takes two
 * conversions rather than one.
 *
 * <h2>The thread arrangement</h2>
 *
 * <p>One daemon thread does nothing but read a block, hand it to the taps, and write it to the
 * line. That thread is the audio thread of ground rule 4: it must never wait on the interface and
 * the interface must never wait on it. The only shared state is a handful of volatiles and one
 * monitor used to park the thread while paused - not to synchronise data.
 *
 * <p>Pacing is the line's job, not ours. {@link SourceDataLine#write} blocks once the card's buffer
 * is full, which is what keeps decoding in step with playback without a single sleep or timer. The
 * buffer is deliberately small ({@value #LINE_BUFFER_BLOCKS} blocks, about
 * {@value #LINE_BUFFER_BLOCKS}&#215;23&nbsp;ms): the taps see a block just <em>before</em> it is
 * played, so a generous buffer would put the meters visibly ahead of the sound.
 *
 * <h2>The clock</h2>
 *
 * <p>{@link #position()} comes from {@link DataLine#getLongFramePosition()} - frames the card has
 * actually rendered - offset by a snapshot taken whenever the stream is repositioned. Counting
 * bytes written instead would run a whole buffer ahead of the music, and counting frames drawn
 * would drift.
 */
public class LocalFileAudioSource implements AudioSource {

    private static final Logger LOG = Logger.getLogger(LocalFileAudioSource.class.getName());

    /**
     * Bytes read from the decoder at a time: 1024 stereo frames, about 23 ms.
     *
     * <p>Small enough that the meters track transients, large enough that the per-block overhead
     * disappears next to decoding.
     */
    private static final int BLOCK_BYTES = 4096;

    /** Blocks the sound card buffers ahead. Small on purpose; see the class note on the tap. */
    private static final int LINE_BUFFER_BLOCKS = 4;

    /** How long {@link #close()} and friends wait for the playback thread to notice and stop. */
    private static final long THREAD_JOIN_MILLIS = 500;

    /**
     * The one format everything downstream is written against.
     *
     * <p>Signed 16-bit, 44.1 kHz, stereo, interleaved, little-endian. Every file ends up here
     * whatever it started as, which is what lets the meters, the beat analyser and the game each
     * assume a single shape of buffer and a single sample rate rather than carrying a format
     * around and branching on it.
     */
    private static final AudioFormat PLAYBACK_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            AppConfig.SAMPLE_RATE,
            AppConfig.SAMPLE_SIZE_BITS,
            AppConfig.CHANNELS,
            AppConfig.BYTES_PER_FRAME,
            AppConfig.SAMPLE_RATE,
            false);

    /** Guards nothing but the parked/running handshake with the playback thread. */
    private final Object pumpLock = new Object();

    /** Guards the two fields the position clock is derived from, so a reader sees a matched pair. */
    private final Object clockLock = new Object();

    private final List<PcmListener> listeners = new CopyOnWriteArrayList<>();

    private Path file;
    private AudioInputStream stream;
    private Duration duration = Duration.ZERO;

    private SourceDataLine line;
    private Thread pump;
    private Runnable onEndOfMedia;
    private double volume = 1.0;

    /**
     * Bumped whenever the stream is replaced or repositioned.
     *
     * <p>A playback thread that finds the number has moved on exits without touching anything. That
     * is how a seek retires the thread that was mid-block on the old position, without interrupts
     * and without a chance of it writing stale audio after the new one has started.
     */
    private volatile long generation;

    private volatile boolean playing;
    private volatile boolean closed;

    /**
     * Whether the loaded stream has been read to its end.
     *
     * <p>An exhausted stream has no bytes left to give, so playing it again has to rewind first.
     * Without this, pressing play after a song finished would start a thread that immediately read
     * end-of-file and stopped, which looks exactly like a broken button.
     */
    private volatile boolean exhausted;

    /** Frames already played when the stream was last repositioned. */
    private long baseFrames;

    /** The line's own frame counter at that same moment, so only the delta since then counts. */
    private long lineFramesAtBase;

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    @Override
    public void load(Path audioFile) {
        Objects.requireNonNull(audioFile, "audioFile must not be null");
        if (closed) {
            throw new AudioException("The audio output has been closed");
        }
        if (!Files.isReadable(audioFile)) {
            throw new AudioException("File not found: " + audioFile);
        }

        retirePump();
        setPlaying(false);
        closeStream();

        this.file = audioFile;
        openStream(audioFile);
        this.duration = readDuration(audioFile);
        rebase(0);
        publishSilence();
    }

    /**
     * Opens the file and inserts whatever conversion is needed to reach the playback format.
     *
     * @param audioFile the file to open
     * @throws AudioException if no installed decoder can produce the playback format
     */
    private void openStream(Path audioFile) {
        AudioInputStream encoded;
        try {
            encoded = AudioSystem.getAudioInputStream(audioFile.toFile());
        } catch (UnsupportedAudioFileException e) {
            throw new AudioException("Not an audio format this build can play: "
                    + audioFile.getFileName(), e);
        } catch (IOException e) {
            throw new AudioException("Could not read " + audioFile.getFileName()
                    + ": " + e.getMessage(), e);
        }
        this.stream = convertToPlaybackFormat(encoded, audioFile);
        // A fresh stream has bytes again, whether this was a load or a seek.
        this.exhausted = false;
    }

    /**
     * Wraps a file's stream in however many conversions it takes to reach {@link #PLAYBACK_FORMAT}.
     *
     * <p><strong>Sometimes that is two, and one would silently not be enough.</strong> A decoder
     * declares its output at the file's own sample rate and channel count - so a 22 kHz mono MP3
     * can be asked for 16-bit PCM, but not for 44.1 kHz stereo 16-bit PCM, and asking for both at
     * once simply fails. The plain PCM-to-PCM providers do resample and do mix channels, verified
     * against the resolved jars rather than assumed, so the answer is to decode first and convert
     * the result second.
     *
     * <p>Almost every file takes the one-step path: a 44.1 kHz stereo MP3 or WAV is already
     * arranged the way the second stage would leave it.
     *
     * @param encoded   the file's own stream
     * @param audioFile the file, for the error message
     * @return a stream delivering {@link #PLAYBACK_FORMAT}
     * @throws AudioException if no combination of installed providers can get there
     */
    private static AudioInputStream convertToPlaybackFormat(AudioInputStream encoded, Path audioFile) {
        AudioFormat source = encoded.getFormat();
        if (AudioSystem.isConversionSupported(PLAYBACK_FORMAT, source)) {
            return AudioSystem.getAudioInputStream(PLAYBACK_FORMAT, encoded);
        }

        AudioFormat decoded = sixteenBitVersionOf(source);
        if (!AudioSystem.isConversionSupported(decoded, source)) {
            closeQuietly(encoded);
            throw new AudioException("No decoder can turn " + source.getEncoding()
                    + " at " + describe(source) + " into 16-bit PCM: " + audioFile.getFileName());
        }

        AudioInputStream pcm = AudioSystem.getAudioInputStream(decoded, encoded);
        if (!AudioSystem.isConversionSupported(PLAYBACK_FORMAT, pcm.getFormat())) {
            closeQuietly(pcm);
            throw new AudioException("Cannot resample " + describe(pcm.getFormat()) + " to "
                    + describe(PLAYBACK_FORMAT) + ": " + audioFile.getFileName());
        }
        return AudioSystem.getAudioInputStream(PLAYBACK_FORMAT, pcm);
    }

    /**
     * Describes the same audio as signed 16-bit little-endian, keeping its rate and channel count.
     *
     * <p>This is the most a decoder will agree to produce in one step, and the input to the
     * resampling stage.
     *
     * @param source the file's own format
     * @return the format to decode into
     */
    private static AudioFormat sixteenBitVersionOf(AudioFormat source) {
        float rate = source.getSampleRate() > 0 ? source.getSampleRate() : AppConfig.SAMPLE_RATE;
        int channels = source.getChannels() > 0 ? source.getChannels() : AppConfig.CHANNELS;
        int bytesPerFrame = channels * AppConfig.SAMPLE_SIZE_BITS / 8;
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                rate,
                AppConfig.SAMPLE_SIZE_BITS,
                channels,
                bytesPerFrame,
                rate,
                false);
    }

    /**
     * Reads the track's playing time.
     *
     * <p>The decoded stream is asked first, because an exact frame count is exact; compressed
     * formats report {@code NOT_SPECIFIED} there and fall through to the header-and-bitrate
     * estimate {@link AudioMetadata} already does for the library import.
     *
     * @param audioFile the open file
     * @return the playing time, or {@link Duration#ZERO} when it cannot be established
     */
    private Duration readDuration(Path audioFile) {
        long frames = stream.getFrameLength();
        float rate = PLAYBACK_FORMAT.getFrameRate();
        if (frames > 0 && rate > 0) {
            return framesToDuration(frames, rate);
        }
        return AudioMetadata.readDuration(audioFile);
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @Override
    public void play() {
        if (closed || stream == null || playing) {
            return;
        }
        if (exhausted) {
            // Playing a song that has already finished means playing it again from the top.
            // Seeking reopens the stream, so this is the rewind.
            seek(Duration.ZERO);
        }
        openLine();
        line.start();
        setPlaying(true);
        startPump();
    }

    @Override
    public void pause() {
        setPlaying(false);
        SourceDataLine open = line;
        if (open != null) {
            // Stops the card without discarding what is buffered, so resuming is seamless and the
            // frame counter - and therefore the position - simply stops advancing.
            open.stop();
        }
        publishSilence();
    }

    @Override
    public void stop() {
        pause();
        seek(Duration.ZERO);
    }

    @Override
    public void seek(Duration position) {
        if (file == null || closed) {
            return;
        }
        boolean wasPlaying = playing;

        retirePump();
        setPlaying(false);
        closeStream();

        long targetFrames = clampToTrack(position);
        openStream(file);
        skipFrames(targetFrames);
        rebase(targetFrames);
        publishSilence();

        if (wasPlaying) {
            play();
        }
    }

    /**
     * @param position the requested position, possibly {@code null} or out of range
     * @return the equivalent frame index, inside the track
     */
    private long clampToTrack(Duration position) {
        if (position == null || position.isNegative()) {
            return 0;
        }
        long frames = Math.round(position.toNanos() / 1_000_000_000d * AppConfig.SAMPLE_RATE);
        if (!duration.isZero()) {
            long total = Math.round(duration.toNanos() / 1_000_000_000d * AppConfig.SAMPLE_RATE);
            frames = Math.min(frames, Math.max(0, total - 1));
        }
        return Math.max(0, frames);
    }

    /**
     * Winds the decoder forward by reading and discarding.
     *
     * <p>Linear in the distance jumped, and it has to be: a compressed stream has no byte offset
     * that corresponds to a given instant, and a variable-bitrate file has no arithmetic that would
     * find one. Decoding half a track to reach its midpoint costs a fraction of a second and is
     * exact, where seeking by file offset would land somewhere approximate and desynchronise every
     * beat that follows.
     *
     * @param frames how many frames to skip
     */
    private void skipFrames(long frames) {
        long remaining = frames * AppConfig.BYTES_PER_FRAME;
        if (remaining <= 0) {
            return;
        }
        byte[] scratch = new byte[BLOCK_BYTES];
        try {
            while (remaining > 0) {
                int chunk = (int) Math.min(scratch.length, remaining);
                int read = stream.read(scratch, 0, chunk);
                if (read <= 0) {
                    return;
                }
                remaining -= read;
            }
        } catch (IOException e) {
            throw new AudioException("Could not seek within " + file.getFileName()
                    + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // The playback thread
    // ------------------------------------------------------------------

    private void startPump() {
        synchronized (pumpLock) {
            if (pump != null) {
                pumpLock.notifyAll();
                return;
            }
            long myGeneration = generation;
            pump = new Thread(() -> run(myGeneration), "sdmk-playback");
            // A daemon, so a forgotten close can never keep the application alive.
            pump.setDaemon(true);
            pump.start();
        }
    }

    /**
     * Reads, taps and writes until the track ends or the generation moves on.
     *
     * @param myGeneration the stream generation this thread was started for
     */
    private void run(long myGeneration) {
        try {
            pumpLoop(myGeneration);
        } finally {
            // Whichever way this thread leaves, it stops being the pump. Leaving a dead thread in
            // the field is silent and total: the next play() sees a pump already running, starts
            // nothing, and the application simply never makes a sound again.
            retire();
        }
    }

    /**
     * @param myGeneration the stream generation this thread was started for
     */
    private void pumpLoop(long myGeneration) {
        AudioInputStream source = stream;
        SourceDataLine sink = line;
        if (source == null || sink == null) {
            return;
        }

        // One buffer, in the one format. Every conversion the file needed was inserted into the
        // stream at load, so nothing is rearranged here and the block that is measured is byte for
        // byte the block that is played.
        byte[] block = new byte[BLOCK_BYTES];
        boolean reachedEnd = false;

        while (generation == myGeneration && !closed) {
            if (!playing && !awaitResume(myGeneration)) {
                return;
            }

            int read;
            try {
                read = source.read(block, 0, block.length);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Playback stopped: could not read " + file, e);
                break;
            }
            if (read <= 0) {
                reachedEnd = true;
                break;
            }

            // Tap first, then forward. The tap must never consume the block: an implementation
            // that read the buffer instead of copying from it would leave the speakers silent.
            publish(block, read);

            if (!write(sink, block, read, myGeneration)) {
                return;
            }
        }

        if (reachedEnd && generation == myGeneration && !closed) {
            exhausted = true;
            finish(sink);
        }
    }

    /**
     * Gives up this thread's claim on the pump field, without disturbing a successor's.
     *
     * <p>Guarded by identity rather than simply nulling the field, because by the time a retiring
     * thread reaches its {@code finally} another one may already have taken over.
     */
    private void retire() {
        synchronized (pumpLock) {
            if (pump == Thread.currentThread()) {
                pump = null;
            }
        }
    }

    /**
     * Writes one block, parking rather than spinning if playback is suspended part-way through.
     *
     * @param sink         the open line
     * @param block        the bytes to write
     * @param length       how many of them
     * @param myGeneration the generation this thread belongs to
     * @return {@code false} if this thread has been retired and must exit
     */
    private boolean write(SourceDataLine sink, byte[] block, int length, long myGeneration) {
        int written = 0;
        while (written < length) {
            if (generation != myGeneration || closed) {
                return false;
            }
            if (!playing) {
                // Checked before every attempt, so a stopped line is never written to and the
                // remainder of a half-written block survives the pause instead of being dropped.
                if (!awaitResume(myGeneration)) {
                    return false;
                }
                continue;
            }
            int count = sink.write(block, written, length - written);
            if (count <= 0) {
                // The line was flushed underneath us; the rest of this block is stale.
                return generation == myGeneration && !closed;
            }
            written += count;
        }
        return true;
    }

    /**
     * Parks the playback thread until playback resumes.
     *
     * @param myGeneration the generation this thread belongs to
     * @return {@code true} if playback resumed, {@code false} if this thread has been retired
     */
    private boolean awaitResume(long myGeneration) {
        synchronized (pumpLock) {
            while (!playing && generation == myGeneration && !closed) {
                try {
                    pumpLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return generation == myGeneration && !closed;
        }
    }

    /**
     * Lets the card finish what it is holding, then reports the track as over.
     *
     * @param sink the open line
     */
    private void finish(SourceDataLine sink) {
        // Drain rather than stop: the last few blocks are still buffered, and cutting them off
        // would clip the end of every song and fire the advance early.
        sink.drain();
        setPlaying(false);
        publishSilence();
        // Retire before announcing, not after. The handler's whole purpose is to start the next
        // song, and it may do so on this very thread; finding the pump field still pointing here
        // it would start nothing at all, and the running order would stop dead one song in.
        retire();
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
                // One misbehaving tap must not silence the music.
                LOG.log(Level.WARNING, "PCM listener failed and was skipped for this block", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // The sound card
    // ------------------------------------------------------------------

    /**
     * Opens the line, or reopens it when the track's format differs from the one it was opened for.
     *
     * <p>Deferred until the first {@link #play()} rather than done on load, so that browsing the
     * library never takes the sound card, and so that a machine with no working audio output still
     * runs everything else.
     *
     * @throws AudioException if the card refuses the format
     */
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
            // A new line counts from zero, so the clock's reference point moves with it.
            resnapClock();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new AudioException("No sound output available for "
                    + describe(PLAYBACK_FORMAT) + ": " + e.getMessage(), e);
        }
    }

    private void applyVolume() {
        SourceDataLine open = line;
        if (open == null || !open.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) open.getControl(FloatControl.Type.MASTER_GAIN);
        // Gain is in decibels, and loudness is logarithmic: a linear 0.5 is a sixth of full scale,
        // not half of it. Silence is the control's own minimum rather than negative infinity.
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

    @Override
    public Duration position() {
        synchronized (clockLock) {
            long played = Math.max(0, currentLineFrames() - lineFramesAtBase);
            return framesToDuration(baseFrames + played, AppConfig.SAMPLE_RATE);
        }
    }

    @Override
    public Duration duration() {
        return duration;
    }

    /**
     * Declares where the stream now stands and takes a fresh reference reading from the line.
     *
     * <p>Only ever called with the line stopped and flushed, so nothing buffered is counted twice.
     *
     * @param frames the stream's new position, in frames from the start of the track
     */
    private void rebase(long frames) {
        synchronized (clockLock) {
            baseFrames = frames;
            lineFramesAtBase = currentLineFrames();
        }
    }

    /** Re-reads the line's counter without moving the stream, for a line that was just opened. */
    private void resnapClock() {
        synchronized (clockLock) {
            lineFramesAtBase = currentLineFrames();
        }
    }

    private long currentLineFrames() {
        SourceDataLine open = line;
        return open != null && open.isOpen() ? open.getLongFramePosition() : 0;
    }

    private static Duration framesToDuration(long frames, float frameRate) {
        if (frameRate <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(Math.round(frames / (double) frameRate * 1_000_000_000d));
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
        return stream != null;
    }

    /** @return the file currently loaded, or {@code null} */
    public Path file() {
        return file;
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

    private void setPlaying(boolean value) {
        synchronized (pumpLock) {
            playing = value;
            pumpLock.notifyAll();
        }
    }

    /**
     * Sends one block of silence to the taps.
     *
     * <p>Called whenever the output stops producing sound - paused, sought, replaced, played out.
     * Without it the meters hold the last block they were given and a paused player sits there
     * showing level, which reads as the meters being decorative rather than live. It is not a
     * phantom reading either: the output genuinely is silent, so a tap that measures it, or later
     * analyses it, gets the truth.
     */
    private void publishSilence() {
        byte[] silence = new byte[AppConfig.BYTES_PER_FRAME];
        publish(silence, silence.length);
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    /**
     * Retires the playback thread and waits briefly for it to notice.
     *
     * <p>Bumping the generation is what retires it; stopping and flushing the line is what wakes it
     * if it was blocked writing. It is never interrupted mid-write, so a block is never half
     * delivered to the card.
     */
    private void retirePump() {
        Thread running;
        synchronized (pumpLock) {
            generation++;
            running = pump;
            pump = null;
            playing = false;
            pumpLock.notifyAll();
        }
        SourceDataLine open = line;
        if (open != null && open.isOpen()) {
            open.stop();
            open.flush();
        }
        if (running == null || running == Thread.currentThread()) {
            return;
        }
        try {
            running.join(THREAD_JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeStream() {
        AudioInputStream open = stream;
        stream = null;
        closeQuietly(open);
    }

    private void closeLine() {
        SourceDataLine open = line;
        line = null;
        if (open != null) {
            open.stop();
            open.flush();
            open.close();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        retirePump();
        closeStream();
        closeLine();
        listeners.clear();
    }

    private static void closeQuietly(AudioInputStream toClose) {
        if (toClose == null) {
            return;
        }
        try {
            toClose.close();
        } catch (IOException e) {
            LOG.fine("Ignoring failure to close an audio stream: " + e);
        }
    }

    private static String describe(AudioFormat format) {
        return (int) format.getSampleRate() + " Hz, " + format.getSampleSizeInBits()
                + "-bit, " + format.getChannels() + " ch";
    }
}
