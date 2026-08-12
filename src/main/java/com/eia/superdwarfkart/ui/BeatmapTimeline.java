package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.analysis.Beatmap;
import com.eia.superdwarfkart.analysis.BeatmapService;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.playback.PlaybackEngine;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * Shows what the beat analyser found in the current track, and whether it is right.
 *
 * <p>The tempo and the onset count say the analysis ran. <strong>The lamp says it is correct.</strong>
 * It lights on every strong beat as the playhead reaches it, so a person watching the window while
 * the music plays can see in two seconds whether the detected beat is the beat they are hearing -
 * which is the one thing neither a unit test nor a screenshot can establish. Everything downstream
 * of M6 is built on those times being right, and a course generated from a grid half a beat out
 * looks perfectly plausible until somebody tries to play it.
 *
 * <p>The onsets are drawn as ticks along the track, with the ones that landed on the tempo grid
 * taller and brighter. Seeing them evenly spaced through a song, and clustered where the drums are,
 * is the second check: an analysis that produced noise looks like noise here.
 *
 * <p>Polls {@link BeatmapService} rather than being called back by it, in the same arrangement the
 * level meters use. <strong>Repaints only when something visible changed</strong> - a playhead that
 * has not moved a whole pixel, on a lamp that has not switched, over a beatmap that has not
 * changed, is the same picture, and a long track advances one pixel about six times a second.
 * Redrawing a thousand ticks at sixty frames a second to show that would be most of a core.
 */
public class BeatmapTimeline extends Pane {

    /** Height of the whole strip, in pixels. */
    public static final double PREFERRED_HEIGHT = 58;

    /** Padding inside the strip. */
    private static final double PADDING = 10;

    /** Label size. Whole pixels: a fractional pixel font is blurry. */
    private static final double LABEL_SIZE = 7;

    /** Height of the timeline track. */
    private static final double TRACK_HEIGHT = 18;

    /**
     * Height of an ordinary onset tick, as a fraction of the track.
     *
     * <p>Kept well under half. A dense track puts an onset on nearly every pixel, and at equal
     * heights the strip fills in solid and the beat structure - the thing worth looking at -
     * disappears into it. Short ticks along the bottom read as density; the full-height strong
     * beats stand clear of them.
     */
    private static final double ONSET_TICK = 0.38;

    /** How long the lamp stays lit after a strong beat, in seconds. */
    private static final double FLASH_SECONDS = 0.12;

    /** Side of the beat lamp, in pixels. */
    private static final double LAMP_SIZE = 8;

    /** How bright the unplayed part of the track is drawn, against the played part. */
    private static final double UNPLAYED_OPACITY = 0.38;

    /** Width of the playhead, in pixels. Two, so it is findable against a bar full of ticks. */
    private static final double PLAYHEAD_WIDTH = 2;

    private final Canvas canvas = new Canvas();
    private final BeatmapService service;
    private final PlaybackEngine engine;

    private AnimationTimer timer;

    /** What was last drawn, so an identical frame can be skipped. */
    private BeatmapService.Status drawnStatus;
    private int drawnPlayheadPixel = -1;
    private boolean drawnLamp;
    private double drawnWidth;
    private double drawnHeight;

