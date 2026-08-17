package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sixteen colours, one per {@link PaletteRole}.
 *
 * <p>This is the seam ground rule 7 exists for. No drawing code anywhere names a colour; it names
 * a role and asks the palette. Every {@code gc.setFill(...)} in the visualizer, the meters, the
 * road and the tree resolves through here, so the mood system can later swap the whole look by
 * replacing one object rather than by a find-and-replace across a finished interface.
 *
 * <p>A palette is immutable. Editing one in the customizer produces a new palette and installs
 * it, which keeps every reader safe without any locking.
 *
 * <p>{@link #active()} is a static holder rather than an injected dependency, matching the way
 * {@code Theme} and {@code AssetRegistry} are already reached in this project. The mood system
 * sets it from the mood stored in {@code AppState}, and the drawing code did not change when it
 * arrived - which was the whole prediction ground rule 7 was made on.
 */
public final class Palette {

    private static volatile Palette active = defaultPalette();

    private final String name;
    private final Map<PaletteRole, Color> colors;

    /**
     * Builds a palette from a complete set of roles.
     *
     * @param name   human-readable palette name
     * @param colors every role mapped to a colour; must contain all sixteen
     * @throws IllegalArgumentException if a role is missing
     */
    public Palette(String name, Map<PaletteRole, Color> colors) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(colors, "colors must not be null");

        Map<PaletteRole, Color> snapped = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            Color color = colors.get(role);
            if (color == null) {
                throw new IllegalArgumentException(
                        "Palette \"" + name + "\" is missing a colour for " + role.name()
                                + "; a palette holds exactly " + PaletteRole.COUNT + " colours");
            }
            // Snapped on the way in, so nothing downstream has to remember to do it.
            snapped.put(role, GbaColor.snap(color));
        }
        this.colors = snapped;
    }

    /**
     * Returns the colour playing the given role.
     *
     * @param role the role to resolve; must not be {@code null}
     * @return the colour for that role, never {@code null}
     */
    public Color color(PaletteRole role) {
        return colors.get(Objects.requireNonNull(role, "role must not be null"));
    }

    /**
     * Returns a role's colour at a reduced opacity, for washes and falloffs.
     *
     * <p>Opacity is not a colour, so varying it does not break the no-literals rule: the hue
     * still comes from the role.
     *
     * @param role    the role to resolve
     * @param opacity 0.0 fully transparent to 1.0 fully opaque
     * @return the role's colour at that opacity
     */
    public Color color(PaletteRole role, double opacity) {
        return color(role).deriveColor(0, 1, 1, Math.clamp(opacity, 0d, 1d));
    }

    /**
     * Mixes two roles, for gradients and for shading one surface towards another.
     *
     * @param from role at {@code t = 0}
     * @param to   role at {@code t = 1}
     * @param t    position between them, clamped to 0..1
     * @return the interpolated colour, snapped back onto the GBA grid
     */
    public Color mix(PaletteRole from, PaletteRole to, double t) {
        return GbaColor.snap(color(from).interpolate(color(to), Math.clamp(t, 0d, 1d)));
    }

    /**
     * Returns a role's colour with its brightness scaled, keeping hue and saturation.
     *
     * <p>A bevel's light and dark edges are not two more palette entries. They are the face colour
     * seen under more and less light, so they are expressed as a factor rather than picked - the
     * same reasoning as {@link #mix}, which stores a distance between roles rather than a colour.
     *
     * <p>Scaling brightness rather than mixing towards white or black is what keeps this working in
     * every mood. Mixing towards white flattens a light mood's bevel away entirely, because the
     * face is already near white; scaling is relative to whatever the face happens to be.
     *
     * @param role   the role to transform
     * @param factor brightness multiplier - below 1 darkens, above 1 lightens, clamped at both ends
     * @return the transformed colour, snapped back onto the GBA grid
     */
    public Color shaded(PaletteRole role, double factor) {
        return GbaColor.snap(color(role).deriveColor(0, 1, Math.max(0d, factor), 1));
    }

    /**
     * Returns a role's colour moved towards white.
     *
     * <p>{@link #shaded} cannot lift a colour that is already at full brightness, and the yellow
     * this interface uses for {@link PaletteRole#PRIMARY} is exactly that: scaling its brightness
     * up does nothing at all and the highlight silently disappears. A highlight on a colour that
     * bright has to come out of its saturation instead.
     *
     * @param role   the role to transform
     * @param amount 0 leaves the colour alone, 1 takes it to white
     * @return the transformed colour, snapped back onto the GBA grid
     */
    public Color tinted(PaletteRole role, double amount) {
        double t = Math.clamp(amount, 0d, 1d);
        return GbaColor.snap(color(role).deriveColor(0, 1 - t, 1 + t, 1));
    }

    /** @return the palette's human-readable name */
    public String name() {
        return name;
    }

    /**
     * Returns a copy with one role recoloured.
     *
     * <p>The customizer's whole edit path. Immutable rather than a setter for the reason stated
     * above: the render loop reads this from another thread, and a palette that could be half-way
     * through an edit is a frame drawn in two moods at once.
     *
     * @param role  the role to change; must not be {@code null}
     * @param color the new colour, snapped on the way in
     * @return the new palette
     */
    public Palette withColor(PaletteRole role, Color color) {
        Map<PaletteRole, Color> copy = new EnumMap<>(colors);
        copy.put(Objects.requireNonNull(role, "role must not be null"), color);
        return new Palette(name, copy);
    }

    /**
     * Returns a copy under a different name.
     *
     * @param newName the name to use
     * @return the new palette
     */
    public Palette renamed(String newName) {
        return new Palette(newName, colors);
    }

    /**
     * Returns the sixteen colours by role.
     *
     * @return a copy of the mapping, safe to modify
     */
    public Map<PaletteRole, Color> asMap() {
        return new EnumMap<>(colors);
    }

    /**
     * Builds a palette from sixteen colours in {@link PaletteRole} declaration order.
     *
     * <p>Declaration order is the format an imported {@code .gpl} or {@code .hex} lands on, so this
     * is what {@link PaletteImporter} and {@link MoodRepository} both go through rather than each
     * writing the same loop.
     *
     * @param name   the palette's name
     * @param colors exactly {@link PaletteRole#COUNT} colours
     * @return the palette
     * @throws IllegalArgumentException if the wrong number of colours is supplied
     */
    public static Palette of(String name, List<Color> colors) {
        Objects.requireNonNull(colors, "colors must not be null");
        if (colors.size() != PaletteRole.COUNT) {
            throw new IllegalArgumentException("A palette holds exactly " + PaletteRole.COUNT
                    + " colours, not " + colors.size());
        }
        Map<PaletteRole, Color> mapped = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            mapped.put(role, colors.get(role.ordinal()));
        }
        return new Palette(name, mapped);
    }

    /**
     * Returns the palette every view currently draws with.
     *
     * @return the active palette, never {@code null}
     */
    public static Palette active() {
        return active;
    }

    /**
     * Installs a palette as the one every view draws with.
     *
     * <p>Views must redraw themselves afterwards; this deliberately notifies nobody. A mood change
     * goes through {@code ui/Theme} and {@code App.applyMood}, which know which views only repaint
     * when their picture changes and have to be told; a notification here would be a second path to
     * the same thing, free to disagree with the first.
     *
     * <p>{@code MoodReactivity} also installs palettes through here, several times a second on a
     * reactive mood - which is exactly why this stays as cheap as a field assignment.
     *
     * @param palette the palette to install; {@code null} restores the default
     */
    public static void setActive(Palette palette) {
        active = palette == null ? defaultPalette() : palette;
    }

    /**
     * Builds the palette shipped with the application: the dark purple and amber look the
     * stylesheet already uses, expressed as roles.
     *
     * <p>The hexadecimal literals below are the one legal place for them in this project - this
     * is the definition of a palette, not a use of a colour. Every value is snapped to the 5-bit
     * grid on construction, so what is written here and what is drawn may differ by a step.
     *
     * @return a new copy of the default palette
     */
    public static Palette defaultPalette() {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        colors.put(PaletteRole.BACKGROUND, GbaColor.web("#12121c"));
        // A clear step above SURFACE rather than the #1c1b35 this started as. The two differ by
        // less than one 5-bit level, so they snapped to the same colour and the alternating table
        // rows they are the two halves of were not alternating at all - invisible while the
        // stylesheet still held its own unsnapped literals, and inherited the moment it stopped.
        colors.put(PaletteRole.BACKGROUND_ALT, GbaColor.web("#211f3e"));
        colors.put(PaletteRole.SURFACE, GbaColor.web("#1a1930"));
        colors.put(PaletteRole.SURFACE_RAISED, GbaColor.web("#2a2750"));
        colors.put(PaletteRole.OUTLINE, GbaColor.web("#443e7a"));
        colors.put(PaletteRole.TEXT_PRIMARY, GbaColor.web("#e6e8f0"));
        colors.put(PaletteRole.TEXT_DIM, GbaColor.web("#9aa3b8"));
        colors.put(PaletteRole.PRIMARY, GbaColor.web("#ffd23f"));
        colors.put(PaletteRole.PRIMARY_DIM, GbaColor.web("#7a6520"));
        colors.put(PaletteRole.ACCENT, GbaColor.web("#6ee7ff"));
        colors.put(PaletteRole.METER_LOW, GbaColor.web("#3fd36b"));
        colors.put(PaletteRole.METER_HIGH, GbaColor.web("#ffd23f"));
        colors.put(PaletteRole.POSITIVE, GbaColor.web("#5ce65c"));
        colors.put(PaletteRole.NEGATIVE, GbaColor.web("#ff4d5a"));
        colors.put(PaletteRole.HIGHLIGHT, GbaColor.web("#ff5edb"));
        colors.put(PaletteRole.SHADOW, GbaColor.web("#05040e"));
        return new Palette("Default", colors);
    }

    /**
     * The palette of the machine itself: black ground, white light, and nothing else.
     *
     * <p><strong>The boot screen and the shutdown screen draw through this rather than through
     * {@link #active()}, and that is a statement rather than an oversight.</strong> Those two screens
     * bracket the application: at the first one the system has not started, and at the last one it
     * has stopped. A mood is something the software chose, so a boot screen in Sunset Wilds is the
     * console admitting it was already running - and the flash at the moment the cartridge lands
     * stops being a flash of light and becomes a flash of somebody's colour scheme.
     *
     * <p>It is a {@link Palette} rather than a handful of literals in {@code ui/} because ground rule
     * 7 is about <em>where colours are defined</em>, not about how many palettes there are. Those
     * screens still name a {@link PaletteRole} for every colour they draw and still ask a palette for
     * it; they simply ask this one. Which means the hexadecimal values below sit in the one file the
     * project allows them in, and a screen that wants the mood back needs no change other than which
     * palette it reads.
     *
     * <p>Monochrome, so the roles carry <em>lightness</em> instead of hue: {@code ACCENT} and
     * {@code NEGATIVE} are the glitch's two interference bands and are a long way apart on that axis
     * for the same reason they are a long way apart in hue everywhere else. This palette is not a
     * mood, is never offered in the switcher and never reaches {@code MoodValidator} - the protected
     * roles' guarantees are about a look a user can choose, and nobody can choose this one.
     *
     * @return the fixed console palette, the same object every time
     */
    public static Palette hardware() {
        return HARDWARE;
    }

    /**
     * The console palette, built once.
     *
     * <p>Built eagerly and held, unlike {@link #defaultPalette()} which hands out a fresh copy:
     * a palette is immutable, this one can never be edited, and the boot screen asks for it on every
     * repaint of every frame of the glitch.
     */
    private static final Palette HARDWARE = hardwarePalette();

    /**
     * @return the console palette; see {@link #hardware()} for why it exists
     */
    private static Palette hardwarePalette() {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        // True black, and it stays true black through the 5-bit snap. A screen with nothing on it is
        // the whole of what a console looks like before it is handed a cartridge.
        colors.put(PaletteRole.BACKGROUND, GbaColor.web("#000000"));
        colors.put(PaletteRole.BACKGROUND_ALT, GbaColor.web("#0a0a0a"));
        colors.put(PaletteRole.SURFACE, GbaColor.web("#121212"));
        colors.put(PaletteRole.SURFACE_RAISED, GbaColor.web("#242424"));
        colors.put(PaletteRole.OUTLINE, GbaColor.web("#5a5a5a"));
        // The flash at the moment of contact, and it has to be white rather than nearly white.
        colors.put(PaletteRole.TEXT_PRIMARY, GbaColor.web("#ffffff"));
        colors.put(PaletteRole.TEXT_DIM, GbaColor.web("#8c8c8c"));
        // What the name on the label and the loading bar are drawn in: bright, but a step under the
        // flash, so the flash still reads as a flash over the top of them.
        colors.put(PaletteRole.PRIMARY, GbaColor.web("#e8e8e8"));
        colors.put(PaletteRole.PRIMARY_DIM, GbaColor.web("#4a4a4a"));
        colors.put(PaletteRole.ACCENT, GbaColor.web("#ffffff"));
        colors.put(PaletteRole.METER_LOW, GbaColor.web("#6e6e6e"));
        colors.put(PaletteRole.METER_HIGH, GbaColor.web("#dcdcdc"));
        colors.put(PaletteRole.POSITIVE, GbaColor.web("#cfcfcf"));
        colors.put(PaletteRole.NEGATIVE, GbaColor.web("#767676"));
        colors.put(PaletteRole.HIGHLIGHT, GbaColor.web("#ffffff"));
        colors.put(PaletteRole.SHADOW, GbaColor.web("#000000"));
        return new Palette("Hardware", colors);
    }

    @Override
    public String toString() {
        return "Palette[" + name + "]";
    }
}
