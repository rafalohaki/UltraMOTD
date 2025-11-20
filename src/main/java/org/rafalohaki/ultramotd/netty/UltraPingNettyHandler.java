package org.rafalohaki.ultramotd.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.rafalohaki.ultramotd.cache.PacketPingCache;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class UltraPingNettyHandler extends ChannelInboundHandlerAdapter {

    private final PacketPingCache packetCache;
    private final boolean enabled;
    private final RateLimiter rateLimiter;

    private static final String STATUS_REQUEST_CLASS_NAME = "com.velocitypowered.proxy.protocol.packet.StatusRequest";
    private static final Class<?> STATUS_REQUEST_CLASS;

    static {
        Class<?> c = null;
        try {
            c = Class.forName(STATUS_REQUEST_CLASS_NAME);
        } catch (Exception e) {
            c = null;
        }
        STATUS_REQUEST_CLASS = c;
    }

    public UltraPingNettyHandler(PacketPingCache packetCache, boolean enabled) {
        this(packetCache, enabled, true, 10);
    }

    public UltraPingNettyHandler(PacketPingCache packetCache, boolean enabled, boolean rateLimitEnabled, int maxPingsPerSecond) {
        this.packetCache = packetCache;
        this.enabled = enabled;
        this.rateLimiter = rateLimitEnabled ? new RateLimiter(maxPingsPerSecond) : null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!enabled || packetCache == null) {
            super.channelRead(ctx, msg);
            return;
        }

        // Lekki rate limit per-IP dla STATUS
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

        if (!isStatusRequest(msg)) {
            super.channelRead(ctx, msg);
            return;
        }

        PacketPingCache.Key key = extractKey();
        ByteBuf packet = packetCache.getPacket(key);
        if (packet == null) {
            packet = packetCache.getPacket(new PacketPingCache.Key(0, ""));
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

    private boolean isStatusRequest(Object msg) {
        return STATUS_REQUEST_CLASS != null && STATUS_REQUEST_CLASS.isInstance(msg);
    }

    private PacketPingCache.Key extractKey() {
        // ignorujemy atrybuty kanału, zawsze jeden wariant pakietu
        return new PacketPingCache.Key(0, "");
    }

    /**
     * Lightweight rate limiter for DDoS protection.
     * Uses sliding window of 1 second per IP address.
     */
    private static final class RateLimiter {
        private static final long WINDOW_MILLIS = 1_000L;
        private final int maxPingsPerWindow;

        private static final class IpState {
            volatile long windowStart;
            int count;
        }

        private final ConcurrentHashMap<InetAddress, IpState> states = new ConcurrentHashMap<>();

        RateLimiter(int maxPingsPerSecond) {
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
