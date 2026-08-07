package cn.mythicland.dreamrpg.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable player career snapshot.
 *
 * @param uniqueId player UUID
 * @param careerId stable career identifier persisted in SQLite
 * @param career   resolved immutable career definition
 */
public record PlayerProfile(
        UUID uniqueId,
        String careerId,
        CareerDefinition career
) {

    /**
     * Validates that the resolved definition matches the persisted identifier.
     */
    public PlayerProfile {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        careerId = Objects.requireNonNull(careerId, "careerId").trim();
        career = Objects.requireNonNull(career, "career");
        if (!career.id().equals(careerId)) {
            throw new IllegalArgumentException("Profile careerId does not match career definition");
        }
    }
}
