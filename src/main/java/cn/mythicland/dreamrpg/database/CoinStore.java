package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.api.InsufficientCoinsException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Storage boundary for the DreamRPG coin ledger.
 */
public interface CoinStore {

    /**
     * Loads an account or creates it with a zero balance.
     *
     * @param uniqueId player UUID
     * @return persisted non-negative balance
     * @throws SQLException when persistence fails
     */
    BigDecimal loadOrCreate(UUID uniqueId) throws SQLException;

    /**
     * Applies one signed balance delta atomically.
     *
     * @param uniqueId player UUID
     * @param delta positive for deposit, negative for withdrawal
     * @return resulting balance
     * @throws InsufficientCoinsException if the delta would make the balance negative
     * @throws SQLException              when persistence fails
     */
    BigDecimal adjust(UUID uniqueId, BigDecimal delta)
            throws SQLException, InsufficientCoinsException;
}
