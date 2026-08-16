package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.analysis.Beatmap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collision rules, driven by a clock rather than by a window.
 *
 * <p>Every one of these is a question a screenshot cannot answer and a person watching the game
 * cannot answer reliably either: whether a coin in another lane was really left alone, whether the
 * star beat the jump to an obstacle, whether seeking forward quietly awarded a hundred coins.
 */
class RunnerGameTest {

    /** Advances a run to a moment the way the frame loop would, one frame at a time. */
    private static void runTo(RunnerGame game, double seconds) {
        for (double at = game.now(); at < seconds; at += 1 / 60d) {
            game.update(Math.min(at, seconds));
        }
        game.update(seconds);
    }

    /**
     * Advances past the moment an entity on a beat is judged.
     *
     * <p>Judgement is deliberately {@link RunnerGame#JUDGEMENT_GRACE_SECONDS} later than the beat -
     * that lateness is the whole of the timing window's late half - so a test that ran to the beat
     * and asked what had happened would be asking before anything had. Written in terms of the
     * constant rather than as a number, so retuning the grace does not silently turn these into
     * tests of nothing.
     *
     * @param game the run to advance
     * @param beat the entity's beat, in seconds
     */
    private static void runPastJudgement(RunnerGame game, double beat) {
        runTo(game, beat + RunnerGame.JUDGEMENT_GRACE_SECONDS + 1 / 60d);
    }

    /**
     * Builds a course holding exactly the entities asked for, at 50cc and 120 BPM.
     *
     * <p>Generation is seeded, so "a coin in the left lane at five seconds" cannot be asked for by
     * generating - the whole point of {@link Course#generate} is that the caller does not choose.
     * These tests are about the rules rather than the layout, so they lay the course out by hand
     * through {@link Course#of}, which is package-private for exactly this reason and is why this
     * test sits in the same package.
     *
     * @param entities the entities to place, in ascending time order
     * @return a course holding exactly them
     */
    private static Course fixed(Entity... entities) {
        return Course.of("test-song", SpeedClass.CC50, 120, 0.5, List.of(entities));
    }

