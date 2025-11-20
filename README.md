# UltraMOTD

High-performance MOTD plugin for the Velocity proxy focused on predictable latency, low resource usage, and production-ready defaults.

## Key Features

1. **Multi-Level Caching Architecture**
   - **API Mode (default)**: ServerPing pre-building cache (zero allocation)
   - **Packet Mode**: Pre-serialized packet cache
   - Favicon cache with TTL, size limits, and Netty direct buffers
   - MiniMessage parsing moved out of hot-path

2. **Advanced MOTD Rendering**
   - Full Adventure MiniMessage support (gradients, hex colors, unicode, nested formatting)
   - Multi-line descriptions with YAML literal blocks
   - Custom `<center>` tag for text alignment

3. **Performance Modes**
   - **Standard Mode**: Zero-allocation ServerPing cache (~80% of max speed, 100% stable)
   - **Netty Mode**: Packet-level caching (~95% of max speed, requires `performance.netty.pipelineInjection: true`)
   - Sub-microsecond ping handler latency in both modes

4. **Java 21 Optimizations**
   - Virtual threads for async tasks
   - Record patterns and modern language features for zero-cost config parsing
   
5. **Production-Ready**
   - Graceful fallback from Netty to standard mode
   - Built-in DDoS protection with configurable rate limiting
   - No reflection/unsafe in default mode
   - Comprehensive configuration options

## License

MIT License. See `LICENSE` for details.
