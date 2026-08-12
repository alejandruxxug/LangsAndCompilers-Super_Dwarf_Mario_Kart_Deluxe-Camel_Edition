package com.eia.superdwarfkart.ui;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the centre-crop arithmetic used to fit album art into the square cover frame.
 */
@DisplayName("Cover crop")
class CoverCropTest {

    @Test
    @DisplayName("a square image is not cropped at all")
    void squareImageUntouched() {
        Rectangle2D crop = LibraryView.centeredSquare(600, 600);

        assertEquals(0, crop.getMinX());
        assertEquals(0, crop.getMinY());
        assertEquals(600, crop.getWidth());
        assertEquals(600, crop.getHeight());
    }

    @Test
    @DisplayName("a wide image loses equal slices from the left and right")
    void wideImageCroppedHorizontally() {
        Rectangle2D crop = LibraryView.centeredSquare(900, 400);

        assertEquals(250, crop.getMinX(), "(900 - 400) / 2");
        assertEquals(0, crop.getMinY());
        assertEquals(400, crop.getWidth());
        assertEquals(400, crop.getHeight());
    }

    @Test
    @DisplayName("a tall image loses equal slices from the top and bottom")
    void tallImageCroppedVertically() {
        Rectangle2D crop = LibraryView.centeredSquare(400, 900);

        assertEquals(0, crop.getMinX());
        assertEquals(250, crop.getMinY(), "(900 - 400) / 2");
        assertEquals(400, crop.getWidth());
        assertEquals(400, crop.getHeight());
    }

    @Test
    @DisplayName("the crop is always square")
    void cropIsAlwaysSquare() {
        for (int[] size : new int[][]{{1, 1}, {3000, 17}, {17, 3000}, {1920, 1080}, {640, 641}}) {
            Rectangle2D crop = LibraryView.centeredSquare(size[0], size[1]);
            assertEquals(crop.getWidth(), crop.getHeight(),
                    "crop of " + size[0] + "x" + size[1] + " must be square");
            assertEquals(Math.min(size[0], size[1]), crop.getWidth());
        }
    }

    @Test
    @DisplayName("the crop stays inside the image")
    void cropStaysInBounds() {
        Rectangle2D crop = LibraryView.centeredSquare(1920, 1080);

        assertEquals(true, crop.getMinX() >= 0 && crop.getMinY() >= 0);
        assertEquals(true, crop.getMaxX() <= 1920);
        assertEquals(true, crop.getMaxY() <= 1080);
    }
}
