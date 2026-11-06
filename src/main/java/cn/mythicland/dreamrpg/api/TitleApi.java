package cn.mythicland.dreamrpg.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only titles service registered with Bukkit while DreamRPG is enabled. All reads are thread-safe.
 */
public interface TitleApi {
    /**
     * @return the current immutable definitions in configuration order
     */
    List<TitleDefinition> definitions();

    /**
     * @param playerId player UUID
     * @return loaded ownership, or empty while offline, loading, or after a load failure
     */
    Optional<TitleSnapshot> snapshot(UUID playerId);

    /**
     * Resolves the selected title against current definitions and the current expiry time.
     *
     * @param playerId player UUID
     * @return equipped, owned and unexpired title, or empty when none is effective
     */
    Optional<TitleDefinition> equipped(UUID playerId);
}
