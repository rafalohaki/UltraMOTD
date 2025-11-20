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
    Java21Config java21,
    ServerConfig server,
    NetworkConfig network
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
     * Server information and SEO settings
     */
    public record ServerConfig(
        String name,
        String description,
        String website,
        String discord,
        String region,
        String[] tags,
        String language
    ) {

        public static ServerConfig getDefault() {
            return new ServerConfig(
                "UltraMOTD Server",
                "High-performance Minecraft server with advanced MOTD system",
                "https://example.com",
                "https://discord.gg/example",
                "EU",
                new String[]{"minecraft", "survival", "pvp", "economy"},
                "en"
            );
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ServerConfig that = (ServerConfig) obj;
            return java.util.Objects.equals(name, that.name) &&
                   java.util.Objects.equals(description, that.description) &&
                   java.util.Objects.equals(website, that.website) &&
                   java.util.Objects.equals(discord, that.discord) &&
                   java.util.Objects.equals(region, that.region) &&
                   java.util.Arrays.equals(tags, that.tags) &&
                   java.util.Objects.equals(language, that.language);
        }

        @Override
        public int hashCode() {
            int result = java.util.Objects.hash(name, description, website, discord, region, language);
            result = 31 * result + java.util.Arrays.hashCode(tags);
            return result;
        }

        @Override
        public String toString() {
            return "ServerConfig{" +
                   "name='" + name + '\'' +
                   ", description='" + description + '\'' +
                   ", website='" + website + '\'' +
                   ", discord='" + discord + '\'' +
                   ", region='" + region + '\'' +
                   ", tags=" + java.util.Arrays.toString(tags) +
                   ", language='" + language + '\'' +
                   '}';
        }
    }

    /**
     * Network and security settings
     */
    public record NetworkConfig(
        IPConfig ipManagement,
        boolean enableIPLogging,
        boolean enableGeoBlocking,
        String[] allowedCountries
    ) {

        public record IPConfig(
            boolean enableWhitelist,
            String[] whitelist,
            boolean enableBlacklist,
            String[] blacklist,
            boolean enableDeduplication,
            boolean logDuplicates
        ) {

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                IPConfig that = (IPConfig) obj;
                return enableWhitelist == that.enableWhitelist &&
                       enableBlacklist == that.enableBlacklist &&
                       enableDeduplication == that.enableDeduplication &&
                       logDuplicates == that.logDuplicates &&
                       java.util.Arrays.equals(whitelist, that.whitelist) &&
                       java.util.Arrays.equals(blacklist, that.blacklist);
            }

            @Override
            public int hashCode() {
                int result = java.util.Objects.hash(enableWhitelist, enableBlacklist, enableDeduplication, logDuplicates);
                result = 31 * result + java.util.Arrays.hashCode(whitelist);
                result = 31 * result + java.util.Arrays.hashCode(blacklist);
                return result;
            }

            @Override
            public String toString() {
                return "IPConfig{" +
                       "enableWhitelist=" + enableWhitelist +
                       ", whitelist=" + java.util.Arrays.toString(whitelist) +
                       ", enableBlacklist=" + enableBlacklist +
                       ", blacklist=" + java.util.Arrays.toString(blacklist) +
                       ", enableDeduplication=" + enableDeduplication +
                       ", logDuplicates=" + logDuplicates +
                       '}';
            }
        }

        public static NetworkConfig getDefault() {
            return new NetworkConfig(
                new IPConfig(false, new String[]{}, false, new String[]{}, true, false),
                false,
                false,
                new String[]{}
            );
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            NetworkConfig that = (NetworkConfig) obj;
            return enableIPLogging == that.enableIPLogging &&
                   enableGeoBlocking == that.enableGeoBlocking &&
                   java.util.Objects.equals(ipManagement, that.ipManagement) &&
                   java.util.Arrays.equals(allowedCountries, that.allowedCountries);
        }

        @Override
        public int hashCode() {
            int result = java.util.Objects.hash(enableIPLogging, enableGeoBlocking);
            result = 31 * result + java.util.Objects.hashCode(ipManagement);
            result = 31 * result + java.util.Arrays.hashCode(allowedCountries);
            return result;
        }

        @Override
        public String toString() {
            return "NetworkConfig{" +
                   "ipManagement=" + ipManagement +
                   ", enableIPLogging=" + enableIPLogging +
                   ", enableGeoBlocking=" + enableGeoBlocking +
                   ", allowedCountries=" + java.util.Arrays.toString(allowedCountries) +
                   '}';
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
            Java21Config.getDefault(),
            ServerConfig.getDefault(),
            NetworkConfig.getDefault()
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
