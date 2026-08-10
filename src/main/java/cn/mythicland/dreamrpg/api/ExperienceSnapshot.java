package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable RPG experience snapshot.
 *
 * @param uniqueId             player UUID
 * @param level                RPG level
 * @param currentExperience    experience accumulated inside the current level
 * @param requiredForNextLevel experience required by the current level's vanilla curve
 * @param progress             normalized progress in the range 0..1
 * @param capped               whether the configured level cap has been reached
 * @param ready                whether the player's data has been applied
 */
public record ExperienceSnapshot(
        UUID uniqueId,
        long level,
        BigDecimal currentExperience,
        long requiredForNextLevel,
        double progress,
        boolean capped,
        boolean ready
) {

    /**
     * Validates and freezes one snapshot.
     */
    @SuppressWarnings("DataFlowIssue")
    public ExperienceSnapshot {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        currentExperience = Objects.requireNonNull(currentExperience, "currentExperience");
        if (currentExperience.signum() < 0) throw new IllegalArgumentException("currentExperience cannot be negative");
        if (requiredForNextLevel <= 0L) {
            throw new IllegalArgumentException("requiredForNextLevel must be positive");
        }
        if (!Double.isFinite(progress) || progress < 0.0D || progress > 1.0D) {
            throw new IllegalArgumentException("progress must be finite and between 0 and 1");
        }
    }

    /**
     * Creates the initial not-ready snapshot used before asynchronous player data is applied.
     *
     * @param uniqueId player UUID
     * @return initial snapshot
     */
    public static ExperienceSnapshot notReady(UUID uniqueId) {
        return new ExperienceSnapshot(
                Objects.requireNonNull(uniqueId, "uniqueId"),
                0L,
                BigDecimal.ZERO,
                7L,
                0.0D,
                false,
                false
        );
    }
}