    /**
     * @param service where beatmaps come from; must not be {@code null}
     * @param engine  the transport, for the playhead; must not be {@code null}
     */
    public BeatmapTimeline(BeatmapService service, PlaybackEngine engine) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        getStyleClass().add("beatmap-timeline");
        setMinHeight(PREFERRED_HEIGHT);
        setPrefHeight(PREFERRED_HEIGHT);
        setMaxHeight(PREFERRED_HEIGHT);
        canvas.setManaged(false);
        getChildren().add(canvas);
    }

    /** Begins polling the analyser and repainting. */
    public void start() {
        if (timer != null) {
            return;
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        };
        timer.start();
    }

    /** Stops polling. Called on shutdown, so no timer repaints a window nobody can see. */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /**
     * Repaints, but only when the picture would actually differ.
     *
     * <p>The status is compared by identity, which is exactly right: {@link BeatmapService.Status}
     * is an immutable snapshot and the service publishes a new object for every change, so this
     * catches a progress step and a finished analysis alike without comparing fields.
     */
    private void tick() {
        BeatmapService.Status current = service.status();
        double position = engine.position().toSeconds();
        int playheadPixel = (int) Math.round(playheadX(current, position));
        boolean lamp = lampLit(current, position);

        if (current == drawnStatus
                && playheadPixel == drawnPlayheadPixel
                && lamp == drawnLamp
                && getWidth() == drawnWidth
                && getHeight() == drawnHeight) {
            return;
        }
        drawnStatus = current;
        drawnPlayheadPixel = playheadPixel;
        drawnLamp = lamp;
        redraw();
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width != canvas.getWidth() || height != canvas.getHeight()) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            redraw();
        }
    }

    /** Repaints the strip. */
    public void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        drawnWidth = getWidth();
        drawnHeight = getHeight();

        BeatmapService.Status status = service.status();
        double position = engine.position().toSeconds();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(color(PaletteRole.SURFACE));
        gc.fillRect(0, 0, width, height);
        gc.setFont(Fonts.pixel(LABEL_SIZE));

        drawHeader(gc, status, position, width);
        drawTrack(gc, status, position, width, height);
    }

    /**
     * Draws the caption line: the lamp, the tempo, the counts and the state.
     *
     * @param gc       the context to draw into
     * @param status   what the analyser knows
     * @param position where playback has reached, in seconds
     * @param width    the strip's width
     */
    private void drawHeader(GraphicsContext gc, BeatmapService.Status status, double position,
                            double width) {
        double baseline = PADDING + LABEL_SIZE;
        double x = PADDING;

        gc.setFill(color(PaletteRole.TEXT_DIM));
        gc.fillText("BEATMAP", x, baseline);
        x += text("BEATMAP") + 14;

        // The lamp, and then the tempo it is flashing at.
        boolean lit = lampLit(status, position);
        gc.setFill(color(lit ? PaletteRole.HIGHLIGHT : PaletteRole.SURFACE_RAISED));
        gc.fillRect(x, baseline - LAMP_SIZE + 1, LAMP_SIZE, LAMP_SIZE);
        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        gc.strokeRect(x + 0.5, baseline - LAMP_SIZE + 1.5, LAMP_SIZE - 1, LAMP_SIZE - 1);
        x += LAMP_SIZE + 10;

        Beatmap beatmap = status.beatmap();
        gc.setFill(color(PaletteRole.TEXT_PRIMARY));
        String tempo = beatmap.hasTempo()
                ? String.format("%.1f BPM", beatmap.bpm())
                : "-- BPM";
        gc.fillText(tempo, x, baseline);
        x += text(tempo) + 16;

        gc.setFill(color(PaletteRole.TEXT_DIM));
        String counts = beatmap.onsetCount() + " ONSETS  " + beatmap.strongBeatCount() + " ON BEAT";
        gc.fillText(counts, x, baseline);
        x += text(counts) + 16;

        gc.setFill(color(status.stage() == BeatmapService.Stage.FAILED
                ? PaletteRole.NEGATIVE
                : PaletteRole.TEXT_DIM));
        gc.fillText(describe(status, width - x - PADDING), x, baseline);
    }

    /**
     * Renders the state in words.
     *
     * @param status    what the analyser knows
     * @param available how much room the message has, in pixels
     * @return the message, shortened to fit
     */
    private static String describe(BeatmapService.Status status, double available) {
        String message = switch (status.stage()) {
            case NONE -> "NO TRACK";
            case ANALYZING -> String.format("ANALYSING %.0f%%", status.progress() * 100);
            case READY -> status.fromCache() ? "CACHED" : "ANALYSED";
            case FAILED -> "FAILED: " + status.failure();
        };
        // Press Start 2P is fixed-width at about one em per glyph, so the room a string needs can
        // be counted rather than measured - and a message that overflows is invisible to a test.
        int fits = (int) Math.max(0, available / LABEL_SIZE);
        if (message.length() <= fits) {
            return message;
        }
        return fits <= 3 ? "" : message.substring(0, fits - 3) + "...";
    }

    /**
     * Draws the timeline: the onsets, the beats that fell on the grid, and the playhead.
     *
     * @param gc       the context to draw into
     * @param status   what the analyser knows
     * @param position where playback has reached, in seconds
     * @param width    the strip's width
     * @param height   the strip's height
     */
    private void drawTrack(GraphicsContext gc, BeatmapService.Status status, double position,
                           double width, double height) {
        double top = height - PADDING - TRACK_HEIGHT;
        double left = PADDING;
        double trackWidth = width - PADDING * 2;
        if (trackWidth <= 0) {
            return;
        }

        gc.setFill(color(PaletteRole.BACKGROUND));
        gc.fillRect(left, top, trackWidth, TRACK_HEIGHT);

        if (status.stage() == BeatmapService.Stage.ANALYZING) {
            gc.setFill(color(PaletteRole.PRIMARY_DIM));
            gc.fillRect(left, top, trackWidth * Math.clamp(status.progress(), 0d, 1d), TRACK_HEIGHT);
        } else {
            drawTicks(gc, status.beatmap(), position, left, top, trackWidth);
        }

        gc.setStroke(color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        gc.strokeRect(left + 0.5, top + 0.5, trackWidth - 1, TRACK_HEIGHT - 1);

        double playhead = playheadX(status, position);
        if (playhead >= 0) {
            // Drawn last and over the border, so it is never buried by the ticks it sits among.
            gc.setFill(color(PaletteRole.PRIMARY));
            gc.fillRect(Math.min(Math.round(playhead), left + trackWidth - PLAYHEAD_WIDTH), top,
                    PLAYHEAD_WIDTH, TRACK_HEIGHT);
        }
    }

    /**
     * Draws one tick per onset, and a taller one per strong beat.
     *
     * <p>Ticks already played are drawn at full strength and those still to come are dimmed, so the
     * strip doubles as a progress bar without a separate fill over the top of the data.
     *
     * <p>Consecutive onsets landing on the same pixel are drawn once. A four-minute track holds
     * more onsets than the strip has pixels, and filling the same one-pixel rectangle repeatedly
     * costs real time for a picture that cannot change.
     *
     * @param gc         the context to draw into
     * @param beatmap    what to draw
     * @param position   where playback has reached, in seconds
     * @param left       left edge of the track
     * @param top        top edge of the track
     * @param trackWidth width of the track
     */
    private void drawTicks(GraphicsContext gc, Beatmap beatmap, double position,
                           double left, double top, double trackWidth) {
        double duration = beatmap.durationSeconds();
        if (duration <= 0 || beatmap.isEmpty()) {
            return;
        }

        Color played = color(PaletteRole.ACCENT);
        Color upcoming = color(PaletteRole.ACCENT, UNPLAYED_OPACITY);
        double tickHeight = TRACK_HEIGHT * ONSET_TICK;
        double tickTop = top + TRACK_HEIGHT - tickHeight;

        int lastPixel = Integer.MIN_VALUE;
        Color lastColor = null;
        for (int index = 0; index < beatmap.onsetCount(); index++) {
            double time = beatmap.onsetAt(index);
            int pixel = (int) Math.round(left + time / duration * trackWidth);
            Color face = time <= position ? played : upcoming;
            if (pixel == lastPixel && face == lastColor) {
                continue;
            }
            lastPixel = pixel;
            lastColor = face;
            gc.setFill(face);
            gc.fillRect(pixel, tickTop, 1, tickHeight);
        }

        Color beatPlayed = color(PaletteRole.HIGHLIGHT);
        Color beatUpcoming = color(PaletteRole.HIGHLIGHT, UNPLAYED_OPACITY);
        lastPixel = Integer.MIN_VALUE;
        lastColor = null;
        for (int index = 0; index < beatmap.strongBeatCount(); index++) {
            double time = beatmap.strongBeatAt(index);
            int pixel = (int) Math.round(left + time / duration * trackWidth);
            Color face = time <= position ? beatPlayed : beatUpcoming;
            if (pixel == lastPixel && face == lastColor) {
                continue;
            }
            lastPixel = pixel;
            lastColor = face;
            gc.setFill(face);
            gc.fillRect(pixel, top + 2, 1, TRACK_HEIGHT - 4);
        }
    }

    /**
     * @param status   what the analyser knows
     * @param position where playback has reached, in seconds
     * @return the playhead's x coordinate, or {@code -1} when there is nothing to place it against
     */
    private double playheadX(BeatmapService.Status status, double position) {
        double duration = trackSeconds(status);
        if (duration <= 0) {
            return -1;
        }
        double trackWidth = getWidth() - PADDING * 2;
        return PADDING + Math.clamp(position / duration, 0d, 1d) * trackWidth;
    }

    /**
     * @param status what the analyser knows
     * @return the length the strip is scaled to, in seconds
     */
    private double trackSeconds(BeatmapService.Status status) {
        double analysed = status.beatmap().durationSeconds();
        // The analysed length is the one the ticks are drawn against, so the playhead must use it
        // too; the transport's figure is only a stand-in while there is nothing to draw.
        return analysed > 0 ? analysed : engine.duration().toSeconds();
    }

    /**
     * @param status   what the analyser knows
     * @param position where playback has reached, in seconds
     * @return whether the beat lamp should be lit
     */
    private boolean lampLit(BeatmapService.Status status, double position) {
        if (!status.isReady() || !engine.isPlaying()) {
            return false;
        }
        double last = status.beatmap().lastStrongBeatAtOrBefore(position);
        return last >= 0 && position - last < FLASH_SECONDS;
    }

    /**
     * @param value a string in the pixel font
     * @return how wide it is, in pixels
     */
    private static double text(String value) {
        return value.length() * LABEL_SIZE;
    }

    /**
     * @param role the role to resolve
     * @return the active palette's colour for that role
     */
    private static Color color(PaletteRole role) {
        return Palette.active().color(role);
    }

    /**
     * @param role    the role to resolve
     * @param opacity 0.0 transparent to 1.0 opaque
     * @return the role's colour at that opacity
     */
    private static Color color(PaletteRole role, double opacity) {
        return Palette.active().color(role, opacity);
    }
}
