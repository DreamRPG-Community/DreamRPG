package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Database-independent persistence boundary for player storage snapshots.
 */
public interface PlayerStorageStore {

    /**
     * Loads an existing snapshot or creates empty first-login storage.
     *
     * @param uniqueId player UUID
     * @return persisted or newly created snapshot
     * @throws SQLException when the database operation fails
     */
    PlayerStorageSnapshot loadOrCreate(UUID uniqueId) throws SQLException;

    /**
     * Saves a snapshot using optimistic version checking.
     *
     * @param snapshot        snapshot to persist
     * @param expectedVersion database version captured before saving
     * @return next database version
     * @throws SQLException when persistence or optimistic checking fails
     */
    long save(PlayerStorageSnapshot snapshot, long expectedVersion) throws SQLException;
}
