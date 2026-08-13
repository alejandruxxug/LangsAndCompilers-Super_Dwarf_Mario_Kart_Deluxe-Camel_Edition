package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the arithmetic the companion window's layout rests on.
 *
 * <p>Building the window itself needs a JavaFX toolkit, but everything below is static and is
 * exactly where this window can go wrong without anything throwing: in a font whose glyphs are one
 * em wide, a caption that does not fit runs off the side of a card 124 pixels across and nothing
 * anywhere reports it. The smoke test measures the real window; these are the rules it is measured
 * against.
 */
@DisplayName("Companion window layout")
class MiniPlayerLayoutTest {

    @Test
    @DisplayName("the full application name is far too wide for the card, which is why none is drawn")
    void fullNameWouldOverflow() {
        double nameWidth = AppConfig.APP_NAME.length() * MiniPlayerView.NAME_SIZE;

        assertTrue(nameWidth > MiniPlayerView.CONTENT_WIDTH * 2,
                "APP_NAME is " + AppConfig.APP_NAME.length() + " characters, about "
                        + nameWidth + "px at " + MiniPlayerView.NAME_SIZE + "px per glyph, on a card "
                        + MiniPlayerView.CONTENT_WIDTH + "px wide. If this ever passes, the reason "
                        + "the companion window draws no name has gone away - check why before "
                        + "adding one.");
        assertTrue(nameWidth > AppConfig.MINI_WIDTH,
                "it does not even fit the whole window, which is wider than the card");
        assertFalse(AppConfig.APP_NAME_SHORT.contains(AppConfig.APP_NAME),
                "the short name must not be the full name");
    }

    @Test
    @DisplayName("the cartridge is sized against the record, and the wider of the two sets the window")
    void theCartridgeIsSizedAgainstTheRecord() {
        assertEquals(MiniPlayerView.DISK_SIZE * MiniPlayerView.CARD_TO_DISK_RATIO,
                MiniPlayerView.CARD_WIDTH, 1,
                "the cartridge's width is that ratio, not a second number kept in step by hand");
        assertEquals(Math.max(MiniPlayerView.DISK_SIZE, MiniPlayerView.CARD_WIDTH),
                AppConfig.MINI_WIDTH, 0.001,
                "whichever of the two is wider is what the window's width means - the ratio decides "
                        + "which, and both arrangements are legitimate");
        assertTrue(MiniPlayerView.CONTENT_WIDTH > 0, "the cartridge must leave room for the song");
    }

    @Test
    @DisplayName("the three transport keys fit across the card")
    void transportFitsTheCard() {
        double gap = 5;
        double buttonWidth = Math.floor((MiniPlayerView.CONTENT_WIDTH - 2 * gap) / 3);

        assertTrue(buttonWidth * 3 + gap * 2 <= MiniPlayerView.CONTENT_WIDTH,
                "three keys and two gaps have to fit the card, not merely nearly fit it");
        // Two glyphs plus the bevel. Below this the caption is clipped rather than the row wrapped,
        // which is invisible until somebody looks at the window.
        assertTrue(buttonWidth >= 2 * 9 + 4,
                "a key must hold its two-character caption at 9px plus its 2px bevel, got "
                        + buttonWidth + "px");
    }

    @Test
    @DisplayName("the caption limits are what actually fits the card")
    void limitsMatchTheCard() {
        assertEquals(MiniPlayerView.CONTENT_WIDTH / MiniPlayerView.TITLE_SIZE,
                MiniPlayerView.TITLE_LIMIT, 1,
                "the title limit is derived from the card, not guessed");
        assertTrue(MiniPlayerView.TITLE_LIMIT * MiniPlayerView.TITLE_SIZE
                        <= MiniPlayerView.CONTENT_WIDTH,
                "a title at the limit must fit the card it is drawn in");
        assertTrue(MiniPlayerView.ARTIST_LIMIT * MiniPlayerView.ARTIST_SIZE
                        <= MiniPlayerView.CONTENT_WIDTH,
                "an artist at the limit must fit the card it is drawn in");
        // A smaller font fits more of them, which is the only reason the two limits differ.
        assertTrue(MiniPlayerView.ARTIST_LIMIT > MiniPlayerView.TITLE_LIMIT,
                "the artist line is drawn smaller, so more of it fits");
    }

