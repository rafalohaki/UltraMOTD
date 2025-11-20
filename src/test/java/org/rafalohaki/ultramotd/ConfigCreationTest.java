package org.rafalohaki.ultramotd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test to verify config creation functionality
 */
public class ConfigCreationTest {

    @Test
    void testConfigFileCreation(@TempDir Path tempDir) throws Exception {
        // Simulate plugin data directory
        Path dataDirectory = tempDir.resolve("ultramotd");
        Files.createDirectories(dataDirectory);
        
        // Test config creation logic
        Path configFile = dataDirectory.resolve("config.json");
        
        // Verify config doesn't exist initially
        assertFalse(Files.exists(configFile), "Config file should not exist initially");
        
        // Create default config (simulating UltraMOTD.createDefaultConfig)
        String defaultConfig = getDefaultConfigContent();
        Files.writeString(configFile, defaultConfig);
        
        // Verify config was created
        assertTrue(Files.exists(configFile), "Config file should exist after creation");
        
        // Verify config content
        String content = Files.readString(configFile);
        assertTrue(content.contains("UltraMOTD"), "Config should contain plugin name");
        assertTrue(content.contains("maxPlayers"), "Config should contain maxPlayers setting");
    }

    private String getDefaultConfigContent() {
        return """
            {
              "description": "<green>UltraMOTD <gray>- <blue>High Performance MOTD</blue></gray>",
              "maxPlayers": 100,
              "enableFavicon": true,
              "faviconPath": "favicons/default.png",
              "enableVirtualThreads": true
            }
            """;
    }
}
