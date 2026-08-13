package com.eia.superdwarfkart.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curve that turns "how long until this arrives" into "how far down the road it is".
 *
 * <p>Worth a test of its own because getting it wrong is invisible in a screenshot and fatal to
 * play. The first version used a real {@code 1/z} divide with depth linear in time: a still frame
 * of it looked like a perfectly good road, and in motion an entity hung around the horizon for most
 * of its life and then covered half the screen in the last tenth of it. Nothing about where a thing
 * was told you when it would arrive - which is the only job this projection has.
 */
class RunnerProjectionTest {

    @Test
    @DisplayName("it spans the road exactly, from the vanishing point to the racer")
    void itSpansTheRoad() {
        assertEquals(0, RunnerView.screenFraction(0), 1e-9,
                "an entity has to appear at the horizon; anywhere else it pops into existence");
        assertEquals(1, RunnerView.screenFraction(1), 1e-9,
                "and reach the racer's line exactly on its beat - that is the whole synchronisation");
    }

    @Test
    @DisplayName("it never goes backwards")
    void itIsMonotonic() {
        double previous = -1;
        for (int step = 0; step <= 100; step++) {
            double at = RunnerView.screenFraction(step / 100d);
            assertTrue(at > previous, "the road doubled back at " + step + "%");
            previous = at;
        }
    }

    @Test
    @DisplayName("progress out of range is clamped rather than extrapolated")
    void itClamps() {
        assertEquals(0, RunnerView.screenFraction(-5), 1e-9);
        assertEquals(1, RunnerView.screenFraction(5), 1e-9);
    }

    @Test
    @DisplayName("screen position stays close to proportional to time, which is what makes it readable")
    void itIsNearlyUniform() {
        // Every tenth of the travel time has to move the entity a visible and comparable distance.
        // The old projection failed this on both counts: the first tenth moved it 1% of the road
        // and the last moved it 44%.
        double previous = 0;
        double smallest = 1;
        double largest = 0;
        for (int step = 1; step <= 10; step++) {
            double at = RunnerView.screenFraction(step / 10d);
            double moved = at - previous;
            smallest = Math.min(smallest, moved);
            largest = Math.max(largest, moved);
            previous = at;
        }
        assertTrue(largest / smallest < 3,
                "the fastest tenth covers " + String.format("%.0f", largest / smallest)
                        + " times as much road as the slowest; an entity that crawls and then "
                        + "whooshes cannot be timed");
    }

    @Test
    @DisplayName("halfway through the travel time is somewhere near halfway down the road")
    void halfwayIsHalfway() {
        double half = RunnerView.screenFraction(0.5);
        assertTrue(half > 0.3 && half < 0.5,
                "halfway through the approach the entity is " + String.format("%.0f%%", half * 100)
                        + " of the way down; the old projection put it at 10%, which is what "
                        + "\"everything pools at the horizon\" looks like");
    }

    @Test
    @DisplayName("it still foreshortens, or the road would be a ladder rather than a road")
    void itStillForeshortens() {
        assertTrue(RunnerView.screenFraction(0.5) < 0.5,
                "with no foreshortening at all the surface bands are evenly spaced and the "
                        + "picture reads as flat");
    }

    // ------------------------------------------------------------------
    // Which way the road goes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the surface travels towards the racer, not away from him")
    void theSurfaceComesTowardsTheRacer() {
        double travel = 2.0;
        // One band boundary, watched over a second of playback. It is a fixed instant on the
        // course, so it must come down the screen exactly as a coin placed at that instant would.
        double before = RunnerView.bandProgress(RunnerView.bandScroll(3.0, travel), 8);
        double after = RunnerView.bandProgress(RunnerView.bandScroll(3.5, travel), 8);

        assertTrue(after > before,
                "the road scrolled backwards: a band went from " + before + " to " + after
                        + ", so the surface climbs towards the horizon while the entities standing "
                        + "on it come down - which is what made the picture feel wrong");
    }

    @Test
    @DisplayName("the surface and the entities on it move at exactly the same rate")
    void theSurfaceKeepsPaceWithTheEntities() {
        double travel = 2.0;
        // An entity's progress is 1 - (beatTime - now) / travel, so half a second of a two-second
        // lookahead is a quarter of the road. The surface has to cover the same ground in the same
        // time or the two slide against each other and nothing on the road can be timed by eye.
        double entityMoved = 0.5 / travel;
        double bandMoved = RunnerView.bandProgress(RunnerView.bandScroll(3.5, travel), 8)
                - RunnerView.bandProgress(RunnerView.bandScroll(3.0, travel), 8);

        assertEquals(entityMoved, bandMoved, 1e-9,
                "the surface must travel exactly as an entity does, or the road slides under the "
                        + "things standing on it");
    }

    @Test
    @DisplayName("an entity grows continuously as it comes in rather than jumping between sizes")
    void entitiesGrowSmoothly() {
        double previous = 0;
        int steps = 0;
        for (int step = 0; step <= 100; step++) {
            double scale = RunnerView.entityScale(step / 100d);
            if (scale != previous) {
                steps++;
                previous = scale;
            }
        }
        assertTrue(steps > 50,
                "the sprite changed size only " + steps + " times across the whole road; snapping "
                        + "between a handful of sizes makes a sprite appear to jump position at "
                        + "the moment the player is trying to time it");
    }

    @Test
    @DisplayName("an entity never shrinks on its way in, and reaches full size at the racer")
    void entityScaleIsMonotonic() {
        double previous = -1;
        for (int step = 0; step <= 100; step++) {
            double scale = RunnerView.entityScale(step / 100d);
            assertTrue(scale >= previous, "the sprite shrank as it got closer");
            previous = scale;
        }
        assertEquals(4, RunnerView.entityScale(1), 1e-9,
                "at the racer's line a sprite is at full magnification");
    }
}
