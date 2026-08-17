package com.eia.superdwarfkart.mood;

import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a palette out of the two text formats the rest of the pixel-art world already exports.
 *
 * <p><strong>This is the highest-value forty lines in the milestone, and the reason is arithmetic
 * rather than aesthetic.</strong> Choosing sixteen colours by eye takes an afternoon and usually
 * comes out muddy. Both of these formats are exported by <em>Aseprite</em> - which is what the
 * artwork in this project was drawn in - and by <em>Lospec</em>, which hosts hundreds of ready-made
 * GBA and sixteen-colour palettes. So "design a mood" goes from an afternoon to ten seconds, which
 * is the difference between shipping twenty moods and shipping two: twenty in the switcher reads as
 * a <em>system</em>, three reads as a setting.
 *
 * <p>It is also the only part of this project a non-technical teammate can contribute to without
 * touching Java.
 *
 * <h2>The formats</h2>
 *
 * <ul>
 *   <li><strong>GIMP {@code .gpl}</strong> - a {@code GIMP Palette} header, then {@code Name:} and
 *       {@code Columns:} lines, then {@code #} comments, then rows of {@code R G B} followed by an
 *       optional name. Channels are whitespace-separated, which in practice means any run of spaces
 *       or a tab, and files in the wild use both.</li>
 *   <li><strong>Plain {@code .hex}</strong> - one {@code RRGGBB} per line, which is what Lospec's
 *       "HEX" download button produces.</li>
 * </ul>
 *
 * <p>Both are read leniently: an unparseable line is skipped rather than fatal, because a palette
 * with a stray line in it is still a palette and refusing the whole file over one is the behaviour
 * ground rule 5 exists to prevent.
 */
public final class PaletteImporter {

    /** Extensions this understands, lower case, for a file filter and for a drop target. */
    public static final List<String> EXTENSIONS = List.of(".gpl", ".hex");

    private PaletteImporter() {
        throw new AssertionError("PaletteImporter is a utility holder and must not be instantiated");
    }

    /**
     * Whether a file looks like something this can read.
     *
     * @param file the file to check; {@code null} yields {@code false}
     * @return whether the extension is one of {@link #EXTENSIONS}
     */
    public static boolean canRead(Path file) {
        if (file == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /**
     * Reads a palette from a file, snaps it to the hardware grid and repairs it if it breaks a
     * protected pair.
     *
     * <p>The repair is not optional and it is not a courtesy. An arbitrary sixteen-colour palette
     * off the internet has no idea that entries twelve and thirteen are going to be coins and
     * obstacles, and a good few of them put two greens there. Importing without checking would turn
     * "design a mood in ten seconds" into "make the game unreadable in ten seconds".
     *
     * @param file the {@code .gpl} or {@code .hex} file; must not be {@code null}
     * @return the palette, named after the file
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if it holds no colours at all
     */
    public static Palette read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String name = stripExtension(file.getFileName().toString());
        return fromText(name, text);
    }

    /**
     * Reads a palette from the text of a {@code .gpl} or {@code .hex} file.
     *
     * <p>The format is decided by the content rather than by the extension, so a {@code .txt} that
     * happens to hold a GIMP palette works and a {@code .gpl} that somebody saved as plain hex does
     * too. There is nothing to be gained from being strict about it.
     *
     * @param name the palette's name
     * @param text the file's contents
     * @return the palette, snapped to the GBA grid and repaired if necessary
     * @throws IllegalArgumentException if no colour could be read at all
     */
    public static Palette fromText(String name, String text) {
        List<Color> colors = readColors(text);
        if (colors.isEmpty()) {
            throw new IllegalArgumentException(
                    "No colours could be read from this file. A GIMP palette holds lines of "
                            + "\"R G B  Name\"; a .hex file holds one RRGGBB per line.");
        }
        return toPalette(name, colors);
    }

    /**
     * Reads every colour a palette file holds, in file order.
     *
     * @param text the file's contents
     * @return the colours, snapped to the GBA grid; empty when none could be read
     */
    public static List<Color> readColors(String text) {
        List<Color> colors = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") && !isHexLine(line)) {
                // A leading hash is a comment in .gpl and a colour in .hex. The two are told apart
                // by whether what follows is six hex digits and nothing else.
                continue;
            }
            if (line.regionMatches(true, 0, "GIMP Palette", 0, 12)) {
                continue;
            }
            // "Name: Sunset" and "Columns: 8" - metadata, not colours.
            int colon = line.indexOf(':');
            if (colon > 0 && !Character.isDigit(line.charAt(0))) {
                continue;
            }
            Color color = parseLine(line);
            if (color != null) {
                colors.add(color);
            }
        }
        return colors;
    }

    /**
     * Assigns colours to roles in enum order and repairs whatever that breaks.
     *
     * <p>The order is the format: {@link PaletteRole}'s declaration order is what an imported
     * palette's first sixteen entries land on, which is why reordering those constants would
     * silently rewrite every palette anybody has ever imported.
     *
     * @param name   the palette's name
     * @param colors the colours, in file order; must hold at least one
     * @return the palette
     */
    public static Palette toPalette(String name, List<Color> colors) {
        Map<PaletteRole, Color> assigned = new EnumMap<>(PaletteRole.class);
        PaletteRole[] roles = PaletteRole.values();
        for (int i = 0; i < roles.length; i++) {
            assigned.put(roles[i], extend(colors, i));
        }
        return MoodValidator.repair(new Palette(name, assigned));
    }

    /**
     * Picks the colour for role {@code index}, extending a short palette rather than refusing it.
     *
     * <p>Plenty of good palettes hold eight colours, or five. Refusing those would mean telling
     * somebody their palette is wrong when it is merely short, over a format that has no notion of
     * sixteen anything. So the sequence repeats, with each repetition shifted a step in lightness -
     * which keeps every role a distinct colour (a palette that used one colour twice would make two
     * roles indistinguishable by construction, which is precisely what the validator is for) and
     * produces a light and a dark variant of the same set, which is a plausible thing for a palette
     * to look like.
     *
     * <p>A palette of sixteen or more is untouched: the first sixteen entries land on the sixteen
     * roles and the rest are dropped, exactly as specified.
     */
    private static Color extend(List<Color> colors, int index) {
        Color base = colors.get(index % colors.size());
        int lap = index / colors.size();
        if (lap == 0) {
            return base;
        }
        // Alternating up and down, growing: lap 1 lightens, lap 2 darkens further, and so on. Going
        // one way only would drive a short palette to white and lose it.
        double amount = 0.22 * ((lap + 1) / 2);
        double direction = lap % 2 == 1 ? 1 : -1;
        return ColorMath.shiftLightness(base, direction * amount);
    }

    /** Whether a line beginning with '#' is a hex colour rather than a comment. */
    private static boolean isHexLine(String line) {
        String body = line.substring(1).strip();
        int end = body.indexOf(' ');
        if (end > 0) {
            body = body.substring(0, end);
        }
        return body.length() == 6 || body.length() == 8;
    }

    /**
     * Reads one colour from a line in either format.
     *
     * @return the colour, or {@code null} when the line holds none
     */
    private static Color parseLine(String line) {
        String body = line.startsWith("#") ? line.substring(1).strip() : line;

        // .hex: six or eight hex digits and nothing else that matters. Checked before the .gpl
        // shape because "ff8800" is also three numbers to anybody splitting on whitespace.
        String first = body.split("\\s+")[0];
        if (first.length() == 6 || first.length() == 8) {
            Color hex = parseHex(first);
            if (hex != null) {
                return hex;
            }
        }

        // .gpl: "255 136 0   Orange"
        String[] parts = body.split("\\s+");
        if (parts.length >= 3) {
            try {
                int r = Integer.parseInt(parts[0]);
                int g = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                if (inRange(r) && inRange(g) && inRange(b)) {
                    return GbaColor.of(r, g, b);
                }
            } catch (NumberFormatException ignored) {
                // Not a colour row. Skipped rather than fatal.
            }
        }
        return null;
    }

    private static Color parseHex(String digits) {
        try {
            int r = Integer.parseInt(digits.substring(0, 2), 16);
            int g = Integer.parseInt(digits.substring(2, 4), 16);
            int b = Integer.parseInt(digits.substring(4, 6), 16);
            return GbaColor.of(r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean inRange(int channel) {
        return channel >= 0 && channel <= 255;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
