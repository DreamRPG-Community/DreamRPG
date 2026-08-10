package cn.mythicland.dreamrpg.health;

import cn.mythicland.dreamrpg.config.ExperienceSettings;

import java.util.Objects;

/**
 * Pure maximum-health formula and current-health transition policy.
 */
public final class HealthProgression {

    private static final double FULL_HEALTH_EPSILON = 1.0E-6D;

    private HealthProgression() {
    }

    /**
     * Calculates maximum health from the configured formula.
     *
     * @param settings experience/health settings
     * @param level    RPG level
     * @return maximum health in Bukkit health units
     */
    public static double maximumHealth(ExperienceSettings settings, long level) {
        ExperienceSettings value = Objects.requireNonNull(settings, "settings");
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        double result = value.baseHealth() + level * value.healthPerLevel();
        if (!Double.isFinite(result) || result <= 0.0D) {
            throw new IllegalArgumentException("Calculated maximum health is invalid: " + result);
        }
        return result;
    }

    /**
     * Adjusts current health after a maximum-health change.
     *
     * <p>Players who were full remain full. Injured players keep their absolute health, with a
     * clamp only when it is above the new maximum.</p>
     *
     * @param currentHealth current health before the change
     * @param oldMaximum    old maximum health
     * @param newMaximum    new maximum health
     * @return safe current health after the change
     */
    public static double adjustedCurrentHealth(
            double currentHealth,
            double oldMaximum,
            double newMaximum
    ) {
        if (!Double.isFinite(currentHealth) || !Double.isFinite(oldMaximum) || !Double.isFinite(newMaximum)) {
            throw new IllegalArgumentException("Health values must be finite");
        }
        if (oldMaximum <= 0.0D || newMaximum <= 0.0D) {
            throw new IllegalArgumentException("Health maximums must be positive");
        }
        double safeCurrent = Math.max(0.0D, currentHealth);
        if (safeCurrent >= oldMaximum - FULL_HEALTH_EPSILON) return newMaximum;
        return Math.min(safeCurrent, newMaximum);
    }
}
