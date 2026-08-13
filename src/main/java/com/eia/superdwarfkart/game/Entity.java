package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.assets.AssetKind;

import java.util.Objects;

/**
 * Something placed on the course at one instant in the music, in one lane.
 *
 * <p><strong>An entity is immutable, and that is load-bearing.</strong> A {@link Course} is
 * generated once per song and speed class and then kept - cached, replayed, screenshotted, run
 * again after a seek - so anything that changes during a run belongs to {@link RunnerGame}, which
 * holds one {@link EntityState} per entity beside the course rather than inside it. Put a
 * {@code collected} flag on the entity instead and a second run through the same course starts with
 * every coin already taken.
 *
 * <p><strong>{@link #beatTime()} is an instant in the track, not a position on a road.</strong>
 * Nothing here knows about pixels, spawn points or travel distances. The entity says <em>when</em>
 * it should meet the racer; the lookahead in {@link RunnerGame} works out when it has to appear for
 * that to happen, and the view works out where to draw it. That is the whole reason the game stays
 * in step with the music: the meeting time is the beat, and it is decided before anything is drawn.
 *
 * <p>Sealed over exactly three kinds. Adding a fourth is a deliberate act that the compiler will
 * point at every place a course is resolved or drawn, which is exactly the review that a new kind
 * of collision deserves.
 */
public abstract sealed class Entity permits Coin, Obstacle, Star {

    private final double beatTime;
    private final Lane lane;

    /**
     * @param beatTime when this should reach the racer, in seconds from the start of the track
     * @param lane     which lane it sits in; must not be {@code null}
     * @throws IllegalArgumentException if the time is negative
     */
    protected Entity(double beatTime, Lane lane) {
        if (beatTime < 0) {
            throw new IllegalArgumentException(
                    "An entity cannot be placed before the track starts, but was given " + beatTime);
        }
        this.beatTime = beatTime;
        this.lane = Objects.requireNonNull(lane, "lane must not be null");
    }

    /**
     * @return when this entity reaches the racer, in seconds from the start of the track, on the
     *         same clock as {@link com.eia.superdwarfkart.audio.AudioSource#position()}
     */
    public final double beatTime() {
        return beatTime;
    }

    /** @return the lane it sits in */
    public final Lane lane() {
        return lane;
    }

    /**
     * @return the artwork that draws this entity, so the view asks the registry by kind and never
     *         names an image file
     */
    public abstract AssetKind kind();

    /**
     * Two entities are the same if they are the same kind, in the same lane, at the same instant.
     *
     * <p>Value equality rather than identity, because that is what a course <em>is</em>: generating
     * the same song at the same class twice has to produce two equal courses, and comparing them is
     * the only way to check it. Identity would make that comparison always false and the check
     * always pass, which is worse than not having one.
     *
     * @param other the object to compare against
     * @return whether they describe the same placement
     */
    @Override
    public final boolean equals(Object other) {
        return other != null
                && getClass() == other.getClass()
                && Double.compare(beatTime, ((Entity) other).beatTime) == 0
                && lane == ((Entity) other).lane
                && sameDetails((Entity) other);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode() * 31 * 31 + Double.hashCode(beatTime) * 31 + lane.hashCode();
    }

    /**
     * Compares whatever a subclass carries beyond a time and a lane.
     *
     * <p>{@link #equals} is final so that the kind, the instant and the lane can never be left out
     * of a comparison by accident; this is the hook for the rest. An obstacle uses it for whether
     * it is part of a wall, which is a real difference between two courses and has to show up when
     * one is compared against another.
     *
     * @param other another entity of the same class, at the same time, in the same lane
     * @return whether they are the same in every other respect
     */
    protected boolean sameDetails(Entity other) {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + String.format("%.3f", beatTime) + "s " + lane + "]";
    }
}
