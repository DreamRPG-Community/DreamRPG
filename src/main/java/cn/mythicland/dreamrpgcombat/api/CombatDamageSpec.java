package cn.mythicland.dreamrpgcombat.api;

import java.util.Objects;

/**
 * An explicit skill hit. Attack coefficients scale equipped attacks once; damage is never replaced
 * with weapon damage. Values must be finite and non-negative.
 *
 * @param type            damage channel
 * @param baseDamage      skill's base damage before defense
 * @param physicalScaling coefficient applied to physical attack
 * @param magicScaling    coefficient applied to magic attack
 * @param critical        whether this hit may roll a critical strike
 * @param lifesteal       whether this hit may restore health through lifesteal
 */
public record CombatDamageSpec(
        CombatDamageType type,
        double baseDamage,
        double physicalScaling,
        double magicScaling,
        boolean critical,
        boolean lifesteal
) {
    /**
     * Creates an explicit fixed skill hit without attack scaling, critical strikes or lifesteal.
     */
    public static CombatDamageSpec fixed(CombatDamageType type, double amount) {
        return new CombatDamageSpec(type, amount, 0, 0, false, false);
    }

    public CombatDamageSpec {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(baseDamage) || baseDamage < 0
                || !Double.isFinite(physicalScaling) || physicalScaling < 0
                || !Double.isFinite(magicScaling) || magicScaling < 0) {
            throw new IllegalArgumentException("Skill damage and attack coefficients must be finite and non-negative");
        }
    }
}
