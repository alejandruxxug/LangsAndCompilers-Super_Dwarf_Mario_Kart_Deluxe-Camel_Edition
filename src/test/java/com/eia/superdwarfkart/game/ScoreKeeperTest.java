package com.eia.superdwarfkart.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tally: what a run is worth, and what it is graded on. */
class ScoreKeeperTest {

    /** What a streak of {@code n} coins is worth, the combo paying 1, 2, 3 ... up to its ceiling. */
    private static int worthOfStreak(int coins) {
        int total = 0;
        for (int coin = 1; coin <= coins; coin++) {
            total += Math.min(coin, ScoreKeeper.MAX_COMBO);
        }
        return total;
    }

    @Test
    @DisplayName("a coin adds to the balance and to what was collected")
    void collectingACoin() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 10);
        score.collectCoin();

        assertEquals(1, score.coins());
        assertEquals(1, score.coinsCollected());
    }

    @Test
    @DisplayName("a bump costs coins but not the record of having collected them")
    void aBumpDoesNotRewriteHistory() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 10);
        for (int coin = 0; coin < 8; coin++) {
            score.collectCoin();
        }
        score.hitObstacle();

        assertEquals(worthOfStreak(8) - ScoreKeeper.HIT_PENALTY_COINS, score.coins());
        assertEquals(8, score.coinsCollected(),
                "the rank measures how well the course was driven; a penalty must not be able to "
                        + "disguise or improve that");
        assertEquals(0.8, score.completion(), 1e-9);
    }

    @Test
    @DisplayName("the balance floors at zero rather than going into debt")
    void theBalanceFloorsAtZero() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 10);
        score.collectCoin();
        score.hitObstacle();

        assertEquals(0, score.coins(),
                "a debt the player cannot see the bottom of stops being a penalty");
    }

    @Test
    @DisplayName("breaking a bump pays, but not towards the rank")
    void breakingPaysOutsideTheRank() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 10);
        score.breakObstacle();

        assertEquals(ScoreKeeper.BREAK_BONUS_COINS, score.coins());
        assertEquals(0, score.coinsCollected());
        assertEquals(0, score.completion(), 1e-9,
                "a bonus that was never on the course could otherwise push the rank past 100%");
    }

    @Test
    @DisplayName("the speed class is what the coins are worth")
    void theClassWeighsTheScore() {
        for (SpeedClass speedClass : SpeedClass.values()) {
            ScoreKeeper score = new ScoreKeeper(speedClass, 10);
            for (int coin = 0; coin < 10; coin++) {
                score.collectCoin();
            }
            assertEquals(Math.round(worthOfStreak(10) * speedClass.scoreMultiplier()),
                    score.score());
        }
    }

    @Test
    @DisplayName("the rank does not depend on the speed class")
    void theRankIgnoresTheClass() {
        for (SpeedClass speedClass : SpeedClass.values()) {
            ScoreKeeper score = new ScoreKeeper(speedClass, 10);
            for (int coin = 0; coin < 10; coin++) {
                score.collectCoin();
            }
            assertSame(Rank.S, score.rank(),
                    "a clean run is a clean run; what the class changes is what it was worth");
        }
    }

    // ------------------------------------------------------------------
    // The combo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a coin is paid at the multiplier it just earned")
    void theComboPaysTheCoinThatEarnedIt() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 10);

        score.collectCoin();
        assertEquals(1, score.combo());
        assertEquals(1, score.coins(), "the first coin is worth one, not none");

        score.collectCoin();
        assertEquals(2, score.combo());
        assertEquals(3, score.coins(),
                "the second coin is paid at the level it took the meter to, not the one it left");
    }

    @Test
    @DisplayName("the combo multiplies the balance and can never touch the rank")
    void theComboCannotReachTheRank() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 100);
        for (int coin = 0; coin < 40; coin++) {
            score.collectCoin();
        }

        assertEquals(40, score.coinsCollected(),
                "the course held forty coins and forty were picked up, whatever they were worth");
        assertEquals(0.4, score.completion(), 1e-9);
        assertTrue(score.coins() > score.coinsCollected(),
                "the balance is what the combo multiplies");
    }

    @Test
    @DisplayName("the combo holds at its ceiling instead of climbing out of sight")
    void theComboHoldsAtTheCeiling() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 100);
        for (int coin = 0; coin < ScoreKeeper.MAX_COMBO + 20; coin++) {
            score.collectCoin();
        }

        assertEquals(ScoreKeeper.MAX_COMBO, score.combo());
        assertEquals(ScoreKeeper.MAX_COMBO, score.multiplier(),
                "past the top of the meter a pickup goes on paying what the meter says it does");
    }

    @Test
    @DisplayName("only a bump breaks the combo")
    void onlyABumpBreaksTheCombo() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 100);
        for (int coin = 0; coin < 6; coin++) {
            score.collectCoin();
        }
        score.collectStar();
        score.clearObstacle();
        score.breakObstacle();
        assertEquals(9, score.combo(),
                "a star, a jump and a break are all things that went right");

        score.hitObstacle();
        assertEquals(0, score.combo());
        assertEquals(1, score.multiplier(), "back to paying one for one");
    }

    @Test
    @DisplayName("a bump costs the streak and not five coins a level with it")
    void theBumpPenaltyIsNotScaledByTheCombo() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 100);
        for (int coin = 0; coin < ScoreKeeper.MAX_COMBO; coin++) {
            score.collectCoin();
        }
        int before = score.coins();
        score.hitObstacle();

        assertEquals(before - ScoreKeeper.HIT_PENALTY_COINS, score.coins(),
                "one mistake at the top of the meter has to stay recoverable");
    }

    @Test
    @DisplayName("the best combo is what the run reached, not what it ended on")
    void theBestComboSurvivesTheBump() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 100);
        for (int coin = 0; coin < 7; coin++) {
            score.collectCoin();
        }
        score.hitObstacle();

        assertEquals(0, score.combo());
        assertEquals(7, score.bestCombo());
    }

    @Test
    @DisplayName("a break is worth the bonus times the multiplier")
    void breakingIsMultipliedToo() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 100);
        for (int coin = 0; coin < 4; coin++) {
            score.collectCoin();
        }
        int before = score.coins();
        score.breakObstacle();

        assertEquals(before + ScoreKeeper.BREAK_BONUS_COINS * 5, score.coins(),
                "the break took the combo to five, and it is paid at five");
        assertEquals(worthOfStreak(4), before);
    }

    @Test
    @DisplayName("the rank thresholds")
    void rankThresholds() {
        assertSame(Rank.S, Rank.forCompletion(1.0));
        assertSame(Rank.S, Rank.forCompletion(0.90));
        assertSame(Rank.A, Rank.forCompletion(0.89));
        assertSame(Rank.A, Rank.forCompletion(0.75));
        assertSame(Rank.B, Rank.forCompletion(0.74));
        assertSame(Rank.B, Rank.forCompletion(0.60));
        assertSame(Rank.C, Rank.forCompletion(0.59));
        assertSame(Rank.C, Rank.forCompletion(0.40));
        assertSame(Rank.D, Rank.forCompletion(0.39));
        assertSame(Rank.D, Rank.forCompletion(0));
    }

    @Test
    @DisplayName("nonsense completions grade D rather than throwing")
    void rankSurvivesNonsense() {
        assertSame(Rank.D, Rank.forCompletion(Double.NaN));
        assertSame(Rank.D, Rank.forCompletion(-1));
    }

    @Test
    @DisplayName("a course with no coins on it is not graded at all")
    void anEmptyCourseIsNotRanked() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC50, 0);

        assertFalse(score.isRanked(),
                "reporting D would blame the player for a beatmap that produced nothing");
        assertEquals(0, score.completion(), 1e-9);
    }

    @Test
    @DisplayName("resetting clears everything")
    void resetting() {
        ScoreKeeper score = new ScoreKeeper(SpeedClass.CC150, 10);
        score.collectCoin();
        score.hitObstacle();
        score.breakObstacle();
        score.collectStar();

        score.reset();

        assertEquals(0, score.coins());
        assertEquals(0, score.coinsCollected());
        assertEquals(0, score.obstaclesHit());
        assertEquals(0, score.obstaclesBroken());
        assertEquals(0, score.starsCollected());
        assertEquals(0, score.combo());
        assertEquals(0, score.bestCombo());
        assertTrue(score.isRanked(), "the course still holds the coins it held");
    }
}
