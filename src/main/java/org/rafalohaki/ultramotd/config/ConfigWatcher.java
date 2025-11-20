package org.rafalohaki.ultramotd.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Configuration hot-reload system using Java WatchService.
 * Monitors configuration files for changes and triggers reload callbacks.
 */
public class ConfigWatcher {

    private final Logger logger;
    private final WatchService watchService;
    private final ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Consumer<Path> reloadCallback;
    private final Object threadLock = new Object();
    private final AtomicReference<Thread> watchThread = new AtomicReference<>();

    public ConfigWatcher(Logger logger, Consumer<Path> reloadCallback) throws IOException {
        this.logger = logger;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ConfigWatcher");
            t.setDaemon(true);
            return t;
        });
        this.reloadCallback = reloadCallback;
    }

    /**
     * Factory method to create a ConfigWatcher for UltraMOTD.
     */
    public static ConfigWatcher createUltraMOTDWatcher(Logger logger, Consumer<Path> reloadCallback) {
        try {
            return new ConfigWatcher(logger, reloadCallback);
        } catch (IOException e) {
            logger.error("Failed to create ConfigWatcher: {}", e.getMessage(), e);
            throw new UltraMOTDConfigException(
                    String.format("Cannot initialize configuration watcher for callback %s", reloadCallback), e);
        }
    }

    /**
     * Starts watching the specified directory for configuration changes.
     */
    public void startWatching(Path configDir) throws IOException {
        if (isRunning.get()) {
            logger.warn("ConfigWatcher is already running");
            return;
        }

        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
            logger.info("Created config directory: {}", configDir);
        }

        // Register for file creation and modification events
        configDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        isRunning.set(true);
        synchronized (threadLock) {
            Thread newThread = new Thread(this::watchLoop, "ConfigWatcher-Thread");
            newThread.setDaemon(true);
            watchThread.set(newThread);
            newThread.start();
        }

        logger.info("ConfigWatcher started for directory: {}", configDir);
    }

    /**
     * Stops the configuration watcher.
     */
    public void stopWatching() {
        if (!isRunning.get()) {
            return;
        }

        isRunning.set(false);

        synchronized (threadLock) {
        Thread currentThread = watchThread.getAndSet(null);
        if (currentThread != null) {
            currentThread.interrupt();
        }
        }

        try {
            watchService.close();
        } catch (IOException e) {
            logger.warn("Error closing WatchService: {}", e.getMessage());
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while stopping ConfigWatcher executor");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ConfigWatcher stopped");
    }

    /**
     * Checks if the watcher is currently running.
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    private void watchLoop() {
        logger.debug("ConfigWatcher loop started");

        while (isRunning.get()) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    handleWatchEvent(event);
                }

                key.reset();

            } catch (InterruptedException e) {
                logger.debug("ConfigWatcher interrupted, stopping");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in ConfigWatcher loop: {}", e.getMessage(), e);
            }
        }

        logger.debug("ConfigWatcher loop ended");
    }

    private void handleWatchEvent(WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.OVERFLOW) {
            logger.warn("ConfigWatcher events overflowed");
            return;
        }

        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                kind == StandardWatchEventKinds.ENTRY_MODIFY) {

            Path changedFile = (Path) event.context();

            // Only process configuration files
            if (isConfigFile(changedFile)) {
                logger.info("Configuration file changed: {} ({})", changedFile, kind);

                try {
                    long startTime = System.nanoTime();
                    reloadCallback.accept(changedFile);
                    long duration = System.nanoTime() - startTime;

                    logger.info("Configuration reloaded in {}μs", duration / 1000);

                } catch (Exception e) {
                    logger.error("Failed to reload configuration {}: {}", changedFile, e.getMessage(), e);
                }
            }
        }
    }

    private boolean isConfigFile(Path file) {
        String fileName = file.toString().toLowerCase();
        return fileName.endsWith(".yml") ||
                fileName.endsWith(".yaml") ||
                fileName.endsWith(".json") ||
                fileName.endsWith(".properties");
    }

    /**
     * Custom exception for configuration-related errors.
     */
    public static class UltraMOTDConfigException extends RuntimeException {
        public UltraMOTDConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
