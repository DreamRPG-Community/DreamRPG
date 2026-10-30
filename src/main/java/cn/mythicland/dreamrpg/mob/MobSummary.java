package cn.mythicland.dreamrpg.mob;

import java.util.Objects;

/**
 * Immutable summary of one loaded MythicMobs mob.
 *
 * @param internalName MythicMobs internal ID
 * @param displayName  display name, falling back to the internal ID when missing
 */
public record MobSummary(String internalName, String displayName) {

    public MobSummary {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(displayName, "displayName");
    }
}
