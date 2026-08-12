package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.mood.PaletteRole;
import com.eia.superdwarfkart.ui.Fonts;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Measured steps plotted against n, accumulating across every operation, with the theoretical
 * curves drawn behind the points.
 *
 * <p>This is where the argument stops being a claim. Each point is one operation that actually
 * ran; the two curves are {@code log n} and {@code n} on the same axes. On a five hundred song
 * library the tree's searches sit on the flat curve near the bottom of the plot while the
 * circular list's ride the diagonal, and the separation is visible from across a room in a way
 * that a table of numbers is not.
 *
 * <p>Points are coloured by structure and shaped by structure as well - square, diamond and
 * cross. Colour alone fails for a colourblind viewer and for a projector with poor gamma, which
 * is the same reason coins and obstacles in the game must differ by more than hue.
 */
public class ComplexityScatter extends Pane {

    /** Left margin, leaving room for the steps axis labels. */
    private static final double PAD_LEFT = 26;

    /** Bottom margin, leaving room for the n axis labels. */
    private static final double PAD_BOTTOM = 16;

    private static final double PAD_TOP = 16;
    private static final double PAD_RIGHT = 8;

    /** Half the width of a plotted point. */
    private static final double POINT = 2.5;

    private static final double TEXT_SIZE = 7;

    /**
     * Operations left off the plot.
     *
     * <p>Building a mode is not one operation, it is n of them - inserting a whole library. Its
     * cost is therefore an order of magnitude above every navigation on the same axes, and
     * including it pins the y scale so high that the log-n and n curves collapse onto each other
     * at the bottom, which is the one thing this plot exists to separate. The build still gets
     * its measured value on the complexity table, where the row says what it is.
     *
     * <p>This is a presentation decision and so it lives in the view rather than in the counter.
     */
    private static final Set<String> BULK_OPERATIONS = Set.of("build");

    private final Canvas canvas = new Canvas();
    private final OperationCounter counter;

