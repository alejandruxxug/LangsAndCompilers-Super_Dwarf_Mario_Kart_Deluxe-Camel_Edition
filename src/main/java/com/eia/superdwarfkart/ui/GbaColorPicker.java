package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.mood.GbaColor;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

/**
 * Picks a colour on the Game Boy Advance's own grid: three channels of thirty-two levels.
 *
 * <p>JavaFX has a {@code ColorPicker} and it is deliberately not used, for the same reason no other
 * stock control survives in this interface - it brings its own chrome, and its popup is a separate
 * scene of rounded corners and gradients in the middle of an application of hard-edged blocks.
 *
 * <p>But there is a better reason than the look. <strong>The channels run 0 to 31, not 0 to 255</strong>,
 * because that is what the hardware has. A picker offering 256 levels would let the user choose
 * values that cannot be represented and then quietly move them, which is the arrangement where
 * somebody nudges a slider and nothing happens - seven times out of eight. Here every step is a step
 * the machine can draw, the swatch shows exactly what will be stored, and the hex readout is the
 * value that lands in the mood file.
 */
public final class GbaColorPicker {

    private static final double SWATCH = 64;

    private GbaColorPicker() {
        throw new AssertionError("GbaColorPicker is a utility holder and must not be instantiated");
    }

    /**
     * Opens the picker and waits for an answer.
     *
     * @param owner   the window to centre on; may be {@code null}
     * @param role    the role being recoloured, shown as the title
     * @param initial the colour to start from; must not be {@code null}
     * @return the chosen colour, or {@code null} when the user cancelled
     */
    public static Color pick(Window owner, PaletteRole role, Color initial) {
        PixelDialog dialog = new PixelDialog(owner, role.displayName().toUpperCase());

        Region preview = new Region();
        preview.setMinSize(SWATCH * 3, SWATCH);
        preview.setPrefSize(SWATCH * 3, SWATCH);
        preview.getStyleClass().add("picker-swatch");

        Label readout = new Label();
        readout.getStyleClass().add("settings-value");

        Slider red = channel(GbaColor.quantize(eightBit(initial.getRed())));
        Slider green = channel(GbaColor.quantize(eightBit(initial.getGreen())));
        Slider blue = channel(GbaColor.quantize(eightBit(initial.getBlue())));

        Color[] chosen = {GbaColor.snap(initial)};
        Runnable refresh = () -> {
            Color color = GbaColor.of(
                    GbaColor.expand((int) red.getValue()),
                    GbaColor.expand((int) green.getValue()),
                    GbaColor.expand((int) blue.getValue()));
            chosen[0] = color;
            // Set with setStyle rather than through the stylesheet: an author stylesheet outranks a
            // programmatic value in JavaFX, and this swatch has to show a colour that is not the
            // active mood's.
            preview.setStyle("-fx-background-color: " + GbaColor.toHex(color) + ";");
            readout.setText(GbaColor.toHex(color) + "   "
                    + (int) red.getValue() + "," + (int) green.getValue() + ","
                    + (int) blue.getValue() + " of " + GbaColor.MAX_LEVEL);
        };
        red.valueProperty().addListener((observable, was, now) -> refresh.run());
        green.valueProperty().addListener((observable, was, now) -> refresh.run());
        blue.valueProperty().addListener((observable, was, now) -> refresh.run());
        refresh.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(caption("RED"), 0, 0);
        grid.add(red, 1, 0);
        grid.add(caption("GREEN"), 0, 1);
        grid.add(green, 1, 1);
        grid.add(caption("BLUE"), 0, 2);
        grid.add(blue, 1, 2);

        Label note = new Label("Five bits a channel: 32 levels, 32,768 colours.\n"
                + "Every step here is one the hardware could draw.");
        note.getStyleClass().add("panel-caption");

        VBox content = new VBox(12, preview, readout, grid, note);
        content.setPadding(new Insets(4));
        dialog.setContent(content);

        return dialog.showAndWait() ? chosen[0] : null;
    }

    private static Slider channel(int value) {
        Slider slider = new Slider(0, GbaColor.MAX_LEVEL, value);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(8);
        slider.setMinorTickCount(7);
        slider.setSnapToTicks(true);
        slider.setPrefWidth(220);
        return slider;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-caption");
        return label;
    }

    private static int eightBit(double channel) {
        return (int) Math.round(Math.clamp(channel, 0d, 1d) * 255);
    }
}
