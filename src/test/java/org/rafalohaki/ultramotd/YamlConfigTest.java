package org.rafalohaki.ultramotd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rafalohaki.ultramotd.config.UltraConfig;
import org.rafalohaki.ultramotd.config.UltraYamlConfigLoader;
import org.rafalohaki.ultramotd.config.ConfigConstants;
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
        // Create a sample YAML config with essential user-facing settings
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
""";

        Path configFile = tempDir.resolve(ConfigConstants.CONFIG_FILENAME);
        java.nio.file.Files.writeString(configFile, yamlContent);

        // Test loading
        Logger logger = LoggerFactory.getLogger("TestLogger");
        UltraYamlConfigLoader loader = new UltraYamlConfigLoader(logger);
        UltraConfig config = loader.loadConfig(configFile);

        // Verify loaded values - performance and Java 21 settings use optimal defaults
        assertNotNull(config);
        assertEquals(50, config.motd().maxPlayers());
        assertFalse(config.motd().enableFavicon());
        assertTrue(config.motd().enableVirtualThreads());
        assertTrue(config.playerCount().enabled());
        assertEquals(5, config.playerCount().maxCount());
        
        // Verify optimal performance defaults are applied automatically
        assertTrue(config.performance().packetOptimization().preSerialization());
        assertTrue(config.performance().packetOptimization().zeroCopyWrite());
        assertEquals(64, config.performance().packetOptimization().batchSize());
        assertFalse(config.cache().favicon().enabled());  // From test config
        assertTrue(config.cache().json().enabled());
        assertFalse(config.cache().enableMetrics());  // From test config
        assertEquals(UltraConfig.SerializationConfig.DescriptionFormat.MINIMESSAGE, 
                    config.serialization().descriptionFormat());
        
        // Verify Java 21 features use hardcoded optimal defaults
        assertTrue(config.java21().enableVirtualThreads());    // Always enabled for performance
        assertFalse(config.java21().enablePreviewFeatures()); // Disabled for stability
        assertTrue(config.java21().enableRecordPatterns());   // Enabled for performance
        assertFalse(config.java21().enableStringTemplates()); // Disabled for compatibility
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
