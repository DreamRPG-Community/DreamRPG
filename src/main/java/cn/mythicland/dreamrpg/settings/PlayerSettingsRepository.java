package cn.mythicland.dreamrpg.settings;

import cn.mythicland.framework.database.SqlDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists the DreamRPG player settings that are shared across supported SQL dialects.
 */
final class PlayerSettingsRepository {

    private final SqlDatabase database;

    PlayerSettingsRepository(SqlDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    Options load(UUID playerId) throws SQLException {
        return this.database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT allow_mob_knockback, auto_pickup FROM player_settings WHERE uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) return new Options(true, false);
                    return new Options(results.getBoolean("allow_mob_knockback"), results.getBoolean("auto_pickup"));
                }
            }
        });
    }

    void setAllowsMobKnockback(UUID playerId, boolean allowed) throws SQLException {
        this.database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_settings (uuid, allow_mob_knockback) VALUES (?, ?) "
                            + "ON CONFLICT (uuid) DO UPDATE SET allow_mob_knockback = excluded.allow_mob_knockback"
            )) {
                statement.setString(1, playerId.toString());
                statement.setBoolean(2, allowed);
                statement.executeUpdate();
            }
            return null;
        });
    }

    void setAutoPickup(UUID playerId, boolean enabled) throws SQLException {
        this.database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_settings (uuid, auto_pickup) VALUES (?, ?) "
                            + "ON CONFLICT (uuid) DO UPDATE SET auto_pickup = excluded.auto_pickup"
            )) {
                statement.setString(1, playerId.toString());
                statement.setBoolean(2, enabled);
                statement.executeUpdate();
            }
            return null;
        });
    }

    record Options(boolean allowMobKnockback, boolean autoPickup) {
    }
}
