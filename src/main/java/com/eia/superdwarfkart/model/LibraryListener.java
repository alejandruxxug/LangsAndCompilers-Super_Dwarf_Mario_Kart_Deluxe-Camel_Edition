package com.eia.superdwarfkart.model;

/**
 * Notified whenever the library's contents change.
 *
 * <p>This exists so the user interface can refresh without the library knowing anything about
 * the user interface. The domain layer must not import {@code javafx.*}, so the library cannot
 * expose an observable list directly; it publishes plain callbacks instead and the view adapts
 * them into whatever it needs.
 */
@FunctionalInterface
public interface LibraryListener {

    /**
     * Called after the library has changed.
     *
     * @param change what happened
     * @param song   the song involved, or {@code null} for changes that affect the whole library
     */
    void libraryChanged(LibraryChange change, Song song);

    /** The kinds of change a listener can be told about. */
    enum LibraryChange {
        /** A song was added. */
        ADDED,
        /** A song was removed. */
        REMOVED,
        /** A song's metadata was edited. */
        UPDATED,
        /** Every song was replaced at once, for instance by loading from disk. */
        RELOADED
    }
}
