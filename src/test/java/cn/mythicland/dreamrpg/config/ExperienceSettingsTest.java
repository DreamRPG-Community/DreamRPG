package cn.mythicland.dreamrpg.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the configurable experience and health settings.
 */
class ExperienceSettingsTest {

    @Test
    void settingsAreLoadedFromTheConfiguredPaths() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("experience.max-level", 100);
        configuration.set("health.enabled", true);
        configuration.set("health.base-health", 40.0D);
        configuration.set("health.health-per-level", 2.0D);

        ExperienceSettings settings = ExperienceSettings.load(configuration);

        assertEquals(100L, settings.maxLevel());
        assertTrue(settings.healthEnabled());
        assertEquals(40.0D, settings.baseHealth());
        assertEquals(2.0D, settings.healthPerLevel());
    }

    @Test
    void missingValuesUseTheResourceDefaults() {
        ExperienceSettings settings = ExperienceSettings.load(new YamlConfiguration());

        assertEquals(-1L, settings.maxLevel());
        assertTrue(settings.healthEnabled());
        assertEquals(20.0D, settings.baseHealth());
        assertEquals(5.0D, settings.healthPerLevel());
    }
}
