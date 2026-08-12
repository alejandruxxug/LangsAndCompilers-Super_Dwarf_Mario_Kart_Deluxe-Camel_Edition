package com.eia.superdwarfkart.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decoding and measuring interleaved PCM.
 *
 * <p>These are the assertions that stand in for listening. Byte order, sign extension and the
 * even/odd channel split all produce a meter that moves plausibly when they are wrong, so none of
 * them can be checked by looking at the running application - only by feeding in a buffer whose
 * correct answer is known in advance.
 */
class LevelAnalyzerTest {

    /**
     * Builds an interleaved stereo block from per-channel sample values.
     *
     * @param left  left channel samples, -1..1
     * @param right right channel samples, -1..1
     * @return the block, little-endian, 16-bit, interleaved
     */
    private static byte[] stereo(float[] left, float[] right) {
        byte[] buffer = new byte[left.length * 4];
        for (int frame = 0; frame < left.length; frame++) {
            writeSample(buffer, frame * 4, left[frame]);
            writeSample(buffer, frame * 4 + 2, right[frame]);
        }
        return buffer;
    }

    private static void writeSample(byte[] buffer, int index, float value) {
        short sample = (short) Math.round(value * 32767);
        buffer[index] = (byte) (sample & 0xFF);
        buffer[index + 1] = (byte) ((sample >> 8) & 0xFF);
    }

    @Test
    @DisplayName("a sample is read little-endian: the low byte comes first")
    void readsLittleEndian() {
        // 0x1234 stored low byte first.
        byte[] buffer = {0x34, 0x12, 0, 0};

        assertEquals(0x1234 / 32768f, LevelAnalyzer.sample(buffer, 0, 0, LevelAnalyzer.LEFT), 1e-7);
    }

    @Test
    @DisplayName("a negative sample keeps its sign")
    void signExtendsNegativeSamples() {
        // -1 as a signed 16-bit value is 0xFFFF. Without the cast to short this reads as 65535.
        byte[] buffer = {(byte) 0xFF, (byte) 0xFF, 0, 0};

        float value = LevelAnalyzer.sample(buffer, 0, 0, LevelAnalyzer.LEFT);

        assertTrue(value < 0, "a sign extension mistake turns quiet negative samples into loud ones");
        assertEquals(-1 / 32768f, value, 1e-7);
    }

    @Test
    @DisplayName("full-scale samples reach the ends of the range")
    void spansTheFullRange() {
        byte[] buffer = new byte[4];
        writeSample(buffer, 0, 1f);
        writeSample(buffer, 2, -1f);

        assertEquals(1f, LevelAnalyzer.sample(buffer, 0, 0, LevelAnalyzer.LEFT), 1e-4);
        assertEquals(-1f, LevelAnalyzer.sample(buffer, 0, 0, LevelAnalyzer.RIGHT), 1e-4);
    }

    @Test
    @DisplayName("the channels are read from alternating samples, not from alternating bytes")
    void deinterleavesTheChannels() {
        byte[] block = stereo(new float[] {0.5f, 0.5f, 0.5f, 0.5f},
                new float[] {0.25f, 0.25f, 0.25f, 0.25f});

        assertEquals(0.5f, LevelAnalyzer.rms(block, 0, block.length, LevelAnalyzer.LEFT), 1e-4);
        assertEquals(0.25f, LevelAnalyzer.rms(block, 0, block.length, LevelAnalyzer.RIGHT), 1e-4);
    }

    @Test
    @DisplayName("a hard-panned block moves one channel and leaves the other at silence")
    void hardPannedAudioSeparatesTheMeters() {
        // The check the whole feature exists for. Any bug that mixes the channels - measuring the
        // buffer as one stream, or summing before deinterleaving - makes these two equal.
        byte[] block = stereo(new float[] {0.8f, -0.8f, 0.8f, -0.8f}, new float[4]);

        Levels levels = new Levels();
        new LevelAnalyzer(levels).pcm(block, 0, block.length);

        assertEquals(0.8f, levels.leftRms(), 1e-3);
        assertEquals(0f, levels.rightRms(), 1e-6);
        assertNotEquals(levels.leftRms(), levels.rightRms());
    }

    @Test
    @DisplayName("peak reports the loudest single sample, which the average smooths away")
    void peakCatchesTransients() {
        // One frame at full scale among fifteen silent ones: a snare hit, in miniature.
        float[] left = new float[16];
        left[7] = 1f;
        byte[] block = stereo(left, new float[16]);

        float rms = LevelAnalyzer.rms(block, 0, block.length, LevelAnalyzer.LEFT);
        float peak = LevelAnalyzer.peak(block, 0, block.length, LevelAnalyzer.LEFT);

        assertEquals(0.25f, rms, 1e-3, "one frame in sixteen at full scale averages to a quarter");
        assertEquals(1f, peak, 1e-3, "but the transient itself was full scale");
    }

    @Test
    @DisplayName("peak ignores the sign, since a trough is as loud as a crest")
    void peakUsesMagnitude() {
        byte[] block = stereo(new float[] {-0.9f, 0.1f}, new float[2]);

        assertEquals(0.9f, LevelAnalyzer.peak(block, 0, block.length, LevelAnalyzer.LEFT), 1e-3);
    }

    @Test
    @DisplayName("an empty block measures as silence rather than dividing by zero")
    void handlesAnEmptyBlock() {
        byte[] empty = new byte[0];

        assertEquals(0f, LevelAnalyzer.rms(empty, 0, 0, LevelAnalyzer.LEFT));
        assertEquals(0f, LevelAnalyzer.peak(empty, 0, 0, LevelAnalyzer.RIGHT));
        assertEquals(0, LevelAnalyzer.frameCount(0));
    }

    @Test
    @DisplayName("only the stated part of the buffer is measured")
    void respectsOffsetAndLength() {
        // A loud first frame that the offset must exclude, then two quiet ones.
        byte[] block = stereo(new float[] {1f, 0.2f, 0.2f}, new float[] {1f, 0.2f, 0.2f});

        float peak = LevelAnalyzer.peak(block, 4, 8, LevelAnalyzer.LEFT);

        assertEquals(0.2f, peak, 1e-3, "the reused tail of a partly filled buffer must not count");
    }
}
