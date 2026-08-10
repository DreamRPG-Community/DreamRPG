package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.api.InsufficientCoinsException;
import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.database.SqlDatabase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC implementation of the DreamRPG coin ledger.
 */
@InjectComponent
public final class CoinRepository implements CoinStore {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAXIMUM = new BigDecimal("99999999999999999.99");

    private final SqlDatabase database;

    /**
     * Creates a coin repository from the initialized DreamRPG database.
     *
     * @param context DreamRPG infrastructure context
     */
    public CoinRepository(DreamRpgContext context) {
        this.database = Objects.requireNonNull(context, "context").database();
    }

    private static BigDecimal ensureAccount(Connection connection, String uuid) throws SQLException {
        BigDecimal existing = selectBalance(connection, uuid);
        if (existing != null) return existing;
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_coins (uuid, balance, created_at, updated_at) VALUES (?, ?, ?, ?)"
        )) {
            statement.setString(1, uuid);
            statement.setBigDecimal(2, ZERO);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
        return ZERO;
    }

    private static BigDecimal selectBalance(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM player_coins WHERE uuid = ?"
        )) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return normalizeBalance(new BigDecimal(resultSet.getString("balance")));
            }
        }
    }

    private static BigDecimal normalizeBalance(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "balance")
                .setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() < 0) throw new IllegalStateException("Stored DreamRPG balance is negative");
        if (normalized.compareTo(MAXIMUM) > 0) {
            throw new IllegalStateException("Stored DreamRPG balance exceeds the maximum amount");
        }
        return normalized;
    }

    private static BigDecimal normalizeDelta(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNull(value, "delta")
                .setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() == 0) throw new IllegalArgumentException("delta cannot be zero");
        if (normalized.abs().compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("delta exceeds the maximum coin amount");
        }
        return normalized;
    }

    @Override
    public BigDecimal loadOrCreate(UUID uniqueId) throws SQLException {
        Objects.requireNonNull(uniqueId, "uniqueId");
        String uuid = uniqueId.toString();
        return database.transaction(connection -> ensureAccount(connection, uuid));
    }

    @Override
    public BigDecimal adjust(UUID uniqueId, BigDecimal delta)
            throws SQLException, InsufficientCoinsException {
        Objects.requireNonNull(uniqueId, "uniqueId");
        BigDecimal normalizedDelta = normalizeDelta(delta);
        String uuid = uniqueId.toString();
        return database.transaction(connection -> {
            BigDecimal current = ensureAccount(connection, uuid);
            BigDecimal next = current.add(normalizedDelta).setScale(2, RoundingMode.UNNECESSARY);
            if (next.signum() < 0) {
                throw new InsufficientCoinsException(uniqueId, normalizedDelta.negate(), current);
            }
            if (next.compareTo(MAXIMUM) > 0) {
                throw new IllegalStateException("DreamRPG coin balance exceeds the maximum amount: " + uniqueId);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_coins SET balance = ?, updated_at = ? WHERE uuid = ?"
            )) {
                statement.setBigDecimal(1, next);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, uuid);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Cannot update missing DreamRPG coin account: " + uniqueId);
                }
            }
            return next;
        });
    }
}
