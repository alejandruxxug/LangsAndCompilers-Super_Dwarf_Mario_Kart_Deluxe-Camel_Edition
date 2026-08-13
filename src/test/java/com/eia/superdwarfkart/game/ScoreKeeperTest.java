package com.eia.superdwarfkart.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tally: what a run is worth, and what it is graded on. */
class ScoreKeeperTest {

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

        assertEquals(8 - ScoreKeeper.HIT_PENALTY_COINS, score.coins());
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
            assertEquals(Math.round(10 * speedClass.scoreMultiplier()), score.score());
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
        assertTrue(score.isRanked(), "the course still holds the coins it held");
    }
}
