package com.eia.superdwarfkart.game;

import com.eia.superdwarfkart.assets.AssetKind;

/**
 * The invulnerability star: rare, placed on a beat like everything else, and seeded like everything
 * else so the same song at the same speed class always hides it in the same place.
 *
 * <p>While it is running the racer cannot be hurt, and driving <em>through</em> an obstacle breaks
 * it - which is worth more than avoiding it, so the star turns the safest few seconds of a run into
 * the greediest. Its duration is counted in <strong>beats</strong> rather than seconds
 * ({@link RunnerGame#STAR_BEATS}), so it lasts the same musical length on a slow track as on a fast
 * one and always runs out on a beat.
 */
public final class Star extends Entity {

    /**
     * @param beatTime when it reaches the racer, in seconds from the start of the track
     * @param lane     which lane it sits in
     */
    public Star(double beatTime, Lane lane) {
        super(beatTime, lane);
    }

    @Override
    public AssetKind kind() {
        return AssetKind.STAR;
    }
}
