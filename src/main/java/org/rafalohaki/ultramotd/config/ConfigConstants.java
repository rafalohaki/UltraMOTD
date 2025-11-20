package org.rafalohaki.ultramotd.config;

/**
 * Constants for JSON configuration field names to avoid magic strings.
 */
public final class ConfigConstants {
    
    // Common field names
    public static final String ENABLED = "enabled";
    public static final String MAX_AGE_MS = "maxAgeMs";
    public static final String MAX_CACHE_SIZE = "maxCacheSize";
    public static final String ENABLE_VIRTUAL_THREADS = "enableVirtualThreads";
    
    // MOTD fields
    public static final String MOTD = "motd";
    public static final String DESCRIPTION = "description";
    public static final String MAX_PLAYERS = "maxPlayers";
    public static final String ENABLE_FAVICON = "enableFavicon";
    public static final String FAVICON_PATH = "faviconPath";
    
    // Player count fields
    public static final String PLAYER_COUNT = "playerCount";
    public static final String UPDATE_RATE = "updateRate";
    public static final String INTERVAL_MS = "intervalMs";
    public static final String SMOOTH_UPDATES = "smoothUpdates";
    public static final String MAX_COUNT_TYPE = "maxCountType";
    public static final String MAX_COUNT = "maxCount";
    public static final String SHOW_REAL_PLAYERS = "showRealPlayers";
    
    // Performance fields
    public static final String PERFORMANCE = "performance";
    public static final String PACKET_OPTIMIZATION = "packetOptimization";
    public static final String PRE_SERIALIZATION = "preSerialization";
    public static final String ZERO_COPY_WRITE = "zeroCopyWrite";
    public static final String BATCH_SIZE = "batchSize";
    public static final String NETTY = "netty";
    public static final String PIPELINE_INJECTION = "pipelineInjection";
    public static final String EVENT_LOOP_THREADS = "eventLoopThreads";
    public static final String USE_DIRECT_BUFFERS = "useDirectBuffers";
    public static final String ALLOCATOR = "allocator";
    public static final String TYPE = "type";
    public static final String MAX_CACHED_BUFFERS = "maxCachedBuffers";
    public static final String BUFFER_SIZE = "bufferSize";
    public static final String VARINT = "varint";
    public static final String OPTIMIZATION_ENABLED = "optimizationEnabled";
    public static final String MAX_VARINT_BYTES = "maxVarintBytes";
    public static final String CACHE_VARINTS = "cacheVarints";
    
    // Cache fields
    public static final String CACHE = "cache";
    public static final String FAVICON = "favicon";
    public static final String JSON = "json";
    public static final String PRELOAD_FAVICONS = "preloadFavicons";
    public static final String COMPRESS_CACHE = "compressCache";
    public static final String ENABLE_METRICS = "enableMetrics";
    
    // Serialization fields
    public static final String SERIALIZATION = "serialization";
    public static final String DESCRIPTION_FORMAT = "descriptionFormat";
    public static final String ENABLE_FALLBACK = "enableFallback";
    public static final String STRICT_PARSING = "strictParsing";
    
    // Java21 fields
    public static final String JAVA21 = "java21";
    public static final String ENABLE_PREVIEW_FEATURES = "enablePreviewFeatures";
    public static final String ENABLE_RECORD_PATTERNS = "enableRecordPatterns";
    public static final String ENABLE_STRING_TEMPLATES = "enableStringTemplates";
    
    // Default values
    public static final String DEFAULT_FAVICON_PATH = "favicons/default.png";
    public static final String DEFAULT_MOTD_TEXT = "§aUltraMOTD §7- §bHigh Performance MOTD";
    public static final int DEFAULT_MAX_PLAYERS = 100;
    public static final long DEFAULT_UPDATE_RATE_MS = 3000;
    
    private ConfigConstants() {
        // Utility class
    }
}
