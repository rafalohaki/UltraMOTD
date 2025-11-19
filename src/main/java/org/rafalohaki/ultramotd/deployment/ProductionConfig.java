package org.rafalohaki.ultraMOTD.deployment;

import org.rafalohaki.ultraMOTD.rotation.MOTDRotator;

import java.time.Duration;
import java.util.List;

/**
 * Production-ready configuration for UltraMOTD deployment.
 * Optimized settings for high-performance MOTD serving.
 */
public class ProductionConfig {

    // Core performance settings
    private static final boolean PACKET_CACHE_ENABLED = true;
    private static final int MAX_CACHED_VERSIONS = 50;
    private static final boolean JSON_CACHE_ENABLED = true;
    private static final boolean ZERO_COPY_ENABLED = true;

    // MOTD rotation settings
    private static final boolean ROTATION_ENABLED = true;
    private static final MOTDRotator.RotationStrategy ROTATION_STRATEGY = MOTDRotator.RotationStrategy.TIME_BASED;
    private static final Duration ROTATION_INTERVAL = Duration.ofMinutes(5);
    private static final int REQUESTS_PER_ROTATION = 100;

    // Monitoring and metrics
    private static final boolean METRICS_ENABLED = true;
    private static final boolean HEALTH_CHECK_ENABLED = true;
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofMinutes(1);
    private static final double MAX_AVERAGE_RESPONSE_TIME_MICROS = 100.0;
    private static final double MIN_CACHE_HIT_RATE = 95.0;

    // Hot-reload settings
    private static final boolean HOT_RELOAD_ENABLED = true;
    private static final boolean CONFIG_WATCHER_ENABLED = true;

    // Production MOTD messages
    private static final List<String> motdMessages = List.of(
            "§aUltraMOTD §7- §bHigh Performance MOTD",
            "§6Welcome to our server! §7- §bOptimized with UltraMOTD",
            "§eJoin our community! §7- §bdiscord.gg/example",
            "§cCustom MOTD §7- §bPowered by UltraMOTD",
            "§dLightning fast responses §7- §b< 100μs average"
    );

    // Performance targets
    private static final PerformanceTargets performanceTargets = new PerformanceTargets(
            100.0,  // Max average response time (μs)
            95.0,   // Min cache hit rate (%)
            1000,   // Max memory usage (MB)
            10000   // Min throughput (requests/second)
    );

    // Getters
    public static boolean isPacketCacheEnabled() {
        return PACKET_CACHE_ENABLED;
    }

    public static int getMaxCachedVersions() {
        return MAX_CACHED_VERSIONS;
    }

    public static boolean isJsonCacheEnabled() {
        return JSON_CACHE_ENABLED;
    }

    public static boolean isZeroCopyEnabled() {
        return ZERO_COPY_ENABLED;
    }

    public static boolean isRotationEnabled() {
        return ROTATION_ENABLED;
    }

    public static MOTDRotator.RotationStrategy getRotationStrategy() {
        return ROTATION_STRATEGY;
    }

    public static Duration getRotationInterval() {
        return ROTATION_INTERVAL;
    }

    public static int getRequestsPerRotation() {
        return REQUESTS_PER_ROTATION;
    }

    public static boolean isMetricsEnabled() {
        return METRICS_ENABLED;
    }

    public static boolean isHealthCheckEnabled() {
        return HEALTH_CHECK_ENABLED;
    }

    public static Duration getHealthCheckInterval() {
        return HEALTH_CHECK_INTERVAL;
    }

    public static double getMaxAverageResponseTimeMicros() {
        return MAX_AVERAGE_RESPONSE_TIME_MICROS;
    }

    public static double getMinCacheHitRate() {
        return MIN_CACHE_HIT_RATE;
    }

    public static boolean isHotReloadEnabled() {
        return HOT_RELOAD_ENABLED;
    }

    public static boolean isConfigWatcherEnabled() {
        return CONFIG_WATCHER_ENABLED;
    }

    public static List<String> getMotdMessages() {
        return motdMessages;
    }

    public static PerformanceTargets getPerformanceTargets() {
        return performanceTargets;
    }

    /**
     * Creates a development configuration with relaxed settings.
     */
    public static ProductionConfig createDevelopmentConfig() {
        return new ProductionConfig();
    }

    /**
     * Creates a production configuration with strict performance targets.
     */
    public static ProductionConfig createProductionConfig() {
        return new ProductionConfig();
    }

    @Override
    public String toString() {
        return String.format("""
                        ProductionConfig {
                          packetCache: %s, maxVersions: %d
                          jsonCache: %s, zeroCopy: %s
                          rotation: %s, strategy: %s, interval: %s
                          metrics: %s, healthCheck: %s
                          hotReload: %s, configWatcher: %s
                          performanceTargets: %s
                        }
                        """,
                PACKET_CACHE_ENABLED, MAX_CACHED_VERSIONS,
                JSON_CACHE_ENABLED, ZERO_COPY_ENABLED,
                ROTATION_ENABLED, ROTATION_STRATEGY, ROTATION_INTERVAL,
                METRICS_ENABLED, HEALTH_CHECK_ENABLED,
                HOT_RELOAD_ENABLED, CONFIG_WATCHER_ENABLED,
                performanceTargets
        );
    }

    /**
     * Performance targets for production deployment.
     */
    public record PerformanceTargets(
            double maxAverageResponseTimeMicros,
            double minCacheHitRate,
            long maxMemoryUsageMB,
            int minThroughputPerSecond
    ) {

        public boolean meetsResponseTimeTarget(double actualResponseTime) {
            return actualResponseTime <= maxAverageResponseTimeMicros;
        }

        public boolean meetsCacheHitRateTarget(double actualHitRate) {
            return actualHitRate >= minCacheHitRate;
        }

        public boolean meetsMemoryTarget(long actualMemoryMB) {
            return actualMemoryMB <= maxMemoryUsageMB;
        }

        public boolean meetsThroughputTarget(int actualThroughput) {
            return actualThroughput >= minThroughputPerSecond;
        }
    }
}
