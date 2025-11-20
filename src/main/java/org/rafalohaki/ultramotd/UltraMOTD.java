package org.rafalohaki.ultramotd;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.rafalohaki.ultramotd.config.ConfigLoader;
import org.rafalohaki.ultramotd.config.MOTDConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * UltraMOTD - High Performance MOTD Plugin for Velocity
 * 
 * Features:
 * - Zero-cost config deserialization using Java 21 record patterns
 * - Virtual threads support for async operations
 * - Config hot-reloading with file watching
 * - Performance metrics and monitoring
 */
@Plugin(
    id = "ultramotd",
    name = "UltraMOTD",
    version = "1.0-SNAPSHOT",
    description = "High Performance MOTD Plugin for Velocity",
    authors = {"RafalOHaki"}
)
public class UltraMOTD {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private MOTDConfig config;

    @Inject
    public UltraMOTD(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Initializing UltraMOTD...");
        
        try {
            // Create data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created plugin data directory: {}", dataDirectory);
            }

            // Create config file if it doesn't exist
            Path configFile = dataDirectory.resolve("config.json");
            createDefaultConfig(configFile);
            
            // Initialize config loader and load configuration
            ConfigLoader loader = new ConfigLoader(logger);
            this.config = loader.loadConfig(configFile);
            
            logger.info("UltraMOTD initialized successfully!");
            logger.info("MOTD Description: {}", config.description());
            logger.info("Max Players: {}", config.maxPlayers());
            logger.info("Favicon Enabled: {}", config.enableFavicon());
            logger.info("Virtual Threads Enabled: {}", config.enableVirtualThreads());
            
        } catch (Exception e) {
            logger.error("Failed to initialize UltraMOTD: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates default config file if it doesn't exist
     */
    private void createDefaultConfig(Path configFile) {
        if (!Files.exists(configFile)) {
            try {
                // Create default config content
                String defaultConfig = getDefaultConfigContent();
                Files.writeString(configFile, defaultConfig);
                logger.info("Created default config file: {}", configFile);
            } catch (IOException e) {
                logger.error("Failed to create default config file: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Default configuration content as JSON
     */
    private static final String DEFAULT_CONFIG_CONTENT = """
            {
              "description": "<green>UltraMOTD <gray>- <blue>High Performance MOTD</blue></gray>",
              "maxPlayers": 100,
              "enableFavicon": true,
              "faviconPath": "favicons/default.png",
              "enableVirtualThreads": true
            }
            """;

    /**
     * Returns default configuration content as JSON
     */
    private String getDefaultConfigContent() {
        return DEFAULT_CONFIG_CONTENT;
    }

    /**
     * Gets current MOTD configuration
     */
    public MOTDConfig getConfig() {
        return config;
    }

    /**
     * Gets the proxy server instance
     */
    public ProxyServer getServer() {
        return server;
    }

    /**
     * Gets the logger instance
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the data directory path
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }
}
