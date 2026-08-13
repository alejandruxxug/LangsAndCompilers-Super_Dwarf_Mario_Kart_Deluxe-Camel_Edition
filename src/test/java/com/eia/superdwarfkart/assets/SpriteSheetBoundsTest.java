package com.eia.superdwarfkart.assets;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where a sheet reports its ink to be.
 *
 * <p>The companion window stands the kart on the record by these numbers, so an off-by-one here is
 * a sprite hovering a pixel above the surface - visible, and impossible to attribute to this
 * method by looking at the window. The placeholder is used as the subject because it needs no
 * artwork on disk and its shape is known exactly: a magenta square filling its whole frame.
 */
@DisplayName("Sprite sheet opaque bounds")
class SpriteSheetBoundsTest {

    @Test
    @DisplayName("a frame that is opaque throughout reports its whole self, edges included")
    void fullyOpaqueFrameReportsEverything() {
        SpriteSheet sheet = SpriteSheet.placeholder();

        Rectangle2D bounds = sheet.opaqueBounds(0);

        assertEquals(0, bounds.getMinX());
        assertEquals(0, bounds.getMinY());
        // The width is a count of columns, not a difference between them: a frame whose ink runs
        // from column 0 to column 31 is 32 wide, and the version of this that returns 31 leaves
        // every sprite placed by it half a pixel out.
        assertEquals(sheet.frameWidth(), bounds.getWidth(),
                "the last opaque column counts, so the width is max - min + 1");
        assertEquals(sheet.frameHeight(), bounds.getHeight());
    }

    @Test
    @DisplayName("the bounds never leave the frame, whichever frame is asked for")
    void boundsStayInsideTheFrame() {
        SpriteSheet sheet = SpriteSheet.placeholder();

        // Deliberately out of range in both directions: callers pass free-running counters here,
        // exactly as they do to viewport(), so this has to wrap rather than throw.
        for (int frame : new int[]{-7, -1, 0, 1, 99}) {
            Rectangle2D bounds = sheet.opaqueBounds(frame);
            assertTrue(bounds.getMinX() >= 0 && bounds.getMinY() >= 0,
                    "frame " + frame + " reported ink outside the frame");
            assertTrue(bounds.getMinX() + bounds.getWidth() <= sheet.frameWidth(),
                    "frame " + frame + " reported ink past the right edge");
            assertTrue(bounds.getMinY() + bounds.getHeight() <= sheet.frameHeight(),
                    "frame " + frame + " reported ink past the bottom edge");
            assertTrue(bounds.getWidth() > 0 && bounds.getHeight() > 0,
                    "an empty rectangle would collapse whatever is anchored to it");
        }
    }

    @Test
    @DisplayName("artwork with no dark panel on it reports none, rather than a box to fill")
    void brightArtworkHasNoPanel() {
        // The placeholder is magenta on near-black, and the near-black is a cross rather than a
        // panel - so nothing here is somewhere to put content.
        SpriteSheet sheet = SpriteSheet.placeholder();

        assertTrue(sheet.darkRegion(0).isEmpty(),
                "a scattering of dark pixels is not a panel; a view told otherwise would lay its "
                        + "contents over the artwork instead of into it");
    }

    @Test
    @DisplayName("the answer is remembered rather than measured again")
    void boundsAreCached() {
        SpriteSheet sheet = SpriteSheet.placeholder();

        // Same object back, not merely an equal one: this is a scan of every pixel in the frame and
        // the drawing code asks for it while laying itself out.
        assertTrue(sheet.opaqueBounds(0) == sheet.opaqueBounds(0),
                "opaqueBounds must not rescan the image on every call");
    }
}
