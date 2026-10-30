package cn.mythicland.dreamrpgcombat.actionbar;

import java.util.UUID;

/**
 * Immutable snapshot used to render one attacker's temporary target health bar.
 */
final class CombatHealthBarStatus {

    private final UUID targetUniqueId;
    private final String targetDisplayName;
    private final double remainingHealth;
    private final double remainingAbsorption;
    private final double maxHealth;
    private final double healthDamage;
    private final long expiresAtMillis;

    private CombatHealthBarStatus(Builder builder) {
        if (builder.targetUniqueId == null) throw new IllegalArgumentException("targetUniqueId is required");
        targetUniqueId = builder.targetUniqueId;
        targetDisplayName = builder.targetDisplayName == null || builder.targetDisplayName.isBlank()
                ? "目标"
                : builder.targetDisplayName;
        remainingHealth = nonNegativeFinite(builder.remainingHealth, "remainingHealth");
        remainingAbsorption = nonNegativeFinite(builder.remainingAbsorption, "remainingAbsorption");
        maxHealth = Math.max(1.0D, nonNegativeFinite(builder.maxHealth, "maxHealth"));
        healthDamage = nonNegativeFinite(builder.healthDamage, "healthDamage");
        expiresAtMillis = builder.expiresAtMillis;
    }

    static Builder builder() {
        return new Builder();
    }

    private static double nonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    UUID targetUniqueId() {
        return targetUniqueId;
    }

    String targetDisplayName() {
        return targetDisplayName;
    }

    double remainingHealth() {
        return remainingHealth;
    }

    double remainingAbsorption() {
        return remainingAbsorption;
    }

    double maxHealth() {
        return maxHealth;
    }

    double healthDamage() {
        return healthDamage;
    }

    boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    static final class Builder {

        private UUID targetUniqueId;
        private String targetDisplayName;
        private double remainingHealth;
        private double remainingAbsorption;
        private double maxHealth;
        private double healthDamage;
        private long expiresAtMillis;

        private Builder() {
        }

        Builder targetUniqueId(UUID value) {
            targetUniqueId = value;
            return this;
        }

        Builder targetDisplayName(String value) {
            targetDisplayName = value;
            return this;
        }

        Builder remainingHealth(double value) {
            remainingHealth = value;
            return this;
        }

        Builder remainingAbsorption(double value) {
            remainingAbsorption = value;
            return this;
        }

        Builder maxHealth(double value) {
            maxHealth = value;
            return this;
        }

        Builder healthDamage(double value) {
            healthDamage = value;
            return this;
        }

        Builder expiresAtMillis(long value) {
            expiresAtMillis = value;
            return this;
        }

        CombatHealthBarStatus build() {
            return new CombatHealthBarStatus(this);
        }
    }
}
