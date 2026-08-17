package com.eia.superdwarfkart.mood;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Where the user's own moods live: one folder each, under {@code ~/.superdwarfkart/moods/}.
 *
 * <p>A folder rather than a file, because a mood is not only sixteen colours. It carries its layer
 * definitions, any artwork imported into it and any tiles drawn in the pixel editor, and all of
 * that has to travel together the moment somebody zips one up and sends it to a teammate. An
 * imported image is <em>copied in</em> and referred to by name alone - a mood that pointed at where
 * a picture came from would work perfectly on the machine it was built on and show nothing anywhere
 * else, silently.
 *
 * <p><strong>The presets are not here, and that is a departure from the mood system's own notes.</strong>
 * Those say built-ins are resources copied out on first run if absent. They are Java instead, which
 * buys two things worth more than the symmetry: a user cannot corrupt a preset, so there is always a
 * known-good mood to fall back to twenty minutes before a defence; and the presets are checked by
 * {@code MoodsTest} at build time rather than parsed at runtime and hoped for. Duplicating a preset
 * writes a <em>user</em> mood here, which is the only way any of them is ever edited.
 *
 * <p><strong>Nothing here may stop the application from opening.</strong> A mood that fails to load
 * is logged and skipped; a folder full of them still yields whichever ones parsed. The active mood
 * falling back to the default costs the user their colours and nothing else (ground rule 5).
 */
public class MoodRepository {

    private static final Logger LOG = Logger.getLogger(MoodRepository.class.getName());

    /** The file inside a mood's folder that describes it. */
    public static final String MOOD_FILE = "mood.json";

    /** The suffix an exported mood carries, so it is recognisable in a downloads folder. */
    public static final String EXPORT_SUFFIX = ".mood.json";

    /** Bumped when the on-disk shape changes in a way an older reader could not handle. */
    private static final int FORMAT_VERSION = 1;

    private final Path root;
    private final ObjectMapper mapper;
    private final Map<String, Mood> loaded = new LinkedHashMap<>();

    /**
     * Opens the repository over a folder, reading whatever is already in it.
     *
     * <p>The path is supplied rather than read from {@code AppConfig}, which keeps this package free
     * of any dependency on {@code app/} - the same reason {@code mood/} takes plain numbers from the
     * audio path rather than importing {@code Levels}.
     *
     * @param root the moods folder; must not be {@code null}, need not exist yet
     */
    public MoodRepository(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        reload();
    }

    /** @return where the moods are kept */
    public Path storageLocation() {
        return root;
    }

    /**
     * Rereads every mood on disk.
     *
     * <p>Called at startup, and again after an import - which is the case that matters, because an
     * import that did not appear in the switcher until the next launch would read as an import that
     * failed.
     */
    public final void reload() {
        loaded.clear();
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> folders;
        try (Stream<Path> entries = Files.list(root)) {
            folders = entries.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list " + root + " - no user moods will be shown", e);
            return;
        }
        for (Path folder : folders) {
            read(folder.resolve(MOOD_FILE)).ifPresent(mood -> loaded.put(mood.id(), mood));
        }
    }

