package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DreamRPG's authoritative coin ledger contract.
 *
 * <p>Vault is only an interoperability adapter around this service. Game systems should use this
 * contract so the SQLite/MySQL storage choice and transaction rules remain inside DreamRPG.</p>
 */
public interface CoinService {

    /**
     * Returns a player's persisted balance, creating a zero-balance account when necessary.
     *
     * @param uniqueId player UUID
     * @return non-negative balance with two decimal places
     */
    BigDecimal balance(UUID uniqueId);

    /**
     * Tests whether the player can spend the requested positive amount.
     *
     * @param uniqueId player UUID
     * @param amount   requested amount
     * @return whether the current balance covers the amount
     */
    boolean has(UUID uniqueId, BigDecimal amount);

    /**
     * Adds coins atomically and returns the resulting transaction snapshot.
     *
     * @param uniqueId player UUID
     * @param amount   positive amount
     * @param source   business source of the deposit
     * @return immutable transaction result
     */
    CoinTransaction deposit(UUID uniqueId, BigDecimal amount, String source);

    /**
     * Removes coins atomically.
     *
     * @param uniqueId player UUID
     * @param amount   positive amount
     * @param source   business source of the withdrawal
     * @return immutable transaction result
     * @throws InsufficientCoinsException if the balance cannot cover the amount
     */
    CoinTransaction withdraw(UUID uniqueId, BigDecimal amount, String source);
}
