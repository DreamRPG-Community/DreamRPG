package cn.mythicland.dreamrpg.database;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Storage contract for the DreamRPG player's persistent career state.
 *
 * <p>The contract deliberately hides the selected JDBC implementation so future inventory and
 * ender-chest storage can reuse the same database boundary.</p>
 */
public interface PlayerProfileStore {

    /**
     * Loads an existing career or creates the supplied default career.
     *
     * @param uniqueId       player UUID
     * @param defaultCareerId configured default career
     * @return persisted or created career identifier
     * @throws SQLException when persistence fails
     */
    String loadOrCreate(UUID uniqueId, String defaultCareerId) throws SQLException;

    /**
     * Updates an existing player's career identifier.
     *
     * @param uniqueId player UUID
     * @param careerId target career identifier
     * @throws SQLException when persistence fails
     */
    void updateCareer(UUID uniqueId, String careerId) throws SQLException;
}
