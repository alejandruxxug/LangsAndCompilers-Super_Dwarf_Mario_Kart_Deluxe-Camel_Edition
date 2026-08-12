package com.eia.superdwarfkart.persistence;

/**
 * Thrown when stored data cannot be read or written.
 *
 * <p>Unchecked on purpose: the callers that can do something useful about it are a small number
 * of user interface entry points, which catch it and show a message. Forcing every intermediate
 * call to declare it would add noise without adding safety.
 */
public class PersistenceException extends RuntimeException {

    /**
     * @param message what failed, in terms the user interface can display
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * @param message what failed, in terms the user interface can display
     * @param cause   the underlying failure
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
