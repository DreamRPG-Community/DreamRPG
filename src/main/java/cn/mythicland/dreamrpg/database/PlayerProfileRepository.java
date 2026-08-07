package cn.mythicland.dreamrpg.database;

import cn.mythicland.lib.database.SqlDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists only the stable career identifier for each DreamRPG player.
 */
public final class PlayerProfileRepository implements PlayerProfileStore {

    private final SqlDatabase database;

    /**
     * Creates a profile repository.
     *
     * @param database initialized DreamRPG JDBC database
     */
    public PlayerProfileRepository(SqlDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Loads a career identifier, creating the default row when necessary.
     *
     * @param uniqueId       player UUID
     * @param defaultCareerId configured default career identifier
     * @return persisted or newly created career identifier
     * @throws SQLException when the selected JDBC database rejects the operation
     */
    public String loadOrCreate(UUID uniqueId, String defaultCareerId) throws SQLException {
        Objects.requireNonNull(uniqueId, "uniqueId");
        String careerId = Objects.requireNonNull(defaultCareerId, "defaultCareerId").trim();
        if (careerId.isBlank()) throw new IllegalArgumentException("defaultCareerId cannot be blank");
        String uuid = uniqueId.toString();
        long now = System.currentTimeMillis();
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT career_id FROM player_profiles WHERE uuid = ?"
            )) {
                select.setString(1, uuid);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) return resultSet.getString("career_id");
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_profiles "
                            + "(uuid, career_id, created_at, updated_at) VALUES (?, ?, ?, ?)"
            )) {
                insert.setString(1, uuid);
                insert.setString(2, careerId);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
            }
            return careerId;
        });
    }

    /**
     * Persists a career change for an existing profile.
     *
     * @param uniqueId player UUID
     * @param careerId target career identifier
     * @throws SQLException when the selected JDBC database rejects the operation
     */
    public void updateCareer(UUID uniqueId, String careerId) throws SQLException {
        Objects.requireNonNull(uniqueId, "uniqueId");
        String normalizedCareerId = Objects.requireNonNull(careerId, "careerId").trim();
        if (normalizedCareerId.isBlank()) throw new IllegalArgumentException("careerId cannot be blank");
        int changedRows = database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_profiles SET career_id = ?, updated_at = ? WHERE uuid = ?"
            )) {
                statement.setString(1, normalizedCareerId);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, uniqueId.toString());
                return statement.executeUpdate();
            }
        });
        if (changedRows != 1) {
            throw new SQLException("Cannot update missing DreamRPG profile: " + uniqueId);
        }
    }
}
