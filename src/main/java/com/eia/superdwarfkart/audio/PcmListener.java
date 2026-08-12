package com.eia.superdwarfkart.audio;

/**
 * Receives every block of decoded audio on its way to the sound card.
 *
 * <p>This is the tap the level meters and, later, the rhythm game read from. The buffer handed
 * over is always the application's one format - 16-bit signed, stereo, interleaved
 * {@code [L0 R0 L1 R1 ...]}, little-endian - whatever the file on disk actually was, so a listener
 * never has to inspect a format.
 *
 * <p><strong>This runs on the playback thread, between decoding a block and writing it to the
 * line.</strong> Two rules follow, and breaking either is audible:
 *
 * <ul>
 *   <li><strong>Return quickly.</strong> Copy what is needed, compute, publish to atomics, return.
 *       Time spent here is time the sound card is not being fed, and a listener that stalls makes
 *       the audio stutter.</li>
 *   <li><strong>Never touch the scene graph.</strong> No JavaFX node, no property, and above all
 *       no {@code Platform.runLater} per block - that would queue tens of tasks a second onto the
 *       interface thread. The interface polls what listeners publish, from an
 *       {@code AnimationTimer}, at its own rate.</li>
 * </ul>
 *
 * <p>The buffer is reused between calls. A listener that needs to keep the samples must copy them.
 */
@FunctionalInterface
public interface PcmListener {

    /**
     * Called with one block of decoded PCM.
     *
     * @param buffer the block, in the application's format; valid only for the duration of the call
     * @param offset first byte of the block within the buffer
     * @param length how many bytes are valid, always a whole number of frames
     */
    void pcm(byte[] buffer, int offset, int length);
}
