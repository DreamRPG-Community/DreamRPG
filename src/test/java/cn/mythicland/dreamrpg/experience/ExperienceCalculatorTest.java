package cn.mythicland.dreamrpg.experience;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies level transitions, fractional retention, and caps.
 */
class ExperienceCalculatorTest {

    @Test
    void exactVanillaThresholdCrossesOneLevel() {
        ExperienceCalculator.Calculation result = ExperienceCalculator.apply(
                0L,
                BigDecimal.ZERO,
                BigDecimal.valueOf(7L),
                -1L
        );

        assertEquals(1L, result.level());
        assertEquals(BigDecimal.ZERO.setScale(6), result.currentExperience());
        assertEquals(1L, result.levelsGained());
        assertTrue(!result.capped());
    }

    @Test
    void overflowCanCrossMultipleLevelsAndKeepTheRemainder() {
        ExperienceCalculator.Calculation result = ExperienceCalculator.apply(
                0L,
                BigDecimal.ZERO,
                BigDecimal.valueOf(16.5D),
                -1L
        );

        assertEquals(2L, result.level());
        assertEquals(BigDecimal.valueOf(0.5D).setScale(6), result.currentExperience());
        assertEquals(2L, result.levelsGained());
    }

    @Test
    void fractionalExperienceIsRetainedWithoutChangingProgressFormula() {
        ExperienceCalculator.Calculation result = ExperienceCalculator.apply(
                0L,
                BigDecimal.valueOf(1.25D),
                BigDecimal.valueOf(2.25D),
                -1L
        );

        assertEquals(0L, result.level());
        assertEquals(BigDecimal.valueOf(3.5D).setScale(6), result.currentExperience());
    }

    @Test
    void configuredCapStopsAtFullProgress() {
        ExperienceCalculator.Calculation result = ExperienceCalculator.apply(
                0L,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100L),
                1L
        );

        assertEquals(1L, result.level());
        assertEquals(BigDecimal.valueOf(9L).setScale(6), result.currentExperience());
        assertTrue(result.capped());
    }
}
