package org.rafalohaki.ultramotd.config;

/**
 * JSON builder using String.format for optimized JSON generation.
 * Provides reliable JSON building without requiring preview features.
 */
public class JsonBuilder {

    /**
     * Builds status response JSON using String.format.
     * Compatible with all Java versions without preview features.
     */
    public String buildStatusJson(String versionName, int protocol, int online, int max, String description) {
        return String.format("""
            {
              "version": {"name": "%s", "protocol": %d},
              "players": {"max": %d, "online": %d, "sample": []},
              "description": {"text": "%s"}
            }
            """, versionName, protocol, max, online, description);
    }

    /**
     * Builds status JSON with favicon using String.format.
     */
    public String buildStatusJsonWithFavicon(String versionName, int protocol, int online, int max,
                                             String description, String faviconBase64) {
        return String.format("""
            {
              "version": {"name": "%s", "protocol": %d},
              "players": {"max": %d, "online": %d, "sample": []},
              "description": {"text": "%s"},
              "favicon": "%s"
            }
            """, versionName, protocol, max, online, description, faviconBase64);
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
