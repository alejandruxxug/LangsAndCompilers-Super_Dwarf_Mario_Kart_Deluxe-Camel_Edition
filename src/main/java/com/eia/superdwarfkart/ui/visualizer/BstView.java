package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.RacerFrame;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.ds.BinarySearchTree;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.playback.AlphabeticalMode;
import com.eia.superdwarfkart.playback.Player;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The binary search tree, live, with its traversals animated.
 *
 * <p>The tree is drawn as a neighbourhood seen from above: nodes are buildings, the parent links
 * are the streets between them, and a traversal is the racer driving across town. Nodes sit at
 * their in-order position across and their depth down, so the shape on screen is the shape in
 * memory - a balanced tree is a town and a degenerate one is a single long road.
 *
 * <p>The point of the view is the second half: <strong>the walk is driven, not just its
 * result</strong>. Advancing sends the kart down each traversed street in sequence - into the
 * right subtree and along to its minimum, or back up through parent pointers until arriving from
 * a left child - and stepping back mirrors it. A view that only moved a highlight from one node
 * to the next would be indistinguishable from one backed by a sorted array, which is exactly the
 * shortcut the oral defense probes for.
 *
 * <p>Two buttons rebuild the tree from the same songs in a different insertion order. Reinserting
 * them already sorted degenerates the tree into a straight line: the height jumps from about
 * log n to n and the measured search cost on the complexity panel jumps with it. That is the
 * worst case of a binary search tree, demonstrated live, in one click.
 *
 * <p>Step-through mode walks one edge per keypress, so the traversal can be narrated an edge at a
 * time in front of the room instead of racing past.
 */
public class BstView extends StructureView {

    /** Node box width in world units, sized for a short title in the 7px pixel font. */
    private static final double NODE_WIDTH = 88;

    /** Node box height in world units. */
    private static final double NODE_HEIGHT = 20;

    /** Horizontal distance between adjacent in-order positions. */
    private static final double X_SPACING = 98;

    /** Vertical distance between depths. */
    private static final double Y_SPACING = 48;

    /** Seconds spent driving each edge of a route in continuous mode. */
    private static final double SEGMENT_SECONDS = 0.34;

    /** Width of a street at 1:1 zoom. */
    private static final double ROAD_WIDTH = 13;

    /** Canvas height above which the racer is drawn a size larger. */
    private static final double LARGE_CANVAS_HEIGHT = 620;

    /**
     * Half the kart's height, for framing the camera on the sprite rather than on the junction it
     * is parked above.
     */
    private static final double KART_HALF_HEIGHT = 32;

    /**
     * The camera's dead zone, as a fraction of the canvas from each edge.
     *
     * <p>While the kart is inside this box the map holds still and only he moves, which is far
     * easier to read than a neighbourhood sliding under a kart nailed to the centre. Past the box
     * edge the map follows him.
     */
    private static final double DEAD_ZONE_X = 0.34;

    /** The dead zone's vertical extent, as a fraction of the canvas from each edge. */
    private static final double DEAD_ZONE_Y = 0.30;

    /** How much of the remaining distance the camera closes each frame once it is following. */
    private static final double FOLLOW_EASE = 0.16;

    /** Below this zoom the node titles are dropped as unreadable. */
    private static final double TEXT_ZOOM_FLOOR = 0.55;

    private static final double MIN_ZOOM = 0.12;
    private static final double MAX_ZOOM = 2.5;

    /**
     * Space left above the root, under the view's heading.
     *
     * <p>Deep enough to hold the kart when it is parked at the root: it sits above the building
     * it is beside, and at the old margin it was drawn off the top of the canvas.
     */
    private static final double TOP_MARGIN = 82;

    /** Breathing room left around the tree when fitting it to the canvas. */
    private static final double FIT_MARGIN = 24;

    /**
     * How much the canvas has to change size before the view frames itself again.
     *
     * <p>Entering presentation mode roughly triples the width, and a framing chosen for a 400
     * pixel panel leaves the tree in the top-left corner of the stage. A resize this large is a
     * different view of the same tree, so it earns a new framing; smaller ones leave whatever the
     * user panned to alone.
     */
    private static final double REFRAME_RATIO = 1.5;

    private final AlphabeticalMode mode;
    private final Player player;
    private final AppState state;
    private final AssetRegistry assets;

