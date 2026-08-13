package com.eia.superdwarfkart.ui;

/**
 * What the middle of the fullscreen window is showing.
 *
 * <p>Until now the centre held the library, with the runner swapped in by {@code F6}. The side rail
 * formalises that: the library becomes one destination among several rather than a permanent
 * fixture, and the road stays what it was - not a destination but an alternative to whichever one
 * is selected, so leaving the race returns to wherever the user was.
 *
 * <p>The labels are short because they have to be. In a fixed-width pixel font a caption is about
 * one em per character, so the rail's width is set by the longest of these and nothing here may
 * grow without checking it against {@code SideRail.RAIL_WIDTH}.
 */
public enum Destination {

    /** The song library: search, filters, ratings, add and edit. */
    LIBRARY("LIBRARY", "The whole library"),

    /** The library, filtered to the songs marked as favourites. */
    FAVORITES("FAVORITES", "Songs marked as favourites"),

    /** What has been played, most recent first, and the totals over it. */
    HISTORY("HISTORY", "Recently played, and the statistics"),

    /** Who is driving. Drawn from frame 3 of each racer's own sheet. */
    RACERS("RACERS", "Choose a racer"),

    /** The look: a palette applied to every window at once. */
    MOODS("MOODS", "Change the look"),

    /** Speed class, storage locations and the keyboard reference. */
    SETTINGS("SETTINGS", "Preferences and shortcuts");

    private final String label;
    private final String description;

    Destination(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** @return the caption shown on the rail */
    public String label() {
        return label;
    }

    /** @return the tooltip shown on the rail */
    public String description() {
        return description;
    }

    /** @return the destination shown when the application opens */
    public static Destination defaultDestination() {
        return LIBRARY;
    }

    @Override
    public String toString() {
        return label;
    }
}
