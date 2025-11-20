package org.rafalohaki.ultramotd.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.rafalohaki.ultramotd.cache.PacketPingCache;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

import static org.rafalohaki.ultramotd.netty.ChannelKeys.PROTOCOL_VERSION;

public class UltraPingNettyHandler extends ChannelInboundHandlerAdapter {

    private final PacketPingCache packetCache;
    private final boolean enabled;
    private final RateLimiter rateLimiter;
    private final PacketRebuilder packetRebuilder;

    @FunctionalInterface
    public interface PacketRebuilder {
        void rebuildPacketForProtocol(int protocol);
    }

    public UltraPingNettyHandler(PacketPingCache packetCache, boolean enabled) {
        this(packetCache, enabled, null, null);
    }

    public UltraPingNettyHandler(PacketPingCache packetCache, boolean enabled, boolean rateLimitEnabled, int maxPingsPerSecond) {
        this(packetCache, enabled, rateLimitEnabled ? new RateLimiter(maxPingsPerSecond) : null, null);
    }

    public UltraPingNettyHandler(PacketPingCache packetCache, boolean enabled, RateLimiter rateLimiter, PacketRebuilder packetRebuilder) {
        this.packetCache = packetCache;
        this.enabled = enabled;
        this.rateLimiter = rateLimiter;
        this.packetRebuilder = packetRebuilder;
    }

    private boolean isStatusRequest(Object msg) {
        // Simple check - we can improve this later if needed
        return msg != null && msg.getClass().getSimpleName().contains("StatusRequest");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!enabled || packetCache == null) {
            super.channelRead(ctx, msg);
            return;
        }

        // Najpierw sprawdzamy, czy to w ogóle STATUS request
        if (!isStatusRequest(msg)) {
            super.channelRead(ctx, msg);
            return;
        }

        // Dopiero dla STATUS request robimy rate limiting
        if (rateLimiter != null) {
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            InetAddress ip = remote.getAddress();
            long now = System.currentTimeMillis();
            if (!rateLimiter.allow(ip, now)) {
                // Rate limit exceeded - close connection silently (only debug log)
                if (msg instanceof ByteBuf buf) {
                    buf.release();
                }
                ctx.close();
                return;
            }
        }

        PacketPingCache.Key key = extractKey(ctx);
        ByteBuf packet = packetCache.getPacket(key);
        if (packet == null) {
            // Packet not cached for this protocol - need to rebuild
            rebuildPacketForProtocol(key.protocolVersion());
            packet = packetCache.getPacket(key);
            if (packet == null) {
                super.channelRead(ctx, msg);
                return;
            }
        }

        if (msg instanceof ByteBuf b) {
            b.release();
        }

        ctx.writeAndFlush(packet).addListener(f -> ctx.close());
    }

    private void rebuildPacketForProtocol(int protocolVersion) {
        if (packetRebuilder == null) {
            return;
        }
        try {
            packetRebuilder.rebuildPacketForProtocol(protocolVersion);
        } catch (Exception e) {
            // swallow and fallback to upstream handling
        }
    }

    private PacketPingCache.Key extractKey(ChannelHandlerContext ctx) {
        // Extract protocol version from channel attributes set by UltraHandshakeTracker
        Integer protocol = ctx.channel().attr(PROTOCOL_VERSION).get();
        int protocolVersion = protocol != null ? protocol : 0; // Fallback to 0 if not set

        // For each protocol version, we cache one packet variant
        return new PacketPingCache.Key(protocolVersion, "");
    }

    /**
     * Lightweight rate limiter for DDoS protection.
     * Uses sliding window of 1 second per IP address.
     */
    public static final class RateLimiter {
        private static final long WINDOW_MILLIS = 1_000L;
        private final int maxPingsPerWindow;

        private static final class IpState {
            volatile long windowStart;
            int count;
        }

        private final ConcurrentHashMap<InetAddress, IpState> states = new ConcurrentHashMap<>();

        public RateLimiter(int maxPingsPerSecond) {
            this.maxPingsPerWindow = maxPingsPerSecond;
        }

        boolean allow(InetAddress addr, long now) {
            IpState state = states.computeIfAbsent(addr, a -> {
                IpState s = new IpState();
                s.windowStart = now;
                s.count = 0;
                return s;
            });

            synchronized (state) {
                if (now - state.windowStart > WINDOW_MILLIS) {
                    state.windowStart = now;
                    state.count = 1;
                    return true;
                }
                if (state.count < maxPingsPerWindow) {
                    state.count++;
                    return true;
                }
                return false;
            }
        }
    }
}
