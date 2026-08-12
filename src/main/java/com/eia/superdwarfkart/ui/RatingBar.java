package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.model.Song;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * A segmented 0 to 100 rating meter, drawn in the arcade idiom: a sunken frame holding ten
 * chunky blocks that light up from the left.
 *
 * <p>The colour climbs with the value the way a health bar does - red while barely liked, yellow
 * through the middle, green once it is a favourite - so a rating is readable from across the room
 * without reading the number.
 *
 * <p>Purely a display. Editing happens through the slider in the details panel, and the model
 * remains the only thing that enforces the 0 to 100 range.
 */
public class RatingBar extends HBox {

    /** How many blocks make up the meter; each one is worth ten rating points. */
    public static final int SEGMENTS = 10;

    /** At or above this rating the meter is green: a song the user really likes. */
    private static final int HIGH_THRESHOLD = 70;

    /** At or above this rating the meter is yellow. Below it, red. */
    private static final int MID_THRESHOLD = 40;

    private final Region[] segments = new Region[SEGMENTS];

    private int rating;

    /** Last painted state, so an update that changes nothing visible does no work at all. */
    private int paintedLit = -1;
    private String paintedTier = "";

    /**
     * Builds a meter with blocks of the given size.
     *
     * @param segmentWidth  width of one block, in pixels
     * @param segmentHeight height of one block, in pixels
     */
    public RatingBar(double segmentWidth, double segmentHeight) {
        setSpacing(2);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("rating-bar");

        for (int i = 0; i < SEGMENTS; i++) {
            Region segment = new Region();
            segment.getStyleClass().add("segment");
            segment.setMinSize(segmentWidth, segmentHeight);
            segment.setPrefSize(segmentWidth, segmentHeight);
            segment.setMaxSize(segmentWidth, segmentHeight);
            segments[i] = segment;
            getChildren().add(segment);
        }
        setRating(0);
    }

    /**
     * Lights up the blocks for a rating.
     *
     * <p>Any rating above zero lights at least one block, so a rated song never looks unrated.
     *
     * @param rating the rating to show
     * @throws IllegalArgumentException if the rating is outside 0 to 100
     */
    public final void setRating(int rating) {
        if (rating < Song.MIN_RATING || rating > Song.MAX_RATING) {
            throw new IllegalArgumentException(
                    "Rating must be between " + Song.MIN_RATING + " and " + Song.MAX_RATING
                            + ", got " + rating);
        }
        this.rating = rating;

        int lit = litSegments(rating);
        String tier = tierStyleClass(rating);

        // Rewriting style classes forces a CSS pass over every block. Most rating changes -
        // nudging 96 to 97, or repainting a table cell onto the same value - light the same
        // blocks in the same colour, so skip the work entirely when nothing would look different.
        if (lit == paintedLit && tier.equals(paintedTier)) {
            return;
        }
        paintedLit = lit;
        paintedTier = tier;

        for (int i = 0; i < SEGMENTS; i++) {
            Region segment = segments[i];
            segment.getStyleClass().setAll("segment");
            if (i < lit) {
                segment.getStyleClass().addAll("filled", tier);
            }
        }
    }

    /** @return the rating currently shown */
    public int getRating() {
        return rating;
    }

    /**
     * Works out how many blocks to light.
     *
     * <p>Rounds upward so that a rating of 1 lights a block: rounding to nearest would show an
     * empty meter for anything under five, which is indistinguishable from unrated.
     *
     * @param rating the rating to show
     * @return the number of lit blocks, from 0 to {@link #SEGMENTS}
     */
    static int litSegments(int rating) {
        if (rating <= 0) {
            return 0;
        }
        return Math.min(SEGMENTS, (int) Math.ceil(rating / (double) (Song.MAX_RATING / SEGMENTS)));
    }

    /**
     * Picks the colour band for a rating.
     *
     * @param rating the rating to show
     * @return the style class carrying that band's colour
     */
    static String tierStyleClass(int rating) {
        if (rating >= HIGH_THRESHOLD) {
            return "high";
        }
        if (rating >= MID_THRESHOLD) {
            return "mid";
        }
        return "low";
    }
}
