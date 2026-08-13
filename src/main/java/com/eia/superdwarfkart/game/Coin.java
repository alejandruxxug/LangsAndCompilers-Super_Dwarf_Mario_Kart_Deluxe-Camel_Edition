package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.assets.AssetKind;

/**
 * A collectible coin: the common entity, and the one the rank is measured against.
 *
 * <p>Coins are what make a course worth driving twice. {@link Rank} is the fraction of a course's
 * coins that were actually collected, so the score is a statement about <em>this</em> course rather
 * than about how long the song was - a four-minute track and a thirty-second one are ranked on the
 * same scale.
 */
public final class Coin extends Entity {

    /**
     * @param beatTime when it reaches the racer, in seconds from the start of the track
     * @param lane     which lane it sits in
     */
    public Coin(double beatTime, Lane lane) {
        super(beatTime, lane);
    }

    @Override
    public AssetKind kind() {
        return AssetKind.COIN;
    }
}
