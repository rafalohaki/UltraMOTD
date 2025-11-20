package org.rafalohaki.ultramotd.cache;

import com.velocitypowered.api.proxy.server.ServerPing;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.rafalohaki.ultramotd.util.VarInts;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance packet-level cache for Minecraft status responses.
 * Caches complete serialized packets (ByteBuf) instead of just ServerPing objects,
 * eliminating JSON serialization and packet building overhead in hot-path.
 * 
 * Performance characteristics:
 * - Zero allocation on cache hit (retainedSlice of pre-built packet)
 * - Zero JSON serialization (done once at cache time)
 * - Thread-safe with ConcurrentHashMap
 * - Proper ByteBuf ref-counting to prevent memory leaks
 * 
 * Architecture:
 * - Experimental feature: packet-level caching
 * - Falls back to ServerPingCache + ProxyPingEvent if disabled
 * - Requires Netty pipeline injection (experimental.netty-direct config)
 */
public final class PacketPingCache {

    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;
    private static final long TTL_MS = 2000L;
    private final ConcurrentHashMap<Key, PacketEntry> cache = new ConcurrentHashMap<>();
    private final Logger logger;

    public PacketPingCache(Logger logger) {
        this.logger = logger;
    }

    /**
     * Cache key for packet variants.
     * Different protocol versions or virtual hosts may require different packets.
     */
    public record Key(int protocolVersion, String virtualHost) {
        public Key {
            virtualHost = virtualHost == null ? "" : virtualHost;
        }
    }

    /**
     * Builds and caches a complete status response packet for given parameters.
     * Call this whenever active MOTD changes (reload/rotation).
     * 
     * Packet format (without outer length - Velocity encoder adds it):
     * [packetId=0x00 VarInt][jsonLength VarInt][JSON UTF-8]
     */
    public void updatePacket(Key key, ServerPing ping) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ping, "ping");

        // 1. Serialize ServerPing -> JSON status
        String json = ServerPingSerializer.toJson(ping);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // 2. Calculate packet size: packetId(=0), VarInt(lenJSON), JSON bytes
        // NO outer packetLength VarInt - Velocity's MinecraftVarintLengthEncoder adds it
        int jsonLen = jsonBytes.length;
        int packetLen = 1 + VarInts.sizeOf(jsonLen) + jsonLen;

        ByteBuf buf = ALLOCATOR.directBuffer(packetLen);

        try {
            // [packetId VarInt] = 0x00 for status response
            VarInts.writeVarInt(buf, 0x00);
            // [jsonLength VarInt]
            VarInts.writeVarInt(buf, jsonLen);
            // [json UTF-8]
            buf.writeBytes(jsonBytes);

            long expiresAt = System.currentTimeMillis() + TTL_MS;
            PacketEntry newEntry = new PacketEntry(buf, expiresAt);

            PacketEntry old = cache.put(key, newEntry);
            if (old != null && old.buf.refCnt() > 0) {
                old.buf.release();
            }

            logger.debug("Updated packet cache for key={} ({} bytes, ttl={}ms)", key, packetLen, TTL_MS);
        } catch (Exception e) {
            // If anything fails, release the buffer to prevent leak
            buf.release();
            throw e;
        }
    }

    /**
     * Returns retainedSlice of cached packet ready for sending.
     * Netty handler MUST release after writeAndFlush or let Netty handle it.
     * 
     * @return ByteBuf with retained reference, or null if not cached
     */
    public ByteBuf getPacket(Key key) {
        PacketEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.buf.refCnt() == 0) {
            cache.remove(key, entry);
            return null;
        }
        long now = System.currentTimeMillis();
        if (now >= entry.expiresAt) {
            if (cache.remove(key, entry) && entry.buf.refCnt() > 0) {
                entry.buf.release();
            }
            return null;
        }
        return entry.buf.retainedSlice();
    }

    /**
     * Clears all cached packets and releases their ByteBufs.
     * Call on plugin shutdown or config reload.
     */
    public void clear() {
        cache.values().forEach(entry -> {
            if (entry.buf.refCnt() > 0) {
                entry.buf.release();
            }
        });
        cache.clear();
        logger.info("PacketPingCache cleared");
    }

    /**
     * Returns current cache size
     */
    public int size() {
        return cache.size();
    }

    private static final class PacketEntry {
        final ByteBuf buf;
        final long expiresAt;
        PacketEntry(ByteBuf buf, long expiresAt) {
            this.buf = buf;
            this.expiresAt = expiresAt;
        }
    }
}
