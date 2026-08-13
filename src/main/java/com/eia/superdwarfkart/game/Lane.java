package com.eia.superdwarfkart.game;

/**
 * One of the three lanes of the runner course.
 *
 * <p>Three is not an arbitrary number. It is the smallest count for which "move sideways" is a real
 * decision rather than a toggle: from the middle there are two ways out of trouble and from an edge
 * there is one, so where the racer already is changes what the next obstacle costs. It is also what
 * keeps the course generator's fairness rule simple - one entity is placed per beat, so a lane is
 * always free.
 *
 * <p>Stepping past an edge is <strong>clamped, not wrapped</strong>. A kart that reappeared on the
 * far side of the road would be a teleport, and the player would learn to distrust the control.
 */
public enum Lane {

    /** The left-hand lane, index 0. */
    LEFT(0),

    /** The middle lane, index 1, where a run starts. */
    CENTER(1),

    /** The right-hand lane, index 2. */
    RIGHT(2);

    /** How many lanes the course has. */
    public static final int COUNT = 3;

    private final int index;

    Lane(int index) {
        this.index = index;
    }

    /**
     * @return this lane's position across the road, 0 on the left
     */
    public int index() {
        return index;
    }

    /**
     * Steps one lane to the left.
     *
     * @return the lane to the left, or this one when already at the left edge
     */
    public Lane left() {
        return index == 0 ? this : ofIndex(index - 1);
    }

    /**
     * Steps one lane to the right.
     *
     * @return the lane to the right, or this one when already at the right edge
     */
    public Lane right() {
        return index == COUNT - 1 ? this : ofIndex(index + 1);
    }

    /**
     * @param index a lane position across the road
     * @return the lane at that position
     * @throws IllegalArgumentException if there is no such lane
     */
    public static Lane ofIndex(int index) {
        for (Lane lane : values()) {
            if (lane.index == index) {
                return lane;
            }
        }
        throw new IllegalArgumentException(
                "There is no lane at index " + index + "; the course has " + COUNT + " lanes");
    }

    /** @return the lane a run starts in */
    public static Lane starting() {
        return CENTER;
    }
}
