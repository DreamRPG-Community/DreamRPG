package cn.mythicland.dreamrpg.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only access to loaded DreamRPG player storage state.
 */
public interface PlayerStorageApi {

    /**
     * Returns whether the player's persistent data is loaded and applied.
     *
     * @param uniqueId player UUID
     * @return true when the player has a loaded session
     */
    boolean isLoaded(UUID uniqueId);

    /**
     * Returns whether the player's persistent data is currently loading.
     *
     * @param uniqueId player UUID
     * @return true while an asynchronous load is pending
     */
    boolean isLoading(UUID uniqueId);

    /**
     * Finds a defensive storage snapshot for a loaded player.
     *
     * @param uniqueId player UUID
     * @return loaded snapshot when present
     */
    Optional<PlayerStorageSnapshot> findLoaded(UUID uniqueId);

    /**
     * Flushes one loaded session to the selected database.
     *
     * @param uniqueId player UUID
     * @return future completed after persistence
     */
    CompletableFuture<Void> flush(UUID uniqueId);
}
