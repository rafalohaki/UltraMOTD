package org.rafalohaki.ultramotd.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for high-performance caches with TTL and size limits.
 * Implements common caching logic, eviction strategies, and metrics collection.
 *
 * Subclasses must implement the specific data loading/caching logic.
 */
public abstract class AbstractCache<T> {

    protected static final Logger logger = org.slf4j.LoggerFactory.getLogger(AbstractCache.class);
    protected static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    protected final ConcurrentHashMap<String, CacheEntry<T>> cache = new ConcurrentHashMap<>();
    protected final int maxCacheSize;
    protected final long maxAgeMs;
    protected final AtomicInteger cacheHits = new AtomicInteger(0);
    protected final AtomicInteger cacheMisses = new AtomicInteger(0);
    protected final AtomicInteger cacheEvictions = new AtomicInteger(0);

    protected AbstractCache(int maxCacheSize, long maxAgeMs) {
        this.maxCacheSize = maxCacheSize;
        this.maxAgeMs = maxAgeMs;
    }

    /**
     * Gets cached item or loads it if not present/expired.
     */
    protected CacheEntry<T> getOrLoad(String key) {
        CacheEntry<T> entry = cache.get(key);

        // Check cache hit and validity
        if (entry != null && !entry.isExpired()) {
            cacheHits.incrementAndGet();
            logger.debug("Cache hit for key: {}", key);
            return entry;
        }

        // Cache miss - load new item
        cacheMisses.incrementAndGet();
        logger.debug("Cache miss for key: {}", key);

        return loadAndCacheItem(key);
    }

    /**
     * Loads item and caches it. Must be implemented by subclasses.
     */
    protected abstract CacheEntry<T> loadAndCacheItem(String key);

    /**
     * Evicts expired entries if cache exceeds size limit.
     * Uses LRU-like strategy by removing oldest entries first.
     */
    protected void evictIfNecessary() {
        if (cache.size() >= maxCacheSize) {
            // Find and remove expired entries first
            cache.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    entry.getValue().release();
                    cacheEvictions.incrementAndGet();
                    return true;
                }
                return false;
            });

            // If still full, remove oldest entries
            while (cache.size() >= maxCacheSize) {
                String oldestKey = cache.entrySet().stream()
                        .min((e1, e2) -> e1.getValue().createdAt().compareTo(e2.getValue().createdAt()))
                        .map(java.util.Map.Entry::getKey)
                        .orElse(null);

                if (oldestKey != null) {
                    CacheEntry<T> removed = cache.remove(oldestKey);
                    if (removed != null) {
                        removed.release();
                        cacheEvictions.incrementAndGet();
                        logger.debug("Evicted oldest entry from cache: {}", oldestKey);
                    }
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Clears all cached entries and releases resources.
     */
    public void clear() {
        logger.info("Clearing cache ({} entries)", cache.size());

        cache.values().forEach(CacheEntry::release);
        cache.clear();

        // Reset metrics
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheEvictions.set(0);
    }

    /**
     * Returns cache statistics for monitoring.
     */
    public CacheStats getStats() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int totalRequests = hits + misses;
        double hitRate = totalRequests > 0 ? (double) hits / totalRequests : 0.0;

        return new CacheStats(
                cache.size(),
                hits,
                misses,
                cacheEvictions.get(),
                hitRate
        );
    }

    /**
     * Cache entry with expiration tracking.
     * Immutable for thread safety.
     */
    protected static class CacheEntry<T> {
        private final T item;
        private final ByteBuf buffer;
        private final Instant expiresAt;
        private final Instant createdAt;

        public CacheEntry(T item, ByteBuf buffer, Instant expiresAt) {
            this.item = item;
            this.buffer = buffer;
            this.expiresAt = expiresAt;
            this.createdAt = Instant.now();
        }

        public CacheEntry(ByteBuf buffer, Instant expiresAt) {
            this(null, buffer, expiresAt);
        }

        public T item() {
            return item;
        }

        public ByteBuf buffer() {
            return buffer;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public Instant createdAt() {
            return createdAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public void release() {
            if (buffer != null && buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    /**
     * Cache statistics for monitoring and debugging.
     */
    public record CacheStats(
            int currentSize,
            int hits,
            int misses,
            int evictions,
            double hitRate
    ) {
        @Override
        public String toString() {
            return String.format("CacheStats{size=%d, hits=%d, misses=%d, evictions=%d, hitRate=%.2f%%}",
                               currentSize, hits, misses, evictions, hitRate * 100);
        }
    }
}