    /** @return the moods the user has built, in name order */
    public List<Mood> userMoods() {
        return loaded.values().stream()
                .sorted(Comparator.comparing(Mood::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Every mood the switcher shows: the presets first, then the user's own.
     *
     * <p>Presets first because they are the ones somebody reaches for when they do not yet know
     * what they want, and because a list that opened with eleven variations on Bowser Castle would
     * bury them.
     *
     * @return the whole set
     */
    public List<Mood> all() {
        List<Mood> everything = new ArrayList<>(Moods.builtIns());
        everything.addAll(userMoods());
        return List.copyOf(everything);
    }

    /**
     * Resolves a stored mood id against the presets and the user's own moods.
     *
     * @param id the identifier; {@code null} yields an empty result
     * @return the mood, if this build has one of that name
     */
    public Optional<Mood> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Mood user = loaded.get(id);
        return user != null ? Optional.of(user) : Moods.byId(id);
    }

    /**
     * Writes a mood out and keeps it in the in-memory set.
     *
     * <p>Through a temporary file and an atomic move, so a crash mid-write cannot leave a
     * half-written mood the next launch has to recover from.
     *
     * @param mood the mood to save; must not be {@code null}
     * @throws IOException if the folder or the file cannot be written
     */
    public void save(Mood mood) throws IOException {
        Objects.requireNonNull(mood, "mood must not be null");
        if (Moods.isBuiltIn(mood.id())) {
            throw new IllegalArgumentException("\"" + mood.displayName() + "\" is one of the moods "
                    + "that ship with the application. Duplicate it and edit the copy - there has "
                    + "to be a known-good mood to fall back to.");
        }
        Path folder = folderOf(mood.id());
        Files.createDirectories(folder);
        Path file = folder.resolve(MOOD_FILE);
        Path temporary = folder.resolve(MOOD_FILE + ".tmp");
        mapper.writeValue(temporary.toFile(), toDto(mood));
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        loaded.put(mood.id(), mood);
    }

    /**
     * Deletes a mood and everything in its folder.
     *
     * @param id the mood to delete
     * @throws IOException if the folder cannot be removed
     */
    public void delete(String id) throws IOException {
        if (Moods.isBuiltIn(id)) {
            throw new IllegalArgumentException("A mood that ships with the application cannot be "
                    + "deleted.");
        }
        loaded.remove(id);
        Path folder = folderOf(id);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(folder)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Where a mood's own files live.
     *
     * @param id the mood's identifier
     * @return the folder; not guaranteed to exist
     */
    public Path folderOf(String id) {
        return root.resolve(slug(id));
    }

    /**
     * Copies an image into a mood's folder, so the mood owns it from then on.
     *
     * <p>The name is made unique inside the folder rather than reused, because importing two
     * pictures that happen to both be called {@code background.png} must not have the second
     * silently replace the first in a layer that is already using it.
     *
     * @param moodId the mood to import into
     * @param source the picture to copy; must not be {@code null}
     * @return the name to give an {@link ImageLayer}
     * @throws IOException if the copy fails
     */
    public String importImage(String moodId, Path source) throws IOException {
        Path folder = folderOf(moodId);
        Files.createDirectories(folder);
        String base = source.getFileName().toString();
        String name = base;
        int attempt = 1;
        while (Files.exists(folder.resolve(name))) {
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String extension = dot > 0 ? base.substring(dot) : "";
            name = stem + "-" + attempt++ + extension;
        }
        Files.copy(source, folder.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
        return name;
    }

    /**
     * Writes a mood and its artwork somewhere a teammate can pick it up.
     *
     * @param mood      the mood to export; must not be {@code null}
     * @param targetDir where to write it
     * @return the folder written
     * @throws IOException if anything cannot be written
     */
    public Path export(Mood mood, Path targetDir) throws IOException {
        Path folder = targetDir.resolve(slug(mood.id()));
        Files.createDirectories(folder);
        // Named after the mood rather than left as mood.json, so half a dozen of them in a
        // downloads folder are still told apart.
        mapper.writeValue(folder.resolve(slug(mood.id()) + EXPORT_SUFFIX).toFile(), toDto(mood));

        Path source = folderOf(mood.id());
        if (Files.isDirectory(source)) {
            try (Stream<Path> entries = Files.list(source)) {
                for (Path file : entries.filter(Files::isRegularFile).toList()) {
                    if (file.getFileName().toString().equals(MOOD_FILE)) {
                        continue;
                    }
                    Files.copy(file, folder.resolve(file.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return folder;
    }

    /**
     * Reads a mood somebody else exported and installs it as one of the user's own.
     *
     * <p>Accepts either the {@code .mood.json} itself or the folder holding it, because both are
     * things a person plausibly drops on the window. The identifier is made unique rather than
     * allowed to collide: a shared mood must never overwrite one the user built.
     *
     * @param source the exported file or folder; must not be {@code null}
     * @return the installed mood
     * @throws IOException if it cannot be read or written
     */
    public Mood importFrom(Path source) throws IOException {
        Path found = source;
        if (Files.isDirectory(source)) {
            try (Stream<Path> entries = Files.list(source)) {
                found = entries.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .findFirst()
                        .orElseThrow(() -> new IOException(
                                "No mood file in " + source + " - an exported mood folder holds a "
                                        + EXPORT_SUFFIX + " beside its images."));
            }
        }
        final Path file = found;
        Mood mood = read(file).orElseThrow(() -> new IOException(
                "Could not read a mood from " + file));

        String id = uniqueId(mood.id());
        Mood installed = mood.copyAs(id,
                loaded.containsKey(mood.id()) || Moods.isBuiltIn(mood.id())
                        ? mood.displayName() + " (imported)"
                        : mood.displayName());
        save(installed);

        // The pictures the layers name, alongside the definition. A layer whose image did not come
        // with it draws the missing-artwork marker rather than throwing, but a mood that arrives
        // showing magenta rectangles reads as a broken import.
        Path folder = file.getParent();
        if (folder != null) {
            try (Stream<Path> entries = Files.list(folder)) {
                for (Path picture : entries.filter(Files::isRegularFile).toList()) {
                    String name = picture.getFileName().toString();
                    if (name.endsWith(".json")) {
                        continue;
                    }
                    Files.copy(picture, folderOf(id).resolve(name),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return installed;
    }

    /**
     * An identifier nothing is already using.
     *
     * @param wanted the identifier to start from
     * @return the same one when it is free, otherwise one with a number on the end
     */
    public String uniqueId(String wanted) {
        String base = slug(wanted);
        String candidate = base;
        int attempt = 2;
        while (loaded.containsKey(candidate) || Moods.isBuiltIn(candidate)) {
            candidate = base + "-" + attempt++;
        }
        return candidate;
    }

    /**
     * Turns a display name into something safe to use as a folder name and an identifier.
     *
     * <p>A mood folder is created from a name the user typed, so this is the only thing between a
     * text field and the filesystem. Anything that is not a letter, a digit or a dash becomes a
     * dash, which rules out separators, traversal and every reserved character at once.
     *
     * @param name the name to convert
     * @return the slug, never blank
     */
    public static String slug(String name) {
        if (name == null || name.isBlank()) {
            return "mood";
        }
        String slug = name.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "mood" : slug;
    }

    /**
     * Reads one mood file.
     *
     * @param file the file to read
     * @return the mood, or empty when it is missing or unreadable
     */
    private Optional<Mood> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            MoodDto dto = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    MoodDto.class);
            return Optional.ofNullable(fromDto(dto, file));
        } catch (IOException | RuntimeException e) {
            // Logged and skipped. One unreadable mood must not cost the user the other nineteen,
            // and it must certainly not stop the application opening.
            LOG.log(Level.WARNING, "Could not read the mood in " + file + " - skipping it", e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private MoodDto toDto(Mood mood) {
        MoodDto dto = new MoodDto();
        dto.version = FORMAT_VERSION;
        dto.id = mood.id();
        dto.name = mood.displayName();
        dto.reactive = mood.reactive();
        dto.palette = new ArrayList<>();
        for (PaletteRole role : PaletteRole.values()) {
            dto.palette.add(GbaColor.toHex(mood.color(role)));
        }
        dto.layers = mood.layers().stream().map(MoodRepository::toDto).toList();
        if (!mood.tiles().isEmpty()) {
            dto.tiles = new LinkedHashMap<>();
            mood.tiles().forEach((name, tile) -> {
                TileDto tileDto = new TileDto();
                tileDto.size = tile.size();
                tileDto.fps = tile.fps();
                tileDto.frames = tile.rows();
                dto.tiles.put(name, tileDto);
            });
        }
        return dto;
    }

    private Mood fromDto(MoodDto dto, Path file) {
        if (dto == null) {
            return null;
        }
        String id = dto.id != null && !dto.id.isBlank() ? dto.id
                : slug(file.getParent() == null ? "mood" : file.getParent().getFileName().toString());
        String name = dto.name != null && !dto.name.isBlank() ? dto.name : id;

        List<Color> colors = new ArrayList<>();
        if (dto.palette != null) {
            for (String hex : dto.palette) {
                try {
                    colors.add(GbaColor.web(hex));
                } catch (RuntimeException e) {
                    // One bad swatch is not a bad mood. The importer's own extension rule fills
                    // whatever is short.
                    LOG.warning("Ignoring unreadable colour \"" + hex + "\" in " + file);
                }
            }
        }
        // Through the importer rather than straight into a Palette: it is the one place that knows
        // how to extend a short palette and it runs the validator on the way out, so a hand-edited
        // mood file cannot install a palette that makes coins and obstacles look alike.
        Palette palette = colors.isEmpty()
                ? Palette.defaultPalette()
                : PaletteImporter.toPalette(name, colors);

        Map<String, PixelTile> tiles = new LinkedHashMap<>();
        if (dto.tiles != null) {
            dto.tiles.forEach((tileName, tileDto) -> {
                try {
                    tiles.put(tileName,
                            PixelTile.fromRows(tileDto.size,
                                    tileDto.fps <= 0 ? PixelTile.DEFAULT_FPS : tileDto.fps,
                                    tileDto.frames));
                } catch (RuntimeException e) {
                    LOG.warning("Ignoring unreadable tile \"" + tileName + "\" in " + file);
                }
            });
        }

        List<MoodLayer> layers = new ArrayList<>();
        if (dto.layers != null) {
            for (LayerDto layerDto : dto.layers) {
                MoodLayer layer = fromDto(layerDto);
                if (layer != null && layers.size() < MoodLayer.MAX_LAYERS) {
                    layers.add(layer);
                }
            }
        }

        return new Mood(id, name, palette, layers, tiles, dto.reactive);
    }

    private static LayerDto toDto(MoodLayer layer) {
        LayerDto dto = new LayerDto();
        LayerStyle style = layer.style();
        dto.zBand = style.zBand().name();
        dto.opacity = style.opacity();
        dto.blend = style.blend().name();
        dto.scrollX = style.scrollX();
        dto.scrollY = style.scrollY();
        dto.visible = style.visible();

        switch (layer) {
            case GradientLayer gradient -> {
                dto.type = "gradient";
                dto.kind = gradient.kind().name();
                dto.angle = gradient.angle();
                dto.centerX = gradient.centerX();
                dto.centerY = gradient.centerY();
                dto.radius = gradient.radius();
                dto.bands = gradient.bands();
                dto.dither = gradient.dither();
                dto.stops = new ArrayList<>();
                for (GradientStop stop : gradient.stops()) {
                    StopDto stopDto = new StopDto();
                    stopDto.position = stop.position();
                    stopDto.role = stop.role() == null ? null : stop.role().name();
                    stopDto.color = stop.fixed() == null ? null : GbaColor.toHex(stop.fixed());
                    dto.stops.add(stopDto);
                }
            }
            case ImageLayer image -> {
                dto.type = "image";
                dto.fileName = image.fileName();
                dto.fit = image.fit().name();
                dto.pixelScale = image.pixelScale();
                dto.animated = image.animated();
            }
            case ProceduralLayer procedural -> {
                dto.type = "procedural";
                dto.pattern = procedural.pattern().name();
                dto.pixelScale = procedural.pixelScale();
                dto.seed = procedural.seed();
            }
        }
        return dto;
    }

    private static MoodLayer fromDto(LayerDto dto) {
        if (dto == null || dto.type == null) {
            return null;
        }
        LayerStyle style = new LayerStyle(ZBand.byName(dto.zBand), dto.opacity,
                LayerBlend.byName(dto.blend), dto.scrollX, dto.scrollY, dto.visible);

        try {
            return switch (dto.type.toLowerCase(Locale.ROOT)) {
                case "gradient" -> {
                    List<GradientStop> stops = new ArrayList<>();
                    if (dto.stops != null) {
                        for (StopDto stop : dto.stops) {
                            PaletteRole role = roleByName(stop.role);
                            Color fixed = stop.color == null ? null : GbaColor.web(stop.color);
                            if (role != null || fixed != null) {
                                stops.add(new GradientStop(stop.position, role, fixed));
                            }
                        }
                    }
                    if (stops.size() < GradientLayer.MIN_STOPS) {
                        yield null;
                    }
                    yield new GradientLayer(style, GradientLayer.Kind.byName(dto.kind), dto.angle,
                            dto.centerX, dto.centerY, dto.radius <= 0 ? 1 : dto.radius,
                            stops.size() > GradientLayer.MAX_STOPS
                                    ? stops.subList(0, GradientLayer.MAX_STOPS) : stops,
                            dto.bands, dto.dither);
                }
                case "image" -> new ImageLayer(style, dto.fileName,
                        ImageLayer.Fit.byName(dto.fit), Math.max(1, dto.pixelScale), dto.animated);
                case "procedural" -> new ProceduralLayer(style,
                        ProceduralLayer.Pattern.byName(dto.pattern), dto.pixelScale, dto.seed);
                default -> null;
            };
        } catch (RuntimeException e) {
            // A layer this build does not understand, or one whose file name escapes the folder.
            // Dropped, and the rest of the mood loads.
            LOG.log(Level.WARNING, "Ignoring an unreadable layer of type \"" + dto.type + "\"", e);
            return null;
        }
    }

    private static PaletteRole roleByName(String name) {
        if (name == null) {
            return null;
        }
        for (PaletteRole role : PaletteRole.values()) {
            if (role.name().equalsIgnoreCase(name.strip())) {
                return role;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // On-disk shape
    // ------------------------------------------------------------------

    /** The mood file. Unknown fields are ignored so a file from a later build still loads. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MoodDto {
        public int version;
        public String id;
        public String name;
        public boolean reactive;
        public List<String> palette;
        public List<LayerDto> layers;
        public Map<String, TileDto> tiles;
    }

    /**
     * One layer.
     *
     * <p>A single shape with a {@code type} discriminator rather than a Jackson polymorphic
     * hierarchy: the file stays readable by a human, an unknown type is one {@code null} rather
     * than a deserialisation failure that takes the whole mood with it, and there is no annotation
     * whose absence silently changes the format.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LayerDto {
        public String type;
        public String zBand;
        public double opacity = 1;
        public String blend;
        public double scrollX;
        public double scrollY;
        public boolean visible = true;

        // Gradient
        public String kind;
        public double angle = 90;
        public double centerX = 0.5;
        public double centerY = 0.5;
        public double radius = 1;
        public int bands = GradientLayer.DEFAULT_BANDS;
        public boolean dither = true;
        public List<StopDto> stops;

        // Image
        public String fileName;
        public String fit;
        public boolean animated;

        // Image and procedural
        public int pixelScale = 1;

        // Procedural
        public String pattern;
        public long seed;
    }

    /** One gradient stop: a role or a colour, never both. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StopDto {
        public double position;
        public String role;
        public String color;
    }

    /** One tile, as palette indices: {@code size} rows of {@code size} hex digits, per frame. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TileDto {
        public int size = 16;
        public double fps = PixelTile.DEFAULT_FPS;
        public List<List<String>> frames;
    }
}
