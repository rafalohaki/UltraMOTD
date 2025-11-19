package org.rafalohaki.ultraMOTD.deployment;

import org.rafalohaki.ultraMOTD.metrics.UltraMOTDMetrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production monitoring system for UltraMOTD deployment.
 * Provides metrics collection, health checks, and alerting.
 */
public class ProductionMonitor {

    private final Logger logger;
    private final UltraMOTDMetrics metrics;
    private final ScheduledExecutorService scheduler;
    private final Path metricsOutputPath;
    // Performance thresholds
    private final double maxAverageResponseTimeMicros;
    private final double minCacheHitRate;
    private final Duration healthCheckInterval;
    private volatile boolean isRunning = false;

    public ProductionMonitor(Logger logger, UltraMOTDMetrics metrics, Path outputDir) {
        this.logger = logger;
        this.metrics = metrics;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ProductionMonitor");
            t.setDaemon(true);
            return t;
        });
        this.metricsOutputPath = outputDir.resolve("metrics.log");

        // Production thresholds
        this.maxAverageResponseTimeMicros = 100.0; // 100μs max average
        this.minCacheHitRate = 95.0; // 95% minimum cache hit rate
        this.healthCheckInterval = Duration.ofMinutes(1);
    }

    /**
     * Starts the production monitoring system.
     */
    public void start() {
        if (isRunning) {
            logger.warn("Production monitor is already running");
            return;
        }

        try {
            // Ensure output directory exists
            Files.createDirectories(metricsOutputPath.getParent());

            // Start monitoring tasks
            scheduler.scheduleAtFixedRate(
                    this::performHealthCheck,
                    1,
                    healthCheckInterval.toMinutes(),
                    TimeUnit.MINUTES
            );

            scheduler.scheduleAtFixedRate(
                    this::logMetricsToFile,
                    0,
                    5,
                    TimeUnit.MINUTES
            );

            isRunning = true;
            logger.info("Production monitor started with thresholds: response_time<{}μs, cache_hit_rate>{}%",
                    maxAverageResponseTimeMicros, minCacheHitRate);

        } catch (IOException e) {
            logger.error("Failed to start production monitor: {}", e.getMessage(), e);
            throw new UltraMOTDMonitorException("Monitor startup failed", e);
        }
    }

    /**
     * Stops the production monitoring system.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            logger.info("Production monitor stopped");
        } catch (InterruptedException e) {
            logger.warn("Interrupted while stopping production monitor");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Performs health check and generates alerts if thresholds are exceeded.
     */
    public void performHealthCheck() {
        try {
            UltraMOTDMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

            boolean healthy = true;

            // Check response time
            if (snapshot.averageResponseTimeMicros() > maxAverageResponseTimeMicros) {
                logger.warn("ALERT: High response time detected: {:.2f}μs (threshold: {}μs)",
                        snapshot.averageResponseTimeMicros(), maxAverageResponseTimeMicros);
                healthy = false;
            }

            // Check cache hit rate
            if (snapshot.getCacheHitRate() < minCacheHitRate) {
                logger.warn("ALERT: Low cache hit rate detected: {:.2f}% (threshold: {}%)",
                        snapshot.getCacheHitRate(), minCacheHitRate);
                healthy = false;
            }

            // Check for unusual patterns
            if (snapshot.totalRequests() == 0) {
                logger.warn("ALERT: No requests recorded in the last monitoring period");
                healthy = false;
            }

            if (healthy) {
                logger.debug("Health check passed: response_time={:.2f}μs, cache_hit_rate={:.2f}%",
                        snapshot.averageResponseTimeMicros(), snapshot.getCacheHitRate());
            }

        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Logs current metrics to file for external monitoring systems.
     */
    public void logMetricsToFile() {
        try {
            UltraMOTDMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            String metricsLine = String.format("%d,%.2f,%.2f,%d,%d,%d%n",
                    System.currentTimeMillis(),
                    snapshot.averageResponseTimeMicros(),
                    snapshot.getCacheHitRate(),
                    snapshot.totalRequests(),
                    snapshot.cacheHits(),
                    snapshot.cacheMisses());

            Files.writeString(metricsOutputPath, metricsLine,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);

        } catch (IOException e) {
            logger.error("Failed to write metrics to file: {}", e.getMessage());
        }
    }

    /**
     * Gets current health status.
     */
    public HealthStatus getHealthStatus() {
        UltraMOTDMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        boolean responseTimeHealthy = snapshot.averageResponseTimeMicros() <= maxAverageResponseTimeMicros;
        boolean cacheHitRateHealthy = snapshot.getCacheHitRate() >= minCacheHitRate;
        boolean requestsHealthy = snapshot.totalRequests() > 0;

        return new HealthStatus(
                responseTimeHealthy && cacheHitRateHealthy && requestsHealthy,
                snapshot.averageResponseTimeMicros(),
                snapshot.getCacheHitRate(),
                snapshot.totalRequests()
        );
    }

    /**
     * Custom exception for monitoring errors.
     */
    public static class UltraMOTDMonitorException extends RuntimeException {
        public UltraMOTDMonitorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Health status snapshot.
     */
    public record HealthStatus(
            boolean healthy,
            double averageResponseTimeMicros,
            double cacheHitRate,
            long totalRequests
    ) {

        public String getStatusMessage() {
            if (healthy) {
                return "HEALTHY";
            } else {
                return String.format("UNHEALTHY: response_time=%.2fμs, cache_hit_rate=%.2f%%, requests=%d",
                        averageResponseTimeMicros, cacheHitRate, totalRequests);
            }
        }
    }
}
