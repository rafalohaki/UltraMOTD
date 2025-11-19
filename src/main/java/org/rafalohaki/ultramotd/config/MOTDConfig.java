package org.rafalohaki.ultraMOTD.config;

import net.kyori.adventure.text.Component;

/**
 * Configuration record for MOTD settings using Java 21 record patterns.
 * Provides zero-cost deserialization and compact representation.
 */
public record MOTDConfig(
        Component description,
        int maxPlayers,
        boolean enableFavicon,
        String faviconPath,
        boolean enableVirtualThreads
) {

    /**
     * Validates configuration parameters.
     */
    public MOTDConfig {
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("maxPlayers must be positive");
        }
        if (description == null) {
            throw new IllegalArgumentException("description cannot be null");
        }
    }

    /**
     * Creates a default MOTD configuration.
     */
    public static MOTDConfig getDefault() {
        return new MOTDConfig(
                Component.text("§aUltraMOTD §7- §bHigh Performance MOTD"),
                100,
                true,
                "favicons/default.png",
                true
        );
    }
}
