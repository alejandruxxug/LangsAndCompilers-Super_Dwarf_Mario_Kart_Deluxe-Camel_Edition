package com.eia.superdwarfkart.mood;

/**
 * One thing wrong with a palette, named in plain English.
 *
 * <p>The message is written for somebody who has just moved a colour picker and does not know what
 * a protected role is, so it names <em>what stops working</em> rather than what threshold was
 * missed. "HIGHLIGHT is too close to OUTLINE" is a fact; "the tree's traversal will be hard to
 * see" is the reason anybody would care.
 *
 * @param first    one of the two roles involved
 * @param second   the other
 * @param measured what was measured - a contrast ratio or a CIE76 distance
 * @param required what it had to be
 * @param message  what stops working, for the validator bar
 */
public record MoodIssue(PaletteRole first, PaletteRole second, double measured, double required,
        String message) {

    /**
     * @return the message with the measurement appended, for a log line or a tooltip
     */
    public String detail() {
        return message + " (" + round(measured) + " against " + round(required) + " needed)";
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10d;
    }

    @Override
    public String toString() {
        return detail();
    }
}
