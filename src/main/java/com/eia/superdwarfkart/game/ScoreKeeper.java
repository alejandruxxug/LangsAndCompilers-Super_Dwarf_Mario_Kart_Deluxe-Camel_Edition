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
 * <p><strong>The combo multiplies the balance and never the rank</strong>, and that split is the
 * reason it fits here at all rather than needing a scoring rule of its own - see {@link #combo()}.
 *
 * <p>Nothing here knows what a frame is. The keeper is told what happened and by whom; when it
 * happened, and whether it was near a beat, is {@link RunnerGame}'s business.
 */
public final class ScoreKeeper {

    /** Coins lost on hitting an obstacle unprotected. */
    public static final int HIT_PENALTY_COINS = 5;

    /** Coins awarded for breaking an obstacle while starred. */
    public static final int BREAK_BONUS_COINS = 5;

    /**
     * The highest the combo goes, and therefore the most a pickup can be multiplied by.
     *
     * <p>Ten because that is where a meter made of blocks stops being countable at a glance, which
     * is the only place the number is ever read from. Past it the combo simply holds: a streak that
     * went on climbing invisibly would make the meter say the same thing at twenty as at ten while
     * quietly paying differently, and the player has no way to see the difference.
     */
    public static final int MAX_COMBO = 10;

    private final SpeedClass speedClass;
    private final int coinsAvailable;

    private int coins;
    private int coinsCollected;
    private int obstaclesHit;
    private int obstaclesBroken;
    private int starsCollected;
    private int combo;
    private int bestCombo;

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

    /**
     * Records a coin driven into.
     *
     * <p><strong>One coin, however many it is worth.</strong> The balance takes the multiplier and
     * {@link #coinsCollected()} takes exactly one, because the course held exactly one - see
     * {@link #combo()}.
     */
    public void collectCoin() {
        advanceCombo();
        coins += multiplier();
        coinsCollected++;
    }

    /**
     * Records a bump taken unprotected.
     *
     * <p>The balance floors at zero rather than going negative: a debt the player cannot see the
     * bottom of stops being a penalty and starts being a reason to give up on the run.
     *
     * <p><strong>The combo is lost here and the penalty is not scaled by it.</strong> Losing a
     * multiplier that took a minute to build is already by far the larger of the two costs; taking
     * five coins per level on top would make one mistake at the top of the meter unrecoverable,
     * which is the opposite of what the brief invulnerability after a bump exists to prevent.
     */
    public void hitObstacle() {
        obstaclesHit++;
        combo = 0;
        coins = Math.max(0, coins - HIT_PENALTY_COINS);
    }

    /** Records an obstacle destroyed by driving through it while starred. */
    public void breakObstacle() {
        advanceCombo();
        obstaclesBroken++;
        coins += BREAK_BONUS_COINS * multiplier();
    }

    /**
     * Records an obstacle jumped clean over.
     *
     * <p>Pays nothing and builds the combo, which is the only reward the jump has ever carried. A
     * wall blocks all three lanes and the jump is the only way past it, so this is where the one
     * control the player has to learn feeds the one number that makes the rest of the course worth
     * more - and it is a deliberate answer to the jump otherwise being a thing that merely avoids a
     * loss.
     */
    public void clearObstacle() {
        advanceCombo();
    }

    /** Records a star driven into. */
    public void collectStar() {
        advanceCombo();
        starsCollected++;
    }

    /**
     * Takes the combo up one, holding at {@link #MAX_COMBO}.
     *
     * <p>Called before the coins are added rather than after, so the pickup that takes the combo to
     * a new level is paid at that level. Rewarding it at the old one means the meter and the number
     * it is multiplying disagree in the one frame the player is looking at both.
     */
    private void advanceCombo() {
        combo = Math.min(MAX_COMBO, combo + 1);
        bestCombo = Math.max(bestCombo, combo);
    }

    /** Clears the tally, for a run being started again. */
    public void reset() {
        coins = 0;
        coinsCollected = 0;
        obstaclesHit = 0;
        obstaclesBroken = 0;
        starsCollected = 0;
        combo = 0;
        bestCombo = 0;
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

    /**
     * How many things have been picked up or cleared in a row, up to {@link #MAX_COMBO}.
     *
     * <p><strong>Only a bump breaks it.</strong> Not a coin that went by in another lane, which
     * happens constantly and by design - there are three lanes and one racer, so a combo broken by
     * missing a coin would be a combo nobody could ever build. What it counts is mistakes: it holds
     * for as long as the player takes nothing on the chin, and it is the same rule at every speed
     * class, where the fast ones simply put far more chances to make one on the road.
     *
     * <p>It multiplies {@link #coins()} and cannot touch {@link #coinsCollected()}, so the rank is
     * untouched by it. That is not tidiness: the rank is a fraction of what the generator put on the
     * course, and a multiplier applied to its numerator would let a good streak read as more coins
     * than the course ever held.
     *
     * @return the current streak, 0 before anything has been collected
     */
    public int combo() {
        return combo;
    }

    /**
     * @return the longest streak this run reached, which is what says whether a course can be
     *         driven cleanly at all
     */
    public int bestCombo() {
        return bestCombo;
    }

    /**
     * @return what a pickup is currently worth, 1 with no combo running and {@link #MAX_COMBO} at
     *         the top of the meter
     */
    public int multiplier() {
        return Math.max(1, combo);
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
                + " collected, combo x" + multiplier() + ", rank " + rank() + ", score " + score()
                + "]";
    }
}
