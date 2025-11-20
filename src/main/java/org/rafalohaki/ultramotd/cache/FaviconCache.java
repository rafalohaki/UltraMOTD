package org.rafalohaki.ultramotd.cache;

import com.velocitypowered.api.util.Favicon;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * High-performance favicon cache with TTL and size limits.
 * Implements Netty best practices: pooled allocators, direct buffers, proper reference counting.
 */
public class FaviconCache extends AbstractCache<Favicon> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(FaviconCache.class);

    private Path dataDirectory;

    public FaviconCache(int maxCacheSize, long maxAgeMs) {
        super(maxCacheSize, maxAgeMs);

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

        // Store dataDirectory for use in loadAndCacheItem
        this.dataDirectory = dataDirectory;

        CacheEntry<Favicon> entry = getOrLoad(faviconPath);
        if (entry == null) {
            return null;
        }

        return new CachedFavicon(entry.item(), entry.buffer().retainedSlice());
    }

    /**
     * Loads favicon from disk and caches it.
     * Implements fail-fast validation and proper resource cleanup.
     */
    @Override
    protected CacheEntry<Favicon> loadAndCacheItem(String faviconPath) {
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
            CacheEntry<Favicon> newEntry = new CacheEntry<>(favicon, directBuffer, Instant.now().plusMillis(maxAgeMs));
            cache.put(faviconPath, newEntry);
            
            logger.debug("Cached favicon: {} ({} bytes, expires in {}ms)", 
                         faviconPath, fileSize, maxAgeMs);
            
            return newEntry;
            
        } catch (IOException e) {
            logger.error("Failed to load favicon: {} - {}", fullPath, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error loading favicon: {} - {}", fullPath, e.getMessage(), e);
            return null;
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
}
