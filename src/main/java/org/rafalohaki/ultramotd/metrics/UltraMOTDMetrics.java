package org.rafalohaki.ultramotd.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight metrics collector for UltraMOTD performance monitoring.
 * Uses atomic operations and LongAdder for high-performance, thread-safe metrics.
 */
public class UltraMOTDMetrics {

    // Cache performance metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final LongAdder totalRequests = new LongAdder();

    // Response time metrics (in nanoseconds)
    private final LongAdder totalResponseTime = new LongAdder();
    private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxResponseTime = new AtomicLong(0);

    // MOTD rotation metrics
    private final AtomicLong rotationCount = new AtomicLong(0);
    private final LongAdder rotationTime = new LongAdder();

    // Hot-reload metrics
    private final AtomicLong configReloads = new AtomicLong(0);
    private final LongAdder reloadTime = new LongAdder();

    /**
     * Records a cache hit.
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
        totalRequests.increment();
    }

    /**
     * Records a cache miss.
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
        totalRequests.increment();
    }

    /**
     * Records response time for a MOTD request.
     */
    public void recordResponseTime(long responseTimeNanos) {
        totalResponseTime.add(responseTimeNanos);

        // Update min/max response times (thread-safe but may have slight race conditions)
        updateMinResponseTime(responseTimeNanos);
        updateMaxResponseTime(responseTimeNanos);
    }

    /**
     * Records a MOTD rotation event.
     */
    public void recordRotation(long rotationTimeNanos) {
        rotationCount.incrementAndGet();
        rotationTime.add(rotationTimeNanos);
    }

    /**
     * Records a configuration reload event.
     */
    public void recordConfigReload(long reloadTimeNanos) {
        configReloads.incrementAndGet();
        reloadTime.add(reloadTimeNanos);
    }

    /**
     * Gets the cache hit rate as a percentage.
     */
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        return total > 0 ? (double) hits / total * 100.0 : 0.0;
    }

    /**
     * Gets the average response time in microseconds.
     */
    public double getAverageResponseTimeMicros() {
        long requests = totalRequests.sum();
        long totalTime = totalResponseTime.sum();
        return requests > 0 ? (double) totalTime / requests / 1000.0 : 0.0;
    }

    /**
     * Gets the current metrics summary.
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
                cacheHits.get(),
                cacheMisses.get(),
                totalRequests.sum(),
                getAverageResponseTimeMicros(),
                minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get() / 1000,
                maxResponseTime.get() / 1000,
                rotationCount.get(),
                rotationTime.sum() / 1000,
                configReloads.get(),
                reloadTime.sum() / 1000
        );
    }

    /**
     * Resets all metrics.
     */
    public void reset() {
        cacheHits.set(0);
        cacheMisses.set(0);
        totalRequests.reset();
        totalResponseTime.reset();
        minResponseTime.set(Long.MAX_VALUE);
        maxResponseTime.set(0);
        rotationCount.set(0);
        rotationTime.reset();
        configReloads.set(0);
        reloadTime.reset();
    }

    private void updateMinResponseTime(long responseTime) {
        long current;
        do {
            current = minResponseTime.get();
        } while (responseTime < current && !minResponseTime.compareAndSet(current, responseTime));
    }

    private void updateMaxResponseTime(long responseTime) {
        long current;
        do {
            current = maxResponseTime.get();
        } while (responseTime > current && !maxResponseTime.compareAndSet(current, responseTime));
    }

    /**
     * Immutable snapshot of current metrics.
     */
    public record MetricsSnapshot(
            long cacheHits,
            long cacheMisses,
            long totalRequests,
            double averageResponseTimeMicros,
            long minResponseTimeMicros,
            long maxResponseTimeMicros,
            long rotationCount,
            long totalRotationTimeMicros,
            long configReloads,
            long totalReloadTimeMicros
    ) {

        public double getCacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total * 100.0 : 0.0;
        }

        public double getAverageRotationTimeMicros() {
            return rotationCount > 0 ? (double) totalRotationTimeMicros / rotationCount : 0.0;
        }

        public double getAverageReloadTimeMicros() {
            return configReloads > 0 ? (double) totalReloadTimeMicros / configReloads : 0.0;
        }

        @Override
        public String toString() {
            return """
                    UltraMOTD Metrics:
                      Cache: %d hits, %d misses (%.2f%% hit rate)
                      Requests: %d total
                      Response Time: %.2fμs avg, %dμs min, %dμs max
                      Rotations: %d (%.2fμs avg)
                      Reloads: %d (%.2fμs avg)
                    """.formatted(
                    cacheHits, cacheMisses, getCacheHitRate(),
                    totalRequests,
                    averageResponseTimeMicros, minResponseTimeMicros, maxResponseTimeMicros,
                    rotationCount, getAverageRotationTimeMicros(),
                    configReloads, getAverageReloadTimeMicros()
            );
        }
    }
}