    // ------------------------------------------------------------------
    // Coins
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a coin in the racer's lane is collected on its beat")
    void coinInLaneIsCollected() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.CENTER)));

        runTo(game, 4.9);
        assertEquals(0, game.score().coinsCollected(), "collected before its beat");

        runPastJudgement(game, 5);
        assertEquals(1, game.score().coinsCollected());
        assertSame(EntityState.COLLECTED, game.stateOf(0));
    }

    @Test
    @DisplayName("a coin in another lane is left alone")
    void coinInAnotherLaneIsMissed() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.LEFT)));
        runPastJudgement(game, 5);

        assertEquals(0, game.score().coinsCollected());
        assertSame(EntityState.MISSED, game.stateOf(0));
    }

    @Test
    @DisplayName("moving into a coin's lane before its beat collects it")
    void movingInTimeCollects() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.RIGHT)));
        runTo(game, 4.5);
        game.moveRight();
        runPastJudgement(game, 5);

        assertEquals(1, game.score().coinsCollected());
    }

    // ------------------------------------------------------------------
    // Obstacles - the three ways past one
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a bump in the racer's lane costs coins and grants a moment's protection")
    void bumpInLaneHits() {
        RunnerGame game = new RunnerGame(fixed(new Coin(1, Lane.CENTER), new Coin(2, Lane.CENTER),
                new Coin(3, Lane.CENTER), new Coin(4, Lane.CENTER), new Coin(5, Lane.CENTER),
                new Coin(6, Lane.CENTER), new Obstacle(7, Lane.CENTER)));
        runPastJudgement(game, 7);

        assertSame(EntityState.HIT, game.stateOf(6));
        // Six coins in a row are worth 1+2+3+4+5+6 rather than six, because the combo was climbing
        // the whole way. The penalty is flat and comes off the balance they built.
        assertEquals(21 - ScoreKeeper.HIT_PENALTY_COINS, game.score().coins());
        assertEquals(6, game.score().coinsCollected(),
                "a penalty must not change how well the course was driven");
        assertTrue(game.isInvulnerable());
    }

    @Test
    @DisplayName("a bump in another lane is passed harmlessly")
    void bumpInAnotherLaneIsMissed() {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.LEFT)));
        runPastJudgement(game, 5);

        assertSame(EntityState.MISSED, game.stateOf(0));
        assertEquals(0, game.score().obstaclesHit());
    }

    @Test
    @DisplayName("jumping clears a bump in the racer's own lane")
    void jumpingClearsABump() {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.CENTER)));
        runTo(game, 4.8);
        game.jump();
        runPastJudgement(game, 5);

        assertSame(EntityState.CLEARED, game.stateOf(0));
        assertEquals(0, game.score().obstaclesHit());
    }

    @Test
    @DisplayName("a jump that has already landed does not clear anything")
    void aLandedJumpDoesNotClear() {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.CENTER)));
        runTo(game, 4.0);
        game.jump();
        runPastJudgement(game, 5);

        assertSame(EntityState.HIT, game.stateOf(0),
                "the jump lasts " + RunnerGame.JUMP_SECONDS + "s and was a second early");
    }

    @Test
    @DisplayName("a jump pressed just after the bump arrived still clears it")
    void aSlightlyLateJumpStillClears() {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.CENTER)));
        // Late, which is the half of the window that did not exist before: the entity is on the
        // screen at the racer and the key lands a moment after. A player cannot tell this apart
        // from an on-time press, because a frame late looks exactly like on time.
        runTo(game, 5 + RunnerGame.JUDGEMENT_GRACE_SECONDS / 2);
        game.jump();
        runPastJudgement(game, 5);

        assertSame(EntityState.CLEARED, game.stateOf(0));
    }

    @Test
    @DisplayName("a jump later than the grace still counts as a miss")
    void tooLateIsStillTooLate() {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.CENTER)));
        runPastJudgement(game, 5);
        game.jump();

        assertSame(EntityState.HIT, game.stateOf(0),
                "the grace is a timing window, not an undo");
    }

    @Test
    @DisplayName("the whole timing window is nearly symmetric about the beat now")
    void theWindowHasBothHalves() {
        // Pressed a jump's length before the beat is the earliest that still covers it, and a
        // grace after it is the latest. Both are checked here because the complaint that started
        // this was that the late half did not exist, and a number in a constant does not prove it
        // reaches the rules.
        assertSame(EntityState.CLEARED, jumpAt(5 - RunnerGame.JUMP_SECONDS + 0.02),
                "the earliest press that still covers the beat");
        assertSame(EntityState.CLEARED, jumpAt(5 + RunnerGame.JUDGEMENT_GRACE_SECONDS - 0.02),
                "the latest press that still covers the beat");
        assertSame(EntityState.HIT, jumpAt(5 - RunnerGame.JUMP_SECONDS - 0.02),
                "and one that landed before the beat came round");
    }

    /**
     * @param pressedAt when the jump key is pressed, in seconds
     * @return what became of an obstacle sitting on the beat at five seconds
     */
    private static EntityState jumpAt(double pressedAt) {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.CENTER)));
        runTo(game, pressedAt);
        game.jump();
        runPastJudgement(game, 5);
        return game.stateOf(0);
    }

    @Test
    @DisplayName("the grace does not punish steering: the lane that counts is the lane on the beat")
    void theGraceDoesNotPunishSteering() {
        RunnerGame game = new RunnerGame(fixed(new Obstacle(5, Lane.LEFT)));
        // Out of the bump's lane on the beat, and into it immediately afterwards. Judged on the
        // current lane instead, this would be a hit for a move made after the thing had gone by -
        // which is the trap that comes free with a delayed judgement.
        runTo(game, 5.01);
        game.moveLeft();
        runPastJudgement(game, 5);

        assertSame(EntityState.MISSED, game.stateOf(0));
        assertEquals(0, game.score().obstaclesHit());
    }

    @Test
    @DisplayName("the protected spell after a bump swallows the next one without a second penalty")
    void invulnerabilityAbsorbsTheNextBump() {
        RunnerGame game = new RunnerGame(fixed(
                new Obstacle(5, Lane.CENTER), new Obstacle(5.5, Lane.CENTER)));
        runPastJudgement(game, 5.5);

        assertSame(EntityState.HIT, game.stateOf(0));
        assertSame(EntityState.MISSED, game.stateOf(1));
        assertEquals(1, game.score().obstaclesHit(),
                "a single mistake must not chain into a wipeout while the player is recovering");
    }

    // ------------------------------------------------------------------
    // The star
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a star runs for the right number of beats and breaks what it drives through")
    void starBreaksObstacles() {
        RunnerGame game = new RunnerGame(fixed(
                new Star(5, Lane.CENTER), new Obstacle(6, Lane.CENTER)));
        runPastJudgement(game, 5);

        assertTrue(game.isStarred());
        // Counted from the star's own beat rather than from the moment it was judged, so by now a
        // grace period of it has already run - which is exactly what keeps the invulnerability
        // covering the same musical span whatever the grace is retuned to.
        assertEquals(RunnerGame.STAR_BEATS * 0.5 - RunnerGame.JUDGEMENT_GRACE_SECONDS,
                game.starRemainingSeconds(), 0.1,
                "the star is counted in beats so it lasts the same musical length on any track");

        runPastJudgement(game, 6);
        assertSame(EntityState.BROKEN, game.stateOf(1));
        // The star took the combo to one and the break took it to two, so the bonus is paid at two.
        assertEquals(ScoreKeeper.BREAK_BONUS_COINS * 2, game.score().coins());
        assertEquals(0, game.score().coinsCollected(),
                "coins broken out of a bump are not coins the course held, so they must not be "
                        + "able to push the rank past what was actually collected");
    }

    @Test
    @DisplayName("being starred beats jumping, so the bonus is not lost to a habit")
    void starBeatsJump() {
        RunnerGame game = new RunnerGame(fixed(
                new Star(5, Lane.CENTER), new Obstacle(6, Lane.CENTER)));
        runTo(game, 5.8);
        game.jump();
        runPastJudgement(game, 6);

        assertSame(EntityState.BROKEN, game.stateOf(1));
    }

    @Test
    @DisplayName("the star runs out")
    void starExpires() {
        RunnerGame game = new RunnerGame(fixed(
                new Star(5, Lane.CENTER), new Obstacle(10, Lane.CENTER)));
        runPastJudgement(game, 5);
        assertTrue(game.isStarred());

        runPastJudgement(game, 10);
        assertFalse(game.isStarred());
        assertSame(EntityState.HIT, game.stateOf(1));
    }

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    @Test
    @DisplayName("seeking forward writes off what was skipped rather than awarding it")
    void seekingForwardAwardsNothing() {
        RunnerGame game = new RunnerGame(fixed(
                new Coin(1, Lane.CENTER), new Coin(2, Lane.CENTER), new Coin(3, Lane.CENTER)));

        // One jump straight to the end, exactly as dragging the seek bar produces.
        game.update(10);

        assertEquals(0, game.score().coinsCollected(),
                "a course must not be collectable by dragging the playhead across it");
        assertSame(EntityState.MISSED, game.stateOf(0));
        assertSame(EntityState.MISSED, game.stateOf(2));
    }

    @Test
    @DisplayName("seeking backwards re-arms the stretch without clearing the tally")
    void seekingBackwardsRearms() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.CENTER)));
        runPastJudgement(game, 5);
        assertEquals(1, game.score().coinsCollected());

        game.update(2);
        assertSame(EntityState.PENDING, game.stateOf(0), "the coin is ahead again");
        assertEquals(1, game.score().coinsCollected(),
                "clearing the tally would throw a run away because somebody nudged the seek bar");
    }

    @Test
    @DisplayName("an entity noticed a frame late is still resolved normally")
    void aFrameLateStillCounts() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.CENTER)));
        runTo(game, 4.9);
        // One frame at 60 fps past the moment it is judged, which is the worst an on-time loop
        // ever is.
        game.update(5 + RunnerGame.JUDGEMENT_GRACE_SECONDS + 1 / 60d);

        assertEquals(1, game.score().coinsCollected());
    }

    @Test
    @DisplayName("restarting clears the run but keeps the course")
    void restartClearsTheRun() {
        Course course = fixed(new Coin(5, Lane.CENTER));
        RunnerGame game = new RunnerGame(course);
        runPastJudgement(game, 5);
        game.moveLeft();

        game.restart();

        assertEquals(0, game.score().coinsCollected());
        assertSame(EntityState.PENDING, game.stateOf(0));
        assertSame(Lane.starting(), game.lane());
        assertSame(course, game.course(), "a restart must not need a course to be generated again");
    }

    // ------------------------------------------------------------------
    // Steering
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the racer cannot be steered off the road")
    void steeringIsClamped() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.CENTER)));
        game.moveLeft();
        game.moveLeft();
        game.moveLeft();
        assertSame(Lane.LEFT, game.lane());

        game.moveRight();
        game.moveRight();
        game.moveRight();
        game.moveRight();
        assertSame(Lane.RIGHT, game.lane());
    }

    @Test
    @DisplayName("the lane changes at once and only the sprite glides")
    void theLaneChangesImmediately() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.LEFT)));
        runTo(game, 4.99);
        game.moveLeft();

        assertSame(Lane.LEFT, game.lane(),
                "a logical lane that eased across with the drawing would count a player who moved "
                        + "as still standing in front of the thing they moved away from");
        assertTrue(game.lanePosition() > Lane.LEFT.index(), "the sprite has not arrived yet");

        runPastJudgement(game, 5);
        assertEquals(1, game.score().coinsCollected());
    }

    @Test
    @DisplayName("the sprite finishes its glide")
    void theSpriteArrives() {
        RunnerGame game = new RunnerGame(fixed(new Coin(50, Lane.CENTER)));
        runTo(game, 1);
        game.moveLeft();
        runTo(game, 1 + RunnerGame.LANE_CHANGE_SECONDS + 0.01);

        assertEquals(Lane.LEFT.index(), game.lanePosition(), 1e-9);
    }

    @Test
    @DisplayName("a jump follows an arc and lands")
    void theJumpArcs() {
        RunnerGame game = new RunnerGame(fixed(new Coin(50, Lane.CENTER)));
        runTo(game, 1);
        game.jump();

        assertEquals(0, game.jumpHeight(), 0.05, "it starts on the ground");
        runTo(game, 1 + RunnerGame.JUMP_SECONDS / 2);
        assertEquals(1, game.jumpHeight(), 0.05, "and reaches its apex halfway");
        runTo(game, 1 + RunnerGame.JUMP_SECONDS + 0.01);
        assertFalse(game.isJumping(), "and comes down");
        assertEquals(0, game.jumpHeight(), 1e-9);
    }

    // ------------------------------------------------------------------
    // What the view is told to draw
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an entity's progress is 0 when it appears and 1 on its beat")
    void progressSpansTheLookahead() {
        Course course = fixed(new Coin(10, Lane.CENTER));
        RunnerGame game = new RunnerGame(course);
        double travel = course.travelTimeSeconds();

        runTo(game, 10 - travel);
        assertEquals(0, game.progressOf(0), 1e-6);

        runTo(game, 10 - travel / 2);
        assertEquals(0.5, game.progressOf(0), 1e-6);
    }

    @Test
    @DisplayName("only what is on the course is offered to the view")
    void theVisibleWindowIsTheLookahead() {
        Course course = fixed(new Coin(3, Lane.CENTER), new Coin(60, Lane.CENTER));
        RunnerGame game = new RunnerGame(course);

        runTo(game, 3 - course.travelTimeSeconds() + 0.01);
        assertEquals(0, game.firstVisible());
        assertEquals(0, game.lastVisible(), "the coin a minute away is not on screen");
    }

    @Test
    @DisplayName("a resolved entity stays visible long enough for its effect to be drawn")
    void resolvedEntitiesLinger() {
        RunnerGame game = new RunnerGame(fixed(new Coin(5, Lane.CENTER)));
        runPastJudgement(game, 5);

        assertTrue(game.firstVisible() <= 0, "the pop has nowhere to be drawn");
        assertEquals(5 + RunnerGame.JUDGEMENT_GRACE_SECONDS, game.resolvedAt(0), 0.05,
                "the effect begins when the entity was judged, a grace period after its beat");

        runTo(game, 5 + RunnerGame.EFFECT_SECONDS + 0.2);
        assertTrue(game.firstVisible() > 0, "and it is gone once the effect has faded");
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    @Test
    @DisplayName("collisions are announced once each")
    void listenersHearEachEventOnce() {
        RunnerGame game = new RunnerGame(fixed(
                new Coin(5, Lane.CENTER), new Obstacle(6, Lane.CENTER)));
        List<String> heard = new ArrayList<>();
        game.addListener(new RunnerListener() {
            @Override
            public void coinCollected(Coin coin, ScoreKeeper score) {
                heard.add("coin");
            }

            @Override
            public void obstacleHit(Obstacle obstacle, ScoreKeeper score) {
                heard.add("hit");
            }
        });

        runTo(game, 7);
        assertEquals(List.of("coin", "hit"), heard);
    }

    // ------------------------------------------------------------------
    // The combo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a coin left in another lane does not break the combo")
    void missingACoinKeepsTheCombo() {
        RunnerGame game = new RunnerGame(fixed(
                new Coin(5, Lane.CENTER), new Coin(6, Lane.LEFT), new Coin(7, Lane.CENTER)));
        runPastJudgement(game, 7);

        assertEquals(2, game.score().coinsCollected(), "the one in the left lane went by");
        assertEquals(2, game.score().combo(),
                "there are three lanes and one racer, so a combo broken by an uncollected coin "
                        + "would be a combo nobody could ever build");
    }

    @Test
    @DisplayName("a bump breaks the combo and the next coin starts again at one")
    void aBumpBreaksTheCombo() {
        RunnerGame game = new RunnerGame(fixed(
                new Coin(1, Lane.CENTER), new Coin(2, Lane.CENTER), new Coin(3, Lane.CENTER),
                new Obstacle(4, Lane.CENTER), new Coin(5, Lane.CENTER)));

        runPastJudgement(game, 3);
        assertEquals(3, game.score().combo());
        int beforeTheBump = game.score().coins();

        runPastJudgement(game, 4);
        assertSame(EntityState.HIT, game.stateOf(3));
        assertEquals(0, game.score().combo());

        runPastJudgement(game, 5);
        assertEquals(1, game.score().combo());
        assertEquals(beforeTheBump - ScoreKeeper.HIT_PENALTY_COINS + 1, game.score().coins(),
                "the coin after a bump is worth one, because the streak it was riding is gone");
        assertEquals(3, game.score().bestCombo());
    }

    @Test
    @DisplayName("jumping a wall builds the combo, which is the only thing the jump has ever paid")
    void clearingAWallBuildsTheCombo() {
        RunnerGame game = new RunnerGame(fixed(
                new Obstacle(5, Lane.LEFT, true), new Obstacle(5, Lane.CENTER, true),
                new Obstacle(5, Lane.RIGHT, true)));
        runTo(game, 5 - RunnerGame.JUMP_SECONDS / 2);
        game.jump();
        runPastJudgement(game, 5);

        assertSame(EntityState.CLEARED, game.stateOf(1), "only the racer's lane is judged");
        assertEquals(1, game.score().combo(),
                "one wall is one obstacle cleared, however many lanes it blocked");
        assertEquals(0, game.score().coins(), "and it pays nothing on its own");
    }

    @Test
    @DisplayName("seeking forward writes entities off without handing over a combo")
    void aSkippedStretchEarnsNoCombo() {
        RunnerGame game = new RunnerGame(fixed(
                new Coin(1, Lane.CENTER), new Coin(2, Lane.CENTER), new Coin(3, Lane.CENTER),
                new Coin(4, Lane.CENTER)));
        game.update(0);
        game.update(20);

        assertEquals(0, game.score().combo(),
                "a course cannot be collected by dragging the playhead across it, and it cannot "
                        + "be used to arrive at the next stretch already multiplied either");
        assertEquals(0, game.score().coinsCollected());
    }

    @Test
    @DisplayName("an empty course is an ordinary state, not a failure")
    void anEmptyCourseRuns() {
        RunnerGame game = new RunnerGame(Course.generate("s", Beatmap.EMPTY, SpeedClass.CC150));
        runTo(game, 30);

        assertEquals(0, game.score().coins());
        assertFalse(game.score().isRanked());
        assertTrue(game.lastVisible() < game.firstVisible());
    }
}
