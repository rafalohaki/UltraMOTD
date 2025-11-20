package org.rafalohaki.ultramotd.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * High-performance JSON response cache for MOTD data.
 * Implements Netty best practices: pooled allocators, direct buffers, proper reference counting.
 */
public class JsonCache extends AbstractCache<Void> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(JsonCache.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final JSONComponentSerializer JSON_SERIALIZER = JSONComponentSerializer.json();

    public JsonCache(int maxCacheSize, long maxAgeMs, boolean compressCache) {
        super(maxCacheSize, maxAgeMs);

        logger.info("JsonCache initialized: maxCacheSize={}, maxAgeMs={}ms, compress={}",
                   maxCacheSize, maxAgeMs, compressCache);
    }

    /**
     * Gets cached JSON MOTD response or creates new one.
     * Uses direct ByteBuf for optimal network I/O performance.
     */
    public CachedJsonResponse getMOTDResponse(Component description, int maxPlayers, String faviconPath) {
        String cacheKey = generateCacheKey(description, maxPlayers, faviconPath);

        CacheEntry<Void> entry = getOrLoad(cacheKey);
        if (entry == null) {
            return null;
        }

        return new CachedJsonResponse(entry.buffer().retainedSlice());
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
    @Override
    protected CacheEntry<Void> loadAndCacheItem(String cacheKey) {
        // Parse cache key back to components
        CacheKey key = GSON.fromJson(cacheKey, CacheKey.class);
        Component description = JSON_SERIALIZER.deserialize(key.description());
        int maxPlayers = key.maxPlayers();
        String faviconPath = key.faviconPath();
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
            CacheEntry<Void> newEntry = new CacheEntry<>(directBuffer, Instant.now().plusMillis(maxAgeMs));
            cache.put(cacheKey, newEntry);
            
            logger.debug("Cached JSON response: {} bytes, expires in {}ms", responseBytes.length, maxAgeMs);
            
            return newEntry;
            
        } catch (Exception e) {
            logger.error("Failed to create JSON response for cache: {}", e.getMessage(), e);
            return null;
        }
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
}
