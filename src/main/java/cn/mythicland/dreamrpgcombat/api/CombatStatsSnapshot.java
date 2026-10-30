package cn.mythicland.dreamrpgcombat.api;

import cn.mythicland.dreamrpgcombat.lore.CombatStat;

import java.util.Objects;

/**
 * Immutable effective Combat attributes for one living entity.
 */
public final class CombatStatsSnapshot {

    private final double damageMinimum;
    private final double damageMaximum;
    private final boolean hasDamage;
    private final double defensePercent;
    private final boolean hasDefense;
    private final double health;
    private final double baseHealth;
    private final double levelHealthBonus;
    private final double healthRegenPercent;
    private final double critChancePercent;
    private final double critDamagePercent;
    private final double experienceBonusPercent;

    private CombatStatsSnapshot(Builder builder) {
        validateFinite(builder.damageMinimum, "damageMinimum");
        validateFinite(builder.damageMaximum, "damageMaximum");
        if (builder.damageMinimum > builder.damageMaximum) {
            throw new IllegalArgumentException("damageMinimum cannot exceed damageMaximum");
        }
        validateRange(builder.defensePercent, -100.0D, 100.0D, "defensePercent");
        validateFinite(builder.health, "health");
        validateRange(builder.baseHealth, 0.0D, Double.MAX_VALUE, "baseHealth");
        validateRange(builder.levelHealthBonus, 0.0D, Double.MAX_VALUE, "levelHealthBonus");
        validateFinite(
                builder.baseHealth + builder.levelHealthBonus + builder.health,
                "totalHealth"
        );
        validateRange(builder.healthRegenPercent, 0.0D, 100.0D, "healthRegenPercent");
        validateRange(builder.critChancePercent, 0.0D, 100.0D, "critChancePercent");
        validateFinite(builder.critDamagePercent, "critDamagePercent");
        validateFinite(builder.experienceBonusPercent, "experienceBonusPercent");

        damageMinimum = builder.damageMinimum;
        damageMaximum = builder.damageMaximum;
        hasDamage = builder.hasDamage;
        defensePercent = builder.defensePercent;
        hasDefense = builder.hasDefense;
        health = builder.health;
        baseHealth = builder.baseHealth;
        levelHealthBonus = builder.levelHealthBonus;
        healthRegenPercent = builder.healthRegenPercent;
        critChancePercent = builder.critChancePercent;
        critDamagePercent = builder.critDamagePercent;
        experienceBonusPercent = builder.experienceBonusPercent;
    }

    /**
     * Creates a builder with safe defaults. Critical damage is stored as a bonus above the
     * base 100% multiplier, so the default bonus is 0% and the effective multiplier is 1.0x.
     *
     * @return a new snapshot builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns an empty snapshot that leaves vanilla combat untouched.
     *
     * @return an empty snapshot
     */
    public static CombatStatsSnapshot empty() {
        return builder().build();
    }

