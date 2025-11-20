package org.rafalohaki.ultramotd.config;

/**
 * Custom exception for configuration parsing errors.
 * Provides specific context for YAML/JSON configuration failures.
 */
public class ConfigParseException extends RuntimeException {
    
    public ConfigParseException(String message) {
        super(message);
    }
    
    public ConfigParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
