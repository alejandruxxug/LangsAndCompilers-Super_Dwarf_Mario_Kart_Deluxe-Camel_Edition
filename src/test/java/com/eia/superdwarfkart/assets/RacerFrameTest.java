package com.eia.superdwarfkart.assets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame layout of a racer sheet.
 *
 * <p>These read as trivial, and they are the assertions that stop the kart flickering between a
 * side view, a rear view and a menu icon: the failure is a visual one that no other test can see,
 * and it comes back the moment somebody loops the sheet the way every other sprite is looped.
 */
@DisplayName("RacerFrame")
class RacerFrameTest {

    @Test
    @DisplayName("maps each frame to its place in the sheet")
    void frameIndices() {
        assertEquals(0, RacerFrame.DRIVE_A.index());
        assertEquals(1, RacerFrame.DRIVE_B.index());
        assertEquals(2, RacerFrame.BACK.index());
        assertEquals(3, RacerFrame.ICON.index());
        assertEquals(RacerFrame.COUNT, RacerFrame.values().length);
    }

    @Test
    @DisplayName("the driving cycle uses only the first two frames")
    void drivingNeverLeavesTheCycle() {
        Set<Integer> seen = new HashSet<>();
        for (int step = 0; step < 400; step++) {
            seen.add(RacerFrame.driving(step * 0.05));
        }
        assertEquals(Set.of(RacerFrame.DRIVE_A.index(), RacerFrame.DRIVE_B.index()), seen,
                "the rear view and the icon must never appear in the driving loop");
    }

    @Test
    @DisplayName("the driving cycle actually alternates")
    void drivingAlternates() {
        double halfCycle = 1 / RacerFrame.DRIVE_FPS;
        assertEquals(RacerFrame.DRIVE_A.index(), RacerFrame.driving(0));
        assertEquals(RacerFrame.DRIVE_B.index(), RacerFrame.driving(halfCycle));
        assertEquals(RacerFrame.DRIVE_A.index(), RacerFrame.driving(halfCycle * 2));
    }

    @Test
    @DisplayName("degrades to a still frame rather than misbehaving on a bad clock")
    void toleratesNonsenseInput() {
        assertEquals(RacerFrame.DRIVE_A.index(), RacerFrame.driving(-1));
        assertEquals(RacerFrame.DRIVE_A.index(), RacerFrame.driving(1, 0));
        assertEquals(RacerFrame.DRIVE_A.index(), RacerFrame.driving(1, -5));
    }

    @Test
    @DisplayName("a racer sheet holds exactly the frames the artwork ships")
    void matchesTheRealArtwork() {
        // The bundled racer sheets are 256x64: four 64x64 frames. If the art is ever redrawn with
        // a different cycle length, this is the constant that has to move with it.
        assertTrue(RacerFrame.COUNT == 4, "racer sheets are authored as four frames");
    }
}
