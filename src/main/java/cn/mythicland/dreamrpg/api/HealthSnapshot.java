package cn.mythicland.dreamrpg.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable DreamRPG health progression values for one ready player.
 *
 * @param uniqueId         player UUID
 * @param level            current DreamRPG level
 * @param baseHealth       configured health at level zero
 * @param levelHealthBonus health added by the current level
 * @param maximumHealth    configured maximum health before other plugin modifiers
 * @param enabled          whether DreamRPG currently owns maximum health for this player
 */
public record HealthSnapshot(
        UUID uniqueId,
        long level,
        double baseHealth,
        double levelHealthBonus,
        double maximumHealth,
        boolean enabled
) {

    /**
     * Validates one immutable health snapshot.
     */
    public HealthSnapshot {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        requirePositiveFinite(baseHealth, "baseHealth");
        requireNonNegativeFinite(levelHealthBonus, "levelHealthBonus");
        requirePositiveFinite(maximumHealth, "maximumHealth");
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
