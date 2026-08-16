package com.eia.superdwarfkart.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scripted driver, which two things depend on being an honest player: the smoke test's claim
 * that a generated course can be survived, and the runner's screenshot of a run in progress.
 */
class ScriptedDriverTest {

    /** @param entities the entities to place, in ascending time order */
    private static Course fixed(Entity... entities) {
        return Course.of("test-song", SpeedClass.CC50, 120, 0.5, List.of(entities));
    }

    @Test
    @DisplayName("it steers into the coins")
    void itCollectsWhatItCan() {
        Course course = fixed(new Coin(3, Lane.LEFT), new Coin(4, Lane.RIGHT),
                new Coin(5, Lane.LEFT));
        ScoreKeeper score = ScriptedDriver.driveLap(course);

        assertEquals(3, score.coinsCollected(), "all three were reachable in turn");
        assertEquals(3, score.bestCombo());
    }

    @Test
    @DisplayName("it goes round a bump rather than into it")
    void itDodges() {
        ScoreKeeper score = ScriptedDriver.driveLap(fixed(new Obstacle(3, Lane.CENTER)));

        assertEquals(0, score.obstaclesHit());
    }

    @Test
    @DisplayName("it jumps a wall, because there is nowhere to go")
    void itJumpsAWall() {
        Course course = fixed(new Obstacle(3, Lane.LEFT, true), new Obstacle(3, Lane.CENTER, true),
                new Obstacle(3, Lane.RIGHT, true));
        ScoreKeeper score = ScriptedDriver.driveLap(course);

        assertEquals(0, score.obstaclesHit(), "the only way past a wall is over it");
        assertEquals(1, score.combo(), "and clearing it is what the jump pays");
    }

    @Test
    @DisplayName("driving to a moment resolves what is behind it instead of writing it off")
    void drivingIsNotSeeking() {
        Course course = fixed(new Coin(1, Lane.CENTER), new Coin(2, Lane.CENTER),
                new Coin(3, Lane.CENTER), new Coin(9, Lane.CENTER));

        RunnerGame driven = new RunnerGame(course);
        ScriptedDriver.driveTo(driven, 5);

        RunnerGame jumped = new RunnerGame(course);
        jumped.update(5);

        assertEquals(3, driven.score().coinsCollected(),
                "the three coins before the moment were driven through");
        assertEquals(0, jumped.score().coinsCollected(),
                "where jumping the clock writes them all off, which is the rule a seek needs and "
                        + "the wrong thing for a picture of a run");
        assertSame(EntityState.PENDING, driven.stateOf(3), "and the one ahead is still to come");
    }

    @Test
    @DisplayName("it arrives exactly at the moment asked for, not a frame short of it")
    void itLandsOnTheMoment() {
        RunnerGame game = new RunnerGame(fixed(new Coin(1, Lane.CENTER)));
        ScriptedDriver.driveTo(game, 4.321);

        assertEquals(4.321, game.now(), 1e-9,
                "a screenshot aimed at a wall is taken on the wall, not up to a frame before it");
    }

    @Test
    @DisplayName("an empty course cannot be driven and says so rather than throwing")
    void anEmptyCourseIsNotDriven() {
        assertNull(ScriptedDriver.driveLap(Course.empty("s", SpeedClass.CC50)));
    }

    @Test
    @DisplayName("driving on from where a run already is does not start it again")
    void itContinuesFromWhereItIs() {
        RunnerGame game = new RunnerGame(fixed(new Coin(1, Lane.CENTER), new Coin(4, Lane.CENTER)));
        ScriptedDriver.driveTo(game, 2);
        assertEquals(1, game.score().coinsCollected());

        ScriptedDriver.driveTo(game, 5);
        assertEquals(2, game.score().coinsCollected(), "the first coin is not collected twice");
        assertTrue(game.now() >= 5);
    }
}
