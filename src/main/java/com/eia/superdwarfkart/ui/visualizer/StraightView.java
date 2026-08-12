package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.playback.ArrivalOrderMode;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The queue as a one-way straight.
 *
 * <p>All the driving is inherited; what this class contributes is the one thing that makes it a
 * queue - <strong>the camera only ever moves forward.</strong> Songs that have played slide off
 * the left edge and there is no shot that brings them back, because there is no operation that
 * would. The previous control is disabled while this view is on screen, and nothing here animates
 * backwards: not because the animation was left unwritten, but because a first-in-first-out queue
 * has no way to produce it. The circular list, on the same road, can and does.
 *
 * <p>Consuming this view consumes only this mode's queue. The library still holds every song,
 * which is the invariant the whole design is arranged around - a road that empties must never
 * mean a library that emptied.
 */
public class StraightView extends RoadView {

    private final ArrivalOrderMode mode;

    private final Tooltip tooltip = new Tooltip();

    /** The queue as last drawn, front first. */
    private List<Song> displayed = List.of();

    /**
     * Builds the straight.
     *
     * @param mode   the arrival-order mode whose queue is drawn; must not be {@code null}
     * @param state  supplies the selected racer; must not be {@code null}
     * @param assets supplies the kart artwork; must not be {@code null}
     */
    public StraightView(ArrivalOrderMode mode, AppState state, AssetRegistry assets) {
        super(state, assets);
        this.mode = Objects.requireNonNull(mode, "mode must not be null");

        getStyleClass().add("straight-view");

        tooltip.setShowDelay(Duration.millis(120));
        tooltip.setText("FIFO - no going back.\nThe camera only ever moves forward.");
        Tooltip.install(canvas(), tooltip);

        displayed = snapshot();
        // The racer sits one slot behind the front of the queue: the song he is on has already
        // left it. Nothing has been dequeued yet, so that is slot -1.
        jumpTo(-1);
    }

    @Override
    public ModeId modeId() {
        return ModeId.ARRIVAL_ORDER;
    }

    @Override
    public void refresh() {
        List<Song> next = snapshot();
        int dropped = droppedFromFront(displayed, next);
        displayed = next;

        if (dropped < 0) {
            // A rebuild: start the road over from the beginning.
            jumpTo(-1);
            return;
        }
        if (dropped == 0) {
            // An enqueue. The front is untouched, so nobody drives anywhere.
            redraw();
            return;
        }
        // Selecting a song further down the queue drains everything in front of it, so the racer
        // can cover several slots at once - and does it in one continuous run, which is what
        // skipping ahead through a queue actually costs.
        driveTo(slot() + dropped, Move.FORWARD);
    }

    /**
     * Reports how many elements were taken off the front.
     *
     * @param before the queue as it was
     * @param after  the queue as it now is
     * @return the number dropped from the front, 0 if nothing was, or -1 if the change was not a
     *         removal from the front at all and the view should start over
     */
    private static int droppedFromFront(List<Song> before, List<Song> after) {
        if (after.equals(before)) {
            return 0;
        }
        if (after.size() < before.size()
                && before.subList(before.size() - after.size(), before.size()).equals(after)) {
            return before.size() - after.size();
        }
        // A pure append is an enqueue: the front is untouched, so nobody drives anywhere.
        if (after.size() > before.size() && after.subList(0, before.size()).equals(before)) {
            return 0;
        }
        return -1;
    }

    /**
     * Copies the queue front to back.
     *
     * <p>Iterating a {@code SimpleQueue} does not consume it, which is the only reason this view
     * can be drawn at all: a visualizer that had to dequeue to see its contents would destroy the
     * running order every time the window repainted.
     *
     * @return the waiting songs, front first
     */
    private List<Song> snapshot() {
        List<Song> songs = new ArrayList<>(mode.size());
        for (Song song : mode.queue()) {
            songs.add(song);
        }
        return songs;
    }

    // ------------------------------------------------------------------
    // What the road needs to know
    // ------------------------------------------------------------------

    @Override
    protected List<Song> roadSongs() {
        return displayed;
    }

    @Override
    protected int firstSlot() {
        // The front of the queue stands one slot ahead of the racer, always.
        return slot() + 1;
    }

    @Override
    protected Song currentSong() {
        return mode.current();
    }

    @Override
    protected String heading() {
        return "THE STRAIGHT - SimpleQueue";
    }

    @Override
    protected String tagline() {
        return "FIFO - the camera only moves forward.";
    }

    @Override
    protected PaletteRole taglineRole() {
        return PaletteRole.NEGATIVE;
    }

    @Override
    protected String footer() {
        return displayed.isEmpty()
                ? "The road is clear - every song has played."
                : "n = " + displayed.size() + " waiting   enqueue O(1)   dequeue O(1)";
    }

    /**
     * Both ends of this structure are kept as pointers and both are constant time, so both are
     * flown on the road. Nothing else in the queue can be reached without walking to it.
     */
    @Override
    protected String flagFor(int index, int size) {
        if (index == 0) {
            return "HEAD";
        }
        return index == size - 1 ? "TAIL" : null;
    }
}
