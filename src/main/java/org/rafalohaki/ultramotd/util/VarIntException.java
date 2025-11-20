package org.rafalohaki.ultramotd.util;

/**
 * Exception thrown when VarInt decoding encounters invalid data.
 * Used for protocol violations in Minecraft packet decoding.
 */
public class VarIntException extends RuntimeException {

    public VarIntException(String message) {
        super(message);
    }

    public VarIntException(String message, Throwable cause) {
        super(message, cause);
    }
}
