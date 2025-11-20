# UltraMOTD

High-performance MOTD plugin for the Velocity proxy focused on predictable latency, low resource usage, and production-ready defaults.

## Key Features

1. **Advanced MOTD Rendering**
   - Full Adventure MiniMessage support (gradients, hex colors, unicode, nested formatting)
   - Multi-line descriptions with YAML literal blocks
2. **Smart Caching**
   - Favicon cache with TTL, size limits, and Netty direct buffers
   - Optional JSON response cache (prepared for future API support)
3. **Java 21 Optimizations**
   - Virtual threads for async tasks
   - Record patterns and modern language features for zero-cost config parsing
4. **Lightweight Configuration**

## License

MIT License. See `LICENSE` for details.
