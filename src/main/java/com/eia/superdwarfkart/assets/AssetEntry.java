package com.eia.superdwarfkart.assets;

import java.net.URL;
import java.util.Objects;

/**
 * One piece of artwork the registry found, before it has been decoded.
 *
 * <p>Scanning records only what can be read from the filename, so that starting up costs a
 * directory listing rather than decoding every PNG in the folder. The image itself is read the
 * first time something asks for it.
 *
 * @param key            lookup name: the filename lower-cased, without its extension
 * @param kind           what the file appears to be for
 * @param relativePath   path relative to the assets root, as written in the manifest
 * @param location       where to read the bytes from
 * @param declaredFrames frame count stated by the manifest, or {@code 0} to infer it from the image
 * @param userSupplied   whether this came from the user's own assets folder rather than the jar
 */
public record AssetEntry(
        String key,
        AssetKind kind,
        String relativePath,
        URL location,
        int declaredFrames,
        boolean userSupplied) {

    /** Frame count meaning "work it out from the image dimensions". */
    public static final int INFER_FRAMES = 0;

    /**
     * @throws NullPointerException if the key, kind, path or location is {@code null}
     */
    public AssetEntry {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(location, "location must not be null");
        declaredFrames = Math.max(INFER_FRAMES, declaredFrames);
    }

    /**
     * Returns a copy carrying the kind and frame count a manifest states.
     *
     * @param kind   the kind the manifest gives
     * @param frames the frame count the manifest gives, or {@link #INFER_FRAMES}
     * @return the overridden entry
     */
    AssetEntry overriddenWith(AssetKind kind, int frames) {
        return new AssetEntry(key, kind, relativePath, location, frames, userSupplied);
    }
}
