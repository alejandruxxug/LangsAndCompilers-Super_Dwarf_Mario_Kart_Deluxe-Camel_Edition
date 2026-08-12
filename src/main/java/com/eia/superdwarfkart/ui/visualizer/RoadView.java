package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.assets.RacerFrame;
import com.eia.superdwarfkart.assets.SpriteSheet;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.scene.canvas.GraphicsContext;

import java.util.List;
import java.util.Objects;

/**
 * A structure drawn as a road seen from the side, with the racer driving it.
 *
 * <p>Two of the three structures are sequences, and a sequence reads best as a road: songs on
 * roadside markers laid out in order, the racer somewhere along it, the road sliding underneath.
 * What differs between them is not the picture but <strong>which moves are possible</strong> - and
 * that is the whole point. A queue can only ever go forward. A circular list can go back, and past
 * its last node it comes round to its first. Both facts are visible here as things the camera can
 * and cannot do, which is a stronger claim than a caption saying so.
 *
 * <p>The racer holds his mark and the road moves under him. He is <em>always</em> driving: the
 * wheels turn and the surface scrolls whatever else is happening, because he is travelling through
 * a running order rather than parked in it. A song change is a burst on top of that - he floors it
 * and the camera hauls him back, or he drops back and the camera follows him down.
 *
 * <p>Subclasses supply the songs, where the racer stands among them and what the moves mean. The
 * road, the camera, the burst and the mini-map all live here.
 */
public abstract class RoadView extends StructureView {

    /**
     * Distance along the road between one song's marker and the next, in world pixels.
     *
     * <p>Wide on purpose, and the reason is the <em>speed match</em>. Two things move when the
     * racer sets off: the markers, carried by the camera, and the road surface, carried by the
     * camera plus a constant cruise. Pack the markers close together and one slot is barely any
     * camera travel, so the tarmac rips past at twice the speed of the signs standing on it and
     * the shot reads as two layers sliding at unrelated rates. Spread them out and the camera
     * dominates, both move at nearly the same speed, and the catch-up reads as one road going by.
     *
     * <p>The cost is how many markers fit on screen at once, which is why this is as wide as it
     * can be while still leaving the next song in shot in the narrow panel.
     */
    protected static final double SLOT_SPACING = 280;

    /** Where the racer sits on screen, measured from the left edge. He holds this mark. */
    protected static final double RACER_SCREEN_X = 92;

    /** How long an ordinary song change takes, from flooring it to the camera settling. */
    protected static final double MOVE_SECONDS = 1.3;

    /** How long the loop back to the start takes. Shorter: the return is meant to be a blur. */
    protected static final double LOOP_SECONDS = 1.0;

    /**
     * How fast the road surface slides past, in pixels per second.
     *
     * <p>The racer holds his mark and the <em>road</em> moves - the oldest trick there is, and the
     * only way a side-on racer reads as driving rather than parked. What scrolls is the surface
     * alone: the kerb stripes and the centre line. The song markers stay pinned to their places on
     * the road, because those are structure positions and those do not drift.
     *
     * <p>Deliberately slow. This is the only motion that is <em>not</em> shared with the markers,
     * so every pixel per second of it is a discrepancy between the road and the signs standing on
     * it. Enough to read as travelling at rest, small enough to disappear next to the camera once
     * he is moving.
     */
    protected static final double CRUISE_SCROLL = 52;

    /** Point in a song change at which the racer is furthest from his mark. */
    protected static final double BURST_PEAK_AT = 0.26;

    /** How much of a song change passes before the camera gives chase. */
    protected static final double CHASE_STARTS_AT = 0.12;

    /** When the loop back whips the camera round; before this he is still pulling away. */
    private static final double WHIP_STARTS_AT = 0.30;

    /** How briefly the whip lasts. This is the "very quickly" part of coming round the loop. */
    private static final double WHIP_LENGTH = 0.25;

    /** How high the racer arcs above the road on the way round the loop. */
    private static final double LOOP_ARC = 62;

    /** How far a roadside sign's board stands above the road surface, in pixels. */
    private static final double SIGN_POST_HEIGHT = 58;

    /** Widest a sign's board gets, however far apart the markers are spread. */
    private static final double SIGN_MAX_WIDTH = 180;

