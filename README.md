# UltraMOTD

High-performance MOTD plugin for the Velocity proxy focused on predictable latency, low resource usage, and production-ready defaults.

## Key Features

1. **Zero-Allocation Ping Responses**
   - Pre-built ServerPing cache eliminates object allocation in hot-path
   - ConcurrentHashMap lookup with identity-based keys
   - Sub-microsecond ping handler latency
2. **Advanced MOTD Rendering**
   - Full Adventure MiniMessage support (gradients, hex colors, unicode, nested formatting)
   - Multi-line descriptions with YAML literal blocks
3. **Smart Caching**
   - ServerPing pre-building cache (zero allocation)
   - Favicon cache with TTL, size limits, and Netty direct buffers
   - MiniMessage parsing moved out of hot-path
4. **Java 21 Optimizations**
   - Virtual threads for async tasks
   - Record patterns and modern language features for zero-cost config parsing
5. **Lightweight Configuration**

## License

MIT License. See `LICENSE` for details.
