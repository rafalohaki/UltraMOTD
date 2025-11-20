package org.rafalohaki.ultramotd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rafalohaki.ultramotd.config.UltraConfig;
import org.rafalohaki.ultramotd.config.UltraYamlConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test YAML configuration loading functionality.
 */
class YamlConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testYamlConfigLoading() throws Exception {
        // Create a sample YAML config
        String yamlContent = """
motd:
  description: "<green>Test MOTD</green>"
  maxPlayers: 50
  enableFavicon: false
  faviconPath: "test.png"
  enableVirtualThreads: true

playerCount:
  enabled: true
  maxCountType: "ADD_SOME"
  maxCount: 5
  showRealPlayers: true

performance:
  packetOptimization:
    preSerialization: true
    zeroCopyWrite: false
    batchSize: 32

cache:
  favicon:
    enabled: false
  json:
    enabled: true
  enableMetrics: false

serialization:
  descriptionFormat: "MINIMESSAGE"
  enableFallback: true
  strictParsing: false

java21:
  enableVirtualThreads: true
  enablePreviewFeatures: false
  enableRecordPatterns: true
  enableStringTemplates: false
""";

        Path configFile = tempDir.resolve("config.yml");
        java.nio.file.Files.writeString(configFile, yamlContent);

        // Test loading
        Logger logger = LoggerFactory.getLogger("TestLogger");
        UltraYamlConfigLoader loader = new UltraYamlConfigLoader(logger);
        UltraConfig config = loader.loadConfig(configFile);

        // Verify loaded values
        assertNotNull(config);
        assertEquals(50, config.motd().maxPlayers());
        assertFalse(config.motd().enableFavicon());
        assertTrue(config.playerCount().enabled());
        assertEquals(5, config.playerCount().maxCount());
        assertTrue(config.performance().packetOptimization().preSerialization());
        assertFalse(config.performance().packetOptimization().zeroCopyWrite());
        assertEquals(32, config.performance().packetOptimization().batchSize());
        assertFalse(config.cache().favicon().enabled());
        assertTrue(config.cache().json().enabled());
        assertFalse(config.cache().enableMetrics());
        assertEquals(UltraConfig.SerializationConfig.DescriptionFormat.MINIMESSAGE, 
                    config.serialization().descriptionFormat());
        assertTrue(config.java21().enableVirtualThreads());
    }

    @Test
    void testDefaultConfigCreation() {
        Logger logger = LoggerFactory.getLogger("TestLogger");
        UltraYamlConfigLoader loader = new UltraYamlConfigLoader(logger);
        
        // Test loading non-existent file returns defaults
        UltraConfig config = loader.loadConfig(tempDir.resolve("nonexistent.yml"));
        
        assertNotNull(config);
        assertEquals(100, config.motd().maxPlayers());
        assertTrue(config.motd().enableFavicon());
        assertTrue(config.java21().enableVirtualThreads());
    }
}
