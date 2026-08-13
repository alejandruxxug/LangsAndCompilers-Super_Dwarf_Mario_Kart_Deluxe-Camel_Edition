package com.eia.superdwarfkart.game;

/**
 * The grade a run earns: how much of a course's gold was actually picked up.
 *
 * <p><strong>Measured against the course, not against a fixed target.</strong> The denominator is
 * the number of coins the generator put on <em>that</em> course at <em>that</em> speed class, so a
 * dense four-minute track and a sparse thirty-second one are graded on the same scale and the
 * letter means the same thing on both. A score in raw coins would say more about the song's length
 * than about the driving.
 *
 * <p>The speed class deliberately does <strong>not</strong> enter into it. A clean 50cc run is an S,
 * and so is a clean 200cc run; what separates them is
 * {@link SpeedClass#scoreMultiplier() what the coins were worth}. Weighting the rank as well would
 * make the letter unreachable at the slow classes, which is where somebody meeting the game for the
 * first time starts.
 */
public enum Rank {

    /** Nearly everything collected. */
    S(0.90),

    /** A strong run. */
    A(0.75),

    /** A solid run. */
    B(0.60),

    /** Most of the course missed. */
    C(0.40),

    /** Anything below C, including a course that was barely driven. */
    D(0);

    private final double threshold;

    Rank(double threshold) {
        this.threshold = threshold;
    }

    /** @return the fraction of a course's coins this rank starts at */
    public double threshold() {
        return threshold;
    }

    /**
     * Grades a run.
     *
     * @param completion the fraction of the course's coins collected, 0.0 to 1.0
     * @return the rank earned; {@link #D} for anything at or below its threshold, including a
     *         negative or not-a-number input, so a caller never has to guard the arithmetic
     */
    public static Rank forCompletion(double completion) {
        if (Double.isNaN(completion)) {
            return D;
        }
        for (Rank rank : values()) {
            if (completion >= rank.threshold) {
                return rank;
            }
        }
        return D;
    }
}
