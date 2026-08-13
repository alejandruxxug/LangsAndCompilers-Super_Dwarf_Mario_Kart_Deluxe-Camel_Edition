package com.eia.superdwarfkart.game;

/**
 * Told when something happens to the racer, so the view can react to it.
 *
 * <p>The view could work all of this out by walking the {@link EntityState} array every frame and
 * looking for changes, and it would be wrong the first time a frame was dropped. A collision is a
 * discrete event that happens once; announcing it once is both cheaper and harder to get wrong.
 *
 * <p>Every method has a default, so a listener implements only what it cares about - the particle
 * layer wants the collisions and the score board wants the end of the run, and neither should have
 * to write empty methods for the other's events.
 *
 * <p><strong>Called from {@link RunnerGame#update(double)}</strong>, which the view drives from its
 * own frame timer. That is the interface thread, so a handler may touch the scene graph - but it
 * runs inside a frame, so it must not do anything slow.
 */
public interface RunnerListener {

    /**
     * A coin was driven into.
     *
     * @param coin  the coin collected
     * @param score the tally, already updated
     */
    default void coinCollected(Coin coin, ScoreKeeper score) {
        // Nothing by default.
    }

    /**
     * A star was driven into and invulnerability has started.
     *
     * @param star  the star collected
     * @param score the tally, already updated
     */
    default void starCollected(Star star, ScoreKeeper score) {
        // Nothing by default.
    }

    /**
     * An obstacle hit an unprotected racer.
     *
     * @param obstacle the obstacle hit
     * @param score    the tally, already updated
     */
    default void obstacleHit(Obstacle obstacle, ScoreKeeper score) {
        // Nothing by default.
    }

    /**
     * An obstacle was destroyed by a starred racer driving through it.
     *
     * @param obstacle the obstacle broken
     * @param score    the tally, already updated
     */
    default void obstacleBroken(Obstacle obstacle, ScoreKeeper score) {
        // Nothing by default.
    }

    /**
     * An obstacle was jumped over.
     *
     * @param obstacle the obstacle cleared
     */
    default void obstacleCleared(Obstacle obstacle) {
        // Nothing by default.
    }
}
