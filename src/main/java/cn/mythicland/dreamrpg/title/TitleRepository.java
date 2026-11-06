package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.framework.database.SqlDatabase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * SQL ownership and selection share a per-player transactional lock on both supported dialects.
 */
final class TitleRepository {
    private final SqlDatabase database;

    TitleRepository(SqlDatabase database) {
        this.database = database;
    }

    TitleSnapshot load(UUID playerId) throws SQLException {
        return database.transaction(connection -> {
            lock(connection, playerId);
            return read(connection, playerId);
        });
    }

    TitleSnapshot expire(UUID playerId, long now) throws SQLException {
        return database.transaction(connection -> {
            lock(connection, playerId);
            return expireSelection(connection, playerId, now);
        });
    }

    TitleSnapshot grant(UUID playerId, String id, long durationMillis, long now) throws SQLException {
        if (durationMillis < 0) throw new IllegalArgumentException("Title duration cannot be negative");
        return database.transaction(connection -> {
            lock(connection, playerId);
            Long previous = expireSelection(connection, playerId, now).expirations().get(id);
            long expiry = 0;
            if (durationMillis != 0 && (previous == null || previous != 0)) {
                expiry = Math.addExact(now, durationMillis);
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO player_titles (uuid, title_id, expires_at) VALUES (?, ?, ?) "
                            + "ON CONFLICT (uuid, title_id) DO UPDATE SET expires_at = excluded.expires_at"
            )) {
                statement.setString(1, playerId.toString());
                statement.setString(2, id);
                statement.setLong(3, expiry);
                statement.executeUpdate();
            }
            return read(connection, playerId);
        });
    }

    TitleSnapshot revoke(UUID playerId, String id) throws SQLException {
        return database.transaction(connection -> {
            lock(connection, playerId);
            try (var selection = connection.prepareStatement(
                    "UPDATE player_title_selection SET title_id = '' WHERE uuid = ? AND title_id = ?"
            ); var grant = connection.prepareStatement("DELETE FROM player_titles WHERE uuid = ? AND title_id = ?")) {
                for (var statement : List.of(selection, grant)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, id);
                    statement.executeUpdate();
                }
            }
            return read(connection, playerId);
        });
    }

    TitleSnapshot select(UUID playerId, String id, long now) throws SQLException {
        return database.transaction(connection -> {
            lock(connection, playerId);
            if (!id.isEmpty() && !read(connection, playerId).owns(id, now))
                throw new IllegalArgumentException("Title is not owned or has expired: " + id);
            try (var statement = connection.prepareStatement("UPDATE player_title_selection SET title_id = ? WHERE uuid = ?")) {
                statement.setString(1, id);
                statement.setString(2, playerId.toString());
                statement.executeUpdate();
            }
            return read(connection, playerId);
        });
    }

    private static void lock(Connection connection, UUID playerId) throws SQLException {
        try (var insert = connection.prepareStatement(
                "INSERT INTO player_title_selection (uuid, title_id) VALUES (?, '') ON CONFLICT (uuid) DO NOTHING"
        ); var lock = connection.prepareStatement("UPDATE player_title_selection SET title_id = title_id WHERE uuid = ?")) {
            insert.setString(1, playerId.toString());
            insert.executeUpdate();
            lock.setString(1, playerId.toString());
            lock.executeUpdate();
        }
    }

    private static TitleSnapshot expireSelection(Connection connection, UUID playerId, long now) throws SQLException {
        TitleSnapshot state = read(connection, playerId);
        if (state.selectedId().isEmpty() || state.owns(state.selectedId(), now)) return state;
        try (var statement = connection.prepareStatement("UPDATE player_title_selection SET title_id = '' WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
        return new TitleSnapshot(state.expirations(), "");
    }

    private static TitleSnapshot read(Connection connection, UUID playerId) throws SQLException {
        Map<String, Long> expirations = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("SELECT title_id, expires_at FROM player_titles WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) expirations.put(rows.getString(1), rows.getLong(2));
            }
        }
        try (var statement = connection.prepareStatement("SELECT title_id FROM player_title_selection WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Missing title selection row: " + playerId);
                return new TitleSnapshot(expirations, rows.getString(1));
            }
        }
    }
}
