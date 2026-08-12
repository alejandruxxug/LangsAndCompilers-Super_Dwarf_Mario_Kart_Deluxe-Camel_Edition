package com.eia.superdwarfkart.assets;

import com.eia.superdwarfkart.model.Racer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Covers the filename classification the whole asset layer rests on.
 *
 * <p>Artwork arrives named by whoever drew it, so these cases are the contract: whatever the
 * files end up being called, the application still finds the star.
 */
@DisplayName("Asset classification")
class AssetKindTest {

    @Nested
    @DisplayName("the artwork already in the repository")
    class RealFiles {

        @Test
        @DisplayName("every sheet in the project is recognised")
        void projectArtIsRecognised() {
            assertEquals(AssetKind.DISK, AssetKind.classify("Disk-Sheet.png"));
            assertEquals(AssetKind.STAR, AssetKind.classify("Star.png"));
            assertEquals(AssetKind.COIN, AssetKind.classify("Coin.png"));
            assertEquals(AssetKind.RACER, AssetKind.classify("Mario.png"));
            assertEquals(AssetKind.RACER, AssetKind.classify("Bowser.png"));
        }

        @Test
        @DisplayName("a racer is found by name, not by a keyword its filename does not contain")
        void everyRacerIsRecognisedByName() {
            // Mario.png contains none of "char", "player", "kart" or "racer". Without matching
            // the racer names themselves, all five sheets would classify as UNKNOWN.
            for (Racer racer : Racer.values()) {
                assertEquals(AssetKind.RACER, AssetKind.classify(racer.displayName() + ".png"),
                        racer.displayName() + " should be recognised as racer artwork");
            }
        }

        @Test
        @DisplayName("a full path classifies the same as a bare filename")
        void pathsAreReducedToTheFilename() {
            assertEquals(AssetKind.STAR, AssetKind.classify("textures/Sprites/Star.png"));
            assertEquals(AssetKind.STAR, AssetKind.classify("textures\\Sprites\\Star.png"));
        }
    }

    @Nested
    @DisplayName("keyword matching")
    class Keywords {

        @Test
        @DisplayName("case does not matter")
        void caseInsensitive() {
            assertEquals(AssetKind.COIN, AssetKind.classify("COIN.PNG"));
            assertEquals(AssetKind.COIN, AssetKind.classify("coin.png"));
            assertEquals(AssetKind.COIN, AssetKind.classify("CoIn.png"));
        }

        @Test
        @DisplayName("a keyword is found anywhere inside the name")
        void keywordAnywhere() {
            assertEquals(AssetKind.EXPLOSION, AssetKind.classify("kart_explosion_2f.png"));
            assertEquals(AssetKind.OBSTACLE, AssetKind.classify("final-bump-v3.png"));
            assertEquals(AssetKind.DISK, AssetKind.classify("vinyl-disc-spin.png"));
        }

        @Test
        @DisplayName("the Spanish filenames the artist uses are matched too")
        void spanishPatterns() {
            // The one place Spanish is allowed: these are patterns matched against, never
            // identifiers, because the artist names their exports in Spanish.
            assertEquals(AssetKind.STAR, AssetKind.classify("estrella.png"));
            assertEquals(AssetKind.COIN, AssetKind.classify("moneda_anim.png"));
            assertEquals(AssetKind.BACKGROUND, AssetKind.classify("fondo-1.png"));
            assertEquals(AssetKind.RACER, AssetKind.classify("personaje3.png"));
        }

        @Test
        @DisplayName("a two-letter keyword only matches as a whole word")
        void shortKeywordsMustStandAlone() {
            // "bg" as a substring would claim anything with those letters adjacent in it.
            assertEquals(AssetKind.BACKGROUND, AssetKind.classify("bg.png"));
            assertEquals(AssetKind.BACKGROUND, AssetKind.classify("bg_forest.png"));
            assertEquals(AssetKind.BACKGROUND, AssetKind.classify("level-1-bg.png"));
            assertNotEquals(AssetKind.BACKGROUND, AssetKind.classify("bgm-theme.png"));
        }

        @Test
        @DisplayName("the specific keyword wins over the thing it is qualifying")
        void specificKeywordsWin() {
            // A filename says what a sprite is and then what it belongs to. "kart", "char" and
            // "player" show up as qualifiers on artwork that is not a racer sprite, so they are
            // matched last: kart_explosion.png is an explosion, racer-select.png is a menu.
            assertEquals(AssetKind.EXPLOSION, AssetKind.classify("kart_explosion.png"));
            assertEquals(AssetKind.SELECT, AssetKind.classify("racer-select.png"));
            assertEquals(AssetKind.SELECT, AssetKind.classify("mario_select_portrait.png"));
            assertEquals(AssetKind.COIN, AssetKind.classify("kart-coin-pickup.png"));
            assertEquals(AssetKind.BACKGROUND, AssetKind.classify("player-bg.png"));
        }

        @Test
        @DisplayName("anything unrecognised is UNKNOWN rather than an error")
        void unknownIsNotAFailure() {
            assertEquals(AssetKind.UNKNOWN, AssetKind.classify("untitled-4.png"));
            assertEquals(AssetKind.UNKNOWN, AssetKind.classify(""));
            assertEquals(AssetKind.UNKNOWN, AssetKind.classify(null));
        }
    }

    @Nested
    @DisplayName("base name extraction")
    class BaseNames {

        @Test
        @DisplayName("the directory and extension are stripped")
        void stripsPathAndExtension() {
            assertEquals("Star", AssetKind.baseName("textures/Sprites/Star.png"));
            assertEquals("Star", AssetKind.baseName("Star.png"));
            assertEquals("Star", AssetKind.baseName("Star"));
        }

        @Test
        @DisplayName("only the last extension is dropped")
        void keepsInnerDots() {
            assertEquals("star.v2", AssetKind.baseName("star.v2.png"));
        }

        @Test
        @DisplayName("a leading dot is part of the name, not an extension")
        void dotfile() {
            assertEquals(".gitkeep", AssetKind.baseName(".gitkeep"));
        }
    }
}
