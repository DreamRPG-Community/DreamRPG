package cn.mythicland.dreamrpgcombat.combat;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatFormulaTest {

    @Test
    void selectsDamageInsideConfiguredRange() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(400.0D)
                .damageMaximum(450.0D)
                .hasDamage(true)
                .build();

        assertEquals(400.0D, CombatFormula.damage(stats, 0.0D));
        assertEquals(425.0D, CombatFormula.damage(stats, 0.5D));
        assertEquals(450.0D, CombatFormula.damage(stats, 1.0D));
    }

    @Test
    void appliesCriticalMultiplierAndDefensePercentage() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(10.0D)
                .damageMaximum(10.0D)
                .hasDamage(true)
                .defensePercent(50.0D)
                .hasDefense(true)
                .critChancePercent(30.0D)
                .critDamagePercent(30.0D)
                .build();

        assertTrue(CombatFormula.critical(stats, 29.99D));
        assertFalse(CombatFormula.critical(stats, 30.0D));
        assertEquals(1.8D, CombatFormula.criticalMultiplier(stats, true));
        assertEquals(5.0D, CombatFormula.applyDefense(10.0D, stats.defensePercent()));
        assertEquals(1.5D, CombatFormula.criticalMultiplier(CombatStatsSnapshot.empty(), true));
    }

    @Test
    void appliesSafeBoundsToSignedCombatValues() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(-20.0D)
                .damageMaximum(-10.0D)
                .hasDamage(true)
                .critDamagePercent(-50.0D)
                .build();

        assertEquals(0.0D, CombatFormula.damage(stats, 0.0D));
        assertEquals(0.0D, CombatFormula.damage(stats, 1.0D));
        assertEquals(1.0D, CombatFormula.criticalMultiplier(stats, true));
        assertEquals(15.0D, CombatFormula.applyDefense(10.0D, -50.0D));
        assertEquals(20.0D, CombatFormula.applyDefense(10.0D, -150.0D));
    }

    @Test
    void clampsExperienceMultiplierAtZero() {
        assertEquals(2.0D, CombatFormula.experienceMultiplier(100.0D));
        assertEquals(0.5D, CombatFormula.experienceMultiplier(-50.0D));
        assertEquals(0.0D, CombatFormula.experienceMultiplier(-100.0D));
        assertEquals(0.0D, CombatFormula.experienceMultiplier(-150.0D));
    }
}
