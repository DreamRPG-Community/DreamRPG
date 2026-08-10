package cn.mythicland.dreamrpg.experience;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the vanilla experience curve boundaries.
 */
class ExperienceCurveTest {

    @Test
    void requiredExperienceUsesVanillaBoundaryFormulas() {
        assertEquals(7L, ExperienceCurve.requiredForNextLevel(0L));
        assertEquals(35L, ExperienceCurve.requiredForNextLevel(14L));
        assertEquals(37L, ExperienceCurve.requiredForNextLevel(15L));
        assertEquals(107L, ExperienceCurve.requiredForNextLevel(29L));
        assertEquals(112L, ExperienceCurve.requiredForNextLevel(30L));
        assertEquals(121L, ExperienceCurve.requiredForNextLevel(31L));
    }

    @Test
    void cumulativeExperienceMatchesTheVanillaProgression() {
        assertEquals(BigDecimal.ZERO, ExperienceCurve.totalExperienceAtLevel(0L));
        assertEquals(BigDecimal.valueOf(7L), ExperienceCurve.totalExperienceAtLevel(1L));
        assertEquals(BigDecimal.valueOf(352L), ExperienceCurve.totalExperienceAtLevel(16L));
        assertEquals(
                0,
                BigDecimal.valueOf(394L).compareTo(ExperienceCurve.totalExperienceAtLevel(17L))
        );
    }
}
