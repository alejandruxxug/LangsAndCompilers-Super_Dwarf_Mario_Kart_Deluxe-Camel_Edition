package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.analysis.Beatmap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Course generation: reproducible, on the music, and possible to survive. */
class CourseTest {

    /**
     * A steady 120 BPM track with an onset halfway between every beat, and no accents at all.
     *
     * <p>Flat strengths on purpose: most of these tests are about the ordinary placement rules, and
     * a course peppered with walls would keep tripping them over for reasons that have nothing to
     * do with what is being checked. {@link #accentedBeatmap} is the one that has accents.
     */
    private static Beatmap steadyBeatmap(double seconds) {
        double period = 0.5;
        List<Double> strong = new ArrayList<>();
        List<Double> onsets = new ArrayList<>();
        for (double at = 0; at < seconds; at += period) {
            strong.add(at);
            onsets.add(at);
            onsets.add(at + period / 2);
        }
        return new Beatmap("hash", 1, seconds, 120, toArray(onsets), toArray(strong));
    }

    /**
     * The same track, with every fourth beat twice as strong as the rest - a downbeat.
     *
     * @param seconds how long the track runs
     * @return a beatmap whose accents are on the bar lines
     */
    private static Beatmap accentedBeatmap(double seconds) {
        Beatmap flat = steadyBeatmap(seconds);
        double[] strong = flat.strongBeats();
        double[] strengths = new double[strong.length];
        for (int index = 0; index < strengths.length; index++) {
            strengths[index] = index % 4 == 0 ? 8 : 3;
        }
        return new Beatmap("hash", 1, seconds, 120, flat.onsets(), strong, strengths);
    }

