package cn.mythicland.dreamrpg.experience;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure implementation of Minecraft's vanilla level curve.
 */
public final class ExperienceCurve {

    private static final BigDecimal TWO_POINT_FIVE = new BigDecimal("2.5");
    private static final BigDecimal FORTY_POINT_FIVE = new BigDecimal("40.5");
    private static final BigDecimal FOUR_POINT_FIVE = new BigDecimal("4.5");
    private static final BigDecimal ONE_HUNDRED_SIXTY_TWO_POINT_FIVE = new BigDecimal("162.5");

    private ExperienceCurve() {
    }

    /**
     * Returns the vanilla experience required to move from one level to the next.
     *
     * @param level current non-negative level
     * @return positive required amount
     */
    public static long requiredForNextLevel(long level) {
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        if (level < 15L) return 2L * level + 7L;
        if (level < 30L) return 5L * level - 38L;
        BigDecimal result = BigDecimal.valueOf(level).multiply(BigDecimal.valueOf(9L))
                .subtract(BigDecimal.valueOf(158L));
        if (result.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) return Long.MAX_VALUE;
        return result.longValueExact();
    }

    /**
     * Returns the vanilla cumulative experience needed to reach a level.
     *
     * @param level non-negative level
     * @return exact cumulative experience
     */
    public static BigDecimal totalExperienceAtLevel(long level) {
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        BigDecimal value = BigDecimal.valueOf(level);
        if (level <= 16L) {
            return value.multiply(value).add(value.multiply(BigDecimal.valueOf(6L)));
        }
        if (level <= 31L) {
            return TWO_POINT_FIVE.multiply(value).multiply(value)
                    .subtract(FORTY_POINT_FIVE.multiply(value))
                    .add(BigDecimal.valueOf(360L));
        }
        return FOUR_POINT_FIVE.multiply(value).multiply(value)
                .subtract(ONE_HUNDRED_SIXTY_TWO_POINT_FIVE.multiply(value))
                .add(BigDecimal.valueOf(2220L));
    }

    /**
     * Returns a normalized current-level fraction.
     *
     * @param current current-level experience
     * @param level   current level
     * @return fraction in the range 0..1
     */
    public static double progress(BigDecimal current, long level) {
        if (current == null) throw new NullPointerException("current");
        long required = requiredForNextLevel(level);
        return current.divide(BigDecimal.valueOf(required), 12, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
