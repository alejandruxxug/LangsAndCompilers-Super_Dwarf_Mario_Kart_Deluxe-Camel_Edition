package com.eia.superdwarfkart.mood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mood that follows the music, and the fences that make that safe to ship.
 *
 * <p>The rate cap is the one worth stating plainly: <strong>a fullscreen overlay flashing at eight
 * hertz in a darkened classroom is a genuine problem, not a style question.</strong> A 200 BPM track
 * is 3.3 beats a second and would drive this past the cap on its own, so the cap is enforced here
 * rather than left to whatever the tempo happens to be.
 *
 * <p>The clamp is the other. Lifting a role's brightness moves it through the colour space, and in a
 * palette whose outline happens to be bright that movement is <em>towards</em> the very role the
 * highlight has to stay away from. "Clamp so the thresholds hold at every point in the modulation"
 * is only meaningful if something measures where that point is.
 */
@DisplayName("Mood reactivity")
class MoodReactivityTest {

    static List<Palette> palettes() {
        return Moods.builtIns().stream().map(Mood::palette).toList();
    }

    @Nested
    @DisplayName("the rate cap")
    class RateCap {

        @Test
        @DisplayName("takes at most three readings a second, whatever it is offered")
        void updatesAreCapped() {
            MoodReactivity reactivity = new MoodReactivity();
            int taken = 0;

            // Sixty offers over one second, which is what an AnimationTimer hands it.
            for (int frame = 0; frame < 60; frame++) {
                if (reactivity.update(frame / 60d, 1, 1)) {
                    taken++;
                }
            }

            assertTrue(taken <= MoodReactivity.MAX_UPDATE_HZ + 1,
                    "took " + taken + " readings in a second, past the "
                            + MoodReactivity.MAX_UPDATE_HZ + " Hz cap");
            assertTrue(taken >= 2, "took only " + taken + " - the modulation would not be visible");
        }

        @Test
        @DisplayName("rises immediately and falls slowly, so it swells rather than strobing")
        void energyRisesFastAndFallsSlowly() {
            MoodReactivity reactivity = new MoodReactivity();

            reactivity.update(0, 1, 1);
            double loud = reactivity.energy();
            reactivity.update(1, 0, 0);
            double afterOneSecondOfSilence = reactivity.energy();

            assertTrue(loud > 0.9, "a loud beat should take it near the top: " + loud);
            assertTrue(afterOneSecondOfSilence > 0,
                    "an instant fall would make the effect a strobe rather than a swell");
            assertTrue(afterOneSecondOfSilence < loud);
        }

        @Test
        @DisplayName("starts again when the clock goes backwards, which is a seek or a new track")
        void seekingBackwardsResets() {
            MoodReactivity reactivity = new MoodReactivity();
            reactivity.update(10, 1, 1);
            assertTrue(reactivity.energy() > 0);

            reactivity.update(0, 0, 0);

            assertEquals(0, reactivity.energy(), 1e-9,
                    "a stale reading carried across a seek is a mood reacting to music that is "
                            + "no longer playing");
        }
    }

    @Nested
    @DisplayName("what it modulates")
    class Modulation {

        /**
         * Brightness and alpha only, never hue. A mood is a set of hues the user chose; a
         * reactivity that moved them would be a different mood every bar.
         */
        @Test
        @DisplayName("moves only the accent and the highlight, and only in lightness")
        void onlyTwoRolesMove() {
            Palette base = Palette.defaultPalette();

            Palette lit = MoodReactivity.modulate(base, MoodReactivity.MAX_LIFT, 1);

            for (PaletteRole role : PaletteRole.values()) {
                boolean allowedToMove =
                        role == PaletteRole.ACCENT || role == PaletteRole.HIGHLIGHT;
                boolean moved = !GbaColor.toHex(base.color(role))
                        .equals(GbaColor.toHex(lit.color(role)));
                assertEquals(allowedToMove, moved,
                        role + (moved ? " moved and should not have" : " did not move and should"));
            }
        }

        @Test
        @DisplayName("keeps the hue of the roles it does move")
        void hueIsNeverTouched() {
            Palette base = Palette.defaultPalette();

            Palette lit = MoodReactivity.modulate(base, MoodReactivity.MAX_LIFT, 1);

            for (PaletteRole role : List.of(PaletteRole.ACCENT, PaletteRole.HIGHLIGHT)) {
                assertEquals(base.color(role).getHue(), lit.color(role).getHue(), 15,
                        role + " changed colour rather than brightness");
            }
        }

        @Test
        @DisplayName("returns the palette itself when nothing would move")
        void noEnergyIsNoWork() {
            Palette base = Palette.defaultPalette();

            assertSame(base, MoodReactivity.modulate(base, MoodReactivity.MAX_LIFT, 0));
            assertSame(base, MoodReactivity.modulate(base, 0, 1));
        }

        @Test
        @DisplayName("stays on the hardware grid, like every other colour in the application")
        void modulationIsSnapped() {
            Palette lit = MoodReactivity.modulate(
                    Palette.defaultPalette(), MoodReactivity.MAX_LIFT, 0.63);

            for (PaletteRole role : PaletteRole.values()) {
                assertEquals(lit.color(role), GbaColor.snap(lit.color(role)));
            }
        }
    }

    @Nested
    @DisplayName("the clamp")
    class Clamp {

        /**
         * The whole point of computing a lift per palette. A single global figure would be safe in
         * one mood and would close the gap between the traversal highlight and the ordinary outline
         * in another - silently, on the view that is watched from the back of a room.
         */
        @ParameterizedTest(name = "{0} stays valid at every point of its modulation")
        @MethodSource("com.eia.superdwarfkart.mood.MoodReactivityTest#palettes")
        void everyPointOfTheModulationIsValid(Palette palette) {
            double lift = MoodReactivity.safeLift(palette);

            for (double energy = 0; energy <= 1.0001; energy += 0.05) {
                Palette lit = MoodReactivity.modulate(palette, lift, energy);
                assertTrue(MoodValidator.isValid(lit),
                        palette.name() + " at energy " + energy + " produced "
                                + MoodValidator.validate(lit));
            }
        }

        @Test
        @DisplayName("never lifts past its own ceiling")
        void liftIsBounded() {
            for (Palette palette : palettes()) {
                double lift = MoodReactivity.safeLift(palette);
                assertTrue(lift >= 0 && lift <= MoodReactivity.MAX_LIFT,
                        palette.name() + " asked for a lift of " + lift);
            }
        }

        /**
         * Found by construction rather than asserted: a palette whose outline sits right beside its
         * highlight has no headroom at all, and the honest answer is zero rather than a small lift
         * that breaks the pair at full energy.
         */
        @Test
        @DisplayName("answers zero for a palette with no headroom, rather than breaking a pair")
        void noHeadroomMeansNoLift() {
            Palette tight = Palette.defaultPalette()
                    .withColor(PaletteRole.OUTLINE, GbaColor.web("#ff7ade"))
                    .withColor(PaletteRole.HIGHLIGHT, GbaColor.web("#ff5ede"));

            assertFalse(MoodValidator.isValid(tight),
                    "this test is pointless unless the pair starts too close");
            assertEquals(0, MoodReactivity.safeLift(tight), 1e-9);
        }
    }
}
