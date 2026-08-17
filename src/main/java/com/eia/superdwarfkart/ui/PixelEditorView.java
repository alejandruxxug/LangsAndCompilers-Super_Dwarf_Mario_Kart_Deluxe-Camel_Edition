package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.ImageQuantizer;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.mood.PixelTile;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Draw a tile inside the application, in the mood's own sixteen colours.
 *
 * <p><strong>The tile stores palette indices, not colours</strong> - see {@link PixelTile} - and
 * every good property of this editor falls out of that rather than out of any code here. The colour
 * picker <em>is</em> the palette, so nothing drawn can be out of palette or off the GBA grid; and
 * changing the palette recolours everything ever drawn, instantly, which is the "oh" moment this
 * feature exists for.
 *
 * <p>Two of the tools matter more than they look:
 *
 * <ul>
 *   <li><strong>Symmetry</strong> mirrors strokes live. It halves the work on anything decorative
 *       and it is most of why a hand-drawn tile comes out looking deliberate rather than
 *       wobbly.</li>
 *   <li><strong>The tiled preview</strong> is a live 3x3 repeat beside the canvas. Almost every tile
 *       here becomes a layer with {@code fit: TILE}, and <em>a seam is invisible on one tile and
 *       glaring once it fills the screen</em>. Without this panel the user finds out after applying
 *       it, which is the worst moment to find out.</li>
 * </ul>
 *
 * <p>The surface is drawn with smoothing off at an integer zoom (ground rule 8), index 0 is shown as
 * the standard transparency checkerboard, and the grid is drawn in {@link PaletteRole#OUTLINE} at
 * low alpha so it follows the mood like everything else.
 */
public class PixelEditorView extends BorderPane {

    private static final Logger LOG = Logger.getLogger(PixelEditorView.class.getName());

    /** How many steps of undo are kept. The specification's floor is thirty. */
    private static final int UNDO_DEPTH = 40;

    /**
     * How wide the editing surface is, in pixels.
     *
     * <p>256, which is 16x for a 16x16 tile - the specification's own default zoom, and a whole
     * number for the other two sizes as well (32x at 8x8, 8x at 32x32). Never a fractional zoom:
     * this is hand-drawn pixel art and half a pixel of it is mush (ground rule 8).
     *
     * <p>It was 320 first, and that was measured to be wrong rather than merely large: the surface,
     * the tiled preview and the controls stack inside one column of the customizer, and at 320 the
     * preview fell below the fold. A tiled preview nobody can see is the one panel here that cannot
     * afford to be missed - a seam is invisible on one tile and glaring once it fills the screen.
     */
    private static final double SURFACE = 256;

    /** Side of one checkerboard square marking transparency, in screen pixels. */
    private static final int CHECKER = 8;

    /** How strongly the grid is drawn over the artwork. */
    private static final double GRID_ALPHA = 0.35;

    /** What a stroke does. */
    private enum Tool {
        PENCIL("PENCIL"), ERASER("ERASER"), FILL("FILL"), LINE("LINE"), RECT("RECT"), PICK("PICK");

        private final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    /** Which axes a stroke is mirrored across. */
    private enum Symmetry {
        NONE("NONE"), HORIZONTAL("MIRROR X"), VERTICAL("MIRROR Y"), BOTH("MIRROR XY");

        private final String label;

        Symmetry(String label) {
            this.label = label;
        }
    }

    private final Canvas surface = new Canvas(SURFACE, SURFACE);
    private final Canvas preview = new Canvas(SURFACE * 0.66, SURFACE * 0.66);
    private final HBox swatches = new HBox(2);
    private final Label status = new Label();
    private final Label frameLabel = new Label();

    private final Deque<PixelTile> undo = new ArrayDeque<>();
    private final Deque<PixelTile> redo = new ArrayDeque<>();

    private Palette palette = Palette.active();
    private PixelTile tile = PixelTile.blank(16);
    private int frame;
    private int selectedIndex = 5;
    private Tool tool = Tool.PENCIL;
    private Symmetry symmetry = Symmetry.NONE;
    private boolean gridOn = true;

    /** Where a drag started, for the two tools that need it. */
    private int anchorX = -1;
    private int anchorY = -1;
    private int hoverX = -1;
    private int hoverY = -1;

    private Consumer<PixelTile> onSaveToLayer;
    private Runnable onChanged;

    /** Builds the editor with a blank 16x16 tile. */
    public PixelEditorView() {
        getStyleClass().add("pixel-editor");
        setPadding(new Insets(12));

        surface.setOnMousePressed(this::pressed);
        surface.setOnMouseDragged(this::dragged);
        surface.setOnMouseReleased(this::released);
        surface.setOnMouseMoved(event -> {
            hoverX = cellX(event.getX());
            hoverY = cellY(event.getY());
        });
        surface.setOnMouseExited(event -> {
            hoverX = -1;
            hoverY = -1;
            redraw();
        });

        status.getStyleClass().add("panel-caption");
        frameLabel.getStyleClass().add("settings-value");

        VBox canvases = new VBox(10, surface, previewBlock());
        canvases.setAlignment(Pos.TOP_CENTER);

        setLeft(canvases);
        setCenter(controls());
        BorderPane.setMargin(canvases, new Insets(0, 16, 0, 0));

        rebuildSwatches();
        redraw();
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    /**
     * Installs the palette the indices resolve through.
     *
     * @param newPalette the palette; must not be {@code null}
     */
    public void setPalette(Palette newPalette) {
        this.palette = newPalette;
        rebuildSwatches();
        redraw();
    }

    /**
     * Loads a tile to edit.
     *
     * @param newTile the tile; {@code null} starts a blank 16x16
     */
    public void setTile(PixelTile newTile) {
        this.tile = newTile == null ? PixelTile.blank(16) : newTile;
        this.frame = 0;
        undo.clear();
        redo.clear();
        redraw();
    }

    /** @return the tile as it currently stands */
    public PixelTile tile() {
        return tile;
    }

    /**
     * Called when the user asks for the tile to become a layer.
     *
     * @param handler what to do with it
     */
    public void setOnSaveToLayer(Consumer<PixelTile> handler) {
        this.onSaveToLayer = handler;
    }

    /**
     * Called whenever the tile changes, so a customizer can keep a live preview.
     *
     * @param handler what to run
     */
    public void setOnChanged(Runnable handler) {
        this.onChanged = handler;
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    private VBox previewBlock() {
        Label caption = new Label("TILED 3x3");
        caption.getStyleClass().add("panel-heading");
        Tooltip.install(preview, new Tooltip(
                "A seam is invisible on one tile and unmissable once it fills the screen."));
        VBox box = new VBox(6, caption, preview);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private VBox controls() {
        Label heading = new Label("CREATE YOUR OWN");
        heading.getStyleClass().add("panel-heading");

        Label swatchCaption = new Label("The picker is the mood's palette - nothing\n"
                + "drawn here can be out of palette or off the grid.");
        swatchCaption.getStyleClass().add("panel-caption");

        VBox box = new VBox(10,
                heading,
                swatchCaption,
                swatches,
                toolRow(),
                optionRow(),
                frameRow(),
                fileRow(),
                status);
        box.setFillWidth(true);
        return box;
    }

    private FlowPane toolRow() {
        FlowPane row = new FlowPane(6, 6);
        ToggleGroup group = new ToggleGroup();
        for (Tool value : Tool.values()) {
            ToggleButton button = new ToggleButton(value.label);
            button.setToggleGroup(group);
            button.setFocusTraversable(false);
            button.setSelected(value == tool);
            button.setOnAction(event -> {
                tool = value;
                button.setSelected(true);
            });
            row.getChildren().add(button);
        }

        Button undoButton = action("UNDO", this::undo);
        Button redoButton = action("REDO", this::redo);
        Button clear = action("CLEAR", () -> {
            push();
            tile = tile.withFrame(frame, new int[tile.size() * tile.size()]);
            changed();
        });
        row.getChildren().addAll(undoButton, redoButton, clear);
        return row;
    }

    private FlowPane optionRow() {
        FlowPane row = new FlowPane(6, 6);

        ComboBox<Integer> size = new ComboBox<>();
        for (int value : PixelTile.SIZES) {
            size.getItems().add(value);
        }
        size.setValue(tile.size());
        size.setFocusTraversable(false);
        size.setPrefWidth(70);
        size.setOnAction(event -> resize(size.getValue()));
        Tooltip.install(size, new Tooltip("Tile size. Changing it starts a blank tile."));

        ComboBox<Symmetry> mirror = new ComboBox<>();
        mirror.getItems().addAll(Symmetry.values());
        mirror.setValue(symmetry);
        mirror.setFocusTraversable(false);
        mirror.setPrefWidth(120);
        mirror.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Symmetry value) {
                return value == null ? "" : value.label;
            }

            @Override
            public Symmetry fromString(String text) {
                return Symmetry.NONE;
            }
        });
        mirror.setOnAction(event -> symmetry = mirror.getValue());
        Tooltip.install(mirror, new Tooltip(
                "Mirrors strokes live. Halves the work on anything decorative."));

        ToggleButton grid = new ToggleButton("GRID");
        grid.setSelected(gridOn);
        grid.setFocusTraversable(false);
        grid.setOnAction(event -> {
            gridOn = grid.isSelected();
            redraw();
        });

        row.getChildren().addAll(size, mirror, grid);
        return row;
    }

    private FlowPane frameRow() {
        FlowPane row = new FlowPane(6, 6);

        Button add = action("+FRAME", () -> {
            push();
            tile = tile.withFrameAdded();
            frame = tile.frameCount() - 1;
            changed();
        });
        Button remove = action("-FRAME", () -> {
            push();
            tile = tile.withFrameRemoved();
            frame = Math.min(frame, tile.frameCount() - 1);
            changed();
        });
        Button step = action("NEXT", () -> {
            frame = (frame + 1) % tile.frameCount();
            redraw();
        });

        Spinner<Integer> fps = new Spinner<>(1, 12, (int) Math.round(tile.fps()));
        fps.setPrefWidth(72);
        fps.setFocusTraversable(false);
        fps.valueProperty().addListener((observable, was, now) -> {
            tile = tile.withFps(now);
            changed();
        });
        Tooltip.install(fps, new Tooltip("Frames per second"));

        row.getChildren().addAll(add, remove, step, fps, frameLabel);
        return row;
    }

    private FlowPane fileRow() {
        FlowPane row = new FlowPane(6, 6);
        row.getChildren().addAll(
                action("SAVE TO LAYER", () -> {
                    if (onSaveToLayer != null) {
                        onSaveToLayer.accept(tile);
                    }
                }),
                action("EXPORT PNG", this::exportPng),
                action("IMPORT PNG", this::importPng));
        return row;
    }

    private Button action(String label, Runnable handler) {
        Button button = new Button(label);
        button.setFocusTraversable(false);
        button.setOnAction(event -> handler.run());
        return button;
    }

    private void rebuildSwatches() {
        swatches.getChildren().clear();
        for (int index = 0; index < PaletteRole.COUNT; index++) {
            final int value = index;
            Region swatch = new Region();
            swatch.setMinSize(20, 20);
            swatch.setPrefSize(20, 20);
            swatch.setMaxSize(20, 20);
            PaletteRole role = PaletteRole.values()[index];
            Color color = palette.color(role);
            // Index 0 is the transparency key, so its swatch shows the checkerboard rather than
            // whatever colour the background role happens to be - a swatch that painted the
            // background colour would look like an ordinary colour that happened to match.
            String fill = index == PixelTile.TRANSPARENT
                    ? "-fx-background-color: -ui-inset;"
                    : "-fx-background-color: " + GbaColor.toHex(color) + ";";
            swatch.setStyle(fill + border(index));
            Tooltip.install(swatch, new Tooltip(index == PixelTile.TRANSPARENT
                    ? "0  transparent"
                    : index + "  " + role.displayName() + "\n" + GbaColor.toHex(color)));
            swatch.setOnMouseClicked(event -> {
                selectedIndex = value;
                rebuildSwatches();
            });
            swatches.getChildren().add(swatch);
        }
    }

    private String border(int index) {
        return index == selectedIndex
                ? "-fx-border-color: -role-highlight; -fx-border-width: 3;"
                : "-fx-border-color: -role-outline; -fx-border-width: 1;";
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    private void resize(int size) {
        if (size == tile.size()) {
            return;
        }
        push();
        tile = PixelTile.blank(size);
        frame = 0;
        changed();
    }

    private void pressed(MouseEvent event) {
        int x = cellX(event.getX());
        int y = cellY(event.getY());
        if (x < 0 || y < 0) {
            return;
        }
        if (tool == Tool.PICK) {
            selectedIndex = tile.indexAt(frame, x, y);
            rebuildSwatches();
            return;
        }
        push();
        anchorX = x;
        anchorY = y;
        switch (tool) {
            case PENCIL, ERASER -> paint(x, y);
            case FILL -> fill(x, y);
            default -> { }
        }
        changed();
    }

    private void dragged(MouseEvent event) {
        int x = cellX(event.getX());
        int y = cellY(event.getY());
        hoverX = x;
        hoverY = y;
        if (x < 0 || y < 0) {
            return;
        }
        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
            paint(x, y);
            changed();
        } else {
            // Line and rectangle show where they would land rather than committing per pixel.
            redraw();
        }
    }

    private void released(MouseEvent event) {
        int x = cellX(event.getX());
        int y = cellY(event.getY());
        if (anchorX >= 0 && x >= 0 && (tool == Tool.LINE || tool == Tool.RECT)) {
            if (tool == Tool.LINE) {
                line(anchorX, anchorY, x, y);
            } else {
                rectangle(anchorX, anchorY, x, y);
            }
            changed();
        }
        anchorX = -1;
        anchorY = -1;
    }

    /** Writes one pixel, and its mirrors. */
    private void paint(int x, int y) {
        int index = tool == Tool.ERASER ? PixelTile.TRANSPARENT : selectedIndex;
        for (int[] point : mirrors(x, y)) {
            tile = tile.withPixel(frame, point[0], point[1], index);
        }
    }

    /**
     * The points a stroke lands on, given the symmetry setting.
     *
     * <p>Mirrored about the tile's own centre line, which for an even size is <em>between</em> two
     * columns: a 16-pixel tile mirrors column 0 onto column 15. Mirroring about a column instead
     * would leave one duplicated line down the middle, which is the artefact that makes hand-drawn
     * symmetry look wrong without being obviously wrong.
     */
    private int[][] mirrors(int x, int y) {
        int last = tile.size() - 1;
        return switch (symmetry) {
            case NONE -> new int[][] {{x, y}};
            case HORIZONTAL -> new int[][] {{x, y}, {last - x, y}};
            case VERTICAL -> new int[][] {{x, y}, {x, last - y}};
            case BOTH -> new int[][] {{x, y}, {last - x, y}, {x, last - y}, {last - x, last - y}};
        };
    }

    private void line(int x0, int y0, int x1, int y1) {
        // Bresenham, so a diagonal is a connected run of pixels rather than a dotted one.
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1;
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            paint(x, y);
            if (x == x1 && y == y1) {
                return;
            }
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    private void rectangle(int x0, int y0, int x1, int y1) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        for (int x = left; x <= right; x++) {
            paint(x, top);
            paint(x, bottom);
        }
        for (int y = top; y <= bottom; y++) {
            paint(left, y);
            paint(right, y);
        }
    }

    /** Four-way flood fill, iterative so a full 32x32 tile cannot overflow the stack. */
    private void fill(int startX, int startY) {
        int target = tile.indexAt(frame, startX, startY);
        int replacement = tool == Tool.ERASER ? PixelTile.TRANSPARENT : selectedIndex;
        if (target == replacement) {
            return;
        }
        int size = tile.size();
        int[] pixels = tile.pixels(frame);
        Deque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[] {startX, startY});
        while (!pending.isEmpty()) {
            int[] point = pending.pop();
            int x = point[0];
            int y = point[1];
            if (x < 0 || y < 0 || x >= size || y >= size || pixels[y * size + x] != target) {
                continue;
            }
            pixels[y * size + x] = replacement;
            pending.push(new int[] {x + 1, y});
            pending.push(new int[] {x - 1, y});
            pending.push(new int[] {x, y + 1});
            pending.push(new int[] {x, y - 1});
        }
        tile = tile.withFrame(frame, pixels);
    }

    private void push() {
        undo.push(tile);
        while (undo.size() > UNDO_DEPTH) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty()) {
            return;
        }
        redo.push(tile);
        tile = undo.pop();
        frame = Math.min(frame, tile.frameCount() - 1);
        changed();
    }

    private void redo() {
        if (redo.isEmpty()) {
            return;
        }
        undo.push(tile);
        tile = redo.pop();
        frame = Math.min(frame, tile.frameCount() - 1);
        changed();
    }

    private void changed() {
        redraw();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    private void exportPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export tile");
        chooser.setInitialFileName("tile.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        try {
            // Both sizes, because the two have different uses and neither substitutes for the
            // other: 1:1 is the artwork, and the magnified copy is what anybody can actually look
            // at outside an editor.
            write(tile.toImage(frame, palette), target);
            Path magnified = target.resolveSibling(
                    stripExtension(target.getFileName().toString()) + "@8x.png");
            write(MoodOverlayRenderer.renderTile(tile, frame, palette, 8), magnified);
            status.setText("Exported " + target.getFileName() + " and " + magnified.getFileName());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not export the tile to " + target, e);
            status.setText("Could not write " + target.getFileName());
        }
    }

    private void importPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import a tile");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.gif", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Image image = new Image(file.toURI().toString());
            if (image.isError()) {
                status.setText("That file could not be read as an image");
                return;
            }
            push();
            tile = tile.withFrame(frame,
                    ImageQuantizer.toTileIndices(image, tile.size(), palette));
            status.setText("Imported " + file.getName() + ", quantised to the mood's sixteen");
            changed();
        } catch (IllegalArgumentException e) {
            // The size refusal, which is deliberate: see ImageQuantizer.toTileIndices.
            status.setText(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not import " + file, e);
            status.setText("Could not import that image");
        }
    }

    private static void write(Image image, Path target) throws IOException {
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", target.toFile());
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private double zoom() {
        return SURFACE / tile.size();
    }

    private int cellX(double x) {
        int cell = (int) (x / zoom());
        return cell >= 0 && cell < tile.size() ? cell : -1;
    }

    private int cellY(double y) {
        int cell = (int) (y / zoom());
        return cell >= 0 && cell < tile.size() ? cell : -1;
    }

    /** Repaints the editing surface and the tiled preview. */
    public void redraw() {
        frameLabel.setText("FRAME " + (frame + 1) + "/" + tile.frameCount());

        GraphicsContext gc = surface.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        double zoom = zoom();

        // The transparency checkerboard, which is the convention and is also the only way to tell
        // "index 0" from "the background colour" at a glance.
        for (int y = 0; y < SURFACE; y += CHECKER) {
            for (int x = 0; x < SURFACE; x += CHECKER) {
                boolean light = ((x / CHECKER) + (y / CHECKER)) % 2 == 0;
                gc.setFill(light
                        ? palette.color(PaletteRole.SURFACE)
                        : palette.color(PaletteRole.SURFACE_RAISED));
                gc.fillRect(x, y, CHECKER, CHECKER);
            }
        }

        for (int y = 0; y < tile.size(); y++) {
            for (int x = 0; x < tile.size(); x++) {
                int index = tile.indexAt(frame, x, y);
                if (index == PixelTile.TRANSPARENT) {
                    continue;
                }
                gc.setFill(palette.color(PaletteRole.values()[index]));
                gc.fillRect(Math.round(x * zoom), Math.round(y * zoom),
                        Math.ceil(zoom), Math.ceil(zoom));
            }
        }

        if (gridOn) {
            gc.setStroke(palette.color(PaletteRole.OUTLINE, GRID_ALPHA));
            gc.setLineWidth(1);
            for (int step = 0; step <= tile.size(); step++) {
                double at = Math.round(step * zoom) + 0.5;
                gc.strokeLine(at, 0, at, SURFACE);
                gc.strokeLine(0, at, SURFACE, at);
            }
        }

        // Where a line or a rectangle would land, and where the pointer is. Drawn in HIGHLIGHT,
        // which is a protected role precisely so that a cursor cannot vanish into the artwork.
        if (hoverX >= 0 && hoverY >= 0) {
            gc.setStroke(palette.color(PaletteRole.HIGHLIGHT));
            gc.setLineWidth(2);
            if (anchorX >= 0 && (tool == Tool.LINE || tool == Tool.RECT)) {
                double left = Math.min(anchorX, hoverX) * zoom;
                double top = Math.min(anchorY, hoverY) * zoom;
                double width = (Math.abs(hoverX - anchorX) + 1) * zoom;
                double height = (Math.abs(hoverY - anchorY) + 1) * zoom;
                gc.strokeRect(left + 1, top + 1, width - 2, height - 2);
            } else {
                gc.strokeRect(hoverX * zoom + 1, hoverY * zoom + 1, zoom - 2, zoom - 2);
            }
        }

        drawPreview();
    }

    private void drawPreview() {
        GraphicsContext gc = preview.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        double width = preview.getWidth();
        double height = preview.getHeight();
        gc.setFill(palette.color(PaletteRole.BACKGROUND));
        gc.fillRect(0, 0, width, height);

        Image image = tile.toImage(frame, palette);
        double side = width / 3;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                gc.drawImage(image, Math.round(x * side), Math.round(y * side),
                        Math.ceil(side), Math.ceil(side));
            }
        }
        gc.setStroke(palette.color(PaletteRole.OUTLINE));
        gc.setLineWidth(2);
        gc.strokeRect(1, 1, width - 2, height - 2);
    }
}
