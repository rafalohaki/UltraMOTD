package org.rafalohaki.ultramotd;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.rafalohaki.ultramotd.config.MOTDConfig;
import org.rafalohaki.ultramotd.config.UltraConfig;
import org.rafalohaki.ultramotd.config.UltraYamlConfigLoader;
import org.rafalohaki.ultramotd.config.ConfigConstants;
import org.rafalohaki.ultramotd.state.UltraMOTDStateMachine;
import org.rafalohaki.ultramotd.cache.FaviconCache;
import org.rafalohaki.ultramotd.cache.JsonCache;

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
 * - High-performance caching with TTL and size limits
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
    private UltraConfig config;
    private UltraMOTDStateMachine stateMachine;
    private Favicon favicon;
    
    // Performance optimization components
    private FaviconCache faviconCache;
    private JsonCache jsonCache;

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
            Path configFile = dataDirectory.resolve(ConfigConstants.CONFIG_FILENAME);
            createDefaultYamlConfig(configFile);
            
            // Initialize config loader and load configuration
            UltraYamlConfigLoader loader = new UltraYamlConfigLoader(logger);
            this.config = loader.loadConfig(configFile);
            refreshFavicon(this.config.motd());
            
            // Initialize performance optimization components
            initializePerformanceComponents();
            
            // Boot state machine for advanced features (rotation, reloads)
            stateMachine = new UltraMOTDStateMachine(logger, configFile);
            stateMachine.start();
            
            logger.info("UltraMOTD initialized successfully!");
            logger.info("MOTD Description: {}", config.motd().description());
            logger.info("Max Players: {}", config.motd().maxPlayers());
            logger.info("Favicon Enabled: {}", config.motd().enableFavicon());
            logger.info("Virtual Threads Enabled: {}", config.motd().enableVirtualThreads());
            
        } catch (Exception e) {
            logger.error("Failed to initialize UltraMOTD: {}", e.getMessage(), e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (stateMachine != null && stateMachine.isRunning()) {
            stateMachine.stop();
        }
        
        // Cleanup performance components
        if (faviconCache != null) {
            faviconCache.clear();
        }
        if (jsonCache != null) {
            jsonCache.clear();
        }
        
        logger.info("UltraMOTD shutdown complete");
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        try {
            MOTDConfig activeMOTD = getActiveMOTDConfig();
            Component description = activeMOTD != null
                    ? activeMOTD.description()
                    : Component.text(ConfigConstants.DEFAULT_MOTD_TEXT);
            
            // Calculate dynamic player count if enabled
            int maxPlayers = calculateMaxPlayers(activeMOTD);

            var builder = event.getPing().asBuilder()
                    .description(description)
                    .maximumPlayers(maxPlayers);

            // Use cached favicon if enabled
            if (activeMOTD != null && activeMOTD.enableFavicon()) {
                Favicon cachedFavicon = getCachedFavicon(activeMOTD);
                if (cachedFavicon != null) {
                    builder.favicon(cachedFavicon);
                }
            }

            event.setPing(builder.build());
            
        } catch (Exception e) {
            logger.error("Error in ping handler: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates default YAML config file if it doesn't exist
     */
    private void createDefaultYamlConfig(Path configFile) {
        if (!Files.exists(configFile)) {
            try {
                String defaultYamlConfig = getDefaultYamlConfigContent();
                Files.writeString(configFile, defaultYamlConfig);
                logger.info("Created default YAML config file: {}", configFile);
            } catch (IOException e) {
                logger.error("Failed to create default YAML config file: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Default configuration content as YAML with improved readability
     */
    private static final String DEFAULT_YAML_CONFIG_CONTENT = """
# UltraMOTD Configuration - High Performance MOTD Plugin
# Documentation: https://github.com/rafalohaki/ultramotd/wiki

# ========================================
# MOTD DISPLAY SETTINGS
# ========================================
motd:
  # Multi-line MOTD support - use \\n for line breaks in MiniMessage format
  description: |
    <green>UltraMOTD <gray>- <blue>High Performance MOTD</blue></gray>
    <gold>Welcome to our server! <gray>• <aqua>Custom plugins</gray> • <green>Active community</green>
  maxPlayers: 100
  enableFavicon: true
  faviconPath: "favicons/default.png"
  enableVirtualThreads: true

# ========================================
# PLAYER COUNT MANIPULATION
# ========================================
playerCount:
  enabled: false
  updateRate:
    intervalMs: 3000
    smoothUpdates: true
  maxCountType: "ADD_SOME"  # Options: VARIABLE, ADD_SOME, MULTIPLY, FIXED
  maxCount: 1
  showRealPlayers: true

# ========================================
# CACHING SETTINGS
# ========================================
cache:
  favicon:
    enabled: true
    maxAgeMs: 300000
    maxCacheSize: 10
    preloadFavicons: false
  json:
    enabled: true
    maxAgeMs: 60000
    maxCacheSize: 100
    compressCache: false
  enableMetrics: false

# ========================================
# SERIALIZATION SETTINGS
# ========================================
serialization:
  descriptionFormat: "MINIMESSAGE"
  enableFallback: true
  strictParsing: false
""";

    /**
     * Returns default configuration content as YAML
     */
    private String getDefaultYamlConfigContent() {
        return DEFAULT_YAML_CONFIG_CONTENT;
    }

    private MOTDConfig getActiveMOTDConfig() {
        if (stateMachine != null && stateMachine.isRunning()) {
            MOTDConfig current = stateMachine.getCurrentConfig();
            if (current != null) {
                return current;
            }
        }
        return config.motd();
    }

    private int calculateMaxPlayers(MOTDConfig baseConfig) {
        if (!config.playerCount().enabled() || baseConfig == null) {
            return baseConfig != null ? baseConfig.maxPlayers() : 100;
        }

        int realPlayers = server.getPlayerCount();
        int addCount = config.playerCount().maxCount();

        return switch (config.playerCount().maxCountType()) {
            case VARIABLE -> addCount;
            case ADD_SOME -> realPlayers + addCount;
            case MULTIPLY -> realPlayers * addCount;
            case FIXED -> addCount;
        };
    }

    private void refreshFavicon(MOTDConfig targetConfig) {
        if (targetConfig == null || !targetConfig.enableFavicon()) {
            this.favicon = null;
            return;
        }

        Path faviconPath = resolveFaviconPath(targetConfig.faviconPath());
        if (!Files.exists(faviconPath)) {
            logger.warn("Favicon path {} does not exist", faviconPath);
            this.favicon = null;
            return;
        }

        try {
            this.favicon = Favicon.create(faviconPath);
            logger.info("Loaded favicon from {}", faviconPath);
        } catch (IOException e) {
            logger.error("Failed to load favicon from {}: {}", faviconPath, e.getMessage());
            this.favicon = null;
        }
    }

    private Path resolveFaviconPath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }

        // Special case: server-icon.png should be in server root directory
        if (ConfigConstants.SERVER_ICON_FILENAME.equals(configuredPath)) {
            return Path.of("").toAbsolutePath().resolve(configuredPath).normalize();
        }

        // Use plugin data directory for other relative paths (custom favicons)
        return dataDirectory.resolve(configuredPath).normalize();
    }

    /**
     * Gets current MOTD configuration
     */
    public MOTDConfig getConfig() {
        return config.motd();
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
     * Initializes performance optimization components.
     * Configures caching systems based on configuration.
     */
    private void initializePerformanceComponents() {
        // Initialize favicon cache
        var faviconConfig = config.cache().favicon();
        if (faviconConfig.enabled()) {
            this.faviconCache = new FaviconCache(
                faviconConfig.maxCacheSize(),
                faviconConfig.maxAgeMs()
            );
            logger.info("Favicon cache enabled: max {} entries, {}ms TTL", 
                       faviconConfig.maxCacheSize(), faviconConfig.maxAgeMs());
        }
        
        // Initialize JSON cache
        var jsonConfig = config.cache().json();
        if (jsonConfig.enabled()) {
            this.jsonCache = new JsonCache(
                jsonConfig.maxCacheSize(),
                jsonConfig.maxAgeMs(),
                jsonConfig.compressCache()
            );
            logger.info("JSON cache enabled: max {} entries, {}ms TTL, compress={}", 
                       jsonConfig.maxCacheSize(), jsonConfig.maxAgeMs(), jsonConfig.compressCache());
        }
        
        logger.info("Performance components initialized successfully");
    }

    /**
     * Gets cached favicon using the high-performance cache system.
     * Falls back to direct loading if cache is disabled or fails.
     */
    private Favicon getCachedFavicon(MOTDConfig motdConfig) {
        if (faviconCache != null && motdConfig.faviconPath() != null) {
            try {
                // Resolve the favicon path first to handle server-icon.png correctly
                Path resolvedFaviconPath = resolveFaviconPath(motdConfig.faviconPath());
                FaviconCache.CachedFavicon cached = faviconCache.getFavicon(resolvedFaviconPath.toString(), dataDirectory);
                if (cached != null) {
                    return cached.favicon();
                }
            } catch (Exception e) {
                logger.warn("Favicon cache error, falling back to direct load: {}", e.getMessage());
            }
        }
        
        // Fallback to existing favicon
        return favicon;
    }

    /**
     * Gets the data directory path
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }
}
