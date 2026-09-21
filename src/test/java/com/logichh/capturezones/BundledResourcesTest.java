package com.logichh.capturezones;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledResourcesTest {
    @Test
    void configContainsTheCompatibilityDefaults() throws Exception {
        YamlConfiguration config = loadYaml("config.yml");

        assertEquals(false, config.getBoolean("conquest.allow-concurrent-matches"));
        assertEquals(10, config.getInt("conquest.max-active-matches"));
        assertTrue(config.getBoolean("conquest.persist-active-matches"));
        assertEquals("ROTATE", config.getString("conquest.actionbar.outside-zone-mode"));
        assertEquals(250, config.getInt("conquest.profiles.default.winner-command-rewards.max-recipients"));
        assertEquals(25, config.getInt("conquest.profiles.default.winner-command-rewards.batch-size"));
    }

    @Test
    void zoneTemplateContainsOwnerWideRewardLimits() throws Exception {
        YamlConfiguration template = loadYaml("zone-template.yml");

        assertEquals(250, template.getInt("rewards.command-rewards.max-recipients"));
        assertEquals(25, template.getInt("rewards.command-rewards.batch-size"));
    }

    @Test
    void englishMessagesRemainValidJson() {
        assertDoesNotThrow(() -> {
            try (Reader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("lang/en.json"),
                StandardCharsets.UTF_8
            )) {
                assertTrue(JsonParser.parseReader(reader).isJsonObject());
            }
        });
    }

    private YamlConfiguration loadYaml(String name) throws URISyntaxException {
        java.net.URL resource = getClass().getClassLoader().getResource(name);
        assertNotNull(resource);
        return YamlConfiguration.loadConfiguration(new File(resource.toURI()));
    }
}
