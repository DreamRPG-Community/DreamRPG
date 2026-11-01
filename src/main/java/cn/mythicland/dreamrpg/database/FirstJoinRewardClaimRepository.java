package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.database.SqlDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores the one-time first-join reward claim marker.
 */
@InjectComponent
public final class FirstJoinRewardClaimRepository {

    private final SqlDatabase database;

    /**
     * Creates the repository from DreamRPG's initialized database.
     *
     * @param context DreamRPG infrastructure context
     */
    public FirstJoinRewardClaimRepository(DreamRPGContext context) {
        this.database = Objects.requireNonNull(context, "context").database();
    }

    /**
     * Returns whether a player has already claimed the reward, creating an unclaimed row when
     * this is the player's first observation.
     *
     * @param uniqueId player UUID
     * @return true when already claimed
     * @throws SQLException when the database operation fails
     */
    public boolean isClaimed(UUID uniqueId) throws SQLException {
        UUID value = Objects.requireNonNull(uniqueId, "uniqueId");
        return database.transaction(connection -> {
            ensureRow(connection, value);
            return selectClaimed(connection, value);
        });
    }

    /**
     * Atomically claims the reward when it has not been claimed before.
     *
     * @param uniqueId player UUID
     * @return true only for the caller that changed the marker
     * @throws SQLException when the database operation fails
     */
    public boolean claimIfUnclaimed(UUID uniqueId) throws SQLException {
        UUID value = Objects.requireNonNull(uniqueId, "uniqueId");
        return database.transaction(connection -> {
            ensureRow(connection, value);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_first_join_rewards SET claimed = 1, claimed_at = ? "
                            + "WHERE uuid = ? AND claimed = 0"
            )) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, value.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    /**
     * Releases a marker when opening the reward menu failed after reservation.
     *
     * @param uniqueId player UUID
     * @throws SQLException when the database operation fails
     */
    public void releaseClaim(UUID uniqueId) throws SQLException {
        UUID value = Objects.requireNonNull(uniqueId, "uniqueId");
        database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_first_join_rewards SET claimed = 0, claimed_at = NULL "
                            + "WHERE uuid = ? AND claimed = 1"
            )) {
                statement.setString(1, value.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void ensureRow(Connection connection, UUID uniqueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid FROM player_first_join_rewards WHERE uuid = ?"
        )) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_first_join_rewards (uuid, claimed, created_at) VALUES (?, 0, ?)"
        )) {
            statement.setString(1, uniqueId.toString());
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static boolean selectClaimed(Connection connection, UUID uniqueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT claimed FROM player_first_join_rewards WHERE uuid = ?"
        )) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Reward claim row disappeared: " + uniqueId);
                return resultSet.getInt("claimed") != 0;
            }
        }
    }
}
