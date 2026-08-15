package cn.mythicland.dreamrpg.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the order of the 1.8.9 player damage calculation.
 */
class LegacyCombatCompatibilityTest {

    @Test
    void criticalMultipliesBaseDamageBeforeEnchantmentBonus() {
        assertEquals(
                18.25D,
                LegacyCombatCompatibility.calculateLegacyDamage(8.0D, 6.25D, true),
                1.0E-9D
        );
    }

    @Test
    void nonCriticalDamageAddsEnchantmentBonusWithoutCooldownScaling() {
        assertEquals(
                14.25D,
                LegacyCombatCompatibility.calculateLegacyDamage(8.0D, 6.25D, false),
                1.0E-9D
        );
    }
}
