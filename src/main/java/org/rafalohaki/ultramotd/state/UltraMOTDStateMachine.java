package org.rafalohaki.ultramotd.state;

import net.kyori.adventure.text.Component;
import org.rafalohaki.ultramotd.config.ConfigWatcher;
import org.rafalohaki.ultramotd.config.MOTDConfig;
import org.rafalohaki.ultramotd.config.UltraConfig;
import org.rafalohaki.ultramotd.config.UltraYamlConfigLoader;
import org.rafalohaki.ultramotd.metrics.UltraMOTDMetrics;
import org.rafalohaki.ultramotd.rotation.MOTDRotator;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main state machine for UltraMOTD that orchestrates MOTD rotation,
 * configuration hot-reload, and metrics collection.
 */
public class UltraMOTDStateMachine {

    private final Logger logger;
    private final Path configPath;
    private final UltraMOTDMetrics metrics;
    private final UltraYamlConfigLoader configLoader;
    private final ConfigWatcher configWatcher;
    private final ScheduledExecutorService scheduler;

    // Current state
    private final AtomicReference<MOTDConfig> currentConfig;
    private final AtomicReference<MOTDRotator> rotator;
    private volatile boolean isRunning = false;

    public UltraMOTDStateMachine(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
        this.metrics = new UltraMOTDMetrics();
        this.configLoader = new UltraYamlConfigLoader(logger);
        this.configWatcher = ConfigWatcher.createUltraMOTDWatcher(logger, this::handleConfigReload);
        this.currentConfig = new AtomicReference<>();
        this.rotator = new AtomicReference<>();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "UltraMOTD-StateMachine");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the state machine with initial configuration.
     */
    public void start() {
        if (isRunning) {
            logger.warn("UltraMOTD state machine is already running");
            return;
        }

        try {
            // Load initial configuration
            UltraConfig ultraConfig = configLoader.loadConfig(configPath);
            MOTDConfig initialConfig = ultraConfig.motd();
            currentConfig.set(initialConfig);
            logger.info("Initial configuration loaded: {}", initialConfig);

            // Initialize MOTD rotator
            initializeRotator();

            // Start configuration watcher
            configWatcher.startWatching(configPath.getParent());

            // Start periodic tasks
            startPeriodicTasks();

            isRunning = true;
            logger.info("UltraMOTD state machine started successfully");

        } catch (Exception e) {
            logger.error("Failed to start UltraMOTD state machine: {}", e.getMessage(), e);
            throw new UltraMOTDStateException(
                    String.format("State machine startup failed for config path: %s", configPath), e);
        }
    }

    /**
     * Stops the state machine and cleans up resources.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        try {
            configWatcher.stopWatching();
            scheduler.shutdown();

            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            logger.info("UltraMOTD state machine stopped");

        } catch (InterruptedException e) {
            logger.warn("Interrupted while stopping state machine");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the current MOTD for the specified protocol version.
     */
    public Component getCurrentMOTD() {
        if (!isRunning || rotator.get() == null) {
            return Component.text("§aUltraMOTD §7- §bHigh Performance MOTD");
        }

        long startTime = System.nanoTime();
        try {
            Component motd = rotator.get().getCurrentMOTD();
            metrics.recordResponseTime(System.nanoTime() - startTime);
            return motd;
        } catch (Exception e) {
            logger.error("Error getting current MOTD from rotator: {}", e.getMessage(), e);
            metrics.recordResponseTime(System.nanoTime() - startTime);
            return Component.text("§aUltraMOTD §7- §bHigh Performance MOTD");
        }
    }

    /**
     * Gets the current configuration.
     */
    public MOTDConfig getCurrentConfig() {
        return currentConfig.get();
    }

    /**
     * Gets the metrics collector.
     */
    public UltraMOTDMetrics getMetrics() {
        return metrics;
    }

    /**
     * Forces an immediate MOTD rotation.
     */
    public void forceRotation() {
        MOTDRotator currentRotator = rotator.get();
        if (currentRotator != null) {
            currentRotator.forceRotation();
            logger.info("Forced MOTD rotation");
        }
    }

    /**
     * Gets the current MOTD rotation index.
     */
    public int getCurrentRotationIndex() {
        MOTDRotator currentRotator = rotator.get();
        return currentRotator != null ? currentRotator.getCurrentIndex() : 0;
    }

    /**
     * Gets the number of configured MOTD messages.
     */
    public int getMOTDCount() {
        MOTDRotator currentRotator = rotator.get();
        return currentRotator != null ? currentRotator.getMOTDCount() : 1;
    }

    /**
     * Checks if the state machine is running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    private void initializeRotator() {
        MOTDConfig config = currentConfig.get();
        List<Component> motdMessages = List.of(
                config.description(),
                Component.text("§6Welcome to UltraMOTD! §7- §bHigh Performance Server"),
                Component.text("§eJoin our community! §7- §bdiscord.gg/example"),
                Component.text("§cCustom MOTD §7- §bPowered by UltraMOTD")
        );

        MOTDRotator newRotator = new MOTDRotator(
                motdMessages,
                MOTDRotator.RotationStrategy.TIME_BASED,
                Duration.ofMinutes(5), // Rotate every 5 minutes
                100, // Or every 100 requests
                metrics
        );
        rotator.set(newRotator);

        logger.info("MOTD rotator initialized with {} messages", motdMessages.size());
    }

    private void startPeriodicTasks() {
        // Log metrics every minute
        scheduler.scheduleAtFixedRate(this::logMetrics, 1, 1, TimeUnit.MINUTES);

        // Health check every 30 seconds
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 30, 30, TimeUnit.SECONDS);
    }

    private void handleConfigReload(Path changedFile) {
        long startTime = System.nanoTime();

        try {
            UltraConfig newUltraConfig = configLoader.loadConfig(configPath);
            MOTDConfig newConfig = newUltraConfig.motd();
            MOTDConfig oldConfig = currentConfig.get();

            if (!newConfig.equals(oldConfig)) {
                currentConfig.set(newConfig);

                // Reinitialize rotator if description changed
                if (!oldConfig.description().equals(newConfig.description())) {
                    initializeRotator();
                }

                logger.info("Configuration reloaded successfully");
            } else {
                logger.debug("Configuration unchanged, skipping reload");
            }

            metrics.recordConfigReload(System.nanoTime() - startTime);

        } catch (Exception e) {
            logger.error("Failed to reload configuration from {}: {}", configPath, e.getMessage(), e);
            throw new UltraMOTDStateException(
                    String.format("Configuration reload failed for file: %s", configPath), e);
        }
    }

    private void logMetrics() {
        UltraMOTDMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
        logger.info("Performance metrics:\n{}", snapshot);
    }

    private void performHealthCheck() {
        try {
            // Basic health checks
            if (!configWatcher.isRunning()) {
                logger.warn("Config watcher is not running");
            }

            if (rotator.get() == null) {
                logger.warn("MOTD rotator is null");
            }

            // Log basic stats
            logger.debug("Health check: running={}, motdCount={}, rotationIndex={}",
                    isRunning, getMOTDCount(), getCurrentRotationIndex());

        } catch (Exception e) {
            logger.error("Health check failed for UltraMOTD state machine: {}", e.getMessage(), e);
            // Health check failures are non-critical, so we log and continue
        }
    }

    /**
     * Custom exception for state machine errors.
     */
    public static class UltraMOTDStateException extends RuntimeException {
        public UltraMOTDStateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
