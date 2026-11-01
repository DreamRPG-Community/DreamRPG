package cn.mythicland.dreamrpgcombat.combat;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;

public final class CombatFormula {

    private static final double BASE_CRITICAL_DAMAGE_PERCENT = 150.0D;

    private CombatFormula() {
    }

    public static double damage(CombatStatsSnapshot stats, double randomUnit) {
        if (!stats.hasDamage()) return 0.0D;
        double unit = Math.clamp(randomUnit, 0.0D, 1.0D);
        return Math.max(
                0.0D,
                stats.damageMinimum()
                        + (stats.damageMaximum() - stats.damageMinimum()) * unit
        );
    }

    public static boolean critical(CombatStatsSnapshot stats, double randomPercent) {
        return stats.critChancePercent() > 0.0D
                && randomPercent >= 0.0D
                && randomPercent < stats.critChancePercent();
    }

    public static double criticalMultiplier(CombatStatsSnapshot stats, boolean critical) {
        return critical
                ? Math.max(0.0D, (BASE_CRITICAL_DAMAGE_PERCENT + stats.critDamagePercent()) / 100.0D)
                : 1.0D;
    }

    public static double applyDefense(double damage, double defensePercent) {
        return Math.max(0.0D, damage * (1.0D - Math.clamp(defensePercent, -100.0D, 100.0D) / 100.0D));
    }

    /**
     * Samples the matching attack range without replacing a skill's explicit base damage.
     */
    public static double damage(CombatStatsSnapshot stats, CombatDamageType type, double randomUnit) {
        if (type == CombatDamageType.TRUE) return 0;
        if (type == CombatDamageType.PHYSICAL) return damage(stats, randomUnit);
        var range = stats.attribute(CombatStat.MAGIC_DAMAGE);
        return Math.max(0, range.minimum() + (range.maximum() - range.minimum()) * Math.clamp(randomUnit, 0, 1));
    }

    /**
     * Applies typed defense, or the unchanged legacy percentage rule when no typed defense exists.
     */
    public static double applyDefense(
            double damage,
            CombatStatsSnapshot attacker,
            CombatStatsSnapshot target,
            CombatDamageType type,
            double constant
    ) {
        if (!Double.isFinite(constant) || constant <= 0) {
            throw new IllegalArgumentException("Defense constant must be finite and positive");
        }
        if (type == CombatDamageType.TRUE) return Math.max(0, damage);
        if (!target.hasAttribute(type.defense())) {
            return target.hasDefense() ? applyDefense(damage, target.defensePercent()) : Math.max(0, damage);
        }
        double defense = Math.max(0, target.value(type.defense()));
        double percentage = Math.clamp(attacker.value(type.percentagePenetration()), 0, 100) / 100;
        double flat = Math.max(0, attacker.value(type.flatPenetration()));
        double effective = Math.max(0, defense * (1 - percentage) - flat);
        return Math.max(0, damage) * constant / (constant + effective);
    }

    /**
     * Computes healing from actual life damage, excluding overkill and excess recovery.
     */
    public static double lifesteal(
            double finalDamage,
            double targetHealth,
            double currentHealth,
            double maximumHealth,
            double percentage
    ) {
        double lifeDamage = Math.min(Math.max(0, finalDamage), Math.max(0, targetHealth));
        double healing = lifeDamage * Math.clamp(percentage, 0, 100) / 100;
        return Math.min(healing, Math.max(0, maximumHealth - currentHealth));
    }

    /**
     * Converts a signed item experience bonus into the multiplier used by DreamRPG.
     *
     * @param bonusPercent the summed item bonus percentage
     * @return a non-negative experience multiplier
     */
    public static double experienceMultiplier(double bonusPercent) {
        if (!Double.isFinite(bonusPercent)) {
            throw new IllegalArgumentException("bonusPercent must be finite");
        }
        return Math.max(0.0D, 1.0D + bonusPercent / 100.0D);
    }
}
