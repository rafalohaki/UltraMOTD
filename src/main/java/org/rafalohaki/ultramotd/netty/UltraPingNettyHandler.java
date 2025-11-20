package org.rafalohaki.ultramotd.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.rafalohaki.ultramotd.cache.PacketPingCache;

public class UltraPingNettyHandler extends ChannelInboundHandlerAdapter {

    private final PacketPingCache packetCache;
    private final boolean enabled;

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
        this.packetCache = packetCache;
        this.enabled = enabled;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!enabled || packetCache == null) {
            super.channelRead(ctx, msg);
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
}
