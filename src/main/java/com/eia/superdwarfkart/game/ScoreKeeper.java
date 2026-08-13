package com.eia.superdwarfkart.game;

import java.util.Objects;

/**
 * The running tally for one attempt at one course.
 *
 * <p>Two numbers are kept, and keeping them apart is the point:
 *
 * <ul>
 *   <li><strong>{@link #coinsCollected()}</strong> counts coins actually picked up off the course
 *       and never goes down. It is the numerator of {@link #rank()} - a measure of how well the
 *       course was <em>driven</em>, which a penalty must not be able to disguise or improve.</li>
 *   <li><strong>{@link #coins()}</strong> is the balance the player is carrying: picked up, less
 *       {@value #HIT_PENALTY_COINS} for every bump taken, plus {@value #BREAK_BONUS_COINS} for
 *       every one broken while starred. This is what {@link #score()} weighs by the speed class.</li>
 * </ul>
 *
 * <p>Coins broken out of an obstacle deliberately do <strong>not</strong> count towards the rank.
 * The rank is a fraction of the coins the generator put on the course, and a bonus that was never
 * on the course would let a lucky star push it past 100%.
 *
 * <p>Nothing here knows what a frame is. The keeper is told what happened and by whom; when it
 * happened, and whether it was near a beat, is {@link RunnerGame}'s business.
 */
public final class ScoreKeeper {

    /** Coins lost on hitting an obstacle unprotected. */
    public static final int HIT_PENALTY_COINS = 5;

    /** Coins awarded for breaking an obstacle while starred. */
    public static final int BREAK_BONUS_COINS = 5;

    private final SpeedClass speedClass;
    private final int coinsAvailable;

    private int coins;
    private int coinsCollected;
    private int obstaclesHit;
    private int obstaclesBroken;
    private int starsCollected;

    /**
     * @param speedClass     the class being driven, which sets what a coin is worth
     * @param coinsAvailable how many coins the course holds; the rank's denominator
     */
    public ScoreKeeper(SpeedClass speedClass, int coinsAvailable) {
        this.speedClass = Objects.requireNonNull(speedClass, "speedClass must not be null");
        this.coinsAvailable = Math.max(0, coinsAvailable);
    }

    // ------------------------------------------------------------------
    // What happened
    // ------------------------------------------------------------------

    /** Records a coin driven into. */
    public void collectCoin() {
        coins++;
        coinsCollected++;
    }

    /**
     * Records a bump taken unprotected.
     *
     * <p>The balance floors at zero rather than going negative: a debt the player cannot see the
     * bottom of stops being a penalty and starts being a reason to give up on the run.
     */
    public void hitObstacle() {
        obstaclesHit++;
        coins = Math.max(0, coins - HIT_PENALTY_COINS);
    }

    /** Records an obstacle destroyed by driving through it while starred. */
    public void breakObstacle() {
        obstaclesBroken++;
        coins += BREAK_BONUS_COINS;
    }

    /** Records a star driven into. */
    public void collectStar() {
        starsCollected++;
    }

    /** Clears the tally, for a run being started again. */
    public void reset() {
        coins = 0;
        coinsCollected = 0;
        obstaclesHit = 0;
        obstaclesBroken = 0;
        starsCollected = 0;
    }

    // ------------------------------------------------------------------
    // Where the run stands
    // ------------------------------------------------------------------

    /** @return the coins being carried, after penalties and bonuses */
    public int coins() {
        return coins;
    }

    /** @return how many coins were picked up off the course, whatever happened afterwards */
    public int coinsCollected() {
        return coinsCollected;
    }

    /** @return how many coins the course holds in total */
    public int coinsAvailable() {
        return coinsAvailable;
    }

    /** @return how many bumps were taken */
    public int obstaclesHit() {
        return obstaclesHit;
    }

    /** @return how many obstacles were broken while starred */
    public int obstaclesBroken() {
        return obstaclesBroken;
    }

    /** @return how many stars were collected */
    public int starsCollected() {
        return starsCollected;
    }

    /** @return the class being driven */
    public SpeedClass speedClass() {
        return speedClass;
    }

    /** @return the coins carried, weighed by what the speed class pays for them */
    public int score() {
        return (int) Math.round(coins * speedClass.scoreMultiplier());
    }

    /**
     * @return the fraction of the course's coins collected, 0.0 to 1.0, or 0 on a course that holds
     *         no coins at all
     */
    public double completion() {
        return coinsAvailable == 0 ? 0 : coinsCollected / (double) coinsAvailable;
    }

    /**
     * @return whether this run is worth grading - a course with no coins on it has nothing to be a
     *         fraction of, and reporting D for it would blame the player for an empty beatmap
     */
    public boolean isRanked() {
        return coinsAvailable > 0;
    }

    /**
     * @return the grade earned so far; meaningless unless {@link #isRanked()}, where it reads
     *         {@link Rank#D}
     */
    public Rank rank() {
        return Rank.forCompletion(completion());
    }

    @Override
    public String toString() {
        return "ScoreKeeper[" + coins + " coins, " + coinsCollected + "/" + coinsAvailable
                + " collected, rank " + rank() + ", score " + score() + "]";
    }
}
