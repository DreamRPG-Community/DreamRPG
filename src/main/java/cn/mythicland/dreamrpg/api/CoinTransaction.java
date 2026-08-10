package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable result of one DreamRPG coin ledger operation.
 *
 * @param uniqueId player whose balance changed
 * @param type operation direction
 * @param amount positive amount changed
 * @param balance balance after the operation
 * @param source business source recorded by the caller
 */
public record CoinTransaction(
        UUID uniqueId,
        Type type,
        BigDecimal amount,
        BigDecimal balance,
        String source
) {

    /** The supported directions of a coin balance change. */
    public enum Type {
        DEPOSIT,
        WITHDRAW
    }

    /**
     * Validates and normalizes a transaction snapshot to Vault-compatible two decimal places.
     */
    public CoinTransaction {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(type, "type");
        amount = normalizeAmount(amount);
        balance = normalizeBalance(balance);
        source = requireSource(source);
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        BigDecimal normalized = scale(Objects.requireNonNull(value, "amount"), "amount");
        if (normalized.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        if (normalized.compareTo(CoinValues.MAXIMUM) > 0) {
            throw new IllegalArgumentException("amount exceeds the maximum coin amount");
        }
        return normalized;
    }

    private static BigDecimal normalizeBalance(BigDecimal value) {
        BigDecimal normalized = scale(Objects.requireNonNull(value, "balance"), "balance");
        if (normalized.signum() < 0) throw new IllegalArgumentException("balance cannot be negative");
        if (normalized.compareTo(CoinValues.MAXIMUM) > 0) {
            throw new IllegalArgumentException("balance exceeds the maximum coin amount");
        }
        return normalized;
    }

    private static BigDecimal scale(BigDecimal value, String name) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must have at most two decimal places", exception);
        }
    }

    private static String requireSource(String value) {
        String source = Objects.requireNonNull(value, "source").trim();
        if (source.isBlank()) throw new IllegalArgumentException("source cannot be blank");
        return source;
    }
}