    private static void validateFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void validateRange(double value, double minimum, double maximum, String name) {
        validateFinite(value, name);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum
            );
        }
    }

    public double damageMinimum() {
        return damageMinimum;
    }

    public double damageMaximum() {
        return damageMaximum;
    }

    public boolean hasDamage() {
        return hasDamage;
    }

    public double defensePercent() {
        return defensePercent;
    }

    public boolean hasDefense() {
        return hasDefense;
    }

    public double health() {
        return health;
    }

    /**
     * Returns DreamRPG's configured health at RPG level zero, or zero when DreamRPG health is
     * unavailable for this snapshot.
     *
     * @return configured base health
     */
    public double baseHealth() {
        return baseHealth;
    }

    /**
     * Returns the health added by the player's DreamRPG level, or zero when unavailable.
     *
     * @return level-based health contribution
     */
    public double levelHealthBonus() {
        return levelHealthBonus;
    }

    /**
     * Returns equipment health plus DreamRPG base and level-based health.
     *
     * @return effective health total represented by this snapshot
     */
    public double totalHealth() {
        return baseHealth + levelHealthBonus + health;
    }

    public double healthRegenPercent() {
        return healthRegenPercent;
    }

    public double critChancePercent() {
        return critChancePercent;
    }

    /**
     * Returns the extra critical-damage percentage, excluding the base 100% damage.
     *
     * @return the extra critical-damage percentage
     */
    public double critDamagePercent() {
        return critDamagePercent;
    }

    /**
     * Returns the signed experience percentage contributed by active equipment.
     *
     * @return signed experience percentage
     */
    public double experienceBonusPercent() {
        return experienceBonusPercent;
    }

    /**
     * Returns a value by public stat identifier.
     *
     * @param stat the requested attribute
     * @return the effective value
     */
    public double value(CombatStat stat) {
        return switch (Objects.requireNonNull(stat, "stat")) {
            case DAMAGE -> damageMinimum;
            case DEFENSE -> defensePercent;
            case HEALTH -> health;
            case HEALTH_REGEN -> healthRegenPercent;
            case CRIT_CHANCE -> critChancePercent;
            case CRIT_DAMAGE -> critDamagePercent;
            case EXPERIENCE_BONUS -> experienceBonusPercent;
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CombatStatsSnapshot snapshot)) return false;
        return Double.compare(damageMinimum, snapshot.damageMinimum) == 0
                && Double.compare(damageMaximum, snapshot.damageMaximum) == 0
                && hasDamage == snapshot.hasDamage
                && Double.compare(defensePercent, snapshot.defensePercent) == 0
                && hasDefense == snapshot.hasDefense
                && Double.compare(health, snapshot.health) == 0
                && Double.compare(baseHealth, snapshot.baseHealth) == 0
                && Double.compare(levelHealthBonus, snapshot.levelHealthBonus) == 0
                && Double.compare(healthRegenPercent, snapshot.healthRegenPercent) == 0
                && Double.compare(critChancePercent, snapshot.critChancePercent) == 0
                && Double.compare(critDamagePercent, snapshot.critDamagePercent) == 0
                && Double.compare(experienceBonusPercent, snapshot.experienceBonusPercent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                damageMinimum,
                damageMaximum,
                hasDamage,
                defensePercent,
                hasDefense,
                health,
                baseHealth,
                levelHealthBonus,
                healthRegenPercent,
                critChancePercent,
                critDamagePercent,
                experienceBonusPercent
        );
    }

    @Override
    public String toString() {
        return "CombatStatsSnapshot["
                + "damageMinimum=" + damageMinimum
                + ", damageMaximum=" + damageMaximum
                + ", hasDamage=" + hasDamage
                + ", defensePercent=" + defensePercent
                + ", hasDefense=" + hasDefense
                + ", health=" + health
                + ", baseHealth=" + baseHealth
                + ", levelHealthBonus=" + levelHealthBonus
                + ", healthRegenPercent=" + healthRegenPercent
                + ", critChancePercent=" + critChancePercent
                + ", critDamagePercent=" + critDamagePercent
                + ", experienceBonusPercent=" + experienceBonusPercent
                + ']';
    }

    /**
     * Named construction API for the snapshot's multiple values and flags.
     */
    public static final class Builder {

        private double damageMinimum;
        private double damageMaximum;
        private boolean hasDamage;
        private double defensePercent;
        private boolean hasDefense;
        private double health;
        private double baseHealth;
        private double levelHealthBonus;
        private double healthRegenPercent;
        private double critChancePercent;
        private double critDamagePercent;
        private double experienceBonusPercent;

        private Builder() {
        }

        public Builder damageMinimum(double value) {
            damageMinimum = value;
            return this;
        }

        public Builder damageMaximum(double value) {
            damageMaximum = value;
            return this;
        }

        public Builder hasDamage(boolean value) {
            hasDamage = value;
            return this;
        }

        public Builder defensePercent(double value) {
            defensePercent = value;
            return this;
        }

        public Builder hasDefense(boolean value) {
            hasDefense = value;
            return this;
        }

        public Builder health(double value) {
            health = value;
            return this;
        }

        public Builder baseHealth(double value) {
            baseHealth = value;
            return this;
        }

        public Builder levelHealthBonus(double value) {
            levelHealthBonus = value;
            return this;
        }

        public Builder healthRegenPercent(double value) {
            healthRegenPercent = value;
            return this;
        }

        public Builder critChancePercent(double value) {
            critChancePercent = value;
            return this;
        }

        public Builder critDamagePercent(double value) {
            critDamagePercent = value;
            return this;
        }

        public Builder experienceBonusPercent(double value) {
            experienceBonusPercent = value;
            return this;
        }

        public CombatStatsSnapshot build() {
            return new CombatStatsSnapshot(this);
        }
    }
}
