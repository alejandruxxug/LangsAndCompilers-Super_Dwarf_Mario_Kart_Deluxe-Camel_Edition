package com.eia.superdwarfkart.mood;

/**
 * The sixteen colour roles every drawn colour in the application resolves through.
 *
 * <p>A GBA tile addresses one bank of sixteen colours, so a mood holds exactly sixteen - no extra
 * slots and no per-view exceptions. Nothing anywhere names a colour directly: it names the
 * <em>role</em> the colour plays, and the active {@link Palette} decides what that looks like.
 * That is what lets the mood system reskin the whole application later without touching a single
 * drawing call.
 *
 * <p>The declaration order is part of the format. An imported {@code .gpl} or {@code .hex}
 * palette assigns its first sixteen entries to these roles in this order, so reordering the
 * constants would silently rewrite every previously imported palette.
 *
 * <p><strong>Four roles are protected.</strong> {@link #TEXT_PRIMARY}, {@link #POSITIVE},
 * {@link #NEGATIVE} and {@link #HIGHLIGHT} carry meaning rather than decoration: a palette that
 * makes coins and obstacles look alike, or that flattens the traversal highlight into the
 * ordinary outline, throws nothing and looks fine in a screenshot. It fails live, in front of
 * the room. {@link MoodValidator} enforces their separation on every load and every edit, and
 * renders a corrected substitute rather than the user's value when one of them fails.
 */
public enum PaletteRole {

    /** Stage base fill and the fullscreen backdrop. */
    BACKGROUND("Background"),

    /** Alternating table rows and panel bands. */
    BACKGROUND_ALT("Background alt"),

    /** Cards, panels, the library table. */
    SURFACE("Surface"),

    /** Hover, selection, popups, the active tab. */
    SURFACE_RAISED("Surface raised"),

    /** Borders, dividers, canvas strokes, lane lines. */
    OUTLINE("Outline"),

    /** Body text. <strong>Protected:</strong> must stay readable against background and surface. */
    TEXT_PRIMARY("Text primary"),

    /** Secondary labels and the complexity table body. */
    TEXT_DIM("Text dim"),

    /** Mode selector, progress fill, buttons. */
    PRIMARY("Primary"),

    /** Disabled and inactive variants, such as previous-disabled in arrival order. */
    PRIMARY_DIM("Primary dim"),

    /** Focus rings, the kart marker on the circuit, pole position on the grid. */
    ACCENT("Accent"),

    /** Bottom of the level meter gradient. */
    METER_LOW("Meter low"),

    /** Top of the level meter gradient, and the peak dot. */
    METER_HIGH("Meter high"),

    /** Coins, score gains, the course-ready badge. <strong>Protected.</strong> */
    POSITIVE("Positive"),

    /** Obstacles, errors. <strong>Protected:</strong> must not resemble {@link #POSITIVE}. */
    NEGATIVE("Negative"),

    /**
     * The animating traversal edge and the step-through cursor.
     * <strong>Protected:</strong> must not resemble {@link #OUTLINE}, or the walk the tree view
     * exists to show becomes invisible from the back of the room.
     */
    HIGHLIGHT("Highlight"),

    /** Drop shadows, road edge falloff, vignette core. */
    SHADOW("Shadow");

    /** Number of colours in a palette. Fixed by the 4bpp GBA tile format, not by preference. */
    public static final int COUNT = 16;

    private final String displayName;

    PaletteRole(String displayName) {
        this.displayName = displayName;
    }

    /** @return human-readable role name, shown under its swatch in the customizer */
    public String displayName() {
        return displayName;
    }

    /**
     * Reports whether this role carries meaning that a palette must not destroy.
     *
     * @return {@code true} for the four roles the validator checks for separation
     */
    public boolean isProtected() {
        return this == TEXT_PRIMARY || this == POSITIVE || this == NEGATIVE || this == HIGHLIGHT;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
