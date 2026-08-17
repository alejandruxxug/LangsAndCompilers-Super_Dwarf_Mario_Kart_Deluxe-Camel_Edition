package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boot fanfare, decoded but never played.
 *
 * <p><strong>A sound effect is the most silent kind of failure there is.</strong> A missing resource,
 * a format no installed decoder can read and an effect that was simply never triggered all produce
 * exactly the same thing: nothing. So the checks worth having are the ones that can be made without
 * a speaker - that the bundled audio is present, that it resolves through the same decode path
 * playback uses, and that a machine with no free output line is a shrug rather than an exception.
 *
 * <p>Nothing here opens a line. {@link SoundEffect#isReady()} exists for this reason and for the
 * smoke test's, which prints the same answer on every launch.
 */
@DisplayName("Sound effect")
class SoundEffectTest {

    @Test
    @DisplayName("the bundled fanfare is present and decodes through the playback path")
    void theFanfareDecodes() {
        SoundEffect fanfare = new SoundEffect(AppConfig.SOUND_BOOT);

        assertTrue(fanfare.isReady(),
                AppConfig.SOUND_BOOT + " is missing from the jar or no installed decoder can read "
                        + "it - the boot would be silent and nothing anywhere would say so");
    }

    @Test
    @DisplayName("it decodes to whole frames of the one playback format, so nothing can shift the channels")
    void theDecodeIsFrameAligned() {
        SoundEffect fanfare = new SoundEffect(AppConfig.SOUND_BOOT);
        double seconds = fanfare.lengthSeconds();

        assertTrue(seconds > 1,
                "the fanfare decoded to " + seconds + "s, which is not a boot sound");
        // The length is derived from the byte count over BYTES_PER_SECOND, so a whole number of
        // frames is the same statement as the byte count being a multiple of the frame size. A part
        // frame written to a line puts left where right belongs for the rest of the sound.
        long bytes = Math.round(seconds * AppConfig.BYTES_PER_SECOND);
        assertEquals(0, bytes % AppConfig.BYTES_PER_FRAME,
                "the decoded audio is not a whole number of 4-byte frames");
    }

    @Test
    @DisplayName("a missing resource is silence and a log line, never an exception")
    void aMissingSoundIsNotAFailure() {
        // Ground rule 5: the application launches and stays usable with nothing present at all. The
        // boot screen calls this from inside its own frame sequence, so a throw here would take the
        // launch with it.
        SoundEffect absent = new SoundEffect("/assets/sounds/there-is-no-such-file.mp3");

        assertFalse(absent.isReady());
        assertEquals(0, absent.lengthSeconds());
        // Both of these have to be safe on something that never decoded.
        absent.play();
        absent.stop();
        assertFalse(absent.isPlaying());
    }

    @Test
    @DisplayName("a resource that is not audio at all is refused the same way")
    void aNonAudioResourceIsRefused() {
        // The stylesheet is on the classpath and is certainly not an MPEG stream. This is the path
        // through PcmFormat's UnsupportedAudioFileException rather than through a null resource.
        SoundEffect notAudio = new SoundEffect(AppConfig.STYLESHEET_RESOURCE);

        assertFalse(notAudio.isReady());
        notAudio.play();
        assertFalse(notAudio.isReady());
    }

    @Test
    @DisplayName("stop is safe before anything has started, and twice over")
    void stopIsIdempotent() {
        SoundEffect fanfare = new SoundEffect(AppConfig.SOUND_BOOT);

        fanfare.stop();
        fanfare.stop();
        assertFalse(fanfare.isPlaying());
        // The stated-length overload takes the same path and has to be as forgiving.
        fanfare.stop(0.65);
        fanfare.stop(0.65);
        assertFalse(fanfare.isPlaying());
    }

    @Test
    @DisplayName("a stated fade covers the audio it says it does, in whole frames")
    void aStatedFadeIsAWholeNumberOfFrames() {
        // The boot handover asks for 650 ms so the fanfare lets go exactly as the library comes up
        // out of the black. A fade is inaudible in a test; what is checkable is that the length asked
        // for is the length covered.
        int bytes = SoundEffect.fadeBytes(0.65);

        assertEquals(Math.round(0.65 * AppConfig.BYTES_PER_SECOND), bytes,
                "a 650 ms fade should cover 650 ms of audio");
        assertEquals(0, bytes % AppConfig.BYTES_PER_FRAME,
                "a fade cut mid-frame puts the left channel where the right belongs for the rest "
                        + "of the sound");
    }

    @Test
    @DisplayName("a fade of nothing is a cut rather than a negative buffer")
    void aFadeOfNothingIsSafe() {
        // writeTo allocates a buffer of this, on the player thread, where nothing is watching a
        // NegativeArraySizeException. Zero and negative both have to come back as a usable count.
        for (double seconds : new double[] {0, -1, -0.0001}) {
            int bytes = SoundEffect.fadeBytes(seconds);

            assertTrue(bytes >= AppConfig.BYTES_PER_FRAME,
                    "a fade of " + seconds + "s came back as " + bytes + " bytes");
            assertEquals(0, bytes % AppConfig.BYTES_PER_FRAME);
        }
    }
}
