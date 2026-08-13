package com.eia.superdwarfkart.game;

import java.time.Instant;
import java.util.Objects;

/**
 * One stored best run: what a song's course at one speed class was completed to.
 *
 * <p>Keyed by {@code (songId, speedClass)} because a course <em>is</em> that pair - the same song
 * at 200cc holds different entities, more of them, and a different number of coins, so ranking the
 * two together would compare two different circuits.
 *
 * <p><strong>The rank is derived, never stored.</strong> It is a function of
 * {@code coinsCollected / coinsAvailable}, and a file that carried its own copy could disagree with
 * its own numbers after a hand edit or a change to the thresholds. The two counts are the facts;
 * the letter is a reading of them.
 *
 * @param songId         the song's stable identifier
 * @param speedClass     the class the run was driven at
 * @param score          coins carried at the end, weighed by the class multiplier
 * @param coinsCollected coins actually picked up off the course
 * @param coinsAvailable how many the course held
 * @param achievedAt     when the run was recorded
 */
public record ScoreEntry(String songId, SpeedClass speedClass, int score, int coinsCollected,
                         int coinsAvailable, Instant achievedAt) {

    /**
     * @throws NullPointerException     if the identifier, class or instant is {@code null}
     * @throws IllegalArgumentException if any count is negative, or more coins were collected than
     *                                  the course held
     */
    public ScoreEntry {
        Objects.requireNonNull(songId, "songId must not be null");
        Objects.requireNonNull(speedClass, "speedClass must not be null");
        Objects.requireNonNull(achievedAt, "achievedAt must not be null");
        if (score < 0 || coinsCollected < 0 || coinsAvailable < 0) {
            throw new IllegalArgumentException("A score cannot be negative, but was given score "
                    + score + ", collected " + coinsCollected + ", available " + coinsAvailable);
        }
        if (coinsCollected > coinsAvailable) {
            throw new IllegalArgumentException("A run collected " + coinsCollected
                    + " coins from a course holding only " + coinsAvailable);
        }
    }

    /**
     * Records what a finished run achieved.
     *
     * @param songId the song that was driven
     * @param score  the tally at the end of the run
     * @return the entry to store
     */
    public static ScoreEntry of(String songId, ScoreKeeper score) {
        Objects.requireNonNull(score, "score must not be null");
        return new ScoreEntry(songId, score.speedClass(), score.score(), score.coinsCollected(),
                score.coinsAvailable(), Instant.now());
    }

    /** @return the fraction of the course's coins collected, 0.0 to 1.0 */
    public double completion() {
        return coinsAvailable == 0 ? 0 : coinsCollected / (double) coinsAvailable;
    }

    /** @return the grade this run earned */
    public Rank rank() {
        return Rank.forCompletion(completion());
    }

    /**
     * Compares two runs at the same course.
     *
     * <p>By coins collected rather than by score, so the better <em>drive</em> wins: the score
     * carries penalties and star bonuses, and a lucky star could otherwise let a sloppier run
     * displace a cleaner one from the board. The score breaks a tie.
     *
     * @param other the run to compare against, or {@code null} for no previous run
     * @return whether this run should replace that one
     */
    public boolean beats(ScoreEntry other) {
        if (other == null) {
            return true;
        }
        if (coinsCollected != other.coinsCollected) {
            return coinsCollected > other.coinsCollected;
        }
        return score > other.score;
    }
}
