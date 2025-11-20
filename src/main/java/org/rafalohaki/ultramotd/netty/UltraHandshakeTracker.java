package org.rafalohaki.ultramotd.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;

import java.lang.reflect.Method;

import static org.rafalohaki.ultramotd.netty.ChannelKeys.PROTOCOL_VERSION;
import static org.rafalohaki.ultramotd.netty.ChannelKeys.VIRTUAL_HOST;

/**
 * Netty handler that tracks handshake packets and stores protocol version + virtual host
 * in channel attributes for later use by UltraPingNettyHandler.
 * 
 * Uses reflection to access Velocity's internal Handshake packet class.
 * Gracefully degrades if reflection fails (logs warning but doesn't crash).
 */
public final class UltraHandshakeTracker extends ChannelInboundHandlerAdapter {
    
    private final Logger logger;

    private static final String HANDSHAKE_CLASS_NAME =
            "com.velocitypowered.proxy.protocol.packet.Handshake";

    private static final Class<?> HANDSHAKE_CLASS;
    private static final Method GET_PROTOCOL_VERSION;
    private static final Method GET_SERVER_ADDRESS;

    static {
        Class<?> clazz = null;
        Method proto = null;
        Method addr = null;
        try {
            clazz = Class.forName(HANDSHAKE_CLASS_NAME);
            proto = clazz.getMethod("getProtocolVersion");
            addr = clazz.getMethod("getServerAddress");
        } catch (Throwable t) {
            // Will be logged by instance
        }
        HANDSHAKE_CLASS = clazz;
        GET_PROTOCOL_VERSION = proto;
        GET_SERVER_ADDRESS = addr;
    }

    public UltraHandshakeTracker(Logger logger) {
        this.logger = logger;
        if (HANDSHAKE_CLASS == null) {
            logger.warn("Failed to load Velocity Handshake class - handshake tracking disabled");
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (HANDSHAKE_CLASS != null && HANDSHAKE_CLASS.isInstance(msg)) {
            try {
                int protocol = (int) GET_PROTOCOL_VERSION.invoke(msg);
                String host = (String) GET_SERVER_ADDRESS.invoke(msg);

                ctx.channel().attr(PROTOCOL_VERSION).set(protocol);
                ctx.channel().attr(VIRTUAL_HOST).set(host);

                logger.trace("Tracked handshake: protocol={}, host={}", protocol, host);
            } catch (Throwable t) {
                logger.debug("Failed to inspect handshake packet: {}", t.getMessage());
            }
        }

        super.channelRead(ctx, msg);
    }
}
