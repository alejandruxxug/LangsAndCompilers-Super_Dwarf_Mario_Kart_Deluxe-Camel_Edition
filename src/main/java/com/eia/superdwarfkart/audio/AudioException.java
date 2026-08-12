package com.eia.superdwarfkart.audio;

/**
 * Thrown when an audio file cannot be opened, decoded or sent to the sound card.
 *
 * <p>Unchecked, for the same reason
 * {@link com.eia.superdwarfkart.persistence.PersistenceException} is: the only callers that can do
 * anything useful about it are a handful of user interface entry points, which catch it and show a
 * message. A missing or unreadable file is an ordinary state in this application - the library
 * stores paths, and paths go stale - so nothing above this layer may treat it as fatal.
 */
public class AudioException extends RuntimeException {

    /**
     * @param message what failed, phrased so the interface can display it unchanged
     */
    public AudioException(String message) {
        super(message);
    }

    /**
     * @param message what failed, phrased so the interface can display it unchanged
     * @param cause   the underlying failure
     */
    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
