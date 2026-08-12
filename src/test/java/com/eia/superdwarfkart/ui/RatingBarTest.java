package com.eia.superdwarfkart.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the rating-to-blocks mapping only. Building the node itself needs a JavaFX toolkit,
 * but the arithmetic below is where the off-by-one lives, and it is static.
 */
@DisplayName("RatingBar mapping")
class RatingBarTest {

    @Test
    @DisplayName("an unrated song lights no blocks")
    void zeroLightsNothing() {
        assertEquals(0, RatingBar.litSegments(0));
    }

    @Test
    @DisplayName("any rating above zero lights at least one block")
    void lowRatingsStillShow() {
        // Rounding to nearest would leave 1 to 4 showing an empty meter, which is
        // indistinguishable from unrated.
        assertEquals(1, RatingBar.litSegments(1));
        assertEquals(1, RatingBar.litSegments(4));
        assertEquals(1, RatingBar.litSegments(10));
    }

    @Test
    @DisplayName("each block is worth ten points")
    void tenPointsPerBlock() {
        assertEquals(2, RatingBar.litSegments(11));
        assertEquals(2, RatingBar.litSegments(20));
        assertEquals(5, RatingBar.litSegments(45));
        assertEquals(6, RatingBar.litSegments(60));
        assertEquals(10, RatingBar.litSegments(97));
    }

    @Test
    @DisplayName("a full rating lights every block and never more")
    void fullRating() {
        assertEquals(RatingBar.SEGMENTS, RatingBar.litSegments(100));
    }

    @Test
    @DisplayName("the colour band changes with the rating")
    void colourBands() {
        assertEquals("low", RatingBar.tierStyleClass(0));
        assertEquals("low", RatingBar.tierStyleClass(39));
        assertEquals("mid", RatingBar.tierStyleClass(40));
        assertEquals("mid", RatingBar.tierStyleClass(69));
        assertEquals("high", RatingBar.tierStyleClass(70));
        assertEquals("high", RatingBar.tierStyleClass(100));
    }
}