    private final ToggleButton stepThrough = new ToggleButton("STEP MODE");
    private final Button stepButton = new Button("STEP >");
    private final Button sortedButton = new Button("INSERT SORTED");
    private final Button shuffledButton = new Button("INSERT SHUFFLED");
    private final Button fitButton = new Button("FIT");
    private final Label readout = new Label();
    private final Tooltip tooltip = new Tooltip();

    /** Every node, with its in-order position and depth worked out. */
    private List<NodeLayout> layout = List.of();

    /** Where each song sits, for edge drawing and hover matching. */
    private final Map<Song, NodeLayout> byValue = new HashMap<>();

    /** The nodes of the walk being shown, in the order they are touched. */
    private List<Song> path = List.of();

    /** How many edges of {@link #path} are lit. */
    private int litEdges;

    /** What the last walk cost, kept on screen after the animation settles. */
    private int lastPathEdges;

    /**
     * The song this view last drew as current.
     *
     * <p>Kept here because by the time the view is told to refresh the mode has already moved, so
     * the song the walk started from is no longer available from anywhere else.
     */
    private Song shownCurrent;

    /** Size and height, recomputed on rebuild rather than per frame: both are O(n) walks. */
    private int treeSize;
    private int treeHeight = -1;

    /** Set when the tree changed shape, or the panel changed size, enough to reframe. */
    private boolean needsFraming = true;

    /** Set by the FIT button: frame the whole tree however small that makes it. */
    private boolean fitRequested;

    /** Canvas size the current framing was chosen for. */
    private double framedWidth;
    private double framedHeight;

    private double zoom = 1;
    private double panX;
    private double panY;

    private double dragAnchorX;
    private double dragAnchorY;

    /**
     * One node's place in the drawing.
     *
     * @param song    the song at this node
     * @param column  its in-order position, left to right
     * @param depth   its distance from the root
     * @param parent  the parent's song, or {@code null} at the root
     */
    private record NodeLayout(Song song, int column, int depth, Song parent) {

        double worldX() {
            return column * X_SPACING;
        }

        double worldY() {
            return depth * Y_SPACING;
        }
    }

    /**
     * Builds the tree view.
     *
     * @param mode   the alphabetical mode whose tree is drawn; must not be {@code null}
     * @param player drives the rebuilds the shape controls perform; must not be {@code null}
     * @param state  supplies the selected racer; must not be {@code null}
     * @param assets supplies the kart artwork; must not be {@code null}
     */
    public BstView(AlphabeticalMode mode, Player player, AppState state, AssetRegistry assets) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.assets = Objects.requireNonNull(assets, "assets must not be null");

        getStyleClass().add("bst-view");
        setControls(buildControls());

        tooltip.setShowDelay(Duration.millis(120));
        Tooltip.install(canvas(), tooltip);
        canvas().setOnMouseMoved(this::updateTooltip);
        canvas().setOnMouseDragged(this::pan);
        canvas().setOnScroll(event -> zoomAt(event.getX(), event.getY(), event.getDeltaY() > 0 ? 1.1 : 1 / 1.1));