    /** Bounces per second of the driving bob. */
    private static final double BOB_RATE = 5.5;

    /**
     * How far the kart rides up and down over the road surface, in pixels at 1:1.
     *
     * <p>Multiplied by the sprite's scale, so it stays proportional to him. Raised when he was
     * halved: the same absolute travel that reads as a bounce under a 128px kart is barely a
     * flicker under a 64px one.
     */
    private static final double BOB_PIXELS = 2.4;

    /** Canvas height above which the racer is drawn a size larger. */
    private static final double LARGE_CANVAS_HEIGHT = 620;

    /** Widest the road gets however tall the canvas is, in pixels. */
    private static final double MAX_ROAD_HEIGHT = 132;

    /** Repaints per second. High enough that the scrolling road does not stutter. */
    private static final double SCROLL_FPS = 30;

    /** Nodes shown in the mini-map at once. */
    private static final int MINI_MAP_NODES = 7;

    /** Baseline of the mini-map's caption, clear of the tagline above it. */
    private static final double MINI_MAP_CAPTION_Y = 48;

    /** Baseline of the mini-map's chain of nodes. */
    private static final double MINI_MAP_Y = 64;

    /** What the racer is doing, which decides where the camera looks and which way he faces. */
    protected enum Move {

        /** Holding his mark; the road still scrolls. */
        NONE,

        /** Advancing: he floors it off the right and the camera hauls him back. */
        FORWARD,

        /** Reversing: he drops back off the left and the camera follows him down. */
        BACKWARD,

        /** Past the end and round to the start: up over the road and back at speed. */
        LOOP_BACK
    }

    private final AppState state;
    private final AssetRegistry assets;

    private int slot;
    private int fromSlot;
    private Move move = Move.NONE;

    /**
     * @param state  supplies the selected racer; must not be {@code null}
     * @param assets supplies the kart artwork; must not be {@code null}
     */
    protected RoadView(AppState state, AssetRegistry assets) {
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.assets = Objects.requireNonNull(assets, "assets must not be null");

        getStyleClass().add("road-view");
        state.racerProperty().addListener((observable, old, selected) -> redraw());

        // He is always driving, so this view always repaints. The rate is the scroll's, not the
        // sprite's: the driving frames come off a clock, so they still change at their own eight
        // a second however often the canvas is redrawn.
        setIdleAnimation(SCROLL_FPS);
    }

    // ------------------------------------------------------------------
    // What a subclass must answer
    // ------------------------------------------------------------------

    /** @return the songs standing along the road, in slot order */
    protected abstract List<Song> roadSongs();

    /** @return the world slot of the first song in {@link #roadSongs()} */
    protected abstract int firstSlot();

    /** @return the song being played */
    protected abstract Song currentSong();

    /** @return the title drawn at the top of the view */
    protected abstract String heading();

    /** @return the one-line claim under the heading */
    protected abstract String tagline();

    /** @return the summary along the bottom */
    protected abstract String footer();

    /**
     * @param index    a position in {@link #roadSongs()}
     * @param size     how many songs are on the road
     * @return a flag to fly on that marker, or {@code null} for none
     */
    protected abstract String flagFor(int index, int size);

    /** @return whether the road closes into a ring, which the mini-map draws as a return arc */
    protected boolean wrapsAround() {
        return false;
    }

    /** @return the colour role for the tagline */
    protected PaletteRole taglineRole() {
        return PaletteRole.TEXT_DIM;
    }

    // ------------------------------------------------------------------
    // Motion
    // ------------------------------------------------------------------

    /** @return the slot the racer occupies */
    protected final int slot() {
        return slot;
    }

    /**
     * Sends the racer to another slot.
     *
     * @param targetSlot where he ends up
     * @param kind       what sort of move it is, which decides the shot
     */
    protected final void driveTo(int targetSlot, Move kind) {
        fromSlot = slot;
        slot = targetSlot;
        move = kind;
        animate(kind == Move.LOOP_BACK ? LOOP_SECONDS : MOVE_SECONDS, () -> move = Move.NONE);
    }

