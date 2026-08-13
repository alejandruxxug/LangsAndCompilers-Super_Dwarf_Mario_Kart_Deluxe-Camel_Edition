package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.assets.AssetKind;

/**
 * A bump in one lane: the entity that costs something.
 *
 * <p>There are three ways past one, and they are the whole of the game's vocabulary - change lane,
 * jump it, or go through it while starred and break it. Hitting one unprotected costs coins and
 * grants a moment of invulnerability, so a single mistake stays a single mistake instead of
 * chaining into a wipeout while the player is still recovering.
 *
 * <p>Whether a particular obstacle was dodged, jumped, broken or hit is not stored here - see
 * {@link Entity} on why a course carries no run state - but in the {@link EntityState} array that
 * {@link RunnerGame} keeps beside it.
 */
public final class Obstacle extends Entity {

    private final boolean wall;

    /**
     * @param beatTime when it reaches the racer, in seconds from the start of the track
     * @param lane     which lane it blocks
     */
    public Obstacle(double beatTime, Lane lane) {
        this(beatTime, lane, false);
    }

    /**
     * @param beatTime when it reaches the racer, in seconds from the start of the track
     * @param lane     which lane it blocks
     * @param wall     whether it is one of a row blocking every lane at once
     */
    public Obstacle(double beatTime, Lane lane, boolean wall) {
        super(beatTime, lane);
        this.wall = wall;
    }

    /**
     * Whether this obstacle is part of a wall across the whole road.
     *
     * <p>A wall is placed on the track's biggest beats and there is <strong>no lane to change
     * into</strong> - the only way past is the jump, which is what makes the jump a control the
     * player has to learn rather than one they can ignore for a whole song.
     *
     * <p>Known here rather than worked out by looking for three obstacles at the same instant,
     * because the view wants to say "JUMP" before the row arrives and the scripted driver in the
     * smoke test wants to know without scanning.
     *
     * @return whether every lane is blocked at this instant
     */
    public boolean isWall() {
        return wall;
    }

    @Override
    protected boolean sameDetails(Entity other) {
        return wall == ((Obstacle) other).wall;
    }

    @Override
    public AssetKind kind() {
        return AssetKind.OBSTACLE;
    }
}