    /**
     * Builds the plot and starts following the counter.
     *
     * @param counter supplies the measurements; must not be {@code null}
     */
    public ComplexityScatter(OperationCounter counter) {
        this.counter = Objects.requireNonNull(counter, "counter must not be null");

        getStyleClass().add("complexity-scatter");
        canvas.setManaged(false);
        getChildren().add(canvas);

        counter.addListener(measurement -> redraw());
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width != canvas.getWidth() || height != canvas.getHeight()) {
            canvas.setWidth(width);
            canvas.setHeight(height);
        }
        redraw();
    }

    /** Repaints the plot from whatever the counter has recorded so far. */
    public void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        gc.setFill(StructureView.color(PaletteRole.BACKGROUND));
        gc.fillRect(0, 0, width, height);

        List<Measurement> samples = new ArrayList<>();
        int maxN = 1;
        int maxSteps = 1;
        for (Measurement sample : counter.samples()) {
            if (BULK_OPERATIONS.contains(sample.operation())) {
                continue;
            }
            samples.add(sample);
            maxN = Math.max(maxN, sample.n());
            maxSteps = Math.max(maxSteps, sample.steps());
        }

        drawAxes(gc, width, height, maxN, maxSteps);
        drawTheoreticalCurves(gc, width, height, maxN, maxSteps);
        drawPoints(gc, width, height, samples, maxN, maxSteps);

        if (samples.isEmpty()) {
            gc.setFont(Fonts.pixel(TEXT_SIZE));
            gc.setFill(StructureView.color(PaletteRole.TEXT_DIM));
            gc.fillText("Play a song to measure.", PAD_LEFT + 6, height / 2);
        }
    }

    private void drawAxes(GraphicsContext gc, double width, double height, int maxN, int maxSteps) {
        gc.setStroke(StructureView.color(PaletteRole.OUTLINE));
        gc.setLineWidth(1);
        gc.strokeLine(PAD_LEFT + 0.5, PAD_TOP, PAD_LEFT + 0.5, height - PAD_BOTTOM + 0.5);
        gc.strokeLine(PAD_LEFT + 0.5, height - PAD_BOTTOM + 0.5, width - PAD_RIGHT, height - PAD_BOTTOM + 0.5);

        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(StructureView.color(PaletteRole.TEXT_DIM));
        gc.fillText("steps", 2, PAD_TOP - 6);
        gc.fillText(String.valueOf(maxSteps), 2, PAD_TOP + 6);
        gc.fillText("0", 2, height - PAD_BOTTOM);
        gc.fillText("n = " + maxN, width - PAD_RIGHT - TEXT_SIZE * (5 + String.valueOf(maxN).length()),
                height - 4);
    }

    /**
     * Draws {@code log n} and {@code n} on the same axes as the measurements.
     *
     * <p>Both are scaled to the plot's own maximum rather than to their own, so a point sitting
     * on a curve means the operation really did cost what that complexity predicts.
     */
    private void drawTheoreticalCurves(GraphicsContext gc, double width, double height,
                                       int maxN, int maxSteps) {
        gc.setLineWidth(1);
        gc.setLineDashes(2, 3);

        gc.setStroke(StructureView.color(PaletteRole.TEXT_DIM, 0.55));
        strokeCurve(gc, width, height, maxN, maxSteps, n -> (double) n);

        gc.setStroke(StructureView.color(PaletteRole.POSITIVE, 0.55));
        strokeCurve(gc, width, height, maxN, maxSteps, n -> n <= 1 ? 0 : Math.log(n) / Math.log(2));

        gc.setLineDashes(null);

        gc.setFont(Fonts.pixel(TEXT_SIZE));
        gc.setFill(StructureView.color(PaletteRole.TEXT_DIM, 0.8));
        gc.fillText("O(n)", PAD_LEFT + 6, PAD_TOP + 8);
        gc.setFill(StructureView.color(PaletteRole.POSITIVE, 0.8));
        gc.fillText("O(log n)", PAD_LEFT + 6, height - PAD_BOTTOM - 4);
    }

    private void strokeCurve(GraphicsContext gc, double width, double height,
                             int maxN, int maxSteps, java.util.function.IntToDoubleFunction curve) {
        gc.beginPath();
        boolean started = false;
        for (int n = 0; n <= maxN; n = Math.max(n + 1, (int) (n * 1.05))) {
            double steps = curve.applyAsDouble(n);
            if (steps > maxSteps) {
                break;
            }
            double x = plotX(width, n, maxN);
            double y = plotY(height, steps, maxSteps);
            if (started) {
                gc.lineTo(x, y);
            } else {
                gc.moveTo(x, y);
                started = true;
            }
        }
        if (started) {
            gc.stroke();
        }
    }

    private void drawPoints(GraphicsContext gc, double width, double height,
                            List<Measurement> samples, int maxN, int maxSteps) {
        for (Measurement sample : samples) {
            double x = plotX(width, sample.n(), maxN);
            double y = plotY(height, sample.steps(), maxSteps);
            gc.setFill(colorFor(sample.structure()));
            drawMarker(gc, sample.structure(), x, y);
        }
    }

    /**
     * Draws one point, shaped by structure so the three are distinguishable without colour.
     */
    private void drawMarker(GraphicsContext gc, String structure, double x, double y) {
        switch (structure) {
            case "BinarySearchTree" -> {
                // A diamond.
                gc.fillPolygon(
                        new double[] {x, x + POINT + 1, x, x - POINT - 1},
                        new double[] {y - POINT - 1, y, y + POINT + 1, y}, 4);
            }
            case "SimpleQueue" -> {
                // A cross.
                gc.fillRect(x - POINT - 1, y - 0.5, (POINT + 1) * 2, 1.5);
                gc.fillRect(x - 0.5, y - POINT - 1, 1.5, (POINT + 1) * 2);
            }
            default -> gc.fillRect(x - POINT, y - POINT, POINT * 2, POINT * 2);
        }
    }

    private Color colorFor(String structure) {
        return switch (structure) {
            case "BinarySearchTree" -> StructureView.color(PaletteRole.POSITIVE);
            case "SimpleQueue" -> StructureView.color(PaletteRole.PRIMARY);
            default -> StructureView.color(PaletteRole.ACCENT);
        };
    }

    private double plotX(double width, double n, int maxN) {
        double usable = width - PAD_LEFT - PAD_RIGHT;
        return PAD_LEFT + (maxN == 0 ? 0 : n / maxN * usable);
    }

    private double plotY(double height, double steps, int maxSteps) {
        double usable = height - PAD_TOP - PAD_BOTTOM;
        return height - PAD_BOTTOM - (maxSteps == 0 ? 0 : Math.min(1, steps / maxSteps) * usable);
    }
}
