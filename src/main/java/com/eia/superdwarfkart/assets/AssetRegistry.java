package com.eia.superdwarfkart.assets;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.model.Racer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Finds the artwork, whatever it happens to be called.
 *
 * <p>Art is dumped into the assets folder flat, from another machine, and neither the filenames
 * nor the frame counts are agreed in advance. Nothing in the application may therefore name an
 * image file directly. Instead this scans two roots at startup, classifies what it finds by
 * filename keyword ({@link AssetKind}), and hands out sheets by <em>kind</em> or by key:
 *
 * <ol>
 *   <li>the artwork bundled in the jar under {@value AppConfig#ASSETS_RESOURCE_ROOT}</li>
 *   <li>the user's own folder, {@code ~/.superdwarfkart/assets}, which <strong>wins</strong> on a
 *       name clash - so new art can be dropped in without rebuilding</li>
 * </ol>
 *
 * <p>An optional {@code assets.json} manifest overrides all of the guessing. When no manifest
 * exists anywhere, one is written out on first run already filled in with what was detected, so
 * that correcting a wrong guess is a matter of editing a line rather than working out the format.
 *
 * <p><strong>Nothing here fails.</strong> A missing folder, an unreadable manifest, a corrupt PNG
 * and a completely empty assets tree are all normal: they log once and the application carries on
 * with magenta placeholders. Scanning reads filenames only - no image is decoded until something
 * asks for it - so an assets folder full of large sheets cannot slow down startup either.
 */
public final class AssetRegistry {

    private static final Logger LOG = Logger.getLogger(AssetRegistry.class.getName());

    /** Bumped when the manifest's shape changes in a way an older reader could not handle. */
    private static final int MANIFEST_VERSION = 1;

    /** Filename of the manifest, looked for in both asset roots. */
    private static final String MANIFEST_NAME = "assets.json";

    /** Extensions treated as artwork. Anything else in the folder - audio, text - is ignored. */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "bmp");

    /** Written into a generated manifest so that whoever opens it knows what it is for. */
    private static final String MANIFEST_NOTE =
            "Overrides the registry's filename guesses. 'kind' is one of "
                    + "DISK, SELECT, RACER, STAR, COIN, EXPLOSION, OBSTACLE, BACKGROUND, UNKNOWN. "
                    + "'frames' is how many frames the strip holds; 0 means work it out from the image.";

    private static AssetRegistry shared;

    private final Map<String, AssetEntry> entriesByKey;
    private final Map<String, SpriteSheet> loadedSheets = new LinkedHashMap<>();
    private final Map<AssetKind, SpriteSheet> loadedByKind = new EnumMap<>(AssetKind.class);
    private final Path manifestFile;

    private SpriteSheet placeholder;

    private AssetRegistry(Map<String, AssetEntry> entriesByKey, Path manifestFile) {
        this.entriesByKey = entriesByKey;
        this.manifestFile = manifestFile;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Returns the registry the interface shares, scanning on first use.
     *
     * <p>One scan per run: the artwork does not change while the application is open, and sprites
     * are looked up once per visible table row.
     *
     * @return the shared registry
     */
    public static synchronized AssetRegistry shared() {
        if (shared == null) {
            shared = scan(AppConfig.assetsDir());
        }
        return shared;
    }

    /**
     * Scans the bundled artwork plus a user folder.
     *
     * @param userAssetsDir the user's own assets folder; need not exist
     * @return a registry describing everything found, never {@code null}
     */
    public static AssetRegistry scan(Path userAssetsDir) {
        Objects.requireNonNull(userAssetsDir, "userAssetsDir must not be null");

        Map<String, AssetEntry> found = new LinkedHashMap<>();
        for (AssetEntry entry : scanClasspath()) {
            found.put(entry.key(), entry);
        }
        // The user's folder is scanned second on purpose: a key found in both wins from here, so
        // dropping a file in replaces the bundled one without a rebuild.
        for (AssetEntry entry : scanDirectory(userAssetsDir, true)) {
            found.put(entry.key(), entry);
        }

        Path manifest = userAssetsDir.resolve(MANIFEST_NAME);
        AssetRegistry registry = new AssetRegistry(found, manifest);
        registry.applyManifest(manifest);

        LOG.info("Assets: " + found.size() + " file(s) found"
                + " (" + countUserSupplied(found) + " from " + userAssetsDir + ")");
        registry.warnAboutMissingKinds();
        return registry;
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /**
     * Returns the sheet registered under a key, decoding it on first use.
     *
     * @param key the lookup name: a filename lower-cased, without its extension
     * @return the sheet, or the magenta placeholder if nothing is registered under that key
     */
    public SpriteSheet sheet(String key) {
        String lookup = key == null ? "" : key.toLowerCase(Locale.ROOT);
        SpriteSheet cached = loadedSheets.get(lookup);
        if (cached != null) {
            return cached;
        }
        AssetEntry entry = entriesByKey.get(lookup);
        SpriteSheet sheet = entry == null ? placeholder() : load(entry);
        loadedSheets.put(lookup, sheet);
        return sheet;
    }

    /**
     * Returns the sheet for a kind of artwork.
     *
     * <p>Where several files match a kind the first one found wins, which is deterministic
     * because scanning walks the tree in sorted order.
     *
     * @param kind what the artwork is for
     * @return the sheet, or the magenta placeholder if no file of that kind was found
     */
    public SpriteSheet sheet(AssetKind kind) {
        SpriteSheet cached = loadedByKind.get(kind);
        if (cached != null) {
            return cached;
        }
        SpriteSheet sheet = firstEntry(kind).map(this::load).orElseGet(this::placeholder);
        loadedByKind.put(kind, sheet);
        return sheet;
    }

    /**
     * Returns a racer's sheet, falling back to any racer artwork and then to the placeholder.
     *
     * @param racer the racer to draw
     * @return the sheet to draw them from
     */
    public SpriteSheet racer(Racer racer) {
        Objects.requireNonNull(racer, "racer must not be null");
        AssetEntry entry = entriesByKey.get(racer.spriteKey());
        if (entry != null) {
            return sheet(entry.key());
        }
        return sheet(AssetKind.RACER);
    }

    /**
     * Looks an entry up without decoding it.
     *
     * @param key the lookup name
     * @return the entry, or empty if nothing is registered under that key
     */
    public Optional<AssetEntry> entry(String key) {
        return Optional.ofNullable(entriesByKey.get(key == null ? "" : key.toLowerCase(Locale.ROOT)));
    }

    /** @return every entry found, in the order they were scanned */
    public List<AssetEntry> entries() {
        return List.copyOf(entriesByKey.values());
    }

    /**
     * Returns every entry of one kind.
     *
     * @param kind what the artwork is for
     * @return the matching entries, possibly empty
     */
    public List<AssetEntry> entries(AssetKind kind) {
        return entriesByKey.values().stream().filter(e -> e.kind() == kind).toList();
    }

    /**
     * @param kind what the artwork is for
     * @return the first entry of that kind, or empty if there is none
     */
    public Optional<AssetEntry> firstEntry(AssetKind kind) {
        return entriesByKey.values().stream().filter(e -> e.kind() == kind).findFirst();
    }

    /** @return whether any artwork at all was found */
    public boolean isEmpty() {
        return entriesByKey.isEmpty();
    }

    /** @return how many files were found */
    public int size() {
        return entriesByKey.size();
    }

    /** @return where the manifest is read from and written to */
    public Path manifestFile() {
        return manifestFile;
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    /**
     * Finds the artwork bundled with the application.
     *
     * <p>Handles both shapes the classpath takes: a directory while running from a build folder,
     * and entries inside a jar once packaged. Getting only the first case right would mean the
     * registry silently found nothing in a released build.
     *
     * @return the bundled entries, or an empty list if there are none
     */
    private static List<AssetEntry> scanClasspath() {
        URL root = AssetRegistry.class.getResource(AppConfig.ASSETS_RESOURCE_ROOT);
        if (root == null) {
            LOG.info("No bundled assets at " + AppConfig.ASSETS_RESOURCE_ROOT);
            return List.of();
        }
        try {
            return switch (root.getProtocol()) {
                case "file" -> scanDirectory(Path.of(root.toURI()), false);
                case "jar" -> scanJar(root);
                default -> {
                    LOG.warning("Cannot scan assets served over " + root.getProtocol() + ": " + root);
                    yield List.of();
                }
            };
        } catch (Exception e) {
            LOG.warning("Could not scan the bundled assets at " + root + ": " + e);
            return List.of();
        }
    }

    /**
     * Walks a folder recursively for artwork.
     *
     * @param root         folder to walk; a missing one yields no entries and is not an error
     * @param userSupplied whether these files came from the user's folder rather than the jar
     * @return the entries found, sorted by path so that "the first of a kind" is deterministic
     */
    static List<AssetEntry> scanDirectory(Path root, boolean userSupplied) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<AssetEntry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(file -> {
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        if (!isImage(relative)) {
                            return;
                        }
                        try {
                            entries.add(entryFor(relative, file.toUri().toURL(), userSupplied));
                        } catch (IOException e) {
                            LOG.warning("Skipping unreadable asset " + file + ": " + e);
                        }
                    });
        } catch (IOException e) {
            LOG.warning("Could not read the assets folder " + root + ": " + e);
        }
        return entries;
    }

    /**
     * Lists artwork inside the jar the application is running from.
     *
     * @param root the {@code jar:} URL of the assets folder
     * @return the entries found, sorted by path
     * @throws IOException if the jar cannot be opened
     */
    private static List<AssetEntry> scanJar(URL root) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        // Without this the JarFile below is shared and cached, and closing it would break every
        // later resource lookup in the whole application.
        connection.setUseCaches(false);

        String prefix = AppConfig.ASSETS_RESOURCE_ROOT.substring(1) + "/";
        List<AssetEntry> entries = new ArrayList<>();
        try (JarFile jar = connection.getJarFile()) {
            List<String> names = new ArrayList<>();
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    names.add(entry.getName());
                }
            }
            Collections.sort(names);
            for (String name : names) {
                String relative = name.substring(prefix.length());
                if (!isImage(relative)) {
                    continue;
                }
                URL location = AssetRegistry.class.getResource("/" + name);
                if (location != null) {
                    entries.add(entryFor(relative, location, false));
                }
            }
        }
        return entries;
    }

    /**
     * Builds an entry from a path and a location, classifying it by name.
     *
     * @param relativePath path relative to the assets root
     * @param location     where the bytes are
     * @param userSupplied whether it came from the user's folder
     * @return the entry, with the frame count left to be inferred at decode time
     */
    private static AssetEntry entryFor(String relativePath, URL location, boolean userSupplied) {
        String key = AssetKind.baseName(relativePath).toLowerCase(Locale.ROOT);
        return new AssetEntry(key, AssetKind.classify(relativePath), relativePath, location,
                AssetEntry.INFER_FRAMES, userSupplied);
    }

    /**
     * @param fileName the filename to test
     * @return whether the extension is one of the image formats JavaFX can decode
     */
    private static boolean isImage(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0
                && IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * @param entries the scanned entries
     * @return how many came from the user's folder
     */
    private static long countUserSupplied(Map<String, AssetEntry> entries) {
        return entries.values().stream().filter(AssetEntry::userSupplied).count();
    }

    // ------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------

    /**
     * Applies the manifest's overrides, or writes a template if there is no manifest at all.
     *
     * <p>The user's copy is preferred over a bundled one so that a correction survives a rebuild.
     *
     * @param userManifest where the user's manifest lives, whether or not it exists yet
     */
    private void applyManifest(Path userManifest) {
        ManifestData data = null;
        String source = null;

        if (Files.isReadable(userManifest)) {
            data = readManifest(userManifest);
            source = userManifest.toString();
        }
        if (data == null) {
            URL bundled = AssetRegistry.class.getResource(
                    AppConfig.ASSETS_RESOURCE_ROOT + "/" + MANIFEST_NAME);
            if (bundled != null) {
                data = readManifest(bundled);
                source = bundled.toString();
            }
        }

        if (data == null || data.assets == null || data.assets.isEmpty()) {
            writeManifestTemplate(userManifest);
            return;
        }

        int applied = 0;
        for (AssetData override : data.assets) {
            if (override == null || override.key == null || override.key.isBlank()) {
                continue;
            }
            String key = override.key.toLowerCase(Locale.ROOT);
            AssetEntry entry = entriesByKey.get(key);
            if (entry == null) {
                LOG.warning("Manifest names '" + key + "', which was not found in the assets folder");
                continue;
            }
            entriesByKey.put(key, entry.overriddenWith(parseKind(override.kind, entry.kind()),
                    Math.max(AssetEntry.INFER_FRAMES, override.frames)));
            applied++;
        }
        LOG.info("Applied " + applied + " asset override(s) from " + source);
    }

    /**
     * @param kindName  the kind named in the manifest, possibly {@code null} or misspelled
     * @param detected  the kind to keep if the manifest does not usefully name one
     * @return the kind to use
     */
    private static AssetKind parseKind(String kindName, AssetKind detected) {
        if (kindName == null || kindName.isBlank()) {
            return detected;
        }
        try {
            return AssetKind.valueOf(kindName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOG.warning("Manifest names an unknown asset kind '" + kindName + "' - keeping " + detected);
            return detected;
        }
    }

    /**
     * @param file the manifest to read
     * @return the parsed manifest, or {@code null} if it could not be read
     */
    private static ManifestData readManifest(Path file) {
        try {
            return new ObjectMapper().readValue(file.toFile(), ManifestData.class);
        } catch (IOException e) {
            LOG.warning("Ignoring the unreadable asset manifest at " + file + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * @param location the manifest to read
     * @return the parsed manifest, or {@code null} if it could not be read
     */
    private static ManifestData readManifest(URL location) {
        try {
            return new ObjectMapper().readValue(location, ManifestData.class);
        } catch (IOException e) {
            LOG.warning("Ignoring the unreadable asset manifest at " + location + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Writes a manifest filled in with what was detected, so the format never has to be guessed.
     *
     * <p>It goes in the user's folder rather than beside the bundled art because that is the only
     * one that is writable and that survives a clean build. Failing to write it is logged and
     * otherwise ignored - a manifest is a convenience, not a requirement.
     *
     * @param file where to write it
     */
    private void writeManifestTemplate(Path file) {
        if (entriesByKey.isEmpty()) {
            return;
        }
        ManifestData data = new ManifestData();
        data.version = MANIFEST_VERSION;
        data.note = MANIFEST_NOTE;
        data.assets = new ArrayList<>();
        for (AssetEntry entry : entriesByKey.values()) {
            AssetData record = new AssetData();
            record.key = entry.key();
            record.kind = entry.kind().name();
            record.file = entry.relativePath();
            record.frames = entry.declaredFrames();
            data.assets.add(record);
        }

        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file.toFile(), data);
            LOG.info("Wrote an asset manifest template to " + file);
        } catch (IOException e) {
            LOG.warning("Could not write the asset manifest template to " + file + ": " + e);
        }
    }

    // ------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------

    /**
     * @param entry the entry to decode
     * @return the decoded sheet, or the placeholder if the file could not be read
     */
    private SpriteSheet load(AssetEntry entry) {
        return SpriteSheet.load(entry.location(), entry.declaredFrames(), entry.relativePath());
    }

    /** @return the shared magenta stand-in, built once */
    private SpriteSheet placeholder() {
        if (placeholder == null) {
            placeholder = SpriteSheet.placeholder();
        }
        return placeholder;
    }

    /**
     * Logs the kinds of artwork the game will need and does not have.
     *
     * <p>One line at startup, not an exception: missing art draws a placeholder and the
     * application stays usable, but knowing what is missing beats discovering it on screen.
     */
    private void warnAboutMissingKinds() {
        List<String> missing = new ArrayList<>();
        for (AssetKind kind : AssetKind.values()) {
            if (kind != AssetKind.UNKNOWN && firstEntry(kind).isEmpty()) {
                missing.add(kind.name());
            }
        }
        if (!missing.isEmpty()) {
            LOG.warning("No artwork found for " + String.join(", ", missing)
                    + " - placeholders will be drawn instead");
        }
    }

    // ------------------------------------------------------------------
    // On-disk shape
    // ------------------------------------------------------------------

    /** The manifest file as Jackson sees it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class ManifestData {
        public int version;
        public String note;
        public List<AssetData> assets;
    }

    /** One override in the manifest. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class AssetData {
        public String key;
        public String kind;
        public String file;
        public int frames;
    }
}
