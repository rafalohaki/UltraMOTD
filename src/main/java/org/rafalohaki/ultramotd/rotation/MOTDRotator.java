package org.rafalohaki.ultramotd.rotation;

import net.kyori.adventure.text.Component;
import org.rafalohaki.ultramotd.metrics.UltraMOTDMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MOTD rotation system that cycles through multiple MOTD messages.
 * Supports time-based, request-based, and random rotation strategies.
 */
public class MOTDRotator {

    private final UltraMOTDMetrics metrics;
    private final List<Component> motdMessages;
    private final RotationStrategy strategy;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Current state
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    // Configuration
    private final Duration rotationInterval;
    private final int requestsPerRotation;
    private volatile Instant lastRotation = Instant.now();
    private final AtomicReference<Component> currentMOTD;

    public MOTDRotator(List<Component> motdMessages, RotationStrategy strategy,
                       Duration rotationInterval, int requestsPerRotation, UltraMOTDMetrics metrics) {
        this.motdMessages = List.copyOf(motdMessages);
        this.strategy = strategy;
        this.rotationInterval = rotationInterval;
        this.requestsPerRotation = requestsPerRotation;
        this.metrics = metrics;
        this.currentMOTD = new AtomicReference<>(motdMessages.isEmpty() ?
                Component.text("§aUltraMOTD §7- §bHigh Performance MOTD") :
                motdMessages.get(0));
    }

    /**
     * Gets the current MOTD, rotating if necessary based on the configured strategy.
     */
    public Component getCurrentMOTD() {
        lock.readLock().lock();
        try {
            if (shouldRotate()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // Double-check after acquiring write lock
                    if (shouldRotate()) {
                        rotateMOTD();
                    }
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
            }
            return currentMOTD.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Forces an immediate rotation to the next MOTD.
     */
    public void forceRotation() {
        long startTime = System.nanoTime();
        lock.writeLock().lock();
        try {
            rotateMOTD();
        } finally {
            lock.writeLock().unlock();
            metrics.recordRotation(System.nanoTime() - startTime);
        }
    }

    /**
     * Updates the MOTD messages list.
     */
    public void updateMOTDMessages(List<Component> newMessages) {
        lock.writeLock().lock();
        try {
            if (!newMessages.isEmpty()) {
                this.currentMOTD.set(newMessages.get(0));
                this.currentIndex.set(0);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the current rotation index.
     */
    public int getCurrentIndex() {
        return currentIndex.get();
    }

    /**
     * Gets the number of configured MOTD messages.
     */
    public int getMOTDCount() {
        return motdMessages.size();
    }

    /**
     * Gets the rotation strategy.
     */
    public RotationStrategy getStrategy() {
        return strategy;
    }

    private boolean shouldRotate() {
        return switch (strategy) {
            case TIME_BASED -> Duration.between(lastRotation, Instant.now()).compareTo(rotationInterval) >= 0;
            case REQUEST_BASED -> currentIndex.get() % requestsPerRotation == 0;
            case RANDOM -> ThreadLocalRandom.current().nextDouble() < 0.1; // 10% chance per request
            case SEQUENTIAL -> false; // Manual rotation only
        };
    }

    private void rotateMOTD() {
        if (motdMessages.isEmpty()) {
            return;
        }

        int nextIndex = switch (strategy) {
            case TIME_BASED, REQUEST_BASED -> (currentIndex.get() + 1) % motdMessages.size();
            case RANDOM -> ThreadLocalRandom.current().nextInt(motdMessages.size());
            case SEQUENTIAL -> (currentIndex.get() + 1) % motdMessages.size();
        };

        currentIndex.set(nextIndex);
        currentMOTD.set(motdMessages.get(nextIndex));
        lastRotation = Instant.now();
    }

    /**
     * Rotation strategies for MOTD messages.
     */
    public enum RotationStrategy {
        TIME_BASED,      // Rotate based on time interval
        REQUEST_BASED,   // Rotate based on number of requests
        RANDOM,          // Random rotation with probability
        SEQUENTIAL       // Manual/sequential rotation only
    }
}
