package cn.mythicland.dreamrpg.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Player profile service contract exposed by DreamRPG through Bukkit's ServicesManager.
 */
public interface DreamRPGApi {

    /**
     * Returns a cached profile or the configured unclassed profile for the UUID.
     *
     * @param uniqueId player UUID
     * @return immutable profile snapshot
     */
    PlayerProfile getProfile(UUID uniqueId);

    /**
     * Loads or creates a profile through DreamRPG's asynchronous persistence boundary.
     *
     * @param uniqueId player UUID
     * @return future completed with an immutable profile snapshot
     */
    CompletableFuture<PlayerProfile> loadProfile(UUID uniqueId);

    /**
     * Changes and persists a player's configured career.
     *
     * @param uniqueId player UUID
     * @param careerId target career identifier
     * @return future completed after the profile cache and career-change event are updated
     */
    CompletableFuture<PlayerProfile> changeCareer(UUID uniqueId, String careerId);

    /**
     * Finds a configured career.
     *
     * @param id career identifier
     * @return configured career when present
     */
    Optional<CareerDefinition> findCareer(String id);

    /**
     * Returns all configured careers in configuration order.
     *
     * @return immutable career definitions
     */
    Collection<CareerDefinition> careers();

    /**
     * Resolves the current display values for a player UUID.
     *
     * @param uniqueId player UUID
     * @return immutable presentation snapshot
     */
    PlayerPresentation presentation(UUID uniqueId);

    /**
     * Returns a defensive copy of the main-city spawn.
     *
     * @return main-city spawn location
     */
    Location mainSpawn();

    /**
     * Teleports a player on Bukkit's primary thread.
     *
     * @param player player to teleport
     * @return future completed with Bukkit's teleport result
     */
    CompletableFuture<Boolean> teleportToSpawn(Player player);
}
