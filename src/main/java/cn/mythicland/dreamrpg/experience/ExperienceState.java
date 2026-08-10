package cn.mythicland.dreamrpg.experience;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persisted experience state for one player.
 *
 * @param uniqueId          player UUID
 * @param level             RPG level
 * @param currentExperience progress within the current level
 * @param databaseVersion   optimistic persistence version
 */
public record ExperienceState(
        UUID uniqueId,
        long level,
        BigDecimal currentExperience,
        long databaseVersion
) {

    /**
     * Validates and normalizes one state.
     */
    public ExperienceState {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        currentExperience = Objects.requireNonNull(currentExperience, "currentExperience");
        if (currentExperience.signum() < 0) {
            throw new IllegalArgumentException("currentExperience cannot be negative");
        }
        if (databaseVersion < 0L) throw new IllegalArgumentException("databaseVersion cannot be negative");
    }

    /**
     * Creates a new level-zero state.
     *
     * @param uniqueId player UUID
     * @return empty state
     */
    public static ExperienceState initial(UUID uniqueId) {
        return new ExperienceState(Objects.requireNonNull(uniqueId, "uniqueId"), 0L, BigDecimal.ZERO, 0L);
    }

    /**
     * Creates a changed in-memory state while preserving its database version.
     *
     * @param nextLevel      next RPG level
     * @param nextExperience next current-level experience
     * @return changed state
     */
    public ExperienceState withProgress(long nextLevel, BigDecimal nextExperience) {
        return new ExperienceState(uniqueId, nextLevel, nextExperience, databaseVersion);
    }

    /**
     * Creates a state with an updated database version.
     *
     * @param nextVersion persisted version
     * @return versioned state
     */
    public ExperienceState withDatabaseVersion(long nextVersion) {
        return new ExperienceState(uniqueId, level, currentExperience, nextVersion);
    }
}
