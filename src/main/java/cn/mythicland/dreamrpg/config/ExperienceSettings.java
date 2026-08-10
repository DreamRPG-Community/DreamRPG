package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.lib.config.ConfigValue;
import cn.mythicland.lib.config.ConfigView;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

/**
 * Mutable configuration snapshot for DreamRPG's experience and health systems.
 *
 * @param maxLevel       maximum RPG level, or {@code -1} for no configured cap
 * @param healthEnabled  whether DreamRPG owns the player's maximum health
 * @param baseHealth     maximum health at RPG level zero
 * @param healthPerLevel maximum-health growth per RPG level
 */
public record ExperienceSettings(
        long maxLevel,
        boolean healthEnabled,
        double baseHealth,
        double healthPerLevel
) {

    /**
     * Validates one configuration snapshot.
     */
    public ExperienceSettings {
        if (maxLevel < -1L) throw new IllegalArgumentException("experience.max-level must be -1 or greater");
        if (!Double.isFinite(baseHealth) || baseHealth <= 0.0D) {
            throw new IllegalArgumentException("health.base-health must be finite and positive");
        }
        if (!Double.isFinite(healthPerLevel) || healthPerLevel < 0.0D) {
            throw new IllegalArgumentException("health.health-per-level must be finite and non-negative");
        }
    }

    /**
     * Loads this snapshot from an arbitrary Bukkit configuration.
     *
     * @param configuration Bukkit configuration
     * @return validated immutable settings
     */
    public static ExperienceSettings load(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return from(ConfigSupport.bind(configuration, RawSettings.class));
    }

    /**
     * Loads this snapshot through Lib's read-only configuration view.
     *
     * @param configuration Lib configuration view
     * @return validated immutable settings
     */
    static ExperienceSettings bind(ConfigView configuration) {
        return from(Objects.requireNonNull(configuration, "configuration").bind(RawSettings.class));
    }

    private static ExperienceSettings from(RawSettings raw) {
        return new ExperienceSettings(
                raw.maxLevel(),
                raw.healthEnabled(),
                raw.baseHealth(),
                raw.healthPerLevel()
        );
    }

    private record RawSettings(
            @ConfigValue(
                    path = "experience.max-level",
                    defaultValue = "-1"
            )
            long maxLevel,
            @ConfigValue(
                    path = "health.enabled",
                    defaultValue = "true"
            )
            boolean healthEnabled,
            @ConfigValue(
                    path = "health.base-health",
                    defaultValue = "20.0",
                    positive = true
            )
            double baseHealth,
            @ConfigValue(
                    path = "health.health-per-level",
                    defaultValue = "5.0",
                    nonNegative = true
            )
            double healthPerLevel
    ) {
    }
}
