package com.eia.superdwarfkart.audio;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four published values and the two mappings the meters read them through.
 */
class LevelsTest {

    private final Levels levels = new Levels();

    @Test
    @DisplayName("each of the four values is published and read back independently")
    void publishesFourIndependentValues() {
        levels.publish(0.1f, 0.2f, 0.3f, 0.4f);

        assertEquals(0.1f, levels.leftRms());
        assertEquals(0.2f, levels.rightRms());
        assertEquals(0.3f, levels.leftPeak());
        assertEquals(0.4f, levels.rightPeak());
    }

    @Test
    @DisplayName("reset drops every value to silence")
    void resetSilencesEverything() {
        levels.publish(0.5f, 0.6f, 0.7f, 0.8f);

        levels.reset();

        assertEquals(0f, levels.leftRms());
        assertEquals(0f, levels.rightRms());
        assertEquals(0f, levels.leftPeak());
        assertEquals(0f, levels.rightPeak());
    }

    @Test
    @DisplayName("a louder reading takes effect immediately")
    void decayRisesInstantly() {
        assertEquals(0.9f, Levels.decay(0.1f, 0.9f));
    }

    @Test
    @DisplayName("a quieter reading is approached over several frames, not jumped to")
    void decayFallsGradually() {
        float first = Levels.decay(1.0f, 0f);

        assertEquals(AppConfig.PEAK_DECAY, first, 1e-6);
        assertTrue(first > 0, "a peak that vanished in one frame would never be seen");
        assertTrue(Levels.decay(first, 0f) < first, "and it must keep falling");
    }

    @Test
    @DisplayName("silence sits at the bottom of the scale and full scale at the top")
    void scaleCoversTheWholeBar() {
        assertEquals(0f, Levels.scale(0f));
        assertEquals(1f, Levels.scale(1f), 1e-6);
    }

    @Test
    @DisplayName("the scale is logarithmic, so ordinary music is not stuck against the floor")
    void scaleIsLogarithmic() {
        // A tenth of full scale is -20 dBFS: two thirds of the way up a 60 dB meter, where a
        // linear bar would put it one tenth of the way up and every song would look identical.
        float typicalMusic = Levels.scale(0.1f);

        assertEquals(2 / 3d, typicalMusic, 0.01);
        assertTrue(typicalMusic > 0.5f, "a linear scale would leave music in the bottom tenth");
    }

    @Test
    @DisplayName("anything below the meter's floor reads as silence")
    void scaleClampsBelowTheFloor() {
        // -80 dBFS, well under the -60 dB floor.
        assertEquals(0f, Levels.scale(0.0001f));
    }
}
