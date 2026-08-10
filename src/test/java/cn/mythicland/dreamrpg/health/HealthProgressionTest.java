package cn.mythicland.dreamrpg.health;

import cn.mythicland.dreamrpg.config.ExperienceSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies configurable maximum-health growth and current-health preservation.
 */
class HealthProgressionTest {

    private static final ExperienceSettings DEFAULTS = new ExperienceSettings(-1L, true, 20.0D, 5.0D);

    @Test
    void levelZeroUsesTheConfiguredBaseHealth() {
        assertEquals(20.0D, HealthProgression.maximumHealth(DEFAULTS, 0L));
    }

    @Test
    void levelGrowthUsesTheConfiguredPerLevelValue() {
        assertEquals(25.0D, HealthProgression.maximumHealth(DEFAULTS, 1L));
        assertEquals(70.0D, HealthProgression.maximumHealth(DEFAULTS, 10L));
        assertEquals(520.0D, HealthProgression.maximumHealth(DEFAULTS, 100L));
    }

    @Test
    void changingConfigurationChangesTheFormula() {
        ExperienceSettings settings = new ExperienceSettings(-1L, true, 40.0D, 2.0D);
        assertEquals(60.0D, HealthProgression.maximumHealth(settings, 10L));
    }

    @Test
    void fullPlayersRemainFullAfterGrowth() {
        assertEquals(25.0D, HealthProgression.adjustedCurrentHealth(20.0D, 20.0D, 25.0D));
    }

    @Test
    void injuredPlayersKeepAbsoluteCurrentHealth() {
        assertEquals(12.0D, HealthProgression.adjustedCurrentHealth(12.0D, 20.0D, 25.0D));
        assertEquals(15.0D, HealthProgression.adjustedCurrentHealth(20.0D, 20.0D, 15.0D));
    }
}
