package com.eia.superdwarfkart.assets;

/**
 * What each frame of a racer sheet actually shows.
 *
 * <p>Every racer sheet is 256x64: four 64x64 frames, and they are not four steps of one
 * animation. The first two are the driving cycle and only those two may be looped; the third is
 * the kart seen from behind and the fourth is a static icon. Playing all four in sequence - which
 * is what a generic sprite animation does - makes the kart flicker between a side view, a rear
 * view and a menu icon several times a second.
 *
 * <p>This lives in {@code assets/} for the same reason filenames do: it is knowledge about the
 * artwork, and the views should ask for "the driving frame" rather than know that it is index 0
 * or 1. If the art is ever redrawn with a longer cycle, this is the only file that changes.
 */
public enum RacerFrame {

    /** First frame of the two-frame driving cycle, seen from the side. */
    DRIVE_A(0),

    /** Second frame of the driving cycle. */
    DRIVE_B(1),

    /** The kart from behind, for driving away from the viewer or across a map seen from above. */
    BACK(2),

    /** Static portrait, for menus and labels. Never animated. */
    ICON(3);

    /** How many frames a racer sheet holds. */
    public static final int COUNT = 4;

    /** Cycles per second of the driving loop. Two frames at this rate read as wheels turning. */
    public static final double DRIVE_FPS = 8;

    private final int index;

    RacerFrame(int index) {
        this.index = index;
    }

    /** @return this frame's index into the sheet */
    public int index() {
        return index;
    }

    /**
     * Returns the driving frame to show at a moment in time.
     *
     * <p>Alternates between {@link #DRIVE_A} and {@link #DRIVE_B} only. The rear view and the
     * icon are never part of the cycle.
     *
     * @param seconds elapsed time; any steadily increasing clock will do
     * @return the frame index to draw
     */
    public static int driving(double seconds) {
        return driving(seconds, DRIVE_FPS);
    }

    /**
     * Returns the driving frame to show at a moment in time, at an explicit rate.
     *
     * @param seconds         elapsed time
     * @param framesPerSecond how fast the two frames alternate
     * @return the frame index to draw
     */
    public static int driving(double seconds, double framesPerSecond) {
        if (framesPerSecond <= 0 || seconds < 0) {
            return DRIVE_A.index;
        }
        return Math.floorMod((int) (seconds * framesPerSecond), 2) == 0
                ? DRIVE_A.index
                : DRIVE_B.index;
    }
}
