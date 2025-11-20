package org.rafalohaki.ultramotd.state;

import net.kyori.adventure.text.Component;
import org.rafalohaki.ultramotd.config.ConfigWatcher;
import org.rafalohaki.ultramotd.config.MOTDConfig;
import org.rafalohaki.ultramotd.config.UltraConfig;
import org.rafalohaki.ultramotd.config.UltraYamlConfigLoader;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simplified state machine for UltraMOTD that handles configuration hot-reload.
 * Removed metrics collection and MOTD rotation for better maintainability.
 */
public class UltraMOTDStateMachine {

    private final Logger logger;
    private final Path configPath;
    private final UltraYamlConfigLoader configLoader;
    private final ConfigWatcher configWatcher;
    private final ScheduledExecutorService scheduler;

    // Current state
    private final AtomicReference<MOTDConfig> currentConfig;
    private volatile boolean isRunning = false;

    public UltraMOTDStateMachine(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
        this.configLoader = new UltraYamlConfigLoader(logger);
        this.configWatcher = ConfigWatcher.createUltraMOTDWatcher(logger, this::handleConfigReload);
        this.currentConfig = new AtomicReference<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "UltraMOTD-StateMachine");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the state machine with configuration watching.
     */
    public void start() {
        if (isRunning) {
            logger.warn("UltraMOTD state machine is already running");
            return;
        }

        try {
            logger.info("Starting UltraMOTD state machine...");
            
            // Load initial configuration
            loadConfiguration();
            
            // Start config file watching
            configWatcher.startWatching(configPath.getParent());
            
            // Schedule periodic health checks
            scheduler.scheduleAtFixedRate(this::performHealthCheck, 30, 30, TimeUnit.SECONDS);
            
            isRunning = true;
            logger.info("UltraMOTD state machine started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start UltraMOTD state machine: {}", e.getMessage(), e);
            throw new UltraMOTDStateException("Failed to start state machine", e);
        }
    }

    /**
     * Stops the state machine and cleans up resources.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        try {
            logger.info("Stopping UltraMOTD state machine...");
            
            isRunning = false;
            
            // Stop config watching
            configWatcher.stopWatching();
            
            // Shutdown scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            logger.info("UltraMOTD state machine stopped");
            
        } catch (InterruptedException e) {
            logger.warn("Interrupted while stopping UltraMOTD state machine");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error stopping UltraMOTD state machine: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the current MOTD configuration.
     */
    public MOTDConfig getCurrentConfig() {
        return currentConfig.get();
    }

    /**
     * Gets the current full configuration.
     */
    public UltraConfig getFullConfig() {
        try {
            return configLoader.loadConfig(configPath);
        } catch (Exception e) {
            logger.error("Error loading full configuration: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if the state machine is running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Handles configuration file reload.
     */
    private void handleConfigReload(Path changedFile) {
        if (!isRunning) {
            return;
        }

        try {
            logger.info("Configuration file changed: {}", changedFile.getFileName());
            loadConfiguration();
            logger.info("Configuration reloaded successfully");
        } catch (Exception e) {
            logger.error("Failed to reload configuration: {}", e.getMessage(), e);
        }
    }

    /**
     * Loads configuration from file.
     */
    private void loadConfiguration() {
        try {
            UltraConfig config = configLoader.loadConfig(configPath);
            MOTDConfig motdConfig = config.motd();
            
            if (motdConfig != null) {
                currentConfig.set(motdConfig);
                logger.info("Loaded MOTD configuration: {} max players", motdConfig.maxPlayers());
            } else {
                logger.warn("MOTD configuration is null, using defaults");
                currentConfig.set(MOTDConfig.getDefault());
            }
            
        } catch (Exception e) {
            logger.error("Failed to load configuration: {}", e.getMessage(), e);
            // Set default configuration on error
            currentConfig.set(MOTDConfig.getDefault());
        }
    }

    /**
     * Performs periodic health check.
     */
    private void performHealthCheck() {
        if (!isRunning) {
            return;
        }

        try {
            // Check configuration file accessibility
            if (!configPath.toFile().exists()) {
                logger.warn("Configuration file not found: {}", configPath);
            }
            
            // Check current configuration
            MOTDConfig config = currentConfig.get();
            if (config == null) {
                logger.warn("Current configuration is null");
            } else {
                logger.debug("Health check: MOTD config loaded with {} max players", config.maxPlayers());
            }
            
        } catch (Exception e) {
            logger.error("Health check failed for UltraMOTD state machine: {}", e.getMessage(), e);
            // Health check failures are non-critical, so we log and continue
        }
    }

    /**
     * Gets current MOTD as Component for display.
     */
    public Component getCurrentMOTD() {
        MOTDConfig config = currentConfig.get();
        if (config != null) {
            return config.description();
        }
        return Component.text("§aUltraMOTD §7- §bHigh Performance MOTD");
    }

    /**
     * Custom exception for state machine errors.
     */
    public static class UltraMOTDStateException extends RuntimeException {
        public UltraMOTDStateException(String message) {
            super(message);
        }

        public UltraMOTDStateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
