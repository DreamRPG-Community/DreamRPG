package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.dreamrpg.experience.ExperienceState;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.database.SqlDatabase;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC implementation of the DreamRPG experience store.
 */
@InjectComponent
public final class ExperienceRepository implements ExperienceStore {

    private final SqlDatabase database;

    /**
     * Creates an experience repository from DreamRPG's shared database.
     *
     * @param context initialized DreamRPG context
     */
    public ExperienceRepository(DreamRpgContext context) {
        this.database = Objects.requireNonNull(context, "context").database();
    }

    private static ExperienceState select(Connection connection, UUID uniqueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT rpg_level, current_experience, version FROM player_experience WHERE uuid = ?"
        )) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                BigDecimal current = resultSet.getBigDecimal("current_experience");
                if (current == null) throw new SQLException("Missing current experience: " + uniqueId);
                return new ExperienceState(
                        uniqueId,
                        resultSet.getLong("rpg_level"),
                        current,
                        resultSet.getLong("version")
                );
            }
        }
    }

    private static void insert(Connection connection, ExperienceState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_experience "
                        + "(uuid, rpg_level, current_experience, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            statement.setString(1, state.uniqueId().toString());
            statement.setLong(2, state.level());
            statement.setBigDecimal(3, state.currentExperience());
            statement.setLong(4, state.databaseVersion());
            long now = System.currentTimeMillis();
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    @Override
    public ExperienceState loadOrCreate(UUID uniqueId) throws SQLException {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return database.transaction(connection -> {
            ExperienceState existing = select(connection, uniqueId);
            if (existing != null) return existing;
            ExperienceState initial = ExperienceState.initial(uniqueId);
            insert(connection, initial);
            return initial;
        });
    }

    @Override
    public long save(ExperienceState state, long expectedVersion) throws SQLException {
        ExperienceState value = Objects.requireNonNull(state, "state");
        if (expectedVersion < 0L) throw new IllegalArgumentException("expectedVersion cannot be negative");
        long nextVersion = expectedVersion + 1L;
        int changedRows = database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_experience SET rpg_level = ?, current_experience = ?, "
                            + "version = ?, updated_at = ? WHERE uuid = ? AND version = ?"
            )) {
                statement.setLong(1, value.level());
                statement.setBigDecimal(2, value.currentExperience());
                statement.setLong(3, nextVersion);
                statement.setLong(4, System.currentTimeMillis());
                statement.setString(5, value.uniqueId().toString());
                statement.setLong(6, expectedVersion);
                return statement.executeUpdate();
            }
        });
        if (changedRows != 1) {
            throw new SQLException("Player experience version conflict: " + value.uniqueId());
        }
        return nextVersion;
    }
}
