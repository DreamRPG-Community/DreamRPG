package cn.mythicland.dreamrpgcombat.combat;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;

public final class CombatFormula {

    private static final double BASE_CRITICAL_DAMAGE_PERCENT = 100.0D;

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
