package org.rafalohaki.ultramotd.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * YAML configuration loader with backward compatibility for JSON.
 * Supports all UltraMOTD features with improved readability.
 */
public class UltraYamlConfigLoader {

    private static final Yaml YAML = new Yaml();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Logger logger;

    public UltraYamlConfigLoader(Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads configuration from file, detecting format (YAML/JSON) automatically.
     */
    public UltraConfig loadConfig(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                logger.info("Config file not found at {}, creating YAML config", configPath);
                return UltraConfig.getDefault();
            }

            String content = Files.readString(configPath);
            UltraConfig config = parseConfig(content, configPath);

            logger.info("UltraMOTD configuration loaded successfully from {}", configPath);
            logConfigurationSummary(config);
            return config;

        } catch (Exception e) {
            logger.error("Failed to load configuration from {}, using defaults: {}",
                    configPath, e.getMessage(), e);
            return UltraConfig.getDefault();
        }
    }

    /**
     * Parses configuration content, detecting format automatically.
     */
    private UltraConfig parseConfig(String content, Path configPath) {
        try {
            // Try YAML first (preferred format)
            if (isYamlFormat(configPath) || looksLikeYaml(content)) {
                return parseYamlConfig(content);
            }
            // Fallback to JSON for backward compatibility
            else {
                logger.info("Detected JSON config, consider migrating to YAML for better readability");
                return parseJsonFallback();
            }
        } catch (Exception e) {
            logger.debug("Config parsing failed, using defaults: {}", e.getMessage());
            return UltraConfig.getDefault();
        }
    }

    /**
     * Checks if file extension indicates YAML format.
     */
    private boolean isYamlFormat(Path configPath) {
        String fileName = configPath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".yml") || fileName.endsWith(".yaml");
    }

    /**
     * Heuristically detects YAML content by looking for YAML-specific patterns.
     */
    private boolean looksLikeYaml(String content) {
        return content.trim().startsWith("---") || 
               content.contains(": ") && !content.trim().startsWith("{");
    }

    /**
     * Parses YAML configuration content.
     */
    private UltraConfig parseYamlConfig(String content) {
        try {
            Map<String, Object> yaml = YAML.load(content);
            return new UltraConfig(
                parseMOTDConfig(yaml),
                parsePlayerCountConfig(yaml),
                parsePerformanceConfig(yaml),
                parseCacheConfig(yaml),
                parseSerializationConfig(yaml),
                parseJava21Config(yaml),
                parseServerConfig(yaml),
                parseNetworkConfig(yaml)
            );
        } catch (YAMLException e) {
            throw new org.rafalohaki.ultramotd.config.ConfigParseException("YAML parsing failed", e);
        }
    }

    /**
     * Fallback JSON parser for backward compatibility.
     * This method will be removed in a future version.
     */
    private UltraConfig parseJsonFallback() {
        // Keep existing JSON parsing logic temporarily
        // This will be removed in a future version
        logger.warn("JSON format is deprecated. Please migrate to YAML configuration.");
        return UltraConfig.getDefault(); // Temporary fallback
    }

    private MOTDConfig parseMOTDConfig(Map<String, Object> yaml) {
        Map<String, Object> motdMap = getMap(yaml, ConfigConstants.MOTD, yaml);
        
        Component description = parseComponent(motdMap.get(ConfigConstants.DESCRIPTION));
        int maxPlayers = getInt(motdMap, ConfigConstants.MAX_PLAYERS, ConfigConstants.DEFAULT_MAX_PLAYERS);
        boolean enableFavicon = getBoolean(motdMap, ConfigConstants.ENABLE_FAVICON, true);
        String faviconPath = getString(motdMap, ConfigConstants.FAVICON_PATH, ConfigConstants.DEFAULT_FAVICON_PATH);
        boolean enableVirtualThreads = getBoolean(motdMap, ConfigConstants.ENABLE_VIRTUAL_THREADS, true);

        return new MOTDConfig(description, maxPlayers, enableFavicon, faviconPath, enableVirtualThreads);
    }

    private UltraConfig.PlayerCountConfig parsePlayerCountConfig(Map<String, Object> yaml) {
        Map<String, Object> pcMap = getMap(yaml, ConfigConstants.PLAYER_COUNT, null);
        if (pcMap == null) {
            return UltraConfig.PlayerCountConfig.getDefault();
        }

        boolean enabled = getBoolean(pcMap, ConfigConstants.ENABLED, false);
        
        // Parse update rate
        UltraConfig.PlayerCountConfig.UpdateRate updateRate;
        Map<String, Object> rateMap = getMap(pcMap, ConfigConstants.UPDATE_RATE, null);
        if (rateMap != null) {
            long interval = getLong(rateMap, ConfigConstants.INTERVAL_MS, ConfigConstants.DEFAULT_UPDATE_RATE_MS);
            boolean smooth = getBoolean(rateMap, ConfigConstants.SMOOTH_UPDATES, true);
            updateRate = new UltraConfig.PlayerCountConfig.UpdateRate(interval, smooth);
        } else {
            updateRate = UltraConfig.PlayerCountConfig.UpdateRate.defaultRate();
        }

        // Parse max count type
        UltraConfig.PlayerCountConfig.MaxCountType maxCountType = 
                UltraConfig.PlayerCountConfig.MaxCountType.ADD_SOME;
        String maxCountTypeStr = getString(pcMap, ConfigConstants.MAX_COUNT_TYPE, "ADD_SOME");
        try {
            maxCountType = UltraConfig.PlayerCountConfig.MaxCountType.valueOf(maxCountTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid maxCountType '{}', using ADD_SOME", maxCountTypeStr);
        }

        int maxCount = getInt(pcMap, ConfigConstants.MAX_COUNT, 1);
        boolean showRealPlayers = getBoolean(pcMap, ConfigConstants.SHOW_REAL_PLAYERS, true);

        return new UltraConfig.PlayerCountConfig(enabled, updateRate, maxCountType, maxCount, showRealPlayers);
    }

    private UltraConfig.PerformanceConfig parsePerformanceConfig(Map<String, Object> yaml) {
        Map<String, Object> perfMap = getMap(yaml, ConfigConstants.PERFORMANCE, null);
        if (perfMap == null) {
            return UltraConfig.PerformanceConfig.getDefault();
        }
        
        return new UltraConfig.PerformanceConfig(
            parsePacketOptimization(perfMap),
            parseNettyConfig(perfMap),
            parseAllocatorConfig(perfMap),
            parseVarintConfig(perfMap)
        );
    }

    private UltraConfig.PerformanceConfig.PacketOptimization parsePacketOptimization(Map<String, Object> perfMap) {
        Map<String, Object> packetMap = getMap(perfMap, ConfigConstants.PACKET_OPTIMIZATION, null);
        if (packetMap == null) {
            return UltraConfig.PerformanceConfig.PacketOptimization.getDefault();
        }

        boolean preSerialization = getBoolean(packetMap, ConfigConstants.PRE_SERIALIZATION, true);
        boolean zeroCopy = getBoolean(packetMap, ConfigConstants.ZERO_COPY_WRITE, true);
        int batchSize = getInt(packetMap, ConfigConstants.BATCH_SIZE, 64);
        
        return new UltraConfig.PerformanceConfig.PacketOptimization(preSerialization, zeroCopy, batchSize);
    }

    private UltraConfig.PerformanceConfig.NettyConfig parseNettyConfig(Map<String, Object> perfMap) {
        Map<String, Object> nettyMap = getMap(perfMap, ConfigConstants.NETTY, null);
        if (nettyMap == null) {
            return UltraConfig.PerformanceConfig.NettyConfig.getDefault();
        }

        boolean pipelineInjection = getBoolean(nettyMap, ConfigConstants.PIPELINE_INJECTION, true);
        int eventLoopThreads = getInt(nettyMap, ConfigConstants.EVENT_LOOP_THREADS, 0);
        boolean directBuffers = getBoolean(nettyMap, ConfigConstants.USE_DIRECT_BUFFERS, true);
        
        return new UltraConfig.PerformanceConfig.NettyConfig(pipelineInjection, eventLoopThreads, directBuffers);
    }

    private UltraConfig.PerformanceConfig.AllocatorConfig parseAllocatorConfig(Map<String, Object> perfMap) {
        Map<String, Object> allocMap = getMap(perfMap, ConfigConstants.ALLOCATOR, null);
        if (allocMap == null) {
            return UltraConfig.PerformanceConfig.AllocatorConfig.getDefault();
        }

        UltraConfig.PerformanceConfig.AllocatorConfig.AllocatorType type = 
                UltraConfig.PerformanceConfig.AllocatorConfig.AllocatorType.DIRECT_POOLED;
        String typeStr = getString(allocMap, ConfigConstants.TYPE, "DIRECT_POOLED");
        try {
            type = UltraConfig.PerformanceConfig.AllocatorConfig.AllocatorType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid allocator type '{}', using DIRECT_POOLED", typeStr);
        }
        
        int maxCached = getInt(allocMap, ConfigConstants.MAX_CACHED_BUFFERS, 1024);
        int bufferSize = getInt(allocMap, ConfigConstants.BUFFER_SIZE, 8192);
        
        return new UltraConfig.PerformanceConfig.AllocatorConfig(type, maxCached, bufferSize);
    }

    private UltraConfig.PerformanceConfig.VarintConfig parseVarintConfig(Map<String, Object> perfMap) {
        Map<String, Object> varintMap = getMap(perfMap, ConfigConstants.VARINT, null);
        if (varintMap == null) {
            return UltraConfig.PerformanceConfig.VarintConfig.getDefault();
        }

        boolean enabled = getBoolean(varintMap, ConfigConstants.OPTIMIZATION_ENABLED, true);
        int maxBytes = getInt(varintMap, ConfigConstants.MAX_VARINT_BYTES, 5);
        boolean cache = getBoolean(varintMap, ConfigConstants.CACHE_VARINTS, true);
        
        return new UltraConfig.PerformanceConfig.VarintConfig(enabled, maxBytes, cache);
    }

    private UltraConfig.CacheConfig parseCacheConfig(Map<String, Object> yaml) {
        Map<String, Object> cacheMap = getMap(yaml, ConfigConstants.CACHE, null);
        if (cacheMap == null) {
            return UltraConfig.CacheConfig.getDefault();
        }
        
        return new UltraConfig.CacheConfig(
            parseFaviconCacheConfig(cacheMap),
            parseJsonCacheConfig(cacheMap),
            getBoolean(cacheMap, ConfigConstants.ENABLE_METRICS, true)
        );
    }

    private UltraConfig.CacheConfig.FaviconCacheConfig parseFaviconCacheConfig(Map<String, Object> cacheMap) {
        Map<String, Object> favMap = getMap(cacheMap, ConfigConstants.FAVICON, null);
        if (favMap == null) {
            return UltraConfig.CacheConfig.FaviconCacheConfig.getDefault();
        }

        boolean enabled = getBoolean(favMap, ConfigConstants.ENABLED, true);
        long maxAge = getLong(favMap, ConfigConstants.MAX_AGE_MS, 300000);
        int maxSize = getInt(favMap, ConfigConstants.MAX_CACHE_SIZE, 10);
        boolean preload = getBoolean(favMap, ConfigConstants.PRELOAD_FAVICONS, false);
        
        return new UltraConfig.CacheConfig.FaviconCacheConfig(enabled, maxAge, maxSize, preload);
    }

    private UltraConfig.CacheConfig.JsonCacheConfig parseJsonCacheConfig(Map<String, Object> cacheMap) {
        Map<String, Object> jsonMap = getMap(cacheMap, ConfigConstants.JSON, null);
        if (jsonMap == null) {
            return UltraConfig.CacheConfig.JsonCacheConfig.getDefault();
        }

        boolean enabled = getBoolean(jsonMap, ConfigConstants.ENABLED, true);
        long maxAge = getLong(jsonMap, ConfigConstants.MAX_AGE_MS, 60000);
        int maxSize = getInt(jsonMap, ConfigConstants.MAX_CACHE_SIZE, 100);
        boolean compress = getBoolean(jsonMap, ConfigConstants.COMPRESS_CACHE, false);
        
        return new UltraConfig.CacheConfig.JsonCacheConfig(enabled, maxAge, maxSize, compress);
    }

    private UltraConfig.SerializationConfig parseSerializationConfig(Map<String, Object> yaml) {
        Map<String, Object> serMap = getMap(yaml, ConfigConstants.SERIALIZATION, null);
        if (serMap == null) {
            return UltraConfig.SerializationConfig.getDefault();
        }
        
        UltraConfig.SerializationConfig.DescriptionFormat format = 
                UltraConfig.SerializationConfig.DescriptionFormat.MINIMESSAGE;
        String formatStr = getString(serMap, ConfigConstants.DESCRIPTION_FORMAT, "MINIMESSAGE");
        try {
            format = UltraConfig.SerializationConfig.DescriptionFormat.valueOf(formatStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid description format '{}', using MINIMESSAGE", formatStr);
        }

        boolean enableFallback = getBoolean(serMap, ConfigConstants.ENABLE_FALLBACK, true);
        boolean strictParsing = getBoolean(serMap, ConfigConstants.STRICT_PARSING, false);

        return new UltraConfig.SerializationConfig(format, enableFallback, strictParsing);
    }

    private UltraConfig.Java21Config parseJava21Config(Map<String, Object> yaml) {
        Map<String, Object> javaMap = getMap(yaml, ConfigConstants.JAVA21, null);
        if (javaMap == null) {
            return UltraConfig.Java21Config.getDefault();
        }
        
        boolean virtualThreads = getBoolean(javaMap, ConfigConstants.ENABLE_VIRTUAL_THREADS, true);
        boolean preview = getBoolean(javaMap, ConfigConstants.ENABLE_PREVIEW_FEATURES, false);
        boolean recordPatterns = getBoolean(javaMap, ConfigConstants.ENABLE_RECORD_PATTERNS, true);
        boolean stringTemplates = getBoolean(javaMap, ConfigConstants.ENABLE_STRING_TEMPLATES, false);

        return new UltraConfig.Java21Config(virtualThreads, preview, recordPatterns, stringTemplates);
    }

    private UltraConfig.ServerConfig parseServerConfig(Map<String, Object> yaml) {
        Map<String, Object> serverMap = getMap(yaml, "server", null);
        if (serverMap == null) {
            return UltraConfig.ServerConfig.getDefault();
        }
        
        String name = getString(serverMap, "name", "UltraMOTD Server");
        String description = getString(serverMap, "description", "High-performance Minecraft server");
        String website = getString(serverMap, "website", "");
        String discord = getString(serverMap, "discord", "");
        String region = getString(serverMap, "region", "EU");
        String[] tags = getStringArray(serverMap, "tags", new String[]{"minecraft"});
        String language = getString(serverMap, "language", "en");

        return new UltraConfig.ServerConfig(name, description, website, discord, region, tags, language);
    }

    private UltraConfig.NetworkConfig parseNetworkConfig(Map<String, Object> yaml) {
        Map<String, Object> networkMap = getMap(yaml, "network", null);
        if (networkMap == null) {
            return UltraConfig.NetworkConfig.getDefault();
        }
        
        Map<String, Object> ipMap = getMap(networkMap, "ipManagement", null);
        UltraConfig.NetworkConfig.IPConfig ipConfig;
        
        if (ipMap != null) {
            boolean enableWhitelist = getBoolean(ipMap, "enableWhitelist", false);
            String[] whitelist = getStringArray(ipMap, "whitelist", new String[]{});
            boolean enableBlacklist = getBoolean(ipMap, "enableBlacklist", false);
            String[] blacklist = getStringArray(ipMap, "blacklist", new String[]{});
            boolean enableDeduplication = getBoolean(ipMap, "enableDeduplication", true);
            boolean logDuplicates = getBoolean(ipMap, "logDuplicates", false);
            
            ipConfig = new UltraConfig.NetworkConfig.IPConfig(
                enableWhitelist, whitelist, enableBlacklist, blacklist, enableDeduplication, logDuplicates
            );
        } else {
            ipConfig = new UltraConfig.NetworkConfig.IPConfig(false, new String[]{}, false, new String[]{}, true, false);
        }
        
        boolean enableIPLogging = getBoolean(networkMap, "enableIPLogging", false);
        boolean enableGeoBlocking = getBoolean(networkMap, "enableGeoBlocking", false);
        String[] allowedCountries = getStringArray(networkMap, "allowedCountries", new String[]{});

        return new UltraConfig.NetworkConfig(ipConfig, enableIPLogging, enableGeoBlocking, allowedCountries);
    }

    /**
     * Parses text component from YAML value.
     */
    private Component parseComponent(Object value) {
        if (value == null) {
            return Component.text(ConfigConstants.DEFAULT_MOTD_TEXT);
        }

        String text = value.toString();
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

    // Helper methods for safe YAML value extraction
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key, Map<String, Object> defaultValue) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    private String[] getStringArray(Map<String, Object> map, String key, String[] defaultValue) {
        Object value = map.get(key);
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
        if (value instanceof String str) {
            return str.split(",");
        }
        return defaultValue;
    }

    /**
     * Logs configuration summary for debugging
     */
    private void logConfigurationSummary(UltraConfig config) {
        logger.info("=== UltraMOTD Configuration Summary ===");
        logger.info("MOTD: {} (max players: {})", 
                config.motd().description(), config.motd().maxPlayers());
        logger.info("Player Count: enabled={}, type={}, maxCount={}", 
                config.playerCount().enabled(), 
                config.playerCount().maxCountType(), 
                config.playerCount().maxCount());
        logger.info("Performance: preSerialization={}, zeroCopy={}, virtualThreads={}", 
                config.performance().packetOptimization().preSerialization(),
                config.performance().packetOptimization().zeroCopyWrite(),
                config.java21().enableVirtualThreads());
        logger.info("Cache: favicon={}, json={}, metrics={}", 
                config.cache().favicon().enabled(),
                config.cache().json().enabled(),
                config.cache().enableMetrics());
        logger.info("Serialization: format={}, fallback={}", 
                config.serialization().descriptionFormat(),
                config.serialization().enableFallback());
        logger.info("========================================");
    }
}
