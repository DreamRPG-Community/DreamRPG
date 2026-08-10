package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of one DreamRPG experience grant.
 *
 * @param status          outcome status
 * @param uniqueId        player UUID
 * @param requestedAmount unmodified base amount
 * @param appliedAmount   effective amount accepted by the level system
 * @param previousLevel   level before the grant
 * @param currentLevel    level after the grant
 * @param levelsGained    number of levels crossed by the grant
 * @param snapshot        current immutable state
 */
public record ExperienceGrantResult(
        Status status,
        UUID uniqueId,
        BigDecimal requestedAmount,
        BigDecimal appliedAmount,
        long previousLevel,
        long currentLevel,
        long levelsGained,
        ExperienceSnapshot snapshot
) {

    /**
     * Validates the result contract.
     */
    public ExperienceGrantResult {
        status = Objects.requireNonNull(status, "status");
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        requestedAmount = requireNonNegative(requestedAmount, "requestedAmount");
        appliedAmount = requireNonNegative(appliedAmount, "appliedAmount");
        if (previousLevel < 0L || currentLevel < previousLevel) {
            throw new IllegalArgumentException("Invalid level transition");
        }
        if (levelsGained < 0L) throw new IllegalArgumentException("levelsGained cannot be negative");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!uniqueId.equals(snapshot.uniqueId())) {
            throw new IllegalArgumentException("Result snapshot belongs to another player");
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        BigDecimal result = Objects.requireNonNull(value, fieldName);
        if (result.signum() < 0) throw new IllegalArgumentException(fieldName + " cannot be negative");
        return result;
    }

    /**
     * Describes how the request was handled.
     */
    public enum Status {
        /**
         * The amount was applied.
         */
        APPLIED,
        /**
         * The request contained zero effective experience.
         */
        NO_EXPERIENCE,
        /**
         * Player data has not reached the main-thread ready state.
         */
        NOT_READY,
        /**
         * The configured level cap has already been reached.
         */
        CAPPED
    }
}
