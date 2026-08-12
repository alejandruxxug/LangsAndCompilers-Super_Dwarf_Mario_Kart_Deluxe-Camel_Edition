package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.assets.SpriteSheet;
import javafx.animation.AnimationTimer;
import javafx.scene.image.ImageView;

/**
 * Shows one frame of a {@link SpriteSheet}, advancing through the frames over time.
 *
 * <p>Every animated sprite in the interface shares a <strong>single</strong> timer, held
 * statically here. One timer ticking a counter is far cheaper than a timeline per sprite, and
 * it matters because these appear once per visible table row.
 *
 * <p>Each view carries a phase offset, so a column of sprites shimmers instead of beating in
 * lockstep.
 */
public class SpriteView extends ImageView {

    /** Animation rate. Sprite art of this kind reads best well below the display refresh rate. */
    private static final long FRAME_INTERVAL_NANOS = 90_000_000L;

    /** Shared frame counter, advanced by the single timer below. */
    private static int sharedFrame;

    private static AnimationTimer ticker;

    /** Views currently on screen and animating. */
    private static final java.util.List<SpriteView> ANIMATING = new java.util.ArrayList<>();

    private final SpriteSheet sheet;
    private final int phase;

    private boolean animating;

    /**
     * Builds a view of a sheet, scaled to the given height.
     *
     * @param sheet  the frames to show; must not be {@code null}
     * @param size   displayed height in pixels, with the aspect ratio preserved
     * @param phase  frame offset, so neighbouring sprites are not in step
     */
    public SpriteView(SpriteSheet sheet, double size, int phase) {
        this.sheet = sheet;
        this.phase = phase;

        setImage(sheet.image());
        setViewport(sheet.viewport(phase));
        setFitHeight(size);
        setFitWidth(size * (sheet.frameWidth() / sheet.frameHeight()));
        setPreserveRatio(true);
        // Pixel art must not be interpolated when scaled, or it turns to mush.
        setSmooth(false);
    }

    /** Starts advancing this view's frames. Safe to call more than once. */
    public void startAnimating() {
        if (animating) {
            return;
        }
        animating = true;
        ANIMATING.add(this);
        ensureTickerRunning();
    }

    /** Stops advancing this view's frames and parks it on a full-face frame. */
    public void stopAnimating() {
        if (!animating) {
            return;
        }
        animating = false;
        ANIMATING.remove(this);
        setViewport(sheet.viewport(phase));
    }

    /** Shows a specific frame without animating. */
    void showFrame(int frameIndex) {
        setViewport(sheet.viewport(frameIndex));
    }

    private void update() {
        setViewport(sheet.viewport(sharedFrame + phase));
    }

    private static synchronized void ensureTickerRunning() {
        if (ticker != null) {
            return;
        }
        ticker = new AnimationTimer() {
            private long lastAdvance;

            @Override
            public void handle(long now) {
                if (now - lastAdvance < FRAME_INTERVAL_NANOS) {
                    return;
                }
                lastAdvance = now;
                sharedFrame++;
                // Iterate a copy: a view may stop animating while being updated.
                for (SpriteView view : java.util.List.copyOf(ANIMATING)) {
                    view.update();
                }
            }
        };
        ticker.start();
    }
}
