package org.rafalohaki.ultramotd.netty;

import io.netty.util.AttributeKey;

/**
 * Channel attributes for storing handshake information.
 * Used by UltraHandshakeTracker to record protocol version and virtual host,
 * which are then read by UltraPingNettyHandler for cache key lookup.
 */
public final class ChannelKeys {
    
    /**
     * Protocol version from handshake packet
     */
    public static final AttributeKey<Integer> PROTOCOL_VERSION =
            AttributeKey.valueOf("ultramotd_protocol_version");
    
    /**
     * Virtual host (server address) from handshake packet
     */
    public static final AttributeKey<String> VIRTUAL_HOST =
            AttributeKey.valueOf("ultramotd_virtual_host");

    private ChannelKeys() {}
}
