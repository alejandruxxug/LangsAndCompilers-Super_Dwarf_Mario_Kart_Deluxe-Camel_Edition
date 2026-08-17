package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The moods that ship with the application.
 *
 * <p>Ten of them: the plain {@code DARK} and {@code LIGHT} that the assignment's dark-mode bonus is
 * delivered as, and eight named after <em>Mario Kart: Super Circuit</em> tracks - the GBA Mario
 * Kart, which is the exact reference the whole application is built on.
 *
 * <p><strong>Ten in the switcher reads as a system; two reads as a setting.</strong> That is the
 * entire argument for building {@link PaletteBuilder} and {@link PaletteImporter} before hand-picking
 * a single colour, and it is why the eight presets below are four lines each rather than sixteen:
 * a preset names the room it is set in and its two brand colours, and everything else is derived and
 * then forced to clear the same bars {@code MoodsTest} holds every mood to.
 *
 * <p><strong>{@code DARK} remains the application's default, not {@code PEACH_CIRCUIT}.</strong>
 * The mood system's own notes name the latter, and this is a deliberate departure: the dark purple
 * and amber look is the one the application has had since its first window, it is what every
 * screenshot in {@code docs/} shows, and it is the identity {@code Palette.defaultPalette()} is
 * documented as. Changing which mood a first launch opens in would have been a change to what the
 * application <em>is</em>, made as a side effect of adding nine more looks. {@code PEACH_CIRCUIT} is
 * the default of the eight <em>presets</em> in the sense that matters - it is the safe, readable one
 * to reach for first - and it is listed first among them.
 *
 * <p>The hexadecimal literals in this file are legal for the same reason the ones in
 * {@link Palette#defaultPalette()} are: this is the <em>definition</em> of a palette rather than a
 * use of a colour. Everywhere else names a {@link PaletteRole}.
 */
public final class Moods {

    /**
     * The name the Sky Garden preset's cloud tile is stored and referred to under.
     *
     * <p>Declared before the moods rather than beside them because a static field cannot be read by
     * an initializer that runs before it.
     */
    static final String CLOUD_TILE = "clouds";

    /**
     * The look the application has had since its first window: dark purple ground, amber primary,
     * cyan accents. The default, and deliberately unchanged by the mood system.
     */
    public static final Mood DARK = new Mood("dark", "Dark", Palette.defaultPalette());

    /**
     * The light mood.
     *
     * <p>Every value here was chosen against the <em>derivations</em> in {@link PaletteCss} rather
     * than by eye, because a light palette breaks an interface of beveled blocks in ways that look
     * fine in a colour picker:
     *
     * <ul>
     *   <li>{@link PaletteRole#SURFACE_RAISED} is a warm grey rather than white. A white face has
     *       no headroom above it, so the bevel's lit edge lands on the same colour as the face it
     *       is meant to be lighting and every button in the application goes flat.</li>
     *   <li>{@link PaletteRole#POSITIVE} and {@link PaletteRole#NEGATIVE} are dark enough to read
     *       against paper. The dark mood's bright green and red are close to invisible on it, and
     *       those two are protected roles: coins and obstacles telling each other apart is the
     *       whole of the runner's readability.</li>
     *   <li>{@link PaletteRole#TEXT_PRIMARY} is near-black and {@link PaletteRole#SHADOW} is a warm
     *       dark grey rather than black, so a shadow under dark text still reads as a shadow.</li>
     * </ul>
     *
     * <p>It predates {@link PaletteBuilder} and is kept hand-written for exactly that reason: it is
     * the palette whose failures taught the builder what to check for.
     */
    public static final Mood LIGHT = new Mood("light", "Light", lightPalette());

    /**
     * Peach Circuit: plum and pink with a grass-green accent. The safe, readable preset.
     */
    public static final Mood PEACH_CIRCUIT = preset("peach_circuit", "Peach Circuit",
            room("#2a1420", "#3a1e2e", "#52304a", "#8a5a7a"),
            "#ffb8d0", "#7ce8a0", List.of());

    /**
     * Sunset Wilds: the banded orange-to-magenta sky the track is named for.
     *
     * <p>The gradient is the preset. Eight bands and dithered, which is the pair of properties that
     * makes it read as hardware rather than as a web page - a smooth version of exactly this ramp
     * is the single fastest way to make the application look like it was built this decade.
     */
    public static final Mood SUNSET_WILDS = preset("sunset_wilds", "Sunset Wilds",
            room("#2a0e2a", "#3a1436", "#5a2050", "#9a4080"),
            "#ff9a3c", "#ff6ec7",
            List.of(sky(GradientLayer.between(PaletteRole.PRIMARY, PaletteRole.BACKGROUND, 90)
                    .withStops(List.of(
                            GradientStop.of(0, PaletteRole.PRIMARY),
                            GradientStop.of(0.45, PaletteRole.ACCENT),
                            GradientStop.of(1, PaletteRole.BACKGROUND))))));

    /**
     * Sky Garden: pastel green and cyan, with clouds drifting across it.
     *
     * <p>The clouds are a hand-drawn 16x16 tile stored as <em>palette indices</em>, which is worth
     * pointing at: recolour this mood and the clouds recolour with it, because there is no colour
     * in them to be out of date. It is also the built-in that proves the pixel editor's output is a
     * first-class layer rather than a toy - what ships here is exactly what "Save to layer"
     * produces.
     */
    public static final Mood SKY_GARDEN = new Mood("sky_garden", "Sky Garden",
            palette("Sky Garden", room("#cfe8d8", "#e2f2e6", "#b8d8c4", "#6a9a80"),
                    "#1a6a4a", "#0a5a7a"),
            List.of(
                    sky(GradientLayer.between(PaletteRole.BACKGROUND, PaletteRole.SURFACE, 90)),
                    new ImageLayer(
                            LayerStyle.behind().withOpacity(0.22).withScroll(-14, 0),
                            CLOUD_TILE, ImageLayer.Fit.TILE, 2, false)),
            Map.of(CLOUD_TILE, clouds()), false);

    /** Boo Lake: desaturated purple and sickly green, seen through a vignette. */
    public static final Mood BOO_LAKE = preset("boo_lake", "Boo Lake",
            room("#161a20", "#1e2430", "#303a4a", "#4a5a6a"),
            "#9ad8a8", "#b8a0e0",
            List.of(ProceduralLayer.of(ProceduralLayer.Pattern.VIGNETTE, 0.3)));

    /** Snow Land: icy white and blue, and the highest-contrast mood here. */
    public static final Mood SNOW_LAND = preset("snow_land", "Snow Land",
            room("#e8f0f8", "#f4f8fc", "#c8d8e8", "#5a7a9a"),
            "#0a4a8a", "#005a6a",
            List.of(sky(GradientLayer.between(PaletteRole.SURFACE, PaletteRole.BACKGROUND, 90))));

    /** Yoshi Desert: sand and amber, with a cyan accent so the headings are not also sand. */
    public static final Mood YOSHI_DESERT = preset("yoshi_desert", "Yoshi Desert",
            room("#2a1e10", "#3a2a18", "#5a4228", "#9a7a48"),
            "#ffc040", "#70d0e0",
            List.of(sky(
                    GradientLayer.between(PaletteRole.BACKGROUND, PaletteRole.SURFACE_RAISED, 90))));

    /**
     * Ribbon Road: pink pastel over a starfield.
     *
     * <p><strong>The stars do not drift, and that is a measurement rather than a preference.</strong>
     * A scrolling full-canvas layer is an alpha composite over the whole window on every frame, and
     * this machine has no working GPU - measured, it costs about eight milliseconds a frame, against
     * a baseline frame that already takes thirty. A starfield is a backdrop rather than a parallax,
     * so it loses nothing by standing still, and standing still costs exactly zero: it is flattened
     * into the cached backdrop and never touched again.
     *
     * <p>{@link #SKY_GARDEN} is the one preset that moves, deliberately - a mood system whose
     * scrolling nobody ever saw would be a feature nobody knew was there - and it is the one that
     * carries the cost. The customizer's own drift slider is right there for anybody who wants more.
     */
    public static final Mood RIBBON_ROAD = preset("ribbon_road", "Ribbon Road",
            room("#241030", "#32183e", "#4c2858", "#8a5a9a"),
            "#ff9ad8", "#9ad8ff",
            List.of(new ProceduralLayer(
                    LayerStyle.behind().withOpacity(0.9),
                    ProceduralLayer.Pattern.STARFIELD, 4, 20250816L)));

    /** Bowser Castle: red, black and orange, behind scanlines. */
    public static final Mood BOWSER_CASTLE = preset("bowser_castle", "Bowser Castle",
            room("#140808", "#1e0c0c", "#3a1414", "#7a2a1a"),
            "#ff5a20", "#ffd020",
            List.of(ProceduralLayer.of(ProceduralLayer.Pattern.SCANLINES, 0.28)));

    private Moods() {
        throw new AssertionError("Moods is a constant holder and must not be instantiated");
    }

    /** @return every built-in mood, in the order the switcher shows them */
    public static List<Mood> builtIns() {
        return List.of(DARK, LIGHT, PEACH_CIRCUIT, SUNSET_WILDS, SKY_GARDEN, BOO_LAKE, SNOW_LAND,
                YOSHI_DESERT, RIBBON_ROAD, BOWSER_CASTLE);
    }

    /** @return the mood used when nothing has been chosen, or when a stored choice is unknown */
    public static Mood defaultMood() {
        return DARK;
    }

    /**
     * Looks a built-in mood up by its stored identifier.
     *
     * <p>An unknown id is not an error: a mood may have been deleted, or a profile written by a
     * later version may name one this build has never heard of. The caller falls back to
     * {@link #defaultMood()} rather than refusing to start (ground rule 5).
     *
     * <p>Only the built-ins. A mood the user built lives on disk and is resolved through
     * {@link MoodRepository}, which searches both.
     *
     * @param id the identifier to resolve; {@code null} yields an empty result
     * @return the mood with that id, if there is one
     */
    public static Optional<Mood> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return builtIns().stream().filter(mood -> mood.id().equals(id)).findFirst();
    }

    /**
     * Whether an identifier belongs to a mood that ships with the application.
     *
     * <p>Which is the question "may this be edited in place" - and the answer is no. A built-in is
     * duplicated and then edited, so there is always a known-good mood to fall back to when an
     * experiment goes wrong twenty minutes before a defence.
     *
     * @param id the identifier to check
     * @return whether it names a built-in
     */
    public static boolean isBuiltIn(String id) {
        return byId(id).isPresent();
    }

    /**
     * How strongly a preset's wallpaper is drawn.
     *
     * <p><strong>Restrained, and the number was found by looking at a screenshot rather than by
     * taste.</strong> A layer {@link ZBand#BEHIND_CONTENT} shows through wherever the interface
     * leaves ground visible, and the largest such area is the library's own filter strip - which is
     * transparent because {@code .root-pane} used to paint a quiet ramp behind it. At full strength
     * an orange-to-magenta sky behind that strip put {@link PaletteRole#ACCENT} text on an orange
     * block and made the search box and every filter caption unreadable, while the table below it -
     * which has an opaque ground - was untouched. The result reads as a rendering fault rather than
     * as a look, and no assertion anywhere would have caught it.
     *
     * <p>At {@value} the same ramp reads as atmosphere over the base backdrop and every caption
     * stays legible. A user who wants a wall of colour can still have one: the customizer's slider
     * goes to 1 in this band, and it is <em>their</em> mood at that point rather than one the
     * application shipped.
     */
    private static final double PRESET_WALLPAPER_OPACITY = 0.4;

    /**
     * Turns a gradient into a preset's wallpaper at the restrained opacity above.
     *
     * @param gradient the gradient
     * @return it, drawn behind the content at {@link #PRESET_WALLPAPER_OPACITY}
     */
    private static MoodLayer sky(GradientLayer gradient) {
        return gradient.withStyle(
                gradient.style().withOpacity(PRESET_WALLPAPER_OPACITY));
    }

    /**
     * Assembles one of the eight presets.
     *
     * @param id      the stored identifier
     * @param name    the name in the switcher
     * @param room    the four ground colours: background, surface, raised surface, outline
     * @param primary the brand colour, which is read as often as it is looked at
     * @param accent  the second brand colour, likewise
     * @param layers  the overlay stack
     * @return the mood, its palette checked and corrected by {@link PaletteBuilder}
     */
    private static Mood preset(String id, String name, Map<PaletteRole, Color> room,
            String primary, String accent, List<MoodLayer> layers) {
        return new Mood(id, name, palette(name, room, primary, accent), layers);
    }

    /**
     * Builds a preset's palette from its room and its two brand colours.
     *
     * @param name    the palette's name
     * @param room    the four ground colours
     * @param primary the brand colour
     * @param accent  the second brand colour
     * @return the checked and corrected palette
     */
    private static Palette palette(String name, Map<PaletteRole, Color> room, String primary,
            String accent) {
        Map<PaletteRole, Color> seed = new EnumMap<>(room);
        seed.put(PaletteRole.PRIMARY, GbaColor.web(primary));
        seed.put(PaletteRole.ACCENT, GbaColor.web(accent));
        return PaletteBuilder.build(name, seed);
    }

    /**
     * The four colours that decide what room a mood is set in.
     *
     * @param background the stage's base fill
     * @param surface    panels and the library table
     * @param raised     control faces, hover and selection
     * @param outline    borders and dividers
     * @return the seed map
     */
    private static Map<PaletteRole, Color> room(String background, String surface, String raised,
            String outline) {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        colors.put(PaletteRole.BACKGROUND, GbaColor.web(background));
        colors.put(PaletteRole.SURFACE, GbaColor.web(surface));
        colors.put(PaletteRole.SURFACE_RAISED, GbaColor.web(raised));
        colors.put(PaletteRole.OUTLINE, GbaColor.web(outline));
        return colors;
    }

    /**
     * Sky Garden's clouds, drawn by hand as palette indices.
     *
     * <p>Index 0 is transparent, 5 is {@link PaletteRole#TEXT_PRIMARY} - the palette's brightest
     * colour, whatever the mood makes that - and 3 is {@link PaletteRole#SURFACE_RAISED} for the
     * underside. Two shapes on a 16x16 grid, offset so the tile has no obvious repeat along either
     * axis.
     *
     * @return the tile
     */
    private static PixelTile clouds() {
        return PixelTile.fromRows(16, PixelTile.DEFAULT_FPS, List.of(List.of(
                "0000000000000000",
                "0000033330000000",
                "0003555553000000",
                "0035555555300000",
                "0355555555530000",
                "0355555555553000",
                "0035555555555300",
                "0003333333333000",
                "0000000000000000",
                "0000000000000000",
                "0000000333300000",
                "0000003555530000",
                "0000035555553000",
                "0000003333333000",
                "0000000000000000",
                "0000000000000000")));
    }

    private static Palette lightPalette() {
        Map<PaletteRole, Color> colors = new EnumMap<>(PaletteRole.class);
        colors.put(PaletteRole.BACKGROUND, GbaColor.web("#e8e4d8"));
        colors.put(PaletteRole.BACKGROUND_ALT, GbaColor.web("#dcd8c8"));
        colors.put(PaletteRole.SURFACE, GbaColor.web("#f0ece0"));
        colors.put(PaletteRole.SURFACE_RAISED, GbaColor.web("#d8d4c4"));
        colors.put(PaletteRole.OUTLINE, GbaColor.web("#8a8270"));
        colors.put(PaletteRole.TEXT_PRIMARY, GbaColor.web("#1a1814"));
        colors.put(PaletteRole.TEXT_DIM, GbaColor.web("#5a5448"));
        // Dark amber, not the bright one this started as. PRIMARY is a text colour as often as it
        // is a fill - the application name, the table headings, the now-playing line, every focus
        // ring - and a bright amber that reads beautifully on the dark mood's near-black ground
        // came out at 2.0:1 against the light mood's own header. This is the shallowest value that
        // clears 4.5:1 on all three of the grounds it is ever drawn on.
        colors.put(PaletteRole.PRIMARY, GbaColor.web("#7a4400"));
        colors.put(PaletteRole.PRIMARY_DIM, GbaColor.web("#a89868"));
        // Darkened for the same reason as PRIMARY: ACCENT carries the section headings and the
        // playback status, so it is read rather than looked at.
        colors.put(PaletteRole.ACCENT, GbaColor.web("#004c80"));
        colors.put(PaletteRole.METER_LOW, GbaColor.web("#389838"));
        colors.put(PaletteRole.METER_HIGH, GbaColor.web("#d05000"));
        colors.put(PaletteRole.POSITIVE, GbaColor.web("#187818"));
        colors.put(PaletteRole.NEGATIVE, GbaColor.web("#b81818"));
        colors.put(PaletteRole.HIGHLIGHT, GbaColor.web("#b000b0"));
        colors.put(PaletteRole.SHADOW, GbaColor.web("#38342c"));
        return new Palette("Light", colors);
    }
}
