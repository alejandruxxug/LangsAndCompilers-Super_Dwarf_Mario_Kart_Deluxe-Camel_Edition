package com.eia.superdwarfkart.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * A frameless modal window drawn entirely by this application.
 *
 * <p>Every window the application opens is undecorated: there is no operating system title bar,
 * no native buttons and no platform styling. The title bar, the close button and the border are
 * drawn here in the same 8-bit idiom as the rest of the interface, so a dialog on macOS and a
 * dialog on Windows look identical and neither breaks the theme.
 *
 * <p>Because there is no system chrome, this class has to provide what the chrome used to:
 * dragging by the title bar, a close button, and keyboard dismissal. Escape cancels and Enter
 * accepts.
 *
 * <p>The one window that cannot be styled is the native file chooser, which belongs to the
 * operating system.
 */
public class PixelDialog {

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final BorderPane shell = new BorderPane();
    private final BooleanProperty acceptDisabled = new SimpleBooleanProperty(false);

    private final Button acceptButton = new Button("OK");
    private final Button cancelButton = new Button("CANCEL");

    private boolean accepted;

    /** Offset between the pointer and the window origin while dragging. */
    private double dragOffsetX;
    private double dragOffsetY;

    /**
     * Creates a modal frameless dialog.
     *
     * @param owner the window to centre on and block; may be {@code null}
     * @param title the title drawn in the custom title bar
     */
    public PixelDialog(Window owner, String title) {
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title);
        stage.setResizable(false);

        shell.getStyleClass().add("pixel-window");
        shell.setTop(buildTitleBar(title));
        shell.setBottom(buildButtonBar());

        Scene scene = new Scene(shell);
        // The stage is transparent, so the window's visible edge is the border this draws.
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancel();
            } else if (event.getCode() == KeyCode.ENTER && !acceptDisabled.get()) {
                accept();
            }
        });

        stage.setScene(scene);
    }

    /**
     * Builds the replacement for the system title bar: the title, and a close button, with the
     * whole strip acting as a drag handle.
     *
     * @param title the window title
     * @return the title bar node
     */
    private HBox buildTitleBar(String title) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("pixel-window-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new Button("X");
        close.getStyleClass().addAll("window-button", "close-button");
        close.setOnAction(e -> cancel());
        close.setFocusTraversable(false);

        HBox bar = new HBox(10, titleLabel, spacer, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 8, 8, 12));
        bar.getStyleClass().add("pixel-titlebar");

        // Without system chrome the window cannot be moved unless this is provided.
        bar.setOnMousePressed(event -> {
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });
        return bar;
    }

    private HBox buildButtonBar() {
        acceptButton.setOnAction(e -> accept());
        acceptButton.disableProperty().bind(acceptDisabled);
        cancelButton.setOnAction(e -> cancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, spacer, cancelButton, acceptButton);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(12, 14, 14, 14));
        bar.getStyleClass().add("pixel-window-buttons");
        return bar;
    }

    /**
     * Sets the body of the dialog.
     *
     * @param content the node to show between the title bar and the buttons
     */
    public void setContent(Node content) {
        VBox wrapper = new VBox(content);
        wrapper.getStyleClass().add("pixel-window-content");
        wrapper.setPadding(new Insets(14));
        shell.setCenter(wrapper);
    }

    /**
     * Sets a plain text message as the body, wrapped and styled.
     *
     * @param message the message to show
     */
    public void setMessage(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("pixel-message");
        label.setWrapText(true);
        label.setMaxWidth(460);
        setContent(label);
    }

    /**
     * @param text label for the accepting button
     */
    public void setAcceptText(String text) {
        acceptButton.setText(text);
    }

    /**
     * @param text label for the cancelling button
     */
    public void setCancelText(String text) {
        cancelButton.setText(text);
    }

    /** Removes the cancel button, for dialogs that only report something. */
    public void setAcknowledgeOnly() {
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
    }

    /**
     * @return whether the accepting button is disabled; bind this to a validity expression
     */
    public BooleanProperty acceptDisabledProperty() {
        return acceptDisabled;
    }

    /**
     * Shows the dialog and waits for it to close.
     *
     * @return {@code true} if the user accepted
     */
    public boolean showAndWait() {
        accepted = false;
        stage.sizeToScene();
        centerOnOwner();
        stage.showAndWait();
        return accepted;
    }

    /** Positions the dialog over the middle of its owner, or the screen if it has none. */
    private void centerOnOwner() {
        Window owner = stage.getOwner();
        if (owner == null) {
            stage.centerOnScreen();
            return;
        }
        double width = shell.prefWidth(-1);
        double height = shell.prefHeight(width);
        stage.setX(owner.getX() + (owner.getWidth() - width) / 2);
        stage.setY(owner.getY() + (owner.getHeight() - height) / 2);
    }

    private void accept() {
        accepted = true;
        stage.close();
    }

    private void cancel() {
        accepted = false;
        stage.close();
    }

    // ------------------------------------------------------------------
    // Ready-made message dialogs
    // ------------------------------------------------------------------

    /**
     * Asks the user to confirm an action.
     *
     * @param owner   the window to centre on
     * @param title   the title bar text
     * @param message the question
     * @return {@code true} if the user confirmed
     */
    public static boolean confirm(Window owner, String title, String message) {
        PixelDialog dialog = new PixelDialog(owner, title);
        dialog.setMessage(message);
        dialog.setAcceptText("OK");
        return dialog.showAndWait();
    }

    /**
     * Reports a failure.
     *
     * @param owner   the window to centre on
     * @param title   the title bar text
     * @param message what went wrong
     */
    public static void error(Window owner, String title, String message) {
        PixelDialog dialog = new PixelDialog(owner, title);
        dialog.setMessage(message);
        dialog.setAcceptText("OK");
        dialog.setAcknowledgeOnly();
        dialog.shell.getStyleClass().add("pixel-window-error");
        dialog.showAndWait();
    }

    /**
     * Reports something the user should notice but which is not a failure.
     *
     * @param owner   the window to centre on
     * @param title   the title bar text
     * @param message the notice
     */
    public static void warn(Window owner, String title, String message) {
        PixelDialog dialog = new PixelDialog(owner, title);
        dialog.setMessage(message);
        dialog.setAcceptText("OK");
        dialog.setAcknowledgeOnly();
        dialog.showAndWait();
    }
}
