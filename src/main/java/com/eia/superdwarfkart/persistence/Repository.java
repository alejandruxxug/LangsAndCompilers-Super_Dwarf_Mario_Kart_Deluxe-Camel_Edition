package com.eia.superdwarfkart.persistence;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Reads and writes a collection of domain objects to durable storage.
 *
 * <p>The interface is deliberately small and storage-agnostic. The library and the score board
 * both persist as JSON today, but nothing above this interface knows that.
 *
 * @param <T> the stored type
 */
public interface Repository<T> {

    /**
     * Loads everything from storage.
     *
     * @return the stored items, or an empty list when nothing has been stored yet
     * @throws PersistenceException if the storage exists but cannot be read
     */
    List<T> loadAll();

    /**
     * Writes everything to storage, replacing what was there.
     *
     * @param items the items to store; must not be {@code null}
     * @throws PersistenceException if the write fails
     */
    void saveAll(Collection<? extends T> items);

    /**
     * @return where this repository stores its data, for display in messages and settings
     */
    Path storageLocation();
}
