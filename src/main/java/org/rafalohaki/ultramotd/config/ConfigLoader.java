package org.rafalohaki.ultramotd.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration loader using Java 21 record patterns for zero-cost deserialization.
 * Provides type-safe configuration loading with fallback to defaults.
 */
public class ConfigLoader {

    private static final Gson GSON = new Gson();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Logger logger;

    public ConfigLoader(Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads MOTD configuration from file using record patterns for efficient deserialization.
     * Falls back to default configuration if loading fails.
     */
    public MOTDConfig loadConfig(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                logger.info("Config file not found at {}, using defaults", configPath);
                return MOTDConfig.getDefault();
            }

            String content = Files.readString(configPath);
            Object parsed = parseConfig(content);

            // Record patterns - zero-cost deserialization
            if (parsed instanceof MOTDConfig(var desc, var max, var favicon, var path, var virtual)) {
                logger.info("Configuration loaded successfully from {}", configPath);
                return new MOTDConfig(desc, max, favicon, path, virtual);
            }

            logger.warn("Invalid configuration format in {}, using defaults", configPath);
            return MOTDConfig.getDefault();

        } catch (Exception e) {
            logger.error("Failed to load configuration from {}, using defaults: {}",
                    configPath, e.getMessage(), e);
            return MOTDConfig.getDefault();
        }
    }

    /**
     * Parses configuration content into appropriate object type.
     */
    private Object parseConfig(String content) {
        try {
            JsonElement json = GSON.fromJson(content, JsonElement.class);
            if (json instanceof JsonObject obj) {
                Component description = parseComponent(obj.get("description"));
                int maxPlayers = obj.has("maxPlayers") ?
                        obj.get("maxPlayers").getAsInt() : 100;
                boolean enableFavicon = !obj.has("enableFavicon") || obj.get("enableFavicon").getAsBoolean();
                String faviconPath = obj.has("faviconPath") ?
                        obj.get("faviconPath").getAsString() : "favicons/default.png";
                boolean enableVirtualThreads = !obj.has("enableVirtualThreads") || obj.get("enableVirtualThreads").getAsBoolean();

                return new MOTDConfig(description, maxPlayers, enableFavicon, faviconPath, enableVirtualThreads);
            }
        } catch (Exception e) {
            logger.debug("JSON parsing failed, trying simple format: {}", e.getMessage());
        }

        // Fallback to simple format or default
        return MOTDConfig.getDefault();
    }

    /**
     * Parses text component from JSON configuration.
     */
    private Component parseComponent(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Component.text("§aUltraMOTD §7- §bHigh Performance MOTD");
        }

        String text = element.getAsString();
        try {
            // Try MiniMessage format first
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            // Fallback to plain text with legacy formatting
            return Component.text(translateLegacyColors(text));
        }
    }

    /**
     * Translates legacy color codes (&) to modern format.
     */
    private String translateLegacyColors(String text) {
        return text.replace("&", "§");
    }
}