    /**
     * Puts the racer somewhere with no move to show, for a rebuild.
     *
     * @param targetSlot where he now stands
     */
    protected final void jumpTo(int targetSlot) {
        stopAnimating();
        fromSlot = targetSlot;
        slot = targetSlot;
        move = Move.NONE;
        redraw();
    }

    /** @return what the racer is doing right now */
    protected final Move move() {
        return move;
    }

    /**
     * Returns the left edge of the visible stretch of road, in world pixels.
     *
     * <p>The camera holds still for a moment when the song changes - long enough for the racer to
     * be seen breaking away - then accelerates after him and eases to a stop at his new mark. It
     * <strong>chases</strong>; it never cuts. Coming round the loop it whips instead, covering the
     * whole length of the road in a quarter of the move.
     *
     * @return the world coordinate drawn at screen x = 0
     */
    private double cameraX() {
        double to = slot * SLOT_SPACING - RACER_SCREEN_X;
        if (!isAnimating()) {
            return to;
        }
        double from = fromSlot * SLOT_SPACING - RACER_SCREEN_X;
        double closed = move == Move.LOOP_BACK
                ? whipFraction(progress())
                : chaseFraction(progress());
        return from + (to - from) * closed;
    }

    /**
     * How far the camera has closed on the racer's new mark.
     *
     * <p>Zero while it waits, then eased all the way to one so it arrives without slamming to a
     * halt. Split out as a pure function of progress because the motion is the whole point of
     * these views and a screenshot cannot show it.
     *
     * @param progress position through the move, 0 to 1
     * @return the fraction of the distance covered, 0 to 1
     */
    static double chaseFraction(double progress) {
        return smoothStep(Math.clamp(
                (progress - CHASE_STARTS_AT) / (1 - CHASE_STARTS_AT), 0, 1));
    }

    /**
     * How far the camera has swung round on a loop back.
     *
     * <p>Nothing, then all of it in a quarter of the move, then nothing again. Past the last node
     * is the first node and it costs one pointer hop, not a drive back down the whole road - so
     * the shot has to be a whip rather than a journey, or the picture would claim the wrong
     * complexity.
     *
     * @param progress position through the move, 0 to 1
     * @return the fraction of the distance covered, 0 to 1
     */
    static double whipFraction(double progress) {
        return smoothStep(Math.clamp((progress - WHIP_STARTS_AT) / WHIP_LENGTH, 0, 1));
    }

    /**
     * How far from his mark the racer is, as a fraction of a full exit from the frame.
     *
     * <p>Up steeply to the peak, then back down over the longer tail while the camera reels him
     * in. Smoothed at both ends so he neither jerks off the mark nor slams back onto it.
     *
     * @param progress position through the move, 0 to 1
     * @return 0 on the mark, 1 fully clear of the frame
     */
    static double burstFraction(double progress) {
        double leg = progress <= BURST_PEAK_AT
                ? progress / BURST_PEAK_AT
                : (1 - progress) / (1 - BURST_PEAK_AT);
        return smoothStep(Math.clamp(leg, 0, 1));
    }