        // Step-through is meant to be driven from the keyboard while talking, so the canvas takes
        // focus on a click and answers the arrow keys as well as the button.
        canvas().setFocusTraversable(true);
        canvas().setOnMousePressed(event -> {
            canvas().requestFocus();
            beginPan(event);
        });
        canvas().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.SPACE) {
                advanceOneEdge();
                event.consume();
            }
        });

        state.racerProperty().addListener((observable, old, selected) -> redraw());

        shownCurrent = mode.current();
        rebuildLayout();
        updateControls();
    }

    private FlowPane buildControls() {
        stepThrough.getStyleClass().add("mode-button");
        stepThrough.setTooltip(new Tooltip(
                "Walk the traversal one edge per press of STEP, the right arrow or the space bar."));
        stepThrough.setOnAction(e -> {
            stopAnimating();
            updateControls();
            redraw();
        });

        stepButton.setOnAction(e -> advanceOneEdge());
        stepButton.setTooltip(new Tooltip("Light the next edge of the walk."));

        sortedButton.setOnAction(e -> reinsert(true));
        sortedButton.setTooltip(new Tooltip(
                "Reinsert every song in alphabetical order.\n"
                        + "A binary search tree built from sorted input degenerates into a straight\n"
                        + "line: height becomes n-1 and search becomes O(n)."));

        shuffledButton.setOnAction(e -> reinsert(false));
        shuffledButton.setTooltip(new Tooltip(
                "Reinsert every song in a shuffled order, which gives a tree of roughly log n height."));

        fitButton.setOnAction(e -> {
            fitRequested = true;
            redraw();
        });
        fitButton.setTooltip(new Tooltip("Fit the whole tree into the panel."));

        readout.getStyleClass().add("visualizer-readout");

        // A FlowPane, not an HBox: at 400 pixels these five controls do not fit on one line, and
        // an HBox answers that by truncating every label to "INS...". Wrapping keeps the words
        // readable in the panel and still lays them out in a single row on the full stage.
        FlowPane bar = new FlowPane(6, 4, stepThrough, stepButton, sortedButton, shuffledButton,
                fitButton, readout);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.getStyleClass().add("visualizer-controls");
        return bar;
    }

    @Override
    public ModeId modeId() {
        return ModeId.ALPHABETICAL;
    }

    // ------------------------------------------------------------------
    // Following the structure
    // ------------------------------------------------------------------

    @Override
    public void refresh() {
        Song before = shownCurrent;
        rebuildLayout();
        Song after = mode.current();
        shownCurrent = after;
        beginWalk(before, after);
    }

    /**
     * Works out which walk connects two songs and starts showing it.
     *
     * <p>The mode has already moved by the time this runs, so the path is recovered from the tree
     * afterwards rather than being reported by the navigation. The tree has not changed in
     * between, so the walk recovered here is the walk that happened - and because these calls sit
     * outside any measurement scope, the steps they cost are discarded rather than being added to
     * what the panel reports.
     *
     * @param from the song that was current, or {@code null}
     * @param to   the song that is current now, or {@code null}
     */
    private void beginWalk(Song from, Song to) {
        BinarySearchTree<Song> tree = mode.tree();
        path = List.of();
        litEdges = 0;

        if (to != null && from != null && !from.equals(to) && tree.search(from) && tree.search(to)) {
            List<Song> forward = tree.successorPath(from);
            if (endsAt(forward, to)) {
                path = forward;
            } else {
                List<Song> backward = tree.predecessorPath(from);
                path = endsAt(backward, to) ? backward : tree.searchPath(to);
            }
        } else if (to != null && tree.search(to)) {
            // A jump: the user picked a song, so the walk to show is the search from the root.
            path = tree.searchPath(to);
        }

        lastPathEdges = Math.max(0, path.size() - 1);
        // Frame him before he sets off; from there the camera follows him street by street.
        snapToRacer();
        updateControls();

        if (path.size() < 2 || stepThrough.isSelected()) {
            stopAnimating();
            redraw();
            return;
        }
        animate(SEGMENT_SECONDS * (path.size() - 1), () -> litEdges = path.size() - 1);
    }

    private static boolean endsAt(List<Song> walk, Song destination) {
        return !walk.isEmpty() && walk.get(walk.size() - 1).equals(destination);
    }

    /** Lights one more edge of the walk, for step-through mode. */
    private void advanceOneEdge() {
        if (path.size() < 2) {
            return;
        }
        stopAnimating();
        litEdges = Math.min(litEdges + 1, path.size() - 1);
        // Keep him in shot as the walk is narrated an edge at a time. Snapped rather than eased
        // because there is no animation here to ease across - and inside the dead zone it does
        // nothing at all, so a step that stays in frame does not move the map.
        snapToRacer();
        updateControls();
        redraw();
    }

    /**
     * Rebuilds the tree from the same songs in a different insertion order.
     *
     * <p>Nothing is added or removed - the library is untouched. Only the order the songs are
     * inserted in changes, and that alone decides the shape.
     *
     * @param sorted whether to insert them in alphabetical order, which degenerates the tree
     */
    private void reinsert(boolean sorted) {
        List<Song> ordering = new ArrayList<>(player.library().all());
        if (sorted) {
            ordering.sort(Song.BY_TITLE);
        } else {
            Collections.shuffle(ordering);
        }
        needsFraming = true;
        // reload() notifies the player's listeners, which brings this view back through refresh().
        player.reload(ordering);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private void rebuildLayout() {
        List<NodeLayout> nodes = new ArrayList<>(mode.size());
        assign(mode.tree().rootRef(), 0, 0, nodes);
        layout = nodes;

        byValue.clear();
        for (NodeLayout node : nodes) {
            byValue.put(node.song(), node);
        }

        // Both of these walk the whole tree, so they are taken once per change rather than once
        // per frame - the status line reads them sixty times a second while an animation runs.
        int height = mode.height();
        if (nodes.size() != treeSize || height != treeHeight) {
            needsFraming = true;
        }
        treeSize = nodes.size();
        treeHeight = height;
    }

    /**
     * Walks the tree in order, giving each node its column and depth.
     *
     * <p>In-order across and depth down is the layout that makes a binary search tree readable:
     * the horizontal order on screen is the alphabetical order, so a degenerate tree draws as a
     * diagonal staircase and a balanced one as a pyramid, with no further explanation needed.
     *
     * @param node  the subtree to place
     * @param depth its distance from the root
     * @param index the next free in-order column
     * @param out   collects the placed nodes
     * @return the next free in-order column after this subtree
     */
    private int assign(BinarySearchTree.NodeRef<Song> node, int depth, int index, List<NodeLayout> out) {
        if (node == null) {
            return index;
        }
        int next = assign(node.left(), depth + 1, index, out);
        Song parent = node.parent() == null ? null : node.parent().value();
        out.add(new NodeLayout(node.value(), next, depth, parent));
        return assign(node.right(), depth + 1, next + 1, out);
    }

    /**
     * Chooses the framing the view comes up in.
     *
     * <p>Not simply "fit everything". Thirty nodes laid out in order are nearly three thousand
     * pixels wide, and squeezing that into a four hundred pixel panel lands at the minimum zoom
     * where the tree is a grey smudge and no title can be read. So: fit only when fitting still
     * leaves the nodes legible, and otherwise stay at 1:1 and centre on the song playing. The FIT
     * button remains available for the deliberate whole-shape look.
     */
    private void frame() {
        double width = canvas().getWidth();
        double height = canvas().getHeight();
        if (layout.isEmpty() || width <= 0 || height <= 0) {
            zoom = 1;
            panX = 0;
            panY = TOP_MARGIN;
            return;
        }
        if (fitZoom(width, height) >= 1) {
            fitToCanvas();
            return;
        }
        zoom = 1;
        panY = TOP_MARGIN;
        NodeLayout current = byValue.get(mode.current());
        panX = current == null
                ? TOP_MARGIN
                : width / 2 - current.worldX() - NODE_WIDTH / 2;
        // Then hand over to the follow camera, which brings the kart into its dead zone - the
        // current node can sit at any depth, and framing on x alone leaves a deep one off screen.
        snapToRacer();
    }

    /** Chooses a zoom and pan that bring the whole tree into view, however small that has to be. */
    private void fitToCanvas() {
        double width = canvas().getWidth();
        double height = canvas().getHeight();
        if (layout.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        zoom = Math.clamp(fitZoom(width, height), MIN_ZOOM, MAX_ZOOM);
        panX = (width - (maxColumn() + 1) * X_SPACING * zoom) / 2;
        panY = TOP_MARGIN;
    }

    /**
     * @param width  canvas width
     * @param height canvas height
     * @return the zoom at which the whole tree would just fit
     */
    private double fitZoom(double width, double height) {
        double worldWidth = (maxColumn() + 1) * X_SPACING;
        double worldHeight = (maxDepth() + 1) * Y_SPACING;
        return Math.min((width - FIT_MARGIN) / worldWidth,
                (height - TOP_MARGIN - FIT_MARGIN) / worldHeight);
    }

    private int maxColumn() {
        int max = 0;
        for (NodeLayout node : layout) {
            max = Math.max(max, node.column());
        }
        return max;
    }

    private int maxDepth() {
        int max = 0;
        for (NodeLayout node : layout) {
            max = Math.max(max, node.depth());
        }
        return max;
    }

    /**
     * Keeps the camera on the racer as he drives.
     *
     * <p>A five hundred song tree is tens of thousands of pixels wide, so a fixed camera means
     * the kart drives out of frame on the first turn and the traversal - the thing this view
     * exists to show - happens off screen.
     *
     * <p>It follows with a <strong>dead zone</strong> rather than nailing him to the middle: while
     * he is inside the central box nothing moves, and the map only slides once he reaches its
     * edge. A camera locked to the centre puts the whole neighbourhood in motion for every small
     * step, which is far harder to read than a kart moving across a map that is holding still.
     * Outside the box the pan eases rather than jumps, so the map slides after him.
     *
     * @param pose   where the racer is, in world coordinates
     * @param width  canvas width
     * @param height canvas height
     * @param snap   whether to arrive immediately instead of easing, for the start of a walk
     */
    private void followRacer(RacerPose pose, double width, double height, boolean snap) {
        if (width <= 0 || height <= 0) {
            return;
        }
        // Framed on the kart itself, which rides above the junction it is at, not on the junction.
        double kartOffset = NODE_HEIGHT * zoom / 2 + KART_HALF_HEIGHT;
        double screenX = pose.worldX() * zoom + panX;
        double screenY = pose.worldY() * zoom + panY - kartOffset;

        double marginX = width * DEAD_ZONE_X;
        double marginY = height * DEAD_ZONE_Y;

        double targetPanX = panX;
        double targetPanY = panY;
        if (screenX < marginX) {
            targetPanX = marginX - pose.worldX() * zoom;
        } else if (screenX > width - marginX) {
            targetPanX = width - marginX - pose.worldX() * zoom;
        }
        if (screenY < marginY) {
            targetPanY = marginY - pose.worldY() * zoom + kartOffset;
        } else if (screenY > height - marginY) {
            targetPanY = height - marginY - pose.worldY() * zoom + kartOffset;
        }

        double ease = snap ? 1 : FOLLOW_EASE;
        panX += (targetPanX - panX) * ease;
        panY += (targetPanY - panY) * ease;
    }

    /**
     * Frames the camera on the racer at once, for the start of a walk.
     *
     * <p>Easing in from wherever the user last panned would spend the first half of the
     * traversal catching up, and the traversal is short.
     */
    private void snapToRacer() {
        RacerPose pose = racerPose(visibleEdgeCount());
        if (pose != null) {
            followRacer(pose, canvas().getWidth(), canvas().getHeight(), true);
        }
    }

    private void beginPan(MouseEvent event) {
        dragAnchorX = event.getX() - panX;
        dragAnchorY = event.getY() - panY;
    }

    private void pan(MouseEvent event) {
        panX = event.getX() - dragAnchorX;
        panY = event.getY() - dragAnchorY;
        redraw();
    }

    private void zoomAt(double x, double y, double factor) {
        double before = zoom;
        zoom = Math.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        // Keep whatever is under the pointer under the pointer.
        panX = x - (x - panX) * (zoom / before);
        panY = y - (y - panY) * (zoom / before);
        redraw();
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    protected void draw(GraphicsContext gc, double width, double height) {
        if (layout.isEmpty()) {
            drawText(gc, "TREE - BinarySearchTree", 10, 16, HEADING_SIZE, PaletteRole.PRIMARY);
            drawText(gc, "The tree is empty.", 10, 36, TEXT_SIZE, PaletteRole.TEXT_DIM);
            return;
        }

        // Framing needs the canvas size, which is not known until the first layout pass, so it
        // happens here rather than when the tree changed. A large resize - entering presentation
        // mode, most of all - counts as a reason to frame again.
        boolean resized = framedWidth <= 0
                || width / framedWidth > REFRAME_RATIO || framedWidth / width > REFRAME_RATIO
                || height / framedHeight > REFRAME_RATIO || framedHeight / height > REFRAME_RATIO;
        if (fitRequested || needsFraming || resized) {
            if (fitRequested) {
                fitToCanvas();
            } else {
                frame();
            }
            fitRequested = false;
            needsFraming = false;
            framedWidth = width;
            framedHeight = height;
        }

        int visibleEdges = visibleEdgeCount();

        // The camera moves before anything is drawn. Working it out afterwards would leave the
        // map a frame behind the kart, which on a fast traversal is visible as a wobble.
        //
        // Only while he is driving, though. Panning and zooming to inspect the tree is a feature
        // of this view, and a camera that followed while he was parked would drag the map back
        // the instant the user dragged it anywhere.
        RacerPose pose = racerPose(visibleEdges);
        if (pose != null && isAnimating()) {
            followRacer(pose, width, height, false);
        }

        drawEdges(gc, visibleEdges);
        drawNodes(gc, visibleEdges);
        if (pose != null) {
            drawRacer(gc, pose);
        }

        // The heading and the status line go on last, over a band of the background colour. The
        // tree pans freely underneath them, so without the band a node label drifts under the
        // text and both become unreadable.
        drawBand(gc, 0, width, 22);
        drawText(gc, "TREE - BinarySearchTree", 10, 16, HEADING_SIZE, PaletteRole.PRIMARY);
        drawBand(gc, height - 20, width, 20);
        drawStatus(gc, width, height, visibleEdges);
    }

    private static void drawBand(GraphicsContext gc, double y, double width, double height) {
        gc.setFill(color(PaletteRole.BACKGROUND, 0.92));
        gc.fillRect(0, y, width, height);
    }

    /** @return how many edges of the current walk should be lit right now */
    private int visibleEdgeCount() {
        if (path.size() < 2) {
            return 0;
        }
        if (stepThrough.isSelected() || !isAnimating()) {
            return litEdges;
        }
        return Math.min(path.size() - 1, (int) Math.ceil(progress() * (path.size() - 1)));
    }

    /**
     * Draws the edges as streets.
     *
     * <p>Every edge is a road: asphalt, kerbs, and a dashed centre line down the middle. The tree
     * seen from above is a neighbourhood, the parent links are the streets between the houses,
     * and a traversal is a drive across town rather than a highlight moving between boxes. The
     * roads carry the same information a plain line would - they are the links, drawn thicker.
     */
    private void drawEdges(GraphicsContext gc, int visibleEdges) {
        double asphalt = Math.max(3, ROAD_WIDTH * zoom);

        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(asphalt + Math.max(2, 2 * zoom));
        strokeEveryEdge(gc);

        gc.setStroke(color(PaletteRole.SURFACE_RAISED));
        gc.setLineWidth(asphalt);
        strokeEveryEdge(gc);

        // The centre line only appears once a street is wide enough to hold one.
        if (asphalt >= 7) {
            gc.setStroke(color(PaletteRole.TEXT_DIM, 0.5));
            gc.setLineWidth(1);
            gc.setLineDashes(5, 5);
            strokeEveryEdge(gc);
            gc.setLineDashes(null);
        }

        // The route being driven is repaved in the protected highlight role, wide enough to read
        // from the back of the room.
        gc.setStroke(color(PaletteRole.HIGHLIGHT));
        gc.setLineWidth(asphalt);
        for (int i = 0; i < visibleEdges && i + 1 < path.size(); i++) {
            NodeLayout from = byValue.get(path.get(i));
            NodeLayout to = byValue.get(path.get(i + 1));
            if (from != null && to != null) {
                strokeEdge(gc, from, to);
            }
        }
        gc.setLineCap(StrokeLineCap.SQUARE);
    }

    /**
     * Strokes every parent-child link with whatever stroke is currently set.
     *
     * <p>Called once per layer of the road - kerb, asphalt, centre line - so the streets are
     * built up in passes rather than each edge being drawn three times in a row, which would put
     * one road's centre line under the next road's kerb.
     *
     * @param gc the context being drawn into
     */
    private void strokeEveryEdge(GraphicsContext gc) {
        for (NodeLayout node : layout) {
            NodeLayout parent = node.parent() == null ? null : byValue.get(node.parent());
            if (parent != null) {
                strokeEdge(gc, parent, node);
            }
        }
    }

    private void strokeEdge(GraphicsContext gc, NodeLayout from, NodeLayout to) {
        gc.strokeLine(
                screenX(from) + NODE_WIDTH * zoom / 2, screenY(from) + NODE_HEIGHT * zoom / 2,
                screenX(to) + NODE_WIDTH * zoom / 2, screenY(to) + NODE_HEIGHT * zoom / 2);
    }

    private void drawNodes(GraphicsContext gc, int visibleEdges) {
        Song current = mode.current();
        Song cursor = path.isEmpty() ? null : path.get(Math.min(visibleEdges, path.size() - 1));
        double boxWidth = NODE_WIDTH * zoom;
        double boxHeight = NODE_HEIGHT * zoom;

        for (NodeLayout node : layout) {
            double x = screenX(node);
            double y = screenY(node);
            if (x + boxWidth < 0 || x > canvas().getWidth() || y + boxHeight < 0 || y > canvas().getHeight()) {
                continue;
            }

            boolean isCurrent = node.song().equals(current);
            boolean onPath = pathContains(node.song(), visibleEdges);
            PaletteRole face = isCurrent ? PaletteRole.ACCENT
                    : onPath ? PaletteRole.SURFACE_RAISED : PaletteRole.SURFACE;
            PaletteRole edge = isCurrent ? PaletteRole.PRIMARY
                    : onPath ? PaletteRole.HIGHLIGHT : PaletteRole.OUTLINE;

            // A shadow cast down and right, so the buildings sit on top of the streets rather
            // than looking like holes cut out of them.
            gc.setFill(color(PaletteRole.SHADOW, 0.5));
            gc.fillRect(x + 3, y + 3, boxWidth, boxHeight);
            drawPanel(gc, x, y, boxWidth, boxHeight, face, edge);

            if (zoom >= TEXT_ZOOM_FLOOR) {
                drawText(gc, fit(node.song().getTitle(), boxWidth - 8, TEXT_SIZE),
                        x + 4, y + boxHeight / 2 + 3, TEXT_SIZE,
                        isCurrent ? PaletteRole.SHADOW : PaletteRole.TEXT_PRIMARY);
            }

            if (node.song().equals(cursor) && !path.isEmpty()) {
                // The step-through cursor: a bracket around whichever node the walk has reached.
                gc.setStroke(color(PaletteRole.HIGHLIGHT));
                gc.setLineWidth(2);
                gc.strokeRect(x - 3, y - 3, boxWidth + 6, boxHeight + 6);
            }
        }
    }

    /**
     * Drives the racer along the route.
     *
     * <p>Node to node down the streets, at the pace of the traversal: while the walk animates he
     * covers one edge per segment, and in step-through mode he is parked at whichever junction
     * the walk has reached. Seeing a kart take the turns is what makes the successor rule
     * readable - down into the right subtree and along to its smallest, or back up through the
     * parents until arriving from a left child - where a highlight jumping between boxes only
     * ever shows the answer.
     */
    /**
     * Where the racer is and which way he is pointing, in world coordinates.
     *
     * @param worldX position across the map, before pan and zoom
     * @param worldY position down the map, before pan and zoom
     * @param dirX   how far the street he is on runs to the right; negative means leftward
     * @param dirY   how far that street runs down the map
     * @param moving whether he is between junctions rather than parked at one
     */
    private record RacerPose(double worldX, double worldY, double dirX, double dirY, boolean moving) {
    }

    /**
     * Works out where the racer is along the route.
     *
     * <p>In world coordinates rather than screen ones, because the camera is decided from this and
     * the camera is what turns world coordinates into screen ones - reading it off the screen
     * would be circular, and a frame behind.
     *
     * @param visibleEdges how many edges of the route are lit
     * @return the pose, or {@code null} when there is nowhere to put him
     */
    private RacerPose racerPose(int visibleEdges) {
        NodeLayout at;
        NodeLayout heading = null;
        double t = 0;

        if (path.size() >= 2 && isAnimating()) {
            int edges = path.size() - 1;
            double travelled = Math.clamp(progress(), 0, 1) * edges;
            int segment = Math.min((int) travelled, edges - 1);
            t = travelled - segment;
            at = byValue.get(path.get(segment));
            heading = byValue.get(path.get(segment + 1));
        } else if (!path.isEmpty()) {
            // Settled, or stepping through: parked at the junction the walk has reached.
            at = byValue.get(path.get(Math.clamp(visibleEdges, 0, path.size() - 1)));
        } else {
            at = byValue.get(mode.current());
        }
        if (at == null) {
            return null;
        }

        double fromX = at.worldX() + NODE_WIDTH / 2;
        double fromY = at.worldY() + NODE_HEIGHT / 2;
        double toX = heading == null ? fromX : heading.worldX() + NODE_WIDTH / 2;
        double toY = heading == null ? fromY : heading.worldY() + NODE_HEIGHT / 2;

        return new RacerPose(
                fromX + (toX - fromX) * t,
                fromY + (toY - fromY) * t,
                toX - fromX, toY - fromY,
                heading != null);
    }

    private void drawRacer(GraphicsContext gc, RacerPose pose) {
        SpriteSheet kart = assets.racer(state.getRacer());
        // Always 1:1 here, unlike the circuit. This is a map: a vehicle on it should be smaller
        // than the blocks it drives between, and at 2x the kart is wider than three buildings and
        // hides whichever street it is on.
        int scale = 1;
        double drawWidth = kart.frameWidth() * scale;
        double drawHeight = kart.frameHeight() * scale;

        double x = pose.worldX() * zoom + panX;
        double y = pose.worldY() * zoom + panY;

        // Seen from above, a kart heading up or down the map shows its back; one heading across
        // shows its side, mirrored when it is going left. Frame 2 is the rear view and frames 0
        // and 1 are the driving cycle - the icon in frame 3 is never part of either.
        boolean mostlyVertical = Math.abs(pose.dirY()) > Math.abs(pose.dirX());
        int frame = mostlyVertical && pose.moving()
                ? RacerFrame.BACK.index()
                : racerFrame();

        // Anchored by its wheels just clear of the building it is beside, rather than centred on
        // it: centred, the kart covers the very title the node exists to show.
        double wheelsY = y - NODE_HEIGHT * zoom / 2 - drawHeight;
        drawSprite(gc, kart, frame, x - drawWidth / 2, wheelsY, scale, pose.dirX() < 0);
    }

    private boolean pathContains(Song song, int visibleEdges) {
        for (int i = 0; i <= visibleEdges && i < path.size(); i++) {
            if (path.get(i).equals(song)) {
                return true;
            }
        }
        return false;
    }

    private double screenX(NodeLayout node) {
        return node.worldX() * zoom + panX;
    }

    private double screenY(NodeLayout node) {
        return node.worldY() * zoom + panY;
    }

    private void drawStatus(GraphicsContext gc, double width, double height, int visibleEdges) {
        // A tree whose height is n-1 has no branching left anywhere: it is a linked list that
        // costs O(n) to search. Saying so on screen is the whole point of the sorted-insert button.
        String shape = treeSize > 2 && treeHeight >= treeSize - 1
                ? "DEGENERATE - a linked list wearing a tree"
                : "height " + treeHeight;
        drawText(gc, "n = " + treeSize + "   " + shape,
                10, height - 12, TEXT_SIZE, PaletteRole.TEXT_DIM);

        if (!path.isEmpty()) {
            String walk = "walk: " + visibleEdges + "/" + lastPathEdges + " edges";
            drawText(gc, walk, width - textWidth(walk, TEXT_SIZE) - 10, height - 12,
                    TEXT_SIZE, PaletteRole.HIGHLIGHT);
        }
    }

    private void updateControls() {
        stepButton.setDisable(!stepThrough.isSelected() || path.size() < 2 || litEdges >= path.size() - 1);
        readout.setText("h = " + treeHeight + "   walk " + lastPathEdges + " edges");
    }

    private void updateTooltip(MouseEvent event) {
        NodeLayout hovered = nodeAt(event.getX(), event.getY());
        if (hovered == null) {
            tooltip.setText("Drag to pan, scroll to zoom.\n"
                    + "The camera follows the kart while it drives a traversal.\n"
                    + "In-order across, depth down. n = " + treeSize + ", height " + treeHeight);
            return;
        }
        tooltip.setText(hovered.song().getTitle() + "\n" + hovered.song().getArtist()
                + "\ndepth " + hovered.depth() + ", in-order position " + (hovered.column() + 1));
    }

    private NodeLayout nodeAt(double x, double y) {
        double boxWidth = NODE_WIDTH * zoom;
        double boxHeight = NODE_HEIGHT * zoom;
        for (NodeLayout node : layout) {
            double nodeX = screenX(node);
            double nodeY = screenY(node);
            if (x >= nodeX && x <= nodeX + boxWidth && y >= nodeY && y <= nodeY + boxHeight) {
                return node;
            }
        }
        return null;
    }
}
