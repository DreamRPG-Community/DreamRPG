package cn.mythicland.dreamrpgcombat.stats;

import cn.mythicland.dreamrpg.api.HealthSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the optional DreamRPG health snapshot for a player.
 */
@FunctionalInterface
public interface DreamRPGHealthResolver {

    /**
     * Resolves one player's ready DreamRPG health values.
     *
     * @param uniqueId player UUID
     * @return health snapshot, or empty while unavailable
     */
    Optional<HealthSnapshot> resolve(UUID uniqueId);
}
