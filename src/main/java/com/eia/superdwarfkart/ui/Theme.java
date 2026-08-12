package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import javafx.collections.ObservableList;
import javafx.scene.Scene;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Applies the bundled stylesheet to scenes and dialogs.
 *
 * <p>Dialogs do not inherit their owner's stylesheets, so anything that opens its own dialog
 * pane has to be styled explicitly through {@link #apply(ObservableList)}.
 *
 * <p>A missing stylesheet is logged once and then ignored: the application runs unstyled rather
 * than refusing to start.
 */
public final class Theme {

    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    private static String cachedUrl;
    private static boolean resolved;

    /**
     * @return the stylesheet URL, or {@code null} when it could not be found
     */
    public static synchronized String stylesheetUrl() {
        if (!resolved) {
            resolved = true;
            URL url = Theme.class.getResource(AppConfig.STYLESHEET_RESOURCE);
            if (url == null) {
                LOG.warning("Stylesheet not found at " + AppConfig.STYLESHEET_RESOURCE
                        + " - the interface will run unstyled");
            } else {
                cachedUrl = url.toExternalForm();
            }
        }
        return cachedUrl;
    }

    /**
     * Styles a scene.
     *
     * @param scene the scene to style; ignored when {@code null}
     */
    public static void apply(Scene scene) {
        if (scene != null) {
            apply(scene.getStylesheets());
        }
    }

    /**
     * Adds the stylesheet to any list of stylesheet URLs, such as a dialog pane's.
     *
     * @param stylesheets the list to add to; ignored when {@code null}
     */
    public static void apply(ObservableList<String> stylesheets) {
        String url = stylesheetUrl();
        if (stylesheets != null && url != null && !stylesheets.contains(url)) {
            stylesheets.add(url);
        }
    }

    private Theme() {
        throw new AssertionError("Theme is a utility holder and must not be instantiated");
    }
}
