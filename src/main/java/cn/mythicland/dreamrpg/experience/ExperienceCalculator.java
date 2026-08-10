package cn.mythicland.dreamrpg.experience;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure level/progress calculator using the vanilla cumulative curve.
 */
public final class ExperienceCalculator {

    /**
     * Internal scale used for fractional experience retention.
     */
    public static final int SCALE = 6;

    private ExperienceCalculator() {
    }

    /**
     * Applies one effective experience amount.
     *
     * @param level         current RPG level
     * @param current       current-level experience
     * @param amount        effective non-negative experience
     * @param configuredCap maximum level, or {@code -1} for unlimited
     * @return calculated state
     */
    public static Calculation apply(
            long level,
            BigDecimal current,
            BigDecimal amount,
            long configuredCap
    ) {
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        BigDecimal normalizedCurrent = normalize(Objects.requireNonNull(current, "current"));
        BigDecimal normalizedAmount = normalize(Objects.requireNonNull(amount, "amount"));
        if (normalizedCurrent.signum() < 0 || normalizedAmount.signum() < 0) {
            throw new IllegalArgumentException("experience cannot be negative");
        }
        if (configuredCap < -1L) throw new IllegalArgumentException("configuredCap must be -1 or greater");

        boolean atCap = configuredCap >= 0L && level >= configuredCap;
        if (atCap) {
            return new Calculation(level, required(level), 0L, true);
        }

        BigDecimal total = ExperienceCurve.totalExperienceAtLevel(level)
                .add(normalizedCurrent)
                .add(normalizedAmount);
        long nextLevel = highestReachableLevel(level, total, configuredCap);
        boolean capped = configuredCap >= 0L && nextLevel >= configuredCap;
        BigDecimal nextCurrent;
        if (capped) {
            nextCurrent = required(nextLevel);
        } else {
            nextCurrent = normalize(total.subtract(ExperienceCurve.totalExperienceAtLevel(nextLevel)));
        }
        long levelsGained = nextLevel - level;
        return new Calculation(nextLevel, nextCurrent, levelsGained, capped);
    }

    /**
     * Normalizes an experience value to the supported fractional precision.
     *
     * @param value input amount
     * @return scale-six amount
     */
    public static BigDecimal normalize(BigDecimal value) {
        return Objects.requireNonNull(value, "value").setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal required(long level) {
        return BigDecimal.valueOf(ExperienceCurve.requiredForNextLevel(level));
    }

    private static long highestReachableLevel(long currentLevel, BigDecimal total, long configuredCap) {
        long high;
        if (configuredCap >= 0L) {
            high = configuredCap;
        } else {
            high = currentLevel == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : Math.max(1L, currentLevel + 1L);
            while (ExperienceCurve.totalExperienceAtLevel(high).compareTo(total) <= 0
                    && high < Long.MAX_VALUE) {
                long doubled = high > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : high * 2L;
                if (doubled == high) break;
                high = doubled;
            }
        }

        if (high <= currentLevel) return high;
        long low = currentLevel;
        if (ExperienceCurve.totalExperienceAtLevel(high).compareTo(total) <= 0) return high;
        while (low + 1L < high) {
            long middle = low + (high - low) / 2L;
            if (ExperienceCurve.totalExperienceAtLevel(middle).compareTo(total) <= 0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /**
     * Immutable result of the pure calculation.
     *
     * @param level             resulting level
     * @param currentExperience resulting current-level experience
     * @param levelsGained      levels crossed
     * @param capped            whether the configured cap is reached
     */
    public record Calculation(
            long level,
            BigDecimal currentExperience,
            long levelsGained,
            boolean capped
    ) {

        /**
         * Validates a calculation result.
         */
        public Calculation {
            if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
            currentExperience = ExperienceCalculator.normalize(currentExperience);
            if (currentExperience.signum() < 0) {
                throw new IllegalArgumentException("currentExperience cannot be negative");
            }
            if (levelsGained < 0L) throw new IllegalArgumentException("levelsGained cannot be negative");
        }
    }
}