    private static double[] toArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int index = 0; index < array.length; index++) {
            array[index] = values.get(index);
        }
        return array;
    }

    // ------------------------------------------------------------------
    // Reproducibility - what the score board rests on
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the same song at the same class always generates the identical course")
    void generationIsDeterministic() {
        Beatmap beatmap = steadyBeatmap(120);
        Course first = Course.generate("song-1", beatmap, SpeedClass.CC150);
        Course second = Course.generate("song-1", beatmap, SpeedClass.CC150);

        assertEquals(first.entities(), second.entities(),
                "a stored high score is filed against a (song, class) pair; if the course behind "
                        + "that pair could differ between runs the board would be recording noise");
    }

    @Test
    @DisplayName("a different song generates a different course")
    void differentSongsDiffer() {
        Beatmap beatmap = steadyBeatmap(120);
        assertNotEquals(
                Course.generate("song-1", beatmap, SpeedClass.CC150).entities(),
                Course.generate("song-2", beatmap, SpeedClass.CC150).entities());
    }

    @Test
    @DisplayName("a different speed class generates a different course from the same song")
    void differentClassesDiffer() {
        Beatmap beatmap = steadyBeatmap(120);
        assertNotEquals(
                Course.generate("song-1", beatmap, SpeedClass.CC150).entities(),
                Course.generate("song-1", beatmap, SpeedClass.CC200).entities());
    }

    @Test
    @DisplayName("the seed depends on both the song and the class")
    void seedDependsOnBoth() {
        assertNotEquals(Course.seedFor("a", SpeedClass.CC50), Course.seedFor("b", SpeedClass.CC50));
        assertNotEquals(Course.seedFor("a", SpeedClass.CC50), Course.seedFor("a", SpeedClass.CC100));
        assertEquals(Course.seedFor("a", SpeedClass.CC50), Course.seedFor("a", SpeedClass.CC50));
    }

    // ------------------------------------------------------------------
    // Placed on the music
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SpeedClass.class)
    @DisplayName("every entity sits on a time the analyser reported")
    void entitiesSitOnAnalysedTimes(SpeedClass speedClass) {
        Beatmap beatmap = steadyBeatmap(120);
        Course course = Course.generate("song-1", beatmap, speedClass);
        double[] onsets = beatmap.onsets();

        for (Entity entity : course.entities()) {
            boolean found = false;
            for (double onset : onsets) {
                if (Math.abs(onset - entity.beatTime()) < 1e-9) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, entity + " is not on any onset - an entity placed on a timer rather "
                    + "than on the music would spawn onto silence");
        }
    }

    @Test
    @DisplayName("a track the analyser found nothing in generates an empty course, not an exception")
    void emptyBeatmapGivesEmptyCourse() {
        Course course = Course.generate("song-1", Beatmap.EMPTY, SpeedClass.CC150);
        assertTrue(course.isEmpty());
        assertEquals(0, course.coinsAvailable());
    }

    @Test
    @DisplayName("a null beatmap is treated as an empty one")
    void nullBeatmapGivesEmptyCourse() {
        assertTrue(Course.generate("song-1", null, SpeedClass.CC150).isEmpty());
    }

    @Test
    @DisplayName("nothing is due before the road has finished filling")
    void nothingIsDueDuringTheLeadIn() {
        for (SpeedClass speedClass : SpeedClass.values()) {
            Course course = Course.generate("song-1", steadyBeatmap(120), speedClass);
            assertFalse(course.isEmpty());
            assertTrue(course.entityAt(0).beatTime() >= course.travelTimeSeconds(),
                    speedClass + " placed an entity at " + course.entityAt(0).beatTime()
                            + "s, inside its own " + course.travelTimeSeconds()
                            + "s lookahead - it would begin the song already halfway down the road");
        }
    }

    @Test
    @DisplayName("a faster class spends more of the music than a slower one")
    void fasterClassesAreDenser() {
        Beatmap beatmap = steadyBeatmap(180);
        int previous = 0;
        for (SpeedClass speedClass : SpeedClass.values()) {
            int size = Course.generate("song-1", beatmap, speedClass).size();
            assertTrue(size > previous,
                    speedClass + " placed " + size + " entities, no more than the class below it");
            previous = size;
        }
    }

    @Test
    @DisplayName("200cc uses the onsets between the beats, the slower classes do not")
    void topClassUsesIntermediateOnsets() {
        Beatmap beatmap = steadyBeatmap(120);
        Course fast = Course.generate("song-1", beatmap, SpeedClass.CC200);
        Course steady = Course.generate("song-1", beatmap, SpeedClass.CC150);

        // The beatmap has an onset halfway between every beat; only 200cc should be spending them.
        assertTrue(offBeatCount(fast, 0.5) > 0, "200cc placed nothing between the beats");
        assertEquals(0, offBeatCount(steady, 0.5), "150cc placed something off the beat");
    }

    /**
     * @param course the course to inspect
     * @param period the beat period
     * @return how many entities sit off the beat grid
     */
    private static int offBeatCount(Course course, double period) {
        int found = 0;
        for (Entity entity : course.entities()) {
            double offset = entity.beatTime() % period;
            if (Math.min(offset, period - offset) > 1e-6) {
                found++;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // Survivable
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SpeedClass.class)
    @DisplayName("two obstacles too close together to dodge are put in the same lane")
    void obstaclesTooCloseShareALane(SpeedClass speedClass) {
        Course course = Course.generate("song-1", steadyBeatmap(600), speedClass);

        Obstacle previous = null;
        for (Entity entity : course.entities()) {
            // A wall blocks every lane on purpose and is cleared by jumping, so the lane rule does
            // not apply to it - that is the whole point of one.
            if (!(entity instanceof Obstacle obstacle) || obstacle.isWall()) {
                continue;
            }
            if (previous != null && obstacle.beatTime() - previous.beatTime() < 0.30) {
                assertSame(previous.lane(), obstacle.lane(),
                        "a bump at " + obstacle.beatTime() + "s follows one at "
                                + previous.beatTime() + "s in a different lane, which no player "
                                + "could get out of the way of twice");
            }
            previous = obstacle;
        }
    }

    @ParameterizedTest
    @EnumSource(SpeedClass.class)
    @DisplayName("no two entities land close enough to be drawn on top of each other")
    void entitiesAreNeverStacked(SpeedClass speedClass) {
        Course course = Course.generate("song-1", steadyBeatmap(300), speedClass);
        for (int index = 1; index < course.size(); index++) {
            double gap = course.entityAt(index).beatTime() - course.entityAt(index - 1).beatTime();
            assertTrue(gap >= 0.09 - 1e-9,
                    "two entities " + gap + "s apart are one unreadable event, and if they are two "
                            + "bumps in different lanes they are an unavoidable one");
        }
    }

    // ------------------------------------------------------------------
    // Walls - the jump the player cannot steer around
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SpeedClass.class)
    @DisplayName("a wall blocks every lane, so the only way past is the jump")
    void aWallBlocksEveryLane(SpeedClass speedClass) {
        Course course = Course.generate("song-1", accentedBeatmap(180), speedClass);
        assertTrue(course.wallCount() > 0, "the track's accents produced no walls at all");

        Map<Double, Set<Lane>> byTime = new HashMap<>();
        for (Entity entity : course.entities()) {
            if (entity instanceof Obstacle obstacle && obstacle.isWall()) {
                byTime.computeIfAbsent(obstacle.beatTime(), at -> new HashSet<>())
                        .add(obstacle.lane());
            }
        }
        assertEquals(course.wallCount(), byTime.size());
        for (var wall : byTime.entrySet()) {
            assertEquals(Lane.COUNT, wall.getValue().size(),
                    "the wall at " + wall.getKey() + "s leaves a lane open, so it can be steered "
                            + "around and the jump is never needed");
        }
    }

    @Test
    @DisplayName("walls land on the track's accents, not on a metronome")
    void wallsLandOnAccents() {
        Beatmap beatmap = accentedBeatmap(180);
        Course course = Course.generate("song-1", beatmap, SpeedClass.CC150);
        double threshold = beatmap.accentThreshold();

        for (Entity entity : course.entities()) {
            if (!(entity instanceof Obstacle obstacle) || !obstacle.isWall()) {
                continue;
            }
            int at = indexOfStrongBeat(beatmap, obstacle.beatTime());
            assertTrue(at >= 0, "a wall at " + obstacle.beatTime() + "s is not on a strong beat");
            assertTrue(beatmap.strongBeatStrengthAt(at) >= threshold,
                    "a wall landed on an ordinary beat; the jump would stop belonging to the music");
        }
    }

    /**
     * @param beatmap the beatmap to search
     * @param time    a strong beat time
     * @return its index, or -1
     */
    private static int indexOfStrongBeat(Beatmap beatmap, double time) {
        for (int index = 0; index < beatmap.strongBeatCount(); index++) {
            if (Math.abs(beatmap.strongBeatAt(index) - time) < 1e-9) {
                return index;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("a track with no strengths recorded gets no walls rather than a wall every beat")
    void noStrengthsMeansNoWalls() {
        // Exactly what an older cached beatmap looks like after the analyser version was bumped.
        assertEquals(0, Course.generate("song-1", steadyBeatmap(180), SpeedClass.CC150).wallCount(),
                "accenting everything would be far worse than accenting nothing");
    }

    @Test
    @DisplayName("walls come often, and never on top of one another")
    void wallsAreFrequentAndSpaced() {
        Course course = Course.generate("song-1", accentedBeatmap(180), SpeedClass.CC150);

        double previous = Double.NEGATIVE_INFINITY;
        int walls = 0;
        for (Entity entity : course.entities()) {
            if (entity instanceof Obstacle obstacle && obstacle.isWall()
                    && obstacle.beatTime() > previous) {
                assertTrue(obstacle.beatTime() - previous >= 5 - 1e-9,
                        "two walls " + (obstacle.beatTime() - previous) + "s apart");
                previous = obstacle.beatTime();
                walls++;
            }
        }
        assertTrue(walls >= 20, "only " + walls + " walls in three minutes; the jump is a control "
                + "the player would never learn");
    }

    @ParameterizedTest
    @EnumSource(SpeedClass.class)
    @DisplayName("a wall gets clear road either side, so the jump is not also a lane change")
    void wallsGetClearRoad(SpeedClass speedClass) {
        Course course = Course.generate("song-1", accentedBeatmap(180), speedClass);

        for (Entity entity : course.entities()) {
            if (!(entity instanceof Obstacle obstacle) || !obstacle.isWall()) {
                continue;
            }
            for (Entity other : course.entities()) {
                if (other == entity || other.beatTime() == obstacle.beatTime()) {
                    continue;
                }
                assertTrue(Math.abs(other.beatTime() - obstacle.beatTime()) >= 0.6 - 1e-9,
                        other + " sits inside the clear road around the wall at "
                                + obstacle.beatTime() + "s, asking the player to be in a lane and "
                                + "in the air at once");
            }
        }
    }

    @Test
    @DisplayName("a wall is a different course from an ordinary bump in the same place")
    void wallnessIsPartOfTheCourse() {
        assertNotEquals(new Obstacle(5, Lane.LEFT, true), new Obstacle(5, Lane.LEFT, false));
        assertEquals(new Obstacle(5, Lane.LEFT, true), new Obstacle(5, Lane.LEFT, true));
    }

    @Test
    @DisplayName("a course is mostly coins, so there is something to collect")
    void coinsDominate() {
        Course course = Course.generate("song-1", steadyBeatmap(300), SpeedClass.CC150);
        assertTrue(course.coinsAvailable() > course.obstacleCount(),
                "a course of mostly bumps has nothing to rank a run against");
    }

    @ParameterizedTest
    @EnumSource(SpeedClass.class)
    @DisplayName("stars turn up on every class, and are never bunched")
    void starsTurnUpAndAreSpaced(SpeedClass speedClass) {
        Course course = Course.generate("song-1", steadyBeatmap(600), speedClass);

        double previous = Double.NEGATIVE_INFINITY;
        for (Entity entity : course.entities()) {
            if (entity instanceof Star) {
                assertTrue(entity.beatTime() - previous >= 15 - 1e-9,
                        "two stars " + (entity.beatTime() - previous) + "s apart");
                previous = entity.beatTime();
            }
        }
        assertTrue(course.starCount() > 0,
                speedClass + " put no star on a ten-minute track; the invulnerability, the "
                        + "explosion and the break bonus would all be features nobody ever meets");
        assertTrue(course.starCount() < course.coinsAvailable() / 8,
                "a star that turns up as often as this stops being an event");
    }

    // ------------------------------------------------------------------
    // The lookahead
    // ------------------------------------------------------------------

    @Test
    @DisplayName("travel time is the course length over the class's speed")
    void travelTimeFollowsTheFormula() {
        for (SpeedClass speedClass : SpeedClass.values()) {
            assertEquals(
                    Course.LENGTH_UNITS
                            / (Course.BASE_SPEED_UNITS_PER_SECOND * speedClass.speedMultiplier()),
                    Course.travelTimeSeconds(speedClass), 1e-9);
        }
    }

    @Test
    @DisplayName("a faster class gives less time to react")
    void fasterClassesShortenTheLookahead() {
        assertTrue(Course.travelTimeSeconds(SpeedClass.CC200)
                < Course.travelTimeSeconds(SpeedClass.CC50));
    }

    @Test
    @DisplayName("the entity search finds the first one at or after a time")
    void searchFindsTheFirstEntityDue() {
        Course course = Course.generate("song-1", steadyBeatmap(120), SpeedClass.CC150);

        assertEquals(0, course.firstEntityAtOrAfter(0));
        assertEquals(course.size(), course.firstEntityAtOrAfter(1e6));

        double at = course.entityAt(5).beatTime();
        assertEquals(5, course.firstEntityAtOrAfter(at), "an exact hit must find that entity");
        assertEquals(5, course.firstEntityAtOrAfter(at - 1e-6));
        assertEquals(6, course.firstEntityAtOrAfter(at + 1e-6));
    }
}
