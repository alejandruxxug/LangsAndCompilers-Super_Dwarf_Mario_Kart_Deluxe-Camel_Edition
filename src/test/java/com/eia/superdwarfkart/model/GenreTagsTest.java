package com.eia.superdwarfkart.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Turning Spotify's free-text artist tags into one of our fourteen genres.
 *
 * <p><strong>The whole risk here is the ordering, and it fails quietly.</strong> The tags are
 * compounds and the compound names the parent - {@code "electropop"}, {@code "latin pop"},
 * {@code "synthpop"} and {@code "dream pop"} all contain {@code "pop"} - so matching is on
 * substrings, most specific first. Put the wide patterns above the narrow ones and nothing throws:
 * half the library simply arrives filed under Pop, and a genre box that came up on a plausible wrong
 * answer is worse than one that came up blank.
 */
@DisplayName("Genre from tags")
class GenreTagsTest {

    @Nested
    @DisplayName("the specific pattern wins over the wide one")
    class Ordering {

        @Test
        @DisplayName("a compound built on \"pop\" is filed by its other half")
        void compoundsBeatPop() {
            assertEquals(Genre.ELECTRONIC, Genre.fromTags(List.of("electropop")));
            assertEquals(Genre.ELECTRONIC, Genre.fromTags(List.of("synthpop")));
            assertEquals(Genre.LATIN, Genre.fromTags(List.of("latin pop")));
            assertEquals(Genre.HIP_HOP, Genre.fromTags(List.of("pop rap")));
            // And the bare tag still lands on Pop, which is what makes the ordering safe rather than
            // merely clever: nothing is lost by putting the wide pattern last.
            assertEquals(Genre.POP, Genre.fromTags(List.of("pop")));
        }

        @Test
        @DisplayName("\"dance\" sits below every genre it qualifies, so it never swallows one")
        void danceIsWiderThanPop() {
            // The one pattern that had to go below "pop" as well as below "rock". Above them it filed
            // dance pop and dance rock as electronic, which is three wrong answers for one right one.
            assertEquals(Genre.POP, Genre.fromTags(List.of("dance pop")));
            assertEquals(Genre.ROCK, Genre.fromTags(List.of("dance rock")));
            assertEquals(Genre.ELECTRONIC, Genre.fromTags(List.of("eurodance")));
        }

        @Test
        @DisplayName("a chiptune is a chiptune and not a soundtrack, and both beat electronic")
        void theNarrowestWins() {
            assertEquals(Genre.CHIPTUNE, Genre.fromTags(List.of("chiptune")));
            assertEquals(Genre.CHIPTUNE, Genre.fromTags(List.of("bitpop")));
            assertEquals(Genre.SOUNDTRACK, Genre.fromTags(List.of("video game music")));
            assertEquals(Genre.SOUNDTRACK, Genre.fromTags(List.of("anime score")));
        }

        @Test
        @DisplayName("metalcore is metal, hardcore hip hop is not")
        void compoundsThatCouldGoEitherWay() {
            assertEquals(Genre.METAL, Genre.fromTags(List.of("metalcore")));
            assertEquals(Genre.METAL, Genre.fromTags(List.of("doom metal")));
            // "rap" is above "metal" in the list, and a tag naming both is nearly always the rap one.
            assertEquals(Genre.HIP_HOP, Genre.fromTags(List.of("rap metal")));
        }
    }

    @Nested
    @DisplayName("across a whole tag list")
    class Lists {

        @Test
        @DisplayName("the most specific tag wins wherever it sits in the list")
        void positionDoesNotDecide() {
            // Spotify orders an artist's genres by its own relevance and the specific one is very often
            // not first. Weighting the first tag would file this artist under Pop.
            assertEquals(Genre.CHIPTUNE,
                    Genre.fromTags(List.of("pop", "indie pop", "chiptune")));
            assertEquals(Genre.CHIPTUNE,
                    Genre.fromTags(List.of("chiptune", "pop", "indie pop")));
        }

        @Test
        @DisplayName("a real artist's tags come out as something sensible")
        void realWorldTags() {
            // Taken from live /v1/artists responses, which is the only vocabulary that matters here.
            assertEquals(Genre.ELECTRONIC,
                    Genre.fromTags(List.of("canadian electronic", "witch house", "experimental")));
            assertEquals(Genre.LATIN,
                    Genre.fromTags(List.of("sertanejo", "sertanejo universitario")));
            assertEquals(Genre.ROCK, Genre.fromTags(List.of("j-rock", "japanese alternative rock")));
            assertEquals(Genre.HIP_HOP, Genre.fromTags(List.of("atl trap", "rap", "hip hop")));
        }
    }

    @Nested
    @DisplayName("when there is nothing to go on")
    class Fallbacks {

        @Test
        @DisplayName("an unrecognised tag is UNKNOWN rather than OTHER")
        void unrecognisedIsUnknown() {
            // The two are not interchangeable. OTHER means the user looked at the list and none of it
            // fitted; UNKNOWN means nobody has said. Filing a guess as OTHER would claim a decision
            // that was never made.
            assertEquals(Genre.UNKNOWN, Genre.fromTags(List.of("bardcore", "shibuya-kei")));
        }

        @Test
        @DisplayName("no tags at all is UNKNOWN, and never an exception")
        void emptyIsUnknown() {
            assertEquals(Genre.UNKNOWN, Genre.fromTags(List.of()));
            assertEquals(Genre.UNKNOWN, Genre.fromTags(null));
        }

        @Test
        @DisplayName("a null inside the list is skipped rather than thrown over")
        void nullTagsAreSkipped() {
            assertEquals(Genre.ROCK,
                    Genre.fromTags(java.util.Arrays.asList(null, "punk rock", null)));
        }

        @Test
        @DisplayName("every genre still answers for its own name, so the list cannot drift from the enum")
        void everyGenreIsReachable() {
            // Not a completeness claim about the tag vocabulary - OTHER and UNKNOWN are deliberately
            // unreachable - but the check that adding a constant and forgetting the patterns shows up.
            for (Genre genre : Genre.values()) {
                if (genre == Genre.OTHER || genre == Genre.UNKNOWN) {
                    continue;
                }
                Genre matched = Genre.fromTags(List.of(genre.displayName().toLowerCase()));
                assertNotNull(matched);
                assertEquals(genre, matched,
                        genre + " does not match its own display name, so a Spotify tag naming it "
                                + "outright would be filed as " + matched);
            }
        }
    }
}
