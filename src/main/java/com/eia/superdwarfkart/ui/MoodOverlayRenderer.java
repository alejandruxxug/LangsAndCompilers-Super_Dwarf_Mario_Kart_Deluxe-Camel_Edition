package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.mood.Bayer;
import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.GradientLayer;
import com.eia.superdwarfkart.mood.ImageLayer;
import com.eia.superdwarfkart.mood.LayerBlend;
import com.eia.superdwarfkart.mood.LayerStyle;
import com.eia.superdwarfkart.mood.Mood;
import com.eia.superdwarfkart.mood.MoodLayer;
import com.eia.superdwarfkart.mood.MoodReactivity;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.mood.PixelTile;
import com.eia.superdwarfkart.mood.ProceduralLayer;
import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Draws a mood's overlay layers around whatever the fullscreen window is showing.
 *
 * <p>One canvas underneath the content and one over it, with the interface sandwiched between them.
 * The pane is what {@code shell.setCenter} holds, so the layers reach the library, the runner, the
 * structure visualiser and presentation mode without any of them knowing this exists.
 *
 * <h2>Performance, which is the whole design</h2>
 *
 * <p>This runs over a 60 fps canvas on a machine whose Prism pipeline falls back to <strong>software
 * rasterisation</strong> - measured, and recorded in the project's own notes: a full-canvas alpha
 * fill at the maximised window size costs about ten milliseconds. So the rules are not advice.
 *
 * <ul>
 *   <li><strong>Every static layer is flattened into one cached image</strong> on mood change or
 *       resize. Per frame the backdrop blits one picture, not six.</li>
 *   <li><strong>A mood whose layers are all static runs no timer at all.</strong> The canvases are
 *       painted once and never touched again, so the eight presets that do not scroll cost exactly
 *       nothing while the game is running. This is why {@link Mood#needsAnimation()} exists.</li>
 *   <li><strong>Images are scaled once, at rebuild.</strong> Nothing is rescaled in the render
 *       loop.</li>
 *   <li>Gradients and procedural patterns are rasterised into an {@code int[]} of ARGB and written
 *       in one call, with the colour computed <em>per band</em> rather than per pixel - a banded
 *       gradient has only as many distinct colours as it has bands.</li>
 * </ul>
 *
 * <h2>What blending can and cannot do here</h2>
 *
 * <p>Layers blend with each other on their own canvas, through
 * {@link GraphicsContext#setGlobalBlendMode}. A layer <em>above</em> the content additionally has to
 * combine with the interface underneath it, and that is a scene-graph composite rather than a canvas
 * one - so the overlay canvas takes the blend mode of the layers above the content when they all
 * agree, and composites normally when they do not. It is the honest answer: the alternative is one
 * canvas per layer, which is one full-canvas composite per layer per frame, which is the cost this
 * class exists to avoid.
 */
public class MoodOverlayRenderer extends StackPane {

    private static final Logger LOG = Logger.getLogger(MoodOverlayRenderer.class.getName());

    /** How many steps a smooth gradient is rasterised in. Beyond this the banding is invisible. */
    private static final int SMOOTH_STEPS = 128;

    /** Roughly one cell in this many carries a star. */
    private static final int STARFIELD_DENSITY = 26;

    /**
     * How much smaller than the canvas a repeating picture has to be before pre-tiling it pays.
     *
     * <p>Sixteen times, by area. Below that the pre-tiled patch is a whole extra canvas of memory to
     * save a handful of draws; above it, it is one draw instead of several hundred.
     */
    private static final int PRE_TILE_AREA_RATIO = 16;

    private final Canvas backdrop = new Canvas();
    private final StackPane content = new StackPane();
    private final Canvas overlay = new Canvas();

    private final MoodReactivity reactivity = new MoodReactivity();
    private final Map<String, Image> imageCache = new HashMap<>();

    private Mood mood;
    private Path moodFolder;
    private Palette basePalette = Palette.active();

    private Image flattenedBehind;
    private Image flattenedAbove;
    private final List<Live> liveBehind = new ArrayList<>();
    private final List<Live> liveAbove = new ArrayList<>();

    private AnimationTimer timer;
    private boolean running;
    private boolean reduceMotion;
    private double safeLift;
    private MusicFeed feed;

    /** The clock a scrolling layer moves on, in nanoseconds, so it survives a stop and a start. */
    private long startedAt;
    private double bankedSeconds;

    /**
     * What the renderer needs from the audio path in order to react to the music.
     *
     * <p>Three plain numbers rather than a dependency on {@code audio/} or {@code analysis/}: this
     * class is a view, and a view that reached into the playback engine to find a beat would be a
     * second place that knew how the beat is found.
     */
    public interface MusicFeed {

        /** @return the playback clock, in seconds */
        double seconds();

        /** @return the louder channel's RMS, 0 to 1 */
        double level();

        /** @return how recently a strong beat landed: 1 at the strike, 0 by the next one */
        double beat();
    }

    /**
     * A layer that has to be redrawn every frame, with its picture already rasterised.
     *
     * <p>A drifting tiled layer's picture is <strong>pre-tiled one repeat larger than the canvas</strong>
     * rather than kept at tile size, so a frame is a single blit from a source rectangle instead of
     * several hundred small ones. That is not a micro-optimisation here: measured on this machine,
     * where Prism falls back to software rasterisation, the tiled version of Sky Garden's clouds cost
     * <em>10.7 ms a frame</em> and the pre-tiled version costs a fraction of that. The pixel count is
     * identical; what goes away is the per-call setup, several hundred times a frame.
     *
     * <p>It is only worth doing for a picture much smaller than the canvas. A gradient or a
     * procedural pattern is rasterised at canvas size already, so pre-tiling one would allocate four
     * canvases' worth of image to save three blits - which is the wrong trade in the other
     * direction. Those wrap with a handful of draws instead.
     *
     * @param layer   the definition
     * @param image   the rasterised picture, pre-tiled when that is worth doing
     * @param periodX the repeat width, or 0 when the picture is drawn once
     * @param periodY the repeat height, or 0 when the picture is drawn once
     * @param patched whether {@code image} is the pre-tiled patch rather than one repeat
     */
    private record Live(MoodLayer layer, Image image, double periodX, double periodY,
            boolean patched) { }

    /** Builds an empty renderer. Nothing draws until a mood is installed. */
    public MoodOverlayRenderer() {
        getStyleClass().add("mood-overlay");
        // A canvas is picked over its whole rectangle whatever it has drawn, and both of these span
        // the window. Left pickable, the overlay would swallow every click in the application while
        // still hovering correctly - the exact fault that ate the companion window's transport.
        backdrop.setMouseTransparent(true);
        overlay.setMouseTransparent(true);
        // And neither may take part in the layout, which is a separate rule from the one above and
        // was learned the harder way. A StackPane's minimum size is the largest of its children's,
        // and a Canvas is not resizable - it reports its own width as its minimum. So two canvases
        // sized *to* this pane make the pane's minimum whatever the pane last was, and this pane is
        // the whole middle of the window: it could grow and then never shrink again. Restoring a
        // maximised window left every view still laid out at the maximised size with the window's
        // edge cutting through it, which reads as the interface failing to redraw rather than as a
        // minimum nothing can go under. Unmanaged, they are excluded from the minimum and from the
        // layout pass; they still sit at (0,0) spanning the pane, because that is where a child
        // nobody positions stays, and resized() is what gives them their size.
        backdrop.setManaged(false);
        overlay.setManaged(false);

        getChildren().addAll(backdrop, content, overlay);

        widthProperty().addListener((observable, was, now) -> resized());
        heightProperty().addListener((observable, was, now) -> resized());
    }

    /**
     * Puts a node between the two canvases.
     *
     * @param node what the window is showing; {@code null} clears it
     */
    public void setContent(Node node) {
        if (node == null) {
            content.getChildren().clear();
        } else {
            content.getChildren().setAll(node);
        }
    }

    /** @return the node currently between the canvases, or {@code null} */
    public Node getContent() {
        return content.getChildren().isEmpty() ? null : content.getChildren().get(0);
    }

    /**
     * Installs a mood: rasterises its layers, flattens the static ones and starts or stops the
     * frame loop according to whether anything actually moves.
     *
     * @param newMood    the mood; {@code null} clears the layers and leaves the base backdrop
     * @param folder     where the mood's imported artwork lives; may be {@code null}
     */
    public void setMood(Mood newMood, Path folder) {
        this.mood = newMood;
        this.moodFolder = folder;
        this.basePalette = newMood == null ? Palette.active() : newMood.palette();
        this.safeLift = newMood != null && newMood.reactive()
                ? MoodReactivity.safeLift(basePalette)
                : 0;
        imageCache.clear();
        reactivity.reset();
        rebuild();
    }

    /**
     * Turns every kind of motion off.
     *
     * <p>Layer scrolling and reactivity both, because a fullscreen overlay drifting or flashing in a
     * darkened classroom is a genuine problem rather than a style question. The runner's own beat
     * effects are wired to the same switch - see {@code RunnerView.setReduceMotion}.
     *
     * @param on whether to suppress motion
     */
    public void setReduceMotion(boolean on) {
        if (reduceMotion == on) {
            return;
        }
        reduceMotion = on;
        reactivity.reset();
        // A reactive palette may be installed at the moment this is switched on, so the base has to
        // be put back explicitly rather than waited for.
        Palette.setActive(basePalette);
        rebuild();
    }

    /** @return whether motion is suppressed */
    public boolean isReduceMotion() {
        return reduceMotion;
    }

    /**
     * Supplies the numbers a reactive mood responds to.
     *
     * @param musicFeed the feed; {@code null} turns reactivity off however the mood is configured
     */
    public void setMusicFeed(MusicFeed musicFeed) {
        this.feed = musicFeed;
    }

    /** Starts the frame loop, if this mood needs one. */
    public void start() {
        running = true;
        startTimerIfNeeded();
    }

    /**
     * Stops the frame loop.
     *
     * <p>Called when the window is put behind the companion strip and when the boot screen is up.
     * An {@code AnimationTimer} does not stop because the node it paints cannot be seen - the same
     * fault the companion window, the {@code F4} fold and the boot screen each had to fix.
     */
    public void stop() {
        running = false;
        if (timer != null) {
            timer.stop();
            timer = null;
            bankedSeconds = elapsedSeconds();
        }
    }

    /** @return whether the frame loop is running */
    public boolean isRunning() {
        return timer != null;
    }

    /** @return how many layers are redrawn per frame; 0 means the canvases are never repainted */
    public int liveLayerCount() {
        return liveBehind.size() + liveAbove.size();
    }

    /** @return whether every layer was flattened into the cached backdrop */
    public boolean isFullyFlattened() {
        return liveLayerCount() == 0;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void resized() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        backdrop.setWidth(width);
        backdrop.setHeight(height);
        overlay.setWidth(width);
        overlay.setHeight(height);
        rebuild();
    }

    /**
     * Rasterises everything and decides whether a frame loop is needed at all.
     *
     * <p>The one expensive call in this class, and it happens on a mood change or a resize rather
     * than per frame. Everything after it is blitting.
     */
    public void rebuild() {
        double width = backdrop.getWidth();
        double height = backdrop.getHeight();
        flattenedBehind = null;
        flattenedAbove = null;
        liveBehind.clear();
        liveAbove.clear();
        if (width <= 0 || height <= 0) {
            return;
        }

        List<MoodLayer> layers = mood == null ? List.of() : mood.visibleLayers();
        flattenedBehind = compile(layers, com.eia.superdwarfkart.mood.ZBand.BEHIND_CONTENT,
                width, height, liveBehind, true);
        flattenedAbove = compile(layers, com.eia.superdwarfkart.mood.ZBand.ABOVE_CONTENT,
                width, height, liveAbove, false);

        overlay.setBlendMode(agreedBlend(layers));

        drawBackdrop();
        drawOverlay();
        startTimerIfNeeded();
    }

    /**
     * Rasterises one band's layers, flattening the static ones into a single image and handing back
     * whichever have to be redrawn per frame.
     *
     * <p>A run of static layers is only flattenable while the blend mode stays {@link LayerBlend#NORMAL}:
     * source-over compositing of opaque images collapses, and anything else does not. A mood that
     * multiplies one layer over another therefore keeps both live, which is correct and is also the
     * mood that costs the most - which is the right way round.
     *
     * @param layers    every visible layer, in order
     * @param band      which band to compile
     * @param width     canvas width
     * @param height    canvas height
     * @param live      filled with the layers that must be redrawn per frame
     * @param withBase  whether to paint the interface's own backdrop underneath
     * @return the flattened image, or {@code null} when there was nothing static to flatten
     */
    private Image compile(List<MoodLayer> layers, com.eia.superdwarfkart.mood.ZBand band,
            double width, double height, List<Live> live, boolean withBase) {
        Canvas scratch = new Canvas(width, height);
        GraphicsContext gc = scratch.getGraphicsContext2D();
        gc.setImageSmoothing(false);

        boolean anythingFlattened = withBase;
        if (withBase) {
            paintBase(gc, width, height);
        }

        for (MoodLayer layer : layers) {
            if (layer.style().zBand() != band) {
                continue;
            }
            Image image = rasterize(layer, width, height);
            if (image == null) {
                continue;
            }
            boolean tiled = isTiled(layer);
            boolean movesNow = layer.isLive() && !reduceMotion;
            boolean reacts = mood != null && mood.reactive() && !reduceMotion;
            if (movesNow || reacts || layer.style().blend() != LayerBlend.NORMAL) {
                // Pre-tiled here, once, rather than repeated per frame - see Live.
                live.add(liveEntry(layer, image, tiled, width, height));
                continue;
            }
            drawTiling(gc, layer, image, tiled, width, height, layer.style().opacity());
            anythingFlattened = true;
        }

        if (!anythingFlattened) {
            return null;
        }
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return scratch.snapshot(parameters, new WritableImage((int) width, (int) height));
    }

    /**
     * Paints the ground the interface used to paint for itself.
     *
     * <p>{@code .root-pane} carried this gradient until the overlays existed, and it had to give it
     * up: a layer behind the content is behind an opaque pane, which is a layer nobody can see. So
     * the pane is transparent and this draws the same three-stop ramp, which means a mood with no
     * layers at all comes out exactly as it did before.
     */
    private void paintBase(GraphicsContext gc, double width, double height) {
        Palette palette = basePalette;
        gc.setFill(new javafx.scene.paint.LinearGradient(0, 0, 0, height, false,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, palette.color(PaletteRole.BACKGROUND)),
                new javafx.scene.paint.Stop(0.6, palette.color(PaletteRole.BACKGROUND_ALT)),
                new javafx.scene.paint.Stop(1,
                        palette.mix(PaletteRole.SURFACE, PaletteRole.SURFACE_RAISED, 0.6))));
        gc.fillRect(0, 0, width, height);
    }

    /**
     * The blend mode the overlay canvas composites onto the interface with.
     *
     * @param layers every visible layer
     * @return the shared blend of the layers above the content, or {@code null} for source-over
     */
    private BlendMode agreedBlend(List<MoodLayer> layers) {
        LayerBlend agreed = null;
        for (MoodLayer layer : layers) {
            if (layer.style().zBand() != com.eia.superdwarfkart.mood.ZBand.ABOVE_CONTENT) {
                continue;
            }
            if (agreed == null) {
                agreed = layer.style().blend();
            } else if (agreed != layer.style().blend()) {
                return null;
            }
        }
        return agreed == null ? null : toBlendMode(agreed);
    }

    private void startTimerIfNeeded() {
        boolean wanted = running && (liveLayerCount() > 0
                || (mood != null && mood.reactive() && !reduceMotion && feed != null));
        if (wanted == (timer != null)) {
            return;
        }
        if (!wanted) {
            stopTimer();
            return;
        }
        startedAt = System.nanoTime();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        };
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) {
            bankedSeconds = elapsedSeconds();
            timer.stop();
            timer = null;
        }
    }

    private double elapsedSeconds() {
        return timer == null
                ? bankedSeconds
                : bankedSeconds + (System.nanoTime() - startedAt) / 1_000_000_000d;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Repaints both canvases from the images already rasterised.
     *
     * <p>What a frame actually costs, as opposed to what {@link #rebuild()} costs. The two are three
     * orders of magnitude apart - a rebuild rasterises a full-canvas gradient pixel by pixel, and a
     * frame blits pictures that already exist - so measuring the wrong one would report this system
     * as unusably expensive when it is nearly free. That is exactly the mistake the project's own
     * notes record about measuring the runner by timing {@code redraw()}.
     */
    public void repaint() {
        if (!liveBehind.isEmpty() || flattenedBehind != null) {
            drawBackdrop();
        }
        if (!liveAbove.isEmpty() || flattenedAbove != null) {
            drawOverlay();
        }
    }

    private void tick() {
        if (mood != null && mood.reactive() && !reduceMotion && feed != null && safeLift > 0
                && reactivity.update(feed.seconds(), feed.level(), feed.beat())) {
            // A modulated palette rather than a modulated stylesheet: the canvases read the active
            // palette on every repaint, so this reaches the road, the meters and the tree for free,
            // and the controls are left alone - which is right both for the framerate and for the
            // look, since a table whose headings pulse is a fault rather than a mood.
            Palette.setActive(MoodReactivity.modulate(basePalette, safeLift, reactivity.energy()));
        }
        if (!liveBehind.isEmpty()) {
            drawBackdrop();
        }
        if (!liveAbove.isEmpty()) {
            drawOverlay();
        }
    }

    private void drawBackdrop() {
        paint(backdrop, flattenedBehind, liveBehind);
    }

    private void drawOverlay() {
        paint(overlay, flattenedAbove, liveAbove);
    }

    private void paint(Canvas canvas, Image flattened, List<Live> live) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        gc.setGlobalAlpha(1);
        gc.clearRect(0, 0, width, height);

        if (flattened != null) {
            gc.drawImage(flattened, 0, 0);
        }

        double seconds = reduceMotion ? 0 : elapsedSeconds();
        double energy = reactivity.energy();
        for (Live entry : live) {
            LayerStyle style = entry.layer().style();
            double offsetX = reduceMotion ? 0 : style.scrollX() * seconds;
            double offsetY = reduceMotion ? 0 : style.scrollY() * seconds;
            // Reactivity reaches a layer through its opacity, which is one of the two quantities it
            // is allowed to touch. Never past the band's own ceiling: an ABOVE_CONTENT layer that
            // could be driven past 0.35 by a loud passage would bury the game on exactly the bars
            // the player most needs to see it.
            double opacity = Math.clamp(
                    style.opacity() * (1 + 0.4 * energy * (mood != null && mood.reactive() ? 1 : 0)),
                    0d, style.zBand().maxOpacity());
            blit(gc, entry, offsetX, offsetY, width, height, opacity);
        }
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        gc.setGlobalAlpha(1);
    }

    /**
     * Blits one live layer, in a single call.
     *
     * <p>A drifting tiled layer's picture was pre-tiled at rebuild to one repeat larger than the
     * canvas, so any scroll offset is a source rectangle inside it rather than a grid of small
     * draws. Everything else is one blit at the offset.
     */
    private void blit(GraphicsContext gc, Live entry, double offsetX, double offsetY,
            double width, double height, double opacity) {
        gc.setGlobalAlpha(Math.clamp(opacity, 0d, 1d));
        gc.setGlobalBlendMode(toBlendMode(entry.layer().style().blend()));

        Image image = entry.image();
        if (entry.periodX() <= 0 || entry.periodY() <= 0) {
            double drawWidth = image.getWidth();
            double drawHeight = image.getHeight();
            double x = placement(entry.layer(), width, drawWidth) + offsetX;
            double y = placement(entry.layer(), height, drawHeight) + offsetY;
            gc.drawImage(image, Math.round(x), Math.round(y), Math.round(drawWidth),
                    Math.round(drawHeight));
        } else if (entry.patched()) {
            // Whole pixels: a tile started on a half pixel is a tile with a seam down every repeat,
            // which is invisible on one and glaring across a screen.
            double sourceX = mod(Math.round(offsetX), entry.periodX());
            double sourceY = mod(Math.round(offsetY), entry.periodY());
            gc.drawImage(image, sourceX, sourceY, width, height, 0, 0, width, height);
        } else {
            // A picture about as big as the canvas: at most two by two draws from a wrapped origin,
            // which is cheaper than holding four canvases of pre-tiled copy to save three of them.
            double originX = -mod(Math.round(offsetX), entry.periodX());
            double originY = -mod(Math.round(offsetY), entry.periodY());
            for (double y = originY; y < height; y += entry.periodY()) {
                for (double x = originX; x < width; x += entry.periodX()) {
                    gc.drawImage(image, x, y, entry.periodX(), entry.periodY());
                }
            }
        }

        gc.setGlobalAlpha(1);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    /**
     * Draws a layer by repeating it across the canvas, for the flattening pass.
     *
     * <p>The slow way, deliberately: this runs once when a mood is installed, where {@link #blit}
     * runs sixty times a second.
     */
    private void drawTiling(GraphicsContext gc, MoodLayer layer, Image image, boolean tiled,
            double width, double height, double opacity) {
        gc.setGlobalAlpha(Math.clamp(opacity, 0d, 1d));
        gc.setGlobalBlendMode(toBlendMode(layer.style().blend()));

        if (!tiled) {
            double drawWidth = image.getWidth();
            double drawHeight = image.getHeight();
            gc.drawImage(image, placement(layer, width, drawWidth),
                    placement(layer, height, drawHeight),
                    Math.round(drawWidth), Math.round(drawHeight));
        } else {
            double tileWidth = Math.max(1, image.getWidth());
            double tileHeight = Math.max(1, image.getHeight());
            for (double y = 0; y < height; y += tileHeight) {
                for (double x = 0; x < width; x += tileWidth) {
                    gc.drawImage(image, x, y, tileWidth, tileHeight);
                }
            }
        }
        gc.setGlobalAlpha(1);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    /**
     * Decides how a live layer will be drawn, and pre-tiles it when that is worth doing.
     *
     * <p>The threshold is area rather than a dimension: pre-tiling costs one image of about the
     * canvas's size, and it is worth it when it turns hundreds of draws into one. A picture already
     * covering a sixteenth of the canvas or more would need at most a handful of draws anyway.
     *
     * @param layer  the definition
     * @param image  its rasterised picture
     * @param tiled  whether it repeats
     * @param width  canvas width
     * @param height canvas height
     * @return the live entry
     */
    private Live liveEntry(MoodLayer layer, Image image, boolean tiled, double width,
            double height) {
        if (!tiled) {
            return new Live(layer, image, 0, 0, false);
        }
        double tileWidth = Math.max(1, image.getWidth());
        double tileHeight = Math.max(1, image.getHeight());
        boolean worthPatching = tileWidth * tileHeight * PRE_TILE_AREA_RATIO <= width * height;
        return worthPatching
                ? new Live(layer, preTile(image, width, height), tileWidth, tileHeight, true)
                : new Live(layer, image, tileWidth, tileHeight, false);
    }

    /**
     * Repeats a tile into a picture one whole repeat larger than the canvas.
     *
     * <p>Which is what makes a scrolling tiled layer cost one blit instead of several hundred: any
     * offset in {@code [0, tileWidth)} then names a source rectangle inside this picture that covers
     * the canvas exactly. Built once per rebuild; the memory is one canvas-sized image, which is the
     * same order as the flattened backdrop already held.
     *
     * @param tile   the tile to repeat
     * @param width  canvas width
     * @param height canvas height
     * @return the pre-tiled picture
     */
    private Image preTile(Image tile, double width, double height) {
        double tileWidth = Math.max(1, tile.getWidth());
        double tileHeight = Math.max(1, tile.getHeight());
        int patchWidth = (int) Math.ceil(width + tileWidth);
        int patchHeight = (int) Math.ceil(height + tileHeight);

        Canvas scratch = new Canvas(patchWidth, patchHeight);
        GraphicsContext gc = scratch.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        for (double y = 0; y < patchHeight; y += tileHeight) {
            for (double x = 0; x < patchWidth; x += tileWidth) {
                gc.drawImage(tile, x, y, tileWidth, tileHeight);
            }
        }
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return scratch.snapshot(parameters, new WritableImage(patchWidth, patchHeight));
    }

    private static double mod(double value, double span) {
        double result = value % span;
        return result < 0 ? result + span : result;
    }

    /** Where a non-tiled image sits: centred for every fit that does not fill the canvas. */
    private static double placement(MoodLayer layer, double canvas, double drawn) {
        if (layer instanceof ImageLayer image && image.fit() == ImageLayer.Fit.STRETCH) {
            return 0;
        }
        return Math.round((canvas - drawn) / 2);
    }

    private static boolean isTiled(MoodLayer layer) {
        return !(layer instanceof ImageLayer image) || image.fit() == ImageLayer.Fit.TILE;
    }

    private static BlendMode toBlendMode(LayerBlend blend) {
        return switch (blend) {
            case NORMAL -> BlendMode.SRC_OVER;
            case MULTIPLY -> BlendMode.MULTIPLY;
            case SCREEN -> BlendMode.SCREEN;
            case OVERLAY -> BlendMode.OVERLAY;
            case ADD -> BlendMode.ADD;
        };
    }

    // ------------------------------------------------------------------
    // Rasterising
    // ------------------------------------------------------------------

    /**
     * Turns a layer definition into a picture, once.
     *
     * @param layer  the layer
     * @param width  canvas width
     * @param height canvas height
     * @return the picture, or {@code null} when there is nothing to draw
     */
    private Image rasterize(MoodLayer layer, double width, double height) {
        return switch (layer) {
            case GradientLayer gradient -> rasterizeGradient(gradient, (int) width, (int) height);
            case ProceduralLayer procedural ->
                    rasterizeProcedural(procedural, (int) width, (int) height);
            case ImageLayer image -> resolveImage(image, width, height);
        };
    }

    /**
     * Rasterises a gradient into an ARGB buffer.
     *
     * <p>The colour is computed <strong>per band</strong> rather than per pixel: a gradient cut into
     * eight steps holds eight colours however many million pixels it covers. With dithering there
     * are sixteen variants of each, one per {@link Bayer} threshold, and both tables are built
     * before the loop starts. What remains per pixel is a multiply, a floor and an array read.
     */
    private Image rasterizeGradient(GradientLayer layer, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        int steps = layer.bands() > 0 ? layer.bands() : SMOOTH_STEPS;
        boolean dither = layer.bands() > 0 && layer.dither();

        // One colour per band. This is the whole optimisation: a gradient cut into eight steps holds
        // eight colours however many million pixels it covers, and each one costs an interpolation
        // and a snap back onto the 5-bit grid that would otherwise be paid per pixel.
        int[] bandArgb = new int[steps];
        for (int band = 0; band < steps; band++) {
            double position = steps == 1 ? 0 : band / (double) (steps - 1);
            bandArgb[band] = toArgb(layer.sample(position, basePalette));
        }

        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double scaled = layer.positionAt(x, y, width, height) * steps;
                if (dither) {
                    // Centred on zero, so the dither pushes a pixel either way rather than always
                    // later - which keeps the banded ramp's average position the same as the smooth
                    // one it replaces. Identical arithmetic to GradientLayer.colorAt, inlined here
                    // because that method allocates a Color and this loop runs a million times.
                    scaled += Bayer.threshold(x, y) - 0.5;
                }
                argb[y * width + x] =
                        bandArgb[Math.clamp((int) Math.floor(scaled), 0, steps - 1)];
            }
        }
        return fromArgb(argb, width, height);
    }

    /**
     * Rasterises one of the four procedural patterns into an ARGB buffer.
     *
     * <p>Each is drawn at full strength; the layer's own opacity is applied at blit time, so the
     * same picture serves a reactive mood whose opacity moves every third of a second.
     */
    private Image rasterizeProcedural(ProceduralLayer layer, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        int[] argb = new int[width * height];
        int ink = toArgb(basePalette.color(layer.role()));
        int scale = Math.max(2, layer.pixelScale());

        switch (layer.pattern()) {
            case SCANLINES -> {
                for (int y = 0; y < height; y++) {
                    if (y % scale != 0) {
                        continue;
                    }
                    java.util.Arrays.fill(argb, y * width, y * width + width, ink);
                }
            }
            case LCD_GRID -> {
                for (int y = 0; y < height; y++) {
                    boolean row = y % scale == 0;
                    for (int x = 0; x < width; x++) {
                        if (row || x % scale == 0) {
                            argb[y * width + x] = ink;
                        }
                    }
                }
            }
            case VIGNETTE -> {
                double centreX = width / 2d;
                double centreY = height / 2d;
                double reach = Math.hypot(centreX, centreY);
                int rgb = ink & 0x00FFFFFF;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        double distance = Math.hypot(x - centreX, y - centreY) / reach;
                        // Squared, so the middle of the screen - where the road, the table and the
                        // tree all are - is left alone and only the corners darken.
                        double strength = Math.clamp(distance * distance, 0d, 1d);
                        argb[y * width + x] = ((int) (strength * 255) << 24) | rgb;
                    }
                }
            }
            case STARFIELD -> {
                int cells = Math.max(2, scale);
                int rgb = ink & 0x00FFFFFF;
                for (int cellY = 0; cellY * cells < height; cellY++) {
                    for (int cellX = 0; cellX * cells < width; cellX++) {
                        if (!layer.hasStar(cellX, cellY, STARFIELD_DENSITY)) {
                            continue;
                        }
                        int alpha = (int) (layer.starBrightness(cellX, cellY) * 255);
                        int x = cellX * cells + cells / 2;
                        int y = cellY * cells + cells / 2;
                        if (x < width && y < height) {
                            argb[y * width + x] = (alpha << 24) | rgb;
                        }
                    }
                }
            }
        }
        return fromArgb(argb, width, height);
    }

    /**
     * Finds an image layer's picture: a tile drawn in the editor, or a file in the mood's folder.
     *
     * <p>Tiles are looked up first, and that ordering is the feature: a tile holds palette
     * <em>indices</em>, so it is re-rendered against the current palette here and recolours itself
     * whenever the mood does. A file cannot.
     *
     * <p>A missing file draws the magenta placeholder rather than throwing, exactly as a missing
     * sprite does (ground rule 5).
     */
    private Image resolveImage(ImageLayer layer, double width, double height) {
        String key = layer.fileName() + "@" + layer.pixelScale() + ":" + layer.fit()
                + ":" + (int) width + "x" + (int) height;
        Image cached = imageCache.get(key);
        if (cached != null) {
            return cached;
        }

        Image source = null;
        PixelTile tile = mood == null ? null : mood.tile(layer.fileName());
        if (tile != null) {
            source = tile.toImage(0, basePalette);
        } else if (moodFolder != null) {
            Path file = moodFolder.resolve(layer.fileName());
            if (Files.isRegularFile(file)) {
                try {
                    source = new Image(file.toUri().toString());
                    if (source.isError()) {
                        source = null;
                    }
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Could not read the layer image " + file, e);
                    source = null;
                }
            }
        }
        if (source == null) {
            LOG.warning("Layer image \"" + layer.fileName()
                    + "\" is missing - drawing the placeholder instead");
            source = placeholder();
        }

        Image prepared = fit(source, layer, width, height);
        imageCache.put(key, prepared);
        return prepared;
    }

    /**
     * Scales a picture to its final drawn size, once.
     *
     * <p>Integer magnification with smoothing off for a tile, because it is hand-drawn pixel art and
     * interpolating it is what turns it to mush (ground rule 8). The fitted modes take whatever
     * scale the canvas dictates, which is the same trade the album artwork already makes.
     */
    private Image fit(Image source, ImageLayer layer, double width, double height) {
        double sourceWidth = source.getWidth();
        double sourceHeight = source.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return source;
        }

        double targetWidth;
        double targetHeight;
        switch (layer.fit()) {
            case TILE, CENTER -> {
                targetWidth = sourceWidth * layer.pixelScale();
                targetHeight = sourceHeight * layer.pixelScale();
            }
            case STRETCH -> {
                targetWidth = width;
                targetHeight = height;
            }
            case CONTAIN -> {
                double scale = Math.min(width / sourceWidth, height / sourceHeight);
                targetWidth = sourceWidth * scale;
                targetHeight = sourceHeight * scale;
            }
            case COVER -> {
                double scale = Math.max(width / sourceWidth, height / sourceHeight);
                targetWidth = sourceWidth * scale;
                targetHeight = sourceHeight * scale;
            }
            default -> {
                targetWidth = sourceWidth;
                targetHeight = sourceHeight;
            }
        }

        int drawWidth = Math.max(1, (int) Math.round(targetWidth));
        int drawHeight = Math.max(1, (int) Math.round(targetHeight));
        if (drawWidth == (int) sourceWidth && drawHeight == (int) sourceHeight) {
            return source;
        }

        Canvas scratch = new Canvas(drawWidth, drawHeight);
        GraphicsContext gc = scratch.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.drawImage(source, 0, 0, drawWidth, drawHeight);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return scratch.snapshot(parameters, new WritableImage(drawWidth, drawHeight));
    }

    /** The same magenta marker a missing sprite gets, so a missing layer looks like a fault. */
    private static Image placeholder() {
        WritableImage image = new WritableImage(16, 16);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean check = ((x / 8) + (y / 8)) % 2 == 0;
                writer.setColor(x, y, check ? Color.MAGENTA : Color.rgb(42, 10, 42));
            }
        }
        return image;
    }

    private static Image fromArgb(int[] argb, int width, int height) {
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), argb, 0, width);
        return image;
    }

    private static int toArgb(Color color) {
        int a = (int) Math.round(color.getOpacity() * 255);
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Renders a tile at an integer scale, for the customizer's previews.
     *
     * @param tile    the tile; must not be {@code null}
     * @param frame   which frame
     * @param palette the palette to resolve indices against
     * @param scale   integer magnification
     * @return the magnified image, never smoothed
     */
    public static Image renderTile(PixelTile tile, int frame, Palette palette, int scale) {
        Image source = tile.toImage(frame, palette);
        int size = tile.size() * Math.max(1, scale);
        Canvas scratch = new Canvas(size, size);
        GraphicsContext gc = scratch.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.drawImage(source, 0, 0, size, size);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return scratch.snapshot(parameters, new WritableImage(size, size));
    }

    /**
     * The hex the customizer shows under a swatch.
     *
     * @param color the colour
     * @return the six-digit hex string
     */
    public static String hex(Color color) {
        return GbaColor.toHex(color);
    }
}