    @Test
    @DisplayName("a title that fits does not scroll, and one that does not wraps round for ever")
    void theMarqueeOnlyMovesWhenItHasTo() {
        int limit = MiniPlayerView.COMPACT_TITLE_LIMIT;

        // Nothing to see: motion carrying no information is just something moving in the corner
        // of the eye.
        assertEquals(null, MiniPlayerView.marqueeWindow("short", limit, 0));
        assertEquals(null, MiniPlayerView.marqueeWindow("x".repeat(limit), limit, 9),
                "exactly filling the strip is still fitting");
        assertEquals(null, MiniPlayerView.marqueeWindow(null, limit, 0));

        String song = "dragon-studio-thudding-heartbeat-372487";
        assertTrue(song.length() > limit);
        assertEquals(song.substring(0, limit), windowAt(song, limit, 0),
                "it starts at the title's own beginning, not part way in");

        // Every instant over ten minutes of a long song: always exactly a strip's worth, never an
        // exception. The wrap reads off a doubled copy and that is where an off-by-one would hide.
        for (double at = 0; at < 600; at += 0.1) {
            String window = windowAt(song, limit, at);
            assertEquals(limit, window.length(), "at " + at + "s");
        }

        // It really does move, and it really does come back round.
        assertTrue(!windowAt(song, limit, 0).equals(windowAt(song, limit, 2)),
                "two seconds in it should have slid along");
        double lap = (song.length() + 7) / 3d;
        assertEquals(windowAt(song, limit, 0), windowAt(song, limit, lap),
                "a full lap of title-plus-gap returns to the start");
    }

    /**
     * @param text    the title
     * @param limit   how many characters fit
     * @param seconds when to sample
     * @return the visible slice, never {@code null}
     */
    private static String windowAt(String text, int limit, double seconds) {
        String window = MiniPlayerView.marqueeWindow(text, limit, seconds);
        assertTrue(window != null, "a title longer than the strip has to scroll");
        return window;
    }

    @Test
    @DisplayName("a character budget never returns less than one glyph")
    void budgetIsAlwaysUsable() {
        assertEquals(10, MiniPlayerView.charBudget(90, 9));
        assertEquals(10, MiniPlayerView.charBudget(98, 9), "a partial glyph does not count");
        assertEquals(1, MiniPlayerView.charBudget(2, 9), "too narrow still asks for one");
        assertEquals(1, MiniPlayerView.charBudget(200, 0), "a nonsense size does not divide by zero");
    }

    @Test
    @DisplayName("the progress line fills in proportion, and a track of unknown length fills none of it")
    void progressLineFollowsThePosition() {
        double width = MiniPlayerView.CONTENT_WIDTH;

        assertEquals(0, MiniPlayerView.progressWidth(width,
                java.time.Duration.ZERO, java.time.Duration.ofMinutes(4)));
        assertEquals(Math.round(width / 2), MiniPlayerView.progressWidth(width,
                java.time.Duration.ofMinutes(2), java.time.Duration.ofMinutes(4)));
        assertEquals(width, MiniPlayerView.progressWidth(width,
                java.time.Duration.ofMinutes(4), java.time.Duration.ofMinutes(4)));

        // A variable-bitrate file with no header has no length until it has been decoded, which is
        // ordinary rather than exceptional - and dividing by it would fill the line with a
        // not-a-number that silently draws nothing at all.
        assertEquals(0, MiniPlayerView.progressWidth(width,
                java.time.Duration.ofMinutes(2), java.time.Duration.ZERO));
        assertEquals(0, MiniPlayerView.progressWidth(width, null, null));

        // Past the end, which happens between a track finishing and the next one loading.
        assertEquals(width, MiniPlayerView.progressWidth(width,
                java.time.Duration.ofMinutes(9), java.time.Duration.ofMinutes(4)));
    }

    @Test
    @DisplayName("sprites are magnified by whole numbers only, and never shrunk below their own size")
    void scaleIsAlwaysAWholeNumberAtLeastOne() {
        // The real artwork: a 32px record frame blown up to fill the window, and a 64px racer frame
        // at the share of it the kart is meant to take up. Written against sizes rather than against
        // whatever DISK_SIZE happens to be today, which is a knob.
        assertEquals(7, MiniPlayerView.integerScale(224, 32));
        assertEquals(8, MiniPlayerView.integerScale(256, 32));
        assertEquals(8, MiniPlayerView.integerScale(254, 32), "the nearest whole scale, not the one that fits");
        assertEquals(1, MiniPlayerView.integerScale(64, 64));

        // Whatever the record is set to, it still comes out at a whole scale of at least one.
        assertTrue(MiniPlayerView.integerScale(MiniPlayerView.DISK_SIZE, 32) >= 1);
        assertTrue(MiniPlayerView.integerScale(
                MiniPlayerView.DISK_SIZE * MiniPlayerView.RACER_SHARE, 64) >= 1);

        // Art larger than the space it is given is drawn at 1:1 rather than at a fraction, which
        // would interpolate it into mush - ground rule 8.
        assertEquals(1, MiniPlayerView.integerScale(64, 128));
        assertEquals(1, MiniPlayerView.integerScale(0, 32));
        assertEquals(1, MiniPlayerView.integerScale(128, 0), "a sheet with no height does not divide by zero");

        for (int frameSize : new int[]{8, 16, 32, 48, 64, 96}) {
            int scale = MiniPlayerView.integerScale(128, frameSize);
            assertTrue(scale >= 1, "a magnification below 1 would shrink hand-drawn pixel art");
        }
    }
}
