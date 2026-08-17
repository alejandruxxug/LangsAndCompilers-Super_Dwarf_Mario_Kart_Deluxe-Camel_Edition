package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lets a mood respond to the music, inside fences that are not negotiable.
 *
 * <p>Both feeds this needs already exist by M7 - the level meters publish RMS per channel and the
 * beatmap knows where the strong beats are - so the feature costs nothing to acquire. What it costs
 * is care, because a fullscreen overlay flashing at eight hertz in a darkened classroom is a genuine
 * problem rather than a style question.
 *
 * <h2>The fences</h2>
 *
 * <ul>
 *   <li><strong>Brightness and alpha only, never hue.</strong> A mood is a set of hues the user
 *       chose; a reactivity that moved them would be a different mood every bar.</li>
 *   <li><strong>The four protected roles keep their separations at every point in the
 *       modulation.</strong> Not checked at the ends and hoped for in between - {@link #safeLift}
 *       finds the largest lift that {@link MoodValidator} still passes and the modulation never
 *       exceeds it. Computed once when the mood is installed, not per frame.</li>
 *   <li><strong>Visible change is capped at {@value #MAX_UPDATE_HZ} Hz</strong> regardless of tempo.
 *       A 200 BPM track is 3.3 beats a second and would otherwise drive this past the cap on its
 *       own.</li>
 *   <li>A <strong>"Reduce motion"</strong> switch in Settings turns the whole thing off, along with
 *       every layer's scrolling and the runner's own beat effects.</li>
 * </ul>
 *
 * <h2>Why the palette is modulated and the stylesheet is not</h2>
 *
 * <p>The canvases read {@link Palette#active()} on every repaint, so installing a modulated palette
 * reaches the road, the meters, the tree and the overlays for free. The controls read a generated
 * stylesheet, and regenerating that means a full CSS pass over the whole scene graph - at three
 * times a second, on a software renderer, on the one window that also has to run a game. So
 * reactivity reaches what is drawn and leaves the buttons alone, which is also the right answer
 * aesthetically: a table whose headings pulse with the music is not a mood, it is a fault.
 */
public final class MoodReactivity {

    /**
     * The hard cap on how often the modulation may visibly change, in hertz.
     *
     * <p>Three, and it is a health figure rather than a performance one.
     */
    public static final double MAX_UPDATE_HZ = 3;

    /** Largest brightness lift ever applied, before the per-palette clamp narrows it further. */
    public static final double MAX_LIFT = 0.35;

    /** How much of the energy comes from the beat rather than from the level. */
    private static final double BEAT_SHARE = 0.6;

    /**
     * How fast the energy falls when the music quietens.
     *
     * <p>Per update rather than per frame, so it is the same at any framerate - and because the
     * updates are capped at {@value #MAX_UPDATE_HZ} Hz, three of these is about a second.
     */
    private static final double DECAY = 0.55;

    private double energy;
    private double lastUpdate = Double.NEGATIVE_INFINITY;

    /**
     * Offers a new reading, which is taken only if the rate cap allows it.
     *
     * <p>Callers hand this the clock every frame and let it decide; a caller that tried to do the
     * rate limiting itself would be a second place the cap lived.
     *
     * @param seconds   the current time on the playback clock
     * @param rms       the louder channel's RMS, 0 to 1
     * @param beatPulse how recently a strong beat landed, 1 at the strike and 0 by the next one
     * @return whether the energy moved, so a caller can skip work when it did not
     */
    public boolean update(double seconds, double rms, double beatPulse) {
        if (seconds < lastUpdate) {
            // A seek backwards, or a new track. Start again rather than hold a stale reading.
            reset();
        }
        if (seconds - lastUpdate < 1 / MAX_UPDATE_HZ) {
            return false;
        }
        lastUpdate = seconds;

        double level = Math.clamp(rms, 0d, 1d);
        double target = Math.clamp(
                (1 - BEAT_SHARE) * level + BEAT_SHARE * Math.clamp(beatPulse, 0d, 1d), 0d, 1d);
        // Rises immediately and falls slowly: the music arriving is the event, and a decay that
        // matched the attack would make the whole thing strobe at exactly the rate the cap exists
        // to prevent.
        energy = target > energy ? target : energy * DECAY + target * (1 - DECAY);
        return true;
    }

    /** @return the current excitement, 0 to 1 */
    public double energy() {
        return energy;
    }

    /** Forgets everything, for a track change or a seek. */
    public void reset() {
        energy = 0;
        lastUpdate = Double.NEGATIVE_INFINITY;
    }

    /**
     * The largest brightness lift this palette can take while every protected separation still
     * holds.
     *
     * <p>This is the "clamp so validator thresholds hold at every point in the modulation" rule,
     * done by measurement rather than by assertion. Lifting {@link PaletteRole#HIGHLIGHT} moves it
     * through the colour space, and in a palette whose {@link PaletteRole#OUTLINE} happens to be
     * bright, that movement is <em>towards</em> the very role it has to stay away from. Searching
     * downwards from {@link #MAX_LIFT} finds where that starts and stops short of it.
     *
     * <p>Called once when a mood is installed. The search is sixteen validations of a
     * sixteen-colour palette, which is nothing once and would be absurd per frame.
     *
     * @param base the mood's own palette; must not be {@code null}
     * @return the safe lift, 0 when no lift at all is safe
     */
    public static double safeLift(Palette base) {
        for (double lift = MAX_LIFT; lift > 0; lift -= 0.05) {
            if (MoodValidator.isValid(modulate(base, lift, 1))) {
                return lift;
            }
        }
        return 0;
    }

    /**
     * Returns the palette as it looks at a given excitement.
     *
     * <p>Only {@link PaletteRole#ACCENT} and {@link PaletteRole#HIGHLIGHT} move, and only in
     * brightness. Those two are the interface's own "look here" colours - the focus ring, the kart
     * marker, the traversal edge - so lifting them on the beat reads as the application paying
     * attention, where lifting a surface would read as the lights flickering.
     *
     * @param base    the mood's own palette; must not be {@code null}
     * @param lift    the ceiling from {@link #safeLift}
     * @param energy  the current excitement, 0 to 1
     * @return the modulated palette, or {@code base} itself when nothing would move
     */
    public static Palette modulate(Palette base, double lift, double energy) {
        double amount = Math.clamp(lift, 0d, MAX_LIFT) * Math.clamp(energy, 0d, 1d);
        if (amount <= 0) {
            return base;
        }
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            Color color = base.color(role);
            if (role == PaletteRole.ACCENT || role == PaletteRole.HIGHLIGHT) {
                color = ColorMath.shiftLightness(color, amount);
            }
            colors.put(role, color);
        }
        return new Palette(base.name(), colors);
    }
}
