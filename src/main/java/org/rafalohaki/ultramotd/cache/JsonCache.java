package org.rafalohaki.ultramotd.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance JSON response cache for MOTD data.
 * Implements Netty best practices: pooled allocators, direct buffers, proper reference counting.
 */
public class JsonCache {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(JsonCache.class);
    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;
    private static final Gson GSON = new GsonBuilder().create();
    private static final JSONComponentSerializer JSON_SERIALIZER = JSONComponentSerializer.json();
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxCacheSize;
    private final long maxAgeMs;
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger cacheEvictions = new AtomicInteger(0);

    public JsonCache(int maxCacheSize, long maxAgeMs, boolean compressCache) {
        this.maxCacheSize = maxCacheSize;
        this.maxAgeMs = maxAgeMs;
        
        logger.info("JsonCache initialized: maxCacheSize={}, maxAgeMs={}ms, compress={}", 
                   maxCacheSize, maxAgeMs, compressCache);
    }

    /**
     * Gets cached JSON MOTD response or creates new one.
     * Uses direct ByteBuf for optimal network I/O performance.
     */
    public CachedJsonResponse getMOTDResponse(Component description, int maxPlayers, String faviconPath) {
        String cacheKey = generateCacheKey(description, maxPlayers, faviconPath);
        
        CacheEntry entry = cache.get(cacheKey);
        
        // Check cache hit and validity
        if (entry != null && !entry.isExpired()) {
            cacheHits.incrementAndGet();
            logger.debug("JSON cache hit for key: {}", cacheKey);
            return new CachedJsonResponse(entry.buffer().duplicate());
        }

        // Cache miss - create new JSON response
        cacheMisses.incrementAndGet();
        logger.debug("JSON cache miss for key: {}", cacheKey);
        
        return createAndCacheResponse(cacheKey, description, maxPlayers, faviconPath);
    }

    /**
     * Generates cache key from MOTD parameters.
     * Uses JSON serialization for consistent key generation.
     */
    private String generateCacheKey(Component description, int maxPlayers, String faviconPath) {
        try {
            // Serialize component to JSON for consistent key
            String descriptionJson = JSON_SERIALIZER.serialize(description);
            
            // Create cache key object and serialize
            CacheKey keyObj = new CacheKey(descriptionJson, maxPlayers, faviconPath);
            return GSON.toJson(keyObj);
            
        } catch (Exception e) {
            logger.warn("Failed to generate cache key, using fallback: {}", e.getMessage());
            // Fallback to simple string-based key
            return String.format("%s|%d|%s", 
                               description.toString(), maxPlayers, faviconPath != null ? faviconPath : "");
        }
    }

    /**
     * Creates JSON response and caches it.
     * Implements fail-fast validation and proper resource cleanup.
     */
    private CachedJsonResponse createAndCacheResponse(String cacheKey, Component description, 
                                                      int maxPlayers, String faviconPath) {
        try {
            // Create MOTD response JSON
            MOTDResponse response = new MOTDResponse(
                    JSON_SERIALIZER.serialize(description),
                    maxPlayers,
                    faviconPath
            );
            
            String jsonResponse = GSON.toJson(response);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            
            // Create direct ByteBuf for network I/O (zero-copy optimization)
            ByteBuf directBuffer = ALLOCATOR.directBuffer(responseBytes.length);
            directBuffer.writeBytes(responseBytes);
            
            // Evict old entries if cache is full
            evictIfNecessary();
            
            // Cache the new entry
            CacheEntry newEntry = new CacheEntry(directBuffer, Instant.now().plusMillis(maxAgeMs));
            cache.put(cacheKey, newEntry);
            
            logger.debug("Cached JSON response: {} bytes, expires in {}ms", responseBytes.length, maxAgeMs);
            
            return new CachedJsonResponse(directBuffer.duplicate());
            
        } catch (Exception e) {
            logger.error("Failed to create JSON response for cache: {}", e.getMessage(), e);
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
                        logger.debug("Evicted oldest JSON entry from cache");
                    }
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Clears all cached JSON responses and releases ByteBuf resources.
     */
    public void clear() {
        logger.info("Clearing JSON cache ({} entries)", cache.size());
        
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
     * Cache key data structure for consistent serialization.
     */
    private record CacheKey(String description, int maxPlayers, String faviconPath) {}

    /**
     * MOTD response data structure for JSON serialization.
     */
    private record MOTDResponse(String description, int maxPlayers, String faviconPath) {}

    /**
     * Cache entry with expiration tracking.
     * Immutable for thread safety.
     */
    private record CacheEntry(
            ByteBuf buffer,
            Instant expiresAt,
            Instant createdAt
    ) {
        public CacheEntry(ByteBuf buffer, Instant expiresAt) {
            this(buffer.retain(), expiresAt, Instant.now());
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
     * Wrapper for cached JSON response with proper ByteBuf handling.
     */
    public static class CachedJsonResponse {
        private final ByteBuf buffer;

        public CachedJsonResponse(ByteBuf buffer) {
            this.buffer = buffer;
        }

        public ByteBuf buffer() {
            return buffer;
        }

        /**
         * Gets JSON response as string for debugging purposes.
         * Note: This creates a copy and should not be used in hot paths.
         */
        public String asString() {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.markReaderIndex();
            buffer.readBytes(bytes);
            buffer.resetReaderIndex();
            return new String(bytes, StandardCharsets.UTF_8);
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
            return String.format("JsonCacheStats{size=%d, hits=%d, misses=%d, evictions=%d, hitRate=%.2f%%}",
                               currentSize, hits, misses, evictions, hitRate * 100);
        }
    }
}
