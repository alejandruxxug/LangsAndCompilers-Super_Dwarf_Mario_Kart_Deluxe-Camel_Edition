package com.eia.superdwarfkart.game;

import java.util.Objects;

/**
 * A greedy, short-sighted player, driven by a clock rather than by a keyboard.
 *
 * <p>The policy is two rules and nothing else: get out of any lane with a bump about to arrive in
 * it, and otherwise sit in the lane of the next thing worth collecting. Every lane blocked at once
 * is a wall, and a wall is what the jump is for. It is deliberately <strong>not</strong> an optimal
 * player - it loses coins whenever a bump and a coin want opposite lanes at the same moment - which
 * is what makes the rank it earns worth reading.
 *
 * <p>It exists for two callers that would otherwise each need their own copy:
 *
 * <ul>
 *   <li>the smoke test's lap, where <strong>a course a competent driver cannot rank well on is a
 *       generated course the collision rules cannot survive</strong> - two bumps too close together
 *       to dodge, or an entity placed where the resolution window can never reach it - which is
 *       caught over four minutes of real beatmap on every launch;</li>
 *   <li>the runner's own screenshot, where the alternative is jumping the clock straight to the
 *       moment being photographed. That resolves every entity behind it as skipped, so the picture
 *       comes out with a zeroed head-up display: no coins, no combo and a rank of D over a road
 *       nobody has driven.</li>
 * </ul>
 *
 * <p>Lives in {@code game/} rather than beside either caller because it is a policy over the
 * collision rules and holds no JavaFX, which is what lets it be exercised by handing it a course.
 */
public final class ScriptedDriver {

    /** How far ahead a bump is treated as a reason to be somewhere else, in seconds. */
    public static final double DANGER_HORIZON_SECONDS = 0.2;

    /** How far ahead the driver looks for something worth collecting, in seconds. */
    public static final double AIM_HORIZON_SECONDS = 0.6;

    /** The frame rate the run is stepped at, matching the one the game is played at. */
    private static final double STEP_SECONDS = 1 / 60d;

    private ScriptedDriver() {
    }

    /**
     * Drives a run from wherever it has reached to a moment, one frame at a time.
     *
     * <p><strong>Stepped, never jumped.</strong> The whole value of this is that the entities in
     * between are resolved by the real rules at the real frame rate; handing
     * {@link RunnerGame#update(double)} the destination in one call writes all of them off as
     * skipped, which is exactly the behaviour that stops a course being collected by dragging the
     * playhead and is the wrong thing to ask for here.
     *
     * @param game       the run to drive; its own course is what is read
     * @param toSeconds  the position to drive to, in seconds
     */
    public static void driveTo(RunnerGame game, double toSeconds) {
        Objects.requireNonNull(game, "game must not be null");
        Course course = game.course();
        for (double at = game.now(); at <= toSeconds; at += STEP_SECONDS) {
            steer(game, course, at);
            game.update(at);
        }
        // The loop lands on whichever step falls short of the target, so the last fraction of a
        // frame is applied outright - otherwise a screenshot is taken up to a frame before the
        // moment it asked for, which on a wall is the difference between a jump prompt and a hit.
        if (game.now() < toSeconds) {
            game.update(toSeconds);
        }
    }

    /**
     * Drives a whole course from the start and reports what the run was worth.
     *
     * @param course the course to drive; must not be {@code null}
     * @return the finished tally, or {@code null} for an empty course, which cannot be driven
     */
    public static ScoreKeeper driveLap(Course course) {
        Objects.requireNonNull(course, "course must not be null");
        if (course.isEmpty()) {
            return null;
        }
        RunnerGame game = new RunnerGame(course);
        driveTo(game, course.entityAt(course.size() - 1).beatTime() + 1);
        return game.score();
    }

    /**
     * Decides what the driver does this frame.
     *
     * @param game   the run being driven
     * @param course the course under it
     * @param at     the moment being stepped to, in seconds
     */
    private static void steer(RunnerGame game, Course course, double at) {
        boolean[] dangerous = new boolean[Lane.COUNT];
        int aim = -1;
        for (int index = course.firstEntityAtOrAfter(at);
                index < course.size()
                        && course.entityAt(index).beatTime() <= at + AIM_HORIZON_SECONDS;
                index++) {
            Entity entity = course.entityAt(index);
            if (entity instanceof Obstacle) {
                if (entity.beatTime() <= at + DANGER_HORIZON_SECONDS) {
                    dangerous[entity.lane().index()] = true;
                }
            } else if (aim < 0) {
                aim = entity.lane().index();
            }
        }

        if (aim >= 0 && !dangerous[aim]) {
            steerTowards(game, Lane.ofIndex(aim));
        }
        if (dangerous[game.lane().index()]) {
            int safe = firstSafeLane(dangerous);
            if (safe >= 0) {
                steerTowards(game, Lane.ofIndex(safe));
            } else {
                // Every lane is blocked. That is what the jump is for.
                game.jump();
            }
        }
    }

    /**
     * @param dangerous which lanes have a bump arriving
     * @return the index of the first lane that does not, or {@code -1} when they all do
     */
    private static int firstSafeLane(boolean[] dangerous) {
        for (int index = 0; index < dangerous.length; index++) {
            if (!dangerous[index]) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Moves the driver one lane towards a target.
     *
     * @param game   the run being driven
     * @param target the lane to head for
     */
    private static void steerTowards(RunnerGame game, Lane target) {
        if (game.lane().index() < target.index()) {
            game.moveRight();
        } else if (game.lane().index() > target.index()) {
            game.moveLeft();
        }
    }
}
