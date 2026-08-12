package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.assets.SpriteSheet;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;

/**
 * The rating as it appears everywhere in the interface: the segmented meter, with the spinning
 * star from the game's own sprite sheet beside it.
 *
 * <p>The star turns only for a song that has actually been rated. An unrated song shows it dimmed
 * and still, which keeps the rows aligned without implying a rating of nothing is worth
 * celebrating.
 */
public class RatingDisplay extends HBox {

    /** The star strip, shared by every instance: one image for the whole interface. */
    private static final SpriteSheet STAR_SHEET =
            SpriteSheet.load("/assets/textures/Sprites/Star.png", 9);

    /** Opacity of the star when the song is unrated. */
    private static final double UNRATED_OPACITY = 0.15;

    private final RatingBar bar;
    private final SpriteView star;

    /**
     * Builds a rating display.
     *
     * @param segmentWidth  width of one meter block, in pixels
     * @param segmentHeight height of one meter block, in pixels
     * @param starSize      displayed height of the star, in pixels
     * @param phase         star animation offset, so a column of them does not spin in lockstep
     */
    public RatingDisplay(double segmentWidth, double segmentHeight, double starSize, int phase) {
        super(8);
        setAlignment(Pos.CENTER_LEFT);

        bar = new RatingBar(segmentWidth, segmentHeight);
        star = new SpriteView(STAR_SHEET, starSize, phase);

        getChildren().addAll(bar, star);
        setRating(0);
    }

    /**
     * Shows a rating on both the meter and the star.
     *
     * @param rating the rating to show, 0 to 100
     * @throws IllegalArgumentException if the rating is out of range
     */
    public final void setRating(int rating) {
        bar.setRating(rating);
        if (rating > 0) {
            star.setOpacity(1);
            star.startAnimating();
        } else {
            star.setOpacity(UNRATED_OPACITY);
            star.stopAnimating();
        }
    }

    /** @return the rating currently shown */
    public int getRating() {
        return bar.getRating();
    }

    /** Stops the star, for a display being removed from view. */
    public void stop() {
        star.stopAnimating();
    }
}
