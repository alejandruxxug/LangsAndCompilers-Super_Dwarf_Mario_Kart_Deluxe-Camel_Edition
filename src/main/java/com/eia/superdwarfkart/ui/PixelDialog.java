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
        dragBy(bar, stage);
        return bar;
    }

    /**
     * Makes a node drag the window it belongs to, the way a system title bar would.
     *
     * <p>Every window this application opens is undecorated, so every one of them has to supply
     * this. It lives here, and is called from here, so that there is one implementation rather than
     * one per window - the companion strip drags itself through this method too.
     *
     * <p>The offset between the pointer and the window origin is taken on press and held for the
     * duration of the drag. Without it the window jumps so that its corner lands under the pointer
     * the moment the mouse moves.
     *
     * @param handle the node to drag by, usually a title bar; must not be {@code null}
     * @param stage  the window it moves; must not be {@code null}
     */
    public static void dragBy(Node handle, Stage stage) {
        java.util.Objects.requireNonNull(handle, "handle must not be null");
        java.util.Objects.requireNonNull(stage, "stage must not be null");
        double[] offset = new double[2];
        handle.setOnMousePressed(event -> {
            offset[0] = event.getScreenX() - stage.getX();
            offset[1] = event.getScreenY() - stage.getY();
        });
        handle.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - offset[0]);
            stage.setY(event.getScreenY() - offset[1]);
        });
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

    /**
     * Shows the dialog without waiting for it, and hands back its scene.
     *
     * <p><strong>For photographing a dialog, and nothing else.</strong> Layout overflow in a
     * fixed-width pixel font is invisible to every unit test - a caption that ran off the side still
     * draws, still throws nothing and still looks deliberate - so the only check for it is a picture,
     * and {@link #showAndWait()} cannot be photographed because it does not return until the dialog is
     * closed. The stage is still modal; what changes is only that the caller is not blocked.
     *
     * @return the dialog's scene, laid out and on screen
     */
    public Scene showForCapture() {
        stage.sizeToScene();
        centerOnOwner();
        stage.show();
        shell.applyCss();
        shell.layout();
        return stage.getScene();
    }

    /** Closes the dialog from code, as the cancel button would. */
    public void close() {
        cancel();
    }

    /**
     * Resizes the open window around contents that have changed size.
     *
     * <p>{@link #showAndWait()} sizes the window to its contents once, on the way up. A dialog whose
     * body grows afterwards - a search that came back with ten rows where the last one had two, a form
     * that appeared once something was picked - gets no second pass on its own: a toolkit does not
     * shrink a window because its contents stopped filling it, and it will happily clip contents that
     * outgrew it. That is the same fault the companion window's compact strip had to fix with
     * {@code sizeToScene}, in a different window.
     *
     * <p>The layout pass has to come first, or the window is sized against the previous contents.
     * Deliberately does not re-centre: a window that jumped every time a result list changed length
     * would be worse than one that grows downwards from where the user left it.
     */
    public void resizeToContent() {
        if (!stage.isShowing()) {
            return;
        }
        shell.applyCss();
        shell.layout();
        stage.sizeToScene();
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
