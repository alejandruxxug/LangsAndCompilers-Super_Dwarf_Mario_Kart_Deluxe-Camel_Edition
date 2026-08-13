package com.eia.superdwarfkart.game;

/**
 * What became of one entity during the current run.
 *
 * <p>{@link RunnerGame} keeps one of these per entity, in an array beside the {@link Course}, so a
 * course stays immutable and can be replayed, cached and drawn twice - see {@link Entity}.
 *
 * <p>The states are distinguished more finely than the score needs them, because the <em>view</em>
 * needs them: a coin that was taken pops, a broken obstacle explodes, a hit flashes the racer, and
 * one that was simply passed in another lane does nothing at all. Collapsing the harmless outcomes
 * into a single "gone" would leave the view unable to tell a near miss from a clean line.
 */
public enum EntityState {

    /** Still on the way; it has not reached the racer yet. */
    PENDING,

    /** A coin or a star the racer drove into. */
    COLLECTED,

    /** An obstacle the racer jumped over, in its lane. */
    CLEARED,

    /** Passed harmlessly: a different lane, or a coin the racer was not in line with. */
    MISSED,

    /** An obstacle that hit an unprotected racer. */
    HIT,

    /** An obstacle destroyed by driving through it while starred. */
    BROKEN;

    /** @return whether this entity has been dealt with and no longer travels towards the racer */
    public boolean isResolved() {
        return this != PENDING;
    }
}
