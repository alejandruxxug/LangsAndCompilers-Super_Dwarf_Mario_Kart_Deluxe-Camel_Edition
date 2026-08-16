package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.game.ScoreKeeper;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.Moods;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colour the combo lays over the whole picture, and the separations it must not destroy.
 *
 * <p>This wash goes over the entities rather than under them, which is what makes it worth a test
 * of its own: coins and bumps are two of the four roles whose entire job is to stay told apart, and
 * a tint that quietly closed the gap between them would throw nothing, look perfectly fine in a
 * screenshot, and fail live at exactly the moment the run was going best - the combo is at its
 * strongest precisely when the player has the most to lose by misreading the road.
 *
 * <p>Checked against every built-in mood, because the wash is a role and the roles move.
 */
@DisplayName("The combo heat")
class RunnerComboHeatTest {

    /** CIE76 separation below which two colours read as the same one across a room. */
    private static final double MIN_DELTA_E = 25;

    static List<Mood> moods() {
        return Moods.builtIns();
    }

    // ------------------------------------------------------------------
    // The curve
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a streak of one is not a streak and lights nothing at all")
    void oneIsNotACombo() {
        assertEquals(0, RunnerView.comboTarget(0), 1e-9);
        assertEquals(0, RunnerView.comboTarget(1), 1e-9,
                "a pickup at x1 is worth what it was worth before; lighting the screen for it "
                        + "would say the multiplier had started when it had not");
        assertEquals(0, RunnerView.comboAlpha(RunnerView.comboTarget(1), 1), 1e-9);
    }

    @Test
    @DisplayName("it fills exactly at the top of the meter")
    void itFillsAtTheCeiling() {
        assertEquals(1, RunnerView.comboTarget(ScoreKeeper.MAX_COMBO), 1e-9);
        assertEquals(1, RunnerView.comboTarget(ScoreKeeper.MAX_COMBO + 5), 1e-9,
                "the combo holds at its ceiling, so the picture must hold there too");
    }

    @Test
    @DisplayName("it only ever climbs")
    void itIsMonotonic() {
        double previous = -1;
        for (int combo = 0; combo <= ScoreKeeper.MAX_COMBO; combo++) {
            double at = RunnerView.comboTarget(combo);
            assertTrue(at >= previous, "the heat went backwards at a combo of " + combo);
            previous = at;
        }
    }

    @Test
    @DisplayName("the middle of the meter has something to show, which is why the beat is added")
    void theMiddleIsVisible() {
        double half = RunnerView.comboTarget(ScoreKeeper.MAX_COMBO / 2);
        double standing = RunnerView.comboAlpha(half, 0);
        double onTheBeat = RunnerView.comboAlpha(half, 1);

        assertTrue(standing < 0.03,
                "half way up, the standing tint has to stay out of the way - a clean run spends "
                        + "most of its time above this and it is what the game then looks like");
        assertTrue(onTheBeat > standing * 2,
                "so the beat is what carries it. Multiplying the surge by the standing tint made "
                        + "this a cube of a small number and it disappeared here, which is exactly "
                        + "where the player needs to see something being built");
    }

    @Test
    @DisplayName("the beat always adds, and never takes away")
    void theBeatOnlyAdds() {
        for (int step = 0; step <= 10; step++) {
            double heat = step / 10d;
            assertTrue(RunnerView.comboAlpha(heat, 1) >= RunnerView.comboAlpha(heat, 0),
                    "the beat dimmed the combo's colour at a heat of " + heat);
        }
    }

    // ------------------------------------------------------------------
    // What it must not do to the road
    // ------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("moods")
    @DisplayName("coins and bumps stay told apart through the heaviest wash")
    void coinsAndBumpsSurviveIt(Mood mood) {
        Color coin = washed(mood.color(PaletteRole.POSITIVE), mood.color(RunnerView.COMBO_ROLE));
        Color bump = washed(mood.color(PaletteRole.NEGATIVE), mood.color(RunnerView.COMBO_ROLE));

        assertTrue(deltaE(coin, bump) >= MIN_DELTA_E,
                mood.id() + ": a full combo on a beat closed coins and bumps to "
                        + Math.round(deltaE(coin, bump)) + " - a run cannot become unreadable "
                        + "because it is going well");
    }

    @ParameterizedTest
    @MethodSource("moods")
    @DisplayName("the coins and the star stay off the road they are seen against")
    void theStarSurvivesIt(Mood mood) {
        // Sharper than it looks now that the wash is PRIMARY itself: the star cannot move at all
        // and the road is being pulled towards it, so this is the separation the choice of role
        // costs the most. It is the pair that would fail first if the tint were raised again.
        Color star = washed(mood.color(PaletteRole.PRIMARY), mood.color(RunnerView.COMBO_ROLE));
        Color road = washed(mood.color(PaletteRole.SURFACE_RAISED),
                mood.color(RunnerView.COMBO_ROLE));

        assertTrue(deltaE(star, road) >= MIN_DELTA_E,
                mood.id() + ": the coins and the star faded into the road under the wash");
    }

    @ParameterizedTest
    @MethodSource("moods")
    @DisplayName("the wash never buries the picture, whatever the mood")
    void itStaysAWash(Mood mood) {
        double heaviest = RunnerView.comboAlpha(1, 1);

        assertTrue(heaviest <= 0.35, "at " + heaviest + " this stops being a tint and starts "
                + "being a coat of paint - the same ceiling a mood's own overlay layers are "
                + "capped at, and for the same reason");
    }

    /**
     * @param face the colour as drawn
     * @param over the wash laid over it, at its heaviest - a full combo on the beat
     * @return what the eye actually receives
     */
    private static Color washed(Color face, Color over) {
        double alpha = RunnerView.comboAlpha(1, 1);
        return face.interpolate(over, alpha);
    }

    /** CIE76: plain Euclidean distance in Lab, which is what the mood system specifies. */
    private static double deltaE(Color a, Color b) {
        double[] first = lab(a);
        double[] second = lab(b);
        double dl = first[0] - second[0];
        double da = first[1] - second[1];
        double db = first[2] - second[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static double[] lab(Color c) {
        double r = linear(c.getRed());
        double g = linear(c.getGreen());
        double b = linear(c.getBlue());

        // sRGB to CIE XYZ, D65.
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;

        return new double[] {116 * pivot(y) - 16, 500 * (pivot(x) - pivot(y)),
                200 * (pivot(y) - pivot(z))};
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double pivot(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (903.3 * t + 16) / 116;
    }
}
