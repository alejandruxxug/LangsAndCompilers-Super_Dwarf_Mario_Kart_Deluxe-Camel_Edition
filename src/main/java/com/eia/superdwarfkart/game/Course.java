package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.analysis.Beatmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The course one song generates at one speed class: an ordered run of entities, each pinned to an
 * instant in the music.
 *
 * <p>A course is <strong>immutable and deterministic</strong>. The same song at the same speed class
 * always produces byte-for-byte the same course, because the generator is seeded from
 * {@code songId} and the class name and from nothing else - no clock, no iteration order, no
 * identity hash. That is not a nicety: a high score is stored against a {@code (song, class)} pair,
 * and if the course behind that pair could differ between runs the score board would be recording
 * noise. It is also what lets a course be thrown away and rebuilt whenever it is wanted rather than
 * cached alongside the beatmap.
 *
 * <p><strong>Entities are placed on the music, never on a timer.</strong> Every candidate instant
 * comes out of the {@link Beatmap} - the strong beats, thinned by
 * {@link SpeedClass#beatInterval()}, plus the intermediate onsets at 200cc. A passage the track
 * drops out of produces no beats and therefore no entities, so the game can never spawn a row of
 * coins onto silence.
 *
 * <p>Nothing here is measured in pixels. The course has a length in abstract units and the class
 * gives it a speed, which together give {@link #travelTimeSeconds()} - how long an entity spends
 * travelling towards the racer, and therefore how far ahead of its beat it has to appear. The view
 * scales those units to whatever canvas it has.
 */
public final class Course {

    // ------------------------------------------------------------------
    // The lookahead
    // ------------------------------------------------------------------

    /**
     * How long the course is, in abstract units.
     *
     * <p>Paired with {@link #BASE_SPEED_UNITS_PER_SECOND} this is the whole of
     * {@code travelTime = courseLength / speed(cc)}. Keeping it abstract is what stops the game
     * logic from acquiring a canvas size: the view maps the same units onto whatever room it has,
     * and resizing the window cannot change when an entity is due.
     */
    public static final double LENGTH_UNITS = 1000;

    /** How fast the course travels at 50cc, in units per second. */
    public static final double BASE_SPEED_UNITS_PER_SECOND = 450;

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    /** Share of placed entities that are obstacles rather than coins. */
    private static final double OBSTACLE_CHANCE = 0.28;

    /** Share that are stars. Rare enough to be an event, common enough to actually turn up. */
    private static final double STAR_CHANCE = 0.05;

    /** Shortest gap between two stars, in seconds, whatever the dice say. */
    private static final double STAR_MIN_GAP_SECONDS = 15;

    // ------------------------------------------------------------------
    // Walls
    // ------------------------------------------------------------------

    /**
     * Shortest gap between two walls, in seconds.
     *
     * <p>Short, because a wall is the only thing on the course that <em>requires</em> the jump, and
     * a control the player meets twice a song is a control they never learn. The track's own
     * accents decide where they actually land, so a busy stretch gets several and a quiet one gets
     * none - the spacing only stops two of them arriving on top of each other.
     */
    private static final double WALL_MIN_GAP_SECONDS = 5;

    /**
     * How much clear road a wall gets to either side of it, in seconds.
     *
     * <p>Nothing else is placed inside this. The player has to see the wall coming, take off, and
     * land - which is {@code RunnerGame.JUMP_SECONDS} plus the reaction before it - and a coin
     * tucked against a wall would be asking them to be in a lane and in the air at once.
     */
    private static final double WALL_CLEARANCE_SECONDS = 0.6;

    /**
     * Two entities closer together than this are treated as one event.
     *
     * <p>Onsets arrive on the analyser's hop boundaries and a drum hit can register twice a few
     * milliseconds apart. Two entities that close are drawn on top of each other and resolved in
     * the same frame, which is unreadable and - for two obstacles in different lanes - unavoidable.
     */
    private static final double MIN_EVENT_GAP_SECONDS = 0.09;

    /**
     * How long the racer needs to get out of a lane, in seconds.
     *
     * <p><strong>This is what makes a generated course survivable.</strong> Two obstacles closer
     * together than this in different lanes cannot both be dodged, however well the player reads
     * them, so the second is moved into the first one's lane: having got out of the way once, the
     * player stays out of the way. Real seconds rather than course units, because it is a human
     * reaction that is being allowed for, not a distance - which is exactly why the faster classes
     * are harder without ever becoming unfair.
     */
    private static final double SAFE_DODGE_SECONDS = 0.30;

    /**
     * Coins within this of each other tend to continue in the same lane, forming a run.
     *
     * <p>A lane drawn independently per coin gives a field of scattered dots that reads as output.
     * Runs of three or four in a line read as a route someone chose, and they are what makes
     * collecting feel like driving rather than like twitching.
     */
    private static final double COIN_RUN_SECONDS = 0.55;

    /** How often a coin continues the previous coin's lane when it is close enough to. */
    private static final double COIN_RUN_CHANCE = 0.6;

    /**
     * Silence at the start of a run, in seconds, on top of the travel time.
     *
     * <p>An entity due before the course has finished filling would begin the song already halfway
     * down the road, and one due in the first instants is gone before the player has looked at the
     * screen.
     */
    private static final double LEAD_IN_SECONDS = 1.0;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private final String songId;
    private final SpeedClass speedClass;
    private final double durationSeconds;
    private final double beatPeriodSeconds;
    private final Entity[] entities;

    /**
     * The entities' times, extracted once.
     *
     * <p>The spawn loop asks "what is due next" at the display's rate, and a long track at 200cc
     * carries a four-figure number of entities. A parallel array of primitives is what makes that
     * a binary search over a contiguous block rather than a walk through boxed objects.
     */
    private final double[] times;

    private final int coinsAvailable;
    private final int obstacleCount;
    private final int starCount;
    private final int wallCount;

    private Course(String songId, SpeedClass speedClass, double durationSeconds,
                   double beatPeriodSeconds, Entity[] entities) {
        this.songId = songId;
        this.speedClass = speedClass;
        this.durationSeconds = Math.max(0, durationSeconds);
        this.beatPeriodSeconds = Math.max(0, beatPeriodSeconds);
        this.entities = entities;

        this.times = new double[entities.length];
        int coins = 0;
        int obstacles = 0;
        int stars = 0;
        int walls = 0;
        double lastWall = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < entities.length; index++) {
            times[index] = entities[index].beatTime();
            switch (entities[index]) {
                case Coin ignored -> coins++;
                case Star ignored -> stars++;
                case Obstacle obstacle -> {
                    obstacles++;
                    // A wall is one event made of three obstacles, and it is the event that gets
                    // counted - reporting "252 bumps" for a course whose bumps are mostly rows of
                    // three says nothing about what driving it is like.
                    if (obstacle.isWall() && obstacle.beatTime() > lastWall) {
                        walls++;
                        lastWall = obstacle.beatTime();
                    }
                }
            }
        }
        this.coinsAvailable = coins;
        this.obstacleCount = obstacles;
        this.starCount = stars;
        this.wallCount = walls;
    }

    /**
     * Builds the course a song generates at a speed class.
     *
     * <p>Pure: same arguments, same course, on any machine and in any order. See the class note on
     * why that matters more than it looks like it should.
     *
     * @param songId     the song's stable identifier, half of the generator's seed
     * @param beatmap    what the analyser found; {@link Beatmap#EMPTY} yields an empty course
     * @param speedClass the class being driven, the other half of the seed
     * @return the generated course, never {@code null}
     */
    public static Course generate(String songId, Beatmap beatmap, SpeedClass speedClass) {
        Objects.requireNonNull(songId, "songId must not be null");
        Objects.requireNonNull(speedClass, "speedClass must not be null");
        Beatmap map = beatmap == null ? Beatmap.EMPTY : beatmap;

        double travelTime = travelTimeSeconds(speedClass);
        double earliest = travelTime + LEAD_IN_SECONDS;
        double[] walls = wallTimes(map, earliest);
        double[] moments = eventTimes(map, speedClass, earliest, walls);

        // Seeded from the song and the class and from nothing else. A Random built from a clock, or
        // a lane drawn from an object's identity hash, would give a different course every launch.
        Random random = new Random(seedFor(songId, speedClass));

        List<Entity> placed = new ArrayList<>(moments.length + walls.length * Lane.COUNT);
        for (double wall : walls) {
            for (Lane lane : Lane.values()) {
                placed.add(new Obstacle(wall, lane, true));
            }
        }

        double lastObstacleTime = Double.NEGATIVE_INFINITY;
        Lane lastObstacleLane = Lane.starting();
        double lastStarTime = Double.NEGATIVE_INFINITY;
        double lastCoinTime = Double.NEGATIVE_INFINITY;
        Lane lastCoinLane = Lane.starting();

        for (double moment : moments) {
            double roll = random.nextDouble();
            Lane lane = Lane.ofIndex(random.nextInt(Lane.COUNT));

            if (roll < STAR_CHANCE && moment - lastStarTime >= STAR_MIN_GAP_SECONDS) {
                placed.add(new Star(moment, lane));
                lastStarTime = moment;
                continue;
            }
            if (roll < STAR_CHANCE + OBSTACLE_CHANCE) {
                if (moment - lastObstacleTime < SAFE_DODGE_SECONDS) {
                    // Too soon to have got out of another lane: leave it where the player already is.
                    lane = lastObstacleLane;
                }
                placed.add(new Obstacle(moment, lane));
                lastObstacleTime = moment;
                lastObstacleLane = lane;
                continue;
            }
            if (moment - lastCoinTime < COIN_RUN_SECONDS && random.nextDouble() < COIN_RUN_CHANCE) {
                lane = lastCoinLane;
            }
            placed.add(new Coin(moment, lane));
            lastCoinTime = moment;
            lastCoinLane = lane;
        }

        // The walls went in first and the rest were laid around them, so the list is in two runs
        // rather than in order.
        placed.sort(java.util.Comparator.comparingDouble(Entity::beatTime));
        return new Course(songId, speedClass, map.durationSeconds(), map.beatPeriod(),
                placed.toArray(new Entity[0]));
    }

    /**
     * Chooses the beats that get a wall across the whole road.
     *
     * <p><strong>The accents, not a metronome.</strong> Every strong beat carries a strength - how
     * far that attack stood above its own surroundings - and the loudest fifth of them are the
     * downbeats, the snare hits, the moment a drop lands. Putting the one obstacle that cannot be
     * steered around on exactly those beats is what makes the jump feel like it belongs to the
     * music instead of to a timer, and it is the reason the analyser now records strengths at all.
     *
     * <p>A track the analyser found no strengths in - an older cache entry, or one with no tempo -
     * has an infinite threshold and therefore no walls, rather than a wall on every beat.
     *
     * @param beatmap  what the analyser found
     * @param earliest nothing before this time, in seconds
     * @return the wall times, ascending
     */
    private static double[] wallTimes(Beatmap beatmap, double earliest) {
        double threshold = beatmap.accentThreshold();
        if (Double.isInfinite(threshold)) {
            return new double[0];
        }

        double[] found = new double[beatmap.strongBeatCount()];
        int count = 0;
        double last = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < beatmap.strongBeatCount(); index++) {
            double beat = beatmap.strongBeatAt(index);
            if (beat < earliest || beatmap.strongBeatStrengthAt(index) < threshold) {
                continue;
            }
            if (beat - last < WALL_MIN_GAP_SECONDS) {
                continue;
            }
            found[count++] = beat;
            last = beat;
        }
        return Arrays.copyOf(found, count);
    }

    /**
     * @param walls  the wall times, ascending
     * @param moment a candidate time
     * @return whether it is inside a wall's clear road
     */
    private static boolean nearWall(double[] walls, double moment) {
        int at = Arrays.binarySearch(walls, moment);
        if (at >= 0) {
            return true;
        }
        int after = -(at + 1);
        if (after < walls.length && walls[after] - moment < WALL_CLEARANCE_SECONDS) {
            return true;
        }
        return after > 0 && moment - walls[after - 1] < WALL_CLEARANCE_SECONDS;
    }

    /**
     * Builds a course with nothing on it, for a song whose beatmap is not ready or could not be
     * produced.
     *
     * <p>The runner drives an empty course rather than refusing to start - ground rule 5 - so the
     * road, the meters and the beat pulse all work while the analysis is still running and the
     * entities simply arrive when it finishes.
     *
     * @param songId     the song's identifier
     * @param speedClass the class being driven
     * @return an empty course
     */
    public static Course empty(String songId, SpeedClass speedClass) {
        return new Course(Objects.requireNonNull(songId, "songId must not be null"),
                Objects.requireNonNull(speedClass, "speedClass must not be null"),
                0, 0, new Entity[0]);
    }

    /**
     * Lays a course out by hand.
     *
     * <p>Package-private, and deliberately so. {@link #generate} is the only way a course is made
     * in the running application - the whole point of it is that nobody chooses where the entities
     * go - but the collision rules have to be tested against a course that says exactly "a bump in
     * the middle lane at five seconds", which a seeded generator can never be asked for. This is
     * that door, and it is only wide enough for the tests in this package.
     *
     * @param songId          the identifier to file it under
     * @param speedClass      the class it is driven at
     * @param durationSeconds the track's playing time
     * @param beatPeriod      seconds between beats, which sets how long a star lasts
     * @param entities        the entities to place, in ascending time order
     * @return the course
     * @throws IllegalArgumentException if the entities are not in ascending time order
     */
    static Course of(String songId, SpeedClass speedClass, double durationSeconds,
                     double beatPeriod, List<Entity> entities) {
        Objects.requireNonNull(entities, "entities must not be null");
        for (int index = 1; index < entities.size(); index++) {
            if (entities.get(index).beatTime() < entities.get(index - 1).beatTime()) {
                throw new IllegalArgumentException("entities must be in ascending time order, but "
                        + entities.get(index) + " follows " + entities.get(index - 1));
            }
        }
        return new Course(Objects.requireNonNull(songId, "songId must not be null"),
                Objects.requireNonNull(speedClass, "speedClass must not be null"),
                durationSeconds, beatPeriod, entities.toArray(new Entity[0]));
    }

    /**
     * Chooses the instants entities may be placed on.
     *
     * @param beatmap    what the analyser found
     * @param speedClass the class being driven
     * @param earliest   nothing before this time, in seconds
     * @param walls      the wall times, which claim the road either side of them
     * @return the candidate times, ascending, with near-duplicates removed
     */
    private static double[] eventTimes(Beatmap beatmap, SpeedClass speedClass, double earliest,
                                       double[] walls) {
        double[] strong = beatmap.strongBeats();
        List<Double> candidates = new ArrayList<>(strong.length);
        for (int index = 0; index < strong.length; index += speedClass.beatInterval()) {
            if (strong[index] >= earliest && !nearWall(walls, strong[index])) {
                candidates.add(strong[index]);
            }
        }

        if (speedClass.usesIntermediateOnsets()) {
            // Everything the track played, not only what landed on the grid. This is what makes
            // 200cc follow a fill rather than only the pulse underneath it.
            for (double onset : beatmap.onsets()) {
                if (onset >= earliest && !nearWall(walls, onset)) {
                    candidates.add(onset);
                }
            }
        }

        double[] moments = new double[candidates.size()];
        for (int index = 0; index < moments.length; index++) {
            moments[index] = candidates.get(index);
        }
        Arrays.sort(moments);

        int kept = 0;
        for (double moment : moments) {
            if (kept == 0 || moment - moments[kept - 1] >= MIN_EVENT_GAP_SECONDS) {
                moments[kept++] = moment;
            }
        }
        return Arrays.copyOf(moments, kept);
    }

    /**
     * Derives the generator's seed from the song and the class.
     *
     * <p>An FNV-1a hash rather than {@link String#hashCode()}: 64 bits instead of 32, so two songs
     * in one library are not going to share a course, and defined here rather than by the platform
     * so the number cannot change under a future runtime and quietly re-roll every stored score.
     *
     * @param songId     the song's identifier
     * @param speedClass the class being driven
     * @return a seed that depends on both and on nothing else
     */
    static long seedFor(String songId, SpeedClass speedClass) {
        String key = songId + '|' + speedClass.name();
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < key.length(); index++) {
            hash ^= key.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // ------------------------------------------------------------------
    // What the course is
    // ------------------------------------------------------------------

    /** @return the song this course was generated from */
    public String songId() {
        return songId;
    }

    /** @return the speed class it was generated for */
    public SpeedClass speedClass() {
        return speedClass;
    }

    /** @return the track's playing time, in seconds */
    public double durationSeconds() {
        return durationSeconds;
    }

    /** @return seconds between beats on the source track, or 0 when no tempo was established */
    public double beatPeriodSeconds() {
        return beatPeriodSeconds;
    }

    /** @return how many entities the course holds */
    public int size() {
        return entities.length;
    }

    /** @return whether there is nothing on the course */
    public boolean isEmpty() {
        return entities.length == 0;
    }

    /**
     * @param index which entity, counting from zero, in ascending time order
     * @return the entity
     * @throws IndexOutOfBoundsException if there is no such entity
     */
    public Entity entityAt(int index) {
        return entities[index];
    }

    /** @return every entity, in ascending time order; a copy, safe to keep */
    public List<Entity> entities() {
        return List.of(entities);
    }

    /**
     * Finds where to start reading for a window that has just moved forward.
     *
     * <p>Binary search rather than a scan, for the same reason
     * {@link Beatmap#firstOnsetAtOrAfter(double)} is: this is asked at the display's rate over a
     * four-figure array.
     *
     * @param seconds the time to search from
     * @return the index of the first entity due at or after that time, or {@link #size()} when
     *         there is none
     */
    public int firstEntityAtOrAfter(double seconds) {
        int found = Arrays.binarySearch(times, seconds);
        // A miss encodes the insertion point, which is the answer; a hit may be one of several
        // equal entries, so walk back to the first of them.
        if (found < 0) {
            return -(found + 1);
        }
        while (found > 0 && times[found - 1] >= seconds) {
            found--;
        }
        return found;
    }

    /** @return how many coins are on the course, the denominator {@link Rank} is measured against */
    public int coinsAvailable() {
        return coinsAvailable;
    }

    /** @return how many obstacles are on the course */
    public int obstacleCount() {
        return obstacleCount;
    }

    /** @return how many stars are on the course */
    public int starCount() {
        return starCount;
    }

    /**
     * @return how many walls are on the course - rows blocking every lane, each one a jump the
     *         player has no way around
     */
    public int wallCount() {
        return wallCount;
    }

    /**
     * How long an entity spends travelling towards the racer.
     *
     * <p>This is the lookahead. An entity due at {@code T} has to appear at {@code T - travelTime}
     * for the two to meet on the beat, and it is the only reason the game looks synchronised rather
     * than merely fast.
     *
     * @return the travel time, in seconds
     */
    public double travelTimeSeconds() {
        return travelTimeSeconds(speedClass);
    }

    /**
     * @param speedClass the class being driven
     * @return how long an entity spends travelling at that class, in seconds
     */
    public static double travelTimeSeconds(SpeedClass speedClass) {
        return LENGTH_UNITS / (BASE_SPEED_UNITS_PER_SECOND * speedClass.speedMultiplier());
    }

    @Override
    public String toString() {
        return "Course[" + speedClass.displayName() + ", " + entities.length + " entities: "
                + coinsAvailable + " coins, " + obstacleCount + " bumps, " + wallCount + " walls, "
                + starCount + " stars, "
                + String.format("%.2f", travelTimeSeconds()) + "s lookahead]";
    }
}
