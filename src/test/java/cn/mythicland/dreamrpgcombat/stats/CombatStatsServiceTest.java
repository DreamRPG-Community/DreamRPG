package cn.mythicland.dreamrpgcombat.stats;

import cn.mythicland.dreamrpg.api.HealthSnapshot;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatStatsServiceTest {

    @Test
    void aggregatesDamageRangesAndClampsPercentageAttributes() {
        CombatItemStats mainHand = new CombatItemStats(Map.of(
                CombatStat.DAMAGE, new NumericRange(400.0D, 450.0D, false),
                CombatStat.DEFENSE, new NumericRange(70.0D, 70.0D, true),
                CombatStat.CRIT_CHANCE, new NumericRange(60.0D, 60.0D, true),
                CombatStat.CRIT_DAMAGE, new NumericRange(30.0D, 30.0D, true)
        ), List.of());
        CombatItemStats armor = new CombatItemStats(Map.of(
                CombatStat.DAMAGE, new NumericRange(10.0D, 20.0D, false),
                CombatStat.DEFENSE, new NumericRange(50.0D, 50.0D, true),
                CombatStat.HEALTH, new NumericRange(1700.0D, 1700.0D, false),
                CombatStat.HEALTH_REGEN, new NumericRange(10.0D, 10.0D, true),
                CombatStat.CRIT_CHANCE, new NumericRange(60.0D, 60.0D, true)
        ), List.of());

        CombatStatsSnapshot result = CombatStatsService.aggregate(List.of(mainHand, armor));

        assertEquals(410.0D, result.damageMinimum());
        assertEquals(470.0D, result.damageMaximum());
        assertEquals(100.0D, result.defensePercent());
        assertEquals(1700.0D, result.health());
        assertEquals(10.0D, result.healthRegenPercent());
        assertEquals(100.0D, result.critChancePercent());
        assertEquals(30.0D, result.critDamagePercent());
        assertTrue(result.hasDamage());
        assertTrue(result.hasDefense());
    }

    @Test
    void aggregatesSignedValuesAndClampsEffectiveDomains() {
        CombatItemStats mainHand = new CombatItemStats(Map.of(
                CombatStat.DAMAGE, new NumericRange(-20.0D, -10.0D, false),
                CombatStat.DEFENSE, new NumericRange(-58.0D, -58.0D, true),
                CombatStat.HEALTH, new NumericRange(-100.0D, -100.0D, false),
                CombatStat.HEALTH_REGEN, new NumericRange(-5.0D, -5.0D, true),
                CombatStat.CRIT_CHANCE, new NumericRange(-20.0D, -20.0D, true),
                CombatStat.CRIT_DAMAGE, new NumericRange(-50.0D, -50.0D, true),
                CombatStat.EXPERIENCE_BONUS, new NumericRange(-50.0D, -50.0D, true)
        ), List.of());
        CombatItemStats armor = new CombatItemStats(Map.of(
                CombatStat.DAMAGE, new NumericRange(20.0D, 30.0D, false),
                CombatStat.DEFENSE, new NumericRange(10.0D, 10.0D, true),
                CombatStat.HEALTH, new NumericRange(50.0D, 50.0D, false),
                CombatStat.HEALTH_REGEN, new NumericRange(10.0D, 10.0D, true),
                CombatStat.CRIT_CHANCE, new NumericRange(15.0D, 15.0D, true),
                CombatStat.CRIT_DAMAGE, new NumericRange(25.0D, 25.0D, true),
                CombatStat.EXPERIENCE_BONUS, new NumericRange(100.0D, 100.0D, true)
        ), List.of());

        CombatStatsSnapshot result = CombatStatsService.aggregate(List.of(mainHand, armor));

        assertEquals(0.0D, result.damageMinimum());
        assertEquals(20.0D, result.damageMaximum());
        assertEquals(-48.0D, result.defensePercent());
        assertEquals(-50.0D, result.health());
        assertEquals(5.0D, result.healthRegenPercent());
        assertEquals(0.0D, result.critChancePercent());
        assertEquals(-25.0D, result.critDamagePercent());
        assertEquals(50.0D, result.experienceBonusPercent());
        assertTrue(result.hasDamage());
    }

    @Test
    void pureNegativeDamageDoesNotReplaceVanillaDamage() {
        CombatStatsSnapshot result = CombatStatsService.aggregate(List.of(
                new CombatItemStats(Map.of(
                        CombatStat.DAMAGE, new NumericRange(-20.0D, -10.0D, false)
                ), List.of())
        ));

        assertEquals(-20.0D, result.damageMinimum());
        assertEquals(-10.0D, result.damageMaximum());
        assertFalse(result.hasDamage());
    }

    @Test
    void includesDreamRPGBaseAndLevelHealthInTheTotal() {
        CombatStatsSnapshot result = CombatStatsService.aggregate(
                List.of(new CombatItemStats(
                        Map.of(CombatStat.HEALTH, new NumericRange(15.0D, 15.0D, false)),
                        List.of()
                )),
                new HealthSnapshot(UUID.randomUUID(), 10L, 20.0D, 50.0D, 70.0D, true)
        );

        assertEquals(15.0D, result.health());
        assertEquals(20.0D, result.baseHealth());
        assertEquals(50.0D, result.levelHealthBonus());
        assertEquals(85.0D, result.totalHealth());
    }

    @Test
    void levelRequirementIsCheckedAgainstTheRpgLevel() {
        CombatItemStats restricted = new CombatItemStats(
                Map.of(CombatStat.DAMAGE, new NumericRange(10.0D, 10.0D, false)),
                List.of(),
                200L
        );

        assertFalse(CombatStatsService.usableAtLevel(restricted, 199L));
        assertTrue(CombatStatsService.usableAtLevel(restricted, 200L));
        assertTrue(CombatStatsService.usableAtLevel(new CombatItemStats(Map.of(), List.of()), 0L));
    }
}