    /**
     * @param t position in 0..1
     * @return the same position eased in and out
     */
    static double smoothStep(double t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Where the racer is drawn across the canvas.
     *
     * @param width     canvas width
     * @param kartWidth how wide the sprite is drawn
     * @return his screen x
     */
    private double racerScreenX(double width, double kartWidth) {
        if (!isAnimating()) {
            return RACER_SCREEN_X;
        }
        double reach = width - RACER_SCREEN_X + kartWidth;
        double p = progress();
        return switch (move) {
            case FORWARD -> RACER_SCREEN_X + burstFraction(p) * reach;
            case BACKWARD -> RACER_SCREEN_X - burstFraction(p) * (RACER_SCREEN_X + kartWidth);
            case LOOP_BACK -> loopScreenX(p, width, kartWidth);
            case NONE -> RACER_SCREEN_X;
        };
    }

    /**
     * Where the racer is while coming round the loop: away off the right, then in from the left.
     *
     * <p>He never drives back down the road. He leaves at one end and arrives at the other, which
     * is what a circular list's {@code next()} does at the tail.
     */
    private double loopScreenX(double p, double width, double kartWidth) {
        double whipAt = WHIP_STARTS_AT + WHIP_LENGTH;
        if (p < whipAt) {
            double leg = smoothStep(Math.clamp(p / whipAt, 0, 1));
            return RACER_SCREEN_X + leg * (width - RACER_SCREEN_X + kartWidth);
        }
        double leg = smoothStep(Math.clamp((p - whipAt) / (1 - whipAt), 0, 1));
        return -kartWidth + leg * (RACER_SCREEN_X + kartWidth);
    }

    /**
     * @return how high above the road the racer is lifted, in pixels; only the loop lifts him
     */
    private double loopLift() {
        if (!isAnimating() || move != Move.LOOP_BACK) {
            return 0;
        }
        return -LOOP_ARC * Math.sin(Math.PI * Math.clamp(progress(), 0, 1));
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    protected final void draw(GraphicsContext gc, double width, double height) {
        double roadY = height * 0.60;
        // The cap keeps the road from swallowing a tall canvas, but it has to stay above the
        // kart's height on the full stage or he overhangs both kerbs at once.
        double roadHeight = Math.min(MAX_ROAD_HEIGHT, height * 0.28);
        double camera = cameraX();

        drawRoad(gc, width, roadY, roadHeight, camera);
        drawMarkers(gc, width, roadY, camera);
        drawRacer(gc, width, roadY);
        drawMiniMap(gc, width);
        drawHud(gc, width, height);
    }

    private void drawRoad(GraphicsContext gc, double width, double roadY, double roadHeight,
                          double camera) {
        gc.setFill(color(PaletteRole.BACKGROUND_ALT));
        gc.fillRect(0, roadY + roadHeight, width, Math.max(0, canvas().getHeight() - roadY));

        gc.setFill(color(PaletteRole.SURFACE_RAISED));
        gc.fillRect(0, roadY, width, roadHeight);

        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        gc.strokeLine(0, Math.round(roadY) + 0.5, width, Math.round(roadY) + 0.5);
        gc.strokeLine(0, Math.round(roadY + roadHeight) + 0.5, width,
                Math.round(roadY + roadHeight) + 0.5);

        // The surface slides whatever else is happening: he is always driving. It is offset by the
        // camera as well, so a song change scrolls it that much further in one go.
        double surface = camera + clockSeconds() * CRUISE_SCROLL;

        gc.setFill(color(PaletteRole.TEXT_DIM, 0.55));
        paintStripes(gc, width, surface, Math.round(roadY + roadHeight / 2), 18, 14, 2);

        gc.setFill(color(PaletteRole.TEXT_DIM, 0.28));
        paintStripes(gc, width, surface, Math.round(roadY) + 2, 10, 12, 2);
        paintStripes(gc, width, surface, Math.round(roadY + roadHeight) - 4, 10, 12, 2);
    }

    /**
     * Paints one row of evenly spaced dashes, positioned in world coordinates.
     *
     * <p>Anchored to the world rather than to the canvas, so the dashes slide continuously
     * instead of restarting from the left edge on every frame.
     */
    private void paintStripes(GraphicsContext gc, double width, double scroll,
                              double y, double dash, double gap, double height) {
        double period = dash + gap;
        double first = Math.floor(scroll / period) * period;
        for (double worldX = first; worldX < scroll + width + period; worldX += period) {
            gc.fillRect(Math.round(worldX - scroll), y, dash, height);
        }
    }

    /**
     * Draws one roadside marker per song on the road.
     *
     * <p>Only the markers inside the camera's stretch of road are drawn, so five hundred songs
     * cost the same to render as five.
     */
    private void drawMarkers(GraphicsContext gc, double width, double roadY, double camera) {
        List<Song> songs = roadSongs();
        int base = firstSlot();
        for (int i = 0; i < songs.size(); i++) {
            double x = (base + i) * SLOT_SPACING - camera;
            if (x < -SLOT_SPACING || x > width + SLOT_SPACING) {
                continue;
            }
            drawMarker(gc, i + 1, songs.get(i), x, roadY, flagFor(i, songs.size()),
                    base + i == slot);
        }
    }

    private void drawMarker(GraphicsContext gc, int position, Song song,
                            double x, double roadY, String flag, boolean isCurrent) {
        // Capped rather than filling the slot: with the markers spread wide for the speed match, a
        // board the width of a whole slot is a billboard, and the gaps between them are what make
        // the road read as being covered.
        double signWidth = Math.min(SLOT_SPACING - 22, SIGN_MAX_WIDTH);
        double signHeight = 30;
        double signY = roadY - signHeight - SIGN_POST_HEIGHT;

        // The post, then the board, so a marker reads as standing beside the road. The board sits
        // well clear of the surface: level with the road it reads as lying on the tarmac, and it
        // crowds the kart, who is a head taller than the road is deep.
        gc.setFill(color(PaletteRole.OUTLINE));
        gc.fillRect(Math.round(x - 1), signY + signHeight, 2, roadY - signY - signHeight + 6);

        PaletteRole face = isCurrent ? PaletteRole.SURFACE_RAISED
                : flag == null ? PaletteRole.SURFACE : PaletteRole.BACKGROUND_ALT;
        PaletteRole edge = isCurrent ? PaletteRole.ACCENT
                : flag == null ? PaletteRole.OUTLINE : PaletteRole.PRIMARY;
        drawPanel(gc, x - signWidth / 2, signY, signWidth, signHeight, face, edge);

        String number = String.valueOf(position);
        drawText(gc, number, x - signWidth / 2 + 6, signY + 13, TEXT_SIZE,
                flag == null ? PaletteRole.TEXT_DIM : PaletteRole.PRIMARY);
        drawText(gc, fit(song.getTitle(), signWidth - 12, TEXT_SIZE),
                x - signWidth / 2 + 6, signY + 25, TEXT_SIZE, PaletteRole.TEXT_PRIMARY);

        if (flag != null) {
            drawText(gc, flag, x - signWidth / 2 + 6 + textWidth(number, TEXT_SIZE) + 8,
                    signY + 13, TEXT_SIZE, PaletteRole.PRIMARY);
        }
    }

    /**
     * Draws the racer, who holds his mark and lets the road do the moving.
     *
     * <p>He rides up and down as he goes, which is what stops a sprite pinned to one spot reading
     * as a sticker on the glass, and he faces the way he is travelling.
     */
    private void drawRacer(GraphicsContext gc, double width, double roadY) {
        SpriteSheet kart = assets.racer(state.getRacer());
        // 1:1 in the panel, doubled on the full stage. There is no size in between: the frames are
        // 64px square and pixel art may only be scaled by whole numbers, so the sizes available
        // are 64, 128 and 192 and nothing else. At 128 he stood taller than the road he was
        // driving on.
        int scale = canvas().getHeight() >= LARGE_CANVAS_HEIGHT ? 2 : 1;
        double drawWidth = kart.frameWidth() * scale;
        double drawHeight = kart.frameHeight() * scale;

        double x = racerScreenX(width, drawWidth);
        // Sitting on the road rather than floating over it: the sprite's wheels meet the surface.
        double y = roadY - drawHeight * 0.62 + bob(scale) + loopLift();

        // Reversing is the one time the art is mirrored: he is driving back the way he came, which
        // a circular list can do in one hop and a queue cannot do at all.
        boolean facingLeft = isAnimating() && move == Move.BACKWARD;
        drawSprite(gc, kart, racerFrame(), x - drawWidth / 2, y, scale, facingLeft);
    }

    /**
     * The wheels never stop on a road. Overridden because the base class freezes a settled racer,
     * which is right for the tree - where parking at a junction means something - and wrong here,
     * where he is travelling through a running order the whole time.
     *
     * @return the sheet frame to draw
     */
    @Override
    protected int racerFrame() {
        return RacerFrame.driving(clockSeconds());
    }

    /**
     * @param scale the sprite's magnification
     * @return the vertical offset of the driving bob, in pixels
     */
    private double bob(int scale) {
        // A gentle bob at all times, deepening with the burst - which is zero at both ends of a
        // move, so it swells and settles without a jump.
        double intensity = 0.45 + 0.55 * (isAnimating() ? burstFraction(progress()) : 0);
        return Math.sin(clockSeconds() * BOB_RATE * Math.PI) * BOB_PIXELS * scale * intensity;
    }

    /**
     * Draws the structure itself as a small chain above the road.
     *
     * <p>The road shows the songs; this shows the <em>shape holding them</em> - nodes as
     * {@code +}, links as {@code ->}, the racer's node ringed. Without it the view is a list of
     * songs on posts and the structure is implied; with it the thing being demonstrated is on
     * screen next to the thing demonstrating it. On a ring the chain closes with a return arc, so
     * the list's circularity is visible even while the racer is driving a straight.
     */
    private void drawMiniMap(GraphicsContext gc, double width) {
        List<Song> songs = roadSongs();
        if (songs.isEmpty()) {
            return;
        }
        int size = songs.size();
        int here = Math.clamp(slot - firstSlot(), 0, Math.max(0, size - 1));

        int shown = Math.min(MINI_MAP_NODES, size);
        int start = Math.clamp(here - shown / 2, 0, Math.max(0, size - shown));

        double left = 10;
        // Every node glyph is three characters wide - "(+)" when it is his, " + " when it is not -
        // so the chain stays evenly spaced instead of shuffling sideways as he moves along it.
        double nodeWidth = textWidth("(+)", TEXT_SIZE);
        double linkWidth = textWidth("->", TEXT_SIZE);

        drawText(gc, "LINKS", left, MINI_MAP_CAPTION_Y, TEXT_SIZE, PaletteRole.TEXT_DIM);

        double x = left;
        for (int i = start; i < start + shown; i++) {
            boolean isHere = i == here;
            drawText(gc, isHere ? "(+)" : " + ", x, MINI_MAP_Y, TEXT_SIZE,
                    isHere ? PaletteRole.ACCENT : PaletteRole.TEXT_DIM);
            x += nodeWidth;
            if (i < start + shown - 1) {
                drawText(gc, "->", x, MINI_MAP_Y, TEXT_SIZE, PaletteRole.TEXT_DIM);
                x += linkWidth;
            }
        }

        boolean atEnd = start + shown >= size;
        if (wrapsAround()) {
            // Drawn whether or not the tail is in the window: the ring closes regardless of where
            // the racer happens to be standing, and that is the point being made.
            drawWrapArc(gc, left, x, MINI_MAP_Y);
        } else if (!atEnd) {
            drawText(gc, " ...", x, MINI_MAP_Y, TEXT_SIZE, PaletteRole.TEXT_DIM);
        }
    }

    /**
     * Draws the return link from the last node back to the first: up, across and down.
     *
     * <p>The same shape the racer flies when he comes round, so the diagram and the animation are
     * telling the same story.
     */
    private void drawWrapArc(GraphicsContext gc, double startX, double endX, double y) {
        double top = y - 14;
        double right = endX + 8;
        gc.setStroke(color(PaletteRole.PRIMARY, 0.85));
        gc.setLineWidth(1);
        gc.strokeLine(right, y - 4, right, top);
        gc.strokeLine(right, top, startX + 2, top);
        gc.strokeLine(startX + 2, top, startX + 2, y - 8);
        // An arrowhead on the way down, so the link reads as pointing back to the head.
        gc.setFill(color(PaletteRole.PRIMARY, 0.85));
        gc.fillPolygon(new double[] {startX - 1, startX + 5, startX + 2},
                new double[] {y - 9, y - 9, y - 4}, 3);
    }

    private void drawHud(GraphicsContext gc, double width, double height) {
        drawText(gc, heading(), 10, 16, HEADING_SIZE, PaletteRole.PRIMARY);
        drawText(gc, tagline(), 10, 30, TEXT_SIZE, taglineRole());

        // The ground runs under both of these lines, so they get their own band to stay readable.
        gc.setFill(color(PaletteRole.BACKGROUND, 0.92));
        gc.fillRect(0, height - 34, width, 34);

        Song current = currentSong();
        String now = "NOW DRIVING  " + (current == null ? "-" : current.getTitle());
        drawText(gc, fit(now, width - 20, TEXT_SIZE), 10, height - 24, TEXT_SIZE,
                PaletteRole.TEXT_PRIMARY);
        drawText(gc, footer(), 10, height - 10, TEXT_SIZE, PaletteRole.TEXT_DIM);
    }
}
