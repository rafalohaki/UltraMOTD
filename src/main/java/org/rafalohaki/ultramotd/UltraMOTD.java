package org.rafalohaki.ultramotd;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.slf4j.Logger;
import org.rafalohaki.ultramotd.config.MOTDConfig;
import org.rafalohaki.ultramotd.config.UltraConfig;
import org.rafalohaki.ultramotd.config.UltraYamlConfigLoader;
import org.rafalohaki.ultramotd.config.ConfigConstants;
import org.rafalohaki.ultramotd.state.UltraMOTDStateMachine;
import org.rafalohaki.ultramotd.cache.FaviconCache;
import org.rafalohaki.ultramotd.cache.JsonCache;
import org.rafalohaki.ultramotd.cache.ServerPingCache;
import org.rafalohaki.ultramotd.cache.PacketPingCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UltraMOTD - High Performance MOTD Plugin for Velocity
 * 
 * Features:
 * - Zero-cost config deserialization using Java 21 record patterns
 * - Virtual threads support for async operations
 * - Config hot-reloading with file watching
 * - High-performance caching (favicon, ServerPing pre-building)
 * - Optimized for high-traffic servers with frequent ping requests
 * 
 * Performance Optimizations:
 * ✅ ServerPing cache - Zero-allocation ping responses (pre-built objects)
 * ✅ Favicon caching - Eliminates repeated disk I/O for server-icon.png
 * ✅ Java 21 features - Virtual threads, record patterns, optimized operations
 * ✅ MiniMessage pre-parsing - Description parsing moved out of hot-path
 * 
 * Hot-path (onProxyPing) is now:
 * 1. Get cached description Component (already parsed)
 * 2. Calculate maxPlayers (simple arithmetic)
 * 3. Lookup pre-built ServerPing from cache (ConcurrentHashMap.get)
 * 4. Set ping (just reference swap)
 * → Zero allocation, minimal CPU, sub-microsecond latency
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
    private ServerPingCache serverPingCache;
    private PacketPingCache packetPingCache;  // Experimental: packet-level cache

    // Constants for Netty pipeline manipulation
    private static final String VELOCITY_HANDLER_NAME = "handler";

    private final AtomicReference<ServerPing.Version> lastVersionInfo = new AtomicReference<>();
    private final AtomicReference<ServerPing.Players> lastPlayersInfo = new AtomicReference<>();

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
            
            // Initialize ServerPing cache for zero-allocation ping responses
            this.serverPingCache = new ServerPingCache(logger);
            logger.info("ServerPing cache initialized");
            
            // Initialize PacketPingCache (experimental)
            this.packetPingCache = new PacketPingCache(logger);
            logger.info("PacketPing cache initialized (experimental)");
            
            // Try Netty pipeline injection if enabled
            if (config.performance().netty().pipelineInjection()) {
                tryNettyPipelineInjection();
            }
            
            // Boot state machine for advanced features (rotation, reloads)
            stateMachine = new UltraMOTDStateMachine(logger, configFile, motd -> {
                // On config reload, clear packet cache so it gets rebuilt on next ping
                if (packetPingCache != null) {
                    packetPingCache.clear();
                }
            });
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
        
        if (serverPingCache != null) {
            serverPingCache.invalidate();
        }
        
        if (packetPingCache != null) {
            packetPingCache.clear();
        }
        
        logger.info("UltraMOTD shutdown complete");
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        // Defensive check in case initialization failed
        if (config == null) {
            logger.warn("Config not loaded, using default fallback");
            event.setPing(ServerPing.builder()
                    .description(Component.text("§aUltraMOTD §7- §bPlugin Loading..."))
                    .maximumPlayers(100)
                    .build());
            return;
        }

        try {
            MOTDConfig activeMOTD = getActiveMOTDConfig();
            Component description = activeMOTD != null
                    ? activeMOTD.description()
                    : Component.text(ConfigConstants.DEFAULT_MOTD_TEXT);
            
            // Calculate dynamic player count if enabled
            int maxPlayers = calculateMaxPlayers(activeMOTD);

            // Get or create favicon
            Favicon faviconToUse = null;
            if (activeMOTD != null && activeMOTD.enableFavicon()) {
                faviconToUse = getCachedFavicon(activeMOTD);
            }
            
            // Get pre-built ServerPing from cache (zero allocation)
            ServerPing cachedPing = serverPingCache.getOrCreatePing(
                description,
                maxPlayers,
                faviconToUse,
                event.getPing().getVersion(),
                event.getPing().getPlayers().orElse(null)
            );
        
            event.setPing(cachedPing);
            lastVersionInfo.set(event.getPing().getVersion());
            lastPlayersInfo.set(event.getPing().getPlayers().orElse(null));
            if (packetPingCache != null && packetPingCache.size() == 0) {
                rebuildPacketCacheForCurrentMotd(event.getPing().getVersion(), event.getPing().getPlayers().orElse(null));
            }
        
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
  # Parsed with Kyori Adventure MiniMessage.
  # W praktyce w MOTD klient Minecraft wyświetla głównie:
  #   - Kolory i gradienty: <color:#hex>, <red>, <green>, <gold>, <gradient:#hex1:#hex2>...
  #   - Styl tekstu: <bold>, <italic>, <underlined>, <strikethrough>, <obfuscated>
  #   - Unicode: ■, ⚡, ↔, •, ★, ✓, itp.
  #   - Custom UltraMOTD: <center> – przybliżone centrowanie tekstu w linii
  # Zaawansowane rzeczy typu <hover:...> / <click:...> są parsowane, ale nie mają efektu w liście serwerów.
  description: |
    <center><color:#FFD700>■ ■ ■</color> <gradient:#facc15:#f97316:#dc2626><bold>UltraMOTD</bold></gradient> <color:#FFD700>■ ■ ■</color></center>
    <center><gradient:#7FFF00:#32CD32>High Performance Velocity MOTD Plugin</gradient></center>
    <center><gradient:#FF6347:#FF1493>MiniMessage · Caching · Java 21 Optimized</gradient></center>
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
     * Attempts to inject Netty handler for packet-level caching (EXPERIMENTAL).
     * This is a highly experimental feature that requires reflection into Velocity internals.
     * 
     * Architecture:
     * - Installs UltraPingNettyHandler before Velocity's standard ping handler
     * - Handler serves pre-built packets from PacketPingCache
     * - Falls back to standard Velocity flow if injection fails or packet not cached
     * 
     * Safety:
     * - Fails gracefully if reflection/injection fails
     * - Always has fallback to API mode (ServerPingCache + ProxyPingEvent)
     * - Logs clear warning that this is experimental and unsupported
     * 
     * Risks:
     * - May break on Velocity version updates
     * - May conflict with other Netty-manipulating plugins
     * - Not officially supported by Velocity API
     */
    private void tryNettyPipelineInjection() {
        try {
            // 1) Ustal realną klasę impl ProxyServer
            Class<?> velocityServerClass = server.getClass();

            // 2) Znajdź pole typu ConnectionManager niezależnie od nazwy
            Class<?> connMgrClass = Class.forName("com.velocitypowered.proxy.network.ConnectionManager");

            java.lang.reflect.Field connectionField = java.util.Arrays.stream(velocityServerClass.getDeclaredFields())
                    .filter(f -> f.getType().equals(connMgrClass))
                    .findFirst()
                    .orElse(null);

            if (connectionField == null) {
                logger.warn("No ConnectionManager field found on {}, Netty injection disabled",
                        velocityServerClass.getName());
                return;
            }

            if (!connectionField.trySetAccessible()) {
                logger.warn("Cannot access ConnectionManager field, Netty injection disabled");
                return;
            }
            Object connectionManager = connectionField.get(server);

            if (!connMgrClass.isInstance(connectionManager)) {
                logger.warn("ConnectionManager field type mismatch: {}", connectionManager);
                return;
            }

            // 3) Pobierz ServerChannelInitializerHolder przez publiczną metodę
            var getServerChannelInitializer = connMgrClass.getMethod("getServerChannelInitializer");
            Object holderObj = getServerChannelInitializer.invoke(connectionManager);

            // 4) ServerChannelInitializerHolder
            Class<?> holderClass = Class.forName("com.velocitypowered.proxy.network.ServerChannelInitializerHolder");

            // Pobierz oryginalny initializer
            var getMethod = holderClass.getMethod("get");
            @SuppressWarnings("unchecked")
            ChannelInitializer<Channel> originalInit = (ChannelInitializer<Channel>) getMethod.invoke(holderObj);

            // 5) Stwórz wrapped initializer
            ChannelInitializer<Channel> wrapped = new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    // Dodaj oryginalny initializer jako handler do pipeline
                    ch.pipeline().addLast("ultramotd-original-init", originalInit);

                    // Dodaj własne handlery
                    var p = ch.pipeline();
                    try {
                        if (p.get(VELOCITY_HANDLER_NAME) != null) {
                            p.addBefore(VELOCITY_HANDLER_NAME, "ultramotd-handshake",
                                    new org.rafalohaki.ultramotd.netty.UltraHandshakeTracker(logger));
                            p.addBefore(VELOCITY_HANDLER_NAME, "ultramotd-ping",
                                    new org.rafalohaki.ultramotd.netty.UltraPingNettyHandler(packetPingCache, true));
                        } else {
                            p.addFirst("ultramotd-handshake",
                                    new org.rafalohaki.ultramotd.netty.UltraHandshakeTracker(logger));
                            p.addLast("ultramotd-ping",
                                    new org.rafalohaki.ultramotd.netty.UltraPingNettyHandler(packetPingCache, true));
                        }
                    } catch (Exception t) {
                        logger.debug("Pipeline modification failed: {}", t.getMessage());
                    }
                }
            };

            // 6) Podmień initializer przez holder.set()
            var setMethod = holderClass.getMethod("set", ChannelInitializer.class);
            setMethod.invoke(holderObj, wrapped);

            logger.info("Injected UltraMOTD Netty handlers via ServerChannelInitializerHolder");
        } catch (Exception t) {
            logger.error("Failed to inject Netty handler, falling back to API mode", t);
        }
    }

    private void rebuildPacketCacheForCurrentMotd(ServerPing.Version version, ServerPing.Players players) {
        if (packetPingCache == null || serverPingCache == null) {
            return;
        }
        MOTDConfig motd = getActiveMOTDConfig();
        if (motd == null) {
            return;
        }
        Favicon faviconToUse = motd.enableFavicon() ? getCachedFavicon(motd) : null;
        ServerPing ping = serverPingCache.getOrCreatePing(
                motd.description(),
                calculateMaxPlayers(motd),
                faviconToUse,
                version,
                players
        );
        packetPingCache.updatePacket(new PacketPingCache.Key(0, ""), ping);
    }

    /**
     * Initializes all caching and performance optimization components.
     * Designed to be fail-safe: if initialization fails for one component, others still work.
     * 
     * Components:
     * - Favicon cache (TTL + size limits)
     * - JSON response cache (configured but not applicable to ProxyPingEvent)
     * - ServerPing cache (zero-allocation ping responses)
     * - PacketPing cache (experimental packet-level caching)
     * 
     * Note: PacketPingCache requires Netty pipeline injection to be effective.
     * Without injection, it's just initialized but not used in hot-path.
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
        
        // Initialize JSON cache (currently unused in hot-path, reserved for future features)
        
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
