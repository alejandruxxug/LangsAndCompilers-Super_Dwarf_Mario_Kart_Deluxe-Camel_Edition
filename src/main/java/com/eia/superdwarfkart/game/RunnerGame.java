package com.eia.superdwarfkart.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The runner: where the racer is, what has hit him, and what the score is.
 *
 * <p><strong>Time comes in; nothing is counted here.</strong> {@link #update(double)} is handed the
 * position the sound card has actually reached and everything is derived from it - which entities
 * are due, how far through a jump the racer is, whether the star has run out. There is no elapsed
 * time, no frame delta and no internal clock, because every one of those drifts away from the music
 * inside a minute and the drift is invisible until the whole course is a beat late.
 *
 * <p><strong>An entity meets the racer at its own beat time.</strong> That single sentence is the
 * synchronisation: the entity is drawn travelling towards a fixed racer over
 * {@link Course#travelTimeSeconds()}, so it arrives exactly when the note it was placed on sounds,
 * and the collision is resolved at that instant rather than by overlapping two rectangles. A hit
 * test in screen space would be a test of the renderer's geometry; this is a test of the music.
 *
 * <p>The run state lives here and not in the {@link Course}, which stays immutable and replayable -
 * see {@link Entity}. One {@link EntityState} per entity, in an array beside it.
 *
 * <p>No {@code javafx} import: the whole of the game's behaviour is exercised by handing this class
 * a sequence of times, which is what makes the collision rules testable without a window, a sound
 * card or a beatmap.
 */
public final class RunnerGame {

    /** How long the sprite takes to slide between lanes, in seconds. Presentation only - see below. */
    public static final double LANE_CHANGE_SECONDS = 0.12;

    /** How long a jump lasts, in seconds. */
    public static final double JUMP_SECONDS = 0.45;

    /** How long the racer cannot be hurt after taking a bump, in seconds. */
    public static final double INVULNERABLE_SECONDS = 1.2;

    /**
     * How long after its beat an entity is judged, in seconds - the late half of the timing window.
     *
     * <p><strong>Without this the window has no late half at all, and that is what a frame-perfect
     * input feels like even when the window is nearly half a second wide.</strong> A jump pressed at
     * {@code p} covers everything in {@code [p, p + JUMP_SECONDS)}, so an obstacle at {@code T} was
     * cleared by any press in {@code (T - 0.45, T]} and by nothing whatsoever after {@code T}. Human
     * timing error is symmetric about what the player aimed at; a window that is 450 ms wide on one
     * side and zero on the other therefore throws away half of every player's attempts, and it
     * throws away exactly the half they cannot see themselves making - because a key pressed a
     * frame late looks, on screen, like a key pressed on time.
     *
     * <p>So judgement happens here rather than on the beat: the entity is still <em>drawn</em>
     * arriving at its beat, and what became of it is decided a tenth of a second later, against the
     * state the racer was in over that whole stretch. The cost is that a coin's pop and a bump's
     * explosion begin a tenth of a second after the contact that caused them, which is the trade
     * every rhythm game makes and nobody has ever noticed.
     *
     * <p>It is applied to <em>every</em> entity and not only to obstacles, because the resolution
     * cursor walks the course in beat order and a delay applied to some entities and not others
     * would put two of them out of order.
     */
    public static final double JUDGEMENT_GRACE_SECONDS = 0.10;

    /**
     * How long a star lasts, in <strong>beats</strong>.
     *
     * <p>Beats rather than seconds so it covers the same musical span on a slow track as on a fast
     * one, and so it always runs out on a beat rather than in the middle of one.
     */
    public static final int STAR_BEATS = 8;

    /** How long a star lasts on a track whose tempo could not be established, in seconds. */
    public static final double STAR_FALLBACK_SECONDS = 6;

    /**
     * How late an entity may be resolved before it is written off as skipped, in seconds.
     *
     * <p>At sixty frames a second an entity is at most 17 ms overdue when it is noticed, so this is
     * generous for ordinary running. What it is really for is a <em>seek</em>: dragging the playhead
     * forward past a hundred entities must not hand the player a hundred coins, and dropping a
     * second of frames must not silently award whatever was in the racer's lane.
     */
    public static final double RESOLVE_WINDOW_SECONDS = 0.25;

    /**
     * How long a resolved entity stays in the visible window, in seconds, so its pop or its
     * explosion has somewhere to be drawn.
     *
     * <p>Long enough for the slowest of them. A bump scatters the coins it cost, and coins that are
     * thrown have to come back down - cut this to the length of a coin pop and they vanish at the
     * top of their arc.
     */
    public static final double EFFECT_SECONDS = 0.8;

    private final List<RunnerListener> listeners = new ArrayList<>();

    private Course course;
    private ScoreKeeper score;

    /** One state per entity, parallel to the course. */
    private EntityState[] states;

    /** When each entity was resolved, so the view can fade its effect out. */
    private double[] resolvedAt;

    /** The first entity not yet resolved. Everything before it has been dealt with. */
    private int cursor;

    private double now;

    private Lane lane = Lane.starting();

    /** Where the sprite was when the current lane change began, as a fractional lane index. */
    private double laneFrom = Lane.starting().index();

    private double laneChangedAt = Double.NEGATIVE_INFINITY;

    /**
     * How many past lane changes are remembered.
     *
     * <p>Judgement happens {@link #JUDGEMENT_GRACE_SECONDS} after the beat, so it has to ask which
     * lane the racer was in <em>then</em> rather than which one they are in now - otherwise the
     * grace would quietly punish steering, letting a player who moved just after passing an
     * obstacle be hit by one they had already gone by. Four entries covers a tenth of a second
     * comfortably: a lane change takes {@link #LANE_CHANGE_SECONDS} and nobody presses faster than
     * that resolves.
     */
    private static final int LANE_HISTORY = 4;

    /** The lane each remembered change moved into. */
    private final Lane[] laneHistory = new Lane[LANE_HISTORY];

    /** The lane each remembered change moved out of, so the instant before it is exact too. */
    private final Lane[] laneLeft = new Lane[LANE_HISTORY];

    private final double[] laneHistoryAt = new double[LANE_HISTORY];

    /** How many changes have been recorded, ever; the ring holds the last {@link #LANE_HISTORY}. */
    private int laneChanges;

    private double jumpStartedAt = Double.NEGATIVE_INFINITY;
    private double starUntil = Double.NEGATIVE_INFINITY;
    private double invulnerableUntil = Double.NEGATIVE_INFINITY;

    private int firstVisible;
    private int lastVisible = -1;

    /** Builds a runner sitting on an empty course, which is what it drives until a song is chosen. */
    public RunnerGame() {
        this(Course.empty("", SpeedClass.defaultClass()));
    }

    /**
     * @param course the course to drive; must not be {@code null}
     */
    public RunnerGame(Course course) {
        setCourse(course);
    }

    // ------------------------------------------------------------------
    // The course
    // ------------------------------------------------------------------

    /**
     * Puts a new course under the racer and starts the run again.
     *
     * @param course the course to drive; must not be {@code null}
     */
    public void setCourse(Course course) {
        this.course = Objects.requireNonNull(course, "course must not be null");
        this.states = new EntityState[course.size()];
        this.resolvedAt = new double[course.size()];
        restart();
    }

    /** @return the course being driven */
    public Course course() {
        return course;
    }

    /** @return the tally for the current run */
    public ScoreKeeper score() {
        return score;
    }

    /**
     * Starts the run again from the beginning: nothing collected, nothing hit, back to the middle
     * lane.
     */
    public void restart() {
        score = new ScoreKeeper(course.speedClass(), course.coinsAvailable());
        Arrays.fill(states, EntityState.PENDING);
        Arrays.fill(resolvedAt, 0);
        cursor = 0;
        now = 0;
        lane = Lane.starting();
        laneFrom = lane.index();
        laneChangedAt = Double.NEGATIVE_INFINITY;
        Arrays.fill(laneHistory, Lane.starting());
        Arrays.fill(laneLeft, Lane.starting());
        Arrays.fill(laneHistoryAt, Double.NEGATIVE_INFINITY);
        laneChanges = 0;
        jumpStartedAt = Double.NEGATIVE_INFINITY;
        starUntil = Double.NEGATIVE_INFINITY;
        invulnerableUntil = Double.NEGATIVE_INFINITY;
        firstVisible = 0;
        lastVisible = -1;
    }

    // ------------------------------------------------------------------
    // The clock
    // ------------------------------------------------------------------

    /**
     * Brings the run up to the moment the sound card has reached.
     *
     * <p>Resolves everything that has fallen due since the last call, in order, and recomputes the
     * window of entities the view has to draw. Called once per displayed frame.
     *
     * <p>A position that has gone <em>backwards</em> is a seek, and every entity after the new
     * position is re-armed so the stretch can be driven again. The tally is deliberately left alone:
     * re-arming and re-scoring would let a run be farmed by dragging the playhead back over a good
     * bar, and clearing the tally would throw away a run because somebody nudged the seek bar.
     *
     * @param songSeconds the playback position, in seconds from the start of the track
     */
    public void update(double songSeconds) {
        double time = Math.max(0, songSeconds);
        if (time < now) {
            rearmFrom(time);
        }
        now = time;

        // Judged a grace period after the beat, never on it. See JUDGEMENT_GRACE_SECONDS: an
        // entity is drawn arriving on its beat and decided a tenth of a second later, which is what
        // gives the timing window a late half at all.
        while (cursor < course.size()
                && course.entityAt(cursor).beatTime() + JUDGEMENT_GRACE_SECONDS <= now) {
            resolve(cursor);
            cursor++;
        }

        firstVisible = course.firstEntityAtOrAfter(now - EFFECT_SECONDS);
        lastVisible = course.firstEntityAtOrAfter(now + course.travelTimeSeconds()) - 1;
    }

    /**
     * Re-arms everything from a time onwards, after the playhead moved backwards.
     *
     * @param time the position seeked to, in seconds
     */
    private void rearmFrom(double time) {
        int from = course.firstEntityAtOrAfter(time);
        for (int index = from; index < states.length; index++) {
            states[index] = EntityState.PENDING;
            resolvedAt[index] = 0;
        }
        cursor = from;
    }

    /**
     * Decides what happened when one entity reached the racer.
     *
     * <p>The order of the obstacle's three escapes is the order of their worth. A starred racer
     * <em>breaks</em> the bump and is paid for it, so being starred has to be checked before
     * jumping - otherwise a player who jumped out of habit would lose the bonus the star exists to
     * offer. Invulnerability comes last, because it protects without rewarding.
     *
     * @param index which entity
     */
    private void resolve(int index) {
        Entity entity = course.entityAt(index);
        resolvedAt[index] = now;

        // Overdue by more than a frame or two: the playhead jumped over it, or frames were dropped.
        // Written off rather than awarded, so a seek cannot be used to collect a course. The grace
        // is added because judgement is deliberately that late; without it every entity in an
        // ordinary run would look a tenth of a second overdue.
        if (now - entity.beatTime() > RESOLVE_WINDOW_SECONDS + JUDGEMENT_GRACE_SECONDS) {
            states[index] = EntityState.MISSED;
            return;
        }
        // The lane the racer was in when the entity arrived, not the one they are in a grace period
        // later. Judging on the current lane would make the grace punish steering: a player who
        // moved just after passing an obstacle would be hit by one they had already gone by.
        if (entity.lane() != laneAt(entity.beatTime())) {
            states[index] = EntityState.MISSED;
            return;
        }

        switch (entity) {
            case Coin coin -> {
                states[index] = EntityState.COLLECTED;
                score.collectCoin();
                for (RunnerListener listener : listeners) {
                    listener.coinCollected(coin, score);
                }
            }
            case Star star -> {
                states[index] = EntityState.COLLECTED;
                score.collectStar();
                // Counted from the beat the star sat on rather than from the moment it was judged,
                // so the invulnerability covers the same musical span however late judgement is.
                starUntil = star.beatTime() + starDurationSeconds();
                for (RunnerListener listener : listeners) {
                    listener.starCollected(star, score);
                }
            }
            case Obstacle obstacle -> resolveObstacle(index, obstacle);
        }
    }

    /**
     * @param index    which entity
     * @param obstacle the obstacle in the racer's lane
     */
    private void resolveObstacle(int index, Obstacle obstacle) {
        double beat = obstacle.beatTime();
        if (beat < starUntil) {
            states[index] = EntityState.BROKEN;
            score.breakObstacle();
            for (RunnerListener listener : listeners) {
                listener.obstacleBroken(obstacle, score);
            }
            return;
        }
        if (clearedByJump(beat)) {
            states[index] = EntityState.CLEARED;
            for (RunnerListener listener : listeners) {
                listener.obstacleCleared(obstacle);
            }
            return;
        }
        if (beat < invulnerableUntil) {
            // Still recovering from the last one. Passing through costs nothing, so a single
            // mistake cannot chain into a wipeout while the player is getting their bearings.
            states[index] = EntityState.MISSED;
            return;
        }
        states[index] = EntityState.HIT;
        score.hitObstacle();
        invulnerableUntil = now + INVULNERABLE_SECONDS;
        for (RunnerListener listener : listeners) {
            listener.obstacleHit(obstacle, score);
        }
    }

    /**
     * Whether a jump covered an obstacle's arrival.
     *
     * <p><strong>An interval overlap, not a reading of the current state.</strong> The jump spans
     * {@code [jumpStartedAt, jumpStartedAt + JUMP_SECONDS)} and the obstacle may be cleared by a
     * press anywhere from a full jump before its beat to {@link #JUDGEMENT_GRACE_SECONDS} after it,
     * so the question is whether those two stretches touch. Asking {@link #isJumping()} at the
     * moment of judgement would answer a different question - whether the racer is airborne
     * <em>now</em> - and would refuse a jump that was in the air for the whole of the arrival and
     * had just landed.
     *
     * @param beat when the obstacle reached the racer, in seconds
     * @return whether the racer was airborne over its arrival, allowing for the grace
     */
    private boolean clearedByJump(double beat) {
        return jumpStartedAt <= beat + JUDGEMENT_GRACE_SECONDS
                && jumpStartedAt + JUMP_SECONDS > beat;
    }

    /** @return how long a star lasts on this course, in seconds */
    private double starDurationSeconds() {
        double period = course.beatPeriodSeconds();
        return period > 0 ? STAR_BEATS * period : STAR_FALLBACK_SECONDS;
    }

    /** @return the position the run has reached, in seconds */
    public double now() {
        return now;
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    /** Moves one lane left, or does nothing at the left edge. */
    public void moveLeft() {
        changeLane(lane.left());
    }

    /** Moves one lane right, or does nothing at the right edge. */
    public void moveRight() {
        changeLane(lane.right());
    }

    /**
     * Changes lane.
     *
     * <p><strong>The lane changes at once; only the sprite glides.</strong> A logical lane that
     * eased across with the drawing would mean a player who moved a frame before an obstacle
     * arrived was still counted as standing in front of it - and there is no way to explain that to
     * them, because the screen showed them moving. The glide is presentation, and
     * {@link #lanePosition()} is where it lives.
     *
     * @param target the lane to move to
     */
    private void changeLane(Lane target) {
        if (target == lane) {
            return;
        }
        Lane leaving = lane;
        // Starts from where the sprite actually is, so two quick presses run into one slide rather
        // than snapping back to the lane the first one started from.
        laneFrom = lanePosition();
        lane = target;
        laneChangedAt = now;

        int slot = laneChanges % LANE_HISTORY;
        laneHistory[slot] = target;
        laneLeft[slot] = leaving;
        laneHistoryAt[slot] = now;
        laneChanges++;
    }

    /**
     * Which lane the racer was in at a past instant.
     *
     * <p>Only ever asked about the very recent past - one grace period, which is shorter than a
     * single lane change takes - so the remembered changes always cover it. Each entry records the
     * lane moved <em>into</em> and the one moved <em>out of</em>, so an instant before every
     * remembered change still has an exact answer rather than a guess.
     *
     * @param time the instant to ask about, in seconds
     * @return the lane the racer occupied then
     */
    private Lane laneAt(double time) {
        if (time >= laneChangedAt) {
            return lane;
        }
        int remembered = Math.min(laneChanges, LANE_HISTORY);
        int oldest = Math.floorMod(laneChanges - remembered, LANE_HISTORY);
        for (int back = 0; back < remembered; back++) {
            int slot = Math.floorMod(laneChanges - 1 - back, LANE_HISTORY);
            if (laneHistoryAt[slot] <= time) {
                return laneHistory[slot];
            }
        }
        // Older than anything remembered: the lane the oldest remembered change moved out of.
        return remembered == 0 ? lane : laneLeft[oldest];
    }

    /** Starts a jump, unless one is already running. */
    public void jump() {
        if (!isJumping()) {
            jumpStartedAt = now;
        }
    }

    // ------------------------------------------------------------------
    // Where the racer is
    // ------------------------------------------------------------------

    /** @return the lane the racer is in, for collisions */
    public Lane lane() {
        return lane;
    }

    /**
     * @return where the sprite is across the road as a fractional lane index, 0 at the left edge
     *         and {@code Lane.COUNT - 1} at the right
     */
    public double lanePosition() {
        double elapsed = (now - laneChangedAt) / LANE_CHANGE_SECONDS;
        if (!(elapsed < 1)) {
            // Also catches the infinities before the first lane change.
            return lane.index();
        }
        return laneFrom + (lane.index() - laneFrom) * ease(Math.max(0, elapsed));
    }

    /**
     * @return whether the racer is off the ground; before the first jump {@code jumpStartedAt} is
     *         negative infinity, which makes the difference infinite and the answer no
     */
    public boolean isJumping() {
        return now - jumpStartedAt < JUMP_SECONDS;
    }

    /** @return how far through the jump the racer is, 0 at take-off and 1 on landing */
    public double jumpProgress() {
        if (!isJumping()) {
            return 0;
        }
        return Math.clamp((now - jumpStartedAt) / JUMP_SECONDS, 0d, 1d);
    }

    /**
     * @return how high the racer is off the ground, 0 on the ground and 1 at the top of the arc -
     *         a half sine, so the take-off and the landing are gentle and the apex is brief
     */
    public double jumpHeight() {
        return isJumping() ? Math.sin(Math.PI * jumpProgress()) : 0;
    }

    /** @return whether the star is running */
    public boolean isStarred() {
        return now < starUntil;
    }

    /** @return how long the star has left, in seconds, or 0 when it is not running */
    public double starRemainingSeconds() {
        return isStarred() ? starUntil - now : 0;
    }

    /** @return whether the racer is in the brief protected spell after taking a bump */
    public boolean isInvulnerable() {
        return now < invulnerableUntil;
    }

    // ------------------------------------------------------------------
    // What the view has to draw
    // ------------------------------------------------------------------

    /** @return the first entity index worth drawing, including recently resolved ones */
    public int firstVisible() {
        return firstVisible;
    }

    /** @return the last entity index worth drawing, or one less than the first when there are none */
    public int lastVisible() {
        return lastVisible;
    }

    /**
     * @param index which entity
     * @return what became of it
     */
    public EntityState stateOf(int index) {
        return states[index];
    }

    /**
     * @param index which entity
     * @return when it was resolved, in seconds, or 0 while it is still pending
     */
    public double resolvedAt(int index) {
        return resolvedAt[index];
    }

    /**
     * How far down the course an entity has travelled.
     *
     * <p>This is the lookahead expressed as a number the view can multiply by a distance: 0 the
     * instant it appears at the far end, 1 the instant it reaches the racer - which is its beat.
     * Past 1 it has gone by, and the view uses that to slide an effect off the bottom of the screen.
     *
     * @param index which entity
     * @return its progress along the course
     */
    public double progressOf(int index) {
        double travel = course.travelTimeSeconds();
        if (travel <= 0) {
            return 1;
        }
        return 1 - (course.entityAt(index).beatTime() - now) / travel;
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    /**
     * @param listener told when the racer collects or collides with something; must not be
     *                 {@code null}
     */
    public void addListener(RunnerListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * @param listener the listener to remove
     */
    public void removeListener(RunnerListener listener) {
        listeners.remove(listener);
    }

    /**
     * Eases a linear fraction so a movement starts and stops gently.
     *
     * @param t linear progress in 0..1
     * @return eased progress in 0..1
     */
    static double ease(double t) {
        double clamped = Math.clamp(t, 0d, 1d);
        return clamped < 0.5
                ? 2 * clamped * clamped
                : 1 - Math.pow(-2 * clamped + 2, 2) / 2;
    }
}
