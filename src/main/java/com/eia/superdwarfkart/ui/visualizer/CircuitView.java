package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.app.AppState;
import com.eia.superdwarfkart.assets.AssetRegistry;
import com.eia.superdwarfkart.model.ModeId;
import com.eia.superdwarfkart.model.Song;
import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.playback.ShuffleMode;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The circular doubly linked list as a road the racer can drive both ways.
 *
 * <p>The same straight the queue uses, and the differences are exactly the structure's:
 *
 * <ul>
 *   <li><strong>He can go back.</strong> {@code previous()} reverses him down the road, one
 *       marker, in one move - because the list is doubly linked and stepping back is a single
 *       pointer hop. A singly linked ring would have to drive the whole lap to get there.</li>
 *   <li><strong>The road has no end.</strong> Driving off the last marker he takes an up-turn,
 *       flies back over the road and lands on the first - and the return is a blur, because past
 *       the tail is the head and it costs one hop, not a journey. Reversing off the first marker
 *       does the same thing the other way.</li>
 * </ul>
 *
 * <p>That loop is the whole reason this mode is backed by a ring rather than a list, and it is
 * demonstrated rather than captioned. The mini-map above the road closes with the same return
 * arc the racer flies, so the diagram and the animation say the same thing.
 *
 * <p>The shuffle itself is baked into the ring once at load, so the order of the markers along
 * the road <em>is</em> the running order - and going back really does return to the song played
 * before, rather than to a fresh random pick.
 */
public class CircuitView extends RoadView {

    private final ShuffleMode mode;

    private final Tooltip tooltip = new Tooltip();

    /** One lap of the ring, from the head, rebuilt whenever the structure changes. */
    private List<Song> lap = List.of();

    /** Laps completed since the mode was built. */
    private int laps;

    /**
     * Builds the circuit.
     *
     * @param mode   the shuffle mode whose ring is drawn; must not be {@code null}
     * @param state  supplies the selected racer; must not be {@code null}
     * @param assets supplies the kart artwork; must not be {@code null}
     */
    public CircuitView(ShuffleMode mode, AppState state, AssetRegistry assets) {
        super(state, assets);
        this.mode = Objects.requireNonNull(mode, "mode must not be null");

        getStyleClass().add("circuit-view");

        tooltip.setShowDelay(Duration.millis(120));
        tooltip.setText("Circular doubly linked list.\n"
                + "Both directions cost one hop, and the road has no end.");
        Tooltip.install(canvas(), tooltip);

        rebuildLap();
        jumpTo(Math.max(0, indexOfCurrent()));
    }

    @Override
    public ModeId modeId() {
        return ModeId.SHUFFLE;
    }

    /**
     * Drives the racer to wherever the ring's cursor now is.
     *
     * <p>The direction is worked out from the indices rather than being told: one marker forward
     * is a drive on, one back is a reverse, and the wrap at either end is the loop. Anything else
     * - the user picking a song from the library, or the ring being rebuilt - is a jump, because
     * {@code select} is not a walk along the road either.
     */
    @Override
    public void refresh() {
        int before = slot();
        int size = lap.size();
        rebuildLap();
        int target = indexOfCurrent();

        if (target < 0 || lap.isEmpty() || lap.size() != size) {
            jumpTo(Math.max(0, target));
            return;
        }
        if (target == before) {
            redraw();
            return;
        }

        int n = lap.size();
        if (target == before + 1) {
            driveTo(target, Move.FORWARD);
        } else if (target == before - 1) {
            driveTo(target, Move.BACKWARD);
        } else if (before == n - 1 && target == 0) {
            // Off the end and round to the start: the move this whole view exists to show.
            laps++;
            driveTo(target, Move.LOOP_BACK);
        } else if (before == 0 && target == n - 1) {
            // The same wrap in reverse: before the head is the tail.
            laps = Math.max(0, laps - 1);
            driveTo(target, Move.LOOP_BACK);
        } else {
            jumpTo(target);
        }
    }

    private void rebuildLap() {
        List<Song> songs = new ArrayList<>(mode.size());
        // The iterator walks exactly one lap from the head, so this terminates on an endless ring.
        for (Song song : mode.ring()) {
            songs.add(song);
        }
        lap = songs;
    }

    private int indexOfCurrent() {
        Song current = mode.current();
        return current == null ? -1 : lap.indexOf(current);
    }

    // ------------------------------------------------------------------
    // What the road needs to know
    // ------------------------------------------------------------------

    @Override
    protected List<Song> roadSongs() {
        return lap;
    }

    @Override
    protected int firstSlot() {
        // The lap is laid out from the head, so the first song is slot zero and the racer's slot
        // is his position in the ring.
        return 0;
    }

    @Override
    protected Song currentSong() {
        return mode.current();
    }

    @Override
    protected boolean wrapsAround() {
        return true;
    }

    @Override
    protected String heading() {
        return "THE LAP - CircularDoublyLinkedList";
    }

    @Override
    protected String tagline() {
        return "No end - past the last is the first, in one hop.";
    }

    @Override
    protected String footer() {
        if (lap.isEmpty()) {
            return "No songs loaded. Add songs to build the ring.";
        }
        return "n = " + lap.size() + "   position " + (slot() + 1)
                + "   lap " + (laps + 1) + "   next/previous O(1)";
    }

    /**
     * The head is where a lap starts and the tail is the node whose {@code next} is the head, so
     * both are worth flying - the pair of them is the join the loop closes.
     */
    @Override
    protected String flagFor(int index, int size) {
        if (index == 0) {
            return "HEAD";
        }
        return index == size - 1 ? "TAIL" : null;
    }

    @Override
    protected PaletteRole taglineRole() {
        return PaletteRole.ACCENT;
    }
}
