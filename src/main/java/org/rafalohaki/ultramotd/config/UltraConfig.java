package org.rafalohaki.ultramotd.config;

/**
 * Comprehensive configuration for UltraMOTD with all roadmap features.
 * Uses nested records for organization and atomic hot-reload support.
 */
public record UltraConfig(
    MOTDConfig motd,
    PlayerCountConfig playerCount,
    PerformanceConfig performance,
    CacheConfig cache,
    SerializationConfig serialization,
    Java21Config java21
) {

    /**
     * Player count manipulation settings
     */
    public record PlayerCountConfig(
        boolean enabled,
        UpdateRate updateRate,
        MaxCountType maxCountType,
        int maxCount,
        boolean showRealPlayers
    ) {

        public enum MaxCountType {
            VARIABLE,    // Use max-count as variable value
            ADD_SOME,    // Add max-count to real players
            MULTIPLY,    // Multiply real players by max-count
            FIXED        // Use max-count as fixed value
        }

        public record UpdateRate(
            long intervalMs,
            boolean smoothUpdates
        ) {

            public static UpdateRate defaultRate() {
                return new UpdateRate(3000, true);
            }
        }

        public static PlayerCountConfig getDefault() {
            return new PlayerCountConfig(
                false,
                UpdateRate.defaultRate(),
                MaxCountType.ADD_SOME,
                1,
                true
            );
        }
    }

    /**
     * Performance optimization settings
     */
    public record PerformanceConfig(
        PacketOptimization packetOptimization,
        NettyConfig netty,
        AllocatorConfig allocator,
        VarintConfig varint
    ) {

        public record PacketOptimization(
            boolean preSerialization,
            boolean zeroCopyWrite,
            int batchSize
        ) {

            public static PacketOptimization getDefault() {
                return new PacketOptimization(true, true, 64);
            }
        }

        public record NettyConfig(
            boolean pipelineInjection,
            int eventLoopThreads,
            boolean useDirectBuffers
        ) {

            public static NettyConfig getDefault() {
                return new NettyConfig(true, 0, true);
            }
        }

        public record AllocatorConfig(
            AllocatorType type,
            int maxCachedBuffers,
            int bufferSize
        ) {

            public enum AllocatorType {
                UNPOOLED,
                POOLED,
                DIRECT_POOLED
            }

            public static AllocatorConfig getDefault() {
                return new AllocatorConfig(AllocatorType.DIRECT_POOLED, 1024, 8192);
            }
        }

        public record VarintConfig(
            boolean optimizationEnabled,
            int maxVarintBytes,
            boolean cacheVarints
        ) {

            public static VarintConfig getDefault() {
                return new VarintConfig(true, 5, true);
            }
        }

        public static PerformanceConfig getDefault() {
            return new PerformanceConfig(
                PacketOptimization.getDefault(),
                NettyConfig.getDefault(),
                AllocatorConfig.getDefault(),
                VarintConfig.getDefault()
            );
        }
    }

    /**
     * Caching strategies
     */
    public record CacheConfig(
        FaviconCacheConfig favicon,
        JsonCacheConfig json,
        boolean enableMetrics
    ) {

        public record FaviconCacheConfig(
            boolean enabled,
            long maxAgeMs,
            int maxCacheSize,
            boolean preloadFavicons
        ) {

            public static FaviconCacheConfig getDefault() {
                return new FaviconCacheConfig(true, 300000, 10, false);
            }
        }

        public record JsonCacheConfig(
            boolean enabled,
            long maxAgeMs,
            int maxCacheSize,
            boolean compressCache
        ) {

            public static JsonCacheConfig getDefault() {
                return new JsonCacheConfig(true, 60000, 100, false);
            }
        }

        public static CacheConfig getDefault() {
            return new CacheConfig(
                FaviconCacheConfig.getDefault(),
                JsonCacheConfig.getDefault(),
                true
            );
        }
    }

    /**
     * Serialization format options
     */
    public record SerializationConfig(
        DescriptionFormat descriptionFormat,
        boolean enableFallback,
        boolean strictParsing
    ) {

        public enum DescriptionFormat {
            MINIMESSAGE,  // <green>text</green>
            LEGACY,       // &a or §a colors
            JSON,         // Adventure Component JSON
            AUTO          // Detect automatically
        }

        public static SerializationConfig getDefault() {
            return new SerializationConfig(DescriptionFormat.MINIMESSAGE, true, false);
        }
    }

    /**
     * Java 21 specific features
     */
    public record Java21Config(
        boolean enableVirtualThreads,
        boolean enablePreviewFeatures,
        boolean enableRecordPatterns,
        boolean enableStringTemplates
    ) {

        public static Java21Config getDefault() {
            return new Java21Config(true, false, true, false);
        }
    }

    /**
     * Creates a default comprehensive configuration
     */
    public static UltraConfig getDefault() {
        return new UltraConfig(
            MOTDConfig.getDefault(),
            PlayerCountConfig.getDefault(),
            PerformanceConfig.getDefault(),
            CacheConfig.getDefault(),
            SerializationConfig.getDefault(),
            Java21Config.getDefault()
        );
    }

    /**
     * Validates configuration parameters
     */
    public UltraConfig {
        if (playerCount.updateRate().intervalMs() < 100) {
            throw new IllegalArgumentException("Update rate must be at least 100ms");
        }
        if (performance.packetOptimization().batchSize() < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (cache.favicon().maxCacheSize() < 1) {
            throw new IllegalArgumentException("Favicon cache size must be positive");
        }
    }
}
