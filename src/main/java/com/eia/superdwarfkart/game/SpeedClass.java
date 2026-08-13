package com.eia.superdwarfkart.game;

import java.util.Arrays;
import java.util.Optional;

/**
 * How fast the course comes at the player, and how much of the music it spends.
 *
 * <p>The four classes are named after Mario Kart's engine classes, and they change three things at
 * once - which is what makes them read as difficulty rather than as a speed slider:
 *
 * <ul>
 *   <li><strong>Speed.</strong> The course travels at {@link #speedMultiplier()} times the base
 *       rate, so the same lookahead distance is covered in less time and there is less of it to
 *       react in.</li>
 *   <li><strong>Density.</strong> {@link #beatInterval()} says how many strong beats pass between
 *       entities, and 200cc additionally spends the {@linkplain #usesIntermediateOnsets()
 *       intermediate onsets} - the attacks that did not land on the tempo grid. <strong>A faster
 *       class is denser because the music is denser</strong>, not because a timer was turned up:
 *       a sparse ambient passage stays sparse at 200cc, and a busy one fills.</li>
 *   <li><strong>Reward.</strong> {@link #scoreMultiplier()} pays for the risk. The rank does not
 *       use it - see {@link Rank} - so a clean run at 50cc still ranks S; the multiplier only
 *       separates two S runs by what they were worth.</li>
 * </ul>
 *
 * <p>The four figures below are fixed by the project brief. They are not tuning knobs: a course is
 * cached and a high score is stored against the class it was set on, so changing one of these
 * numbers silently invalidates every score anyone has recorded.
 */
public enum SpeedClass {

    /** Gentle: every fourth strong beat, base speed, no bonus. */
    CC50("50cc", 1.0, 4, 1.0, false),

    /** Every second strong beat at 1.4x speed. */
    CC100("100cc", 1.4, 2, 1.5, false),

    /** Every strong beat at 1.8x speed. */
    CC150("150cc", 1.8, 1, 2.0, false),

    /** Every strong beat <em>and</em> the intermediate onsets, at 2.2x speed. */
    CC200("200cc", 2.2, 1, 3.0, true);

    private final String displayName;
    private final double speedMultiplier;
    private final int beatInterval;
    private final double scoreMultiplier;
    private final boolean intermediateOnsets;

    SpeedClass(String displayName, double speedMultiplier, int beatInterval, double scoreMultiplier,
               boolean intermediateOnsets) {
        this.displayName = displayName;
        this.speedMultiplier = speedMultiplier;
        this.beatInterval = beatInterval;
        this.scoreMultiplier = scoreMultiplier;
        this.intermediateOnsets = intermediateOnsets;
    }

    /** @return the name shown on the selector, for example {@code 150cc} */
    public String displayName() {
        return displayName;
    }

    /** @return how much faster than the base rate the course travels */
    public double speedMultiplier() {
        return speedMultiplier;
    }

    /** @return how many strong beats pass between placed entities; 1 means every beat */
    public int beatInterval() {
        return beatInterval;
    }

    /** @return what a collected coin is worth, as a multiple of its face value */
    public double scoreMultiplier() {
        return scoreMultiplier;
    }

    /**
     * @return whether onsets that did <em>not</em> fall on the tempo grid also carry entities,
     *         which is what makes 200cc follow the track's fills rather than only its pulse
     */
    public boolean usesIntermediateOnsets() {
        return intermediateOnsets;
    }

    /**
     * Steps to the next class, wrapping from the fastest back to the slowest.
     *
     * @return the next class in the list
     */
    public SpeedClass next() {
        SpeedClass[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** @return the class a run starts on when the user has expressed no preference */
    public static SpeedClass defaultClass() {
        return CC50;
    }

    /**
     * Resolves a stored class name.
     *
     * <p>{@link #valueOf} would throw on a name this build does not know, which a profile written
     * by a later version could easily contain and which is no reason to refuse to open. An unknown
     * name is simply absent, and the caller keeps its default.
     *
     * @param name the constant name to resolve; {@code null} yields an empty result
     * @return the class with that name, if there is one
     */
    public static Optional<SpeedClass> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(speed -> speed.name().equals(name)).findFirst();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
