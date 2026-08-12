package com.eia.superdwarfkart.assets;

import com.eia.superdwarfkart.model.Racer;

import java.util.List;
import java.util.Locale;

/**
 * What a piece of artwork is for, worked out from its filename.
 *
 * <p>Artwork is dumped into the assets folder flat, from another machine, with no agreed naming
 * scheme. So nothing here may assume a particular filename: a sheet is classified by looking for
 * keywords <em>inside</em> its name, case-insensitively, and anything unrecognised becomes
 * {@link #UNKNOWN} rather than an error.
 *
 * <p>The Spanish keywords are the one place in this codebase where Spanish is allowed. They are
 * patterns being matched <em>against</em>, never identifiers - the artist names files in Spanish
 * and the application has to cope with that.
 *
 * <p><strong>Declaration order is priority order</strong>, and it runs from the most specific
 * keyword to the least. A filename names what a sprite <em>is</em> and then qualifies it with
 * what it belongs to, so {@code kart_explosion.png} is an explosion and {@code racer-select.png}
 * is a menu screen. {@link #RACER} is therefore tried last of all: {@code kart}, {@code char} and
 * {@code player} turn up as qualifiers on artwork that is not a racer sprite at all, whereas
 * {@code explos} only ever appears on an explosion.
 */
public enum AssetKind {

    /** The spinning disk shown in the mini player. */
    DISK("disk", "disc"),

    /** Menu and portrait art for choosing a racer. */
    SELECT("select"),

    /** The invulnerability star. */
    STAR("star", "estrella"),

    /** Collectible coins. */
    COIN("coin", "moneda"),

    /** The two-frame explosion played when a starred racer breaks an obstacle. */
    EXPLOSION("explos"),

    /** Obstacles in the runner. */
    OBSTACLE("bump", "obstacle"),

    /** Backdrop art. */
    BACKGROUND("bg", "background", "fondo"),

    /**
     * A racer's sprite sheet.
     *
     * <p>Matched by the generic keywords below <em>and</em> by every {@link Racer#spriteKey()},
     * because the sheets already in the repository are named after the characters
     * ({@code Mario.png}, {@code Yoshi.png}) and contain none of these words.
     *
     * <p>Last in the list on purpose - see the note on the class above.
     */
    RACER("char", "player", "kart", "racer", "personaje"),

    /** Recognised as artwork, but not as anything the application knows what to do with. */
    UNKNOWN();

    /**
     * Longer than this and a keyword is matched anywhere inside the name; at or below it the
     * keyword has to stand alone, so that {@code bg} does not match a file called {@code bgm}.
     */
    private static final int SUBSTRING_MIN_LENGTH = 3;

    private final List<String> keywords;

    AssetKind(String... keywords) {
        this.keywords = List.of(keywords);
    }

    /** @return the filename keywords that identify this kind, in the order they are tried */
    public List<String> keywords() {
        return keywords;
    }

    /**
     * Works out what a file is for from its name.
     *
     * @param fileName the filename, with or without a directory in front of it
     * @return the matching kind, or {@link #UNKNOWN} if no keyword matched
     */
    public static AssetKind classify(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }
        String name = baseName(fileName).toLowerCase(Locale.ROOT);
        for (AssetKind kind : values()) {
            if (kind != UNKNOWN && kind.matches(name)) {
                return kind;
            }
        }
        return UNKNOWN;
    }

    /**
     * Reduces a path to the filename without its extension, which is the only part classified.
     *
     * @param fileName the filename or path
     * @return the bare name, for example {@code Disk-Sheet} for {@code textures/Disk-Sheet.png}
     */
    static String baseName(String fileName) {
        String name = fileName.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * @param name the lower-cased base name
     * @return whether this kind claims that name
     */
    private boolean matches(String name) {
        for (String keyword : keywords) {
            if (contains(name, keyword)) {
                return true;
            }
        }
        if (this == RACER) {
            for (Racer racer : Racer.values()) {
                if (contains(name, racer.spriteKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Looks for a keyword in a name.
     *
     * <p>Short keywords are matched only as whole words. Without that rule the two-letter
     * {@code bg} would claim anything with those letters adjacent anywhere in it.
     *
     * @param name    the lower-cased base name
     * @param keyword the lower-cased keyword to look for
     * @return whether the keyword is present
     */
    private static boolean contains(String name, String keyword) {
        if (keyword.length() >= SUBSTRING_MIN_LENGTH) {
            return name.contains(keyword);
        }
        int at = name.indexOf(keyword);
        while (at >= 0) {
            int end = at + keyword.length();
            boolean startsCleanly = at == 0 || !Character.isLetterOrDigit(name.charAt(at - 1));
            boolean endsCleanly = end == name.length() || !Character.isLetterOrDigit(name.charAt(end));
            if (startsCleanly && endsCleanly) {
                return true;
            }
            at = name.indexOf(keyword, at + 1);
        }
        return false;
    }
}
