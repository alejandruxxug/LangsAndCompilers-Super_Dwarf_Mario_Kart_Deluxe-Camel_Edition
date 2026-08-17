package com.eia.superdwarfkart.ui;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The runner lays the beat, the combo and the last event over the finished frame. Each is a
 * constant colour covering the whole canvas, and painting them one after another is three
 * full-canvas alpha blends a frame - which matters here far more than it usually would, because
 * JavaFX cannot initialise a GPU pipeline on this project's machine and falls back to Prism's
 * software renderer, so every blend is the CPU touching every pixel of a maximised window.
 *
 * <p>{@link RunnerView#compositeWashes} collapses the three into one. This holds it to the bar that
 * makes that safe: <strong>the collapsed colour must be the one three ordered blends would have
 * arrived at</strong>, over any destination. A faster wash that quietly shifted the colour of the
 * beat would be a look drifting rather than an optimisation, and neither a screenshot nor a frame
 * counter would report it.
 *
 * <p>Note what this compares and what it does not. The blending here is done in {@code double}, so
 * this is a statement about the arithmetic. On the real framebuffer the old path rounded to eight
 * bits after each of the three fills where this rounds once, so the drawn frame shifts by up to one
 * level of 255 - an eighth of one step of the 5-bit grid the whole application is snapped to, and in
 * the direction of the more faithful answer. That was measured by differencing two driven
 * screenshots rather than reasoned about; see {@code RunnerView.compositeWashes}.
 */
class RunnerWashCompositeTest {

    /** For the cases that are exact to the bit; the general one is checked in 8-bit channels below. */
    private static final double EPSILON = 1e-6;

    // ------------------------------------------------------------------
    // The property that makes the collapse legitimate
    // ------------------------------------------------------------------

    @Test
    @DisplayName("one composited wash lands on the same pixel as three laid down in order")
    void theCollapseChangesNoPixel() {
        Random random = new Random(20260816L);
        double worst = 0;
        for (int trial = 0; trial < 2000; trial++) {
            Color under = randomWash(random);
            Color middle = randomWash(random);
            Color over = randomWash(random);
            // An arbitrary destination: whatever the road, the entities and the kart left behind.
            Color destination = Color.color(random.nextDouble(), random.nextDouble(),
                    random.nextDouble());

            Color sequential = sourceOver(over, sourceOver(middle, sourceOver(under, destination)));
            Color collapsed = RunnerView.compositeWashes(under, middle, over);
            Color once = collapsed == null ? destination : sourceOver(collapsed, destination);

            // Compared as the eight-bit channels a framebuffer actually holds. The arithmetic is
            // algebraically exact, but the collapsed path stores one extra intermediate in a
            // javafx.scene.paint.Color - which keeps its channels as floats - so the two can differ
            // by about 1e-8. That is five orders of magnitude below one channel step, and this is
            // the assertion that says so in the units that matter rather than hiding it in a
            // tolerance.
            assertEquals(channel(sequential.getRed()), channel(once.getRed()),
                    "red changed on trial " + trial);
            assertEquals(channel(sequential.getGreen()), channel(once.getGreen()),
                    "green changed on trial " + trial);
            assertEquals(channel(sequential.getBlue()), channel(once.getBlue()),
                    "blue changed on trial " + trial);

            worst = Math.max(worst, Math.abs(sequential.getRed() - once.getRed()));
            worst = Math.max(worst, Math.abs(sequential.getGreen() - once.getGreen()));
            worst = Math.max(worst, Math.abs(sequential.getBlue() - once.getBlue()));
        }
        // Half of one eight-bit step is 1/510. Anything approaching that would mean the collapse
        // was drifting rather than rounding, and the channel comparison above could pass on a lucky
        // seed while a different palette failed it.
        assertEquals(true, worst < 1.0 / 510,
                "the collapse drifted by " + worst + ", which is within reach of a channel step");
    }

    @Test
    @DisplayName("the order the three are handed in is still the order they are seen in")
    void orderIsPreserved() {
        Color red = Color.color(1, 0, 0, 0.5);
        Color blue = Color.color(0, 0, 1, 0.5);
        Color ground = Color.BLACK;

        Color redOverBlue = sourceOver(RunnerView.compositeWashes(blue, red, null), ground);
        Color blueOverRed = sourceOver(RunnerView.compositeWashes(red, blue, null), ground);

        // Whichever went on top must dominate. If the collapse were order-blind these would match,
        // and the bump's alarm - which is deliberately drawn last - would stop being on top.
        assertEquals(true, redOverBlue.getRed() > redOverBlue.getBlue(),
                "the wash handed in last must still be the one seen on top");
        assertEquals(true, blueOverRed.getBlue() > blueOverRed.getRed(),
                "the wash handed in last must still be the one seen on top");
    }

    // ------------------------------------------------------------------
    // Nothing to draw
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no washes at all means no fill, rather than a transparent one")
    void nothingToDrawIsNothingDrawn() {
        assertNull(RunnerView.compositeWashes(null, null, null),
                "a frame with no beat, no combo and no recent event must skip the fill entirely "
                        + "rather than blend a fully transparent rectangle over the whole canvas");
        assertNull(RunnerView.compositeWashes(Color.color(1, 0, 0, 0), null, null),
                "a wash that has faded to nothing is nothing");
    }

    @Test
    @DisplayName("a single wash survives untouched")
    void oneWashIsItself() {
        Color only = Color.color(0.84, 0.73, 0.31, 0.33);
        Color collapsed = RunnerView.compositeWashes(null, only, null);
        assertNotNull(collapsed);
        assertEquals(only.getRed(), collapsed.getRed(), EPSILON);
        assertEquals(only.getGreen(), collapsed.getGreen(), EPSILON);
        assertEquals(only.getBlue(), collapsed.getBlue(), EPSILON);
        assertEquals(only.getOpacity(), collapsed.getOpacity(), EPSILON);
    }

    @Test
    @DisplayName("an opaque wash on top hides everything under it")
    void anOpaqueWashHidesTheRest() {
        Color collapsed = RunnerView.compositeWashes(
                Color.color(1, 0, 0, 0.9), Color.color(0, 1, 0, 0.9), Color.color(0, 0, 1, 1));
        assertNotNull(collapsed);
        assertEquals(1, collapsed.getOpacity(), EPSILON);
        assertEquals(0, collapsed.getRed(), EPSILON);
        assertEquals(0, collapsed.getGreen(), EPSILON);
        assertEquals(1, collapsed.getBlue(), EPSILON);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** @return the eight-bit channel a framebuffer would store for a 0..1 component */
    private static int channel(double component) {
        return (int) Math.round(Math.clamp(component, 0d, 1d) * 255);
    }

    /** @return a wash, or {@code null} a fifth of the time - the frames where one is not running */
    private static Color randomWash(Random random) {
        if (random.nextInt(5) == 0) {
            return null;
        }
        return Color.color(random.nextDouble(), random.nextDouble(), random.nextDouble(),
                random.nextDouble());
    }

    /**
     * Textbook source-over, which is what the framebuffer does when a translucent rectangle is
     * filled over it.
     *
     * @param source      what is being laid down, or {@code null} for nothing
     * @param destination what is already there
     * @return the result, opaque
     */
    private static Color sourceOver(Color source, Color destination) {
        if (source == null) {
            return destination;
        }
        double at = source.getOpacity();
        double keep = 1 - at;
        return Color.color(
                source.getRed() * at + destination.getRed() * keep,
                source.getGreen() * at + destination.getGreen() * keep,
                source.getBlue() * at + destination.getBlue() * keep);
    }
}
