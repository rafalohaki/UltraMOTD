package org.rafalohaki.ultramotd.config;

/**
 * JSON builder using Java 21 string templates for optimized JSON generation.
 * Provides faster JSON building compared to traditional String.format.
 */
public class JsonBuilder {

    /**
     * Builds status response JSON using string templates (preview feature).
     * Faster than String.format for JSON building with multiple variables.
     * <p>
     * Note: Requires --enable-preview flag for Java 21 string templates
     */
    public String buildStatusJson(String versionName, int protocol, int online, int max, String description) {
        // String template (preview) - faster than String.format
        return STR."""
            {
              "version": {"name": "\{versionName}", "protocol": \{protocol}},
              "players": {"max": \{max}, "online": \{online}, "sample": []},
              "description": {"text": "\{description}"}
            }
            """;
    }

    /**
     * Builds status JSON with favicon using string templates.
     */
    public String buildStatusJsonWithFavicon(String versionName, int protocol, int online, int max,
                                             String description, String faviconBase64) {
        return STR."""
            {
              "version": {"name": "\{versionName}", "protocol": \{protocol}},
              "players": {"max": \{max}, "online": \{online}, "sample": []},
              "description": {"text": "\{description}"},
              "favicon": "\{faviconBase64}"
            }
            """;
    }

    /**
     * Fallback method for environments without preview features enabled.
     * Uses traditional String.format as fallback.
     */
    public String buildStatusJsonFallback(String versionName, int protocol, int online, int max,
                                          String description) {
        return String.format(
                "{\"version\":{\"name\":\"%s\",\"protocol\":%d},\"players\":{\"max\":%d,\"online\":%d,\"sample\":[]},\"description\":{\"text\":\"%s\"}}",
                versionName, protocol, max, online, description
        );
    }

    /**
     * Fallback method with favicon for environments without preview features.
     */
    public String buildStatusJsonWithFaviconFallback(String versionName, int protocol, int online, int max,
                                                     String description, String faviconBase64) {
        return String.format(
                "{\"version\":{\"name\":\"%s\",\"protocol\":%d},\"players\":{\"max\":%d,\"online\":%d,\"sample\":[]},\"description\":{\"text\":\"%s\"},\"favicon\":\"%s\"}",
                versionName, protocol, max, online, description, faviconBase64
        );
    }
}
