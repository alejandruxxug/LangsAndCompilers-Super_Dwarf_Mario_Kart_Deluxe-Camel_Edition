package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One short sound bundled in the jar, played on a line of its own.
 *
 * <p><strong>Its own {@link SourceDataLine}, and that is the whole design.</strong> The application
 * already has a line, and it belongs to the music: it is the clock the runner's entire lookahead is
 * read off ({@code position()} counts the frames that line has rendered), and pushing a fanfare
 * through it would move that clock by however long the fanfare is. So this opens a second line,
 * writes to it and closes it. Nothing here touches {@link AudioSource}, the taps, the meters or the
 * beat analyser - a sound effect is not part of the track and must not appear in its beatmap.
 *
 * <p><strong>Nothing here ever throws.</strong> A missing resource, a format no installed decoder
 * can read, and a machine with no free output line are all ordinary; the sound simply does not
 * happen and a line goes in the log (ground rule 5 - the application launches and stays usable with
 * nothing present at all). {@link #isReady()} is what a caller can check if it wants to <em>report</em>
 * the absence, which is the only reason to know.
 *
 * <p>Decoded once, through {@link PcmFormat} rather than through a decoder of its own, so a sound
 * effect and a song are resolved to the same 44.1 kHz stereo shape by the same code. The decode
 * happens on this class's own thread the first time it is played: it is a few hundred kilobytes of
 * MPEG and doing it on the interface thread would stall whatever asked for the sound, which for the
 * boot fanfare is the first frame of the glitch.
 */
public final class SoundEffect {

    private static final Logger LOG = Logger.getLogger(SoundEffect.class.getName());

    /**
     * How long the fade at the end of {@link #stop()} takes, in seconds.
     *
     * <p>Short enough to be a cut and long enough not to be a click. Stopping a line mid-block
     * leaves the speaker cone somewhere other than zero, and that step is audible as a tick - which
     * on a sound whose whole job is to be the nice noise the application makes is exactly the wrong
     * detail to get wrong.
     */
    private static final double FADE_SECONDS = 0.25;

    /** How much audio is handed to the line at a time, in bytes; also the stop check's granularity. */
    private static final int BLOCK_BYTES = 4096;

    private final String resource;

    /**
     * The decoded audio, or {@code null} until the first play - and still {@code null} afterwards if
     * it could not be read.
     *
     * <p>Volatile because {@link #isReady()} is answered from whatever thread asks, while the field
     * is written by the player thread.
     */
    private volatile byte[] pcm;

    /** Set once the resource has been looked at, so a failure is not retried on every play. */
    private volatile boolean resolved;

    /** Set by {@link #stop()}; read once per block by the player thread. */
    private volatile boolean stopping;

    /** The thread currently playing, or {@code null}. Never more than one. */
    private volatile Thread player;

    /**
     * Names a sound without touching it.
     *
     * <p>Deliberately does no work: constructing one of these must be free, so a field holding an
     * effect that is never played costs nothing at all.
     *
     * @param resource the classpath location of the audio, for instance
     *                 {@link AppConfig#SOUND_BOOT}; must not be {@code null}
     */
    public SoundEffect(String resource) {
        this.resource = resource;
    }

    /**
     * Plays the sound from the beginning, replacing whatever this effect was already playing.
     *
     * <p>Returns immediately. The decode, if it has not happened yet, and the whole of the playback
     * happen on a daemon thread of this effect's own, so a caller on the interface thread is never
     * held up and a sound left playing can never keep the JVM alive.
     */
    public void play() {
        stop();
        stopping = false;
        Thread thread = new Thread(this::run, "sdmk-sound-" + shortName());
        thread.setDaemon(true);
        player = thread;
        thread.start();
    }

    /**
     * Fades the sound out and stops it.
     *
     * <p>Fades rather than cuts - see {@link #FADE_SECONDS}. Returns as soon as the player thread has
     * been told; it is not waited for, because the caller is usually the interface thread and the
     * fade is a quarter of a second of it.
     *
     * <p>Safe to call when nothing is playing, and safe to call twice.
     */
    public void stop() {
        stopping = true;
        player = null;
    }

    /** @return whether the sound is playing right now */
    public boolean isPlaying() {
        Thread thread = player;
        return thread != null && thread.isAlive();
    }

    /**
     * Decodes the sound without playing it, and says whether that worked.
     *
     * <p>Exists for the smoke test, and for exactly the reason so much of this project is measured:
     * a sound that fails to decode is completely silent, and silence is also what a sound that
     * decoded perfectly and was never triggered looks like. This is the one check that tells those
     * apart, and it makes no noise doing it.
     *
     * @return whether the audio is present and decodable
     */
    public boolean isReady() {
        decode();
        return pcm != null;
    }

    /** @return how long the sound runs for in seconds, or 0 when it could not be read */
    public double lengthSeconds() {
        decode();
        byte[] audio = pcm;
        return audio == null ? 0 : audio.length / (double) AppConfig.BYTES_PER_SECOND;
    }

    // ------------------------------------------------------------------
    // The player thread
    // ------------------------------------------------------------------

    /** Decodes if needed, then writes the whole buffer to a line of its own. */
    private void run() {
        decode();
        byte[] audio = pcm;
        if (audio == null || stopping) {
            return;
        }
        try (SourceDataLine line = AudioSystem.getSourceDataLine(PcmFormat.PLAYBACK_FORMAT)) {
            line.open(PcmFormat.PLAYBACK_FORMAT);
            line.start();
            writeTo(line, audio);
            if (!stopping) {
                // Only on a natural end. After a fade the line holds a quarter second of audio that
                // has already been faded to nothing, and draining it would simply wait for silence.
                line.drain();
            } else {
                line.flush();
            }
        } catch (LineUnavailableException e) {
            // Every output line is busy, or the machine has none. Not a failure worth reporting to
            // the user: the sound is a flourish and the application is not about to stop for it.
            LOG.log(Level.FINE, "No output line was free for " + resource, e);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Could not play " + resource, e);
        }
    }

    /**
     * Writes the buffer a block at a time, fading out if asked to stop part way through.
     *
     * @param line  the open, started line
     * @param audio the decoded audio
     */
    private void writeTo(SourceDataLine line, byte[] audio) {
        int fadeBytes = alignToFrame((int) (FADE_SECONDS * AppConfig.BYTES_PER_SECOND));
        int offset = 0;
        while (offset < audio.length) {
            if (stopping) {
                // The tail is faded in place on a copy, so the cached buffer stays pristine and the
                // next play starts from the sound as decoded rather than from a faded one.
                int length = Math.min(fadeBytes, audio.length - offset);
                byte[] tail = new byte[length];
                System.arraycopy(audio, offset, tail, 0, length);
                fadeOut(tail);
                line.write(tail, 0, length);
                return;
            }
            int length = Math.min(BLOCK_BYTES, audio.length - offset);
            line.write(audio, offset, length);
            offset += length;
        }
    }

    /**
     * Ramps a block of audio linearly down to silence, in place.
     *
     * <p>Little-endian signed 16-bit, both channels, exactly as documented in the audio invariants -
     * the low byte first, and the sign carried by the high one. Scaling the wrong way round here
     * would be inaudible as a fade and unmistakable as a crunch.
     *
     * @param block the audio to fade; length is a whole number of frames
     */
    private static void fadeOut(byte[] block) {
        int frames = block.length / AppConfig.BYTES_PER_FRAME;
        if (frames == 0) {
            return;
        }
        for (int frame = 0; frame < frames; frame++) {
            double gain = 1 - frame / (double) frames;
            for (int channel = 0; channel < AppConfig.CHANNELS; channel++) {
                int index = frame * AppConfig.BYTES_PER_FRAME
                        + channel * (AppConfig.SAMPLE_SIZE_BITS / 8);
                short sample = (short) ((block[index + 1] << 8) | (block[index] & 0xFF));
                short faded = (short) Math.round(sample * gain);
                block[index] = (byte) (faded & 0xFF);
                block[index + 1] = (byte) ((faded >> 8) & 0xFF);
            }
        }
    }

    /**
     * @param bytes a byte count
     * @return the same count rounded down to a whole number of frames, so a partial frame can never
     *         be written and shift the channels against each other for the rest of the sound
     */
    private static int alignToFrame(int bytes) {
        return Math.max(AppConfig.BYTES_PER_FRAME,
                bytes - (bytes % AppConfig.BYTES_PER_FRAME));
    }

    // ------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------

    /**
     * Reads and decodes the resource, once.
     *
     * <p>Synchronized rather than lock-free: it runs at most once per effect for the life of the
     * application, and the alternative is two threads decoding the same few hundred kilobytes.
     */
    private synchronized void decode() {
        if (resolved) {
            return;
        }
        resolved = true;
        try (InputStream in = SoundEffect.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warning("No sound at " + resource + "; it simply will not play");
                return;
            }
            try (AudioInputStream audio = PcmFormat.open(in, resource)) {
                pcm = audio.readAllBytes();
            }
        } catch (IOException | AudioException e) {
            LOG.log(Level.WARNING, "Could not decode " + resource, e);
        }
    }

    /** @return the resource's file name, for the thread's name */
    private String shortName() {
        int slash = resource.lastIndexOf('/');
        return slash < 0 ? resource : resource.substring(slash + 1);
    }

    @Override
    public String toString() {
        return "SoundEffect[" + resource + "]";
    }
}
