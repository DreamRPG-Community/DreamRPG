package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.experience.ExperienceState;

import java.sql.SQLException;

/**
 * Persistence boundary for DreamRPG experience state.
 */
public interface ExperienceStore {

    /**
     * Loads a state, creating a level-zero row if necessary.
     *
     * @param uniqueId player UUID
     * @return persisted state
     * @throws SQLException if the database operation fails
     */
    ExperienceState loadOrCreate(java.util.UUID uniqueId) throws SQLException;

    /**
     * Saves a state using optimistic locking.
     *
     * @param state           state to persist
     * @param expectedVersion expected current database version
     * @return next database version
     * @throws SQLException if the database operation fails
     */
    long save(ExperienceState state, long expectedVersion) throws SQLException;
}
