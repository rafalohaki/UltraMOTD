package org.rafalohaki.ultramotd.cache;

import com.velocitypowered.api.util.Favicon;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance favicon cache with TTL and size limits.
 * Implements Netty best practices: pooled allocators, direct buffers, proper reference counting.
 */
public class FaviconCache {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(FaviconCache.class);
    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxCacheSize;
    private final long maxAgeMs;
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger cacheEvictions = new AtomicInteger(0);

    public FaviconCache(int maxCacheSize, long maxAgeMs) {
        this.maxCacheSize = maxCacheSize;
        this.maxAgeMs = maxAgeMs;
        
        logger.info("FaviconCache initialized: maxCacheSize={}, maxAgeMs={}ms", maxCacheSize, maxAgeMs);
    }

    /**
     * Gets favicon from cache or loads from disk.
     * Uses direct ByteBuf for optimal network I/O performance.
     */
    public CachedFavicon getFavicon(String faviconPath, Path dataDirectory) {
        if (faviconPath == null || faviconPath.trim().isEmpty()) {
            return null;
        }

        CacheEntry entry = cache.get(faviconPath);
        
        // Check cache hit and validity
        if (entry != null && !entry.isExpired()) {
            cacheHits.incrementAndGet();
            logger.debug("Favicon cache hit: {}", faviconPath);
            return new CachedFavicon(entry.favicon(), entry.buffer().duplicate());
        }

        // Cache miss - load from disk
        cacheMisses.incrementAndGet();
        logger.debug("Favicon cache miss: {}", faviconPath);
        
        return loadAndCacheFavicon(faviconPath, dataDirectory);
    }

    /**
     * Loads favicon from disk and caches it.
     * Implements fail-fast validation and proper resource cleanup.
     */
    private CachedFavicon loadAndCacheFavicon(String faviconPath, Path dataDirectory) {
        Path fullPath = dataDirectory.resolve(faviconPath);
        
        try {
            // Validate file exists and is readable
            if (!Files.exists(fullPath) || !Files.isReadable(fullPath)) {
                logger.warn("Favicon file not found or not readable: {}", fullPath);
                return null;
            }

            // Validate file size (favicon should be small, typically < 64KB)
            long fileSize = Files.size(fullPath);
            if (fileSize > 64 * 1024) {
                logger.warn("Favicon file too large: {} bytes (max: 64KB)", fileSize);
                return null;
            }

            // Load favicon bytes
            byte[] faviconBytes = Files.readAllBytes(fullPath);
            
            // Create Velocity Favicon from Path
            Favicon favicon = Favicon.create(fullPath);
            
            // Create direct ByteBuf for network I/O (zero-copy optimization)
            ByteBuf directBuffer = ALLOCATOR.directBuffer(faviconBytes.length);
            directBuffer.writeBytes(faviconBytes);
            
            // Evict old entries if cache is full
            evictIfNecessary();
            
            // Cache the new entry
            CacheEntry newEntry = new CacheEntry(favicon, directBuffer, Instant.now().plusMillis(maxAgeMs));
            cache.put(faviconPath, newEntry);
            
            logger.info("Cached favicon: {} ({} bytes, expires in {}ms)", 
                       faviconPath, fileSize, maxAgeMs);
            
            return new CachedFavicon(favicon, directBuffer.duplicate());
            
        } catch (IOException e) {
            logger.error("Failed to load favicon: {} - {}", fullPath, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error loading favicon: {} - {}", fullPath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Evicts expired entries if cache exceeds size limit.
     * Uses LRU-like strategy by removing oldest entries first.
     */
    private void evictIfNecessary() {
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
                    CacheEntry removed = cache.remove(oldestKey);
                    if (removed != null) {
                        removed.release();
                        cacheEvictions.incrementAndGet();
                        logger.debug("Evicted oldest favicon from cache: {}", oldestKey);
                    }
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Clears all cached favicons and releases ByteBuf resources.
     */
    public void clear() {
        logger.info("Clearing favicon cache ({} entries)", cache.size());
        
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
    private record CacheEntry(
            Favicon favicon,
            ByteBuf buffer,
            Instant expiresAt,
            Instant createdAt
    ) {
        public CacheEntry(Favicon favicon, ByteBuf buffer, Instant expiresAt) {
            this(favicon, buffer.retain(), expiresAt, Instant.now());
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public void release() {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    /**
     * Wrapper for cached favicon with proper ByteBuf handling.
     */
    public static class CachedFavicon {
        private final Favicon favicon;
        private final ByteBuf buffer;

        public CachedFavicon(Favicon favicon, ByteBuf buffer) {
            this.favicon = favicon;
            this.buffer = buffer;
        }

        public Favicon favicon() {
            return favicon;
        }

        public ByteBuf buffer() {
            return buffer;
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
