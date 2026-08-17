package com.eia.superdwarfkart.mood;

/**
 * Which side of the interface an overlay layer is drawn on.
 *
 * <p>A layer is either wallpaper or gauze. Behind the content it can be anything it likes, because
 * the panels, the table and the road are drawn on top of it and nothing it does can make them
 * harder to read. In front of the content it is looking through a filter at the two things this
 * project is graded on, so it is capped.
 */
public enum ZBand {

    /** Wallpaper: drawn under everything, so it may be as strong as it likes. */
    BEHIND_CONTENT("Behind", 1.0),

    /**
     * Gauze: drawn over the interface, and <strong>hard-capped at 0.35</strong>.
     *
     * <p>The cap is not a taste setting. A layer in front of the content sits over the runner's
     * coins and obstacles and over the tree's traversal highlight, and those are the four protected
     * roles' whole job. At full strength a user-built mood could bury the game and the structure
     * visualiser behind its own wallpaper, throw nothing, and photograph perfectly - the failure
     * arrives live, on stage, on the two views the project exists to show.
     */
    ABOVE_CONTENT("Above", 0.35);

    private final String displayName;
    private final double maxOpacity;

    ZBand(String displayName, double maxOpacity) {
        this.displayName = displayName;
        this.maxOpacity = maxOpacity;
    }

    /** @return the caption shown in the customizer's layer list */
    public String displayName() {
        return displayName;
    }

    /**
     * The strongest a layer in this band may be drawn.
     *
     * @return the opacity ceiling, 0 to 1
     */
    public double maxOpacity() {
        return maxOpacity;
    }

    /**
     * Reads a stored band name, tolerating anything a later version might have written.
     *
     * @param name the stored name; {@code null} or unknown yields {@link #BEHIND_CONTENT}
     * @return the band
     */
    public static ZBand byName(String name) {
        if (name != null) {
            for (ZBand band : values()) {
                if (band.name().equalsIgnoreCase(name.strip())) {
                    return band;
                }
            }
        }
        return BEHIND_CONTENT;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
