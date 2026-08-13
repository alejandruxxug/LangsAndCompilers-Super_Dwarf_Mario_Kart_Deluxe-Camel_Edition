package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteCss;
import javafx.collections.ObservableList;
import javafx.scene.Scene;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Applies the interface's two stylesheets: the fixed one that shapes every control, and the
 * generated one that colours it from the active mood.
 *
 * <p>{@code app.css} names colours but defines none. Every one of them is a looked-up colour
 * supplied by {@link PaletteCss}, which means a scene given the first stylesheet and not the
 * second would come up with nothing resolving. So the two are never handed out separately:
 * {@link #apply(Scene)} installs both, and it is the single funnel every window in the
 * application already goes through.
 *
 * <p>Dialogs do not inherit their owner's stylesheets, so anything that opens its own scene has to
 * be styled explicitly. That is the reason this class exists at all, and it is now also the reason
 * a mood switch reaches everything: each list that was ever styled is remembered, so
 * {@link #setPalette} can swap the generated stylesheet in all of them at once.
 *
 * <p>The lists are held <em>weakly</em>. A dialog is styled, shown, closed and dropped, and a
 * strong reference here would keep every dialog the session ever opened alive for the sake of
 * restyling windows nobody can see.
 *
 * <p>A missing {@code app.css} is logged once and then ignored: the application runs unstyled
 * rather than refusing to start (ground rule 5).
 */
public final class Theme {

    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    /** Prefix identifying the generated stylesheet, so a swap can find the one to replace. */
    private static final String GENERATED_PREFIX = "data:text/css";

    private static final List<WeakReference<ObservableList<String>>> styled = new ArrayList<>();

    private static String cachedUrl;
    private static boolean resolved;
    private static String paletteUrl;

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
     * Styles a scene and keeps it in step with later mood changes.
     *
     * @param scene the scene to style; ignored when {@code null}
     */
    public static void apply(Scene scene) {
        if (scene != null) {
            apply(scene.getStylesheets());
        }
    }

    /**
     * Adds both stylesheets to any list of stylesheet URLs, such as a dialog scene's.
     *
     * @param stylesheets the list to add to; ignored when {@code null}
     */
    public static synchronized void apply(ObservableList<String> stylesheets) {
        if (stylesheets == null) {
            return;
        }
        String url = stylesheetUrl();
        if (url != null && !stylesheets.contains(url)) {
            stylesheets.add(url);
        }
        installPalette(stylesheets);
        remember(stylesheets);
    }

    /**
     * Installs a palette as the one the whole interface draws with, canvases and controls alike.
     *
     * <p>Sets the active palette and regenerates the colour stylesheet in every window that has
     * been styled. Canvases read {@link Palette#active()} on their next repaint, so a caller that
     * wants the change to be visible immediately still has to redraw them - the controls restyle
     * themselves.
     *
     * @param palette the palette to install; {@code null} restores the default
     */
    public static synchronized void setPalette(Palette palette) {
        Palette.setActive(palette);
        paletteUrl = PaletteCss.stylesheetUrl(Palette.active());

        Iterator<WeakReference<ObservableList<String>>> each = styled.iterator();
        while (each.hasNext()) {
            ObservableList<String> stylesheets = each.next().get();
            if (stylesheets == null) {
                each.remove();
            } else {
                installPalette(stylesheets);
            }
        }
    }

    /** @return the URL of the generated colour stylesheet currently in force */
    public static synchronized String paletteStylesheetUrl() {
        if (paletteUrl == null) {
            paletteUrl = PaletteCss.stylesheetUrl(Palette.active());
        }
        return paletteUrl;
    }

    /**
     * Replaces whatever generated stylesheet a list holds with the current one.
     *
     * <p>The old entry is removed rather than left in place: two colour stylesheets in one list
     * would resolve by order, so the palette would depend on how many times the mood had been
     * changed.
     */
    private static void installPalette(ObservableList<String> stylesheets) {
        String wanted = paletteStylesheetUrl();
        stylesheets.removeIf(entry -> entry.startsWith(GENERATED_PREFIX) && !entry.equals(wanted));
        if (!stylesheets.contains(wanted)) {
            stylesheets.add(wanted);
        }
    }

    private static void remember(ObservableList<String> stylesheets) {
        for (WeakReference<ObservableList<String>> reference : styled) {
            if (reference.get() == stylesheets) {
                return;
            }
        }
        styled.removeIf(reference -> reference.get() == null);
        styled.add(new WeakReference<>(stylesheets));
    }

    private Theme() {
        throw new AssertionError("Theme is a utility holder and must not be instantiated");
    }
}
