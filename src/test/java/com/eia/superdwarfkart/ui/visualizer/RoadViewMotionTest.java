package com.eia.superdwarfkart.ui.visualizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camera work behind the queue view.
 *
 * <p>Motion is the whole point of this view and a screenshot cannot show it: a still frame of a
 * scrolling road is a static road. These curves are therefore split out as pure functions so the
 * shape of the shot can be asserted - that the racer really does leave the frame, that the camera
 * really does wait before giving chase, and that both settle exactly rather than nearly.
 */
@DisplayName("Road view motion")
class RoadViewMotionTest {

    @Nested
    @DisplayName("the racer's burst")
    class Burst {

        @Test
        @DisplayName("starts and ends exactly on the mark")
        void settlesOnTheMark() {
            assertEquals(0, RoadView.burstFraction(0), 1e-9);
            assertEquals(0, RoadView.burstFraction(1), 1e-9);
        }

        @Test
        @DisplayName("carries him fully clear of the frame at its peak")
        void clearsTheFrame() {
            double peak = 0;
            for (int i = 0; i <= 1000; i++) {
                peak = Math.max(peak, RoadView.burstFraction(i / 1000d));
            }
            // 1.0 is defined as "off the right edge plus his own width", so nothing less than
            // that actually gets him out of shot.
            assertEquals(1, peak, 1e-6, "the burst must reach a full exit, not merely approach it");
        }

        @Test
        @DisplayName("rises then falls, without doubling back")
        void risesThenFalls() {
            double previous = -1;
            boolean falling = false;
            for (int i = 0; i <= 1000; i++) {
                double value = RoadView.burstFraction(i / 1000d);
                if (value < previous - 1e-9) {
                    falling = true;
                } else if (falling && value > previous + 1e-9) {
                    throw new AssertionError("the burst rose again after starting to fall at p="
                            + i / 1000d + "; he would appear to lurch forward twice");
                }
                previous = value;
            }
            assertTrue(falling, "the burst must come back down, or he never returns to his mark");
        }

        @Test
        @DisplayName("stays inside its range for any progress value")
        void staysInRange() {
            for (int i = -50; i <= 150; i++) {
                double value = RoadView.burstFraction(i / 100d);
                assertTrue(value >= 0 && value <= 1,
                        "burst out of range at p=" + i / 100d + ": " + value);
            }
        }
    }

    @Nested
    @DisplayName("the camera's chase")
    class Chase {

        @Test
        @DisplayName("holds still at first, so he is seen pulling away")
        void holdsBeforeChasing() {
            assertEquals(0, RoadView.chaseFraction(0), 1e-9);

            // A real hold, not merely a slow start - measured rather than written down, so the
            // timing can be retuned without this quietly becoming an assertion about nothing.
            double stillUntil = 0;
            for (int i = 0; i <= 1000; i++) {
                double p = i / 1000d;
                if (RoadView.chaseFraction(p) > 0) {
                    break;
                }
                stillUntil = p;
            }
            assertTrue(stillUntil >= 0.05,
                    "the camera must wait a beat before giving chase, or there is nothing to"
                            + " chase; it started moving at p=" + stillUntil);
        }

        @Test
        @DisplayName("arrives exactly, rather than nearly")
        void arrivesExactly() {
            assertEquals(1, RoadView.chaseFraction(1), 1e-9);
        }

        @Test
        @DisplayName("never runs backwards - which is the whole argument the view makes")
        void neverReverses() {
            double previous = 0;
            for (int i = 0; i <= 1000; i++) {
                double value = RoadView.chaseFraction(i / 1000d);
                assertTrue(value >= previous - 1e-9,
                        "the camera moved backwards at p=" + i / 1000d
                                + "; a queue has no shot that looks behind it");
                previous = value;
            }
        }

        @Test
        @DisplayName("the loop back is a whip, not a drive down the road")
        void theLoopIsAWhip() {
            assertEquals(0, RoadView.whipFraction(0), 1e-9);
            assertEquals(1, RoadView.whipFraction(1), 1e-9);

            // Past the tail is the head and it costs one pointer hop. If the camera covered the
            // road at an even pace it would look like a drive back down the whole ring, which is
            // the wrong complexity: O(n) instead of O(1). So the swing has to be brief.
            double moving = 0;
            for (int i = 0; i < 1000; i++) {
                double a = RoadView.whipFraction(i / 1000d);
                double b = RoadView.whipFraction((i + 1) / 1000d);
                if (b - a > 1e-9) {
                    moving++;
                }
            }
            assertTrue(moving / 1000 < 0.4,
                    "the camera should swing round in a fraction of the move, but it was moving"
                            + " for " + (moving / 10) + "% of it");
        }

        @Test
        @DisplayName("the loop back never runs backwards either")
        void theLoopNeverReverses() {
            double previous = 0;
            for (int i = 0; i <= 1000; i++) {
                double value = RoadView.whipFraction(i / 1000d);
                assertTrue(value >= previous - 1e-9, "the whip reversed at p=" + i / 1000d);
                previous = value;
            }
        }

        @Test
        @DisplayName("the racer is already clear of the frame before the loop whips round")
        void heIsGoneBeforeTheWhip() {
            // He has to leave at one end and arrive at the other. If the camera swung while he
            // was still in shot he would visibly slide backwards down the road he just drove.
            double peak = peakOfBurst();
            assertTrue(RoadView.whipFraction(peak) < 0.5,
                    "the whip should not be underway while he is still pulling away; at the burst"
                            + " peak it had covered " + RoadView.whipFraction(peak));
        }

        @Test
        @DisplayName("still trails the racer at the moment he is furthest ahead")
        void trailsThePeak() {
            // If the camera had already arrived by the time he peaks, he would never leave frame
            // and the shot would be a slide rather than a chase. The peak is found rather than
            // written down, so retuning the burst cannot quietly invalidate this.
            double peakAt = peakOfBurst();
            assertTrue(RoadView.chaseFraction(peakAt) < 0.5,
                    "the camera should still be well behind when the burst peaks at p=" + peakAt
                            + ", but had already covered " + RoadView.chaseFraction(peakAt));
        }

        @Test
        @DisplayName("spends most of the move catching up, not waiting")
        void theCatchUpIsTheLongPart() {
            // The break is quick and the recovery is long: run the catch-up fast and he snaps
            // back into shot rather than being caught, which reads as a glitch rather than a shot.
            assertTrue(peakOfBurst() < 0.35,
                    "he should be furthest ahead early, leaving the rest of the move to be caught;"
                            + " peak was at p=" + peakOfBurst());
        }

        /** @return the progress value at which the burst is at its furthest */
        static double peakOfBurst() {
            double peakAt = 0;
            double peak = -1;
            for (int i = 0; i <= 1000; i++) {
                double p = i / 1000d;
                double value = RoadView.burstFraction(p);
                if (value > peak) {
                    peak = value;
                    peakAt = p;
                }
            }
            return peakAt;
        }
    }
}
