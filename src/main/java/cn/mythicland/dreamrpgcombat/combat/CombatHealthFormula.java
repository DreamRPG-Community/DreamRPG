package cn.mythicland.dreamrpgcombat.combat;

/**
 * Safe maximum-health calculations shared by the runtime listener and unit tests.
 */
public final class CombatHealthFormula {

    private static final double MINIMUM_MAX_HEALTH = 1.0D;

    private CombatHealthFormula() {
    }

    /**
     * Applies a signed equipment health contribution without allowing an invalid maximum.
     *
     * @param baseMaximumHealth maximum health after removing DreamRPG combat's old modifier
     * @param healthBonus       signed DreamRPG combat health contribution
     * @return the new maximum health, never below one Bukkit health point
     */
    public static double maximumHealth(double baseMaximumHealth, double healthBonus) {
        requireFinite(baseMaximumHealth, "baseMaximumHealth");
        requireFinite(healthBonus, "healthBonus");
        double result = baseMaximumHealth + healthBonus;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("maximum health overflow");
        return Math.max(MINIMUM_MAX_HEALTH, result);
    }

    /**
     * Preserves a full player's health on an increase and clamps damaged players only when
     * the new maximum is lower than their current health.
     *
     * @param currentHealth         health before the modifier was recalculated
     * @param previousMaximumHealth previous maximum health
     * @param newMaximumHealth      newly calculated maximum health
     * @return the safe current health to apply
     */
    public static double synchronizedCurrentHealth(
            double currentHealth,
            double previousMaximumHealth,
            double newMaximumHealth
    ) {
        requireFinite(currentHealth, "currentHealth");
        requireFinite(previousMaximumHealth, "previousMaximumHealth");
        requireFinite(newMaximumHealth, "newMaximumHealth");
        if (currentHealth >= previousMaximumHealth) return newMaximumHealth;
        return Math.min(currentHealth, newMaximumHealth);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
