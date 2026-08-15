package cn.mythicland.dreamrpg.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public DreamRPG health progression service exposed through Bukkit's ServicesManager.
 */
public interface HealthApi {

    /**
     * Returns the configured health progression for a ready player.
     *
     * @param uniqueId player UUID
     * @return immutable health snapshot, or empty while the player's experience data is not ready
     */
    Optional<HealthSnapshot> snapshot(UUID uniqueId);
}
